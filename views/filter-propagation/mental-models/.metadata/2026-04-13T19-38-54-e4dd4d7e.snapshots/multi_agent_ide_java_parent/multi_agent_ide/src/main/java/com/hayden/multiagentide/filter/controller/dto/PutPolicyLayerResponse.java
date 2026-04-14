package com.hayden.multiagentide.filter.controller.dto;

import lombok.Builder;

@Builder(toBuilder = true)
public record PutPolicyLayerResponse(
        boolean ok,
        String policyId,
        String layerId,
        String message
) {
}
