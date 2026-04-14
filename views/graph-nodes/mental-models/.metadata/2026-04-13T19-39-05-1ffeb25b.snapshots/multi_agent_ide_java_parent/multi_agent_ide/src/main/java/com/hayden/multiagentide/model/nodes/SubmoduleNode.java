package com.hayden.multiagentide.model.nodes;

import com.hayden.acp_cdc_ai.acp.events.Events;

import java.time.Instant;
import java.util.*;

/**
 * Root node in the computation graph that orchestrates the overall goal.
 * Can be Branchable, Summarizable, and Viewable.
 */
public record SubmoduleNode(
        String nodeId,
        String title,
        String goal,
        Events.NodeStatus status,
        String parentNodeId,
        List<String> childNodeIds,
        Map<String, String> metadata,
        Instant createdAt,
        Instant lastUpdatedAt,

        // Orchestrator-specific fields
        String repositoryUrl,
        String baseBranch,
        boolean hasSubmodules,
        List<String> submoduleNames,
        String mainWorktreeId,
        List<String> submoduleWorktreeIds,
        String orchestratorOutput
)  {

    public SubmoduleNode {
        if (nodeId == null || nodeId.isEmpty()) throw new IllegalArgumentException("nodeId required");
        if (repositoryUrl == null || repositoryUrl.isEmpty()) throw new IllegalArgumentException("repositoryUrl required");
        if (childNodeIds == null) childNodeIds = new ArrayList<>();
        if (metadata == null) metadata = new HashMap<>();
        if (submoduleNames == null) submoduleNames = new ArrayList<>();
        if (submoduleWorktreeIds == null) submoduleWorktreeIds = new ArrayList<>();
    }

    /**
     * Create an updated version with new status.
     */
    public SubmoduleNode withStatus(Events.NodeStatus newStatus) {
        return new SubmoduleNode(
                nodeId, title, goal, newStatus, parentNodeId,
                childNodeIds, metadata, createdAt, Instant.now(),
                repositoryUrl, baseBranch, hasSubmodules, submoduleNames,
                mainWorktreeId, submoduleWorktreeIds, orchestratorOutput
        );
    }

    /**
     * Add a child node ID.
     */
    public SubmoduleNode addChildNode(String childNodeId) {
        List<String> newChildren = new ArrayList<>(childNodeIds);
        newChildren.add(childNodeId);
        return new SubmoduleNode(
                nodeId, title, goal, status, parentNodeId,
                newChildren, metadata, createdAt, Instant.now(),
                repositoryUrl, baseBranch, hasSubmodules, submoduleNames,
                mainWorktreeId, submoduleWorktreeIds, orchestratorOutput
        );
    }

    /**
     * Update orchestrator output.
     */
    public SubmoduleNode withOutput(String output) {
        return new SubmoduleNode(
                nodeId, title, goal, status, parentNodeId,
                childNodeIds, metadata, createdAt, Instant.now(),
                repositoryUrl, baseBranch, hasSubmodules, submoduleNames,
                mainWorktreeId, submoduleWorktreeIds, output
        );
    }
}
