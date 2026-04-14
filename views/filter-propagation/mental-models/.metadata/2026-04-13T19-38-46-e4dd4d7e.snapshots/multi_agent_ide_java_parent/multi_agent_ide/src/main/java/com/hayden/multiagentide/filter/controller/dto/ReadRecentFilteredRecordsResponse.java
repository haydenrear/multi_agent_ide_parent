package com.hayden.multiagentide.filter.controller.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.List;

@Builder(toBuilder = true)
public record ReadRecentFilteredRecordsResponse(
        String policyId,
        String layerId,
        List<FilteredRecordSummary> records,
        int totalCount,
        String nextCursor
) {

    @Builder(toBuilder = true)
    public record FilteredRecordSummary(
            String decisionId,
            String filterType,
            String action,
            String inputSummary,
            String outputSummary,
            int appliedInstructionCount,
            String errorMessage,
            Instant createdAt
    ) {
    }
}
