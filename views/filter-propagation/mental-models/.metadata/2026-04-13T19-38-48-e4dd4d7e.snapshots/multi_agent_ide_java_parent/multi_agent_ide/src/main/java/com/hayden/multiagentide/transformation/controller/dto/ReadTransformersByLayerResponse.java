package com.hayden.multiagentide.transformation.controller.dto;

import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
public record ReadTransformersByLayerResponse(String layerId, List<TransformerSummary> transformers, int totalCount) {
    @Builder(toBuilder = true)
    public record TransformerSummary(String registrationId, String name, String description, String transformerKind, String status, int priority) {
    }
}
