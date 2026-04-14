package com.hayden.multiagentide.filter.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder(toBuilder = true)
public record ReadPoliciesByLayerRequest(
        @NotBlank String layerId,
        String status
) {
    public String statusOrDefault() {
        return status == null || status.isBlank() ? "ACTIVE" : status;
    }
}
