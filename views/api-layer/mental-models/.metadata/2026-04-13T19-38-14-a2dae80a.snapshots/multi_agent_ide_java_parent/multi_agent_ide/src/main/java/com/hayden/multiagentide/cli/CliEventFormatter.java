package com.hayden.multiagentide.cli;

import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.filter.integration.ControllerEventFilterIntegration;
import com.hayden.multiagentide.filter.integration.PathFilterIntegration;
import com.hayden.multiagentide.filter.service.LayerIdResolver;
import com.hayden.multiagentide.filter.model.FilterSource;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.agent.AgentPretty;
import com.hayden.multiagentide.filter.model.layer.GraphEventObjectContext;
import com.hayden.multiagentide.filter.model.layer.DefaultPathFilterContext;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class CliEventFormatter {

    private static final int MAX_FIELD_LENGTH = 160;
    private final ArtifactKeyFormatter artifactKeyFormatter;
    private final LayerIdResolver layerIdResolver;
    private final ControllerEventFilterIntegration controllerEventFilterIntegration;
    private final PathFilterIntegration pathFilterIntegration;

    @Autowired
    public CliEventFormatter(ArtifactKeyFormatter artifactKeyFormatter,
                             LayerIdResolver layerIdResolver,
                             ControllerEventFilterIntegration controllerEventFilterIntegration,
                             PathFilterIntegration pathFilterIntegration) {
        this.artifactKeyFormatter = artifactKeyFormatter;
        this.layerIdResolver = layerIdResolver;
        this.controllerEventFilterIntegration = controllerEventFilterIntegration;
        this.pathFilterIntegration = pathFilterIntegration;
    }

    public CliEventFormatter(ArtifactKeyFormatter artifactKeyFormatter) {
        this(artifactKeyFormatter, null, null, null);
    }

    public String format(Events.GraphEvent event) {
        return format(new CliEventArgs(MAX_FIELD_LENGTH, event));
    }

    public String format(CliEventArgs args) {
        if (args == null) {
            return "[EVENT] null";
        }
        Events.GraphEvent rawEvent = args.graphEvent();
        if (rawEvent == null) {
            return "[EVENT] null";
        }
        Events.GraphEvent event = filterGraphEvent(args, rawEvent);
        if (event == null) {
            return "[EVENT] filtered";
        }
        CliEventArgs normalizedArgs = normArgs(args, event);
        return switch (event) {
                case Events.ActionStartedEvent e -> formatActionStarted(normalizedArgs, e);
                case Events.ActionCompletedEvent e -> formatActionCompleted(normalizedArgs, e);
                case Events.ToolCallEvent e -> formatToolCall(normalizedArgs, e);
                case Events.NodeAddedEvent e -> formatNodeAdded(normalizedArgs, e);
                case Events.AddChildNodeEvent e -> formatAddChildNode(normalizedArgs, e);
                case Events.NodeStatusChangedEvent e -> formatNodeStatusChanged(normalizedArgs, e);
                case Events.NodeErrorEvent e -> formatNodeError(normalizedArgs, e);
                case Events.NodeBranchedEvent e -> formatNodeBranched(normalizedArgs, e);
                case Events.NodePrunedEvent e -> formatNodePruned(normalizedArgs, e);
                case Events.NodeReviewRequestedEvent e -> formatNodeReviewRequested(normalizedArgs, e);
                case Events.InterruptStatusEvent e -> formatInterruptStatus(normalizedArgs, e);
                case Events.PauseEvent e -> format(normalizedArgs, "INTERRUPT", e, "message=" + summarize(normalizedArgs, e.toAddMessage()));
                case Events.ResumeEvent e -> format(normalizedArgs, "INTERRUPT", e, "message=" + summarize(normalizedArgs, e.message()));
                case Events.ResolveInterruptEvent e -> format(normalizedArgs, "INTERRUPT", e, "message=" + summarize(normalizedArgs, e.toAddMessage()));
                case Events.StopAgentEvent e -> format(normalizedArgs, "ACTION", e, "node=" + e.nodeId());
                case Events.AddMessageEvent e -> format(normalizedArgs, "MESSAGE", e, "message=" + summarize(normalizedArgs, e.toAddMessage()));
                case Events.InterruptRequestEvent e -> format(normalizedArgs, "INTERRUPT", e,
                        "source=" + summarize(normalizedArgs, e.sourceAgentType())
                                + " reroute=" + summarize(normalizedArgs, e.rerouteToAgentType())
                                + " reason=" + summarize(normalizedArgs, e.reason()));
                case Events.WorktreeCreatedEvent e -> formatWorktreeCreated(normalizedArgs, e);
                case Events.WorktreeBranchedEvent e -> formatWorktreeBranched(normalizedArgs, e);
                case Events.WorktreeMergedEvent e -> formatWorktreeMerged(normalizedArgs, e);
                case Events.WorktreeDiscardedEvent e -> formatWorktreeDiscarded(normalizedArgs, e);
                case Events.NodeUpdatedEvent e -> format(normalizedArgs, "NODE", e, "updates=" + countOf(e.updates()));
                case Events.NodeDeletedEvent e -> format(normalizedArgs, "NODE", e, "reason=" + summarize(normalizedArgs, e.reason()));
                case Events.ChatSessionCreatedEvent e -> format(normalizedArgs, "CHAT", e, "nodeId=" + summarize(normalizedArgs, e.nodeId()));
                case Events.ChatSessionClosedEvent e -> format(normalizedArgs, "CHAT", e, "sessionId=" + summarize(normalizedArgs, e.sessionId()));
                case Events.ChatSessionResetEvent e -> format(normalizedArgs, "CHAT", e, "sessionId=" + summarize(normalizedArgs, e.sessionId()));
                case Events.AiFilterSessionEvent e -> format(normalizedArgs, "AI_FILTER", e,
                        "policyId=" + summarize(normalizedArgs, e.policyId())
                                + " mode=" + summarize(normalizedArgs, e.sessionMode())
                                + " scope=" + summarize(normalizedArgs, e.scopeNodeId())
                                + " sessionId=" + summarize(normalizedArgs, e.sessionContextId() == null ? null : e.sessionContextId().value()));
                case Events.NodeStreamDeltaEvent e -> format(normalizedArgs, "STREAM", e, "tokens=" + e.tokenCount() + " final=" + e.isFinal());
                case Events.NodeThoughtDeltaEvent e -> format(normalizedArgs, "THOUGHT", e, "tokens=" + e.tokenCount() + " final=" + e.isFinal());
                case Events.GuiRenderEvent e -> format(normalizedArgs, "UI", e, "sessionId=" + e.sessionId());
                case Events.UiDiffAppliedEvent e -> format(normalizedArgs, "UI", e, "revision=" + e.revision() + " summary=" + summarize(normalizedArgs, e.summary()));
                case Events.UiDiffRejectedEvent e -> format(normalizedArgs, "UI", e, "error=" + summarize(normalizedArgs, e.errorCode()) + " message=" + summarize(normalizedArgs, e.message()));
                case Events.UiDiffRevertedEvent e -> format(normalizedArgs, "UI", e, "revision=" + e.revision() + " source=" + summarize(normalizedArgs, e.sourceEventId()));
                case Events.UiFeedbackEvent e -> format(normalizedArgs, "UI", e, "message=" + summarize(normalizedArgs, e.message()));
                case Events.NodeBranchRequestedEvent e -> format(normalizedArgs, "NODE", e, "message=" + summarize(normalizedArgs, e.message()));
                case Events.PlanUpdateEvent e -> format(normalizedArgs, "PLAN", e, "entries=" + countOf(e.entries()));
                case Events.UserMessageChunkEvent e -> format(normalizedArgs, "MESSAGE", e, "content=" + summarize(normalizedArgs, e.content()));
                case Events.CurrentModeUpdateEvent e -> format(normalizedArgs, "MODE", e, "mode=" + summarize(normalizedArgs, e.currentModeId()));
                case Events.AvailableCommandsUpdateEvent e -> format(normalizedArgs, "MODE", e, "commands=" + countOf(e.commands()));
                case Events.PermissionRequestedEvent e -> format(normalizedArgs, "PERMISSION", e, "requestId=" + e.requestId() + " toolCallId=" + e.toolCallId());
                case Events.PermissionResolvedEvent e -> format(normalizedArgs, "PERMISSION", e, "requestId=" + e.requestId() + " outcome=" + summarize(normalizedArgs, e.outcome()));
                case Events.GoalStartedEvent e -> format(normalizedArgs, "GOAL", e,
                        "title=" + summarize(normalizedArgs, e.title())
                                + " tags=" + countOf(e.tags()));
                case Events.GoalCompletedEvent e -> format(normalizedArgs, "GOAL", e, "workflowId=" + summarize(normalizedArgs, e.workflowId()));
                case Events.ArtifactEvent e -> formatArtifactEvent(normalizedArgs, e);
                case Events.TuiInteractionGraphEvent e -> format(normalizedArgs, "TUI", e, "sessionId=" + summarize(normalizedArgs, e.sessionId())
                        + " event=" + summarize(normalizedArgs, e.tuiEvent() == null ? null : e.tuiEvent().getClass().getSimpleName()));
                case Events.TuiSystemGraphEvent e -> format(normalizedArgs, "TUI", e, "sessionId=" + summarize(normalizedArgs, e.sessionId())
                        + " event=" + summarize(normalizedArgs, e.tuiEvent() == null ? null : e.tuiEvent().getClass().getSimpleName()));
                case Events.MergePhaseStartedEvent e -> format(normalizedArgs, "MERGE", e, "direction=" + e.mergeDirection()
                        + " trunk=" + summarize(normalizedArgs, e.trunkWorktreeId()) + " child=" + summarize(normalizedArgs, e.childWorktreeId())
                        + " children=" + e.childCount());
                case Events.MergePhaseCompletedEvent e -> format(normalizedArgs, "MERGE", e, "direction=" + e.mergeDirection()
                        + " successful=" + e.successful() + " merged=" + e.mergedCount()
                        + " conflicts=" + e.conflictCount());
                case Events.PropagationEvent e -> format(normalizedArgs, "PROPAGATION", e, "stage=" + e.stage()
                        + " action=" + e.action()
                        + " source=" + summarize(normalizedArgs, e.sourceName())
                        + " payloadType=" + summarize(normalizedArgs, e.payloadType()));
                case Events.TransformationEvent e -> format(normalizedArgs, "TRANSFORMATION", e, "action=" + e.action()
                        + " controller=" + summarize(normalizedArgs, e.controllerId())
                        + " endpoint=" + summarize(normalizedArgs, e.endpointId()));
                case Events.CompactionEvent e -> format(normalizedArgs, "COMPACTION", e, "message=" + summarize(normalizedArgs, e.message()));
                case Events.AgentCallStartedEvent e -> format(normalizedArgs, "AGENT_CALL", e,
                        "caller=" + summarize(normalizedArgs, e.callerNodeId())
                                + " target=" + summarize(normalizedArgs, e.targetNodeId())
                                + " callId=" + summarize(normalizedArgs, e.callId()));
                case Events.AgentCallCompletedEvent e -> format(normalizedArgs, "AGENT_CALL", e,
                        "callId=" + summarize(normalizedArgs, e.callId()));
                case Events.AgentCallEvent e -> format(normalizedArgs, "AGENT_CALL", e,
                        (e.callEventType() != null ? e.callEventType().name() : "")
                                + " caller=" + summarize(normalizedArgs, e.callerSessionId())
                                + " target=" + summarize(normalizedArgs, e.targetSessionId()));
                case Events.PromptReceivedEvent e -> format(normalizedArgs, "PROMPT_RECEIVED", e,
                        "agent=" + summarize(normalizedArgs, e.agentType())
                                + " template=" + summarize(normalizedArgs, e.templateName())
                                + " prompt=" + summarize(normalizedArgs, e.assembledPrompt()));
                case Events.AgentExecutorStartEvent e -> format(normalizedArgs, "EXECUTOR", e,
                        "action=" + summarize(normalizedArgs, e.actionName()) + " session=" + summarize(normalizedArgs, e.sessionKey())
                                + " requestCtx=" + summarize(normalizedArgs, e.requestContextId()));
                case Events.AgentExecutorCompleteEvent e -> format(normalizedArgs, "EXECUTOR", e,
                        "action=" + summarize(normalizedArgs, e.actionName()) + " session=" + summarize(normalizedArgs, e.sessionKey())
                                + " requestCtx=" + summarize(normalizedArgs, e.requestContextId())
                                + " startNodeId=" + summarize(normalizedArgs, e.startNodeId()));
                case Events.NullResultEvent e -> format(normalizedArgs, "NULL_RESULT", e,
                        "session=" + summarize(normalizedArgs, e.sessionKey()) + " retry=" + e.retryCount() + "/" + e.maxRetries());
                case Events.IncompleteJsonEvent e -> format(normalizedArgs, "INCOMPLETE_JSON", e,
                        "session=" + summarize(normalizedArgs, e.sessionKey()) + " retry=" + e.retryCount());
                case Events.UnparsedToolCallEvent e -> format(normalizedArgs, "UNPARSED_TOOL_CALL", e,
                        "session=" + summarize(normalizedArgs, e.sessionKey()) + " text=" + summarize(normalizedArgs, e.toolCallText()));
                case Events.TimeoutEvent e -> format(normalizedArgs, "TIMEOUT", e,
                        "session=" + summarize(normalizedArgs, e.sessionKey()) + " retry=" + e.retryCount());
                case Events.ParseErrorEvent e -> format(normalizedArgs, "PARSE_ERROR", e,
                        "session=" + summarize(normalizedArgs, e.sessionKey())
                                + " action=" + summarize(normalizedArgs, e.actionName())
                                + " rawOutput=" + summarize(normalizedArgs, e.rawOutput()));
            };
    }

    private @NonNull CliEventArgs normArgs(CliEventArgs args, Events.GraphEvent event) {
        int maxFieldLength = args.maxFieldLength();
        if (maxFieldLength >= 0) {
            if (event instanceof Events.PropagationEvent propagationEvent) {
                maxFieldLength = Math.max(maxFieldLength, propagationEvent.prettyPrint().length());
            }
        }
        return maxFieldLength == args.maxFieldLength() && event == args.graphEvent()
                ? args
                : new CliEventArgs(maxFieldLength, event, args.prettyPrint(), args.layerId());
    }

    public record CliEventArgs(int maxFieldLength, Events.GraphEvent graphEvent, boolean prettyPrint, String layerId) {
        public CliEventArgs(int maxFieldLength, Events.GraphEvent graphEvent) {
            this(maxFieldLength, graphEvent, false, null);
        }

        public CliEventArgs(int maxFieldLength, Events.GraphEvent graphEvent, boolean prettyPrint) {
            this(maxFieldLength, graphEvent, prettyPrint, null);
        }
    }

    private String formatActionStarted(CliEventArgs args, Events.ActionStartedEvent event) {
        String hierarchy = artifactKeyFormatter.formatHierarchy(event.nodeId());
        String details = "agent=" + event.agentName()
                + " action=" + event.actionName()
                + " " + hierarchy;
        return format(args, "ACTION", event, details);
    }

    private String formatActionCompleted(CliEventArgs args, Events.ActionCompletedEvent event) {
        String hierarchy = artifactKeyFormatter.formatHierarchy(event.nodeId());
        String details = "agent=" + event.agentName()
                + " action=" + event.actionName()
                + " outcome=" + summarize(args, event.outcomeType())
                + " " + hierarchy;
        return format(args, "ACTION", event, details);
    }

    private String formatToolCall(CliEventArgs args, Events.ToolCallEvent event) {
        ToolCallRenderer renderer = ToolCallRendererFactory.rendererFor(event);
        if (args.prettyPrint()) {
            String header = "[TOOL] " + event.eventType() + " node=" + summarize(args, event.nodeId());
            return header + "\n" + indent(renderer.formatDetail(args, event), 1);
        }
        String details = renderer.formatSummary(args, event);
        return format(args, "TOOL", event, details);
    }

    private String formatNodeAdded(CliEventArgs args, Events.NodeAddedEvent event) {
        String details = "title=" + summarize(args, event.nodeTitle())
                + " type=" + event.nodeType()
                + " parent=" + summarize(args, event.parentNodeId());
        return format(args, "NODE", event, details);
    }

    private String formatAddChildNode(CliEventArgs args, Events.AddChildNodeEvent event) {
        String details = "child=" + summarize(args, event.nodeId())
                + " parent=" + summarize(args, event.parentNodeId());
        return format(args, "NODE", event, details);
    }

    private String formatNodeStatusChanged(CliEventArgs args, Events.NodeStatusChangedEvent event) {
        String details = "from=" + event.oldStatus() + " to=" + event.newStatus()
                + " reason=" + summarize(args, event.reason());
        return format(args, "NODE", event, details);
    }

    private String formatNodeError(CliEventArgs args, Events.NodeErrorEvent event) {
        String details = "title=" + summarize(args, event.nodeTitle())
                + " type=" + event.nodeType()
                + " message=" + summarize(args, event.message());
        return format(args, "NODE", event, details);
    }

    private String formatNodeBranched(CliEventArgs args, Events.NodeBranchedEvent event) {
        String details = "original=" + summarize(args, event.originalNodeId())
                + " branched=" + summarize(args, event.branchedNodeId())
                + " goal=" + summarize(args, event.newGoal());
        return format(args, "NODE", event, details);
    }

    private String formatNodePruned(CliEventArgs args, Events.NodePrunedEvent event) {
        String details = "reason=" + summarize(args, event.reason())
                + " worktrees=" + countOf(event.pruneWorktreeIds());
        return format(args, "NODE", event, details);
    }

    private String formatNodeReviewRequested(CliEventArgs args, Events.NodeReviewRequestedEvent event) {
        String details = "reviewNode=" + summarize(args, event.reviewNodeId())
                + " type=" + event.reviewType();
        return format(args, "REVIEW", event, details);
    }

    private String formatInterruptStatus(CliEventArgs args, Events.InterruptStatusEvent event) {
        String details = "type=" + summarize(args, event.interruptType())
                + " status=" + summarize(args, event.interruptStatus())
                + " origin=" + summarize(args, event.originNodeId());
        return format(args, "INTERRUPT", event, details);
    }

    private String formatWorktreeCreated(CliEventArgs args, Events.WorktreeCreatedEvent event) {
        String details = "worktreeId=" + summarize(args, event.worktreeId())
                + " path=" + summarize(args, event.worktreePath())
                + " type=" + summarize(args, event.worktreeType())
                + " submodule=" + summarize(args, event.submoduleName());
        return format(args, "WORKTREE", event, details);
    }

    private String formatWorktreeBranched(CliEventArgs args, Events.WorktreeBranchedEvent event) {
        String details = "branch=" + summarize(args, event.branchName())
                + " original=" + summarize(args, event.originalWorktreeId())
                + " branched=" + summarize(args, event.branchedWorktreeId());
        return format(args, "WORKTREE", event, details);
    }

    private String formatWorktreeMerged(CliEventArgs args, Events.WorktreeMergedEvent event) {
        String details = "child=" + summarize(args, event.childWorktreeId())
                + " parent=" + summarize(args, event.parentWorktreeId());
        return format(args, "WORKTREE", event, details);
    }

    private String formatWorktreeDiscarded(CliEventArgs args, Events.WorktreeDiscardedEvent event) {
        String details = "worktreeId=" + summarize(args, event.worktreeId())
                + " type=" + summarize(args, event.worktreeType());
        return format(args, "WORKTREE", event, details);
    }

    private String formatArtifactEvent(CliEventArgs args, Events.ArtifactEvent event) {

        Artifact artifact = event.artifact();
        if (artifact instanceof Artifact.AgentModelArtifact agentModelArtifact)
            return formatAgentModelArtifact(args, event, agentModelArtifact);

        String details = "type=" + summarize(args, event.artifactType())
                + " key=" + event.artifactKey()
                + " artifact=" + summarize(args, artifact == null ? null : artifact.getClass().getSimpleName());
        return format(args, "ARTIFACT", event, details);
    }

    private String formatAgentModelArtifact(CliEventArgs args, Events.ArtifactEvent event, Artifact.AgentModelArtifact artifact) {
        String details = "type=" + summarize(args, event.artifactType())
                + " key=" + event.artifactKey()
                + " " + formatAgentModel(args, artifact.agentModel());
        return format(args, "ARTIFACT", event, details);
    }

    private String formatAgentModel(CliEventArgs args, Artifact.AgentModel model) {
        if (model == null) {
            return "model=none";
        }

        if (args.prettyPrint && model instanceof AgentPretty c)
            return c.prettyPrint();

        return switch (model) {
            case AgentModels.InterruptRequest interrupt -> "interrupt=" + formatInterruptRequest(args, interrupt);
            case AgentModels.AgentRequest request -> "request=" + formatAgentRequest(args, request);
            case AgentModels.AgentResult result -> "result=" + formatAgentResult(args, result);
            case AgentPretty context -> "context=" + formatAgentContext(args, context);
            default -> "model=" + summarize(args, model.getClass().getSimpleName());
        };
    }

    private String formatAgentRequest(CliEventArgs args, AgentModels.AgentRequest request) {
        String summary = summarize(args, request.prettyPrintInterruptContinuation());
        return switch (request) {
            case AgentModels.OrchestratorRequest r ->
                    "OrchestratorRequest goal=" + summarize(args, r.goal())
                            + " phase=" + summarize(args, r.phase())
                            + " summary=" + summary;
            case AgentModels.OrchestratorCollectorRequest r ->
                    "OrchestratorCollectorRequest goal=" + summarize(args, r.goal())
                            + " phase=" + summarize(args, r.phase())
                            + " summary=" + summary;
            case AgentModels.DiscoveryOrchestratorRequest r ->
                    "DiscoveryOrchestratorRequest goal=" + summarize(args, r.goal())
                            + " summary=" + summary;
            case AgentModels.DiscoveryAgentRequests r ->
                    "DiscoveryAgentRequests goal=" + summarize(args, r.goal())
                            + " requests=" + countOf(r.requests())
                            + " summary=" + summary;
            case AgentModels.DiscoveryAgentRequest r ->
                    "DiscoveryAgentRequest goal=" + summarize(args, r.goal())
                            + " subdomain=" + summarize(args, r.subdomainFocus())
                            + " summary=" + summary;
            case AgentModels.DiscoveryCollectorRequest r ->
                    "DiscoveryCollectorRequest goal=" + summarize(args, r.goal())
                            + " summary=" + summary;
            case AgentModels.PlanningOrchestratorRequest r ->
                    "PlanningOrchestratorRequest goal=" + summarize(args, r.goal())
                            + " summary=" + summary;
            case AgentModels.PlanningAgentRequests r ->
                    "PlanningAgentRequests goal=" + summarize(args, r.goal())
                            + " requests=" + countOf(r.requests())
                            + " summary=" + summary;
            case AgentModels.PlanningAgentRequest r ->
                    "PlanningAgentRequest goal=" + summarize(args, r.goal())
                            + " summary=" + summary;
            case AgentModels.PlanningCollectorRequest r ->
                    "PlanningCollectorRequest goal=" + summarize(args, r.goal())
                            + " summary=" + summary;
            case AgentModels.TicketOrchestratorRequest r ->
                    "TicketOrchestratorRequest goal=" + summarize(args, r.goal())
                            + " summary=" + summary;
            case AgentModels.TicketAgentRequests r ->
                    "TicketAgentRequests goal=" + summarize(args, r.goal())
                            + " requests=" + countOf(r.requests())
                            + " summary=" + summary;
            case AgentModels.TicketAgentRequest r ->
                    "TicketAgentRequest ticket=" + summarize(args, r.ticketDetails())
                            + " path=" + summarize(args, r.ticketDetailsFilePath())
                            + " summary=" + summary;
            case AgentModels.CommitAgentRequest r ->
                    "CommitAgentRequest sourceAgentType=" + summarize(args, r.sourceAgentType())
                            + " summary=" + summary;
            case AgentModels.MergeConflictRequest r ->
                    "MergeConflictRequest direction=" + summarize(args, r.mergeDirection())
                            + " conflicts=" + summarize(args, r.conflictFiles())
                            + " summary=" + summary;
            case AgentModels.TicketCollectorRequest r ->
                    "TicketCollectorRequest goal=" + summarize(args, r.goal())
                            + " summary=" + summary;
            case AgentModels.ContextManagerRoutingRequest r ->
                    "ContextManagerRoutingRequest type=" + summarize(args, r.type())
                            + " reason=" + summarize(args, r.reason())
                            + " summary=" + summary;
            case AgentModels.ContextManagerRequest r ->
                    "ContextManagerRequest type=" + summarize(args, r.type())
                            + " reason=" + summarize(args, r.reason())
                            + " goal=" + summarize(args, r.goal())
                            + " summary=" + summary;
            case AgentModels.ResultsRequest r ->
                    "ResultsRequest results=" + countOf(r.childResults())
                            + " summary=" + summary;
            case AgentModels.InterruptRequest r -> "InterruptRequest summary=" + summary;
            case AgentModels.AiFilterRequest aiFilterRequest ->
                    "AiFilterResult";
            case AgentModels.AiPropagatorRequest aiPropagatorRequest ->
                    "AiPropagatorRequest";
            case AgentModels.AiTransformerRequest aiTransformerRequest ->
                    "AiTransformerRequest";
            case AgentModels.AgentToAgentRequest r ->
                    "AgentToAgentRequest target=" + r.targetAgentType() + " summary=" + summary;
            case AgentModels.AgentToControllerRequest r ->
                    "AgentToControllerRequest source=" + r.sourceAgentType() + " summary=" + summary;
            case AgentModels.ControllerToAgentRequest r ->
                    "ControllerToAgentRequest target=" + r.targetAgentType() + " summary=" + summary;
        };
    }

    private String formatAgentResult(CliEventArgs args, AgentModels.AgentResult result) {
        String summary = summarize(args, result.prettyPrint());
        return switch (result) {
            case AgentModels.OrchestratorAgentResult r -> "OrchestratorAgentResult summary=" + summary;
            case AgentModels.OrchestratorCollectorResult r ->
                    "OrchestratorCollectorResult summary=" + summary;
            case AgentModels.DiscoveryOrchestratorResult r -> "DiscoveryOrchestratorResult summary=" + summary;
            case AgentModels.DiscoveryCollectorResult r ->
                    "DiscoveryCollectorResult summary=" + summary;
            case AgentModels.DiscoveryAgentResult r ->
                    "DiscoveryAgentResult children=" + countOf(r.children())
                            + " summary=" + summary;
            case AgentModels.PlanningOrchestratorResult r -> "PlanningOrchestratorResult summary=" + summary;
            case AgentModels.PlanningCollectorResult r ->
                    "PlanningCollectorResult summary=" + summary;
            case AgentModels.PlanningAgentResult r ->
                    "PlanningAgentResult children=" + countOf(r.children())
                            + " summary=" + summary;
            case AgentModels.TicketOrchestratorResult r -> "TicketOrchestratorResult summary=" + summary;
            case AgentModels.TicketCollectorResult r ->
                    "TicketCollectorResult summary=" + summary;
            case AgentModels.TicketAgentResult r ->
                    "TicketAgentResult summary=" + summary;
            case AgentModels.CommitAgentResult r ->
                    "CommitAgentResult successful=" + r.successful()
                            + " commits=" + countOf(r.commitMetadata())
                            + " summary=" + summary;
            case AgentModels.MergeConflictResult r ->
                    "MergeConflictResult successful=" + r.successful()
                            + " resolved=" + countOf(r.resolvedConflictFiles())
                            + " summary=" + summary;
            case AgentModels.ReviewAgentResult r ->
                    "ReviewAgentResult summary=" + summary;
            case AgentModels.MergerAgentResult r ->
                    "MergerAgentResult summary=" + summary;
            case AgentModels.AiFilterResult r ->
                    "AiFilterResult " + summary;
            case AgentModels.AiPropagatorResult r ->
                    "AiPropagatorResult " + summary;
            case AgentModels.AiTransformerResult r ->
                    "AiTransformerResult " + summary;
            case AgentModels.AgentCallResult r ->
                    "AgentCallResult " + summary;
            case AgentModels.ControllerCallResult r ->
                    "ControllerCallResult " + summary;
            case AgentModels.ControllerResponseResult r ->
                    "ControllerResponseResult " + summary;
        };
    }

    private String formatAgentContext(CliEventArgs args, AgentPretty context) {
        String summary = summarize(args, context.prettyPrint());
        return switch (context) {
            case AgentModels.DiscoveryCuration c ->
                    "DiscoveryCuration reports=" + countOf(c.discoveryReports())
                            + " recommendations=" + countOf(c.recommendations())
                            + " summary=" + summary;
            case AgentModels.PlanningCuration c ->
                    "PlanningCuration tickets=" + countOf(c.finalizedTickets())
                            + " results=" + countOf(c.planningAgentResults())
                            + " summary=" + summary;
            case AgentModels.TicketCuration c ->
                    "TicketCuration results=" + countOf(c.ticketAgentResults())
                            + " followUps=" + countOf(c.followUps())
                            + " summary=" + summary;
            default -> """
                    %s
                    Context summary=%s
                    """.formatted(context.getClass().getSimpleName(), summary);
        };
    }

    private String formatInterruptRequest(CliEventArgs args, AgentModels.InterruptRequest request) {
        String base = "type=" + summarize(args, request.type())
                + " reason=" + summarize(args, request.reason())
                + " context=" + summarize(args, request.contextForDecision())
                + " choices=" + countOf(request.choices())
                + " confirmations=" + countOf(request.confirmationItems());
        return switch (request) {
            case AgentModels.InterruptRequest.OrchestratorInterruptRequest r ->
                    "OrchestratorInterruptRequest " + base
                            + " phase=" + summarize(args, r.phase())
                            + " goal=" + summarize(args, r.goal());
            case AgentModels.InterruptRequest.OrchestratorCollectorInterruptRequest r ->
                    "OrchestratorCollectorInterruptRequest " + base
                            + " phaseOptions=" + countOf(r.phaseOptions());
            case AgentModels.InterruptRequest.DiscoveryOrchestratorInterruptRequest r ->
                    "DiscoveryOrchestratorInterruptRequest " + base
                            + " scope=" + summarize(args, r.scope())
                            + " subdomains=" + countOf(r.subdomainPartitioning());
            case AgentModels.InterruptRequest.DiscoveryAgentInterruptRequest r ->
                    "DiscoveryAgentInterruptRequest " + base
                            + " codeFindings=" + summarize(args, r.codeFindings())
                            + " files=" + countOf(r.fileReferences());
            case AgentModels.InterruptRequest.DiscoveryCollectorInterruptRequest r ->
                    "DiscoveryCollectorInterruptRequest " + base
                            + " consolidation=" + summarize(args, r.consolidationDecisions())
                            + " recommendations=" + countOf(r.recommendations());
            case AgentModels.InterruptRequest.DiscoveryAgentDispatchInterruptRequest r ->
                    "DiscoveryAgentDispatchInterruptRequest " + base
                            + " assignments=" + countOf(r.agentAssignments())
                            + " routing=" + summarize(args, r.routingRationale());
            case AgentModels.InterruptRequest.PlanningOrchestratorInterruptRequest r ->
                    "PlanningOrchestratorInterruptRequest " + base
                            + " ticketDecomposition=" + summarize(args, r.ticketDecomposition())
                            + " scope=" + summarize(args, r.planningScope());
            case AgentModels.InterruptRequest.PlanningAgentInterruptRequest r ->
                    "PlanningAgentInterruptRequest " + base
                            + " ticketDesign=" + summarize(args, r.ticketDesign())
                            + " proposedTickets=" + countOf(r.proposedTickets());
            case AgentModels.InterruptRequest.PlanningCollectorInterruptRequest r ->
                    "PlanningCollectorInterruptRequest " + base
                            + " ticketConsolidation=" + summarize(args, r.ticketConsolidation())
                            + " consolidatedTickets=" + countOf(r.consolidatedTickets());
            case AgentModels.InterruptRequest.PlanningAgentDispatchInterruptRequest r ->
                    "PlanningAgentDispatchInterruptRequest " + base
                            + " assignments=" + countOf(r.agentAssignments())
                            + " routing=" + summarize(args, r.routingRationale());
            case AgentModels.InterruptRequest.TicketOrchestratorInterruptRequest r ->
                    "TicketOrchestratorInterruptRequest " + base
                            + " scope=" + summarize(args, r.implementationScope())
                            + " strategy=" + summarize(args, r.executionStrategy());
            case AgentModels.InterruptRequest.TicketAgentInterruptRequest r ->
                    "TicketAgentInterruptRequest " + base
                            + " approach=" + summarize(args, r.implementationApproach())
                            + " files=" + countOf(r.filesToModify());
            case AgentModels.InterruptRequest.TicketCollectorInterruptRequest r ->
                    "TicketCollectorInterruptRequest " + base
                            + " status=" + summarize(args, r.completionStatus())
                            + " followUps=" + countOf(r.followUps());
            case AgentModels.InterruptRequest.TicketAgentDispatchInterruptRequest r ->
                    "TicketAgentDispatchInterruptRequest " + base
                            + " assignments=" + countOf(r.agentAssignments())
                            + " routing=" + summarize(args, r.routingRationale());
            case AgentModels.InterruptRequest.ContextManagerInterruptRequest r ->
                    "ContextManagerInterruptRequest " + base
                            + " findings=" + summarize(args, r.contextFindings())
                            + " sources=" + countOf(r.sourceReferences());
            case AgentModels.InterruptRequest.QuestionAnswerInterruptRequest r ->
                    "QuestionAnswerInterruptRequest " + base;
        };
    }

    private String format(CliEventArgs args, String category, Events.GraphEvent event, String details) {
        String prefix = "[" + category + "]";
        String header = prefix + " " + event.eventType() + " node=" + summarize(args, event.nodeId());
        String payload = serializeEvent(args, event);
        if (args.prettyPrint()) {
            return header
                    + "\n\tDetails: " + details
                    + "\n\tPayload:\n"
                    + indent(payload, 2);
        }
        return header
                + " " + details
                + " payload=" + summarize(args, payload.replace('\n', ' '));
    }

    private String serializeEvent(CliEventArgs args, Events.GraphEvent event) {
        String pretty = event.prettyPrint();
        if (pretty == null || pretty.isBlank()) {
            pretty = summarize(args, event.eventType());
        }
        String layerId = resolveLayerId(args, event);
        if (layerId == null || pathFilterIntegration == null) {
            return pretty;
        }
        return pathFilterIntegration.applyTextPathFilters(
                        layerId,
                        FilterSource.graphEvent(event),
                        pretty,
                        new DefaultPathFilterContext(layerId, new GraphEventObjectContext(layerId, event))

                )
                .t();
    }

    private Events.GraphEvent filterGraphEvent(CliEventArgs args, Events.GraphEvent event) {
        if (event == null || controllerEventFilterIntegration == null) {
            return event;
        }
        String layerId = resolveLayerId(args, event);
        if (layerId == null) {
            return event;
        }
        return controllerEventFilterIntegration.applyFilters(layerId, event).t();
    }

    private String resolveLayerId(CliEventArgs args, Events.GraphEvent event) {
        if (args != null && args.layerId() != null && !args.layerId().isBlank()) {
            return args.layerId();
        }
        if (layerIdResolver == null) {
            return null;
        }
        return layerIdResolver.resolveForGraphEvent(event).orElse(null);
    }

    private String summarize(CliEventArgs args, Object value) {
        if (value == null) {
            return "none";
        }
        String text = String.valueOf(value);
        if (args.maxFieldLength() < 0) {
            return text;
        }
        int maxFieldLength = Math.max(0, args.maxFieldLength());
        if (text.length() <= maxFieldLength) {
            return text;
        }
        return text.substring(0, maxFieldLength) + "...";
    }

    private String indent(String text, int depth) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String prefix = "\t".repeat(Math.max(0, depth));
        return text.replace("\r\n", "\n")
                .replace("\r", "\n")
                .lines()
                .map(line -> prefix + line)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    private int countOf(List<?> items) {
        return items == null ? 0 : items.size();
    }

    private int countOf(Map<?, ?> items) {
        return items == null ? 0 : items.size();
    }
}
