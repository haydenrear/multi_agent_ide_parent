package com.hayden.multiagentide.agent;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.multiagentide.model.nodes.*;
import com.hayden.multiagentide.orchestration.ComputationGraphOrchestrator;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Events;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkflowGraphService {

    private final ComputationGraphOrchestrator computationGraphOrchestrator;
    private final GraphRepository graphRepository;
    private final GraphNodeFactory nodeFactory;

    public synchronized  <T> T resolveState(OperationContext context, Function<WorkflowGraphState, T> t) {
        Optional<BlackboardHistory> bhOpt = Optional.ofNullable(context.last(BlackboardHistory.class));
        T state = bhOpt
                    .flatMap(bh -> bh.fromState(t))
                    .orElse(null);

        if (bhOpt.isEmpty())
            throw new RuntimeException("No blackboard history existed or workflow graph state existed.");

        return state;
    }

    public synchronized void updateState(OperationContext context,
                                         Function<WorkflowGraphState, WorkflowGraphState> state) {
        Optional<BlackboardHistory> bhOpt = Optional.ofNullable(context.last(BlackboardHistory.class));

        bhOpt.ifPresentOrElse(bh -> {
            bh.updateState(state);
        }, () -> {
            log.error("Could not find state to update for blackboard!");
        });
    }

    public OrchestratorNode requireOrchestrator(OperationContext context) {
        return resolveState(context, state -> requireNode(state.orchestratorNodeId(), OrchestratorNode.class, "orchestrator"));
    }

    public DiscoveryOrchestratorNode requireDiscoveryOrchestrator(OperationContext context) {
        return resolveState(context, s -> requireNode(s.discoveryOrchestratorNodeId(), DiscoveryOrchestratorNode.class, "discovery orchestrator"));
    }

    public DiscoveryCollectorNode requireDiscoveryCollector(OperationContext context) {
        return resolveState(context, s -> requireNode(s.discoveryCollectorNodeId(), DiscoveryCollectorNode.class, "discovery collector"));
    }

    public DiscoveryDispatchAgentNode requireDiscoveryDispatch(OperationContext context) {
        return resolveState(context, s -> requireNode(s.discoveryDispatchNodeId(), DiscoveryDispatchAgentNode.class, "discovery dispatch"));
    }

    public PlanningOrchestratorNode requirePlanningOrchestrator(OperationContext context) {
        return resolveState(context, s -> requireNode(s.planningOrchestratorNodeId(), PlanningOrchestratorNode.class, "planning orchestrator"));
    }

    public PlanningCollectorNode requirePlanningCollector(OperationContext context) {
        return resolveState(context, s -> requireNode(s.planningCollectorNodeId(), PlanningCollectorNode.class, "planning collector"));
    }

    public PlanningDispatchAgentNode requirePlanningDispatch(OperationContext context) {
        return resolveState(context, s -> requireNode(s.planningDispatchNodeId(), PlanningDispatchAgentNode.class, "planning dispatch"));
    }

    public TicketOrchestratorNode requireTicketOrchestrator(OperationContext context) {
        return resolveState(context, state -> requireNode(state.ticketOrchestratorNodeId(), TicketOrchestratorNode.class, "ticket orchestrator"));
    }

    public TicketCollectorNode requireTicketCollector(OperationContext context) {
        return resolveState(context, state -> requireNode(state.ticketCollectorNodeId(), TicketCollectorNode.class, "ticket collector"));
    }

    public TicketDispatchAgentNode requireTicketDispatch(OperationContext context) {
        return resolveState(context, state -> requireNode(state.ticketDispatchNodeId(), TicketDispatchAgentNode.class, "ticket dispatch"));
    }

    public CollectorNode requireOrchestratorCollector(OperationContext context) {
        return resolveState(context, state -> requireNode(state.orchestratorCollectorNodeId(), CollectorNode.class, "orchestrator collector"));
    }

    public AgentToAgentConversationNode startAgentCall(
            AgentModels.AgentToAgentRequest request
    ) {
        String parentId = request != null ? request.callingNodeId() : null;
        ArtifactKey contextId = request != null ? request.contextId() : null;
        AgentToAgentConversationNode node = nodeFactory.agentToAgentNode(parentId, request, contextId);
        if (parentId != null && graphRepository.exists(parentId)) {
            computationGraphOrchestrator.addChildNodeAndEmitEvent(parentId, node);
        } else {
            computationGraphOrchestrator.emitNodeAddedEvent(node, parentId);
        }
        return markNodeRunning(node);
    }

    public void completeAgentCall(AgentToAgentConversationNode node, AgentModels.AgentCallRouting routing) {
        if (node == null) {
            return;
        }
        String response = routing != null ? routing.response() : "";
        AgentToAgentConversationNode updated = node.toBuilder()
                .lastUpdatedAt(Instant.now())
                .metadata(new java.util.concurrent.ConcurrentHashMap<>(
                        java.util.Map.of("response", response != null ? response : "")))
                .build();
        persistNode(updated);
        markNodeCompleted(updated);
    }

    public DataLayerOperationNode startDataLayerOperation(
            ArtifactKey contextId,
            String operationType,
            String chatId
    ) {
        // Parent is derived from contextId's parent if it exists in the graph
        String parentId = contextId != null
                ? contextId.parent().map(ArtifactKey::value).filter(graphRepository::exists).orElse(null)
                : null;
        DataLayerOperationNode node = nodeFactory.dataLayerOperationNode(
                parentId, operationType, chatId, contextId);
        if (parentId != null) {
            computationGraphOrchestrator.addChildNodeAndEmitEvent(parentId, node);
        } else {
            computationGraphOrchestrator.emitNodeAddedEvent(node, parentId);
        }
        return markNodeRunning(node);
    }

    public void completeDataLayerOperation(DataLayerOperationNode node) {
        if (node == null) {
            return;
        }
        markNodeCompleted(node);
    }

    public void emitErrorEvent(GraphNode node, String message) {
        if (node == null) {
            return;
        }
        computationGraphOrchestrator.emitErrorEvent(
                node.nodeId(),
                node.title(),
                node.nodeType(),
                message
        );
    }

    public void emitDecoratorError(OperationContext context, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        if (context == null) {
            log.error("Decorator error with no operation context: {}", message);
            return;
        }
        try {
            emitErrorEvent(requireOrchestrator(context), message);
        } catch (RuntimeException e) {
            log.error("Decorator error and failed to emit node error event: {}", message, e);
        }
    }

    public OrchestratorNode startOrchestrator(OperationContext context) {
        OrchestratorNode orchestratorNode = requireOrchestrator(context);
        return markNodeRunning(orchestratorNode);
    }

    public void pendingOrchestrator(OrchestratorNode running, AgentModels.OrchestratorRouting routing) {
        String output = routing != null ? routing.toString() : "";
        OrchestratorNode withOutput = running.withOutput(output);
        persistNode(withOutput);
        if (handleRoutingInterrupt(withOutput, routing != null ? routing.interruptRequest() : null, output)) {
            return;
        }
        markNodePending(withOutput);
    }

    public void completeOrchestratorCollectorResult(
            OperationContext context,
            AgentModels.OrchestratorCollectorResult input
    ) {
        resolveState(context, state -> {
            OrchestratorNode orchestratorNode =
                    requireNode(state.orchestratorNodeId(), OrchestratorNode.class, "orchestrator");
            String consolidated = input != null ? input.consolidatedOutput() : "";
            OrchestratorNode withOutput = consolidated != null && !consolidated.isBlank()
                    ? orchestratorNode.withOutput(consolidated)
                    : orchestratorNode;
            persistNode(withOutput);
            if (state.orchestratorCollectorNodeId() != null) {
                findNode(state.orchestratorCollectorNodeId(), CollectorNode.class)
                        .map(node -> input != null ? node.withResult(input) : node)
                        .ifPresent(this::markNodeCompleted);
            }
            OrchestratorNode completed = markNodeCompleted(withOutput);
            markDescendantNodesCompleted(completed.nodeId());
            return null;
        });
    }

    public CollectorNode startOrchestratorCollector(
            OperationContext context,
            AgentModels.OrchestratorCollectorRequest input
    ) {
        return resolveState(context, state -> {
            OrchestratorNode orchestratorNode = requireOrchestrator(context);
            return startOrReuseRerunnableNode(
                    context,
                    state.orchestratorCollectorNodeId(),
                    input != null ? input.contextId() : null,
                    CollectorNode.class,
                    existing -> existing.toBuilder()
                            .goal(firstNonBlank(input != null ? input.goal() : null, existing.goal()))
                            .parentNodeId(orchestratorNode.nodeId())
                            .lastUpdatedAt(Instant.now())
                            .build(),
                    () -> {
                        CollectorNode collectorNode = nodeFactory.orchestratorCollectorNode(
                                orchestratorNode,
                                input != null ? input.goal() : "",
                                input != null ? input.contextId() : null
                        );
                        computationGraphOrchestrator.addChildNodeAndEmitEvent(orchestratorNode.nodeId(), collectorNode);
                        return collectorNode;
                    },
                    (s, nodeId) -> s.withOrchestratorCollectorNodeId(nodeId)
            );
        });
    }

    public void pendingOrchestratorCollector(
            OperationContext context,
            CollectorNode running,
            AgentModels.OrchestratorCollectorRouting routing
    ) {
        OrchestratorNode orchestratorNode = requireOrchestrator(context);
        AgentModels.OrchestratorCollectorResult collectorResult =
                routing != null ? routing.collectorResult() : null;
        String output = collectorResult != null ? collectorResult.consolidatedOutput() : "";
        CollectorNode withResult = running.withResult(collectorResult).withOutput(output);
        persistNode(withResult);
        if (handleRoutingInterrupt(withResult, routing != null ? routing.interruptRequest() : null, output)) {
            return;
        }
        markNodePending(withResult);
        if (output != null && !output.isBlank()) {
            persistNode(orchestratorNode.withOutput(output));
        }
    }

    public void handleOrchestratorInterrupt(
            OperationContext context,
            AgentModels.InterruptRequest.OrchestratorInterruptRequest request
    ) {
        OrchestratorNode orchestratorNode = requireOrchestrator(context);
        handleRoutingInterrupt(orchestratorNode, request, request != null ? request.reason() : "");
    }

    public void handleContextManagerInterrupt(
            OperationContext context,
            AgentModels.InterruptRequest.ContextManagerInterruptRequest request
    ) {
        OrchestratorNode orchestratorNode = requireOrchestrator(context);
        handleRoutingInterrupt(orchestratorNode, request, request != null ? request.reason() : "");
    }

    public DiscoveryOrchestratorNode startDiscoveryOrchestrator(
            OperationContext context,
            AgentModels.DiscoveryOrchestratorRequest input
    ) {
        return resolveState(context, state -> {
            String parentId = firstNonBlank(state.orchestratorCollectorNodeId(), state.orchestratorNodeId());
            return startOrReuseRerunnableNode(
                    context,
                    state.discoveryOrchestratorNodeId(),
                    input != null ? input.contextId() : null,
                    DiscoveryOrchestratorNode.class,
                    existing -> existing.toBuilder()
                            .goal(firstNonBlank(input != null ? input.goal() : null, existing.goal()))
                            .parentNodeId(parentId)
                            .lastUpdatedAt(Instant.now())
                            .build(),
                    () -> {
                        DiscoveryOrchestratorNode discoveryNode = nodeFactory.discoveryOrchestratorNode(
                                parentId,
                                input != null ? input.goal() : "",
                                input != null ? input.contextId() : null
                        );
                        computationGraphOrchestrator.addChildNodeAndEmitEvent(parentId, discoveryNode);
                        return discoveryNode;
                    },
                    (s, nodeId) -> s.withDiscoveryOrchestratorNodeId(nodeId)
            );
        });
    }

    public void pendingDiscoveryOrchestrator(
            DiscoveryOrchestratorNode running,
            AgentModels.DiscoveryOrchestratorRouting routing
    ) {
        String summary = routing != null ? routing.toString() : "";
        DiscoveryOrchestratorNode updated = running.withContent(summary);
        persistNode(updated);
        if (handleRoutingInterrupt(updated, routing != null ? routing.interruptRequest() : null, summary)) {
            return;
        }
        markNodePending(updated);
    }

    public void handleDiscoveryInterrupt(
            OperationContext context,
            AgentModels.InterruptRequest.DiscoveryOrchestratorInterruptRequest request
    ) {
        resolveState(context, state -> {
            if (state.discoveryOrchestratorNodeId() == null) {
                return null;
            }
            findNode(state.discoveryOrchestratorNodeId(), DiscoveryOrchestratorNode.class)
                    .ifPresent(node -> handleRoutingInterrupt(node, request, request != null ? request.reason() : ""));
            return null;
        });
    }

    public Optional<GraphNode> findNodeForContext(OperationContext context) {
        String nodeId = resolveNodeId(context);
        if (nodeId == null || nodeId.isBlank() || "unknown".equals(nodeId)) {
            return Optional.empty();
        }
        return graphRepository.findById(nodeId);
    }

    public <T extends GraphNode> Optional<T> findNodeById(String nodeId, Class<T> type) {
        return findNode(nodeId, type);
    }

    public void ensureNodeRecorded(GraphNode node) {
        if (node == null || node.nodeId() == null || node.nodeId().isBlank()) {
            return;
        }

        if (!graphRepository.exists(node.nodeId())) {
            String parentNodeId = node.parentNodeId();
            if (parentNodeId != null && !parentNodeId.isBlank() && graphRepository.exists(parentNodeId)) {
                computationGraphOrchestrator.addChildNodeAndEmitEvent(parentNodeId, node);
            } else {
                computationGraphOrchestrator.emitNodeAddedEvent(node, parentNodeId);
            }
        }

        persistNode(node);
    }

    public void handleAgentInterrupt(
            GraphNode originNode,
            AgentModels.InterruptRequest request
    ) {
        if (originNode == null || request == null) {
            log.error("Found agent interrupt where origin node {} request node {} was null.", originNode, request);
            return;
        }
        handleRoutingInterrupt(
                originNode,
                request,
                firstNonBlank(request.reason(), request.contextForDecision()));
    }

    public void pendingNode(GraphNode node) {
        if (node == null) {
            return;
        }
        if (node.status() == Events.NodeStatus.PENDING) {
            persistNode(node);
            return;
        }
        markNodePending(node);
    }

    public DiscoveryDispatchAgentNode startDiscoveryDispatch(
            OperationContext context,
            AgentModels.DiscoveryAgentRequests input
    ) {
        return resolveState(context, state -> {
            DiscoveryOrchestratorNode parent = requireDiscoveryOrchestrator(context);
            return startOrReuseRerunnableNode(
                    context,
                    state.discoveryDispatchNodeId(),
                    input != null ? input.contextId() : null,
                    DiscoveryDispatchAgentNode.class,
                    existing -> existing.withRequests(input)
                            .toBuilder()
                            .parentNodeId(parent.nodeId())
                            .lastUpdatedAt(Instant.now())
                            .build(),
                    () -> {
                        DiscoveryDispatchAgentNode dispatchNode = nodeFactory.discoveryDispatchAgentNode(parent.nodeId(), input);
                        computationGraphOrchestrator.addChildNodeAndEmitEvent(parent.nodeId(), dispatchNode);
                        return dispatchNode;
                    },
                    (s, nodeId) -> s.withDiscoveryDispatchNodeId(nodeId)
            );
        });
    }

    public DiscoveryNode startDiscoveryAgent(
            DiscoveryDispatchAgentNode parent,
            String goal,
            String focus,
            AgentModels.DiscoveryAgentRequest enrichedRequest) {
        String requestGoal = firstNonBlank(goal, enrichedRequest != null ? enrichedRequest.goal() : null);
        String title = "Discover: " + firstNonBlank(focus, enrichedRequest != null ? enrichedRequest.subdomainFocus() : null);
        DiscoveryNode discoveryNode = nodeFactory.discoveryNode(
                parent.nodeId(),
                requestGoal,
                title,
                enrichedRequest,
                enrichedRequest != null ? enrichedRequest.contextId() : null
        );
        return startChildNode(parent.nodeId(), discoveryNode);
    }

    public void completeDiscoveryAgent(AgentModels.DiscoveryAgentResult response,
                                       String nodeId) {
        var runningOpt = graphRepository.findById(nodeId);
        if (runningOpt.isEmpty())
            return;
        if (response == null) {
            markNodeCompleted(runningOpt.get());
            return;
        }

        if (runningOpt.get() instanceof DiscoveryNode running) {
            DiscoveryNode completed = running.withResult(response).withContent(response.output());
            persistNode(completed);
            markNodeCompleted(completed);
        }
    }

    public DiscoveryCollectorNode startDiscoveryCollector(
            OperationContext context,
            AgentModels.DiscoveryCollectorRequest input
    ) {
        return resolveState(context, state -> {
            DiscoveryOrchestratorNode discoveryParent = requireDiscoveryOrchestrator(context);
            return startOrReuseRerunnableNode(
                    context,
                    state.discoveryCollectorNodeId(),
                    input != null ? input.contextId() : null,
                    DiscoveryCollectorNode.class,
                    existing -> existing.toBuilder()
                            .goal(firstNonBlank(input != null ? input.goal() : null, existing.goal()))
                            .parentNodeId(discoveryParent.nodeId())
                            .lastUpdatedAt(Instant.now())
                            .build(),
                    () -> {
                        DiscoveryCollectorNode collectorNode = nodeFactory.discoveryCollectorNode(
                                discoveryParent.nodeId(),
                                input != null ? input.goal() : "",
                                input != null ? input.contextId() : null
                        );
                        computationGraphOrchestrator.addChildNodeAndEmitEvent(discoveryParent.nodeId(), collectorNode);
                        return collectorNode;
                    },
                    (s, nodeId) -> s.withDiscoveryCollectorNodeId(nodeId)
            );
        });
    }

    public void pendingDiscoveryCollector(
            OperationContext context,
            DiscoveryCollectorNode running,
            AgentModels.DiscoveryCollectorRouting routing
    ) {
        DiscoveryOrchestratorNode discoveryParent = requireDiscoveryOrchestrator(context);

        AgentModels.DiscoveryCollectorResult collectorResult =
                routing != null ? routing.collectorResult() : null;

        String output = collectorResult != null ? collectorResult.consolidatedOutput() : "";

        List<CollectedNodeStatus> collectedNodes =
                collectSiblingStatusSnapshots(discoveryParent.nodeId(), DiscoveryNode.class);

        DiscoveryCollectorNode withResult = running
                .withResult(collectorResult)
                .withContent(output)
                .withCollectedNodes(collectedNodes);

        persistNode(withResult);

        if (handleRoutingInterrupt(withResult, routing != null ? routing.interruptRequest() : null, output)) {
            return;
        }

        markNodePending(withResult);
        DiscoveryOrchestratorNode updatedParent = discoveryParent.withContent(output);

        persistNode(updatedParent);

        markNodePending(updatedParent);
    }

    public PlanningOrchestratorNode startPlanningOrchestrator(
            OperationContext context,
            AgentModels.PlanningOrchestratorRequest input
    ) {
         return resolveState(context, state -> {
             String parentId = firstNonBlank(
                     state.orchestratorNodeId()
             );
             return startOrReuseRerunnableNode(
                     context,
                     state.planningOrchestratorNodeId(),
                     input != null ? input.contextId() : null,
                     PlanningOrchestratorNode.class,
                     existing -> existing.toBuilder()
                             .goal(firstNonBlank(input != null ? input.goal() : null, existing.goal()))
                             .parentNodeId(parentId)
                             .lastUpdatedAt(Instant.now())
                             .build(),
                     () -> {
                         PlanningOrchestratorNode planningNode = nodeFactory.planningOrchestratorNode(
                                 parentId,
                                 input != null ? input.goal() : "",
                                 Map.of(),
                                 input != null ? input.contextId() : null
                         );
                         computationGraphOrchestrator.addChildNodeAndEmitEvent(parentId, planningNode);
                         return planningNode;
                     },
                     (s, nodeId) -> s.withPlanningOrchestratorNodeId(nodeId)
             );
         });
    }

    public void pendingPlanningOrchestrator(
            PlanningOrchestratorNode running,
            AgentModels.PlanningOrchestratorRouting routing
    ) {
        String summary = routing != null ? routing.toString() : "";
        PlanningOrchestratorNode updated = running.withPlanContent(summary);
        persistNode(updated);
        if (handleRoutingInterrupt(updated, routing != null ? routing.interruptRequest() : null, summary)) {
            return;
        }
        markNodePending(updated);
    }

    public void handlePlanningInterrupt(
            OperationContext context,
            AgentModels.InterruptRequest.PlanningOrchestratorInterruptRequest request
    ) {
        resolveState(context, state -> {
            if (state.planningOrchestratorNodeId() == null) {
                return null;
            }
            findNode(state.planningOrchestratorNodeId(), PlanningOrchestratorNode.class)
                    .ifPresent(node -> handleRoutingInterrupt(node, request, request != null ? request.reason() : ""));
            return null;
        });
    }

    public PlanningDispatchAgentNode startPlanningDispatch(
            OperationContext context,
            AgentModels.PlanningAgentRequests input
    ) {
        return resolveState(context, state -> {
            PlanningOrchestratorNode parent = requirePlanningOrchestrator(context);
            return startOrReuseRerunnableNode(
                    context,
                    state.planningDispatchNodeId(),
                    input != null ? input.contextId() : null,
                    PlanningDispatchAgentNode.class,
                    existing -> existing.withRequests(input)
                            .toBuilder()
                            .parentNodeId(parent.nodeId())
                            .lastUpdatedAt(Instant.now())
                            .build(),
                    () -> {
                        PlanningDispatchAgentNode dispatchNode = nodeFactory.planningDispatchAgentNode(parent.nodeId(), input);
                        computationGraphOrchestrator.addChildNodeAndEmitEvent(parent.nodeId(), dispatchNode);
                        return dispatchNode;
                    },
                    (s, nodeId) -> s.withPlanningDispatchNodeId(nodeId)
            );
        });
    }

    public PlanningNode startPlanningAgent(
            PlanningDispatchAgentNode parent,
            String goal,
            String title,
            ArtifactKey artifactKey
    ) {
        PlanningNode planningNode = nodeFactory.planningNode(
                parent.nodeId(),
                goal,
                title,
                Map.of(),
                artifactKey
        );
        return startChildNode(parent.nodeId(), planningNode);
    }

    public PlanningNode startPlanningAgent(
            PlanningDispatchAgentNode parent,
            AgentModels.PlanningAgentRequest request
    ) {
        int index = nextChildIndex(parent.nodeId(), PlanningNode.class);
        String title = "Plan segment " + index;
        return startPlanningAgent(parent, Objects.toString(request.goal(), ""), title, request != null ? request.contextId() : null);
    }

    public void completePlanningAgent(PlanningNode running, AgentModels.PlanningAgentResult response) {
        if (response == null) {
            markNodeCompleted(running);
            return;
        }
        PlanningNode completed = running.withResult(response).withPlanContent(response.output());
        persistNode(completed);
        markNodeCompleted(completed);
    }

    public PlanningCollectorNode startPlanningCollector(
            OperationContext context,
            AgentModels.PlanningCollectorRequest input
    ) {
        return resolveState(context, state -> {
            PlanningOrchestratorNode planningParent = requirePlanningOrchestrator(context);
            return startOrReuseRerunnableNode(
                    context,
                    state.planningCollectorNodeId(),
                    input != null ? input.contextId() : null,
                    PlanningCollectorNode.class,
                    existing -> existing.toBuilder()
                            .goal(firstNonBlank(input != null ? input.goal() : null, existing.goal()))
                            .parentNodeId(planningParent.nodeId())
                            .lastUpdatedAt(Instant.now())
                            .build(),
                    () -> {
                        PlanningCollectorNode collectorNode = nodeFactory.planningCollectorNode(
                                planningParent.nodeId(),
                                input != null ? input.goal() : "",
                                input != null ? input.contextId() : null
                        );
                        computationGraphOrchestrator.addChildNodeAndEmitEvent(planningParent.nodeId(), collectorNode);
                        return collectorNode;
                    },
                    (s, nodeId) -> s.withPlanningCollectorNodeId(nodeId)
            );
        });
    }

    public void pendingPlanningCollector(
            OperationContext context,
            PlanningCollectorNode running,
            AgentModels.PlanningCollectorRouting routing
    ) {
        PlanningOrchestratorNode planningParent = requirePlanningOrchestrator(context);
        AgentModels.PlanningCollectorResult collectorResult =
                routing != null ? routing.collectorResult() : null;
        String output = collectorResult != null ? collectorResult.consolidatedOutput() : "";
        List<CollectedNodeStatus> collectedNodes =
                collectSiblingStatusSnapshots(planningParent.nodeId(), PlanningNode.class);
        PlanningCollectorNode withResult = running
                .withResult(collectorResult)
                .withPlanContent(output)
                .withCollectedNodes(collectedNodes);
        persistNode(withResult);
        if (handleRoutingInterrupt(withResult, routing != null ? routing.interruptRequest() : null, output)) {
            return;
        }
        markNodePending(withResult);
        PlanningOrchestratorNode updatedParent = planningParent.withPlanContent(output);
        persistNode(updatedParent);
        markNodePending(updatedParent);
    }

    public TicketOrchestratorNode startTicketOrchestrator(
            OperationContext context,
            AgentModels.TicketOrchestratorRequest input
    ) {
        return resolveState(context, state -> {
            String parentId = firstNonBlank(
                    state.orchestratorNodeId()
            );
            OrchestratorNode root = requireOrchestrator(context);
            return startOrReuseRerunnableNode(
                    context,
                    state.ticketOrchestratorNodeId(),
                    input != null ? input.contextId() : null,
                    TicketOrchestratorNode.class,
                    existing -> existing.toBuilder()
                            .goal(firstNonBlank(input != null ? input.goal() : null, existing.goal()))
                            .parentNodeId(parentId)
                            .lastUpdatedAt(Instant.now())
                            .build(),
                    () -> {

                        TicketOrchestratorNode ticketNode = nodeFactory.ticketOrchestratorNode(
                                parentId,
                                input != null ? input.goal() : "",
                                Map.of(),
                                input != null ? input.contextId() : null
                        );
                        computationGraphOrchestrator.addChildNodeAndEmitEvent(parentId, ticketNode);
                        return ticketNode;
                    },
                    (s, nodeId) -> s.withTicketOrchestratorNodeId(nodeId)
            );
        });
    }

    public void pendingTicketOrchestrator(
            TicketOrchestratorNode running,
            AgentModels.TicketOrchestratorRouting routing
    ) {
        String output = routing != null ? routing.toString() : "";
        TicketOrchestratorNode updated = running.withOutput(output, 0);
        persistNode(updated);
        if (handleRoutingInterrupt(updated, routing != null ? routing.interruptRequest() : null, output)) {
            return;
        }
        markNodePending(updated);
    }

    public void handleTicketInterrupt(
            OperationContext context,
            AgentModels.InterruptRequest.TicketOrchestratorInterruptRequest request
    ) {
        resolveState(context, state -> {
            if (state.ticketOrchestratorNodeId() == null) {
                return null;
            }
            findNode(state.ticketOrchestratorNodeId(), TicketOrchestratorNode.class)
                    .ifPresent(node -> handleRoutingInterrupt(node, request, request != null ? request.reason() : ""));
            return null;
        });
    }

    public TicketDispatchAgentNode startTicketDispatch(
            OperationContext context,
            AgentModels.TicketAgentRequests input
    ) {
        return resolveState(context, state -> {
            TicketOrchestratorNode parent = requireTicketOrchestrator(context);
            return startOrReuseRerunnableNode(
                    context,
                    state.ticketDispatchNodeId(),
                    input != null ? input.contextId() : null,
                    TicketDispatchAgentNode.class,
                    existing -> existing.withRequests(input)
                            .toBuilder()
                            .parentNodeId(parent.nodeId())
                            .lastUpdatedAt(Instant.now())
                            .build(),
                    () -> {
                        TicketDispatchAgentNode dispatchNode = nodeFactory.ticketDispatchAgentNode(parent.nodeId(), input);
                        computationGraphOrchestrator.addChildNodeAndEmitEvent(parent.nodeId(), dispatchNode);
                        return dispatchNode;
                    },
                    (s, nodeId) -> s.withTicketDispatchNodeId(nodeId)
            );
        });
    }

    public TicketNode startTicketAgent(
            TicketDispatchAgentNode parent,
            AgentModels.TicketAgentRequest request,
            int index
    ) {
        TicketOrchestratorNode ticketParent = requireNode(
                parent.parentNodeId(),
                TicketOrchestratorNode.class,
                "ticket orchestrator for dispatch"
        );

        String title = firstNonBlank(request.ticketDetailsFilePath(), "Ticket " + index);
        TicketNode ticketNode = nodeFactory.ticketNode(
                parent.nodeId(),
                title,
                Objects.toString(request.ticketDetails(), ""),
                Map.of(),
                request != null ? request.contextId() : null
        );
        return startChildNode(parent.nodeId(), ticketNode);
    }

    public TicketNode startTicketAgent(
            TicketDispatchAgentNode parent,
            AgentModels.TicketAgentRequest request
    ) {
        int index = nextChildIndex(parent.nodeId(), TicketNode.class);
        return startTicketAgent(parent, request, index);
    }

    public void completeTicketAgent(TicketNode running, AgentModels.TicketAgentResult response) {
        if (response == null) {
            markNodeCompleted(running);
            return;
        }
        TicketNode completed = running.withTicketAgentResult(response).withOutput(response.output());
        persistNode(completed);
        markNodeCompleted(completed);
    }

    public TicketCollectorNode startTicketCollector(
            OperationContext context,
            AgentModels.TicketCollectorRequest input
    ) {
        return resolveState(context, state -> {
            TicketOrchestratorNode ticketParent = requireTicketOrchestrator(context);
            return startOrReuseRerunnableNode(
                    context,
                    state.ticketCollectorNodeId(),
                    input != null ? input.contextId() : null,
                    TicketCollectorNode.class,
                    existing -> existing.toBuilder()
                            .goal(firstNonBlank(input != null ? input.goal() : null, existing.goal()))
                            .parentNodeId(ticketParent.nodeId())
                            .lastUpdatedAt(Instant.now())
                            .build(),
                    () -> {
                        TicketCollectorNode collectorNode = nodeFactory.ticketCollectorNode(
                                ticketParent.nodeId(),
                                input != null ? input.goal() : "",
                                input != null ? input.contextId() : null
                        );
                        computationGraphOrchestrator.addChildNodeAndEmitEvent(ticketParent.nodeId(), collectorNode);
                        return collectorNode;
                    },
                    (s, nodeId) -> s.withTicketCollectorNodeId(nodeId)
            );
        });
    }

    public void pendingTicketCollector(
            OperationContext context,
            TicketCollectorNode running,
            AgentModels.TicketCollectorRouting routing
    ) {
        TicketOrchestratorNode ticketParent = requireTicketOrchestrator(context);
        AgentModels.TicketCollectorResult collectorResult =
                routing != null ? routing.collectorResult() : null;
        String summary = collectorResult != null ? collectorResult.consolidatedOutput() : "";
        List<CollectedNodeStatus> collectedNodes =
                collectSiblingStatusSnapshots(ticketParent.nodeId(), TicketNode.class);
        TicketCollectorNode withResult = running
                .withResult(collectorResult)
                .withSummary(summary)
                .withCollectedNodes(collectedNodes);
        persistNode(withResult);
        if (handleRoutingInterrupt(withResult, routing != null ? routing.interruptRequest() : null, summary)) {
            return;
        }
        markNodePending(withResult);
        TicketOrchestratorNode updatedParent = ticketParent.withOutput(summary, 0);
        persistNode(updatedParent);
        markNodePending(updatedParent);
    }

    private <T extends GraphNode> T startChildNode(String parentId, T node) {
        computationGraphOrchestrator.addChildNodeAndEmitEvent(parentId, node);
        return markNodeRunning(node);
    }

    private <T extends GraphNode> Optional<T> findNode(String nodeId, Class<T> type) {
        if (nodeId == null || nodeId.isBlank()) {
            return Optional.empty();
        }
        return graphRepository.findById(nodeId)
                .filter(type::isInstance)
                .map(type::cast);
    }

    private <T extends GraphNode> T requireNode(String nodeId, Class<T> type, String label) {
        return findNode(nodeId, type)
                .orElseThrow(() -> new IllegalStateException("Missing " + label + " node: " + nodeId));
    }

    private <T extends GraphNode> Optional<T> findExistingNodeForRun(
            String stateNodeId,
            ArtifactKey requestContextId,
            Class<T> type
    ) {
        Optional<T> fromState = findNode(stateNodeId, type);
        if (fromState.isPresent()) {
            return fromState;
        }
        String requestNodeId = requestContextId != null ? requestContextId.value() : null;
        return findNode(requestNodeId, type);
    }

    private <T extends GraphNode> Optional<T> updateExisting(
            String stateNodeId,
            ArtifactKey requestContextId,
            Class<T> type,
            Function<T, T> updater
    ) {
        return findExistingNodeForRun(stateNodeId, requestContextId, type)
                .map(existing -> {
                    if (updater == null) {
                        return existing;
                    }
                    T updated = updater.apply(existing);
                    return updated != null ? updated : existing;
                });
    }

    private <T extends GraphNode> T startOrReuseRerunnableNode(
            OperationContext context,
            String stateNodeId,
            ArtifactKey requestContextId,
            Class<T> type,
            Function<T, T> existingUpdater,
            Supplier<T> createNew,
            BiFunction<WorkflowGraphState, String, WorkflowGraphState> stateUpdater
    ) {
        Optional<T> existing = updateExisting(stateNodeId, requestContextId, type, existingUpdater);
        if (existing.isPresent()) {
            T updated = existing.get();
            if (context != null && stateUpdater != null) {
                updateState(context, state -> stateUpdater.apply(state, updated.nodeId()));
            }
            return markNodeRunning(updated);
        }

        T created = createNew != null ? createNew.get() : null;
        if (created == null) {
            return null;
        }
        if (context != null && stateUpdater != null) {
            updateState(context, state -> stateUpdater.apply(state, created.nodeId()));
        }
        return markNodeRunning(created);
    }

    private <T extends GraphNode> T persistNode(T node) {
        if (node == null) {
            return null;
        }
        computationGraphOrchestrator.emitNodeUpdatedEvent(node);
        return node;
    }

    private <T extends GraphNode> T markNodeRunning(T node) {
        if (node == null) {
            return null;
        }
        T rerunIncrementedNode = incrementWorkflowCounter(node);
        T runningNode = updateNodeStatus(
                rerunIncrementedNode,
                Events.NodeStatus.RUNNING
        );
        persistNode(runningNode);
        computationGraphOrchestrator.emitStatusChangeEvent(
                node.nodeId(),
                node.status(),
                Events.NodeStatus.RUNNING,
                "Agent execution started"
        );
        return runningNode;
    }

    private <T extends GraphNode> T markNodeCompleted(T node) {
        GraphNode completedNode = updateNodeStatus(
                node,
                Events.NodeStatus.COMPLETED
        );
        persistNode(completedNode);
        computationGraphOrchestrator.emitStatusChangeEvent(
                node.nodeId(),
                node.status(),
                Events.NodeStatus.COMPLETED,
                "Agent execution completed successfully"
        );
        return (T) completedNode;
    }

    private <T extends GraphNode> T markNodePending(T node) {
        GraphNode pendingNode = updateNodeStatus(
                node,
                Events.NodeStatus.PENDING
        );
        persistNode(pendingNode);
        computationGraphOrchestrator.emitStatusChangeEvent(
                node.nodeId(),
                node.status(),
                Events.NodeStatus.PENDING,
                "Awaiting route-back or downstream continuation"
        );
        return (T) pendingNode;
    }

    @SuppressWarnings("unchecked")
    private <T extends GraphNode> T incrementWorkflowCounter(T node) {
        if (node instanceof HasWorkflowContext<?> rerunnable) {
            return (T) rerunnable.incrementCounter();
        }
        return node;
    }

    private void markDescendantNodesCompleted(String rootNodeId) {
        if (rootNodeId == null || rootNodeId.isBlank()) {
            return;
        }
        var queue = new ArrayDeque<>(graphRepository.findByParentId(rootNodeId));
        var visited = new java.util.HashSet<String>();
        while (!queue.isEmpty()) {
            GraphNode node = queue.removeFirst();
            if (node == null || !visited.add(node.nodeId())) {
                continue;
            }
            queue.addAll(graphRepository.findByParentId(node.nodeId()));
            if (shouldMarkCompleted(node)) {
                markNodeCompleted(node);
            }
        }
    }

    private boolean shouldMarkCompleted(GraphNode node) {
        return switch (node.status()) {
            case COMPLETED, CANCELED, FAILED, PRUNED -> false;
            default -> true;
        };
    }

    private <T extends GraphNode> T updateNodeStatus(
            T node,
            Events.NodeStatus newStatus
    ) {
        return (T) node.withStatus(newStatus);
    }

    private boolean handleRoutingInterrupt(
            GraphNode node,
            AgentModels.InterruptRequest interruptRequest,
            String resultPayload
    ) {
        if (interruptRequest == null) {
            return false;
        }
        InterruptContext interruptContext = new InterruptContext(
                interruptRequest.type(),
                InterruptContext.InterruptStatus.REQUESTED,
                interruptRequest.reason(),
                node.nodeId(),
                node.nodeId(),
                null,
                resultPayload
        );
        GraphNode withInterrupt = updateNodeInterruptibleContext(node, interruptContext);
        persistNode(withInterrupt);
        computationGraphOrchestrator.emitInterruptStatusEvent(
                node.nodeId(),
                interruptRequest.type().name(),
                interruptContext.status().name(),
                interruptContext.originNodeId(),
                interruptContext.resumeNodeId()
        );

        Events.NodeStatus newStatus = switch (interruptRequest.type()) {
            case PAUSE, HUMAN_REVIEW, BRANCH -> Events.NodeStatus.WAITING_INPUT;
            case AGENT_REVIEW -> Events.NodeStatus.WAITING_REVIEW;
            case STOP -> Events.NodeStatus.CANCELED;
            case PRUNE -> Events.NodeStatus.PRUNED;
        };
        GraphNode updated = updateNodeStatus(withInterrupt, newStatus);
        persistNode(updated);
        computationGraphOrchestrator.emitStatusChangeEvent(
                node.nodeId(),
                node.status(),
                newStatus,
                interruptRequest.reason()
        );
        emitInterruptNode(updated, interruptContext);
        return true;
    }

    private void emitInterruptNode(GraphNode node, InterruptContext context) {
        if (context.interruptNodeId() != null && !context.interruptNodeId().isBlank()) {
            return;
        }
        String interruptNodeId = nodeFactory.newNodeId();
        InterruptContext emittedContext = context.withInterruptNodeId(interruptNodeId);
        GraphNode interruptNode = switch (context.type()) {
            case HUMAN_REVIEW, AGENT_REVIEW -> buildReviewInterruptNode(node, emittedContext);
            default -> buildInterruptNode(node, emittedContext);
        };
        computationGraphOrchestrator.addChildNodeAndEmitEvent(
                node.nodeId(),
                interruptNode
        );
        GraphNode updatedOrigin = updateNodeInterruptibleContext(node, emittedContext);
        persistNode(updatedOrigin);
    }

    private GraphNode updateNodeInterruptibleContext(GraphNode node, InterruptContext context) {
        return switch (node) {
            case DiscoveryNode n -> n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case DiscoveryCollectorNode n -> n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case DiscoveryDispatchAgentNode n -> n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case DiscoveryOrchestratorNode n -> n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case PlanningNode n -> n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case PlanningCollectorNode n -> n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case PlanningDispatchAgentNode n -> n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case PlanningOrchestratorNode n -> n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case TicketNode n -> n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case TicketCollectorNode n -> n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case TicketDispatchAgentNode n -> n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case TicketOrchestratorNode n -> n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case ReviewNode n -> n.toBuilder().interruptContext(context).lastUpdatedAt(Instant.now()).build();
            case MergeNode n -> n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case OrchestratorNode n -> n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case CollectorNode n -> n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case SummaryNode n -> n.toBuilder().interruptibleContext(context).lastUpdatedAt(Instant.now()).build();
            case InterruptNode n -> n.toBuilder().interruptContext(context).lastUpdatedAt(Instant.now()).build();
            case AskPermissionNode n -> n.toBuilder().lastUpdatedAt(Instant.now()).build();
            case AgentToAgentConversationNode n -> n.toBuilder().lastUpdatedAt(Instant.now()).build();
            case AgentToControllerConversationNode n -> n.toBuilder().lastUpdatedAt(Instant.now()).build();
            case ControllerToAgentConversationNode n -> n.toBuilder().lastUpdatedAt(Instant.now()).build();
            case DataLayerOperationNode n -> n.toBuilder().lastUpdatedAt(Instant.now()).build();
        };
    }

    private InterruptNode buildInterruptNode(GraphNode node, InterruptContext context) {
        Instant now = Instant.now();
        return new InterruptNode(
                context.interruptNodeId(),
                "Interrupt",
                node.goal(),
                Events.NodeStatus.READY,
                node.nodeId(),
                new ArrayList<>(),
                new ConcurrentHashMap<>(),
                now,
                now,
                context
        );
    }

    private ReviewNode buildReviewInterruptNode(GraphNode node, InterruptContext context) {
        String reviewerType = context.type() == Events.InterruptType.HUMAN_REVIEW
                ? "human"
                : "agent-review";
        Instant now = Instant.now();
        return new ReviewNode(
                context.interruptNodeId(),
                "Review: " + node.title(),
                node.goal(),
                context.type() == Events.InterruptType.HUMAN_REVIEW
                        ? Events.NodeStatus.WAITING_INPUT
                        : Events.NodeStatus.READY,
                node.nodeId(),
                new ArrayList<>(),
                new ConcurrentHashMap<>(),
                now,
                now,
                context.originNodeId(),
                context.reason(),
                false,
                false,
                "",
                reviewerType,
                null,
                null,
                context
        );
    }

    private <T extends GraphNode> List<CollectedNodeStatus> collectSiblingStatusSnapshots(
            String parentNodeId,
            Class<T> siblingType
    ) {
        List<CollectedNodeStatus> collected = new ArrayList<>();
        if (parentNodeId == null || parentNodeId.isBlank()) {
            return collected;
        }
        List<GraphNode> children = computationGraphOrchestrator.getChildNodes(parentNodeId);
        for (GraphNode child : children) {
            if (!siblingType.isInstance(child)) {
                continue;
            }
            collected.add(new CollectedNodeStatus(
                    child.nodeId(),
                    child.title(),
                    child.nodeType(),
                    child.status()
            ));
        }
        return collected;
    }

    private <T extends GraphNode> int nextChildIndex(String parentNodeId, Class<T> type) {
        if (parentNodeId == null || parentNodeId.isBlank()) {
            return 1;
        }
        List<GraphNode> children = computationGraphOrchestrator.getChildNodes(parentNodeId);
        long count = children.stream().filter(type::isInstance).count();
        return (int) count + 1;
    }

    private String resolveNodeId(OperationContext context) {
        if (context == null || context.getProcessContext() == null) {
            return "unknown";
        }
        var options = context.getProcessContext().getProcessOptions();
        if (options == null) {
            return "unknown";
        }
        String contextId = options.getContextIdString();
        return contextId != null ? contextId : "unknown";
    }

    private static String shortId(String id) {
        if (id == null) {
            return "";
        }
        return id.length() <= 8 ? id : id.substring(0, 8);
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
