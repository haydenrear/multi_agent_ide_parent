package com.hayden.multiagentide.model.nodes;

import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.acp_cdc_ai.acp.events.Events;
import lombok.Builder;

import java.time.Instant;
import java.util.*;

/**
 * Node that summarizes completed work.
 * Can be Summarizable, Viewable.
 */
@Builder(toBuilder = true)
public record DiscoveryCollectorNode(
        String nodeId,
        String title,
        String goal,
        Events.NodeStatus status,
        String parentNodeId,
        List<String> childNodeIds,
        Map<String, String> metadata,
        Instant createdAt,
        Instant lastUpdatedAt,
        String summaryContent,
        int totalTasksCompleted,
        int totalTasksFailed,
        List<CollectedNodeStatus> collectedNodes,
        AgentModels.DiscoveryCollectorResult discoveryCollectorResult,
        InterruptContext interruptibleContext,
        WorkflowContext workflowContext
) implements GraphNode, Viewable<String>, Collector, Interruptible, HasWorkflowContext<DiscoveryCollectorNode> {

    public DiscoveryCollectorNode(String nodeId, String title, String goal, Events.NodeStatus status, String parentNodeId, List<String> childNodeIds, Map<String, String> metadata, Instant createdAt, Instant lastUpdatedAt, String summaryContent, int totalTasksCompleted, int totalTasksFailed) {
        this(nodeId, title, goal, status, parentNodeId, childNodeIds, metadata, createdAt, lastUpdatedAt,
                summaryContent, totalTasksCompleted, totalTasksFailed, new ArrayList<>(), null, null, WorkflowContext.initial());
    }

    public DiscoveryCollectorNode {
        if (nodeId == null || nodeId.isEmpty()) throw new IllegalArgumentException("nodeId required");
        if (childNodeIds == null) childNodeIds = new ArrayList<>();
        if (metadata == null) metadata = new HashMap<>();
        if (collectedNodes == null) collectedNodes = new ArrayList<>();
        if (workflowContext == null) workflowContext = WorkflowContext.initial();
    }

    @Override
    public Events.NodeType nodeType() {
        return Events.NodeType.SUMMARY;
    }

    @Override
    public String getView() {
        return summaryContent;
    }

    /**
     * Create an updated version with new status.
     */
    public DiscoveryCollectorNode withStatus(Events.NodeStatus newStatus) {
        return toBuilder()
                .status(newStatus)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    /**
     * Update summary content.
     */
    public DiscoveryCollectorNode withContent(String content) {
        return toBuilder()
                .summaryContent(content)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public DiscoveryCollectorNode withResult(AgentModels.DiscoveryCollectorResult result) {
        return toBuilder()
                .discoveryCollectorResult(result)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public DiscoveryCollectorNode withCollectedNodes(List<CollectedNodeStatus> nodes) {
        return toBuilder()
                .collectedNodes(nodes)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public DiscoveryCollectorNode withInterruptibleContext(InterruptContext context) {
        return toBuilder()
                .interruptibleContext(context)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    @Override
    public DiscoveryCollectorNode withWorkflowContext(WorkflowContext workflowContext) {
        return toBuilder()
                .workflowContext(workflowContext)
                .lastUpdatedAt(Instant.now())
                .build();
    }


}
