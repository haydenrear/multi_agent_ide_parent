package com.hayden.multiagentide.model.nodes;

import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.agent.AgentModels;
import lombok.Builder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Dispatch node for batches of discovery agent requests.
 */
@Builder(toBuilder = true)
public record DiscoveryDispatchAgentNode(
        String nodeId,
        String title,
        String goal,
        Events.NodeStatus status,
        String parentNodeId,
        List<String> childNodeIds,
        Map<String, String> metadata,
        Instant createdAt,
        Instant lastUpdatedAt,
        int requestedAgentCount,
        String delegationRationale,
        AgentModels.DiscoveryAgentRequests discoveryAgentRequests,
        InterruptContext interruptibleContext,
        WorkflowContext workflowContext
) implements GraphNode, Interruptible, HasWorkflowContext<DiscoveryDispatchAgentNode> {

    public DiscoveryDispatchAgentNode {
        if (nodeId == null || nodeId.isEmpty()) throw new IllegalArgumentException("nodeId required");
        if (childNodeIds == null) childNodeIds = new ArrayList<>();
        if (metadata == null) metadata = new HashMap<>();
        if (workflowContext == null) workflowContext = WorkflowContext.initial();
    }

    @Override
    public Events.NodeType nodeType() {
        return Events.NodeType.SUMMARY;
    }

    public DiscoveryDispatchAgentNode withStatus(Events.NodeStatus newStatus) {
        return toBuilder()
                .status(newStatus)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public DiscoveryDispatchAgentNode withRequests(AgentModels.DiscoveryAgentRequests requests) {
        return toBuilder()
                .discoveryAgentRequests(requests)
                .goal(requests != null && requests.goal() != null ? requests.goal() : goal)
                .delegationRationale(requests != null ? requests.delegationRationale() : delegationRationale)
                .requestedAgentCount(requests != null && requests.requests() != null ? requests.requests().size() : requestedAgentCount)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    @Override
    public DiscoveryDispatchAgentNode withWorkflowContext(WorkflowContext workflowContext) {
        return toBuilder()
                .workflowContext(workflowContext)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public DiscoveryDispatchAgentNode withInterruptibleContext(InterruptContext context) {
        return toBuilder()
                .interruptibleContext(context)
                .lastUpdatedAt(Instant.now())
                .build();
    }
}

