package com.hayden.multiagentide.transformation.controller.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder(toBuilder = true)
public record ReadTransformationRecordsResponse(List<RecordSummary> records, int totalCount) {
    @Builder(toBuilder = true)
    public record RecordSummary(String recordId, String registrationId, String layerId, String controllerId, String endpointId, String action, Instant createdAt) {
    }
}
