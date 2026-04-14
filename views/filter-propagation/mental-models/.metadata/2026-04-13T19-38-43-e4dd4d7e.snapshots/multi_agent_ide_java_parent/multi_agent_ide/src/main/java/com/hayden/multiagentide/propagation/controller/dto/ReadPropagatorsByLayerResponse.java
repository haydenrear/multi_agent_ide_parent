package com.hayden.multiagentide.propagation.controller.dto;

import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
public record ReadPropagatorsByLayerResponse(String layerId, List<PropagatorSummary> propagators, int totalCount) {
    @Builder(toBuilder = true)
    public record PropagatorSummary(String registrationId, String name, String description, String propagatorKind, String status, int priority) {
    }
}
