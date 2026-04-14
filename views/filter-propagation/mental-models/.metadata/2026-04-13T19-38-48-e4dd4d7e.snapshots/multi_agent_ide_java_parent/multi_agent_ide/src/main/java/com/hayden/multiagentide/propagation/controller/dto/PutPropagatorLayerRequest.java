package com.hayden.multiagentide.propagation.controller.dto;

import lombok.Builder;

@Builder(toBuilder = true)
public record PutPropagatorLayerRequest(String registrationId, PropagatorRegistrationRequest.LayerBindingRequest layerBinding) {
}
