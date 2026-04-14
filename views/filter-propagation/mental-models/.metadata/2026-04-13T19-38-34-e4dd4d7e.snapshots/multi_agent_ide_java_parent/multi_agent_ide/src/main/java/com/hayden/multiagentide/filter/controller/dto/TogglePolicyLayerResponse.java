package com.hayden.multiagentide.filter.controller.dto;

import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
public record TogglePolicyLayerResponse(
        boolean ok,
        String policyId,
        String layerId,
        boolean enabled,
        List<String> affectedDescendantLayers,
        String message
) {
}
