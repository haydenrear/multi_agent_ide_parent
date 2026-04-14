package com.hayden.multiagentide.model.nodes;

import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.agent.AgentType;
import lombok.Builder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Node representing a controller-to-agent conversation in the computation graph.
 * The targetAgentKey field is used for lookup in the controller conversation key resolution algorithm.
 */
@Builder(toBuilder = true)
public record ControllerToAgentConversationNode(
        String nodeId,
        String title,
        String goal,
        Events.NodeStatus status,
        String parentNodeId,
        List<String> childNodeIds,
        Map<String, String> metadata,
        Instant createdAt,
        Instant lastUpdatedAt,
        String targetAgentKey,
        AgentType targetAgentType
) implements GraphNode {

    public ControllerToAgentConversationNode {
        if (nodeId == null || nodeId.isEmpty()) throw new IllegalArgumentException("nodeId required");
        if (childNodeIds == null) childNodeIds = new ArrayList<>();
        if (metadata == null) metadata = new HashMap<>();
    }

    @Override
    public Events.NodeType nodeType() {
        return Events.NodeType.CONTROLLER_TO_AGENT_CONVERSATION;
    }

    @Override
    public ControllerToAgentConversationNode withStatus(Events.NodeStatus newStatus) {
        return toBuilder()
                .status(newStatus)
                .lastUpdatedAt(Instant.now())
                .build();
    }
}
