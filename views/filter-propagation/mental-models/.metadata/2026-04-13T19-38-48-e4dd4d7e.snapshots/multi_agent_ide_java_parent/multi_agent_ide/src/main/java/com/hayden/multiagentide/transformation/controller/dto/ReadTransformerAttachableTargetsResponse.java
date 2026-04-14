package com.hayden.multiagentide.transformation.controller.dto;

import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
public record ReadTransformerAttachableTargetsResponse(List<EndpointTarget> endpoints) {
    @Builder(toBuilder = true)
    public record EndpointTarget(String layerId, String controllerId, String controllerClass, String endpointId, String httpMethod, String path) {
    }
}
