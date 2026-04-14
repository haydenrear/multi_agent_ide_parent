package com.hayden.multiagentide.model.nodes;

/**
 * Capability mixin: Nodes that orchestrate a workflow phase.
 */
public sealed interface Orchestrator
        permits DiscoveryOrchestratorNode, OrchestratorNode, PlanningOrchestratorNode, TicketOrchestratorNode {
}
