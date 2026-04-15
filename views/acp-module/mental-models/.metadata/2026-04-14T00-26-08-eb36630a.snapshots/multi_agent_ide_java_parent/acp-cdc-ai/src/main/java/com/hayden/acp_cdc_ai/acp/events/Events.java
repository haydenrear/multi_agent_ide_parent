package com.hayden.acp_cdc_ai.acp.events;

import com.agui.core.types.BaseEvent;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.hayden.acp_cdc_ai.acp.filter.FilteredObject;
import lombok.Builder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public interface Events {

    Logger log = LoggerFactory.getLogger(Events.class);

    static BaseEvent mapToEvent(GraphEvent toMap) {
        if (toMap == null) {
            log.warn("Skipping null GraphEvent mapping.");
            return null;
        }
        return AgUiEventMappingRegistry.map(toMap);
    }

    enum ReviewType {
        AGENT, HUMAN;

        public InterruptType toInterruptType() {
            return this == AGENT ? InterruptType.AGENT_REVIEW : InterruptType.HUMAN_REVIEW;
        }
    }

    /**
     * Node status values.
     */
    enum NodeStatus {
        PENDING,           // Not yet ready
        READY,             // Ready to execute
        RUNNING,           // Currently executing
        WAITING_REVIEW,    // Awaiting human/agent review
        WAITING_INPUT,     // Awaiting user input
        COMPLETED,         // Successfully completed
        FAILED,            // Execution failed
        CANCELED,          // Manually canceled
        PRUNED,            // Removed from graph
    }

    /**
     * Node type for classification.
     */
    enum NodeType {
        ORCHESTRATOR,
        PLANNING,
        WORK,
        HUMAN_REVIEW,
        AGENT_REVIEW,
        SUMMARY,
        INTERRUPT,
        PERMISSION,
        AGENT_TO_AGENT_CONVERSATION,
        AGENT_TO_CONTROLLER_CONVERSATION,
        CONTROLLER_TO_AGENT_CONVERSATION,
        DATA_LAYER_OPERATION
    }

    enum InterruptType {
        HUMAN_REVIEW,
        AGENT_REVIEW,
        PAUSE
    }

    @JsonClassDescription("""
            Decision type (ADVANCE_PHASE, ROUTE_BACK, STOP).
            ADVANCE_PHASE: advance to the next orchestrator.
                Discovery -> Planning
                Planning -> Ticket
                Ticket -> OrchestratorCollector (finish)
            ROUTE_BACK: route back to the orchestrator
                DiscoveryCollector -> DiscoveryOrchestrator
                PlanningCollector -> PlanningOrchestrator
                TicketCollector -> TicketOrchestrator
            STOP: route to the OrchestratorCollector, no matter what stage of execution
            """)
    enum CollectorDecisionType {
        ROUTE_BACK,
        ADVANCE_PHASE,
        STOP
    }

    /**
     * Base interface for all graph and worktree events.
     * Sealed to restrict implementations.
     */
    @JsonTypeInfo(
            use = JsonTypeInfo.Id.NAME,
            include = JsonTypeInfo.As.EXISTING_PROPERTY,
            property = "eventType",
            visible = false
    )
    @JsonSubTypes({
            @JsonSubTypes.Type(value = NodeAddedEvent.class, name = "NODE_ADDED"),
            @JsonSubTypes.Type(value = AddChildNodeEvent.class, name = "ADD_CHILD_NODE"),
            @JsonSubTypes.Type(value = ActionStartedEvent.class, name = "ACTION_STARTED"),
            @JsonSubTypes.Type(value = ActionCompletedEvent.class, name = "ACTION_COMPLETED"),
            @JsonSubTypes.Type(value = StopAgentEvent.class, name = "STOP_AGENT"),
            @JsonSubTypes.Type(value = PauseEvent.class, name = "PAUSE_EVENT"),
            @JsonSubTypes.Type(value = ResumeEvent.class, name = "RESUME_EVENT"),
            @JsonSubTypes.Type(value = ResolveInterruptEvent.class, name = "RESOLVE_INTERRUPT"),
            @JsonSubTypes.Type(value = AddMessageEvent.class, name = "ADD_MESSAGE_EVENT"),
            @JsonSubTypes.Type(value = InterruptRequestEvent.class, name = "INTERRUPT_REQUEST_EVENT"),
            @JsonSubTypes.Type(value = NodeStatusChangedEvent.class, name = "NODE_STATUS_CHANGED"),
            @JsonSubTypes.Type(value = NodeErrorEvent.class, name = "NODE_ERROR"),
            @JsonSubTypes.Type(value = CompactionEvent.class, name = "COMPACTION"),
            @JsonSubTypes.Type(value = NodeBranchedEvent.class, name = "NODE_BRANCHED"),
            @JsonSubTypes.Type(value = NodePrunedEvent.class, name = "NODE_PRUNED"),
            @JsonSubTypes.Type(value = NodeReviewRequestedEvent.class, name = "NODE_REVIEW_REQUESTED"),
            @JsonSubTypes.Type(value = InterruptStatusEvent.class, name = "INTERRUPT_STATUS"),
            @JsonSubTypes.Type(value = GoalStartedEvent.class, name = "GOAL_STARTED"),
            @JsonSubTypes.Type(value = GoalCompletedEvent.class, name = "GOAL_COMPLETED"),
            @JsonSubTypes.Type(value = WorktreeCreatedEvent.class, name = "WORKTREE_CREATED"),
            @JsonSubTypes.Type(value = WorktreeBranchedEvent.class, name = "WORKTREE_BRANCHED"),
            @JsonSubTypes.Type(value = WorktreeMergedEvent.class, name = "WORKTREE_MERGED"),
            @JsonSubTypes.Type(value = WorktreeDiscardedEvent.class, name = "WORKTREE_DISCARDED"),
            @JsonSubTypes.Type(value = NodeUpdatedEvent.class, name = "NODE_UPDATED"),
            @JsonSubTypes.Type(value = NodeDeletedEvent.class, name = "NODE_DELETED"),
            @JsonSubTypes.Type(value = ChatSessionCreatedEvent.class, name = "CHAT_SESSION_CREATED"),
            @JsonSubTypes.Type(value = ChatSessionClosedEvent.class, name = "CHAT_SESSION_CLOSED"),
            @JsonSubTypes.Type(value = ChatSessionResetEvent.class, name = "CHAT_SESSION_RESET"),
            @JsonSubTypes.Type(value = AiFilterSessionEvent.class, name = "AI_FILTER_SESSION"),
            @JsonSubTypes.Type(value = NodeStreamDeltaEvent.class, name = "NODE_STREAM_DELTA"),
            @JsonSubTypes.Type(value = NodeThoughtDeltaEvent.class, name = "NODE_THOUGHT_DELTA"),
            @JsonSubTypes.Type(value = ToolCallEvent.class, name = "TOOL_CALL"),
            @JsonSubTypes.Type(value = GuiRenderEvent.class, name = "GUI_RENDER"),
            @JsonSubTypes.Type(value = UiDiffAppliedEvent.class, name = "UI_DIFF_APPLIED"),
            @JsonSubTypes.Type(value = UiDiffRejectedEvent.class, name = "UI_DIFF_REJECTED"),
            @JsonSubTypes.Type(value = UiDiffRevertedEvent.class, name = "UI_DIFF_REVERTED"),
            @JsonSubTypes.Type(value = UiFeedbackEvent.class, name = "UI_FEEDBACK"),
            @JsonSubTypes.Type(value = NodeBranchRequestedEvent.class, name = "NODE_BRANCH_REQUESTED"),
            @JsonSubTypes.Type(value = PlanUpdateEvent.class, name = "PLAN_UPDATE"),
            @JsonSubTypes.Type(value = UserMessageChunkEvent.class, name = "USER_MESSAGE_CHUNK"),
            @JsonSubTypes.Type(value = CurrentModeUpdateEvent.class, name = "CURRENT_MODE_UPDATE"),
            @JsonSubTypes.Type(value = AvailableCommandsUpdateEvent.class, name = "AVAILABLE_COMMANDS_UPDATE"),
            @JsonSubTypes.Type(value = PermissionRequestedEvent.class, name = "PERMISSION_REQUESTED"),
            @JsonSubTypes.Type(value = PermissionResolvedEvent.class, name = "PERMISSION_RESOLVED"),
            @JsonSubTypes.Type(value = TuiInteractionGraphEvent.class, name = "TUI_INTERACTION"),
            @JsonSubTypes.Type(value = TuiSystemGraphEvent.class, name = "TUI_SYSTEM"),
            @JsonSubTypes.Type(value = MergePhaseStartedEvent.class, name = "MERGE_PHASE_STARTED"),
            @JsonSubTypes.Type(value = MergePhaseCompletedEvent.class, name = "MERGE_PHASE_COMPLETED"),
            @JsonSubTypes.Type(value = ArtifactEvent.class, name = "ARTIFACT_EMITTED"),
            @JsonSubTypes.Type(value = PropagationEvent.class, name = "PROPAGATION"),
            @JsonSubTypes.Type(value = TransformationEvent.class, name = "TRANSFORMATION"),
            @JsonSubTypes.Type(value = AgentCallStartedEvent.class, name = "AGENT_CALL_STARTED"),
            @JsonSubTypes.Type(value = AgentCallCompletedEvent.class, name = "AGENT_CALL_COMPLETED"),
            @JsonSubTypes.Type(value = AgentCallEvent.class, name = "AGENT_CALL"),
            @JsonSubTypes.Type(value = AgentExecutorStartEvent.class, name = "AGENT_EXECUTOR_START"),
            @JsonSubTypes.Type(value = AgentExecutorCompleteEvent.class, name = "AGENT_EXECUTOR_COMPLETE"),
            @JsonSubTypes.Type(value = PromptReceivedEvent.class, name = "PROMPT_RECEIVED"),
            @JsonSubTypes.Type(value = NullResultEvent.class, name = "NULL_RESULT"),
            @JsonSubTypes.Type(value = IncompleteJsonEvent.class, name = "INCOMPLETE_JSON"),
            @JsonSubTypes.Type(value = UnparsedToolCallEvent.class, name = "UNPARSED_TOOL_CALL"),
            @JsonSubTypes.Type(value = TimeoutEvent.class, name = "TIMEOUT"),
            @JsonSubTypes.Type(value = ParseErrorEvent.class, name = "PARSE_ERROR")
    })
    sealed interface GraphEvent extends FilteredObject, HasContextId {

        Logger log = LoggerFactory.getLogger(GraphEvent.class);

        @Override
        @JsonIgnore
        default ArtifactKey contextId() {
            var n = nodeId();

            try {
                return new ArtifactKey(n);
            } catch (Exception e) {
                log.error("Failed to parse node {} as artifact key.", n, e);
            }

            return ArtifactKey.createRoot();
        }

        /**
         * Unique event ID.
         */
        String eventId();

        String nodeId();

        /**
         * Timestamp when event was created.
         */
        Instant timestamp();

        /**
         * Type of event for classification.
         */
        @JsonProperty("eventType")
        String eventType();

        default String prettyPrint() {
            return switch (this) {
                case NodeAddedEvent e -> formatEvent("Node Added Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("nodeTitle", e.nodeTitle()),
                        line("nodeType", e.nodeType()),
                        line("parentNodeId", e.parentNodeId()));
                case AddChildNodeEvent e -> formatEvent("Add Child Node Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("parentNodeId", e.parentNodeId()));
                case ActionStartedEvent e -> formatEvent("Action Started Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("agentName", e.agentName()),
                        line("actionName", e.actionName()));
                case ActionCompletedEvent e -> formatEvent("Action Completed Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("agentName", e.agentName()),
                        line("actionName", e.actionName()),
                        line("outcomeType", e.outcomeType()),
                        line("agentModel", summarizeObject(e.agentModel())));
                case StopAgentEvent e -> formatEvent("Stop Agent Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()));
                case PauseEvent e -> formatEvent("Pause Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        block("toAddMessage", e.toAddMessage()));
                case ResumeEvent e -> formatEvent("Resume Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        block("message", e.message()));
                case AddMessageEvent e -> formatEvent("Add Message Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        block("toAddMessage", e.toAddMessage()));
                case InterruptRequestEvent e -> formatEvent("Interrupt Request Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("sourceAgentType", e.sourceAgentType()),
                        line("rerouteToAgentType", e.rerouteToAgentType()),
                        line("interruptType", e.interruptType()),
                        block("reason", e.reason()),
                        list("choices", e.choices()),
                        list("confirmationItems", e.confirmationItems()),
                        block("contextForDecision", e.contextForDecision()));
                case NodeStatusChangedEvent e -> formatEvent("Node Status Changed Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("oldStatus", e.oldStatus()),
                        line("newStatus", e.newStatus()),
                        block("reason", e.reason()));
                case NodeErrorEvent e -> formatEvent("Node Error Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("nodeTitle", e.nodeTitle()),
                        line("nodeType", e.nodeType()),
                        block("message", e.message()));
                case NodeBranchedEvent e -> formatEvent("Node Branched Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("originalNodeId", e.originalNodeId()),
                        line("branchedNodeId", e.branchedNodeId()),
                        block("newGoal", e.newGoal()),
                        line("mainWorktreeId", e.mainWorktreeId()),
                        list("submoduleWorktreeIds", e.submoduleWorktreeIds()));
                case NodePrunedEvent e -> formatEvent("Node Pruned Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        block("reason", e.reason()),
                        list("pruneWorktreeIds", e.pruneWorktreeIds()));
                case NodeReviewRequestedEvent e -> formatEvent("Node Review Requested Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("reviewNodeId", e.reviewNodeId()),
                        line("reviewType", e.reviewType()),
                        block("contentToReview", e.contentToReview()));
                case ResolveInterruptEvent e -> formatEvent("Interrupt Resolved Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("interruptType", e.interruptType),
                        block("resolution", e.toAddMessage));
                case InterruptStatusEvent e -> formatEvent("Interrupt Status Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("interruptType", e.interruptType()),
                        line("interruptStatus", e.interruptStatus()),
                        line("originNodeId", e.originNodeId()),
                        line("resumeNodeId", e.resumeNodeId()));
                case GoalStartedEvent e -> formatEvent("Goal Started Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        block("goal", e.goal()),
                        line("repositoryUrl", e.repositoryUrl()),
                        line("baseBranch", e.baseBranch()),
                        line("title", e.title()),
                        list("tags", e.tags()));
                case GoalCompletedEvent e -> formatEvent("Goal Completed Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("orchestratorNodeId", e.orchestratorNodeId()),
                        line("workflowId", e.workflowId()),
                        line("model", summarizeObject(e.model())),
                        line("worktreePath", e.worktreePath()));
                case WorktreeCreatedEvent e -> formatEvent("Worktree Created Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("worktreeId", e.worktreeId()),
                        line("associatedNodeId", e.associatedNodeId()),
                        line("worktreePath", e.worktreePath()),
                        line("worktreeType", e.worktreeType()),
                        line("submoduleName", e.submoduleName()));
                case WorktreeBranchedEvent e -> formatEvent("Worktree Branched Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("originalWorktreeId", e.originalWorktreeId()),
                        line("branchedWorktreeId", e.branchedWorktreeId()),
                        line("branchName", e.branchName()),
                        line("worktreeType", e.worktreeType()),
                        line("nodeId", e.nodeId()));
                case WorktreeMergedEvent e -> formatEvent("Worktree Merged Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("childWorktreeId", e.childWorktreeId()),
                        line("parentWorktreeId", e.parentWorktreeId()),
                        line("mergeCommitHash", e.mergeCommitHash()),
                        line("conflictDetected", e.conflictDetected()),
                        list("conflictFiles", e.conflictFiles()),
                        line("worktreeType", e.worktreeType()),
                        line("nodeId", e.nodeId()));
                case WorktreeDiscardedEvent e -> formatEvent("Worktree Discarded Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("worktreeId", e.worktreeId()),
                        block("reason", e.reason()),
                        line("worktreeType", e.worktreeType()),
                        line("nodeId", e.nodeId()));
                case NodeUpdatedEvent e -> formatEvent("Node Updated Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        map("updates", e.updates()));
                case NodeDeletedEvent e -> formatEvent("Node Deleted Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        block("reason", e.reason()));
                case ChatSessionCreatedEvent e -> formatEvent("Chat Session Created Event", e.eventType(),
                        line("chatSessionId / agentNodeId (for sending messages to this chat)", e.chatModelId.value()),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()));
                case ChatSessionClosedEvent e -> formatEvent("Chat Session Closed Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("sessionId", e.sessionId()));
                case ChatSessionResetEvent e -> formatEvent("Chat Session Reset Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("sessionId", e.sessionId()));
                case AiFilterSessionEvent e -> formatEvent("AI Filter Session Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("policyId", e.policyId()),
                        line("sessionMode", e.sessionMode()),
                        line("scopeNodeId", e.scopeNodeId()),
                        line("scopeQualifier", e.scopeQualifier()),
                        line("rootNodeId", e.rootNodeId()),
                        line("sessionContextId", e.sessionContextId() == null ? null : e.sessionContextId().value()));
                case NodeStreamDeltaEvent e -> formatEvent("Node Stream Delta Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("tokenCount", e.tokenCount()),
                        line("isFinal", e.isFinal()),
                        block("deltaContent", e.deltaContent()));
                case NodeThoughtDeltaEvent e -> formatEvent("Node Thought Delta Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("tokenCount", e.tokenCount()),
                        line("isFinal", e.isFinal()),
                        block("deltaContent", e.deltaContent()));
                case ToolCallEvent e -> formatEvent("Tool Call Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("toolCallId", e.toolCallId()),
                        line("title", e.title()),
                        line("kind", e.kind()),
                        line("status", e.status()),
                        line("phase", e.phase()),
                        list("content", e.content()),
                        list("locations", e.locations()),
                        line("rawInput", summarizeObject(e.rawInput())),
                        line("rawOutput", summarizeObject(e.rawOutput())));
                case GuiRenderEvent e -> formatEvent("GUI Render Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("sessionId", e.sessionId()),
                        line("payload", summarizeObject(e.payload())));
                case UiDiffAppliedEvent e -> formatEvent("UI Diff Applied Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("sessionId", e.sessionId()),
                        line("revision", e.revision()),
                        line("renderTree", summarizeObject(e.renderTree())),
                        block("summary", e.summary()));
                case UiDiffRejectedEvent e -> formatEvent("UI Diff Rejected Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("sessionId", e.sessionId()),
                        line("errorCode", e.errorCode()),
                        block("message", e.message()));
                case UiDiffRevertedEvent e -> formatEvent("UI Diff Reverted Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("sessionId", e.sessionId()),
                        line("revision", e.revision()),
                        line("renderTree", summarizeObject(e.renderTree())),
                        line("sourceEventId", e.sourceEventId()));
                case UiFeedbackEvent e -> formatEvent("UI Feedback Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("sessionId", e.sessionId()),
                        line("sourceEventId", e.sourceEventId()),
                        block("message", e.message()),
                        line("snapshot", summarizeObject(e.snapshot())));
                case NodeBranchRequestedEvent e -> formatEvent("Node Branch Requested Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        block("message", e.message()));
                case PlanUpdateEvent e -> formatEvent("Plan Update Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        list("entries", e.entries()));
                case UserMessageChunkEvent e -> formatEvent("User Message Chunk Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        block("content", e.content()));
                case CurrentModeUpdateEvent e -> formatEvent("Current Mode Update Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("currentModeId", e.currentModeId()));
                case AvailableCommandsUpdateEvent e -> formatEvent("Available Commands Update Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        list("commands", e.commands()));
                case PermissionRequestedEvent e -> formatEvent("Permission Requested Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("originNodeId", e.originNodeId()),
                        line("requestId", e.requestId()),
                        line("toolCallId", e.toolCallId()),
                        line("permissions", summarizeObject(e.permissions())));
                case PermissionResolvedEvent e -> formatEvent("Permission Resolved Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("originNodeId", e.originNodeId()),
                        line("requestId", e.requestId()),
                        line("toolCallId", e.toolCallId()),
                        line("outcome", e.outcome()),
                        line("selectedOptionId", e.selectedOptionId()));
                case TuiInteractionGraphEvent e -> formatEvent("TUI Interaction Graph Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("sessionId", e.sessionId()),
                        line("tuiEvent", summarizeObject(e.tuiEvent())));
                case TuiSystemGraphEvent e -> formatEvent("TUI System Graph Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("sessionId", e.sessionId()),
                        line("tuiEvent", summarizeObject(e.tuiEvent())));
                case MergePhaseStartedEvent e -> formatEvent("Merge Phase Started Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("mergeDirection", e.mergeDirection()),
                        line("trunkWorktreeId", e.trunkWorktreeId()),
                        line("childWorktreeId", e.childWorktreeId()),
                        line("childCount", e.childCount()));
                case MergePhaseCompletedEvent e -> formatEvent("Merge Phase Completed Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("mergeDirection", e.mergeDirection()),
                        line("successful", e.successful()),
                        line("mergedCount", e.mergedCount()),
                        line("conflictCount", e.conflictCount()),
                        list("conflictFiles", e.conflictFiles()),
                        line("errorMessage", e.errorMessage()));
                case ArtifactEvent e -> formatEvent("Artifact Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("artifactType", e.artifactType()),
                        line("parentArtifactKey", e.parentArtifactKey()),
                        line("artifactKey", e.artifactKey() == null ? null : e.artifactKey().value()),
                        line("artifact", summarizeObject(e.artifact())));
                case PropagationEvent e -> formatEvent("Propagation Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("registrationId", e.registrationId()),
                        line("layerId", e.layerId()),
                        line("stage", e.stage()),
                        line("action", e.action()),
                        line("sourceNodeId", e.sourceNodeId()),
                        line("sourceName", e.sourceName()),
                        line("payloadType", e.payloadType()),
                        block("payload", summarizeObject(e.payload())));
                case TransformationEvent e -> formatEvent("Transformation Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("registrationId", e.registrationId()),
                        line("layerId", e.layerId()),
                        line("controllerId", e.controllerId()),
                        line("endpointId", e.endpointId()),
                        line("action", e.action()));
                case CompactionEvent e -> formatEvent("Compaction Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        block("message", e.message()));
                case AgentCallStartedEvent e -> formatEvent("Agent Call Started Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("callerNodeId", e.callerNodeId()),
                        line("targetNodeId", e.targetNodeId()),
                        line("callId", e.callId()));
                case AgentCallCompletedEvent e -> formatEvent("Agent Call Completed Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("callId", e.callId()));
                case AgentCallEvent e -> formatEvent("Agent Call Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("callEventType", e.callEventType() != null ? e.callEventType().name() : "null"),
                        line("callerSessionId", e.callerSessionId()),
                        line("targetSessionId", e.targetSessionId()),
                        line("callerAgentType", e.callerAgentType()),
                        line("targetAgentType", e.targetAgentType()));
                case PromptReceivedEvent e -> formatEvent("Prompt Received Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("agentType", e.agentType()),
                        line("templateName", e.templateName()),
                        block("assembledPrompt", e.assembledPrompt()));
                case AgentExecutorStartEvent e -> formatEvent("Agent Executor Start Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("sessionKey", e.sessionKey()),
                        line("actionName", e.actionName()),
                        line("requestContextId", e.requestContextId()));
                case AgentExecutorCompleteEvent e -> formatEvent("Agent Executor Complete Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("sessionKey", e.sessionKey()),
                        line("actionName", e.actionName()),
                        line("requestContextId", e.requestContextId()),
                        line("startNodeId", e.startNodeId()));
                case NullResultEvent e -> formatEvent("Null Result Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("sessionKey", e.sessionKey()),
                        line("retryCount", e.retryCount()),
                        line("maxRetries", e.maxRetries()));
                case IncompleteJsonEvent e -> formatEvent("Incomplete JSON Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("sessionKey", e.sessionKey()),
                        line("retryCount", e.retryCount()),
                        block("rawFragment", e.rawFragment()));
                case UnparsedToolCallEvent e -> formatEvent("Unparsed Tool Call Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("sessionKey", e.sessionKey()),
                        block("toolCallText", e.toolCallText()));
                case TimeoutEvent e -> formatEvent("Timeout Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("sessionKey", e.sessionKey()),
                        line("retryCount", e.retryCount()),
                        block("detail", e.detail()));
                case ParseErrorEvent e -> formatEvent("Parse Error Event", e.eventType(),
                        line("eventId", e.eventId()),
                        line("timestamp", e.timestamp()),
                        line("nodeId", e.nodeId()),
                        line("sessionKey", e.sessionKey()),
                        line("actionName", e.actionName()),
                        block("rawOutput", e.rawOutput()));
            };
        }

    }

    private static String formatEvent(String title, String eventType, String... lines) {
        StringBuilder builder = new StringBuilder();
        builder.append(title).append("\n");
        builder.append("type: ").append(eventType == null ? "(none)" : eventType).append("\n");
        for (String line : lines) {
            if (line == null || line.isBlank()) {
                continue;
            }
            builder.append(line).append("\n");
        }
        return builder.toString().trim();
    }

    private static String line(String label, Object value) {
        if (value == null) {
            return label + ": (none)";
        }
        String rendered = String.valueOf(value);
        if (rendered.isBlank()) {
            return label + ": (empty)";
        }
        return label + ": " + rendered;
    }

    private static String block(String label, String value) {
        if (value == null) {
            return label + ":\n\t(none)";
        }
        if (value.isBlank()) {
            return label + ":\n\t(empty)";
        }
        return label + ":\n\t" + value.trim().replace("\n", "\n\t");
    }

    private static String list(String label, List<?> values) {
        if (values == null || values.isEmpty()) {
            return label + ":\n\t(none)";
        }
        StringBuilder builder = new StringBuilder(label).append(":\n");
        int index = 1;
        for (Object value : values) {
            builder.append("\t").append(index++).append(". ")
                    .append(value == null ? "(none)" : summarizeObject(value))
                    .append("\n");
        }
        return builder.toString().trim();
    }

    private static String map(String label, Map<?, ?> values) {
        if (values == null || values.isEmpty()) {
            return label + ":\n\t(none)";
        }
        StringBuilder builder = new StringBuilder(label).append(":\n");
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            builder.append("\t- ")
                    .append(entry.getKey() == null ? "(null)" : entry.getKey())
                    .append(": ")
                    .append(entry.getValue() == null ? "(none)" : summarizeObject(entry.getValue()))
                    .append("\n");
        }
        return builder.toString().trim();
    }

    private static String summarizeObject(Object value) {
        if (value == null) {
            return "(none)";
        }
        if (value instanceof String s) {
            return s.isBlank() ? "(empty)" : s;
        }
        if (value instanceof ArtifactKey key) {
            return key.value();
        }
        return String.valueOf(value);
    }

    sealed interface AgentEvent extends GraphEvent, HasContextId {


        String nodeId();

        Logger log = LoggerFactory.getLogger(AgentEvent.class);

        @Override
        @JsonIgnore
        default ArtifactKey contextId() {
            var n = nodeId();

            try {
                return new ArtifactKey(n);
            } catch (Exception e) {
                log.error("Failed to parse node {} as artifact key.", n, e);
            }

            return ArtifactKey.createRoot();
        }
    }

    /**
     * Emitted when a new node is added to the graph.
     */
    record NodeAddedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String nodeTitle,
            NodeType nodeType,
            String parentNodeId,
            @JsonIgnore EventNode node
    ) implements AgentEvent {
        public NodeAddedEvent(
                String eventId,
                Instant timestamp,
                String nodeId,
                String nodeTitle,
                NodeType nodeType,
                String parentNodeId
        ) {
            this(eventId, timestamp, nodeId, nodeTitle, nodeType, parentNodeId, null);
        }

        @Override
        public String eventType() {
            return "NODE_ADDED";
        }
    }

    record AddChildNodeEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String parentNodeId,
            @JsonIgnore EventNode updatedParentNode,
            @JsonIgnore EventNode childNode
    ) implements AgentEvent {
        @Override
        public String eventType() {
            return "ADD_CHILD_NODE";
        }
    }

    record ActionStartedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String agentName,
            String actionName
    ) implements AgentEvent {
        @Override
        public String eventType() {
            return "ACTION_STARTED";
        }
    }

    record ActionCompletedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String agentName,
            String actionName,
            String outcomeType,
            Artifact.AgentModel agentModel
    ) implements AgentEvent {
        @Override
        public String eventType() {
            return "ACTION_COMPLETED";
        }
    }

    record StopAgentEvent(
            String eventId,
            Instant timestamp,
            String nodeId
    ) implements AgentEvent {
        @Override
        public String eventType() {
            return "STOP_AGENT";
        }
    }

    /**
     * Pause execution for an agent to view results.
     * @param eventId
     * @param timestamp
     * @param nodeId
     * @param toAddMessage
     */
    record PauseEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String toAddMessage
    ) implements AgentEvent {
        @Override
        public String eventType() {
            return "PAUSE_EVENT";
        }
    }

    record ResumeEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String message
    ) implements AgentEvent {
        @Override
        public String eventType() {
            return "RESUME_EVENT";
        }
    }

    record ResolveInterruptEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String interruptId,
            String toAddMessage,
            InterruptType interruptType
    ) implements AgentEvent {
        @Override
        public String eventType() {
            return "RESOLVE_INTERRUPT";
        }
    }

    record AddMessageEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String toAddMessage
    ) implements AgentEvent {
        @Override
        public String eventType() {
            return "ADD_MESSAGE_EVENT";
        }
    }

    record StructuredChoice(
            String label,
            String value,
            String description
    ) {}

    record ConfirmationItem(
            String id,
            String prompt
    ) {}

    record InterruptRequestEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String sourceAgentType,
            String rerouteToAgentType,
            InterruptType interruptType,
            String reason,
            List<StructuredChoice> choices,
            List<ConfirmationItem> confirmationItems,
            String contextForDecision,
            String requestId
    ) implements AgentEvent {
        @Override
        public String eventType() {
            return "INTERRUPT_REQUEST_EVENT";
        }
    }

    /**
     * Emitted when a node's status changes.
     */
    record NodeStatusChangedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            NodeStatus oldStatus,
            NodeStatus newStatus,
            String reason
    ) implements AgentEvent {
        @Override
        public String eventType() {
            return "NODE_STATUS_CHANGED";
        }
    }

    @Builder
    record NodeErrorEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String nodeTitle,
            NodeType nodeType,
            String message
    ) implements GraphEvent {

        public static NodeErrorEvent err(String errorMessage, ArtifactKey curr) {
            return NodeErrorEvent.builder()
                    .message(errorMessage)
                    .nodeId(curr.value())
                    .nodeType(NodeType.SUMMARY)
                    .nodeTitle("Found an error.")
                    .timestamp(Instant.now())
                    .build();
        }

        @Override
        public String eventType() {
            return "NODE_ERROR";
        }
    }

    record CompactionEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String message
    ) implements GraphEvent {

        public static CompactionEvent of(String message, ArtifactKey key) {
            return new CompactionEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    key.value(),
                    message
            );
        }

        @Override
        public String eventType() {
            return "COMPACTION";
        }
    }

    /**
     * Emitted when a node is branched with modified goal, or the same goal.
     *  Sometimes, we want to have another agent do the same thing, or do something
     *  just a bit different to try it. Additionally, in the future, as agents become
     *  more and more cheap, we'll even try with automated branching, where an meta-
     *  orchestrator starts branching agents with modified goals to predict what the
     *  user might want to see. This will be an experiment with coding entire architectures
     *  generatively to test ideas, predicting how would this look, what's wrong with this
     *  - that's a plugin point for being able to change entire code-bases to test a single
     *    change - sort of like - adding a lifetime specifier to a rust codebase and that
     *    propagating through the whole code base in an instance in a work-tree, or just
     *    moving to an event based system - like - here's with events, and oh wow it ran
     *    into a sever issue with consistency, nope, doesn't look good, etc.
     *  - this helps sort of "test the attractors"
     */
    record NodeBranchedEvent(
            String eventId,
            Instant timestamp,
            String originalNodeId,
            String branchedNodeId,
            String newGoal,
            String mainWorktreeId,
            List<String> submoduleWorktreeIds
    ) implements AgentEvent {
        @Override
        public String eventType() {
            return "NODE_BRANCHED";
        }

        @Override
        public String nodeId() {
            return branchedNodeId;
        }
    }

    /**
     * Emitted when a node is pruned.
     */
    record NodePrunedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String reason,
            List<String> pruneWorktreeIds
    ) implements Events.GraphEvent {
        @Override
        public String eventType() {
            return "NODE_PRUNED";
        }
    }

    /**
     * Emitted when a review is requested.
     */
    record NodeReviewRequestedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String reviewNodeId,
            ReviewType reviewType,
            // "human", "agent", or specific agent type
            String contentToReview
    ) implements Events.AgentEvent {
        @Override
        public String eventType() {
            return "NODE_REVIEW_REQUESTED";
        }
    }

    /**
     * Emitted when interrupt status changes or is recorded.
     */
    record InterruptStatusEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String interruptType,
            String interruptStatus,
            String originNodeId,
            String resumeNodeId
    ) implements AgentEvent {
        @Override
        public String eventType() {
            return "INTERRUPT_STATUS";
        }
    }

    /**
     * Emitted when a goal submission is accepted for orchestration.
     */
    record GoalStartedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String goal,
            String repositoryUrl,
            String baseBranch,
            String title,
            List<String> tags
    ) implements Events.GraphEvent {
        public GoalStartedEvent {
            tags = tags == null ? List.of() : List.copyOf(tags);
        }

        @Override
        public String eventType() {
            return "GOAL_STARTED";
        }
    }

    /**
     * Emitted when overall goal is completed.
     */
    record GoalCompletedEvent(
            String eventId,
            Instant timestamp,
            String orchestratorNodeId,
            String workflowId,
            Artifact.AgentModel model,
            String worktreePath
    ) implements Events.GraphEvent {

        public GoalCompletedEvent(String eventId, Instant timestamp, String orchestratorNodeId, String workflowId, Artifact.AgentModel model) {
            this(eventId, timestamp, orchestratorNodeId, workflowId, model, "");
        }

        @Override
        public String nodeId() {
            return orchestratorNodeId;
        }

        @Override
        public String eventType() {
            return "GOAL_COMPLETED";
        }
    }

