package com.hayden.multiagentide.model.nodes;

import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.acp_cdc_ai.acp.events.Events;
import lombok.Builder;

import java.time.Instant;
import java.util.*;

/**
 * Node that breaks down goals into work tickets.
 * Can be Reviewable.
 */
@Builder(toBuilder = true)
public record PlanningCollectorNode(
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
        List<CollectedNodeStatus> collectedNodes,
        AgentModels.PlanningCollectorResult planningCollectorResult,
        InterruptContext interruptibleContext,
        WorkflowContext workflowContext
) implements GraphNode, Viewable<String>, Collector, Interruptible, HasWorkflowContext<PlanningCollectorNode> {

    public PlanningCollectorNode(String nodeId, String title, String goal, Events.NodeStatus status, String parentNodeId, List<String> childNodeIds, Map<String, String> metadata, Instant createdAt, Instant lastUpdatedAt, List<String> generatedTicketIds, String planContent, int estimatedSubtasks, int completedSubtasks) {
        this(nodeId, title, goal, status, parentNodeId, childNodeIds, metadata, createdAt, lastUpdatedAt,
                generatedTicketIds, planContent, estimatedSubtasks, completedSubtasks, new ArrayList<>(), null, null, WorkflowContext.initial());
    }

    public PlanningCollectorNode {
        if (nodeId == null || nodeId.isEmpty()) throw new IllegalArgumentException("nodeId required");
        if (goal == null || goal.isEmpty()) throw new IllegalArgumentException("goal required");
        if (childNodeIds == null) childNodeIds = new ArrayList<>();
        if (metadata == null) metadata = new HashMap<>();
        if (generatedTicketIds == null) generatedTicketIds = new ArrayList<>();
        if (collectedNodes == null) collectedNodes = new ArrayList<>();
        if (workflowContext == null) workflowContext = WorkflowContext.initial();
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
    public PlanningCollectorNode withStatus(Events.NodeStatus newStatus) {
        return toBuilder()
                .status(newStatus)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    /**
     * Add a generated ticket.
     */
    public PlanningCollectorNode addTicket(String ticketId) {
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
    public PlanningCollectorNode withPlanContent(String content) {
        return toBuilder()
                .planContent(content)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    /**
     * Update progress.
     */
    public PlanningCollectorNode withProgress(int completed, int total) {
        return toBuilder()
                .estimatedSubtasks(total)
                .completedSubtasks(completed)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public PlanningCollectorNode withResult(AgentModels.PlanningCollectorResult result) {
        return toBuilder()
                .planningCollectorResult(result)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public PlanningCollectorNode withCollectedNodes(List<CollectedNodeStatus> nodes) {
        return toBuilder()
                .collectedNodes(nodes)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public PlanningCollectorNode withInterruptibleContext(InterruptContext context) {
        return toBuilder()
                .interruptibleContext(context)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    @Override
    public PlanningCollectorNode withWorkflowContext(WorkflowContext workflowContext) {
        return toBuilder()
                .workflowContext(workflowContext)
                .lastUpdatedAt(Instant.now())
                .build();
    }
}
