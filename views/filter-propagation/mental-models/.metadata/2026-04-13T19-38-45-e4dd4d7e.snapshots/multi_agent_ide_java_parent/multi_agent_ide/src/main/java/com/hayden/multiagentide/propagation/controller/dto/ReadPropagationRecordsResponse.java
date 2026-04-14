package com.hayden.multiagentide.propagation.controller.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder(toBuilder = true)
public record ReadPropagationRecordsResponse(List<RecordSummary> records, int totalCount) {
    @Builder(toBuilder = true)
    public record RecordSummary(String recordId, String registrationId, String layerId, String sourceType, String action, String sourceNodeId, Instant createdAt) {
    }
}
