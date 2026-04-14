package com.hayden.multiagentide.controller;

import com.hayden.multiagentide.model.nodes.*;
import jakarta.validation.Valid;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.adapter.SseEventAdapter;
import com.hayden.multiagentide.cli.CliEventFormatter;
import com.hayden.multiagentide.controller.model.NodeIdRequest;
import com.hayden.multiagentide.filter.service.LayerIdResolver;
import com.hayden.multiagentide.propagation.service.PropagationItemService;
import com.hayden.multiagentide.repository.EventStreamRepository;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentide.transformation.integration.ControllerEndpointTransformationIntegration;
import com.hayden.multiagentide.ui.shared.SharedUiInteractionService;
import com.hayden.multiagentide.ui.shared.UiActionCommand;
import com.hayden.multiagentide.ui.shared.UiStateSnapshot;
import com.hayden.multiagentide.ui.shared.UiStateStore;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestController
@RequestMapping("/api/ui")
@RequiredArgsConstructor
@Tag(name = "Orchestration UI", description = "Workflow graph, node events, and goal management for the orchestration UI")
public class LlmOrchestrationController {

    private static final String CONTROLLER_ID = "LlmOrchestrationController";

    private final EventBus eventBus;
    private final SharedUiInteractionService sharedUiInteractionService;
    private final UiStateStore uiStateStore;
    private final SseEventAdapter sseEventAdapter;
    private final EventStreamRepository eventStreamRepository;
    private final GraphRepository graphRepository;
    private final CliEventFormatter cliEventFormatter;
    private final LayerIdResolver layerIdResolver;
    private final OrchestrationController orchestrationController;
    private final ControllerEndpointTransformationIntegration controllerEndpointTransformationIntegration;
    private final PropagationItemService propagationItemService;

    @PostMapping("/nodes/state")
    @Operation(summary = "Get current UI state snapshot for a node",
            description = "Returns the full UiStateSnapshot for the given node scope — active panel focus, "
                    + "chat input/search state, event stream position, and session list. This is the same state "
                    + "that concrete renderers (TUI, web) consume. See UiStateStore for the server-side state model.")
    public UiStateSnapshot state(@RequestBody @Valid NodeIdRequest request) {
        return uiStateStore.snapshot(request.nodeId());
    }

    @PostMapping("/goals/start")
    @Operation(summary = "Start a new goal and return the root node identifier",
            description = "Creates a new workflow execution for the given goal and repository. Returns the root "
                    + "nodeId (ArtifactKey) which scopes all subsequent polling, event, and action calls. "
                    + "Tags should be 3-8 short kebab-case descriptors covering change type and subsystem.")
    public StartGoalResponse startGoal(@RequestBody @Valid OrchestrationController.StartGoalRequest request) {
        OrchestrationController.StartGoalResponse started = orchestrationController.startGoalAsync(request);
        return new StartGoalResponse(started.nodeId(), started.nodeId(), "started");
    }

    @PostMapping("/nodes/actions")
    @Operation(summary = "Dispatch a UI action event to a node",
            description = "Publishes a TuiInteractionGraphEvent for the given node. actionType must match a value in "
                    + "UiActionCommand.ActionType (case-insensitive). The payload map carries action-specific data. "
                    + "The interaction is converted via SharedUiInteractionService.toInteractionEvent() and published "
                    + "to the EventBus. Returns 'queued' on success or 'rejected' if the actionType is missing or invalid.")
    public ActionResponse action(@RequestBody @Valid UiActionRequest request) {
        String nodeId = request.nodeId();
        if (request.actionType() == null || request.actionType().isBlank()) {
            return new ActionResponse(null, "rejected");
        }
        UiActionCommand.ActionType actionType;
        try {
            actionType = UiActionCommand.ActionType.valueOf(request.actionType().trim().toUpperCase());
        } catch (Exception e) {
            return new ActionResponse(null, "rejected");
        }

        UiActionCommand command = new UiActionCommand(
                null,
                nodeId,
                actionType,
                request.payload() == null ? Map.of() : request.payload(),
                Instant.now()
        );

        eventBus.publish(new Events.TuiInteractionGraphEvent(
                command.actionId(),
                command.requestedAt(),
                nodeId,
                nodeId,
                sharedUiInteractionService.toInteractionEvent(command)
        ));

        return new ActionResponse(command.actionId(), "queued");
    }

