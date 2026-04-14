package com.hayden.multiagentide.agent.decorator.request;

import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.agent.AgentModels;

/**
 * Decorator interface for processing ResultsRequest types (TicketAgentResults, 
 * PlanningAgentResults, DiscoveryAgentResults) during the dispatch agent phase.
 * 
 * This is used for Phase 2 of the worktree merge flow: merging child agent 
 * worktrees back to the parent (trunk) worktree before routing decisions.
 */
public interface ResultsRequestDecorator {

    /**
     * Ordering for decorator execution. Lower values execute first.
     * Default decorators should use 0.
     */
    default int order() {
        return 0;
    }

    /**
     * Decorate a ResultsRequest (TicketAgentResults, PlanningAgentResults, DiscoveryAgentResults).
     * 
     * @param resultsRequest The results request to decorate
     * @param context The decorator context containing operation context and last request
     * @return The decorated results request (may be the same instance if no decoration needed)
     */
    <T extends AgentModels.ResultsRequest> T decorate(T resultsRequest, DecoratorContext context);
}