// ============ WORKTREE EVENTS ============

    /**
     * Emitted when a worktree is created (main or submodule).
     */
    record WorktreeCreatedEvent(
            String eventId,
            Instant timestamp,
            String worktreeId,
            String associatedNodeId,
            String worktreePath,
            String worktreeType,
            // "main" or "submodule"
            String submoduleName
            // Only if submodule
    ) implements Events.GraphEvent {
        @Override
        public String nodeId() {
            return associatedNodeId;
        }

        @Override
        public String eventType() {
            return "WORKTREE_CREATED";
        }
    }

    /**
     * Emitted when a worktree is branched.
     */
    record WorktreeBranchedEvent(
            String eventId,
            Instant timestamp,
            String originalWorktreeId,
            String branchedWorktreeId,
            String branchName,
            String worktreeType,
            String nodeId
            // "main" or "submodule"
    ) implements Events.GraphEvent {
        @Override
        public String eventType() {
            return "WORKTREE_BRANCHED";
        }
    }

    /**
     * Emitted when a child worktree is merged into parent.
     */
    record WorktreeMergedEvent(
            String eventId,
            Instant timestamp,
            String childWorktreeId,
            String parentWorktreeId,
            String mergeCommitHash,
            boolean conflictDetected,
            List<String> conflictFiles,
            String worktreeType,
            String nodeId
    ) implements Events.GraphEvent {
        @Override
        public String eventType() {
            return "WORKTREE_MERGED";
        }
    }

    /**
     * Emitted when a worktree is discarded/removed.
     */
    record WorktreeDiscardedEvent(
            String eventId,
            Instant timestamp,
            String worktreeId,
            String reason,
            String worktreeType,
            String nodeId
            // "main" or "submodule"
    ) implements Events.GraphEvent {
        @Override
        public String eventType() {
            return "WORKTREE_DISCARDED";
        }
    }