    @PostMapping("/quick-actions")
    @Operation(summary = "Execute a quick action (START_GOAL, SEND_MESSAGE)",
            description = "Convenience endpoint combining goal creation and message injection. "
                    + "START_GOAL requires 'goal' and 'repositoryUrl'; creates a new workflow and returns the root nodeId. "
                    + "'tags' should be 3-8 short kebab-case descriptors. "
                    + "SEND_MESSAGE requires 'nodeId'; publishes an AddMessageEvent to inject a human message "
                    + "into the agent's conversation stream. Returns 'started'/'queued' on success or 'rejected' with an error message.")
    public QuickActionResponse quickAction(@RequestBody @Valid QuickActionRequest request) {
        String actionType = request == null || request.actionType() == null
                ? ""
                : request.actionType().trim().toUpperCase();
        return switch (actionType) {
            case "START_GOAL" -> {
                if (request.goal() == null || request.goal().isBlank()) {
                    yield new QuickActionResponse(null, "rejected", null, "goal is required");
                }
                if (request.repositoryUrl() == null || request.repositoryUrl().isBlank()) {
                    yield new QuickActionResponse(null, "rejected", null, "repositoryUrl is required");
                }
                OrchestrationController.StartGoalResponse started = orchestrationController.startGoalAsync(
                        new OrchestrationController.StartGoalRequest(
                                request.goal(),
                                request.repositoryUrl(),
                                request.baseBranch(),
                                request.title(),
                                request.tags()
                        )
                );
                yield new QuickActionResponse(started.nodeId(), "started", started.nodeId(), null);
            }
            case "SEND_MESSAGE" -> {
                if (request.nodeId() == null || request.nodeId().isBlank()) {
                    yield new QuickActionResponse(null, "rejected", null, "nodeId is required");
                }
                String actionId = UUID.randomUUID().toString();
                eventBus.publish(new Events.AddMessageEvent(
                        actionId,
                        Instant.now(),
                        request.nodeId(),
                        request.message() == null ? "" : request.message()
                ));
                yield new QuickActionResponse(actionId, "queued", request.nodeId(), null);
            }
            default -> new QuickActionResponse(null, "rejected", null, "Unsupported actionType");
        };
    }

    @PostMapping("/nodes/events")
    @Operation(summary = "List events for a node with cursor pagination",
            description = "Returns paginated, formatted event summaries for all events within the node scope "
                    + "(node and all descendants). Summaries are truncated by CliEventFormatter using the 'truncate' "
                    + "parameter (default 180 chars, positive values clamped to [80, 10000]). "
                    + "Pass a negative value (e.g. -1) to disable truncation and return full event text. "
                    + "For full formatted detail of a single event, use the event-detail endpoint. "
                    + "See CliEventFormatter.java for truncation logic.")
    public UiEventPage events(@RequestBody @Valid NodeEventsRequest request) {
        String nodeId = request.nodeId();
        int limit = request.limit() <= 0 ? 50 : request.limit();
        int rawTruncate = request.truncate();
        int truncate = rawTruncate == 0 ? 180 : rawTruncate;
        String sort = request.sort() == null || request.sort().isBlank() ? "asc" : request.sort();
        String cursor = request.cursor();

        int safeLimit = Math.max(1, Math.min(500, limit));
        int safeTruncate = truncate < 0 ? truncate : Math.max(80, Math.min(10_000, truncate));
        String controllerLayerId = controllerLayerId();

        List<Events.GraphEvent> scopedEvents = eventStreamRepository.list().stream()
                .filter(Objects::nonNull)
                .filter(event -> matchesNodeScope(nodeId, event.nodeId()))
                .sorted(Comparator.comparing(Events.GraphEvent::timestamp)
                        .thenComparing(Events.GraphEvent::eventId, Comparator.nullsLast(String::compareTo)))
                .toList();

        int offset;
        if ("desc".equalsIgnoreCase(sort) && cursor == null) {
            offset = Math.max(0, scopedEvents.size() - safeLimit);
        } else {
            offset = parseCursor(cursor);
        }

        int toIndex = Math.min(scopedEvents.size(), offset + safeLimit);
        List<Events.GraphEvent> pageEvents = offset >= scopedEvents.size() ? List.of() : scopedEvents.subList(offset, toIndex);
        List<UiEventSummary> items = pageEvents.stream()
                .map(event -> new UiEventSummary(
                        event.eventId(),
                        event.nodeId(),
                        event.eventType(),
                        event.timestamp(),
                        summarize(event, safeTruncate, controllerLayerId)
                ))
                .toList();

        String nextCursor = toIndex < scopedEvents.size() ? Integer.toString(toIndex) : null;
        return new UiEventPage(
                items,
                new PageMeta(safeLimit, cursor, nextCursor, nextCursor != null),
                scopedEvents.size()
        );
    }

