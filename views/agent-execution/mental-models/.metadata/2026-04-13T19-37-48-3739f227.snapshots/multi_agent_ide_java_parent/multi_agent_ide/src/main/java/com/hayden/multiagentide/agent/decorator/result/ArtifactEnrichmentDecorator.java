package com.hayden.multiagentide.agent.decorator.result;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.service.RequestEnrichment;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ArtifactEnrichmentDecorator implements ResultDecorator {

    private final RequestEnrichment requestEnrichment;

    @Override
    public int order() {
        return -10_000;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends AgentModels.AgentRouting> T decorate(T t, DecoratorContext context) {
        if (t == null) {
            return null;
        }

        OperationContext operationContext = context.operationContext();

        return switch (t) {
            case AgentModels.ContextManagerResultRouting routing ->
                    (T) routing.toBuilder()
                            .interruptRequest(enrichRequest(routing.interruptRequest(), operationContext))
                            .orchestratorRequest(enrichRequest(routing.orchestratorRequest(), operationContext))
                            .orchestratorCollectorRequest(enrichRequest(routing.orchestratorCollectorRequest(), operationContext))
                            .discoveryOrchestratorRequest(enrichRequest(routing.discoveryOrchestratorRequest(), operationContext))
                            .discoveryCollectorRequest(enrichRequest(routing.discoveryCollectorRequest(), operationContext))
                            .planningOrchestratorRequest(enrichRequest(routing.planningOrchestratorRequest(), operationContext))
                            .planningCollectorRequest(enrichRequest(routing.planningCollectorRequest(), operationContext))
                            .ticketOrchestratorRequest(enrichRequest(routing.ticketOrchestratorRequest(), operationContext))
                            .ticketCollectorRequest(enrichRequest(routing.ticketCollectorRequest(), operationContext))
                            .planningAgentRequest(enrichRequest(routing.planningAgentRequest(), operationContext))
                            .planningAgentRequests(enrichRequest(routing.planningAgentRequests(), operationContext))
                            .planningAgentResults(enrichRequest(routing.planningAgentResults(), operationContext))
                            .ticketAgentRequest(enrichRequest(routing.ticketAgentRequest(), operationContext))
                            .ticketAgentRequests(enrichRequest(routing.ticketAgentRequests(), operationContext))
                            .ticketAgentResults(enrichRequest(routing.ticketAgentResults(), operationContext))
                            .discoveryAgentRequest(enrichRequest(routing.discoveryAgentRequest(), operationContext))
                            .discoveryAgentRequests(enrichRequest(routing.discoveryAgentRequests(), operationContext))
                            .discoveryAgentResults(enrichRequest(routing.discoveryAgentResults(), operationContext))
                            .build();
            case AgentModels.DiscoveryAgentDispatchRouting routing ->
                    (T) routing.toBuilder()
                            .interruptRequest(enrichRequest(routing.interruptRequest(), operationContext))
                            .collectorRequest(enrichRequest(routing.collectorRequest(), operationContext))

                            .build();
            case AgentModels.DiscoveryAgentRouting routing ->
                    (T) routing.toBuilder()
                            .interruptRequest(enrichRequest(routing.interruptRequest(), operationContext))
                            .agentResult(enrichResult(routing.agentResult(), operationContext))
                            .build();
            case AgentModels.DiscoveryCollectorRouting routing ->
                    (T) routing.toBuilder()
                            .interruptRequest(enrichRequest(routing.interruptRequest(), operationContext))
                            .collectorResult(enrichResult(routing.collectorResult(), operationContext))
                            .orchestratorRequest(enrichRequest(routing.orchestratorRequest(), operationContext))
                            .discoveryRequest(enrichRequest(routing.discoveryRequest(), operationContext))
                            .planningRequest(enrichRequest(routing.planningRequest(), operationContext))
                            .build();
            case AgentModels.DiscoveryOrchestratorRouting routing ->
                    (T) routing.toBuilder()
                            .interruptRequest(enrichRequest(routing.interruptRequest(), operationContext))
                            .agentRequests(enrichRequest(routing.agentRequests(), operationContext))
                            .collectorRequest(enrichRequest(routing.collectorRequest(), operationContext))

                            .build();
            case AgentModels.OrchestratorCollectorRouting routing ->
                    (T) routing.toBuilder()
                            .interruptRequest(enrichRequest(routing.interruptRequest(), operationContext))
                            .collectorResult(enrichResult(routing.collectorResult(), operationContext))
                            .orchestratorRequest(enrichRequest(routing.orchestratorRequest(), operationContext))

                            .build();
            case AgentModels.OrchestratorRouting routing ->
                    (T) routing.toBuilder()
                            .interruptRequest(enrichRequest(routing.interruptRequest(), operationContext))
                            .collectorRequest(enrichRequest(routing.collectorRequest(), operationContext))
                            .discoveryOrchestratorRequest(enrichRequest(routing.discoveryOrchestratorRequest(), operationContext))

                            .build();
            case AgentModels.PlanningAgentDispatchRouting routing ->
                    (T) routing.toBuilder()
                            .interruptRequest(enrichRequest(routing.interruptRequest(), operationContext))
                            .planningCollectorRequest(enrichRequest(routing.planningCollectorRequest(), operationContext))

                            .build();
            case AgentModels.PlanningAgentRouting routing ->
                    (T) routing.toBuilder()
                            .interruptRequest(enrichRequest(routing.interruptRequest(), operationContext))
                            .agentResult(enrichResult(routing.agentResult(), operationContext))
                            .build();
            case AgentModels.PlanningCollectorRouting routing ->
                    (T) routing.toBuilder()
                            .orchestratorRequest(enrichRequest(routing.orchestratorRequest(), operationContext))
                            .interruptRequest(enrichRequest(routing.interruptRequest(), operationContext))
                            .collectorResult(enrichResult(routing.collectorResult(), operationContext))
                            .planningRequest(enrichRequest(routing.planningRequest(), operationContext))
                            .ticketOrchestratorRequest(enrichRequest(routing.ticketOrchestratorRequest(), operationContext))

                            .build();
            case AgentModels.PlanningOrchestratorRouting routing ->
                    (T) routing.toBuilder()
                            .interruptRequest(enrichRequest(routing.interruptRequest(), operationContext))
                            .agentRequests(enrichRequest(routing.agentRequests(), operationContext))
                            .collectorRequest(enrichRequest(routing.collectorRequest(), operationContext))

                            .build();
            case AgentModels.TicketAgentDispatchRouting routing ->
                    (T) routing.toBuilder()
                            .interruptRequest(enrichRequest(routing.interruptRequest(), operationContext))
                            .ticketCollectorRequest(enrichRequest(routing.ticketCollectorRequest(), operationContext))

                            .build();
            case AgentModels.TicketAgentRouting routing ->
                    (T) routing.toBuilder()
                            .interruptRequest(enrichRequest(routing.interruptRequest(), operationContext))
                            .agentResult(enrichResult(routing.agentResult(), operationContext))
                            .build();
            case AgentModels.TicketCollectorRouting routing ->
                    (T) routing.toBuilder()
                            .interruptRequest(enrichRequest(routing.interruptRequest(), operationContext))
                            .collectorResult(enrichResult(routing.collectorResult(), operationContext))
                            .ticketRequest(enrichRequest(routing.ticketRequest(), operationContext))
                            .orchestratorCollectorRequest(enrichRequest(routing.orchestratorCollectorRequest(), operationContext))
                            .orchestratorRequest(enrichRequest(routing.orchestratorRequest(), operationContext))

                            .build();
            case AgentModels.TicketOrchestratorRouting routing ->
                    (T) routing.toBuilder()
                            .interruptRequest(enrichRequest(routing.interruptRequest(), operationContext))
                            .agentRequests(enrichRequest(routing.agentRequests(), operationContext))
                            .collectorRequest(enrichRequest(routing.collectorRequest(), operationContext))

                            .build();
            case AgentModels.InterruptRouting routing ->
                    (T) routing.toBuilder()
                            .orchestratorRequest(enrichRequest(routing.orchestratorRequest(), operationContext))
                            .discoveryOrchestratorRequest(enrichRequest(routing.discoveryOrchestratorRequest(), operationContext))
                            .planningOrchestratorRequest(enrichRequest(routing.planningOrchestratorRequest(), operationContext))
                            .ticketOrchestratorRequest(enrichRequest(routing.ticketOrchestratorRequest(), operationContext))

                            .orchestratorCollectorRequest(enrichRequest(routing.orchestratorCollectorRequest(), operationContext))
                            .discoveryCollectorRequest(enrichRequest(routing.discoveryCollectorRequest(), operationContext))
                            .planningCollectorRequest(enrichRequest(routing.planningCollectorRequest(), operationContext))
                            .ticketCollectorRequest(enrichRequest(routing.ticketCollectorRequest(), operationContext))
                            .discoveryAgentRequests(enrichRequest(routing.discoveryAgentRequests(), operationContext))
                            .planningAgentRequests(enrichRequest(routing.planningAgentRequests(), operationContext))
                            .ticketAgentRequests(enrichRequest(routing.ticketAgentRequests(), operationContext))
                            .build();
            case AgentModels.AgentCallRouting ignored -> t;
            case AgentModels.ControllerCallRouting ignored -> t;
            case AgentModels.ControllerResponseRouting ignored -> t;
        };
    }

    private <T extends AgentModels.AgentResult> T enrichResult(T value, OperationContext context) {
        if (value == null) {
            return null;
        }

        return requestEnrichment.enrich(value, context);
    }

    private <T extends AgentModels.AgentRequest> T enrichRequest(T request, OperationContext context) {
        if (request == null) {
            return null;
        }

        return request;
    }
}
