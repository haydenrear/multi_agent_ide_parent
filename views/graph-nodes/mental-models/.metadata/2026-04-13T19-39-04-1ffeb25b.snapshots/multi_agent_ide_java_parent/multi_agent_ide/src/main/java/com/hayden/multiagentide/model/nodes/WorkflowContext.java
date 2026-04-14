package com.hayden.multiagentide.model.nodes;

/**
 * Shared workflow execution state for rerunnable graph nodes.
 */
public record WorkflowContext(int runCount) {

    public WorkflowContext {
        if (runCount < 0) {
            throw new IllegalArgumentException("runCount must be >= 0");
        }
    }

    public static WorkflowContext initial() {
        return new WorkflowContext(0);
    }

    public WorkflowContext incrementCounter() {
        return new WorkflowContext(runCount + 1);
    }
}

