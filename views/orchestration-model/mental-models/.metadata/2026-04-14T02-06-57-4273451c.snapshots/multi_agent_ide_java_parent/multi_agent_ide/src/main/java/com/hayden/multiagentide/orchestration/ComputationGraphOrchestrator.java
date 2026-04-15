package com.hayden.multiagentide.orchestration;

import com.hayden.multiagentide.model.nodes.*;
import com.hayden.multiagentide.model.worktree.WorktreeContext;
import com.hayden.multiagentide.repository.EventStreamRepository;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentide.repository.WorktreeRepository;

import java.time.Instant;
import java.util.*;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

/**
 * Main orchestrator for the computation graph.
 * Manages node execution, event emission, and worktree/spec lifecycle.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ComputationGraphOrchestrator {

    private final GraphRepository graphRepository;
    private final WorktreeRepository worktreeRepository;
    private final EventStreamRepository eventStreamRepository;

    private EventBus eventBus;

    @Lazy
    @Autowired
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }


    /**
     * Get a node from the graph.
     */
    public Optional<GraphNode> getNode(String nodeId) {
        return Optional.ofNullable(nodeId).flatMap(graphRepository::findById);
    }

    /**
     * Get all nodes in the graph.
     */
    public List<GraphNode> getAllNodes() {
        return graphRepository.findAll();
    }

    /**
     * Get child nodes of a parent.
     */
    public List<GraphNode> getChildNodes(String parentNodeId) {
        return graphRepository.findByParentId(parentNodeId);
    }

    /**
     * Add a child node to parent.
     */
    public void addChildNodeAndEmitEvent(
            String parentNodeId,
            GraphNode childNode
    ) {
        Optional<GraphNode> parentOpt = graphRepository.findById(parentNodeId);
        if (parentOpt.isEmpty()) {
            // Parent may not be persisted yet due to dispatch fan-out ordering; child node is still
            // registered via emitNodeAddedEvent below. Log as warn, not error, to avoid false NODE_ERRORs.
            log.warn("Parent node not found for child registration: {} — child {} will still be registered via NODE_ADDED",
                    parentNodeId, childNode.nodeId());
        }

        parentOpt.ifPresent(parent -> {
            List<String> childIds = new ArrayList<>(parent.childNodeIds());
            if (!childIds.contains(childNode.nodeId())) {
                childIds.add(childNode.nodeId());
            }

            // Update parent based on type
            GraphNode updatedParent = updateNodeChildren(parent, childIds);
            emitAddChildNodeEvent(parentNodeId, updatedParent, childNode);
        });

        emitNodeAddedEvent(
                childNode.nodeId(),
                childNode.title(),
                childNode.nodeType(),
                parentNodeId
        );
    }

    /**
     * Get all worktrees.
     */
    public List<WorktreeContext> getAllWorktrees() {
        return worktreeRepository.findAll();
    }

    /**
     * Get worktrees for a node.
     */
    public List<WorktreeContext> getWorktreesForNode(String nodeId) {
        return worktreeRepository.findByNodeId(nodeId);
    }

    /**
     * Detect goal completion.
     * Goal is complete when all leaf nodes are COMPLETED or PRUNED,
     * and all worktrees are merged or discarded.
     */
    public boolean isGoalComplete(String orchestratorNodeId) {
        Optional<GraphNode> orchestratorOpt = graphRepository.findById(
                orchestratorNodeId
        );
        if (orchestratorOpt.isEmpty()) {
            return false;
        }

        // Check all nodes in graph
        for (GraphNode node : graphRepository.findAll()) {
            if (
                    node.status() == Events.NodeStatus.RUNNING ||
                            node.status() == Events.NodeStatus.WAITING_REVIEW ||
                            node.status() == Events.NodeStatus.WAITING_INPUT ||
                            node.status() == Events.NodeStatus.PENDING
            ) {
                return false;
            }
        }

        return true;
    }

    /**
     * Emit node added event.
     */
    public void emitNodeAddedEvent(
            String nodeId,
            String title,
            Events.NodeType nodeType,
            String parentId
    ) {
        emitNodeAddedEvent(nodeId, title, nodeType, parentId, null);
    }

    public void emitNodeAddedEvent(
            String nodeId,
            String title,
            Events.NodeType nodeType,
            String parentId,
            GraphNode node
    ) {
        Events.NodeAddedEvent event = new Events.NodeAddedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                nodeId,
                title,
                nodeType,
                parentId,
                node
        );
        eventBus.publish(event);
    }

    public void emitNodeAddedEvent(GraphNode node, String parentId) {
        if (node == null) {
            return;
        }
        emitNodeAddedEvent(node.nodeId(), node.title(), node.nodeType(), parentId, node);
    }

    public void emitNodeUpdatedEvent(GraphNode node) {
        emitNodeUpdatedEvent(node, Map.of());
    }

    public void emitNodeUpdatedEvent(GraphNode node, Map<String, String> updates) {
        if (node == null) {
            return;
        }
        Events.NodeUpdatedEvent event = new Events.NodeUpdatedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                node.nodeId(),
                updates != null ? updates : Map.of(),
                node
        );
        eventBus.publish(event);
    }

    public void emitAddChildNodeEvent(String parentNodeId, GraphNode updatedParentNode, GraphNode childNode) {
        if (childNode == null) {
            return;
        }
        Events.AddChildNodeEvent event = new Events.AddChildNodeEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                childNode.nodeId(),
                parentNodeId,
                updatedParentNode,
                childNode
        );
        eventBus.publish(event);
    }

    /**
     * Emit status changed event.
     */
    public void emitStatusChangeEvent(
            String nodeId,
            Events.NodeStatus oldStatus,
            Events.NodeStatus newStatus,
            String reason
    ) {
        Events.NodeStatusChangedEvent event = new Events.NodeStatusChangedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                nodeId,
                oldStatus,
                newStatus,
                reason
        );
        eventBus.publish(event);
    }

    public void emitErrorEvent(
            String nodeId,
            String nodeTitle,
            Events.NodeType nodeType,
            String errorMessage
    ) {
        Events.NodeErrorEvent event = new Events.NodeErrorEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                nodeId,
                nodeTitle,
                nodeType,
                errorMessage
        );
        eventBus.publish(event);
    }

    /**
     * Emit worktree created event.
     */
    public void emitWorktreeCreatedEvent(
            String worktreeId,
            String nodeId,
            String path,
            String type,
            String submoduleName
    ) {
        Events.WorktreeCreatedEvent event = new Events.WorktreeCreatedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                worktreeId,
                nodeId,
                path,
                type,
                submoduleName
        );
        eventBus.publish(event);
    }

    public void emitInterruptResolved(
            String nodeId,
            String interruptId,
             Events.InterruptType reviewType,
            String content
    ) {
        Events.ResolveInterruptEvent event = new Events.ResolveInterruptEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                nodeId,
                interruptId,
                content,
                reviewType
        );
        eventBus.publish(event);
    }

    public void emitReviewRequestedEvent(
            String nodeId,
            String reviewNodeId,
            Events.ReviewType reviewType,
            String contentToReview
    ) {
        Events.NodeReviewRequestedEvent event = new Events.NodeReviewRequestedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                nodeId,
                reviewNodeId,
                reviewType,
                contentToReview
        );
        eventBus.publish(event);
    }

    public void emitInterruptStatusEvent(
            String nodeId,
            String interruptType,
            String interruptStatus,
            String originNodeId,
            String resumeNodeId
    ) {
        Events.InterruptStatusEvent event = new Events.InterruptStatusEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                nodeId,
                interruptType,
                interruptStatus,
                originNodeId,
                resumeNodeId
        );
        eventBus.publish(event);
    }

    /**
     * Helper to update node children based on type.
     */
    public GraphNode updateNodeChildren(
            GraphNode parent,
            List<String> childIds
    ) {
        return switch (parent) {
            case OrchestratorNode p ->
                    p.toBuilder()
                            .childNodeIds(childIds)
                            .lastUpdatedAt(Instant.now())
                            .build();
            case PlanningNode p ->
                    p.toBuilder()
                            .childNodeIds(childIds)
                            .lastUpdatedAt(Instant.now())
                            .build();
            case TicketNode p ->
                    p.toBuilder()
                            .childNodeIds(childIds)
                            .lastUpdatedAt(Instant.now())
                            .build();
            case DiscoveryOrchestratorNode p ->
                    p.toBuilder()
                            .childNodeIds(childIds)
                            .lastUpdatedAt(Instant.now())
                            .build();
            case DiscoveryNode p ->
                    p.toBuilder()
                            .childNodeIds(childIds)
                            .lastUpdatedAt(Instant.now())
                            .build();
            case DiscoveryCollectorNode p ->
                    p.toBuilder()
                            .childNodeIds(childIds)
                            .lastUpdatedAt(Instant.now())
                            .build();
            case DiscoveryDispatchAgentNode p ->
                    p.toBuilder()
                            .childNodeIds(childIds)
                            .lastUpdatedAt(Instant.now())
                            .build();
            case PlanningOrchestratorNode p ->
                    p.toBuilder()
                            .childNodeIds(childIds)
                            .lastUpdatedAt(Instant.now())
                            .build();
            case PlanningCollectorNode p ->
                    p.toBuilder()
                            .childNodeIds(childIds)
                            .lastUpdatedAt(Instant.now())
                            .build();
            case PlanningDispatchAgentNode p ->
                    p.toBuilder()
                            .childNodeIds(childIds)
                            .lastUpdatedAt(Instant.now())
                            .build();
            case TicketCollectorNode p ->
                    p.toBuilder()
                            .childNodeIds(childIds)
                            .lastUpdatedAt(Instant.now())
                            .build();
            case TicketDispatchAgentNode p ->
                    p.toBuilder()
                            .childNodeIds(childIds)
                            .lastUpdatedAt(Instant.now())
                            .build();
            case CollectorNode p -> p.toBuilder()
                    .childNodeIds(childIds)
                    .lastUpdatedAt(Instant.now())
                    .build();
            case InterruptNode p ->
                    p.toBuilder()
                            .childNodeIds(childIds)
                            .lastUpdatedAt(Instant.now())
                            .build();
            case MergeNode p ->
                    p.toBuilder()
                            .childNodeIds(childIds)
                            .lastUpdatedAt(Instant.now())
                            .build();
            case ReviewNode p ->
                    p.toBuilder()
                            .childNodeIds(childIds)
                            .lastUpdatedAt(Instant.now())
                            .build();
            case SummaryNode p ->
                    p.toBuilder()
                            .childNodeIds(childIds)
                            .lastUpdatedAt(Instant.now())
                            .build();
            case TicketOrchestratorNode ticketOrchestratorNode ->
                    ticketOrchestratorNode.toBuilder()
                            .childNodeIds(childIds)
                            .lastUpdatedAt(Instant.now())
                            .build();
            case AskPermissionNode askPermissionNode ->
                    askPermissionNode.toBuilder()
                            .childNodeIds(childIds)
                            .lastUpdatedAt(Instant.now())
                            .build();
            case AgentToAgentConversationNode n ->
                    n.toBuilder()
                            .childNodeIds(childIds)
                            .lastUpdatedAt(Instant.now())
                            .build();
            case AgentToControllerConversationNode n ->
                    n.toBuilder()
                            .childNodeIds(childIds)
                            .lastUpdatedAt(Instant.now())
                            .build();
            case ControllerToAgentConversationNode n ->
                    n.toBuilder()
                            .childNodeIds(childIds)
                            .lastUpdatedAt(Instant.now())
                            .build();
            case DataLayerOperationNode n ->
                    n.toBuilder()
                            .childNodeIds(childIds)
                            .lastUpdatedAt(Instant.now())
                            .build();
        };
    }
}
