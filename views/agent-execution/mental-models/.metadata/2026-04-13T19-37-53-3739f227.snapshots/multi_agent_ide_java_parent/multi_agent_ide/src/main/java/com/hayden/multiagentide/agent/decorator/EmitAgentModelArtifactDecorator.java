package com.hayden.multiagentide.agent.decorator;

import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.agent.decorator.request.DispatchedAgentRequestDecorator;
import com.hayden.multiagentide.agent.decorator.request.RequestDecorator;
import com.hayden.multiagentide.agent.decorator.result.DispatchedAgentResultDecorator;
import com.hayden.multiagentide.agent.decorator.result.FinalResultDecorator;
import com.hayden.multiagentide.agent.decorator.result.ResultDecorator;
import com.hayden.multiagentide.artifacts.ArtifactEmissionService;
import com.hayden.multiagentide.artifacts.ExecutionScopeService;
import com.hayden.multiagentide.agent.AgentModels;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmitAgentModelArtifactDecorator implements
        RequestDecorator, ResultDecorator, FinalResultDecorator,
        DispatchedAgentResultDecorator, DispatchedAgentRequestDecorator {

    private final ExecutionScopeService exec;

    private final ArtifactEmissionService emissionService;

    @Override
    public int order() {
        return 100_000_000;
    }

    @Override
    public <T extends AgentModels.AgentRouting> T decorateFinalResult(T t, FinalResultDecoratorContext context) {
        return t;
    }

    @Override
    public <T extends AgentModels.AgentRouting> T decorate(T t, DecoratorContext context) {
        return t;
    }

    @Override
    public <T extends AgentModels.AgentResult> T decorateFinalResult(T t, FinalResultDecoratorContext context) {
        emissionService.emitAgentModel(t, context.decoratorContext().hashContext());
        return t;
    }

    @Override
    public <T extends AgentModels.AgentRequest> T decorate(T t, DecoratorContext context) {
        emissionService.emitAgentModel(t, context.hashContext());
        return t;
    }

    @Override
    public <T extends AgentModels.AgentResult> T decorate(T t, DecoratorContext context) {
        emissionService.emitAgentModel(t, context.hashContext());
        return t;
    }

    @Override
    public <T extends AgentModels.AgentRequest> T decorateRequestResult(T t, DecoratorContext context) {
        emissionService.emitAgentModel(t, context.hashContext());
        return t;
    }
}
