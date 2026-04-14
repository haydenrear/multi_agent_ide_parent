package com.hayden.multiagentide.repository;

import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.model.nodes.GraphNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Repository for storing and retrieving graph nodes.
 */
public interface GraphRepository {

    /**
     * Save a node.
     * @param node the node to save
     */
    void save(GraphNode node);

    /**
     * Get a node by ID.
     * @param nodeId the node ID
     * @return the node if found
     */
    Optional<GraphNode> findById(String nodeId);

    /**
     * Get all nodes.
     * @return list of all nodes
     */
    List<GraphNode> findAll();

    /**
     * Get all child nodes of a parent.
     * @param parentNodeId the parent node ID
     * @return list of child nodes
     */
    List<GraphNode> findByParentId(String parentNodeId);

    /**
     * Get nodes by type.
     * @param nodeType the node type
     * @return list of nodes of that type
     */
    List<GraphNode> findByType(Events.NodeType nodeType);

    /**
     * Delete a node.
     * @param nodeId the node ID
     */
    void delete(String nodeId);

    /**
     * Check if node exists.
     * @param nodeId the node ID
     * @return true if exists
     */
    boolean exists(String nodeId);

    /**
     * Count all nodes.
     * @return count of nodes
     */
    long count();

    /**
     * Clear all nodes.
     */
    void clear();

    /**
     * Find an interrupt or review node that targets the given origin node.
     */
    Optional<GraphNode> findInterruptByOrigin(String nodeId);

    /**
     * Find a node and all its descendants recursively.
     * Returns map of nodeId -> GraphNode for efficient lookup.
     */
    Map<String, GraphNode> findSubtree(String rootNodeId);

}
