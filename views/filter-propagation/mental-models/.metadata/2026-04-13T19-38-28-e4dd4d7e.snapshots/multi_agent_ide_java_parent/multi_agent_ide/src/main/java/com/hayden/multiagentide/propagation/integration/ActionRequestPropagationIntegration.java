package com.hayden.multiagentide.propagation.integration;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.multiagentide.filter.service.FilterLayerCatalog;
import com.hayden.multiagentide.propagation.service.PropagationExecutionService;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.agent.AgentPretty;
import com.hayden.multiagentide.model.PropagatorMatchOn;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class ActionRequestPropagationIntegration {

    @Autowired
    @Lazy
    private PropagationExecutionService propagationExecutionService;

    public <T extends AgentModels.AgentRequest> T propagate(T request,
                                                            String agentName,
                                                            String actionName,
                                                            String methodName,
                                                            OperationContext operationContext) {
        if (FilterLayerCatalog.isInternalAutomationAction(agentName, actionName, methodName)) {
            return request;
        }
        // Pre-serialize with PropagatorSerialization to compact worktree context
        // and strip duplicated nested fields. Without this, the propagator receives
        // the raw object serialized via Jackson with 5-7 redundant worktreeContext copies.
        String compactPayload = request.prettyPrint(
                new AgentPretty.AgentSerializationCtx.PropagatorSerialization());
        FilterLayerCatalog.resolveActionLayer(agentName, actionName, methodName)
                .ifPresent(layerId -> propagationExecutionService.execute(
                        layerId,
                        PropagatorMatchOn.ACTION_REQUEST,
                        compactPayload,
                        resolveNodeId(operationContext),
                        FilterLayerCatalog.canonicalActionName(agentName, actionName, methodName),
                        operationContext
                ));
        return request;
    }

    private String resolveNodeId(OperationContext context) {
        if (context == null || context.getProcessContext() == null || context.getProcessContext().getProcessOptions() == null) {
            return "unknown";
        }
        String contextId = context.getProcessContext().getProcessOptions().getContextIdString();
        return contextId == null ? "unknown" : contextId;
    }
}