    @PostMapping("/nodes/events/detail")
    @Operation(summary = "Get full formatted detail for a single event",
            description = "Returns the complete formatted representation of a single event. The 'maxFieldLength' "
                    + "parameter controls CliEventFormatter truncation (default 20000, positive values clamped to [120, 80000]). "
                    + "Pass a negative value (e.g. -1) to disable truncation entirely and return the full untruncated event body. "
                    + "Set 'pretty' to true for multi-line indented output. Propagation events auto-expand to "
                    + "full prettyPrint() length regardless of maxFieldLength when maxFieldLength is positive. "
                    + "See CliEventFormatter.java for formatting logic.")
    public UiEventDetail eventDetail(@RequestBody @Valid EventDetailRequest request) {
        String nodeId = request.nodeId();
        String eventId = request.eventId();
        boolean pretty = request.pretty();
        int rawMaxFieldLength = request.maxFieldLength();
        int maxFieldLength = rawMaxFieldLength == 0 ? 20000 : rawMaxFieldLength;

        Events.GraphEvent event = eventStreamRepository.findById(eventId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Event not found: " + eventId));
        if (!matchesNodeScope(nodeId, event.nodeId())) {
            throw new ResponseStatusException(NOT_FOUND, "Event not in node scope: " + eventId);
        }
        int safeMaxFieldLength = maxFieldLength < 0 ? maxFieldLength : Math.max(120, Math.min(80_000, maxFieldLength));
        String controllerLayerId = controllerLayerId();
        String formatted = cliEventFormatter.format(
                new CliEventFormatter.CliEventArgs(
                        safeMaxFieldLength,
                        event,
                        pretty,
                        controllerLayerId
                ));
        return new UiEventDetail(
                event.eventId(),
                event.nodeId(),
                event.eventType(),
                event.timestamp(),
                summarize(event, 220, controllerLayerId),
                formatted
        );
    }

    @PostMapping(value = "/nodes/events/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream live events for a node via SSE",
            description = "Opens a Server-Sent Events stream that pushes every new GraphEvent matching the given "
                    + "nodeId scope (node and all descendants) in real time. Events are delivered as-is (not formatted). "
                    + "The SSE connection stays open until the client disconnects. "
                    + "See SseEventAdapter for connection management and keep-alive behavior.")
    public SseEmitter stream(@RequestBody @Valid NodeIdRequest request) {
        String nodeId = request.nodeId();
        return sseEventAdapter.registerEmitter(
                event -> matchesNodeScope(nodeId, event.nodeId()),
                CONTROLLER_ID
        );
    }

    @PostMapping("/workflow-graph")
    @Operation(summary = "Get the full workflow graph tree with metrics for a node",
            description = "Primary polling endpoint. Returns the full workflow node tree rooted at the given nodeId, "
                    + "with per-node metrics (event counts, pending items, route-back counts). "
                    + "metrics.pendingItems surfaces PERMISSION, INTERRUPT, REVIEW, and PROPAGATION blocked states. "
                    + "The response may be transformed by active ControllerEndpointTransformationIntegration policies. "
                    + "Use errorWindowSeconds to control the recent-error time window (default 180s, range [30, 3600]).")
    public Object workflowGraph(@RequestBody @Valid WorkflowGraphRequest request) {
        String nodeId = request.nodeId();
        int errorWindowSeconds = request.errorWindowSeconds() <= 0 ? 180 : request.errorWindowSeconds();

        int safeErrorWindowSeconds = Math.max(30, Math.min(3600, errorWindowSeconds));
        String rootNodeId = toRootNodeId(nodeId);
        Instant now = Instant.now();
        Instant errorWindowStart = now.minusSeconds(safeErrorWindowSeconds);

        Map<String, GraphNode> allNodes = graphRepository.findSubtree(rootNodeId);

        EventStreamRepository.ScopedEventStats globalStats =
                eventStreamRepository.computeScopedStats(rootNodeId, errorWindowStart);

        Map<String, EventStreamRepository.NodeEventMetrics> metricsMap = new LinkedHashMap<>();
        for (String nid : allNodes.keySet()) {
            eventStreamRepository.computeMetrics(nid).ifPresent(m -> metricsMap.put(nid, m));
        }

        Map<String, Integer> errorsByAction = new LinkedHashMap<>();
        for (GraphNode gn : allNodes.values()) {
            String action = actionName(gn);
            EventStreamRepository.NodeEventMetrics m = metricsMap.get(gn.nodeId());
            if (action != null && m != null && m.nodeErrorCount() > 0) {
                errorsByAction.merge(action, m.nodeErrorCount(), Integer::sum);
            }
        }

        WorkflowNode root = buildWorkflowNode(rootNodeId, allNodes, metricsMap);

        WorkflowGraphStats stats = new WorkflowGraphStats(
                globalStats.totalEvents(),
                allNodes.size(),
                globalStats.eventTypeCounts(),
                safeErrorWindowSeconds,
                globalStats.recentErrorCount(),
                globalStats.recentErrorsByNodeType(),
                errorsByAction,
                globalStats.chatSessionEvents(),
                globalStats.chatMessageEvents(),
                globalStats.totalThoughtTokens(),
                globalStats.totalStreamTokens()
        );

        WorkflowGraphResponse response = new WorkflowGraphResponse(nodeId, rootNodeId, now, stats, root);
        return controllerEndpointTransformationIntegration
                .maybeTransform(CONTROLLER_ID, "workflowGraph", response)
                .body();
    }

    @PostMapping("/workflow-graph/status")
    @Operation(summary = "Get compact run status for a node",
            description = "Lightweight endpoint returning RUNNING, COMPLETE, or FAILED for a workflow node. "
                    + "COMPLETE is set when a GOAL_COMPLETED event is present. FAILED when NODE_ERROR count > 0 "
                    + "and no GOAL_COMPLETED. Suitable for polling loops that only need a terminal-status check.")
    public WorkflowStatusResponse workflowStatus(@RequestBody @Valid NodeIdRequest request) {
        String nodeId = request.nodeId();
        String rootNodeId = toRootNodeId(nodeId);
        Instant errorWindowStart = Instant.now().minusSeconds(180);
        EventStreamRepository.ScopedEventStats stats =
                eventStreamRepository.computeScopedStats(rootNodeId, errorWindowStart);
        Map<String, Integer> counts = stats.eventTypeCounts() != null ? stats.eventTypeCounts() : Map.of();
        int goalCompleted = counts.getOrDefault("GOAL_COMPLETED", 0);
        int nodeErrors = counts.getOrDefault("NODE_ERROR", 0);
        String status = goalCompleted > 0 ? "COMPLETE" : (nodeErrors > 0 ? "FAILED" : "RUNNING");
        String summary = goalCompleted > 0
                ? "Goal completed (" + goalCompleted + " GOAL_COMPLETED event(s))"
                : nodeErrors > 0
                ? "Run has " + nodeErrors + " NODE_ERROR event(s), no GOAL_COMPLETED"
                : "Run is in progress";
        return new WorkflowStatusResponse(nodeId, status, summary, stats.totalEvents());
    }

    public record WorkflowStatusResponse(
            String nodeId,
            @Schema(description = "RUNNING | COMPLETE | FAILED") String status,
            String summary,
            long totalEvents
    ) {}

    private WorkflowNode buildWorkflowNode(
            String nid,
            Map<String, GraphNode> allNodes,
            Map<String, EventStreamRepository.NodeEventMetrics> metricsMap
    ) {
        GraphNode gn = allNodes.get(nid);
        if (gn == null) return null;

        EventStreamRepository.NodeEventMetrics m = metricsMap.getOrDefault(nid,
                new EventStreamRepository.NodeEventMetrics(0, 0, 0, 0, 0, 0L, 0, 0L, 0, 0));

        List<PendingItem> pendingItems = new ArrayList<>();
        List<WorkflowNode> children = new ArrayList<>();

        for (String childId : gn.childNodeIds()) {
            GraphNode child = allNodes.get(childId);
            if (child == null) continue;

            if (child instanceof AskPermissionNode perm) {
                if (perm.status() == Events.NodeStatus.COMPLETED) continue;
                pendingItems.add(new PendingItem(
                        perm.toolCallId(), perm.nodeId(), "PERMISSION",
                        "Permission requested for tool call " + perm.toolCallId()
                                + ". Resolve with: resolve-permission --id " + perm.toolCallId() + " --option-type ALLOW_ONCE"
                ));
                children.add(buildWorkflowNode(childId, allNodes, metricsMap));
            } else if (child instanceof InterruptNode interrupt) {
                InterruptContext ctx = interrupt.interruptContext();
                if (ctx.status() == InterruptContext.InterruptStatus.RESOLVED
                        || interrupt.status() == Events.NodeStatus.COMPLETED) continue;
                pendingItems.add(new PendingItem(
                        ctx.interruptNodeId() != null ? ctx.interruptNodeId() : interrupt.nodeId(),
                        interrupt.nodeId(), "INTERRUPT",
                        "Interrupt requested: " + ctx.reason()
                                + ". Resolve with: resolve-interrupt --id " + interrupt.nodeId()
                                + " --origin-node-id " + ctx.originNodeId()
                ));
                children.add(buildWorkflowNode(childId, allNodes, metricsMap));
            } else if (child instanceof ReviewNode review) {
                InterruptContext ctx = review.interruptContext();
                if (ctx != null && ctx.status() != InterruptContext.InterruptStatus.RESOLVED
                        && review.status() != Events.NodeStatus.COMPLETED) {
                    pendingItems.add(new PendingItem(
                            review.nodeId(), review.nodeId(), "REVIEW",
                            "Review requested (" + review.reviewerAgentType()
                                    + "). Review content at node " + review.nodeId()
                    ));
                }
                children.add(buildWorkflowNode(childId, allNodes, metricsMap));
            } else {
                children.add(buildWorkflowNode(childId, allNodes, metricsMap));
            }
        }

        propagationItemService.findPendingBySourceNodeId(nid).forEach(item ->
                pendingItems.add(new PendingItem(
                        item.getItemId(),
                        item.getSourceNodeId(),
                        "PROPAGATION",
                        item.getSummaryText() == null || item.getSummaryText().isBlank()
                                ? "Propagation item pending acknowledgement or review"
                                : item.getSummaryText()
                )));

        children.sort(Comparator.comparing(
                (WorkflowNode wn) -> wn != null && wn.lastEventAt() != null ? wn.lastEventAt() : Instant.MAX
        ).thenComparing(wn -> wn != null ? wn.nodeId() : ""));

        int routeBackCount = (gn instanceof HasWorkflowContext<?> hwc && hwc.workflowContext() != null)
                ? hwc.workflowContext().runCount()
                : 0;

        WorkflowNodeMetrics metrics = new WorkflowNodeMetrics(
                m.nodeErrorCount(), m.chatSessionEvents(), m.chatMessageEvents(),
                m.thoughtDeltas(), m.thoughtTokens(), m.streamDeltas(), m.streamTokens(),
                m.toolEvents(), m.otherEvents(), List.copyOf(pendingItems)
        );

        return new WorkflowNode(
                gn.nodeId(),
                gn.parentNodeId(),
                gn.title(),
                gn.nodeType() != null ? gn.nodeType().name() : null,
                gn.status() != null ? gn.status().name() : null,
                actionName(gn),
                gn.goal(),
                routeBackCount,
                gn.lastUpdatedAt(),
                m.totalEvents(),
                metrics,
                children.stream().filter(Objects::nonNull).toList()
        );
    }

    private static String actionName(GraphNode gn) {
        return switch (gn) {
            case OrchestratorNode ignored -> "orchestrator";
            case DiscoveryOrchestratorNode ignored -> "discovery-orchestrator";
            case DiscoveryDispatchAgentNode ignored -> "discovery-dispatch";
            case DiscoveryNode ignored -> "discovery-agent";
            case DiscoveryCollectorNode ignored -> "discovery-collector";
            case PlanningOrchestratorNode ignored -> "planning-orchestrator";
            case PlanningDispatchAgentNode ignored -> "planning-dispatch";
            case PlanningNode ignored -> "planning-agent";
            case PlanningCollectorNode ignored -> "planning-collector";
            case TicketOrchestratorNode ignored -> "ticket-orchestrator";
            case TicketDispatchAgentNode ignored -> "ticket-dispatch";
            case TicketNode ignored -> "ticket-agent";
            case TicketCollectorNode ignored -> "ticket-collector";
            default -> null;
        };
    }

    private String summarize(Events.GraphEvent event, int maxLength, String layerId) {
        String formatted = cliEventFormatter.format(
                new CliEventFormatter.CliEventArgs(maxLength, event, false, layerId)
        );
        if (maxLength < 0 || formatted == null || formatted.length() <= maxLength) {
            return formatted;
        }
        return formatted.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String controllerLayerId() {
        return layerIdResolver.resolveForController(CONTROLLER_ID).orElse(null);
    }

    private int parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            return Math.max(0, Integer.parseInt(cursor));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private boolean matchesNodeScope(String scopeNodeId, String eventNodeId) {
        if (scopeNodeId == null || scopeNodeId.isBlank() || eventNodeId == null || eventNodeId.isBlank()) {
            return false;
        }
        if (scopeNodeId.equals(eventNodeId)) {
            return true;
        }
        try {
            ArtifactKey candidate = new ArtifactKey(eventNodeId);
            ArtifactKey scope = new ArtifactKey(scopeNodeId);
            return candidate.isDescendantOf(scope);
        } catch (Exception ignored) {
            return eventNodeId.startsWith(scopeNodeId + "/");
        }
    }

    private String toRootNodeId(String nodeId) {
        try {
            ArtifactKey current = new ArtifactKey(nodeId);
            while (current.parent().isPresent()) {
                current = current.parent().get();
            }
            return current.value();
        } catch (Exception ignored) {
            int idx = nodeId.indexOf('/');
            return idx > 0 ? nodeId.substring(0, idx) : nodeId;
        }
    }

    private String parentNodeId(String nodeId) {
        try {
            return new ArtifactKey(nodeId).parent().map(ArtifactKey::value).orElse(null);
        } catch (Exception ignored) {
            int idx = nodeId.lastIndexOf('/');
            return idx > 0 ? nodeId.substring(0, idx) : null;
        }
    }

    // ── Request/Response Records ─────────────────────────────────────

    @Schema(description = "Request to dispatch a UI action to a node.")
    public record UiActionRequest(
            @Schema(description = "ArtifactKey identifying the target node") String nodeId,
            @Schema(description = "Action type — must match a UiActionCommand.ActionType enum value (case-insensitive)") String actionType,
            @Schema(description = "Action-specific key-value payload") java.util.Map<String, Object> payload) {
    }

    public record ActionResponse(String actionId, String status) {
    }

    public record StartGoalResponse(String nodeId, String runId, String status) {
    }

    @Schema(description = "Request for a quick action. Fields are context-dependent on actionType.")
    public record QuickActionRequest(
            @Schema(description = "Action to execute: 'START_GOAL' or 'SEND_MESSAGE'") String actionType,
            @Schema(description = "Target node ArtifactKey (required for SEND_MESSAGE)") String nodeId,
            @Schema(description = "Message text to inject (SEND_MESSAGE only)") String message,
            @Schema(description = "Goal description for the new workflow (START_GOAL only, required)") String goal,
            @Schema(description = "Git repository URL (START_GOAL only, required)") String repositoryUrl,
            @Schema(description = "Base branch to target (START_GOAL only, optional)") String baseBranch,
            @Schema(description = "Human-readable title for the goal (START_GOAL only, optional)") String title,
            @Schema(description = "3-8 short kebab-case tags covering change type and subsystem (START_GOAL only)") List<String> tags
    ) {
        public QuickActionRequest(
                String actionType,
                String nodeId,
                String message,
                String goal,
                String repositoryUrl,
                String baseBranch,
                String title
        ) {
            this(actionType, nodeId, message, goal, repositoryUrl, baseBranch, title, List.of());
        }

        public QuickActionRequest {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }
    }

    public record QuickActionResponse(String actionId, String status, String nodeId, String error) {
    }

    @Schema(description = "Request for paginated node events. Summaries are truncated by CliEventFormatter — "
            + "see CliEventFormatter.java for the formatting and truncation pipeline.")
    public record NodeEventsRequest(
            @Schema(description = "ArtifactKey scoping the query to this node and all descendants") String nodeId,
            @Schema(description = "Max events per page (default 50, clamped to [1, 500])") int limit,
            @Schema(description = "Opaque cursor from previous page's nextCursor for forward pagination") String cursor,
            @Schema(description = "Max characters for each event summary field (default 180, positive values clamped to [80, 10000]). "
                    + "Pass any negative value (e.g. -1) to disable truncation and return full event text. "
                    + "0 is treated as the default (180). Controls CliEventFormatter.summarize() truncation length.") int truncate,
            @Schema(description = "Sort order: 'asc' (default) or 'desc'. Desc without cursor starts from latest events.") String sort
    ) {
    }

    @Schema(description = "Request for full event detail. Formatting is performed by CliEventFormatter — "
            + "see CliEventFormatter.java for the complete rendering pipeline including filter integration.")
    public record EventDetailRequest(
            @Schema(description = "ArtifactKey scope — event must be within this node's subtree") String nodeId,
            @Schema(description = "Unique event identifier to retrieve") String eventId,
            @Schema(description = "Enable multi-line indented output (CliEventFormatter prettyPrint mode)") boolean pretty,
            @Schema(description = "Max characters per field value (default 20000, positive values clamped to [120, 80000]). "
                    + "Pass any negative value (e.g. -1) to disable truncation entirely and return the full untruncated body. "
                    + "0 is treated as the default (20000). "
                    + "Controls CliEventFormatter.summarize() truncation. Propagation events auto-expand to full "
                    + "prettyPrint() length when maxFieldLength is positive.") int maxFieldLength
    ) {
    }

    @Schema(description = "Request for the workflow graph tree. This is the primary polling endpoint.")
    public record WorkflowGraphRequest(
            @Schema(description = "ArtifactKey — automatically resolved to the root node for tree construction") String nodeId,
            @Schema(description = "Seconds to look back for recent errors (default 180, range [30, 3600])") int errorWindowSeconds
    ) {
    }

    public record UiEventSummary(String eventId, String nodeId, String eventType, Instant timestamp, String summary) {
    }

    public record UiEventDetail(
            String eventId,
            String nodeId,
            String eventType,
            Instant timestamp,
            String summary,
            String formatted
    ) {
    }

    public record UiEventPage(List<UiEventSummary> items, PageMeta page, int total) {
    }

    public record PageMeta(int limit, String cursor, String nextCursor, boolean hasNext) {
    }

    public record WorkflowGraphResponse(
            String requestedNodeId,
            String rootNodeId,
            Instant capturedAt,
            WorkflowGraphStats stats,
            WorkflowNode root
    ) {
    }

    public record WorkflowGraphStats(
            int totalEvents,
            int totalNodes,
            Map<String, Integer> eventTypeCounts,
            int errorWindowSeconds,
            int recentErrorCount,
            Map<String, Integer> recentErrorsByNodeType,
            Map<String, Integer> errorsByAction,
            int chatSessionEvents,
            int chatMessageEvents,
            long thoughtTokens,
            long streamTokens
    ) {
    }

    public record WorkflowNode(
            String nodeId,
            String parentNodeId,
            String title,
            String nodeType,
            String currentStatus,
            String actionName,
            String statusReason,
            int routeBackCount,
            Instant lastEventAt,
            int totalEvents,
            WorkflowNodeMetrics metrics,
            List<WorkflowNode> children
    ) {
    }

    public record WorkflowNodeMetrics(
            int nodeErrorCount,
            int chatSessionEvents,
            int chatMessageEvents,
            int thoughtDeltas,
            long thoughtTokens,
            int streamDeltas,
            long streamTokens,
            int toolEvents,
            int otherEvents,
            List<PendingItem> pendingItems
    ) {
    }

    public record PendingItem(
            String id,
            String nodeId,
            String type,
            String description
    ) {
    }

}
