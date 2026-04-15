package com.hayden.multiagentide.agent.decorator.request;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.agent.WorkflowGraphService;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.model.nodes.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
@Slf4j
public class StartWorkflowRequestDecorator implements RequestDecorator, DispatchedAgentRequestDecorator {

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
    public <T extends AgentModels.AgentRequest> T decorate(T request, DecoratorContext context) {
        if (request == null || workflowGraphService == null || context == null) {
            return request;
        }

        OperationContext operationContext = context.operationContext();
        if (operationContext == null) {
            return request;
        }

        switch (request) {
            case AgentModels.OrchestratorRequest req -> storeRunning(operationContext, workflowGraphService.startOrchestrator(operationContext));
            case AgentModels.OrchestratorCollectorRequest req -> storeRunning(operationContext, workflowGraphService.startOrchestratorCollector(operationContext, req));
            case AgentModels.DiscoveryOrchestratorRequest req -> storeRunning(operationContext, workflowGraphService.startDiscoveryOrchestrator(operationContext, req));
            case AgentModels.DiscoveryCollectorRequest req -> storeRunning(operationContext, workflowGraphService.startDiscoveryCollector(operationContext, req));
            case AgentModels.PlanningOrchestratorRequest req -> storeRunning(operationContext, workflowGraphService.startPlanningOrchestrator(operationContext, req));
            case AgentModels.PlanningCollectorRequest req -> storeRunning(operationContext, workflowGraphService.startPlanningCollector(operationContext, req));
            case AgentModels.TicketOrchestratorRequest req -> storeRunning(operationContext, workflowGraphService.startTicketOrchestrator(operationContext, req));
            case AgentModels.TicketCollectorRequest req -> storeRunning(operationContext, workflowGraphService.startTicketCollector(operationContext, req));
            case AgentModels.DiscoveryAgentRequest req -> {
                DiscoveryDispatchAgentNode parent = requireNode(operationContext, req,
                        () -> workflowGraphService.requireDiscoveryDispatch(operationContext));

                if (parent == null) {
                    return failNoParent(req.contextId(), request);
                }
                String focus = firstNonBlank(req.subdomainFocus(), "Discovery");
                String goal = firstNonBlank(req.goal());
                storeRunning(operationContext, workflowGraphService.startDiscoveryAgent(parent, goal, focus, req));
            }
            case AgentModels.PlanningAgentRequest req -> {
                PlanningDispatchAgentNode parent = requireNode(operationContext, req,
                        () -> workflowGraphService.requirePlanningDispatch(operationContext));

                if (parent == null) {
                    return failNoParent(req.contextId(), request);
                }
                storeRunning(operationContext, workflowGraphService.startPlanningAgent(parent, req));
            }
            case AgentModels.TicketAgentRequest req -> {
                TicketDispatchAgentNode parent = requireNode(operationContext, req,
                        () -> workflowGraphService.requireTicketDispatch(operationContext));
                if (parent == null) {
                    return failNoParent(req.contextId(), request);
                }
                storeRunning(operationContext, workflowGraphService.startTicketAgent(parent, req));
            }
            case AgentModels.AiFilterRequest req -> {
                String chatSessionKey = req.chatKey() != null ? req.chatKey().value() : null;
                storeRunning(operationContext,
                        workflowGraphService.startDataLayerOperation(req.contextId(), "AiFilter", chatSessionKey));
            }
            case AgentModels.AiPropagatorRequest req -> {
                String chatSessionKey = req.chatKey() != null ? req.chatKey().value() : null;
                storeRunning(operationContext,
                        workflowGraphService.startDataLayerOperation(req.contextId(), "AiPropagator", chatSessionKey));
            }
            case AgentModels.AiTransformerRequest req -> {
                String chatSessionKey = req.chatKey() != null ? req.chatKey().value() : null;
                storeRunning(operationContext,
                        workflowGraphService.startDataLayerOperation(req.contextId(), "AiTransformer", chatSessionKey));
            }
            case AgentModels.CommitAgentRequest req -> {
                String chatSessionKey = req.chatKey() != null ? req.chatKey().value() : null;
                storeRunning(operationContext,
                        workflowGraphService.startDataLayerOperation(req.contextId(), "CommitAgent", chatSessionKey));
            }
            case AgentModels.MergeConflictRequest req -> {
                String chatSessionKey = req.chatKey() != null ? req.chatKey().value() : null;
                storeRunning(operationContext,
                        workflowGraphService.startDataLayerOperation(req.contextId(), "MergeConflictAgent", chatSessionKey));
            }
            case AgentModels.ContextManagerRequest req ->
                    storeRequiredNode(operationContext, req,
                            () -> workflowGraphService.requireOrchestrator(operationContext));
            case AgentModels.ContextManagerRoutingRequest req ->
                    storeRequiredNode(operationContext, req,
                            () -> workflowGraphService.requireOrchestrator(operationContext));
            case AgentModels.DiscoveryAgentRequests req ->
                    storeRunning(operationContext, workflowGraphService.startDiscoveryDispatch(operationContext, req));
            case AgentModels.PlanningAgentRequests req ->
                    storeRunning(operationContext, workflowGraphService.startPlanningDispatch(operationContext, req));
            case AgentModels.ResultsRequest req -> storeResultsNode(operationContext, req);
            case AgentModels.TicketAgentRequests req ->
                    storeRunning(operationContext, workflowGraphService.startTicketDispatch(operationContext, req));
            case AgentModels.InterruptRequest.ContextManagerInterruptRequest req -> workflowGraphService.handleContextManagerInterrupt(operationContext, req);
            case AgentModels.InterruptRequest.OrchestratorInterruptRequest req -> workflowGraphService.handleOrchestratorInterrupt(operationContext, req);
            case AgentModels.InterruptRequest.DiscoveryOrchestratorInterruptRequest req -> workflowGraphService.handleDiscoveryInterrupt(operationContext, req);
            case AgentModels.InterruptRequest.PlanningOrchestratorInterruptRequest req -> workflowGraphService.handlePlanningInterrupt(operationContext, req);
            case AgentModels.InterruptRequest.TicketOrchestratorInterruptRequest req -> workflowGraphService.handleTicketInterrupt(operationContext, req);
            case AgentModels.InterruptRequest.DiscoveryAgentInterruptRequest req ->
                    handleInterrupt(operationContext, req,
                            () -> workflowGraphService.requireDiscoveryDispatch(operationContext));
            case AgentModels.InterruptRequest.PlanningAgentInterruptRequest req ->
                    handleInterrupt(operationContext, req,
                            () -> workflowGraphService.requirePlanningDispatch(operationContext));
            case AgentModels.InterruptRequest.TicketAgentInterruptRequest req ->
                    handleInterrupt(operationContext, req,
                            () -> workflowGraphService.requireTicketDispatch(operationContext));
            case AgentModels.InterruptRequest.DiscoveryAgentDispatchInterruptRequest req ->
                    handleInterrupt(operationContext, req,
                            () -> workflowGraphService.requireDiscoveryDispatch(operationContext));
            case AgentModels.InterruptRequest.DiscoveryCollectorInterruptRequest req ->
                    handleInterrupt(operationContext, req,
                            () -> workflowGraphService.requireDiscoveryCollector(operationContext));
            case AgentModels.InterruptRequest.OrchestratorCollectorInterruptRequest req ->
                    handleInterrupt(operationContext, req,
                            () -> workflowGraphService.requireOrchestratorCollector(operationContext));
            case AgentModels.InterruptRequest.PlanningAgentDispatchInterruptRequest req ->
                    handleInterrupt(operationContext, req,
                            () -> workflowGraphService.requirePlanningDispatch(operationContext));
            case AgentModels.InterruptRequest.PlanningCollectorInterruptRequest req ->
                    handleInterrupt(operationContext, req,
                            () -> workflowGraphService.requirePlanningCollector(operationContext));
            case AgentModels.InterruptRequest.QuestionAnswerInterruptRequest req ->
                    handleInterrupt(operationContext, req,
                            () -> workflowGraphService.requireOrchestrator(operationContext));
            case AgentModels.InterruptRequest.TicketAgentDispatchInterruptRequest req ->
                    handleInterrupt(operationContext, req,
                            () -> workflowGraphService.requireTicketDispatch(operationContext));
            case AgentModels.InterruptRequest.TicketCollectorInterruptRequest req ->
                    handleInterrupt(operationContext, req,
                            () -> workflowGraphService.requireTicketCollector(operationContext));
            case AgentModels.AgentToAgentRequest req -> {
                AgentToAgentConversationNode node = workflowGraphService.startAgentCall(req);
                storeRunning(operationContext, node);
                if (node != null) {
                    String sourceKey = req.sourceAgentKey() != null ? req.sourceAgentKey().value() : "";
                    String targetKey = req.targetAgentKey() != null ? req.targetAgentKey().value() : "";
                    eventBus.publish(new Events.AgentCallStartedEvent(
                            UUID.randomUUID().toString(),
                            Instant.now(),
                            node.nodeId(),
                            sourceKey,
                            targetKey,
                            node.nodeId()
                    ));
                }
            }
            case AgentModels.AgentToControllerRequest ignored -> {
                // Communication request: no dedicated workflow node needed.
            }
            case AgentModels.ControllerToAgentRequest ignored -> {
                // Communication request: no dedicated workflow node needed.
            }
        }

        return request;
    }