// ============ GENERIC GRAPH EVENTS ============

    /**
     * Generic event for updates to nodes.
     */
    record NodeUpdatedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            Map<String, String> updates,
            @JsonIgnore EventNode node
    ) implements Events.GraphEvent {
        public NodeUpdatedEvent(
                String eventId,
                Instant timestamp,
                String nodeId,
                Map<String, String> updates
        ) {
            this(eventId, timestamp, nodeId, updates, null);
        }

        @Override
        public String eventType() {
            return "NODE_UPDATED";
        }
    }

    /**
     * Event for deletion of nodes (less common than pruning).
     */
    record NodeDeletedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String reason
    ) implements Events.GraphEvent {
        @Override
        public String eventType() {
            return "NODE_DELETED";
        }
    }

    record ChatSessionCreatedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            ArtifactKey chatModelId,
            String encodedChatOptions
    ) implements Events.GraphEvent {

        public ChatSessionCreatedEvent(String eventId, Instant timestamp, String nodeId, ArtifactKey chatModelId) {
            this(eventId, timestamp, nodeId, chatModelId, "");
        }

        @Override
        public String eventType() {
            return "CHAT_SESSION_CREATED";
        }
    }

    record ChatSessionClosedEvent(
            String eventId,
            Instant timestamp,
            String sessionId
    ) implements Events.GraphEvent {
        @Override
        public String nodeId() {
            return sessionId;
        }

        @Override
        public String eventType() {
            return "CHAT_SESSION_CLOSED";
        }
    }

    record ChatSessionResetEvent(
            String eventId,
            Instant timestamp,
            String sessionId
    ) implements Events.GraphEvent {
        @Override
        public String nodeId() {
            return sessionId;
        }

        @Override
        public String eventType() {
            return "CHAT_SESSION_RESET";
        }
    }

    record AiFilterSessionEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String policyId,
            String sessionMode,
            String scopeNodeId,
            String scopeQualifier,
            String rootNodeId,
            ArtifactKey sessionContextId
    ) implements Events.GraphEvent {
        @Override
        public String eventType() {
            return "AI_FILTER_SESSION";
        }
    }

    /**
     * Emitted during streaming output from an agent (e.g., code generation).
     */
    record NodeStreamDeltaEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            ArtifactKey chatKey,
            String deltaContent,
            int tokenCount,
            boolean isFinal
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "NODE_STREAM_DELTA";
        }
    }

    record NodeThoughtDeltaEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            ArtifactKey chatKey,
            String deltaContent,
            int tokenCount,
            boolean isFinal
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "NODE_THOUGHT_DELTA";
        }
    }

    record ToolCallEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            ArtifactKey chatKey,
            String toolCallId,
            String title,
            String kind,
            String status,
            String phase,
            List<Map<String, Object>> content,
            List<Map<String, Object>> locations,
            Object rawInput,
            Object rawOutput
    ) implements GraphEvent {

        @Override
        public String eventType() {
            return "TOOL_CALL";
        }


        public String toolCallType() {
            String normalized = phase != null ? phase.toUpperCase(Locale.ROOT) : "UPDATE";
            return "TOOL_CALL_" + normalized;
        }
    }

    record GuiRenderEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String sessionId,
            Object payload
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "GUI_RENDER";
        }
    }

    record UiDiffAppliedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String sessionId,
            String revision,
            Object renderTree,
            String summary
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "UI_DIFF_APPLIED";
        }
    }

    record UiDiffRejectedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String sessionId,
            String errorCode,
            String message
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "UI_DIFF_REJECTED";
        }
    }

    record UiDiffRevertedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String sessionId,
            String revision,
            Object renderTree,
            String sourceEventId
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "UI_DIFF_REVERTED";
        }
    }

    record UiFeedbackEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String sessionId,
            String sourceEventId,
            String message,
            UiStateSnapshot snapshot
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "UI_FEEDBACK";
        }
    }

    record NodeBranchRequestedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String message
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "NODE_BRANCH_REQUESTED";
        }
    }

    record PlanUpdateEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            List<Map<String, Object>> entries
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "PLAN_UPDATE";
        }
    }

    record UserMessageChunkEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String content
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "USER_MESSAGE_CHUNK";
        }
    }

    record CurrentModeUpdateEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String currentModeId
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "CURRENT_MODE_UPDATE";
        }
    }

    record AvailableCommandsUpdateEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            List<Map<String, Object>> commands
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "AVAILABLE_COMMANDS_UPDATE";
        }
    }

    record PermissionRequestedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String originNodeId,
            String requestId,
            String toolCallId,
            Object permissions
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "PERMISSION_REQUESTED";
        }
    }

    record PermissionResolvedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String originNodeId,
            String requestId,
            String toolCallId,
            String outcome,
            String selectedOptionId
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "PERMISSION_RESOLVED";
        }
    }

    // ============ TUI EVENTS ============

    sealed interface UiEvent {
    }

    sealed interface UiInteractionEvent extends UiEvent
            permits
            EventStreamMoveSelection,
            EventStreamScroll,
            EventStreamOpenDetail,
            EventStreamCloseDetail,
            FocusChatInput,
            FocusEventStream,
            ChatInputChanged,
            ChatInputSubmitted,
            ChatSearchOpened,
            ChatSearchQueryChanged,
            ChatSearchResultNavigate,
            ChatSearchClosed,
            SessionSelected,
            SessionCreated,
            FocusSessionList {
    }

    sealed interface UiSystemEvent extends UiEvent
            permits
            EventStreamAppended,
            EventStreamTrimmed,
            ChatMessageAppended,
            ChatHistoryTrimmed {
    }

    record TuiInteractionGraphEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String sessionId,
            UiInteractionEvent tuiEvent
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "TUI_INTERACTION";
        }
    }

    record TuiSystemGraphEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String sessionId,
            UiSystemEvent tuiEvent
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "TUI_SYSTEM";
        }
    }

    record EventStreamMoveSelection(
            int delta,
            int newSelectedIndex
    ) implements UiInteractionEvent {
    }

    record EventStreamScroll(
            int delta,
            int newScrollOffset
    ) implements UiInteractionEvent {
    }

    record EventStreamOpenDetail(
            String eventId
    ) implements UiInteractionEvent {
    }

    record EventStreamCloseDetail(
            String eventId
    ) implements UiInteractionEvent {
    }

    record FocusChatInput(
            String previousFocus
    ) implements UiInteractionEvent {
    }

    record FocusEventStream(
            String previousFocus
    ) implements UiInteractionEvent {
    }

    record ChatInputChanged(
            String text,
            int cursorPosition
    ) implements UiInteractionEvent {
    }

    record ChatInputSubmitted(
            String text
    ) implements UiInteractionEvent {
    }

    record ChatSearchOpened(
            String initialQuery
    ) implements UiInteractionEvent {
    }

    record ChatSearchQueryChanged(
            String query,
            int cursorPosition
    ) implements UiInteractionEvent {
    }

    record ChatSearchResultNavigate(
            int delta,
            int resultIndex
    ) implements UiInteractionEvent {
    }

    record ChatSearchClosed(
            String query
    ) implements UiInteractionEvent {
    }

    record SessionSelected(
            String sessionId
    ) implements UiInteractionEvent {
    }

    record SessionCreated(
            String sessionId
    ) implements UiInteractionEvent {
    }

    record FocusSessionList(
            String previousFocus
    ) implements UiInteractionEvent {
    }

    record EventStreamAppended(
            String graphEventId,
            int rowIndex
    ) implements UiSystemEvent {
    }

    record EventStreamTrimmed(
            int removedCount
    ) implements UiSystemEvent {
    }

    record ChatMessageAppended(
            String messageId,
            int rowIndex
    ) implements UiSystemEvent {
    }

    record ChatHistoryTrimmed(
            int removedCount
    ) implements UiSystemEvent {
    }

    record UiStateSnapshot(
            String sessionId,
            String revision,
            Instant timestamp,
            Object renderTree
    ) {
    }

    // ============ MERGE PHASE EVENTS ============

    /**
     * Emitted when a merge phase starts in a decorator (trunk→child or child→trunk).
     */
    record MergePhaseStartedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String mergeDirection,
            String trunkWorktreeId,
            String childWorktreeId,
            int childCount
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "MERGE_PHASE_STARTED";
        }
    }

    /**
     * Emitted when a merge phase completes in a decorator, with the aggregate result.
     */
    record MergePhaseCompletedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String mergeDirection,
            boolean successful,
            int mergedCount,
            int conflictCount,
            List<String> conflictFiles,
            String errorMessage
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "MERGE_PHASE_COMPLETED";
        }
    }

    // ============ ARTIFACT EVENTS ============

    /**
     * Event emitted when an artifact is created during execution.
     * Used by ArtifactEventListener to build and persist the artifact tree.
     */
    record ArtifactEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String artifactType,
            String parentArtifactKey,
            Artifact artifact
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "ARTIFACT_EMITTED";
        }

        public ArtifactKey artifactKey() {
            return artifact.artifactKey();
        }
    }

    // ============ PROPAGATION & TRANSFORMATION EVENTS ============

    /**
     * Event emitted when a propagator runs against an action request or response.
     */
    record PropagationEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String registrationId,
            String layerId,
            String stage,
            String action,
            String sourceNodeId,
            String sourceName,
            String payloadType,
            Object payload,
            String correlationKey
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "PROPAGATION";
        }
    }

    /**
     * Event emitted when a transformer runs against a controller endpoint response.
     */
    record TransformationEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String registrationId,
            String layerId,
            String controllerId,
            String endpointId,
            String action,
            String errorMessage
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "TRANSFORMATION";
        }
    }

    /**
     * Emitted when an inter-agent communication call begins.
     * Used to track the active call chain for cycle detection.
     */
    record AgentCallStartedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String callerNodeId,
            String targetNodeId,
            String callId
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "AGENT_CALL_STARTED";
        }
    }

    /**
     * Emitted when an inter-agent communication call completes.
     */
    record AgentCallCompletedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String callId
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "AGENT_CALL_COMPLETED";
        }
    }

    /**
     * High-level observability event for all communication actions (agent-to-agent, agent-to-controller,
     * controller-to-agent). Covers the full lifecycle: INITIATED, INTERMEDIARY, RETURNED, ERROR.
     */
    enum AgentCallEventType {
        INITIATED,
        INTERMEDIARY,
        RETURNED,
        ERROR
    }

    record AgentCallEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            AgentCallEventType callEventType,
            String callerSessionId,
            String callerAgentType,
            String targetSessionId,
            String targetAgentType,
            List<String> callChain,
            List<String> availableAgents,
            String message,
            String response,
            String errorDetail,
            String checklistAction
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "AGENT_CALL";
        }
    }

    /**
     * Emitted before the LLM call in AgentExecutor.run(). Signals a new executor attempt.
     * Used by AcpRetryEventListener to bracket retry state.
     *
     * <p>{@code requestContextId} is the unique ArtifactKey for this particular execution
     * attempt (a child of the prompt context's currentContextId). It ties start/complete
     * pairs together and lets ActionRetryListenerImpl detect unmatched starts (retries).
     */
    record AgentExecutorStartEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String sessionKey,
            String actionName,
            String requestContextId
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "AGENT_EXECUTOR_START";
        }
    }

    /**
     * Emitted after a successful LLM call in AgentExecutor.run(). Signals executor success.
     * Used by AcpRetryEventListener to clear retry state.
     *
     * <p>{@code requestContextId} is the agentRequest's contextId (same across retries).
     * {@code startNodeId} is the corresponding {@link AgentExecutorStartEvent#nodeId()},
     * which is unique per execution attempt and used for start/complete matching.
     */
    record AgentExecutorCompleteEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String sessionKey,
            String actionName,
            String requestContextId,
            String startNodeId
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "AGENT_EXECUTOR_COMPLETE";
        }
    }

    /**
     * Event emitted when a fully assembled prompt is about to be sent to the LLM.
     * Captures the complete prompt text (template + all prompt contributors) for observability.
     */
    record PromptReceivedEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String agentType,
            String templateName,
            String assembledPrompt
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "PROMPT_RECEIVED";
        }
    }

    /**
     * Emitted when the ACP session returns a null or empty result and a retry
     * loop is entered (or exhausted).
     */
    @Builder
    record NullResultEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String sessionKey,
            int retryCount,
            int maxRetries
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "NULL_RESULT";
        }
    }

    /**
     * Emitted when the ACP response contains an opening '{' but no complete
     * JSON object — the agent was likely truncated mid-response.
     */
    @Builder
    record IncompleteJsonEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String sessionKey,
            int retryCount,
            String rawFragment
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "INCOMPLETE_JSON";
        }
    }

    /**
     * Emitted when the ACP response is an unparsed tool call returned as
     * final structured output instead of an actual response.
     */
    @Builder
    record UnparsedToolCallEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String sessionKey,
            String toolCallText
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "UNPARSED_TOOL_CALL";
        }
    }

    /**
     * Emitted when an ACP session or executor call times out.
     */
    @Builder
    record TimeoutEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String sessionKey,
            int retryCount,
            String detail
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "TIMEOUT";
        }
    }

    /**
     * Emitted when an LLM response cannot be parsed into the expected type.
     * Covers JSON parse failures and generic deserialization errors.
     */
    @Builder
    record ParseErrorEvent(
            String eventId,
            Instant timestamp,
            String nodeId,
            String sessionKey,
            String actionName,
            String rawOutput
    ) implements GraphEvent {
        @Override
        public String eventType() {
            return "PARSE_ERROR";
        }
    }
}

// ============ NODE EVENTS ============
