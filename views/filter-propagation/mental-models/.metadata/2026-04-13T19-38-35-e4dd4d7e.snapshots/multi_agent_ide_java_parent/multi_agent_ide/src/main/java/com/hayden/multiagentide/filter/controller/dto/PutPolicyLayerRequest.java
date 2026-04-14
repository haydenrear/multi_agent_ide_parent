package com.hayden.multiagentide.filter.controller.dto;

import lombok.Builder;

@Builder(toBuilder = true)
public record PutPolicyLayerRequest(
        String policyId,
        PolicyRegistrationRequest.LayerBindingRequest layerBinding
) {
}
