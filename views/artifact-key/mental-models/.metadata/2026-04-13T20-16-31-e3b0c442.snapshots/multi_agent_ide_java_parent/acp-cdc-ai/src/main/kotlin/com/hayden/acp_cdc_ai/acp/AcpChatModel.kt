package com.hayden.acp_cdc_ai.acp

import com.agentclientprotocol.annotations.UnstableApi
import com.agentclientprotocol.client.Client
import com.agentclientprotocol.client.ClientInfo
import com.agentclientprotocol.common.ClientSessionOperations
import com.agentclientprotocol.common.SessionCreationParameters
import com.agentclientprotocol.model.*
import com.agentclientprotocol.protocol.JsonRpcException
import com.agentclientprotocol.protocol.Protocol
import com.agentclientprotocol.transport.Transport
import com.fasterxml.jackson.databind.ObjectMapper
import com.hayden.acp_cdc_ai.acp.config.AcpChatOptionsString
import com.hayden.acp_cdc_ai.acp.config.AcpModelProperties
import com.hayden.acp_cdc_ai.acp.config.AcpProvider
import com.hayden.acp_cdc_ai.acp.config.AcpResolvedCall
import com.hayden.acp_cdc_ai.acp.config.AcpSessionRoutingKey
import com.hayden.acp_cdc_ai.acp.config.McpProperties
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey
import com.hayden.acp_cdc_ai.acp.events.EventBus
import com.hayden.acp_cdc_ai.acp.events.Events
import com.hayden.acp_cdc_ai.permission.IPermissionGate
import com.hayden.acp_cdc_ai.repository.RequestContextRepository
import com.hayden.acp_cdc_ai.sandbox.SandboxTranslation
import com.hayden.acp_cdc_ai.sandbox.SandboxTranslationRegistry
import com.hayden.utilitymodule.nullable.mapNullable
import com.hayden.utilitymodule.nullable.or
import io.modelcontextprotocol.server.IdeMcpAsyncServer.TOOL_ALLOWLIST_HEADER
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.reactor.flux
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonElement
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.ai.chat.messages.*
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.model.ChatResponse
import org.springframework.ai.chat.model.Generation
import org.springframework.ai.chat.model.StreamingChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.mcp.AsyncMcpToolCallback
import org.springframework.ai.mcp.SyncMcpToolCallback
import org.springframework.ai.model.tool.ToolCallingChatOptions
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.readLines
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * ACP-backed ChatModel implementation using the agentclientprotocol SDK.
 */

