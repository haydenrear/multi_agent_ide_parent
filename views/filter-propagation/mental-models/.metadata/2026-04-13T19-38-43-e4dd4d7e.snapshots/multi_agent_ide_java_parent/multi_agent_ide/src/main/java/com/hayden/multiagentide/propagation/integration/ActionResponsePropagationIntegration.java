package com.hayden.multiagentide.propagation.integration;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.filter.service.FilterLayerCatalog;
import com.hayden.multiagentide.propagation.service.PropagationExecutionService;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.agent.AgentPretty;
import com.hayden.multiagentide.model.PropagationAction;
import com.hayden.multiagentide.model.PropagatorMatchOn;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

@Component
public class ActionResponsePropagationIntegration {

    @Autowired
    @Lazy
    private PropagationExecutionService propagationExecutionService;

    @Autowired
    @Lazy
    private EventBus eventBus;

    public <T extends AgentModels.AgentRouting> T propagate(T routing,
                                                       String agentName,
                                                       String actionName,
                                                       String methodName,
                                                       OperationContext operationContext) {
        propagatePayload(routing, agentName, actionName, methodName, operationContext);
        return routing;
    }

    public <T extends AgentModels.AgentResult> T propagate(T result,
                                                           String agentName,
                                                           String actionName,
                                                           String methodName,
                                                           OperationContext operationContext) {
        propagatePayload(result, agentName, actionName, methodName, operationContext);
        return result;
    }

    public <T extends AgentModels.AgentRequest> T propagateRequestResult(T request,
                                                                         String agentName,
                                                                         String actionName,
                                                                         String methodName,
                                                                         OperationContext operationContext) {
        propagatePayload(request, agentName, actionName, methodName, operationContext);
        return request;
    }

    private void propagatePayload(Object payload,
                                  String agentName,
                                  String actionName,
                                  String methodName,
                                  OperationContext operationContext) {
        if (payload == null || FilterLayerCatalog.isInternalAutomationAction(agentName, actionName, methodName)) {
            return;
        }

        String canonicalActionName = FilterLayerCatalog.canonicalActionName(agentName, actionName, methodName);
        String sourceNodeId = resolveNodeId(operationContext);

        // Pre-serialize with PropagatorSerialization to compact worktree context
        // and strip duplicated nested fields from response payloads.
        Object compactPayload = payload instanceof AgentPretty ap
                ? ap.prettyPrint(new AgentPretty.AgentSerializationCtx.PropagatorSerialization())
                : payload;

        FilterLayerCatalog.resolveActionLayer(agentName, actionName, methodName)
                .ifPresentOrElse(
                        layerId -> propagationExecutionService.execute(
                                layerId,
                                PropagatorMatchOn.ACTION_RESPONSE,
                                compactPayload,
                                sourceNodeId,
                                canonicalActionName,
                                operationContext
                        ),
                        () -> emitUnmappedActionResponse(payload, agentName, actionName, methodName, canonicalActionName, sourceNodeId)
                );
    }

    private void emitUnmappedActionResponse(Object payload,
                                            String agentName,
                                            String actionName,
                                            String methodName,
                                            String canonicalActionName,
                                            String sourceNodeId) {
        Instant now = Instant.now();
        if (eventBus != null) {
            eventBus.publish(new Events.PropagationEvent(
                    "prop-event-" + UUID.randomUUID(),
                    now,
                    sourceNodeId,
                    null,
                    null,
                    PropagatorMatchOn.ACTION_RESPONSE.name(),
                    PropagationAction.PASSTHROUGH.name(),
                    sourceNodeId,
                    canonicalActionName,
                    payload.getClass().getName(),
                    payload,
                    null
            ));
            eventBus.publish(Events.NodeErrorEvent.err(
                    "Could not resolve propagation layer for action response: agent=%s action=%s method=%s canonical=%s"
                            .formatted(safe(agentName), safe(actionName), safe(methodName), safe(canonicalActionName)),
                    resolveArtifactKey(sourceNodeId)
            ));
        }
    }

    private String resolveNodeId(OperationContext context) {
        if (context == null || context.getProcessContext() == null || context.getProcessContext().getProcessOptions() == null) {
            return "unknown";
        }
        String contextId = context.getProcessContext().getProcessOptions().getContextIdString();
        return contextId == null ? "unknown" : contextId;
    }

    private ArtifactKey resolveArtifactKey(String sourceNodeId) {
        if (sourceNodeId != null && ArtifactKey.isValid(sourceNodeId)) {
            return new ArtifactKey(sourceNodeId);
        }
        return ArtifactKey.createRoot();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
