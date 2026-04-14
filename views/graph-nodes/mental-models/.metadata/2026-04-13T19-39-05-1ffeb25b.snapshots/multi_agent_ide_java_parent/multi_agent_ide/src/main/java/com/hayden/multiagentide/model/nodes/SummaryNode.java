package com.hayden.multiagentide.model.nodes;

import com.hayden.acp_cdc_ai.acp.events.Events;
import lombok.Builder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Node that summarizes completed work.
 * Can be Summarizable, Viewable.
 */
@Builder(toBuilder = true)
public record SummaryNode(
        String nodeId,
        String title,
        String goal,
        Events.NodeStatus status,
        String parentNodeId,
        List<String> childNodeIds,
        Map<String, String> metadata,
        Instant createdAt,
        Instant lastUpdatedAt,
        
        // Summary-specific fields
        List<String> summarizedNodeIds,
        String summaryContent,
        int totalTasksCompleted,
        int totalTasksFailed,
        InterruptContext interruptibleContext
) implements GraphNode, Viewable<String>, Interruptible {

    public SummaryNode(String nodeId, String title, String goal, Events.NodeStatus status, String parentNodeId, List<String> childNodeIds, Map<String, String> metadata, Instant createdAt, Instant lastUpdatedAt, List<String> summarizedNodeIds, String summaryContent, int totalTasksCompleted, int totalTasksFailed) {
        this(nodeId, title, goal, status, parentNodeId, childNodeIds, metadata, createdAt, lastUpdatedAt,
                summarizedNodeIds, summaryContent, totalTasksCompleted, totalTasksFailed, null);
    }

    public SummaryNode {
        if (nodeId == null || nodeId.isEmpty()) throw new IllegalArgumentException("nodeId required");
        if (childNodeIds == null) childNodeIds = new ArrayList<>();
        if (metadata == null) metadata = new HashMap<>();
        if (summarizedNodeIds == null) summarizedNodeIds = new ArrayList<>();
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
    public SummaryNode withStatus(Events.NodeStatus newStatus) {
        return toBuilder()
                .status(newStatus)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    /**
     * Update summary content.
     */
    public SummaryNode withContent(String content) {
        return toBuilder()
                .summaryContent(content)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    /**
     * Add a summarized node.
     */
    public SummaryNode addSummarizedNode(String nodeId) {
        List<String> newSummarized = new ArrayList<>(summarizedNodeIds);
        newSummarized.add(nodeId);
        return toBuilder()
                .summarizedNodeIds(newSummarized)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public SummaryNode withInterruptibleContext(InterruptContext context) {
        return toBuilder()
                .interruptibleContext(context)
                .lastUpdatedAt(Instant.now())
                .build();
    }
}
