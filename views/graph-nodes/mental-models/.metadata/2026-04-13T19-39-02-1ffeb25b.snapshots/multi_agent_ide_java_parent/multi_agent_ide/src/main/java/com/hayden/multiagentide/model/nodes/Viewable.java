package com.hayden.multiagentide.model.nodes;

/**
 * Capability mixin: Nodes that support viewing results of type O.
 */
public sealed interface Viewable<O>
        permits CollectorNode, DiscoveryCollectorNode, DiscoveryNode, DiscoveryOrchestratorNode, InterruptNode, MergeNode, OrchestratorNode, PlanningCollectorNode, PlanningNode, PlanningOrchestratorNode, ReviewNode, SummaryNode, TicketCollectorNode, TicketOrchestratorNode {

    /**
     * Get the viewable output/result.
     */
    O getView();
}
