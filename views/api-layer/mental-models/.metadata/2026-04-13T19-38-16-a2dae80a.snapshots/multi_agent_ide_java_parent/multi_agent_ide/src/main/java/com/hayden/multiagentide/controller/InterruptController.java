package com.hayden.multiagentide.controller;

import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentide.prompt.contributor.NodeMappings;
import jakarta.validation.Valid;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.service.AgentControlService;
import com.hayden.multiagentide.gate.PermissionGate;
import com.hayden.multiagentide.service.PermissionGateService;
import com.hayden.multiagentide.agent.AgentType;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import com.hayden.acp_cdc_ai.permission.IPermissionGate;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/interrupts")
@RequiredArgsConstructor
@Tag(name = "Interrupts", description = "Request, resolve, and inspect interrupt signals for agent nodes")
public class InterruptController {

    private final AgentControlService agentControlService;
    private final EventBus eventBus;
    private final PermissionGate permissionGate;
    private final PermissionGateService permissionGateService;
    private final GraphRepository graphRepository;

    public record PendingInterruptSummary(
            String interruptId,
            String originNodeId,
            String reason,
            String interruptType
    ) {
    }

    @GetMapping("/pending")
    @Operation(summary = "List all pending interrupt requests",
            description = "Returns all interrupt requests currently awaiting resolution. "
                    + "Each entry includes the interruptId, originNodeId, reason, and interruptType. "
                    + "Resolve via POST /resolve with the interruptId or originNodeId as the id field.")
    public List<PendingInterruptSummary> pending() {
        return permissionGate.pendingInterruptRequests().stream()
                .map(p -> new PendingInterruptSummary(
                        p.getInterruptId(),
                        p.getOriginNodeId(),
                        p.getReason(),
                        p.getType() != null ? p.getType().name() : null
                ))
                .toList();
    }

    @PostMapping
    @Operation(summary = "Request an interrupt (PAUSE, STOP, HUMAN_REVIEW, PRUNE)",
            description = "Sends an interrupt signal to the agent node identified by originNodeId. "
                    + "PAUSE suspends execution (resumable via resolve). STOP terminates the node. "
                    + "HUMAN_REVIEW triggers a review gate requiring explicit resolution before the node continues. "
                    + "PRUNE emits a NodePrunedEvent that removes the node from the active graph. "
                    + "See Events.InterruptType for the full enum and AgentControlService for pause/stop semantics.")
    public InterruptStatusResponse requestInterrupt(@RequestBody @Valid InterruptRequest request) {
        String interruptId = UUID.randomUUID().toString();
        String reason = request.reason() != null ? request.reason() : "Interrupt requested";
        Events.InterruptType type = request.type();

        // Only HUMAN_REVIEW is accepted — the controller acts as human for routing decisions
        if (type != Events.InterruptType.HUMAN_REVIEW) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Only HUMAN_REVIEW interrupt type is accepted; got: " + type);
        }

        agentControlService.requestReview(request.originNodeId(), reason);

        String sourceAgentType = graphRepository.findById(request.originNodeId())
                .or(() -> graphRepository.findById(interruptId))
                .flatMap(gn -> Optional.ofNullable(NodeMappings.agentTypeFromNode(gn)))
                .map(AgentType::wireValue)
                .orElse(null);

        String rerouteWireValue = request.rerouteToAgentType().wireValue();

