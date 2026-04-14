package com.hayden.multiagentide.model.nodes;

import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.agent.AgentModels;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Java helper for Lombok builder chains that Kotlin cannot see.
 * The kotlin-lombok plugin resolves builder()/toBuilder() but not the
 * individual setter methods on the generated builder classes.
 */
public final class GraphNodeBuilderHelper {

    private GraphNodeBuilderHelper() {}

    // ── updateInterruptContext: sets interruptibleContext/interruptContext + lastUpdatedAt ──

    public static GraphNode updateInterruptContext(GraphNode node, InterruptContext context) {
        return switch (node) {
            case DiscoveryNode n ->
                n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case DiscoveryCollectorNode n ->
                n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case DiscoveryDispatchAgentNode n ->
                n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case DiscoveryOrchestratorNode n ->
                n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case PlanningNode n ->
                n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case PlanningCollectorNode n ->
                n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case PlanningDispatchAgentNode n ->
                n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case PlanningOrchestratorNode n ->
                n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case TicketNode n ->
                n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case TicketCollectorNode n ->
                n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case TicketDispatchAgentNode n ->
                n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case TicketOrchestratorNode n ->
                n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case ReviewNode n ->
                n.toBuilder().interruptContext(context).lastUpdatedAt(Instant.now()).build();
            case MergeNode n ->
                n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case OrchestratorNode n ->
                n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case CollectorNode n ->
                n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case SummaryNode n ->
                n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case InterruptNode n ->
                n.toBuilder().interruptContext(context).lastUpdatedAt(Instant.now()).build();
            case AskPermissionNode n ->
                n.toBuilder().lastUpdatedAt(Instant.now()).build();
            case AgentToAgentConversationNode n ->
                n.toBuilder().lastUpdatedAt(Instant.now()).build();
            case AgentToControllerConversationNode n ->
                n.toBuilder().lastUpdatedAt(Instant.now()).build();
            case ControllerToAgentConversationNode n ->
                n.toBuilder().lastUpdatedAt(Instant.now()).build();
            case DataLayerOperationNode n ->
                n.toBuilder().lastUpdatedAt(Instant.now()).build();
        };
    }

    // ── ReviewNode status update ──

    public static ReviewNode withStatus(ReviewNode node, Events.NodeStatus status) {
        return node.toBuilder()
                .status(status)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    // ── ReviewNode full resolution ──

    public static ReviewNode withReviewResolution(
            ReviewNode node,
            boolean approved,
            String agentFeedback,
            AgentModels.ReviewAgentResult reviewResult,
            InterruptContext interruptContext,
            Events.NodeStatus status
    ) {
        return node.toBuilder()
                .approved(approved)
                .agentFeedback(agentFeedback)
                .reviewCompletedAt(Instant.now())
                .reviewResult(reviewResult)
                .interruptContext(interruptContext)
                .status(status)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    // ── InterruptNode resolution ──

    public static InterruptNode withInterruptResolution(
            InterruptNode node,
            InterruptContext interruptContext,
            Events.NodeStatus status
    ) {
        return node.toBuilder()
                .interruptContext(interruptContext)
                .status(status)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    // ── AskPermissionNode builder for PermissionGateAdapter ──

    public static AskPermissionNode buildAskPermissionNode(
            String nodeId,
            String title,
            String goal,
            Events.NodeStatus status,
            String parentNodeId,
            List<String> childNodeIds,
            Map<String, String> metadata,
            Instant createdAt,
            Instant lastUpdatedAt,
            String toolCallId,
            List<String> optionIds
    ) {
        return AskPermissionNode.builder()
                .nodeId(nodeId)
                .title(title)
                .goal(goal)
                .status(status)
                .parentNodeId(parentNodeId)
                .childNodeIds(childNodeIds)
                .metadata(metadata)
                .createdAt(createdAt)
                .lastUpdatedAt(lastUpdatedAt)
                .toolCallId(toolCallId)
                .optionIds(optionIds)
                .build();
    }
}
