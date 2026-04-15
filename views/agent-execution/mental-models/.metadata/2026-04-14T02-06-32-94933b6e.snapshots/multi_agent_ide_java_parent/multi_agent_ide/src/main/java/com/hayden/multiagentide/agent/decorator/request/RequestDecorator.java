package com.hayden.multiagentide.agent.decorator.request;

import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.agent.AgentModels;

public interface RequestDecorator {

    /**
     * Ordering for decorator execution. Lower values execute first.
     * Default decorators should use 0. The emitActionStarted decorator
     * should use Integer.MIN_VALUE to ensure it runs first.
     */
    default int order() {
        return 0;
    }

    /**
     * Decorate/process an incoming request before the action executes.
     * Decorators should return the same request or a modified version of it.
     * @param request the incoming request
     * @param context the decorator context with action metadata
     * @param <T> the request type
     * @return the decorated request (or the same request if not modified)
     */
    default <T extends AgentModels.AgentRequest> T decorate(T request, DecoratorContext context) {
        return request;
    }

}