    private static <T extends AgentModels.AgentRequest> T failNoParent(ArtifactKey req, T request) {
        log.error("Could not find parent for PlanningAgentRequest {}.",
                req);
        return request;
    }

    private void storeRunning(OperationContext context, GraphNode node) {
        if (context == null || node == null) {
            return;
        }
        workflowGraphService.ensureNodeRecorded(node);
    }

    private void storeRequiredNode(
            OperationContext context,
            AgentModels.AgentRequest request,
            Supplier<? extends GraphNode> requiredNode
    ) {
        GraphNode node = requireNode(context, request, requiredNode);
        storeRunning(context, node);
    }

    private void storeResultsNode(OperationContext context, AgentModels.ResultsRequest request) {
        if (context == null || request == null) {
            return;
        }
        switch (request) {
            case AgentModels.DiscoveryAgentResults ignored ->
                    storeResultsNodeWithFallback(
                            context,
                            request,
                            () -> workflowGraphService.requireDiscoveryOrchestrator(context),
                            () -> workflowGraphService.requireDiscoveryCollector(context),
                            "discovery"
                    );
            case AgentModels.PlanningAgentResults ignored ->
                    storeResultsNodeWithFallback(
                            context,
                            request,
                            () -> workflowGraphService.requirePlanningOrchestrator(context),
                            () -> workflowGraphService.requirePlanningCollector(context),
                            "planning"
                    );
            case AgentModels.TicketAgentResults ignored ->
                    storeResultsNodeWithFallback(
                            context,
                            request,
                            () -> workflowGraphService.requireTicketOrchestrator(context),
                            () -> workflowGraphService.requireTicketCollector(context),
                            "ticket"
                    );
        }
    }

