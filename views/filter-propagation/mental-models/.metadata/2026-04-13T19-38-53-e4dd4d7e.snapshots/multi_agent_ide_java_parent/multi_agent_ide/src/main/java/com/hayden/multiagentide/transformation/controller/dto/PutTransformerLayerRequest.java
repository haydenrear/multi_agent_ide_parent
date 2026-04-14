package com.hayden.multiagentide.transformation.controller.dto;

import lombok.Builder;

@Builder(toBuilder = true)
public record PutTransformerLayerRequest(String registrationId, TransformerRegistrationRequest.LayerBindingRequest layerBinding) {
}
