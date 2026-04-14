package com.hayden.acp_cdc_ai.acp

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.client.Client
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.client.ClientSession
import com.agentclientprotocol.common.Event
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.model.ClientCapabilities
import com.agentclientprotocol.model.ContentBlock
import com.agentclientprotocol.model.FileSystemCapability
import com.agentclientprotocol.model.ModelId
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.Transport
import com.hayden.acp_cdc_ai.acp.AcpChatModel.AcpSessionOperations
import com.hayden.acp_cdc_ai.acp.AcpChatModel.AddMessage
import com.hayden.acp_cdc_ai.acp.config.AcpResolvedCall
import com.hayden.acp_cdc_ai.acp.events.Artifact
import com.hayden.acp_cdc_ai.acp.events.ArtifactHashing
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey
import com.hayden.acp_cdc_ai.acp.events.EventBus
import com.hayden.acp_cdc_ai.acp.events.Events
import com.hayden.acp_cdc_ai.permission.IPermissionGate
import com.hayden.acp_cdc_ai.sandbox.SandboxTranslation
import com.hayden.acp_cdc_ai.sandbox.SandboxTranslationStrategy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.JsonElement
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class AcpSessionManager {

    val sessionContexts = ConcurrentHashMap<Any, AcpSessionContext>()

    @Lazy
    @Autowired
    lateinit var eventBus: EventBus

    private val log: Logger = LoggerFactory.getLogger(AcpSessionManager::class.java)

    inner class AcpSessionContext(
        val scope: CoroutineScope,
        val transport: Transport,
        val protocol: Protocol,
        val client: Client,
        var session: ClientSession,
        val streamWindows: AcpStreamWindowBuffer = AcpStreamWindowBuffer(eventBus),
        val messageParent: ArtifactKey,
        val chatModelKey: ArtifactKey,
        val sessionCreationParameters: SessionCreationParameters,
        val permissionGate: IPermissionGate,
        val chatKey: ArtifactKey,
        val chatOptions: String,
        val sandbox: SandboxTranslation,
        val resolvedCall: AcpResolvedCall
    ) {

        @OptIn(UnstableApi::class)
        suspend fun resetSession() {
            eventBus.publish(
                Events.ChatSessionResetEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    chatKey.value
                )
            )
            val freshAddMessage = object : AddMessage {
                override suspend fun addToSession(message: String) {
                    session.prompt(listOf(ContentBlock.Text(message)))
                }
            }
            session = client.newSession(sessionCreationParameters)
            { _, _ -> AcpSessionOperations(permissionGate, chatKey.value, streamWindows, freshAddMessage) }
            val modelToSet = sandbox.model ?: resolvedCall.effectiveModel()
            modelToSet?.takeIf { it.isNotBlank() }?.let {
                log.info("Setting ACP session model to {}", it)
                session.setModel(ModelId(it))
            }
        }

        init {
            eventBus.publish(
                Events.ChatSessionCreatedEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    messageParent.value,
                    chatModelKey,
                    chatOptions
                )
            )
        }

        suspend fun prompt(content: List<ContentBlock>, _meta: JsonElement? = null): Flow<Event> =
            session.prompt(content, _meta)

        fun appendStreamWindow(
            memoryId: Any?,
            type: AcpStreamWindowBuffer.StreamWindowType,
            content: ContentBlock
        ) = streamWindows.appendStreamWindow(memoryId, type, content, messageParent, chatModelKey)

        fun appendStreamWindow(
            memoryId: Any?,
            type: AcpStreamWindowBuffer.StreamWindowType,
            content: String
        ) = streamWindows.appendStreamWindow(memoryId, type, content, messageParent, chatModelKey)

        fun appendEventWindow(
            memoryId: Any?,
            type: AcpStreamWindowBuffer.StreamWindowType,
            event: Events.GraphEvent
        ) = streamWindows.appendEventWindow(memoryId, type, event, messageParent, chatModelKey)

        fun flushWindows(memoryId: Any?) = streamWindows.flushWindows(memoryId)

        fun flushOtherWindows(
            memoryId: Any?,
            keepType: AcpStreamWindowBuffer.StreamWindowType?
        ) = streamWindows.flushOtherWindows(memoryId, keepType)

    }

}
