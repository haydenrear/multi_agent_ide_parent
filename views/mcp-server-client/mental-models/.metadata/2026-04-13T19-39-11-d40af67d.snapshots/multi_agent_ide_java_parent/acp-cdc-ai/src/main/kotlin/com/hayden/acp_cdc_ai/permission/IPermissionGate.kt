package com.hayden.acp_cdc_ai.permission

import com.agentclientprotocol.model.PermissionOption
import com.agentclientprotocol.model.PermissionOptionId
import com.agentclientprotocol.model.PermissionOptionKind
import com.agentclientprotocol.model.RequestPermissionOutcome
import com.agentclientprotocol.model.RequestPermissionResponse
import com.agentclientprotocol.model.SessionUpdate
import com.hayden.acp_cdc_ai.acp.AcpChatModel.AddMessage
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey
import com.hayden.acp_cdc_ai.acp.events.Events
import kotlinx.coroutines.CompletableDeferred
import kotlinx.serialization.json.JsonElement
import java.util.function.Predicate

interface IPermissionGate {

    enum class ResolutionType {
        APPROVED,
        REJECTED,
        CANCELLED,
        FEEDBACK,
        RESOLVED;

        fun approved(): Boolean = this == APPROVED
    }

    data class PermissionResolvedResponse(
        val requestPermissionResponse: RequestPermissionResponse,
        val note: String = ""
    )

    data class PendingPermissionRequest(
        val requestId: String,
        val originNodeId: String,
        val toolCallId: String,
        val permissions: List<PermissionOption>,
        val deferred: CompletableDeferred<PermissionResolvedResponse>,
        val meta: JsonElement?,
        val nodeId: String?
    )

    data class InterruptResolution(
        val interruptId: String,
        val originNodeId: String,
        val resolutionType: ResolutionType? = null,
        val resolutionNotes: String?
    ) {
        fun approved(): Boolean = resolutionType?.approved() == true
    }

    data class InterruptResult(
            val contextId: ArtifactKey? = null,
            val assessmentStatus: ResolutionType? = null,
            val feedback: String? = null,
            val suggestions: List<String> = emptyList(),
            val contentLinks: List<String> = emptyList(),
            val output: String? = null
    )

    data class PendingInterruptRequest(
        val interruptId: String,
        val originNodeId: String,
        val type: Events.InterruptType,
        val reason: String?,
        val deferred: CompletableDeferred<InterruptResolution>
    )

    fun resolveInterrupt(
        interruptId: String,
        resolutionType: ResolutionType?,
        resolutionNotes: String?,
        reviewResult: InterruptResult? = null
    ): Boolean

    fun publishInterrupt(
        interruptId: String,
        originNodeId: String,
        type: Events.InterruptType,
        reason: String?
    ): PendingInterruptRequest

    fun getInterruptPending(requestId: Predicate<PendingInterruptRequest>): PendingInterruptRequest?

    fun isInterruptPending(requestId: Predicate<PendingInterruptRequest>): Boolean

    fun pendingPermissionRequests(): List<PendingPermissionRequest>

    fun pendingInterruptRequests(): List<PendingInterruptRequest>

    suspend fun awaitInterrupt(interruptId: String): InterruptResolution

    fun publishRequest(
        requestId: String,
        originNodeId: String,
        toolCall: SessionUpdate.ToolCallUpdate,
        permissions: List<PermissionOption>,
        meta: JsonElement?
    ): PendingPermissionRequest

    suspend fun awaitResponse(requestId: String): PermissionResolvedResponse

    suspend fun awaitResponse(requestId: String, addMessage: AddMessage?): PermissionResolvedResponse {
        return awaitResponse(requestId)
    }

    companion object {

        fun rejectOnce(): PermissionOption {
            return PermissionOption(
                PermissionOptionId(PermissionOptionKind.REJECT_ONCE.name),
                PermissionOptionKind.REJECT_ONCE.name,
                PermissionOptionKind.REJECT_ONCE
            )
        }

        fun rejectAlways(): PermissionOption {
            return PermissionOption(
                PermissionOptionId(PermissionOptionKind.REJECT_ALWAYS.name),
                PermissionOptionKind.REJECT_ALWAYS.name,
                PermissionOptionKind.REJECT_ALWAYS
            )

        }

        fun allowOnce(): PermissionOption {
            return PermissionOption(
                PermissionOptionId(PermissionOptionKind.ALLOW_ONCE.name),
                PermissionOptionKind.ALLOW_ONCE.name,
                PermissionOptionKind.ALLOW_ONCE
            )

        }

        fun allowAlways(): PermissionOption {
            return PermissionOption(
                PermissionOptionId(PermissionOptionKind.ALLOW_ALWAYS.name),
                PermissionOptionKind.ALLOW_ALWAYS.name,
                PermissionOptionKind.ALLOW_ALWAYS
            )
        }
    }

    fun resolveSelected(requestId: String, optionId: PermissionOption?): Boolean {
        return resolveSelected(requestId, optionId?.optionId?.value, "")
    }

    fun resolveSelected(requestId: String, optionId: PermissionOption?, note: String): Boolean {
        return resolveSelected(requestId, optionId?.optionId?.value, note)
    }

    fun resolveSelected(requestId: String, optionId: String?): Boolean {
        return resolveSelected(requestId, optionId, "")
    }

    fun resolveSelected(requestId: String, optionId: String?, note: String): Boolean

    fun resolveCancelled(requestId: String): Boolean

    fun resolveSelectedOption(
        permissions: List<PermissionOption>,
        optionId: String?
    ): PermissionOption?

    fun completePending(
        pending: PendingPermissionRequest,
        outcome: RequestPermissionOutcome,
        selectedOptionId: String?
    ) {
        completePending(pending, outcome, selectedOptionId, "")
    }

    fun completePending(
        pending: PendingPermissionRequest,
        outcome: RequestPermissionOutcome,
        selectedOptionId: String?,
        note: String
    )
}
