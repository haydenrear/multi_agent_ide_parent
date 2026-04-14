package com.hayden.multiagentide.filter.model.layer;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import lombok.Builder;

/**
 * Sealed layer interface representing filter application points in the architecture.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "layerType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = Layer.WorkflowAgentLayer.class, name = "WORKFLOW_AGENT"),
        @JsonSubTypes.Type(value = Layer.WorkflowAgentActionLayer.class, name = "WORKFLOW_AGENT_ACTION"),
        @JsonSubTypes.Type(value = Layer.ControllerLayer.class, name = "CONTROLLER"),
        @JsonSubTypes.Type(value = Layer.ControllerUiEventPollLayer.class, name = "CONTROLLER_UI_EVENT_POLL")
})
public sealed interface Layer
        permits Layer.WorkflowAgentLayer, Layer.WorkflowAgentActionLayer,
                Layer.ControllerLayer, Layer.ControllerUiEventPollLayer {

    String layerId();
    FilterEnums.LayerType layerType();
    boolean matches(LayerCtx ctx);

    @Builder(toBuilder = true)
    record WorkflowAgentLayer(
            String layerId,
            String agentId
    ) implements Layer {
        @Override
        public FilterEnums.LayerType layerType() {
            return FilterEnums.LayerType.WORKFLOW_AGENT;
        }

        @Override
        public boolean matches(LayerCtx ctx) {
            return ctx instanceof LayerCtx.AgentLayerCtx agentCtx
                    && agentCtx.agentId().equals(agentId);
        }
    }

    @Builder(toBuilder = true)
    record WorkflowAgentActionLayer(
            String layerId,
            String agentId,
            String actionId
    ) implements Layer {
        @Override
        public FilterEnums.LayerType layerType() {
            return FilterEnums.LayerType.WORKFLOW_AGENT_ACTION;
        }

        @Override
        public boolean matches(LayerCtx ctx) {
            return ctx instanceof LayerCtx.ActionLayerCtx actionCtx
                    && actionCtx.agentId().equals(agentId)
                    && actionCtx.actionId().equals(actionId);
        }
    }

    @Builder(toBuilder = true)
    record ControllerLayer(
            String layerId,
            String controllerId
    ) implements Layer {
        @Override
        public FilterEnums.LayerType layerType() {
            return FilterEnums.LayerType.CONTROLLER;
        }

        @Override
        public boolean matches(LayerCtx ctx) {
            return ctx instanceof LayerCtx.ControllerLayerCtx controllerCtx
                    && controllerCtx.controllerId().equals(controllerId);
        }
    }

    @Builder(toBuilder = true)
    record ControllerUiEventPollLayer(
            String layerId,
            String controllerId
    ) implements Layer {
        @Override
        public FilterEnums.LayerType layerType() {
            return FilterEnums.LayerType.CONTROLLER_UI_EVENT_POLL;
        }

        @Override
        public boolean matches(LayerCtx ctx) {
            return ctx instanceof LayerCtx.ControllerLayerCtx controllerCtx
                    && controllerCtx.controllerId().equals(controllerId);
        }
    }
}
