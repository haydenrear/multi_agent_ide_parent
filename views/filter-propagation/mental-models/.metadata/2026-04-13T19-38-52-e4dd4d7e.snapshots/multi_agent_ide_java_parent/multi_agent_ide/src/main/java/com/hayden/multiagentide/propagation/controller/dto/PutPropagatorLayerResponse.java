package com.hayden.multiagentide.propagation.controller.dto;

import lombok.Builder;

@Builder(toBuilder = true)
public record PutPropagatorLayerResponse(boolean ok, String registrationId, String layerId, String message) {
}
