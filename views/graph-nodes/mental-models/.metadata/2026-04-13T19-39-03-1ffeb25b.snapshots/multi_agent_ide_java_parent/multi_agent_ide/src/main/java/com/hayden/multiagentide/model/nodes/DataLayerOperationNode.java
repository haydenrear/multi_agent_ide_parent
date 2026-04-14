package com.hayden.multiagentide.model.nodes;

import com.hayden.acp_cdc_ai.acp.events.Events;
import lombok.Builder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Node representing a data layer operation (AiFilter, AiPropagator, AiTransformer)
 * in the computation graph. These operations may run on their own chat sessions
 * resolved via AiFilterSessionResolver.
 */
@Builder(toBuilder = true)
public record DataLayerOperationNode(
        String nodeId,
        String title,
        String goal,
        Events.NodeStatus status,
        String parentNodeId,
        List<String> childNodeIds,
        Map<String, String> metadata,
        Instant createdAt,
        Instant lastUpdatedAt,
        String chatId,
        String operationType
) implements GraphNode, HasChatId {

    public DataLayerOperationNode {
        if (nodeId == null || nodeId.isEmpty()) throw new IllegalArgumentException("nodeId required");
        if (childNodeIds == null) childNodeIds = new ArrayList<>();
        if (metadata == null) metadata = new HashMap<>();
    }

    @Override
    public Events.NodeType nodeType() {
        return Events.NodeType.DATA_LAYER_OPERATION;
    }

    @Override
    public DataLayerOperationNode withStatus(Events.NodeStatus newStatus) {
        return toBuilder()
                .status(newStatus)
                .lastUpdatedAt(Instant.now())
                .build();
    }
}
