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
public record MergeNode(
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
        AgentModels.MergerAgentResult mergerResult,
        InterruptContext interruptibleContext
) implements GraphNode, Viewable<String>, Interruptible {

    public MergeNode(String nodeId, String title, String goal, Events.NodeStatus status, String parentNodeId, List<String> childNodeIds, Map<String, String> metadata, Instant createdAt, Instant lastUpdatedAt, String summaryContent, int totalTasksCompleted, int totalTasksFailed) {
        this(nodeId, title, goal, status, parentNodeId, childNodeIds, metadata, createdAt, lastUpdatedAt,
                summaryContent, totalTasksCompleted, totalTasksFailed, null, null);
    }

    public MergeNode {
        if (nodeId == null || nodeId.isEmpty()) throw new IllegalArgumentException("nodeId required");
        if (childNodeIds == null) childNodeIds = new ArrayList<>();
        if (metadata == null) metadata = new HashMap<>();
    }

    @Override
    public Events.NodeType nodeType() {
        return Events.NodeType.SUMMARY;
    }

    public boolean isFinalMerge() {
        return Optional.ofNullable(metadata())
                .flatMap(m -> Optional.ofNullable(m.get("merge_scope")))
                .filter(s -> Objects.equals(s, "final"))
                .isPresent();
    }

    @Override
    public String getView() {
        return summaryContent;
    }

    /**
     * Create an updated version with new status.
     */
    public MergeNode withStatus(Events.NodeStatus newStatus) {
        return toBuilder()
                .status(newStatus)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    /**
     * Update summary content.
     */
    public MergeNode withContent(String content) {
        return toBuilder()
                .summaryContent(content)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public MergeNode withResult(AgentModels.MergerAgentResult result) {
        return toBuilder()
                .mergerResult(result)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public MergeNode withInterruptibleContext(InterruptContext context) {
        return toBuilder()
                .interruptibleContext(context)
                .lastUpdatedAt(Instant.now())
                .build();
    }


}