    private void storeResultsNodeWithFallback(
            OperationContext context,
            AgentModels.ResultsRequest request,
            Supplier<? extends GraphNode> primaryNode,
            Supplier<? extends GraphNode> fallbackNode,
            String workflowType
    ) {
        GraphNode node = null;
        try {
            node = primaryNode.get();
        } catch (RuntimeException expectedMissingCollector) {
            log.debug(
                    "Collector node unavailable for {} results (contextId={}), falling back to orchestrator: {}",
                    workflowType,
                    request != null && request.contextId() != null ? request.contextId().value() : "unknown",
                    expectedMissingCollector.getMessage()
            );
        }

        if (node == null) {
            try {
                node = fallbackNode.get();
            } catch (RuntimeException e) {
                String message = "Failed to resolve node for " + requestType(request)
                        + " (collector + orchestrator fallback): " + e.getMessage();
                log.error(message, e);
                workflowGraphService.emitDecoratorError(context, message);
                return;
            }
        }

        storeRunning(context, node);
    }

    private void handleInterrupt(
            OperationContext context,
            AgentModels.InterruptRequest interruptRequest,
            Supplier<? extends GraphNode> requiredNode
    ) {
        if (context == null || interruptRequest == null) {
            return;
        }

        GraphNode originNode = requireNode(context, interruptRequest, requiredNode);

        if (originNode == null) {
            return;
        }
        workflowGraphService.handleAgentInterrupt(originNode, interruptRequest);
    }

    private <T extends GraphNode> T requireNode(
            OperationContext context,
            AgentModels.AgentRequest request,
            Supplier<T> requiredNode
    ) {
        if (context == null || requiredNode == null) {
            return null;
        }
        try {
            return requiredNode.get();
        } catch (RuntimeException e) {
            String message = "Failed to resolve node for " + requestType(request) + ": " + e.getMessage();
            log.error(message, e);
            workflowGraphService.emitDecoratorError(context, message);
            return null;
        }
    }

    private static String requestType(AgentModels.AgentRequest request) {
        return request != null ? request.getClass().getSimpleName() : "UnknownRequest";
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
