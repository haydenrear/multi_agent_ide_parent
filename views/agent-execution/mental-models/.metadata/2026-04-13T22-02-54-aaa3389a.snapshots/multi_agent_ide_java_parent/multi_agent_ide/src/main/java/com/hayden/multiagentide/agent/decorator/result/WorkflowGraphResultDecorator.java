package com.hayden.multiagentide.agent.decorator.result;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.multiagentide.agent.*;
import com.hayden.multiagentide.model.nodes.*;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowGraphResultDecorator implements ResultDecorator, DispatchedAgentResultDecorator {

    private final WorkflowGraphService workflowGraphService;

    private EventBus eventBus;

    @Autowired
    @Lazy
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public int order() {
        return 10_000;
    }

    @Override
    public <T extends AgentModels.AgentRouting> T decorate(T t, DecoratorContext context) {
        if (t == null || workflowGraphService == null || context == null) {
            return t;
        }

        OperationContext operationContext = context.operationContext();

        switch (t) {
            case AgentModels.OrchestratorRouting routing -> {
                OrchestratorNode running = requireNode(operationContext, routing,
                        () -> workflowGraphService.requireOrchestrator(operationContext));
                if (running != null) {
                    workflowGraphService.pendingOrchestrator(running, routing);
                }
            }
            case AgentModels.OrchestratorCollectorRouting routing -> {
                CollectorNode running = requireNode(operationContext, routing,
                        () -> workflowGraphService.requireOrchestratorCollector(operationContext));
                if (running != null) {
                    workflowGraphService.pendingOrchestratorCollector(operationContext, running, routing);
                }
            }
            case AgentModels.DiscoveryOrchestratorRouting routing -> {
                DiscoveryOrchestratorNode running = requireNode(operationContext, routing,
                        () -> workflowGraphService.requireDiscoveryOrchestrator(operationContext));
                if (running != null) {
                    workflowGraphService.pendingDiscoveryOrchestrator(running, routing);
                }
            }
            case AgentModels.DiscoveryCollectorRouting routing -> {
                DiscoveryCollectorNode running = requireNode(operationContext, routing,
                        () -> workflowGraphService.requireDiscoveryCollector(operationContext));
                if (running != null) {
                    workflowGraphService.pendingDiscoveryCollector(operationContext, running, routing);
                }
            }
            case AgentModels.PlanningOrchestratorRouting routing -> {
                PlanningOrchestratorNode running = requireNode(operationContext, routing,
                        () -> workflowGraphService.requirePlanningOrchestrator(operationContext));
                if (running != null) {
                    workflowGraphService.pendingPlanningOrchestrator(running, routing);
                }
            }
            case AgentModels.PlanningCollectorRouting routing -> {
                PlanningCollectorNode running = requireNode(operationContext, routing,
                        () -> workflowGraphService.requirePlanningCollector(operationContext));
                if (running != null) {
                    workflowGraphService.pendingPlanningCollector(operationContext, running, routing);
                }
            }
            case AgentModels.TicketOrchestratorRouting routing -> {
                TicketOrchestratorNode running = requireNode(operationContext, routing,
                        () -> workflowGraphService.requireTicketOrchestrator(operationContext));
                if (running != null) {
                    workflowGraphService.pendingTicketOrchestrator(running, routing);
                }
            }
            case AgentModels.TicketCollectorRouting routing -> {
                TicketCollectorNode running = requireNode(operationContext, routing,
                        () -> workflowGraphService.requireTicketCollector(operationContext));
                if (running != null) {
                    workflowGraphService.pendingTicketCollector(operationContext, running, routing);
                }
            }
            case AgentModels.DiscoveryAgentRouting routing -> {
                if (routing.interruptRequest() != null) {
                    GraphNode originNode = requireNode(operationContext, routing,
                            () -> workflowGraphService.requireDiscoveryDispatch(operationContext));
                    handleRoutingInterrupt(operationContext, routing.interruptRequest(), originNode, routing);
                } else if (routing.agentResult() != null) {
                    DiscoveryNode running = resolveRunning(context, routing, DiscoveryNode.class);
                    if (running == null) {
                        reportMissingNode(operationContext, routing, null);
                    } else {
                        workflowGraphService.completeDiscoveryAgent(routing.agentResult(), running.nodeId());
                    }
                }
            }
            case AgentModels.PlanningAgentRouting routing -> {
                if (routing.interruptRequest() != null) {
                    GraphNode originNode = requireNode(operationContext, routing,
                            () -> workflowGraphService.requirePlanningDispatch(operationContext));
                    handleRoutingInterrupt(operationContext, routing.interruptRequest(), originNode, routing);
                } else if (routing.agentResult() != null) {
                    PlanningNode running = resolveRunning(context, routing, PlanningNode.class);
                    if (running == null) {
                        reportMissingNode(operationContext, routing, null);
                    } else {
                        workflowGraphService.completePlanningAgent(running, routing.agentResult());
                    }
                }
            }
            case AgentModels.TicketAgentRouting routing -> {
                if (routing.interruptRequest() != null) {
                    GraphNode originNode = requireNode(operationContext, routing,
                            () -> workflowGraphService.requireTicketDispatch(operationContext));
                    handleRoutingInterrupt(operationContext, routing.interruptRequest(), originNode, routing);
                } else if (routing.agentResult() != null) {
                    TicketNode running = resolveRunning(context, routing, TicketNode.class);
                    if (running == null) {
                        reportMissingNode(operationContext, routing, null);
                    } else {
                        workflowGraphService.completeTicketAgent(running, routing.agentResult());
                    }
                }
            }
            case AgentModels.DiscoveryAgentDispatchRouting routing -> {
                GraphNode originNode = requireNode(operationContext, routing,
                        () -> workflowGraphService.requireDiscoveryDispatch(operationContext));
                boolean interrupted = handleRoutingInterrupt(operationContext, routing.interruptRequest(), originNode,
                        routing);
                if (!interrupted) {
                    handleRoutingResult(operationContext, routing, originNode);
                }
            }
            case AgentModels.PlanningAgentDispatchRouting routing -> {
                GraphNode originNode = requireNode(operationContext, routing,
                        () -> workflowGraphService.requirePlanningDispatch(operationContext));
                boolean interrupted = handleRoutingInterrupt(operationContext, routing.interruptRequest(), originNode,
                        routing);
                if (!interrupted) {
                    handleRoutingResult(operationContext, routing, originNode);
                }
            }
            case AgentModels.TicketAgentDispatchRouting routing -> {
                GraphNode originNode = requireNode(operationContext, routing,
                        () -> workflowGraphService.requireTicketDispatch(operationContext));
                boolean interrupted = handleRoutingInterrupt(operationContext, routing.interruptRequest(), originNode,
                        routing);
                if (!interrupted) {
                    handleRoutingResult(operationContext, routing, originNode);
                }
            }
            case AgentModels.ContextManagerResultRouting routing -> {
                GraphNode targetNode = resolveContextManagerRoutingTarget(operationContext, routing);
                boolean interrupted = handleRoutingInterrupt(operationContext, routing.interruptRequest(), targetNode,
                        routing);
                if (!interrupted) {
                    handleRoutingResult(operationContext, routing, targetNode);
                }
            }
            case AgentModels.InterruptRouting routing -> {
                GraphNode targetNode = resolveInterruptRoutingTarget(operationContext, routing);
                handleRoutingResult(operationContext, routing, targetNode);
            }
            case AgentModels.AgentCallRouting routing -> {
                // AgentToAgentRequest is filtered out by findLastWorkflowRequest (used by
                // getLastFromHistory with AgentRequest.class), so resolveRunning's generic
                // DecoratorContext path won't find it.  Look it up directly by type instead.
                AgentToAgentConversationNode running = resolveRunningByRequestType(
                        context, routing, AgentModels.AgentToAgentRequest.class, AgentToAgentConversationNode.class);
                if (running != null) {
                    workflowGraphService.completeAgentCall(running, routing);
                    eventBus.publish(new Events.AgentCallCompletedEvent(
                            UUID.randomUUID().toString(),
                            Instant.now(),
                            running.nodeId(),
                            running.nodeId()
                    ));
                } else {
                    reportMissingNode(operationContext, routing, null);
                }
            }
            case AgentModels.ControllerCallRouting routing -> {
                log.debug("ControllerCallRouting result received — node completion handled by interrupt resolution flow");
            }
            case AgentModels.ControllerResponseRouting routing -> {
                log.debug("ControllerResponseRouting result received — node completion handled by controller endpoint");
            }
        }

        return t;
    }

    @Override
    public <T extends AgentModels.AgentResult> T decorate(T t, DecoratorContext context) {
        if (t == null || workflowGraphService == null || context == null) {
            return t;
        }
        switch (t) {
            case AgentModels.MergeConflictResult ignored ->
                    handleDataLayerOperationNode(context);
            case AgentModels.CommitAgentResult ignored ->
                    handleDataLayerOperationNode(context);
            case AgentModels.AiTransformerResult ignored ->
                    handleDataLayerOperationNode(context);
            case AgentModels.AiPropagatorResult ignored ->
                    handleDataLayerOperationNode(context);
            case AgentModels.AiFilterResult ignored ->
                    handleDataLayerOperationNode(context);
            default -> {
                // Other result types handled elsewhere
            }
        }
        return t;
    }

    private void handleDataLayerOperationNode(DecoratorContext context) {
        String nodeId = resolveResultNodeId(context);
        if (nodeId != null) {
            workflowGraphService.findNodeById(nodeId, DataLayerOperationNode.class)
                    .ifPresent(workflowGraphService::completeDataLayerOperation);
        }
    }

    private String resolveResultNodeId(DecoratorContext context) {
        AgentContext requestContext = null;
        if (context.agentRequest() instanceof AgentContext ac) {
            requestContext = ac;
        } else if (context.lastRequest() instanceof AgentContext lc) {
            requestContext = lc;
        }
        if (requestContext == null) {
            return null;
        }
        ArtifactKey contextId = requestContext.contextId();
        return contextId != null ? contextId.value() : null;
    }

    private <T extends GraphNode> T resolveRunning(
            DecoratorContext decoratorContext,
            AgentModels.AgentRouting routing,
            Class<T> type
    ) {
        String nodeId = resolveRequestNodeId(decoratorContext, routing);
        if (nodeId == null || nodeId.isBlank()) {
            return null;
        }
        return workflowGraphService.findNodeById(nodeId, type).orElse(null);
    }

    /**
     * Resolve the running node by looking up the request directly from the blackboard
     * by its concrete type. This bypasses the {@code findLastWorkflowRequest} filter
     * which excludes communication request types (AgentToAgentRequest, etc.).
     */
    private <R extends AgentModels.AgentRequest & AgentContext, N extends GraphNode> N resolveRunningByRequestType(
            DecoratorContext decoratorContext,
            AgentModels.AgentRouting routing,
            Class<R> requestType,
            Class<N> nodeType
    ) {
        if (decoratorContext == null || decoratorContext.operationContext() == null) {
            return null;
        }
        R request = BlackboardHistory.getEntireBlackboardHistory(decoratorContext.operationContext()) != null
                ? BlackboardHistory.getEntireBlackboardHistory(decoratorContext.operationContext()).getLastOfType(requestType)
                : null;
        if (request == null) {
            reportMissingNode(decoratorContext.operationContext(), routing,
                    new IllegalStateException("No " + requestType.getSimpleName() + " found on blackboard"));
            return null;
        }
        ArtifactKey contextId = request.contextId();
        if (contextId == null || contextId.value() == null || contextId.value().isBlank()) {
            reportMissingNode(decoratorContext.operationContext(), routing,
                    new IllegalStateException("Missing contextId on " + requestType.getSimpleName()));
            return null;
        }
        return workflowGraphService.findNodeById(contextId.value(), nodeType).orElse(null);
    }

    private String resolveRequestNodeId(DecoratorContext decoratorContext, AgentModels.AgentRouting routing) {
        if (decoratorContext == null) {
            return null;
        }
        AgentContext requestContext = null;
        if (decoratorContext.agentRequest() instanceof AgentContext agentRequestContext) {
            requestContext = agentRequestContext;
        } else if (decoratorContext.lastRequest() instanceof AgentContext lastRequestContext) {
            requestContext = lastRequestContext;
        }
        if (requestContext == null) {
            reportMissingNode(
                    decoratorContext.operationContext(),
                    routing,
                    new IllegalStateException("Missing agent request context in DecoratorContext")
            );
            return null;
        }
        ArtifactKey contextId = requestContext.contextId();
        if (contextId == null || contextId.value() == null || contextId.value().isBlank()) {
            reportMissingNode(
                    decoratorContext.operationContext(),
                    routing,
                    new IllegalStateException("Missing request contextId for " + routingType(routing))
            );
            return null;
        }
        return contextId.value();
    }

    private boolean handleRoutingInterrupt(
            OperationContext context,
            AgentModels.InterruptRequest interruptRequest,
            GraphNode originNode,
            AgentModels.AgentRouting routing
    ) {
        if (context == null || interruptRequest == null) {
            return false;
        }
        if (originNode == null) {
            reportMissingNode(context, routing, null);
            return false;
        }
        workflowGraphService.handleAgentInterrupt(originNode, interruptRequest);
        return true;
    }

    private void handleRoutingResult(
            OperationContext context,
            AgentModels.AgentRouting routing,
            GraphNode node
    ) {
        if (context == null || routing == null) {
            return;
        }
        if (node == null) {
            reportMissingNode(context, routing, null);
            return;
        }
        workflowGraphService.pendingNode(node);
    }

    private <T extends GraphNode> T requireNode(
            OperationContext context,
            AgentModels.AgentRouting routing,
            java.util.function.Supplier<T> requiredNode
    ) {
        if (requiredNode == null) {
            return null;
        }
        try {
            return requiredNode.get();
        } catch (RuntimeException e) {
            reportMissingNode(context, routing, e);
            return null;
        }
    }

    private GraphNode resolveInterruptRoutingTarget(
            OperationContext context,
            AgentModels.InterruptRouting routing
    ) {
        if (routing == null) {
            return null;
        }
        if (routing.orchestratorRequest() != null) {
            return requireNode(context, routing, () -> workflowGraphService.requireOrchestrator(context));
        }
        if (routing.orchestratorCollectorRequest() != null) {
            return requireNode(context, routing, () -> workflowGraphService.requireOrchestratorCollector(context));
        }
        if (routing.discoveryCollectorRequest() != null) {
            return requireNode(context, routing, () -> workflowGraphService.requireDiscoveryCollector(context));
        }
        if (routing.discoveryOrchestratorRequest() != null || routing.discoveryAgentRequests() != null) {
            return routing.discoveryAgentRequests() != null
                    ? requireNode(context, routing, () -> workflowGraphService.requireDiscoveryDispatch(context))
                    : requireNode(context, routing, () -> workflowGraphService.requireDiscoveryOrchestrator(context));
        }
        if (routing.planningCollectorRequest() != null) {
            return requireNode(context, routing, () -> workflowGraphService.requirePlanningCollector(context));
        }
        if (routing.planningOrchestratorRequest() != null || routing.planningAgentRequests() != null) {
            return routing.planningAgentRequests() != null
                    ? requireNode(context, routing, () -> workflowGraphService.requirePlanningDispatch(context))
                    : requireNode(context, routing, () -> workflowGraphService.requirePlanningOrchestrator(context));
        }
        if (routing.ticketCollectorRequest() != null) {
            return requireNode(context, routing, () -> workflowGraphService.requireTicketCollector(context));
        }
        if (routing.ticketOrchestratorRequest() != null || routing.ticketAgentRequests() != null) {
            return routing.ticketAgentRequests() != null
                    ? requireNode(context, routing, () -> workflowGraphService.requireTicketDispatch(context))
                    : requireNode(context, routing, () -> workflowGraphService.requireTicketOrchestrator(context));
        }
        return null;
    }

    private GraphNode resolveContextManagerRoutingTarget(
            OperationContext context,
            AgentModels.ContextManagerResultRouting routing
    ) {
        if (routing == null) {
            return null;
        }
        if (routing.orchestratorRequest() != null) {
            return requireNode(context, routing, () -> workflowGraphService.requireOrchestrator(context));
        }
        if (routing.orchestratorCollectorRequest() != null) {
            return requireNode(context, routing, () -> workflowGraphService.requireOrchestratorCollector(context));
        }
        if (routing.discoveryCollectorRequest() != null) {
            return requireNode(context, routing, () -> workflowGraphService.requireDiscoveryCollector(context));
        }
        if (routing.discoveryOrchestratorRequest() != null
                || routing.discoveryAgentRequest() != null
                || routing.discoveryAgentRequests() != null
                || routing.discoveryAgentResults() != null) {
            if (routing.discoveryAgentRequest() != null || routing.discoveryAgentRequests() != null) {
                return requireNode(context, routing, () -> workflowGraphService.requireDiscoveryDispatch(context));
            }
            if (routing.discoveryAgentResults() != null) {
                return requireNode(context, routing, () -> workflowGraphService.requireDiscoveryCollector(context));
            }
            return requireNode(context, routing, () -> workflowGraphService.requireDiscoveryOrchestrator(context));
        }
        if (routing.planningCollectorRequest() != null) {
            return requireNode(context, routing, () -> workflowGraphService.requirePlanningCollector(context));
        }
        if (routing.planningOrchestratorRequest() != null
                || routing.planningAgentRequest() != null
                || routing.planningAgentRequests() != null
                || routing.planningAgentResults() != null) {
            if (routing.planningAgentRequest() != null || routing.planningAgentRequests() != null) {
                return requireNode(context, routing, () -> workflowGraphService.requirePlanningDispatch(context));
            }
            if (routing.planningAgentResults() != null) {
                return requireNode(context, routing, () -> workflowGraphService.requirePlanningCollector(context));
            }
            return requireNode(context, routing, () -> workflowGraphService.requirePlanningOrchestrator(context));
        }
        if (routing.ticketCollectorRequest() != null) {
            return requireNode(context, routing, () -> workflowGraphService.requireTicketCollector(context));
        }
        if (routing.ticketOrchestratorRequest() != null
                || routing.ticketAgentRequest() != null
                || routing.ticketAgentRequests() != null
                || routing.ticketAgentResults() != null) {
            if (routing.ticketAgentRequest() != null || routing.ticketAgentRequests() != null) {
                return requireNode(context, routing, () -> workflowGraphService.requireTicketDispatch(context));
            }
            if (routing.ticketAgentResults() != null) {
                return requireNode(context, routing, () -> workflowGraphService.requireTicketCollector(context));
            }
            return requireNode(context, routing, () -> workflowGraphService.requireTicketOrchestrator(context));
        }
        return requireNode(context, routing, () -> workflowGraphService.requireOrchestrator(context));
    }

    private void reportMissingNode(OperationContext context, AgentModels.AgentRouting routing, RuntimeException e) {
        String message = "Failed to resolve graph node for " + routingType(routing);
        if (e != null && e.getMessage() != null && !e.getMessage().isBlank()) {
            message = message + ": " + e.getMessage();
        }
        log.error(message, e);
        workflowGraphService.emitDecoratorError(context, message);
    }

    private static String routingType(AgentModels.AgentRouting routing) {
        return routing != null ? routing.getClass().getSimpleName() : "UnknownRouting";
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
