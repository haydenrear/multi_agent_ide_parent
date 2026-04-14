package com.hayden.multiagentide.agent;

public record WorkflowGraphState(
        String orchestratorNodeId,
        String orchestratorCollectorNodeId,
        String discoveryOrchestratorNodeId,
        String discoveryDispatchNodeId,
        String discoveryCollectorNodeId,
        String planningOrchestratorNodeId,
        String planningDispatchNodeId,
        String planningCollectorNodeId,
        String ticketOrchestratorNodeId,
        String ticketDispatchNodeId,
        String ticketCollectorNodeId,
        String reviewNodeId,
        String mergeNodeId
) {
    public WorkflowGraphState(
            String orchestratorNodeId,
            String orchestratorCollectorNodeId,
            String discoveryOrchestratorNodeId,
            String discoveryCollectorNodeId,
            String planningOrchestratorNodeId,
            String planningCollectorNodeId,
            String ticketOrchestratorNodeId,
            String ticketCollectorNodeId,
            String reviewNodeId,
            String mergeNodeId
    ) {
        this(
                orchestratorNodeId,
                orchestratorCollectorNodeId,
                discoveryOrchestratorNodeId,
                null,
                discoveryCollectorNodeId,
                planningOrchestratorNodeId,
                null,
                planningCollectorNodeId,
                ticketOrchestratorNodeId,
                null,
                ticketCollectorNodeId,
                reviewNodeId,
                mergeNodeId
        );
    }

    public static WorkflowGraphState initial(String orchestratorNodeId) {
        return new WorkflowGraphState(
                orchestratorNodeId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }

    public WorkflowGraphState withOrchestratorCollectorNodeId(String nodeId) {
        return new WorkflowGraphState(
                orchestratorNodeId,
                nodeId,
                discoveryOrchestratorNodeId,
                discoveryDispatchNodeId,
                discoveryCollectorNodeId,
                planningOrchestratorNodeId,
                planningDispatchNodeId,
                planningCollectorNodeId,
                ticketOrchestratorNodeId,
                ticketDispatchNodeId,
                ticketCollectorNodeId,
                reviewNodeId,
                mergeNodeId
        );
    }

    public WorkflowGraphState withDiscoveryOrchestratorNodeId(String nodeId) {
        return new WorkflowGraphState(
                orchestratorNodeId,
                orchestratorCollectorNodeId,
                nodeId,
                discoveryDispatchNodeId,
                discoveryCollectorNodeId,
                planningOrchestratorNodeId,
                planningDispatchNodeId,
                planningCollectorNodeId,
                ticketOrchestratorNodeId,
                ticketDispatchNodeId,
                ticketCollectorNodeId,
                reviewNodeId,
                mergeNodeId
        );
    }

    public WorkflowGraphState withDiscoveryDispatchNodeId(String nodeId) {
        return new WorkflowGraphState(
                orchestratorNodeId,
                orchestratorCollectorNodeId,
                discoveryOrchestratorNodeId,
                nodeId,
                discoveryCollectorNodeId,
                planningOrchestratorNodeId,
                planningDispatchNodeId,
                planningCollectorNodeId,
                ticketOrchestratorNodeId,
                ticketDispatchNodeId,
                ticketCollectorNodeId,
                reviewNodeId,
                mergeNodeId
        );
    }

    public WorkflowGraphState withDiscoveryCollectorNodeId(String nodeId) {
        return new WorkflowGraphState(
                orchestratorNodeId,
                orchestratorCollectorNodeId,
                discoveryOrchestratorNodeId,
                discoveryDispatchNodeId,
                nodeId,
                planningOrchestratorNodeId,
                planningDispatchNodeId,
                planningCollectorNodeId,
                ticketOrchestratorNodeId,
                ticketDispatchNodeId,
                ticketCollectorNodeId,
                reviewNodeId,
                mergeNodeId
        );
    }

    public WorkflowGraphState withPlanningOrchestratorNodeId(String nodeId) {
        return new WorkflowGraphState(
                orchestratorNodeId,
                orchestratorCollectorNodeId,
                discoveryOrchestratorNodeId,
                discoveryDispatchNodeId,
                discoveryCollectorNodeId,
                nodeId,
                planningDispatchNodeId,
                planningCollectorNodeId,
                ticketOrchestratorNodeId,
                ticketDispatchNodeId,
                ticketCollectorNodeId,
                reviewNodeId,
                mergeNodeId
        );
    }

    public WorkflowGraphState withPlanningDispatchNodeId(String nodeId) {
        return new WorkflowGraphState(
                orchestratorNodeId,
                orchestratorCollectorNodeId,
                discoveryOrchestratorNodeId,
                discoveryDispatchNodeId,
                discoveryCollectorNodeId,
                planningOrchestratorNodeId,
                nodeId,
                planningCollectorNodeId,
                ticketOrchestratorNodeId,
                ticketDispatchNodeId,
                ticketCollectorNodeId,
                reviewNodeId,
                mergeNodeId
        );
    }

    public WorkflowGraphState withPlanningCollectorNodeId(String nodeId) {
        return new WorkflowGraphState(
                orchestratorNodeId,
                orchestratorCollectorNodeId,
                discoveryOrchestratorNodeId,
                discoveryDispatchNodeId,
                discoveryCollectorNodeId,
                planningOrchestratorNodeId,
                planningDispatchNodeId,
                nodeId,
                ticketOrchestratorNodeId,
                ticketDispatchNodeId,
                ticketCollectorNodeId,
                reviewNodeId,
                mergeNodeId
        );
    }

    public WorkflowGraphState withTicketOrchestratorNodeId(String nodeId) {
        return new WorkflowGraphState(
                orchestratorNodeId,
                orchestratorCollectorNodeId,
                discoveryOrchestratorNodeId,
                discoveryDispatchNodeId,
                discoveryCollectorNodeId,
                planningOrchestratorNodeId,
                planningDispatchNodeId,
                planningCollectorNodeId,
                nodeId,
                ticketDispatchNodeId,
                ticketCollectorNodeId,
                reviewNodeId,
                mergeNodeId
        );
    }

    public WorkflowGraphState withTicketDispatchNodeId(String nodeId) {
        return new WorkflowGraphState(
                orchestratorNodeId,
                orchestratorCollectorNodeId,
                discoveryOrchestratorNodeId,
                discoveryDispatchNodeId,
                discoveryCollectorNodeId,
                planningOrchestratorNodeId,
                planningDispatchNodeId,
                planningCollectorNodeId,
                ticketOrchestratorNodeId,
                nodeId,
                ticketCollectorNodeId,
                reviewNodeId,
                mergeNodeId
        );
    }

    public WorkflowGraphState withTicketCollectorNodeId(String nodeId) {
        return new WorkflowGraphState(
                orchestratorNodeId,
                orchestratorCollectorNodeId,
                discoveryOrchestratorNodeId,
                discoveryDispatchNodeId,
                discoveryCollectorNodeId,
                planningOrchestratorNodeId,
                planningDispatchNodeId,
                planningCollectorNodeId,
                ticketOrchestratorNodeId,
                ticketDispatchNodeId,
                nodeId,
                reviewNodeId,
                mergeNodeId
        );
    }

    public WorkflowGraphState withReviewNodeId(String nodeId) {
        return new WorkflowGraphState(
                orchestratorNodeId,
                orchestratorCollectorNodeId,
                discoveryOrchestratorNodeId,
                discoveryDispatchNodeId,
                discoveryCollectorNodeId,
                planningOrchestratorNodeId,
                planningDispatchNodeId,
                planningCollectorNodeId,
                ticketOrchestratorNodeId,
                ticketDispatchNodeId,
                ticketCollectorNodeId,
                nodeId,
                mergeNodeId
        );
    }

    public WorkflowGraphState withMergeNodeId(String nodeId) {
        return new WorkflowGraphState(
                orchestratorNodeId,
                orchestratorCollectorNodeId,
                discoveryOrchestratorNodeId,
                discoveryDispatchNodeId,
                discoveryCollectorNodeId,
                planningOrchestratorNodeId,
                planningDispatchNodeId,
                planningCollectorNodeId,
                ticketOrchestratorNodeId,
                ticketDispatchNodeId,
                ticketCollectorNodeId,
                reviewNodeId,
                nodeId
        );
    }
}
