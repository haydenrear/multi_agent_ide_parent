package com.hayden.multiagentide.model.nodes;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.model.worktree.MainWorktreeContext;
import com.hayden.multiagentide.model.worktree.SubmoduleWorktreeContext;
import com.hayden.utilitymodule.stream.StreamUtil;
import lombok.Builder;
import org.springframework.util.CollectionUtils;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Root node in the computation graph that orchestrates the overall goal.
 * Can be Branchable, and Viewable.
 */
@Builder(toBuilder = true)
public record OrchestratorNode(
        String nodeId,
        String title,
        String goal,
        Events.NodeStatus status,
        String parentNodeId,
        List<String> childNodeIds,
        Map<String, String> metadata,
        Instant createdAt,
        Instant lastUpdatedAt,
        MainWorktreeContext worktreeContext,
        String orchestratorOutput,
        AgentModels.OrchestratorAgentResult orchestratorResult,
        InterruptContext interruptibleContext,
        WorkflowContext workflowContext
) implements GraphNode, Viewable<String>, Orchestrator, Interruptible, HasWorkflowContext<OrchestratorNode>, ExecutionNode {

    public OrchestratorNode {
        if (nodeId == null || nodeId.isEmpty()) throw new IllegalArgumentException("nodeId required");
        if (worktreeContext.repositoryUrl() == null || worktreeContext.repositoryUrl().isEmpty()) throw new IllegalArgumentException("repositoryUrl required");
        if (childNodeIds == null) childNodeIds = new ArrayList<>();
        if (metadata == null) metadata = new HashMap<>();
        if (workflowContext == null) workflowContext = WorkflowContext.initial();
    }

    public boolean hasSubmodules() {
        return Optional.ofNullable(worktreeContext)
                .map(mwc -> !CollectionUtils.isEmpty(mwc.submoduleWorktrees()))
                .orElse(false);
    }

    @Override
    public Events.NodeType nodeType() {
        return Events.NodeType.ORCHESTRATOR;
    }

    @Override
    public String getView() {
        return orchestratorOutput;
    }

    /**
     * Create an updated version with new status.
     */
    public OrchestratorNode withStatus(Events.NodeStatus newStatus) {
        return toBuilder()
                .status(newStatus)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    /**
     * Add a child node ID.
     */
    public OrchestratorNode addChildNode(String childNodeId) {
        List<String> newChildren = new ArrayList<>(childNodeIds);
        newChildren.add(childNodeId);
        return toBuilder()
                .childNodeIds(newChildren)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    /**
     * Update orchestrator output.
     */
    public OrchestratorNode withOutput(String output) {
        return toBuilder()
                .orchestratorOutput(output)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public OrchestratorNode withResult(AgentModels.OrchestratorAgentResult result) {
        return toBuilder()
                .orchestratorResult(result)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    public OrchestratorNode withInterruptibleContext(InterruptContext context) {
        return toBuilder()
                .interruptibleContext(context)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    @Override
    public OrchestratorNode withWorkflowContext(WorkflowContext workflowContext) {
        return toBuilder()
                .workflowContext(workflowContext)
                .lastUpdatedAt(Instant.now())
                .build();
    }

    @JsonIgnore
    public String mainWorktreeId() {
        return this.worktreeContext.worktreeId();
    }

    @JsonIgnore
    public List<SubmoduleWorktreeContext> submoduleWorktrees() {
        return this.worktreeContext.submoduleWorktrees();
    }

    @JsonIgnore
    public List<String> submoduleWorktreeIds() {
        return StreamUtil.toStream(submoduleWorktrees())
                .map(SubmoduleWorktreeContext::worktreeId)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    @Override
    public String agent() {
        return "WORKFLOW_AGENT";
    }
}
