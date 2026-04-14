package com.hayden.multiagentide.filter.controller.dto;

import lombok.Builder;

@Builder(toBuilder = true)
public record ReadRecentFilteredRecordsRequest(
        String policyId,
        String layerId,
        int limit,
        String cursor
) {
    public int limitOrDefault() {
        return limit <= 0 ? 50 : limit;
    }
}
