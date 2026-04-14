package com.hayden.multiagentide.agent.decorator.result;

import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.agent.AgentModels;

public interface FinalResultDecorator extends ResultDecorator {

    record FinalResultDecoratorContext(AgentModels.AgentRequest originalRequest, DecoratorContext decoratorContext) {
    }

    default <T extends AgentModels.AgentRouting> T decorateFinalResult(T t, FinalResultDecoratorContext context) {
        return this.decorate(t, context.decoratorContext);
    }

    default <T extends AgentModels.AgentResult> T decorateFinalResult(T t, FinalResultDecoratorContext context) {
        return this.decorate(t, context.decoratorContext);
    }

}
