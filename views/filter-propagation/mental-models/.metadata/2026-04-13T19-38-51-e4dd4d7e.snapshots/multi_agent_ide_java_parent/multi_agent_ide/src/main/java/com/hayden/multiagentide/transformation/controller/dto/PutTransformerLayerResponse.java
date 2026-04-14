package com.hayden.multiagentide.transformation.controller.dto;

import lombok.Builder;

@Builder(toBuilder = true)
public record PutTransformerLayerResponse(boolean ok, String registrationId, String layerId, String message) {
}
