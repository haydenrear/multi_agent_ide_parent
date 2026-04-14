package com.hayden.multiagentide.agent.decorator.result;

import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.service.RequestEnrichment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Request decorator that emits ActionStartedEvent before action execution.
 * Uses Integer.MIN_VALUE ordering to ensure it runs first.
 */
@Component
@RequiredArgsConstructor
public class EnrichResultDecorator implements ResultDecorator {

    private final RequestEnrichment requestEnrichment;

    @Override
    public int order() {
        return -11_000;
    }

    @Override
    public <T extends AgentModels.AgentRouting> T decorate(T t, DecoratorContext context) {
        return t;
    }

    @Override
    public <T extends AgentModels.AgentResult> T decorate(T request, DecoratorContext context) {
        return requestEnrichment.enrich(request, context.operationContext());
    }

}
