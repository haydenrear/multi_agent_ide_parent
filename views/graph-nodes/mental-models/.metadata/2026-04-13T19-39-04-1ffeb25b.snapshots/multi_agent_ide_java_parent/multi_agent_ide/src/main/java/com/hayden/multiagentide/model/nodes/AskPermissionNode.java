package com.hayden.multiagentide.model.nodes;

import com.hayden.acp_cdc_ai.acp.events.Events;
import lombok.Builder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Node representing a permission request from an agent/tool invocation.
 */
@Builder(toBuilder = true)
public record AskPermissionNode(
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
) implements GraphNode {

    public AskPermissionNode {
        if (nodeId == null || nodeId.isEmpty()) throw new IllegalArgumentException("nodeId required");
        if (toolCallId == null || toolCallId.isBlank()) throw new IllegalArgumentException("toolCallId required");
        if (childNodeIds == null) childNodeIds = new ArrayList<>();
        if (metadata == null) metadata = new HashMap<>();
        if (optionIds == null) optionIds = new ArrayList<>();
    }

    @Override
    public Events.NodeType nodeType() {
        return Events.NodeType.PERMISSION;
    }

    @Override
    public AskPermissionNode withStatus(Events.NodeStatus nodeStatus) {
        return toBuilder()
                .status(nodeStatus)
                .lastUpdatedAt(Instant.now())
                .build();
    }
}
