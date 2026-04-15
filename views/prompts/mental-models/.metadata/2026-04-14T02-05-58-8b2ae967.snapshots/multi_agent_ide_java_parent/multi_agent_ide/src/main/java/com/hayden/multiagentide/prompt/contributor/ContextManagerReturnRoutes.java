package com.hayden.multiagentide.prompt.contributor;

import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.agent.BlackboardHistory;

/**
 * Utility for resolving where the context manager should route back to, based on the
 * blackboard history. Delegates to {@link NodeMappings} for the type-to-field-name mapping.
 *
 * <p>Used by both {@link ContextManagerPromptContributorFactory} and
 * {@link InterruptLoopBreakerPromptContributorFactory}.</p>
 */
public final class ContextManagerReturnRoutes {

    private ContextManagerReturnRoutes() {}

    /**
     * Resolved return route: the field name and display name for routing back.
     */
    public record ReturnRouteMapping(
            Class<?> requestType,
            String fieldName,
            String displayName
    ) {}

    public static ReturnRouteMapping findMapping(Class<?> requestType) {
        NodeMappings.NodeMapping mapping = NodeMappings.findByType(requestType);
        if (mapping != null && mapping.fieldName() != null) {
            return new ReturnRouteMapping(mapping.requestType(), mapping.fieldName(), mapping.displayName());
        }
        return null;
    }

    /**
     * Finds the last non-context-manager, non-interrupt request from the blackboard history.
     */
    public static AgentModels.AgentRequest findLastNonContextManagerRequest(BlackboardHistory history) {
        return BlackboardHistory.findLastNonContextRequest(history);
    }

    /**
     * Resolves the specific field name and display name for the context manager to route back to,
     * based on the blackboard history.
     *
     * @return the mapping for the last active agent, or {@code null} if no agent could be identified
     */
    public static ReturnRouteMapping resolveReturnRoute(BlackboardHistory history) {
        AgentModels.AgentRequest lastAgent = findLastNonContextManagerRequest(history);
        if (lastAgent == null) {
            return null;
        }
        return findMapping(lastAgent.getClass());
    }
}