@Component
class AcpChatModel(
    private val properties: AcpModelProperties,
    private val chatMemoryContext: ChatMemoryContext?,
    private val sessionManager: AcpSessionManager,
    private val mcpProperties: McpProperties,
    private val permissionGate: IPermissionGate,
    private val requestContextRepository: RequestContextRepository,
    private val sandboxTranslationRegistry: SandboxTranslationRegistry,
    private val objectMapper: ObjectMapper = ObjectMapper()
) : ChatModel, StreamingChatModel {

    private val log: Logger = LoggerFactory.getLogger(AcpChatModel::class.java)

    @Autowired
    @Lazy
    private lateinit var eventBus: EventBus

    @Value("\${server.port:8080}")
    lateinit var mcpServerPort: Integer


    companion object AcpChatModel {

        const val MCP_SESSION_HEADER: String = "X-AG-UI-SESSION"

        fun MCP_SESSION_HEADER(): String {
            return MCP_SESSION_HEADER
        }

    }

    override fun call(prompt: Prompt): ChatResponse {
        log.info("Received request - {}.", prompt)
        val cr = doChat(prompt)
        return cr
    }

    override fun stream(prompt: Prompt): Flux<ChatResponse> {
        log.info("Received request - {}.", prompt)
        return performStream(prompt, resolveMemoryId(prompt))
    }

    fun performStream(messages: Prompt, memoryId: Any?): Flux<ChatResponse> {
        return flux {
            Flux.just(
                toChatResponse(
                    streamChat(messages, memoryId)
                        .toList(mutableListOf()),
                )
            )
        }
    }

    fun doChat(chatRequest: Prompt?): ChatResponse {
        val request = requireNotNull(chatRequest) { "chatRequest must not be null" }
        val resolvedCall = resolveRuntimeCall(request)
        val memoryId = resolveMemoryId(chatRequest)
        val hasSession = sessionExists(resolvedCall)
        val sessionContext = getOrCreateSession(resolvedCall, chatRequest)
        val messages = resolveToSendMessages(chatRequest, hasSession)
        val builtPrompt = Prompt.builder().messages(messages).chatOptions(chatRequest.options).build()

        return invokeChat(
            builtPrompt,
            sessionContext,
            memoryId
        )
    }

    fun resolveToSendMessages(messages: Prompt, hasSession: Boolean): List<Message> {
        val memoryId = resolveMemoryId(messages)
//        return if (hasSession) {
//            messages.instructions
//        } else {
        return resolveMessages(messages, memoryId)
//        }
    }

    suspend fun streamChat(prompt: Prompt, memoryId: Any?): Flow<Generation> {
        val resolvedCall = resolveRuntimeCall(prompt)
        val hasSession = sessionExists(resolvedCall)
        val session = getOrCreateSession(resolvedCall, prompt)

        val messages = resolveToSendMessages(prompt, hasSession)

        val content = listOf(
            ContentBlock.Text(
                formatPromptMessages(
                    Prompt.builder().messages(messages).chatOptions(prompt.options).build()
                )
            )
        )

        return session.prompt(content)
            .transform { event ->
                parseGenerationsFromAcpEvent(event, session, memoryId).forEach { emit(it) }
            }
            .onCompletion {
                session.flushWindows(memoryId).forEach { emit(it) }
            }
    }

    fun invokeChat(
        messages: Prompt,
        sessionContext: AcpSessionManager.AcpSessionContext,
        memoryId: Any?
    ): ChatResponse = runBlocking {
        val session = sessionContext
        val generations = mutableListOf<Generation>()
        val content = listOf(ContentBlock.Text(formatPromptMessages(messages)))

        try {
            doPerformPrompt(session, content, sessionContext, memoryId, generations)
        } catch (e: JsonRpcException) {
            if (e.message?.contains("Prompt is too long") == true) {
                // "Prompt is too long" is a compaction signal — throw CompactionException
                // so ActionRetryListenerImpl classifies it and the framework retries.
                throw CompactionException(
                    "Prompt is too long for session ${sessionContext.chatModelKey.value}",
                    sessionContext.chatModelKey.value
                )
            }
            throw e
        }

        generations.addAll(session.flushWindows(memoryId))

        // Detect compaction — throw so the framework retry handles it
        if (isSessionCompacting(generations)) {
            log.info(
                "ACP session compaction detected for {} — throwing CompactionException for framework retry",
                sessionContext.chatModelKey
            )
            eventBus.publish(
                Events.CompactionEvent.of(
                    "ACP session compacting for ${sessionContext.chatModelKey.value}",
                    sessionContext.chatModelKey
                )
            )
            throw CompactionException(
                "Session compacting for ${sessionContext.chatModelKey.value}",
                sessionContext.chatModelKey.value
            )
        }

        // Filter out compaction markers that slipped through
        generations.removeAll { g ->
            val text = runCatching { g.output.text }.getOrNull()?.trim()
            text?.lowercase()?.contains("compacting...") ?: false || text == "..." || text?.lowercase()
                ?.contains("compaction completed") ?: false
        }

        // Null/empty result — throw so ActionRetryListenerImpl classifies as NullResultError
        if (generations.isEmpty() || generations.all { it.output == null || it.output.text.isNullOrBlank() }) {
            throw RuntimeException(
                "ACP returned null result for session ${sessionContext.chatModelKey.value}"
            )
        }

        // Unparsed tool call — throw so ActionRetryListenerImpl classifies as UnparsedToolCallError
        val unparsedToolCall = detectUnparsedToolCallInLastMessage(generations)
        if (unparsedToolCall != null) {
            val err = "ACP returned unparsed tool call as final structured output: $unparsedToolCall"
            log.warn(err)
            sessionManager.eventBus.publish(Events.NodeErrorEvent.err(err, sessionContext.chatModelKey))
            throw RuntimeException(err)
        }

        // Strip markdown code fences (```json ... ```) and leading prose before JSON.
        sanitizeGenerationText(generations)

        toChatResponse(generations)
    }

    /**
     * Returns true when the session is still compacting: the combined generation text
     * contains "compacting..." but does NOT have a "compaction completed" after the
     * last "compacting...".
     */
    private fun isSessionCompacting(generations: List<Generation>): Boolean {
        val combined = generations
            .mapNotNull { runCatching { it.output.text }.getOrNull() }
            .joinToString("")
            .lowercase()

        val lastCompacting = combined.lastIndexOf("compacting...")
        if (lastCompacting < 0) {
            return false
        }

        // Check if "compaction completed" appears after the last "compacting..."
        val completedAfter = combined.indexOf("compaction completed", lastCompacting)
        return completedAfter < 0
    }

    /**
     * Strips markdown code fences and leading prose from generation text so the upstream
     * structured output parser receives clean JSON.  Agents sometimes emit responses like:
     *   "I need to clarify...\n\n```json\n{...}\n```"
     * This extracts the JSON content from inside the fence, or strips leading prose before
     * the first '{' if no fence is present.
     */
    private fun sanitizeGenerationText(generations: MutableList<Generation>) {
        val combinedText = generations
            .mapNotNull { runCatching { it.output.text }.getOrNull() }
            .joinToString("")

        val stripped = stripMarkdownCodeFences(combinedText)
        if (stripped != null && stripped != combinedText) {
            log.info("Stripped markdown code fences / leading prose from LLM response")
            generations.clear()
            generations.add(Generation(AssistantMessage(stripped)))
        }
    }

    /**
     * If the text contains a markdown code fence (```json ... ``` or ``` ... ```),
     * extracts the content inside the fence.  If no fence but there's leading prose
     * before a '{', strips the prose.  Returns null if no transformation needed.
     */
    private fun stripMarkdownCodeFences(text: String): String? {
        // Match ```json\n...\n``` or ```\n...\n```
        val fencePattern = Regex("```(?:json)?\\s*\\n([\\s\\S]*?)\\n?```")
        val fenceMatch = fencePattern.find(text)
        if (fenceMatch != null) {
            return fenceMatch.groupValues[1].trim()
        }

        // No fence — check for leading prose before first '{'
        val firstBrace = text.indexOf('{')
        if (firstBrace > 0) {
            val leading = text.substring(0, firstBrace).trim()
            // Only strip if there's actual prose (not just whitespace)
            if (leading.isNotEmpty() && !leading.startsWith("{")) {
                return text.substring(firstBrace)
            }
        }

        return null
    }

    private suspend fun doPerformPrompt(
        session: AcpSessionManager.AcpSessionContext,
        content: List<ContentBlock.Text>,
        sessionContext: AcpSessionManager.AcpSessionContext,
        memoryId: Any?,
        generations: MutableList<Generation>
    ) {
        val prompted = session.prompt(content)
        prompted
            .transform { event ->
                parseGenerationsFromAcpEvent(event, sessionContext, memoryId).forEach { emit(it) }
            }
            .collect { generations.add(it) }
    }

    private fun toChatResponse(generations: List<Generation>): ChatResponse = ChatResponse.builder()
        .generations(generations.toMutableList())
        .build()

    fun createProcessStdioTransport(
        coroutineScope: CoroutineScope,
        command: Array<String>,
        extraEnv: Map<String, String>,
        dir: Path
    ): Transport {
        val pb = ProcessBuilder(*command)
            .redirectInput(ProcessBuilder.Redirect.PIPE)
            .redirectOutput(ProcessBuilder.Redirect.PIPE)
            .redirectError(File("%s-errs.log".format(command.first())))
            .directory(dir.toFile())

        pb.environment()["CLAUDECODE"] = "0"

        extraEnv.forEach { (envKey, envValue) -> pb.environment()[envKey] = envValue }

        val process = pb
            .start()

        val stdin = process.outputStream.asSink().buffered()
        val stdout = process.inputStream.asSource().buffered()
        return AcpSerializerTransport(
            parentScope = coroutineScope,
            ioDispatcher = Dispatchers.IO,
            input = stdout,
            output = stdin
        )
    }

    private fun resolveMessages(chatRequest: Prompt, memoryId: Any?): List<Message> {
        if (memoryId == null) {
            return chatRequest.instructions
        }
        val history = chatMemoryContext?.getMessages(memoryId).orEmpty()
        return if (history.isNotEmpty()) history else chatRequest.instructions
    }

    private fun resolveMemoryId(chatRequest: Prompt): Any? {
        return resolveChatOptions(chatRequest).sessionArtifactKey()
    }

    private fun getOrCreateSession(
        resolvedCall: AcpResolvedCall,
        chatRequest: Prompt?
    ): AcpSessionManager.AcpSessionContext {
        val routingKey = resolveSessionRoutingKey(resolvedCall)
        return sessionManager.sessionContexts.computeIfAbsent(routingKey) {
            runBlocking { createSessionContext(resolvedCall, chatRequest) }
        }
    }

    @OptIn(UnstableApi::class)
    private suspend fun createSessionContext(
        resolvedCall: AcpResolvedCall,
        chatRequest: Prompt?
    ): AcpSessionManager.AcpSessionContext {
        log.info(
            "Creating ACP session context for session={}, provider={}, model={}",
            resolvedCall.sessionArtifactKey(),
            resolvedCall.providerName(),
            resolvedCall.effectiveModel()
        )

        val provider = resolvedCall.providerDefinition()

        if (!provider.transport.isNullOrBlank() && !provider.transport.equals("stdio", ignoreCase = true)) {
            throw IllegalStateException("Only stdio transport is supported for ACP integration")
        }

        val command = provider.command?.trim()?.split(Regex("\\s+"))?.toTypedArray()

        if (command == null || command.size == 0) {
            throw IllegalStateException("ACP command is not configured")
        }

        val sandboxTranslation =
            resolveSandboxTranslation(
                resolvedCall.sessionArtifactKey(),
                resolvedCall.providerName() ?: AcpProvider.CLAUDE_LLAMA,
                provider.args,
                acpResolvedCall = resolvedCall
            )
        val process = command + sandboxTranslation.args.toTypedArray()
        val workingDirectory = provider.workingDirectory

        val joinedEnv = sandboxTranslation.env.toMutableMap()
        joinedEnv.putAll(provider.envCopy())

        // Use sandbox translation working directory if available, otherwise fall back to properties or system default
        var cwd = workingDirectoryOrNull(sandboxTranslation)
            .or {
                if (workingDirectory == null || workingDirectory.isBlank())
                    null
                else
                    workingDirectory
            }
            .or { System.getProperty("user.dir") }!!

        if (cwd.isBlank())
            cwd = System.getProperty("user.dir")

        val chatKey = resolvedCall.sessionArtifactKey()
            .mapNullable { ArtifactKey(it) }
            .or { ArtifactKey.createRoot() }
            ?: ArtifactKey.createRoot()

        return try {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            val transport = createProcessStdioTransport(scope, process, joinedEnv, Path.of(cwd))
            val protocol = Protocol(scope, transport)
            val client = Client(protocol)

            val agentInfo = protocol.start()

            provider.authMethod?.let {
                val authenticationResult = client.authenticate(AuthMethodId(it))
                log.info("Authenticated with ACP {}", authenticationResult)
            }

            val initialized = client.initialize(
                ClientInfo(
                    capabilities = ClientCapabilities(
                        fs = FileSystemCapability(
                            readTextFile = true,
                            writeTextFile = true
                        ),
                        terminal = true
                    )
                )
            )

            log.info("Agent info: ${initialized.implementation.toString()}")

            val toolAllowlist = mutableSetOf<String>()
            val mcpSyncServers: MutableSet<McpServer> = mutableSetOf()

            if (chatRequest?.options is ToolCallingChatOptions) {
                val options = chatRequest.options as ToolCallingChatOptions
                toolAllowlist.addAll(options.toolNames)

                options.toolCallbacks.map { it.toolDefinition.name() }
                    .map {
                        if (it.contains(".")) {
                            val splitted = it.split(".")
                            splitted.subList(1, splitted.size).joinToString(".")
                        } else
                            it
                    }
                    .forEach { toolAllowlist.add(it) }

                options.toolCallbacks.map { it.toolDefinition }
                    .mapNotNull {
                        this.mcpProperties.retrieve(it)
                            .orElse(null)
                    }
                    .distinct()
                    .forEach { mcpSyncServers.add(it) }

                options.toolCallbacks
                    .mapNotNull {
                        when (it) {
                            is SyncMcpToolCallback -> Pair(it.toolDefinition, it.toolMetadata)
                            is AsyncMcpToolCallback -> Pair(it.toolDefinition, it.toolMetadata)
                            else -> null
                        }
                    }
                    .mapNotNull {
                        this.mcpProperties.retrieve(it.first)
                            .orElse(null)
                    }
                    .distinct()
                    .forEach { mcpSyncServers.add(it) }
            }

            val toolHeaders = mutableListOf(
                HttpHeader(TOOL_ALLOWLIST_HEADER, toolAllowlist.joinToString(","))
            )
            if (resolvedCall.sessionArtifactKey().isNotBlank()) {
                toolHeaders.add(HttpHeader(MCP_SESSION_HEADER, resolvedCall.sessionArtifactKey()))
            }

            // Only add the local MCP server if it's available

            if (this.mcpProperties.didEnableSelf()) {
                mcpSyncServers.add(McpServer.Http("agent-tools", $"http://localhost:${mcpServerPort}/mcp", toolHeaders))
            }


            val sessionParams = SessionCreationParameters(cwd, mcpSyncServers.toList())

            val messageParent = chatKey.createChild()

            // Create the buffer first so AcpSessionOperations and AcpSessionContext share the same instance.
            // This allows requestPermissions to flush buffered ToolCallEvents before blocking.
            val streamWindows = AcpStreamWindowBuffer(sessionManager.eventBus)

            var acp: AcpSessionManager.AcpSessionContext? = null

            val addMessage = object : AddMessage {
                override suspend fun addToSession(message: String) {
                    acp?.session?.prompt(listOf(ContentBlock.Text(message)))
                }
            }
            val session = client.newSession(sessionParams)
            { _, _ -> AcpSessionOperations(permissionGate, chatKey.value, streamWindows, addMessage) }

            val modelToSet = sandboxTranslation.model ?: resolvedCall.effectiveModel()
            modelToSet?.takeIf { it.isNotBlank() }?.let {
                log.info("Setting ACP session model to {}", it)
                session.setModel(ModelId(it))
            }

            val s = sessionManager.AcpSessionContext(
                scope, transport, protocol, client, session,
                streamWindows = streamWindows,
                messageParent = messageParent,
                chatModelKey = chatKey,
                sessionCreationParameters = sessionParams,
                permissionGate = permissionGate,
                chatKey = chatKey,
                chatOptions = objectMapper.writeValueAsString(resolvedCall),
                sandbox = sandboxTranslation,
                resolvedCall = resolvedCall,
            )

            s

        } catch (ex: Exception) {
            eventBus.publish(
                Events.NodeErrorEvent.err(
                    "Error when attempting to establish ACP connection for %s: %s".format(
                        chatKey.value,
                        ex.message
                    ), chatKey
                )
            )
            throw IllegalStateException("Failed to initialize ACP session", ex)
        }
    }

    private fun workingDirectoryOrNull(sandboxTranslation: SandboxTranslation): String? {
        return if (sandboxTranslation.workingDirectory() == null)
            null
        else if (sandboxTranslation.workingDirectory().isBlank())
            null
        else
            sandboxTranslation.workingDirectory
    }

    fun parseArgs(args: String?): List<String> {
        if (args.isNullOrBlank()) {
            return emptyList()
        }
        val tokenizer = StringTokenizer(args)
        val tokens = mutableListOf<String>()
        while (tokenizer.hasMoreTokens()) {
            tokens.add(tokenizer.nextToken())
        }
        return tokens.filter { it.isNotEmpty() }
    }

    fun resolveSandboxTranslation(sessionId: String?, providerName: AcpProvider, args: String?): SandboxTranslation {
        return resolveSandboxTranslation(sessionId, providerName, args, null);
    }

    fun resolveSandboxTranslation(sessionId: String?, providerName: AcpProvider, args: String?, acpResolvedCall: AcpResolvedCall?): SandboxTranslation {
        sessionId ?: return SandboxTranslation.empty()
        val context =
            requestContextRepository.findBySessionId(sessionId).orElse(null) ?: return SandboxTranslation.empty()
        var direct = sandboxTranslationRegistry.find(providerName.providerKey()).orElse(null)
        if (direct != null) {
            return direct.translateResolvedCall(context, parseArgs(args), acpResolvedCall)
        }
        direct = sandboxTranslationRegistry.find(providerName.wireValue()).orElse(null)
        if (direct != null) {
            return direct.translateResolvedCall(context, parseArgs(args), acpResolvedCall)
        }
        val fallback = sandboxTranslationRegistry.find(AcpProvider.CLAUDE_OPENROUTER.wireValue())
            .orElse(null)
        return fallback?.translateResolvedCall(context, parseArgs(args), acpResolvedCall) ?: SandboxTranslation.empty()
    }

    private fun resolveChatOptions(chatRequest: Prompt): AcpChatOptionsString {
        val chatModel = chatRequest.options?.model
        return try {
            AcpChatOptionsString.fromEncodedModel(chatModel, objectMapper)
        } catch (ex: IllegalArgumentException) {
            log.warn("Falling back to legacy ACP chat model parsing for '{}': {}", chatModel, ex.message)
            AcpChatOptionsString.create(chatModel ?: ArtifactKey.createRoot().value, null, null, emptyMap())
        }
    }

    private fun resolveRuntimeCall(chatRequest: Prompt): AcpResolvedCall {
        val chatOptions = resolveChatOptions(chatRequest)
        val providerName = properties.resolveProviderName(chatOptions.requestedProvider())
        val providerDefinition = properties.resolveProvider(chatOptions.requestedProvider())
        val effectiveModel = chatOptions.requestedModel()
            ?.takeIf { it.isNotBlank() }
            ?: providerDefinition.defaultModel()
            ?: ""
        return AcpResolvedCall(
            chatOptions.sessionArtifactKey(),
            providerName,
            effectiveModel,
            providerDefinition,
            chatOptions.options()
        )
    }

    private fun resolveSessionRoutingKey(resolvedCall: AcpResolvedCall): AcpSessionRoutingKey {
        return AcpSessionRoutingKey.from(resolvedCall, fingerprintRoutingOptions(resolvedCall.options()))
    }

    private fun fingerprintRoutingOptions(options: Map<String, Any?>?): String {
        if (options.isNullOrEmpty()) {
            return ""
        }
        val json = objectMapper.writeValueAsString(TreeMap(options))
        val digest = MessageDigest.getInstance("SHA-256").digest(json.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun sessionExists(resolvedCall: AcpResolvedCall): Boolean {
        return sessionManager.sessionContexts.containsKey(resolveSessionRoutingKey(resolvedCall))
    }

    private fun detectUnparsedToolCallInLastMessage(generations: List<Generation>): String? {
        val lastMessage = generations
            .asReversed()
            .mapNotNull { generation -> runCatching { generation.output.text }.getOrNull() }
            .firstOrNull { it.isNotBlank() }
            ?: return null

        return extractToolCallNameFromLastStructuredPayload(lastMessage)
    }

    internal fun extractToolCallNameFromLastStructuredPayload(lastMessage: String): String? {
        if (lastMessage.isBlank()) {
            return null
        }

        val trailingJson = extractTrailingJsonObject(lastMessage)
        if (trailingJson != null) {
            val tree = runCatching { objectMapper.readTree(trailingJson) }.getOrNull()
            if (tree != null && tree.isObject) {
                val toolName = tree.get("tool_name")?.asText()
                    ?: tree.get("name")?.asText()
                if (!toolName.isNullOrBlank()) {
                    return toolName
                }
            }
        }

        val trailingToolCallXml = Regex("""<tool_call>\s*([A-Za-z0-9_./-]+)""")
            .find(lastMessage.trimEnd())
            ?.groupValues
            ?.getOrNull(1)
        return if (trailingToolCallXml.isNullOrBlank()) null else trailingToolCallXml
    }

    private fun extractTrailingJsonObject(text: String): String? {
        val trimmed = text.trimEnd()
        if (!trimmed.endsWith("}")) {
            return null
        }

        var depth = 0
        var inString = false
        var start = -1

        for (i in trimmed.lastIndex downTo 0) {
            val c = trimmed[i]
            if (inString) {
                if (c == '"') {
                    // Count consecutive backslashes BEFORE this quote (to the left = lower indices,
                    // but we're already at index i so look at i-1, i-2, ...).
                    var backslashes = 0
                    var j = i - 1
                    while (j >= 0 && trimmed[j] == '\\') {
                        backslashes++
                        j--
                    }
                    // Quote is escaped only if preceded by an odd number of backslashes
                    if (backslashes % 2 == 0) {
                        inString = false
                    }
                }
                continue
            }

            when (c) {
                '"' -> inString = true
                '}' -> depth++
                '{' -> {
                    depth--
                    if (depth == 0) {
                        start = i
                        break
                    }
                }
            }
        }

        if (start < 0) {
            return null
        }
        return trimmed.substring(start)
    }

    private fun formatPromptMessages(messages: Prompt): String {
        if (messages.instructions.isEmpty()) {
            return ""
        }
        val builder = StringBuilder()
        fun formatMessageRole(role: String, message: Message): String = "$role ${message.text}"

        messages.instructions.forEach { message ->
            val role = resolveRole(message)
            if (builder.isNotEmpty()) {
                builder.append('\n')
            }
            when (message) {
                is UserMessage -> builder.append(formatMessageRole(role, message))
                is AssistantMessage -> builder.append(formatMessageRole(role, message))
                is SystemMessage -> builder.append(formatMessageRole(role, message))
                is ToolResponseMessage -> {}
            }
        }

        return builder.toString()
    }

    private fun resolveRole(message: Message): String = when (message) {
        is UserMessage -> MessageType.USER.name
        is SystemMessage -> MessageType.SYSTEM.name
        is AssistantMessage -> MessageType.ASSISTANT.name
        is ToolResponseMessage -> MessageType.TOOL.name
        else -> "user"
    }

    interface AddMessage {
        suspend fun addToSession(message: String)
    }

    class AcpSessionOperations(
        private val permissionGate: IPermissionGate,
        private val originNodeId: String,
        private val streamWindows: AcpStreamWindowBuffer,
        private val addMessage: AddMessage? = null
    ) : ClientSessionOperations {

        private val activeTerminals = ConcurrentHashMap<String, Process>()

        override suspend fun requestPermissions(
            toolCall: SessionUpdate.ToolCallUpdate,
            permissions: List<PermissionOption>,
            _meta: JsonElement?
        ): RequestPermissionResponse {
            // Flush all buffered stream events (ToolCallEvent, thoughts, etc.) before
            // blocking on the permission gate. Without this, buffered ToolCallEvents
            // are not visible to the /detail endpoint until after the permission resolves.
            streamWindows.flushAll()
            val requestId = toolCall.toolCallId.value
            permissionGate.publishRequest(requestId, originNodeId, toolCall, permissions, _meta)
            return permissionGate.awaitResponse(requestId, addMessage).requestPermissionResponse
        }

        override suspend fun fsReadTextFile(
            path: String,
            line: UInt?,
            limit: UInt?,
            _meta: JsonElement?
        ): ReadTextFileResponse {

            if (StringUtils.isBlank(path) || !Paths.get(path).toFile().exists()) {
                return ReadTextFileResponse("Path did not exist.")
            }

            val p = Paths.get(path)

            if (line == null && limit == null && p.toFile().exists()) {
                return ReadTextFileResponse(p.readText())
            }

            val lines = p.readLines()
            val startIndex = line?.toInt()?.coerceAtLeast(1)?.minus(1) ?: 0
            val endExclusive = limit
                ?.toInt()
                ?.let { (startIndex + it).coerceAtMost(lines.size) }
                ?: lines.size
            val sliced = if (startIndex >= lines.size) emptyList() else lines.subList(startIndex, endExclusive)
            val content = sliced.joinToString("\n")

            return ReadTextFileResponse(content)
        }

        override suspend fun fsWriteTextFile(
            path: String,
            content: String,
            _meta: JsonElement?
        ): WriteTextFileResponse {
            Paths.get(path).writeText(content)
            return WriteTextFileResponse()
        }

        override suspend fun notify(notification: SessionUpdate, _meta: JsonElement?) {
        }

        override suspend fun terminalCreate(
            command: String,
            args: List<String>,
            cwd: String?,
            env: List<EnvVariable>,
            outputByteLimit: ULong?,
            _meta: JsonElement?,
        ): CreateTerminalResponse {
            val processBuilder = ProcessBuilder(listOf(command) + args)
            if (cwd != null) {
                processBuilder.directory(File(cwd))
            }
            env.forEach { processBuilder.environment()[it.name] = it.value }

            val process = processBuilder.start()
            val terminalId = UUID.randomUUID().toString()
            activeTerminals[terminalId] = process

            return CreateTerminalResponse(terminalId)
        }

        override suspend fun terminalOutput(
            terminalId: String,
            _meta: JsonElement?,
        ): TerminalOutputResponse {
            val process = activeTerminals[terminalId] ?: error("Terminal not found: $terminalId")
            val stdout = process.inputStream.bufferedReader().readText()
            val stderr = process.errorStream.bufferedReader().readText()
            val output = if (stderr.isNotEmpty()) "$stdout\nSTDERR:\n$stderr" else stdout

            return TerminalOutputResponse(output, truncated = false)
        }

        override suspend fun terminalRelease(
            terminalId: String,
            _meta: JsonElement?,
        ): ReleaseTerminalResponse {
            activeTerminals.remove(terminalId)
            return ReleaseTerminalResponse()
        }

        override suspend fun terminalWaitForExit(
            terminalId: String,
            _meta: JsonElement?,
        ): WaitForTerminalExitResponse {
            val process = activeTerminals[terminalId] ?: error("Terminal not found: $terminalId")
            val exitCode = process.waitFor()
            return WaitForTerminalExitResponse(exitCode.toUInt())
        }

        override suspend fun terminalKill(
            terminalId: String,
            _meta: JsonElement?,
        ): KillTerminalCommandResponse {
            val process = activeTerminals[terminalId]
            process?.destroy()
            return KillTerminalCommandResponse()
        }
    }
}
