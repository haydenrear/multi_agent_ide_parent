package com.hayden.multiagentide.transformation.controller.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

@Builder(toBuilder = true)
public record ReadTransformersByLayerRequest(@NotBlank String layerId) {
}
