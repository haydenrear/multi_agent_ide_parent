package com.hayden.multiagentide.model.nodes;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventNode;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.acp_cdc_ai.acp.events.HasContextId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Sealed interface representing a node in the computation graph.
 * Nodes can implement capability mixins (Branchable, Editable, etc.) for optional behaviors.
 * This is a data-oriented interface that composes capabilities.
 */
public sealed interface GraphNode extends EventNode, HasContextId
        permits
            AgentToAgentConversationNode, AgentToControllerConversationNode, AskPermissionNode, CollectorNode, ControllerToAgentConversationNode,
            DataLayerOperationNode, DiscoveryCollectorNode, DiscoveryDispatchAgentNode, DiscoveryNode, DiscoveryOrchestratorNode, InterruptNode,
            MergeNode, OrchestratorNode, PlanningCollectorNode, PlanningDispatchAgentNode, PlanningNode, PlanningOrchestratorNode,
            ReviewNode, SummaryNode, TicketCollectorNode, TicketDispatchAgentNode, TicketNode, TicketOrchestratorNode {

    Logger log = LoggerFactory.getLogger(GraphNode.class);

    @JsonIgnore
    default ArtifactKey contextId() {
        var n = nodeId();

        try {
            return new ArtifactKey(n);
        } catch (Exception e) {
            log.error("Failed to parse node {} as artifact key.", n, e);
        }

        return ArtifactKey.createRoot();
    }


    GraphNode withStatus(Events.NodeStatus nodeStatus);

    /**
     * Unique identifier for this node.
     */
    String nodeId();

    /**
     * Human-readable title/name.
     */
    String title();

    /**
     * Goal or description of work for this node.
     */
    String goal();

    /**
     * Current status of this node.
     */
    Events.NodeStatus status();

    /**
     * ID of parent node, or null if root.
     */
    String parentNodeId();

    /**
     * Child node IDs.
     */
    List<String> childNodeIds();

    /**
     * Metadata and annotations.
     */
    Map<String, String> metadata();

    /**
     * Timestamp when node was created.
     */
    Instant createdAt();

    /**
     * Timestamp when node status last changed.
     */
    Instant lastUpdatedAt();

    /**
     * Type/kind of this node for pattern matching.
     */
    Events.NodeType nodeType();


}
