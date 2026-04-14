package com.hayden.multiagentide.filter.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder(toBuilder = true)
public record TogglePolicyLayerRequest(
        @NotBlank String policyId,
        @NotBlank String layerId,
        boolean includeDescendants
) {
}
