package com.hayden.multiagentide.agent.decorator.result;

import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.agent.AgentModels;

public interface ResultDecorator {


    /**
     * Ordering for decorator execution. Lower values execute first.
     * Default decorators should use 0. The emitActionCompleted decorator
     * should use Integer.MAX_VALUE to ensure it runs last with the fully
     * decorated result.
     */
    default int order() {
        return 0;
    }

    default <T extends AgentModels.AgentRouting> T decorate(T t, DecoratorContext context) {
        return t;
    }

    default <T extends AgentModels.AgentResult> T decorate(T t, DecoratorContext context) {
        return t;
    }

    default <T extends AgentModels.AgentRequest> T decorateRequestResult(T t, DecoratorContext context) {
        return t;
    }

}
