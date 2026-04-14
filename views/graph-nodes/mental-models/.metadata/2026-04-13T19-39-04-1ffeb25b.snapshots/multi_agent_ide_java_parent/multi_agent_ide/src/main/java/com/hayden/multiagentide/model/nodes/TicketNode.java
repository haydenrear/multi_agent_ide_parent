package com.hayden.multiagentide.model.nodes;

import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.acp_cdc_ai.acp.events.Events;
import lombok.Builder;
import java.time.Instant;
import java.util.*;

/**
 * Node representing a unit of work tied to main and submodule worktrees.
 * Can be Branchable, Editable, Reviewable, Prunable.
 */
@Builder(toBuilder = true)
public record TicketNode(
        String nodeId,
        String title,
        String goal,
        Events.NodeStatus status,
        String parentNodeId,
        List<String> childNodeIds,
        Map<String, String> metadata,
        Instant createdAt,
        Instant lastUpdatedAt,
        String workOutput,
        AgentModels.TicketAgentResult ticketAgentResult,
        InterruptContext interruptibleContext
) implements GraphNode, Interruptible {

    public TicketNode {
        if (nodeId == null || nodeId.isEmpty()) throw new IllegalArgumentException("nodeId required");
        if (goal == null || goal.isEmpty()) throw new IllegalArgumentException("goal required");
        if (childNodeIds == null) childNodeIds = new ArrayList<>();
        if (metadata == null) metadata = new HashMap<>();
    }

    public TicketNode(String nodeId, String title, String goal, Events.NodeStatus status, String parentNodeId, List<String> childNodeIds, Map<String, String> metadata, Instant createdAt, Instant lastUpdatedAt) {
        this(nodeId, title, goal, status, parentNodeId, childNodeIds, metadata, createdAt, lastUpdatedAt, "", null, null);
    }

    @Override
    public Events.NodeType nodeType() {
        return Events.NodeType.WORK;
    }

    /**
     * Create an updated version with new status.
     */
    public TicketNode withStatus(Events.NodeStatus newStatus) {
        return toBuilder()
                .status(newStatus)
                .lastUpdatedAt(Instant.now())
                .build();
    }


    /**
     * Update work output and streaming progress.
     */
    public TicketNode withOutput(String output) {
        return toBuilder()
                .workOutput(output)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public TicketNode withTicketAgentResult(AgentModels.TicketAgentResult result) {
        return toBuilder()
                .ticketAgentResult(result)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public TicketNode withInterruptibleContext(InterruptContext context) {
        return toBuilder()
                .interruptibleContext(context)
                .lastUpdatedAt(Instant.now())
                .build();
    }

}
