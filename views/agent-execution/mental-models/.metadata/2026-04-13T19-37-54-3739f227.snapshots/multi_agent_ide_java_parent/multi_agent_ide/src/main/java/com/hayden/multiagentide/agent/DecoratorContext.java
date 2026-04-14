package com.hayden.multiagentide.agent;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import org.jspecify.annotations.Nullable;

/**
 * Context passed to decorators containing action metadata.
 */
public record DecoratorContext(
        OperationContext operationContext,
        String agentName,
        String actionName,
        String methodName,
        Artifact.AgentModel lastRequest,
        Artifact.AgentModel agentRequest,
        Artifact.HashContext hashContext,
        @Nullable ErrorDescriptor errorDescriptor
) {

    public DecoratorContext(OperationContext operationContext, String agentName, String actionName, String methodName, Artifact.AgentModel lastRequest, Artifact.AgentModel agentRequest) {
        this(operationContext, agentName, actionName, methodName, lastRequest, agentRequest, Artifact.HashContext.defaultHashContext(), null);
    }

    public DecoratorContext(OperationContext operationContext, String agentName, String actionName, String methodName, Artifact.AgentModel lastRequest, Artifact.AgentModel agentRequest, Artifact.HashContext hashContext) {
        this(operationContext, agentName, actionName, methodName, lastRequest, agentRequest, hashContext, null);
    }
}
