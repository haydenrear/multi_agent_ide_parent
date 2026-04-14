package com.hayden.multiagentide.model.nodes;

import com.hayden.acp_cdc_ai.acp.events.Events;

/**
 * Snapshot of a collected node's identity and status at collection time.
 */
public record CollectedNodeStatus(
        String nodeId,
        String title,
        Events.NodeType nodeType,
        Events.NodeStatus status
) {
}
