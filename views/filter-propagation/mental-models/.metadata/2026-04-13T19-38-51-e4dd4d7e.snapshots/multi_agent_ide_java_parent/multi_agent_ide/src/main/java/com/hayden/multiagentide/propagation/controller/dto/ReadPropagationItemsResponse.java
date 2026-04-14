package com.hayden.multiagentide.propagation.controller.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder(toBuilder = true)
public record ReadPropagationItemsResponse(List<ItemSummary> items, int totalCount) {
    @Builder(toBuilder = true)
    public record ItemSummary(String itemId, String registrationId, String layerId, String sourceNodeId, String sourceName, String summaryText, String mode, String status, Instant createdAt, Instant resolvedAt) {
    }
}