        eventBus.publish(new Events.InterruptRequestEvent(
                interruptId,
                Instant.now(),
                request.originNodeId(),
                sourceAgentType,
                rerouteWireValue,
                type,
                reason,
                List.of(),
                List.of(),
                null,
                interruptId
        ));
        return new InterruptStatusResponse(interruptId, "REQUESTED", request.originNodeId(), request.originNodeId());
    }

    @PostMapping("/resolve")
    @Operation(summary = "Resolve a pending interrupt by ID",
            description = "Resolves a pending interrupt, unblocking the paused agent node. "
                    + "The id field accepts either a direct interrupt requestId or an ArtifactKey (e.g. 'ak:01KJ...') — "
                    + "when an ArtifactKey is provided, the system searches for matching interrupts within that node's scope "
                    + "(including descendant nodes). resolutionType maps to IPermissionGate.ResolutionType. "
                    + "reviewResult carries structured feedback (IPermissionGate.InterruptResult) when resolving HUMAN_REVIEW interrupts. "
                    + "On success, publishes a ResolveInterruptEvent to the event bus. "
                    + "See also: QuestionAnswerInterruptRequest for propagator-initiated interrupts that follow the same resolution path.")
    public InterruptStatusResponse resolveInterrupt(
            @RequestBody @Valid InterruptResolution request
    ) {
        String interruptId = request.id();
        if (interruptId == null || interruptId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "id is required");
        }
        String message = request.resolutionNotes() != null ? request.resolutionNotes() : "Interrupt resolved";
        boolean resolved = permissionGate.resolveInterrupt(
                interruptId,
                request.resolutionType(),
                message,
                request.reviewResult()
        );
        String resolvedInterruptId = interruptId;

        if (!resolved && permissionGateService.isArtifactKey(interruptId)) {
            for (String candidateInterruptId : permissionGateService.findInterruptIdsInScope(interruptId)) {
                resolved = permissionGate.resolveInterrupt(
                        candidateInterruptId,
                        request.resolutionType(),
                        message,
                        request.reviewResult()
                );
                if (resolved) {
                    resolvedInterruptId = candidateInterruptId;
                    break;
                }
            }
        }

        if (!resolved) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Interrupt request not found");
        }
        String originNodeId = request.originNodeId() != null ? request.originNodeId() : resolvedInterruptId;
        eventBus.publish(new Events.ResolveInterruptEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                new ArtifactKey(originNodeId).createChild().value(),
                originNodeId,
                message,
                Events.InterruptType.HUMAN_REVIEW
        ));
        return new InterruptStatusResponse(resolvedInterruptId, "RESOLVED", originNodeId, originNodeId);
    }

    @PostMapping("/status")
    @Operation(summary = "Get status of an interrupt by ID",
            description = "Returns the current status of an interrupt. Note: the current implementation returns UNKNOWN "
                    + "for all queries — use /detail for richer interrupt inspection including tool call history.")
    public InterruptStatusResponse getStatus(@RequestBody @Valid InterruptStatusRequest request) {
        return new InterruptStatusResponse(request.interruptId(), "UNKNOWN", null, null);
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @SuppressWarnings("unused")
    public record InterruptError(String message) {
    }

    @Schema(description = "Request to create an interrupt signal on an agent node. "
            + "Only HUMAN_REVIEW is accepted — the controller acts as a human for routing decisions.")
    public record InterruptRequest(
            @Schema(description = "Interrupt type — must be HUMAN_REVIEW. See Events.InterruptType.")
            Events.InterruptType type,
            @Schema(description = "ArtifactKey of the target agent node")
            String originNodeId,
            @Schema(description = "Human-readable reason for the interrupt")
            String reason,
            @Schema(description = "Target AgentType to reroute the interrupted agent to. Required (non-null). "
                    + "Must be a workflow agent type. Cannot route to: ALL, COMMIT_AGENT, MERGE_CONFLICT_AGENT, "
                    + "AI_FILTER, AI_PROPAGATOR, AI_TRANSFORMER, REVIEW_AGENT, REVIEW_RESOLUTION_AGENT, "
                    + "MERGER_AGENT, CONTEXT_MANAGER.")
            @jakarta.validation.constraints.NotNull
            AgentType rerouteToAgentType
    ) {
    }

    @Schema(description = "Request to resolve a pending interrupt. Accepts direct IDs or ArtifactKey for scope-based lookup.")
    public record InterruptResolution(
            @Schema(description = "Interrupt requestId, nodeId, or ArtifactKey (scope-based lookup for descendants)") String id,
            @Schema(description = "Origin node ArtifactKey — used in the published ResolveInterruptEvent") String originNodeId,
            @Schema(description = "Resolution outcome. See IPermissionGate.ResolutionType.") IPermissionGate.ResolutionType resolutionType,
            @Schema(description = "Free-text notes attached to the resolution") String resolutionNotes,
            @Schema(description = "Structured review feedback for HUMAN_REVIEW interrupts. See IPermissionGate.InterruptResult.") IPermissionGate.InterruptResult reviewResult
    ) {
    }

    public record InterruptStatusRequest(
            @Schema(description = "Interrupt ID to check status for") String interruptId) {
    }

    public record InterruptStatusResponse(
            String interruptId,
            String status,
            String originNodeId,
            String resumeNodeId
    ) {
    }

    public record InterruptDetailResponse(
            String interruptId,
            String requestId,
            String originNodeId,
            String nodeId,
            String interruptType,
            String sourceAgentType,
            String rerouteToAgentType,
            String reason,
            String contextForDecision,
            String status,
            List<PermissionGateService.ToolCallInfo> toolCalls
    ) {
    }

    public record InterruptDetailRequest(String id) {
    }

    @PostMapping("/detail")
    @Operation(summary = "Get full detail for an interrupt including tool calls",
            description = "Returns comprehensive interrupt detail: the interrupt's origin, type, source/reroute agent types, "
                    + "reason, contextForDecision, pending/resolved status, and the most recent 40 tool calls "
                    + "within the interrupt's node scope. The id field accepts a requestId, nodeId, or ArtifactKey — "
                    + "ArtifactKey-based lookup matches any InterruptRequestEvent whose nodeId is a descendant of the given key. "
                    + "sourceAgentType and rerouteToAgentType reflect the unified interrupt handling model "
                    + "(see InterruptRouting and handleUnifiedInterrupt for routing semantics).")
    public InterruptDetailResponse detail(@RequestBody @Valid InterruptDetailRequest request) {
        String id = request.id();
        Events.InterruptRequestEvent interruptEvent = permissionGateService.findInterruptRequestEvent(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Interrupt request not found"));

        boolean pending = permissionGate.pendingInterruptRequests().stream()
                .anyMatch(p -> Objects.equals(p.getInterruptId(), interruptEvent.requestId())
                        || Objects.equals(p.getInterruptId(), interruptEvent.nodeId()));
        String status = pending ? "PENDING" : "RESOLVED_OR_UNKNOWN";

        List<PermissionGateService.ToolCallInfo> toolCalls = permissionGateService.findToolCallsInScope(
                interruptEvent.nodeId(), 40);

        return new InterruptDetailResponse(
                interruptEvent.requestId(),
                interruptEvent.requestId(),
                interruptEvent.nodeId(),
                interruptEvent.nodeId(),
                interruptEvent.interruptType().name(),
                interruptEvent.sourceAgentType(),
                interruptEvent.rerouteToAgentType(),
                interruptEvent.reason(),
                interruptEvent.contextForDecision(),
                status,
                toolCalls
        );
    }
}
