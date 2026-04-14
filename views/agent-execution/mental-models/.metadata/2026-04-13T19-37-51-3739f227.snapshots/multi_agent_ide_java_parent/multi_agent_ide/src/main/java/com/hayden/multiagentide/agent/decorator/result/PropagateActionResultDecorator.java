package com.hayden.multiagentide.agent.decorator.result;

import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.propagation.integration.ActionResponsePropagationIntegration;
import com.hayden.multiagentide.agent.AgentModels;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PropagateActionResultDecorator implements ResultDecorator, DispatchedAgentResultDecorator, FinalResultDecorator {

    private final ActionResponsePropagationIntegration integration;

    @Override
    public int order() {
        return 10_002;
    }

    @Override
    public <T extends AgentModels.AgentRouting> T decorate(T t, DecoratorContext context) {
        if (t instanceof AgentModels.AgentCallRouting) {
            return t;
        }
        return integration.propagate(
                t,
                context.agentName(),
                context.actionName(),
                context.methodName(),
                context.operationContext()
        );
    }

    @Override
    public <T extends AgentModels.AgentResult> T decorate(T t, DecoratorContext context) {
        return integration.propagate(
                t,
                context.agentName(),
                context.actionName(),
                context.methodName(),
                context.operationContext()
        );
    }

    @Override
    public <T extends AgentModels.AgentRequest> T decorateRequestResult(T t, DecoratorContext context) {
        return integration.propagateRequestResult(
                t,
                context.agentName(),
                context.actionName(),
                context.methodName(),
                context.operationContext()
        );
    }

    @Override
    public <T extends AgentModels.AgentResult> T decorateFinalResult(T t, FinalResultDecoratorContext context) {
        return integration.propagate(
                t,
                context.decoratorContext().agentName(),
                context.decoratorContext().actionName(),
                context.decoratorContext().methodName(),
                context.decoratorContext().operationContext()
        );
    }
}
