package com.hayden.multiagentide.agent.decorator.request;

import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.propagation.integration.ActionRequestPropagationIntegration;
import com.hayden.multiagentide.agent.AgentModels;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PropagateActionRequestDecorator implements RequestDecorator, DispatchedAgentRequestDecorator, ResultsRequestDecorator {

    private final ActionRequestPropagationIntegration integration;

    @Override
    public int order() {
        return 10_000;
    }

    @Override
    public <T extends AgentModels.ResultsRequest> T decorate(T resultsRequest, DecoratorContext context) {
        return integration.propagate(resultsRequest, context.agentName(), context.actionName(), context.methodName(), context.operationContext());
    }

    @Override
    public <T extends AgentModels.AgentRequest> T decorate(T request, DecoratorContext context) {
        if (request instanceof AgentModels.AgentToAgentRequest) {
            return request;
        }
        return integration.propagate(request, context.agentName(), context.actionName(), context.methodName(), context.operationContext());
    }
}
