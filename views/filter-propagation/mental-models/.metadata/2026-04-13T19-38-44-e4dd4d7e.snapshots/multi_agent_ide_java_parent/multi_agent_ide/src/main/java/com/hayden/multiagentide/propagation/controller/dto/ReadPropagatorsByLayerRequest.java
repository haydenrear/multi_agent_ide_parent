package com.hayden.multiagentide.propagation.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder(toBuilder = true)
public record ReadPropagatorsByLayerRequest(@NotBlank String layerId) {
}
