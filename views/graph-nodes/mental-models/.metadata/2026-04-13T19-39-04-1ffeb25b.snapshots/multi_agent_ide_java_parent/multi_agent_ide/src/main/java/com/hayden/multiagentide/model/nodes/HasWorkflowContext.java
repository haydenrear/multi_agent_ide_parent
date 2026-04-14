package com.hayden.multiagentide.model.nodes;

/**
 * Capability mixin for rerunnable nodes that track workflow execution count.
 */
public interface HasWorkflowContext<SELF extends GraphNode> {

    WorkflowContext workflowContext();

    SELF withWorkflowContext(WorkflowContext workflowContext);

    default SELF incrementCounter() {
        WorkflowContext current = workflowContext() != null
                ? workflowContext()
                : WorkflowContext.initial();
        return withWorkflowContext(current.incrementCounter());
    }
}

