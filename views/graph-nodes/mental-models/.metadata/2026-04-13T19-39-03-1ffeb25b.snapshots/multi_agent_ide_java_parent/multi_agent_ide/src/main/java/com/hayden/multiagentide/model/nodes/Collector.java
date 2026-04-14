package com.hayden.multiagentide.model.nodes;

import java.util.List;

/**
 * Capability mixin: Nodes that collect results from multiple agents.
 */
public sealed interface Collector
        permits CollectorNode, DiscoveryCollectorNode, PlanningCollectorNode, TicketCollectorNode {

    List<CollectedNodeStatus> collectedNodes();
}
