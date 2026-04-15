package com.hayden.multiagentide.controller;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.permission.IPermissionGate;
import com.hayden.multiagentide.gate.PermissionGate;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentide.service.AgentExecutor;
import com.hayden.multiagentide.agent.AgentType;
import com.hayden.multiagentide.model.nodes.GraphNode;
import com.hayden.multiagentide.prompt.contributor.NodeMappings;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/agent-conversations")
@RequiredArgsConstructor
@Tag(name = "Agent Conversations", description = "Controller-to-agent conversation management")
@Slf4j
public class AgentConversationController {

    private final PermissionGate permissionGate;
    private final GraphRepository graphRepository;
    private final AgentExecutor agentExecutor;

    // ── Request/Response DTOs ────────────────────────────────────────────────

    @Schema(description = "Request to respond to an agent's call_controller justification dialogue.")
    public record RespondRequest(
            @Schema(description = "ArtifactKey of the target agent (optional, for routing context)") String targetAgentKey,
            @Schema(description = "Controller's response message delivered to the blocked agent", requiredMode = Schema.RequiredMode.REQUIRED) String message,
            @Schema(description = "Checklist action taken by the controller (e.g. 'APPROVE', 'REQUEST_CHANGES')") String checklistAction,
            @Schema(description = "Interrupt ID of the pending HUMAN_REVIEW interrupt to resolve", requiredMode = Schema.RequiredMode.REQUIRED) String interruptId,
            @Schema(description = "Whether the agent should respond back via call_controller. "
                    + "When true (default), a note is prepended telling the agent to use call_controller to reply. "
                    + "Set false for final approvals that need no further dialogue.", defaultValue = "true") Boolean expectResponse
    ) {
        public boolean expectsResponse() {
            return expectResponse == null || expectResponse;
        }
    }

    @Schema(description = "Response from the conversation respond endpoint.")
    public record RespondResponse(
            @Schema(description = "Outcome: 'resolved' on success, 'error' on failure") String status,
            @Schema(description = "The interrupt ID that was resolved") String interruptId,
            @Schema(description = "Human-readable result message") String message
    ) {}

    @Schema(description = "Request to list conversations under a workflow node scope.")
    public record ListRequest(
            @Schema(description = "ArtifactKey of the root node — conversations from descendant nodes are included", requiredMode = Schema.RequiredMode.REQUIRED) String nodeId
    ) {}

    @Schema(description = "Summary of an agent-to-controller conversation.")
    public record ConversationSummary(
            @Schema(description = "ArtifactKey of the agent node that initiated the conversation") String targetKey,
            @Schema(description = "Agent type wire value (e.g. 'discovery-agent', 'ticket-collector')") String agentType,
            @Schema(description = "Interrupt ID — use this to respond via /respond endpoint") String interruptId,
            @Schema(description = "Agent's justification reason for calling the controller") String reason,
            @Schema(description = "True if the conversation is still awaiting a controller response") boolean pending
    ) {}

    // ── POST /api/agent-conversations/respond ────────────────────────────────

    @PostMapping("/respond")
    @Operation(summary = "Respond to an agent's justification request",
            description = "Controller sends a response to an agent that called call_controller. "
                    + "Resolves the pending interrupt, delivering the message back to the blocked agent.")
    public ResponseEntity<RespondResponse> respond(@RequestBody RespondRequest request) {
        if (request.interruptId() == null || request.interruptId().isBlank()) {
            return ResponseEntity.badRequest().body(
                    new RespondResponse("error", null, "interruptId is required"));
        }
        if (request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().body(
                    new RespondResponse("error", request.interruptId(), "message is required"));
        }

        // Find the pending interrupt
        IPermissionGate.PendingInterruptRequest pending = permissionGate.getInterruptPending(
                p -> p.getInterruptId().equals(request.interruptId()));

        if (pending == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    new RespondResponse("error", request.interruptId(), "No pending interrupt found with this ID"));
        }

        // Delegate all execution logic to AgentExecutor
        AgentExecutor.ControllerResponseResult result = agentExecutor.controllerResponseExecution(
                AgentExecutor.ControllerResponseArgs.builder()
                        .interruptId(request.interruptId())
                        .originNodeId(pending.getOriginNodeId())
                        .message(request.message())
                        .checklistAction(request.checklistAction())
                        .expectResponse(request.expectsResponse())
                        .targetAgentKey(request.targetAgentKey())
                        .build()
        );

        if (!result.resolved()) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(
                    new RespondResponse("error", request.interruptId(), result.errorMessage()));
        }

        return ResponseEntity.ok(new RespondResponse("resolved", request.interruptId(), "Response delivered to agent"));
    }

    // ── POST /api/agent-conversations/list ───────────────────────────────────

    @PostMapping("/list")
    @Operation(summary = "List agent conversations under a node",
            description = "Returns conversation summaries for pending and recent agent-to-controller conversations "
                    + "within the scope of the given nodeId.")
    public ResponseEntity<List<ConversationSummary>> list(@RequestBody ListRequest request) {
        if (request.nodeId() == null || request.nodeId().isBlank()) {
            return ResponseEntity.badRequest().body(List.of());
        }

        List<ConversationSummary> summaries = new ArrayList<>();

        // Find pending interrupts that originate from descendants of the given node
        for (IPermissionGate.PendingInterruptRequest pending : permissionGate.pendingInterruptRequests()) {
            String originNodeId = pending.getOriginNodeId();
            if (isDescendantOrSelf(request.nodeId(), originNodeId)) {
                GraphNode node = graphRepository.findById(originNodeId).orElse(null);
                AgentType agentType = node != null ? NodeMappings.agentTypeFromNode(node) : null;
                summaries.add(new ConversationSummary(
                        originNodeId,
                        agentType != null ? agentType.wireValue() : null,
                        pending.getInterruptId(),
                        pending.getReason(),
                        true
                ));
            }
        }

        return ResponseEntity.ok(summaries);
    }

    private boolean isDescendantOrSelf(String scopeNodeId, String candidateNodeId) {
        if (scopeNodeId == null || candidateNodeId == null) return false;
        if (scopeNodeId.equals(candidateNodeId)) return true;
        try {
            ArtifactKey candidate = new ArtifactKey(candidateNodeId);
            ArtifactKey scope = new ArtifactKey(scopeNodeId);
            return candidate.isDescendantOf(scope);
        } catch (Exception e) {
            return candidateNodeId.startsWith(scopeNodeId + "/");
        }
    }
}
