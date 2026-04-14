package com.hayden.multiagentide.filter.model.layer;

import lombok.Builder;

/**
 * Sealed layer context interface providing runtime layer information.
 * FilterContext extends this to add filter-specific context.
 */
public sealed interface LayerCtx
        permits FilterContext, LayerCtx.AgentLayerCtx, LayerCtx.ActionLayerCtx, LayerCtx.ControllerLayerCtx {

    String layerId();

    @Builder(toBuilder = true)
    record AgentLayerCtx(
            String layerId,
            String agentId
    ) implements LayerCtx {
    }

    @Builder(toBuilder = true)
    record ActionLayerCtx(
            String layerId,
            String agentId,
            String actionId
    ) implements LayerCtx {
    }

    @Builder(toBuilder = true)
    record ControllerLayerCtx(
            String layerId,
            String controllerId
    ) implements LayerCtx {
    }
}
