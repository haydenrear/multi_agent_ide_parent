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
 * Dispatch node for batches of ticket agent requests.
 */
@Builder(toBuilder = true)
public record TicketDispatchAgentNode(
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
        AgentModels.TicketAgentRequests ticketAgentRequests,
        InterruptContext interruptibleContext,
        WorkflowContext workflowContext
) implements GraphNode, Interruptible, HasWorkflowContext<TicketDispatchAgentNode> {

    public TicketDispatchAgentNode {
        if (nodeId == null || nodeId.isEmpty()) throw new IllegalArgumentException("nodeId required");
        if (childNodeIds == null) childNodeIds = new ArrayList<>();
        if (metadata == null) metadata = new HashMap<>();
        if (workflowContext == null) workflowContext = WorkflowContext.initial();
    }

    @Override
    public Events.NodeType nodeType() {
        return Events.NodeType.WORK;
    }

    public TicketDispatchAgentNode withStatus(Events.NodeStatus newStatus) {
        return toBuilder()
                .status(newStatus)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public TicketDispatchAgentNode withRequests(AgentModels.TicketAgentRequests requests) {
        return toBuilder()
                .ticketAgentRequests(requests)
                .goal(requests != null && requests.goal() != null ? requests.goal() : goal)
                .delegationRationale(requests != null ? requests.delegationRationale() : delegationRationale)
                .requestedAgentCount(requests != null && requests.requests() != null ? requests.requests().size() : requestedAgentCount)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    @Override
    public TicketDispatchAgentNode withWorkflowContext(WorkflowContext workflowContext) {
        return toBuilder()
                .workflowContext(workflowContext)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public TicketDispatchAgentNode withInterruptibleContext(InterruptContext context) {
        return toBuilder()
                .interruptibleContext(context)
                .lastUpdatedAt(Instant.now())
                .build();
    }
}

