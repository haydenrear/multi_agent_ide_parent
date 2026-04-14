package com.hayden.multiagentide.adapter;

import com.hayden.acp_cdc_ai.acp.events.EventListener;
import com.hayden.acp_cdc_ai.acp.events.EventNode;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentide.model.nodes.GraphNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SaveGraphEventToGraphRepositoryListener implements EventListener {

    private final GraphRepository graphRepository;

    @Override
    public String listenerId() {
        return "save-graph-event-to-graph-repository";
    }

    @Override
    public int order() {
        return Integer.MIN_VALUE;
    }

    @Override
    public boolean isInterestedIn(String eventType) {
        return "NODE_ADDED".equals(eventType)
                || "ADD_CHILD_NODE".equals(eventType)
                || "NODE_UPDATED".equals(eventType)
                || "NODE_DELETED".equals(eventType);
    }

    @Override
    public void onEvent(Events.GraphEvent event) {
        switch (event) {
            case Events.AddChildNodeEvent addChild -> {
                saveNodeIfPresent(addChild.updatedParentNode());
                saveNodeIfPresent(addChild.childNode());
            }
            case Events.NodeAddedEvent added -> saveNodeIfPresent(added.node());
            case Events.NodeUpdatedEvent updated -> saveNodeIfPresent(updated.node());
            case Events.NodeDeletedEvent deleted -> graphRepository.delete(deleted.nodeId());
            case Events.NodeStatusChangedEvent nodeStatusChangedEvent -> {
                var found = graphRepository.findById(nodeStatusChangedEvent.nodeId());
                found.ifPresentOrElse(
                        gn -> {
                            graphRepository.save(gn.withStatus(nodeStatusChangedEvent.newStatus()));
                        }, () -> {
                            log.error("Error! Could not find node for status to change.");
                        });
            }
            case Events.ActionCompletedEvent actionCompletedEvent -> {
            }
            case Events.ActionStartedEvent actionStartedEvent -> {
            }
            case Events.AddMessageEvent addMessageEvent -> {
            }
            case Events.ArtifactEvent artifactEvent -> {
            }
            case Events.AvailableCommandsUpdateEvent availableCommandsUpdateEvent -> {
            }
            case Events.ChatSessionClosedEvent chatSessionClosedEvent -> {
            }
            case Events.ChatSessionResetEvent chatSessionResetEvent -> {
            }
            case Events.ChatSessionCreatedEvent chatSessionCreatedEvent -> {
            }
            case Events.AiFilterSessionEvent aiFilterSessionEvent -> {
            }
            case Events.CurrentModeUpdateEvent currentModeUpdateEvent -> {
            }
            case Events.GoalStartedEvent goalStartedEvent -> {
            }
            case Events.GoalCompletedEvent goalCompletedEvent -> {
            }
            case Events.GuiRenderEvent guiRenderEvent -> {
            }
            case Events.InterruptRequestEvent interruptRequestEvent -> {
            }
            case Events.InterruptStatusEvent interruptStatusEvent -> {
            }
            case Events.NodeBranchRequestedEvent nodeBranchRequestedEvent -> {
            }
            case Events.NodeBranchedEvent nodeBranchedEvent -> {
            }
            case Events.NodeErrorEvent nodeErrorEvent -> {
            }
            case Events.NodePrunedEvent nodePrunedEvent -> {
            }
            case Events.NodeReviewRequestedEvent nodeReviewRequestedEvent -> {
            }
            case Events.NodeStreamDeltaEvent nodeStreamDeltaEvent -> {
            }
            case Events.NodeThoughtDeltaEvent nodeThoughtDeltaEvent -> {
            }
            case Events.PauseEvent pauseEvent -> {
            }
            case Events.PermissionRequestedEvent permissionRequestedEvent -> {
            }
            case Events.PermissionResolvedEvent permissionResolvedEvent -> {
            }
            case Events.PlanUpdateEvent planUpdateEvent -> {
            }
            case Events.ResolveInterruptEvent resolveInterruptEvent -> {
            }
            case Events.ResumeEvent resumeEvent -> {
            }
            case Events.StopAgentEvent stopAgentEvent -> {
            }
            case Events.ToolCallEvent toolCallEvent -> {
            }
            case Events.TuiInteractionGraphEvent tuiInteractionGraphEvent -> {
            }
            case Events.TuiSystemGraphEvent tuiSystemGraphEvent -> {
            }
            case Events.UiDiffAppliedEvent uiDiffAppliedEvent -> {
            }
            case Events.UiDiffRejectedEvent uiDiffRejectedEvent -> {
            }
            case Events.UiDiffRevertedEvent uiDiffRevertedEvent -> {
            }
            case Events.UiFeedbackEvent uiFeedbackEvent -> {
            }
            case Events.UserMessageChunkEvent userMessageChunkEvent -> {
            }
            case Events.WorktreeBranchedEvent worktreeBranchedEvent -> {
            }
            case Events.WorktreeCreatedEvent worktreeCreatedEvent -> {
            }
            case Events.WorktreeDiscardedEvent worktreeDiscardedEvent -> {
            }
            case Events.WorktreeMergedEvent worktreeMergedEvent -> {
            }
            case Events.MergePhaseStartedEvent mergePhaseStartedEvent -> {
            }
            case Events.MergePhaseCompletedEvent mergePhaseCompletedEvent -> {
            }
            case Events.PropagationEvent ignored -> {
            }
            case Events.TransformationEvent ignored -> {
            }
            case Events.CompactionEvent ignored -> {
            }
            case Events.AgentCallStartedEvent ignored -> {
            }
            case Events.AgentCallCompletedEvent ignored -> {
            }
            case Events.AgentCallEvent ignored -> {
            }
            case Events.PromptReceivedEvent ignored -> {
            }
            case Events.AgentExecutorStartEvent ignored -> {
            }
            case Events.AgentExecutorCompleteEvent ignored -> {
            }
            case Events.NullResultEvent ignored -> {
            }
            case Events.IncompleteJsonEvent ignored -> {
            }
            case Events.UnparsedToolCallEvent ignored -> {
            }
            case Events.TimeoutEvent ignored -> {
            }
            case Events.ParseErrorEvent ignored -> {
            }
        }
    }

    private void saveNodeIfPresent(EventNode nodePayload) {
        if (nodePayload instanceof GraphNode graphNode) {
            graphRepository.save(graphNode);
        }
    }
}
