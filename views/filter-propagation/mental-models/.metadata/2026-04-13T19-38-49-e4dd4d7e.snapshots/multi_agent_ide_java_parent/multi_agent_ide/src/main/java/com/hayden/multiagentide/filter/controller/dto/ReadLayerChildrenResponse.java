package com.hayden.multiagentide.filter.controller.dto;

import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
public record ReadLayerChildrenResponse(
        String layerId,
        List<LayerSummary> children,
        int totalCount
) {

    @Builder(toBuilder = true)
    public record LayerSummary(
            String layerId,
            String layerType,
            String layerKey,
            String parentLayerId,
            int depth
    ) {
    }
}
