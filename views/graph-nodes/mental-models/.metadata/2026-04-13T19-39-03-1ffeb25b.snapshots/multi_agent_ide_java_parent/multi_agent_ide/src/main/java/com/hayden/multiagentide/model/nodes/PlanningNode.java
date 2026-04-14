package com.hayden.multiagentide.model.nodes;

import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.acp_cdc_ai.acp.events.Events;
import lombok.Builder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Node that breaks down goals into work tickets.
 * Can be Reviewable.
 */
@Builder(toBuilder = true)
public record PlanningNode(
        String nodeId,
        String title,
        String goal,
        Events.NodeStatus status,
        String parentNodeId,
        List<String> childNodeIds,
        Map<String, String> metadata,
        Instant createdAt,
        Instant lastUpdatedAt,
        
        // Planning-specific fields
        List<String> generatedTicketIds,
        String planContent,
        int estimatedSubtasks,
        int completedSubtasks,
        AgentModels.PlanningAgentResult planningResult,
        InterruptContext interruptibleContext
) implements GraphNode, Viewable<String>, Interruptible {

    public PlanningNode(String nodeId, String title, String goal, Events.NodeStatus status, String parentNodeId, List<String> childNodeIds,
                        Map<String, String> metadata, Instant createdAt, Instant lastUpdatedAt, List<String> generatedTicketIds, String planContent,
                        int estimatedSubtasks, int completedSubtasks) {
        this(nodeId, title, goal, status, parentNodeId, childNodeIds, metadata, createdAt, lastUpdatedAt,
                generatedTicketIds, planContent, estimatedSubtasks, completedSubtasks, null, null);
    }

    public PlanningNode {
        if (nodeId == null || nodeId.isEmpty()) throw new IllegalArgumentException("nodeId required");
        if (goal == null || goal.isEmpty()) throw new IllegalArgumentException("goal required");
        if (childNodeIds == null) childNodeIds = new ArrayList<>();
        if (metadata == null) metadata = new HashMap<>();
        if (generatedTicketIds == null) generatedTicketIds = new ArrayList<>();
    }

    @Override
    public Events.NodeType nodeType() {
        return Events.NodeType.PLANNING;
    }

    @Override
    public String getView() {
        return planContent;
    }

    /**
     * Create an updated version with new status.
     */
    public PlanningNode withStatus(Events.NodeStatus newStatus) {
        return toBuilder()
                .status(newStatus)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    /**
     * Add a generated ticket.
     */
    public PlanningNode addTicket(String ticketId) {
        List<String> newTickets = new ArrayList<>(generatedTicketIds);
        newTickets.add(ticketId);
        return toBuilder()
                .generatedTicketIds(newTickets)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    /**
     * Update plan content.
     */
    public PlanningNode withPlanContent(String content) {
        return toBuilder()
                .planContent(content)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    /**
     * Update progress.
     */
    public PlanningNode withProgress(int completed, int total) {
        return toBuilder()
                .estimatedSubtasks(total)
                .completedSubtasks(completed)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public PlanningNode withResult(AgentModels.PlanningAgentResult result) {
        return toBuilder()
                .planningResult(result)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public PlanningNode withInterruptibleContext(InterruptContext context) {
        return toBuilder()
                .interruptibleContext(context)
                .lastUpdatedAt(Instant.now())
                .build();
    }
}
