package com.hayden.multiagentide.agent.decorator.request;

import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.service.RequestEnrichment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DispatchedAgentRequestEnrichmentDecorator implements DispatchedAgentRequestDecorator {

    private final RequestEnrichment requestEnrichment;

    @Override
    public int order() {
        return -10_000;
    }

    @Override
    public <T extends AgentModels.AgentRequest> T decorate(T request, DecoratorContext context) {
        return switch (request) {
            case AgentModels.InterruptRequest.DiscoveryAgentInterruptRequest d ->
                    requestEnrichment.enrich((T) d, context.operationContext(), context.lastRequest());
            case AgentModels.InterruptRequest.PlanningAgentInterruptRequest d ->
                    requestEnrichment.enrich((T) d, context.operationContext(), context.lastRequest());
            case AgentModels.InterruptRequest.TicketAgentInterruptRequest d ->
                    requestEnrichment.enrich((T) d, context.operationContext(), context.lastRequest());
            default -> request;
        };
    }
}
