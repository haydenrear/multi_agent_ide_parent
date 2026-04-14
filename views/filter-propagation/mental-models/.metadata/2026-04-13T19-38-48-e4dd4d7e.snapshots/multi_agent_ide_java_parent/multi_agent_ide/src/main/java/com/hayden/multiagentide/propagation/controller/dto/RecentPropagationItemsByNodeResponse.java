package com.hayden.multiagentide.propagation.controller.dto;

import com.hayden.multiagentide.propagation.model.Propagation;
import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder(toBuilder = true)
public record RecentPropagationItemsByNodeResponse(List<PropagationItemPayload> items, int totalCount) {
    @Builder(toBuilder = true)
    public record PropagationItemPayload(
            String itemId,
            String registrationId,
            String layerId,
            String sourceNodeId,
            String sourceName,
            String stage,
            String summaryText,
            Propagation propagatedText,
            String mode,
            String status,
            String resolutionType,
            String resolutionNotes,
            Instant createdAt,
            Instant resolvedAt
    ) {
    }
}
