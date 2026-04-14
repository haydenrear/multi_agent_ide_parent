package com.hayden.multiagentide.filter.controller.dto;

import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
public record ReadPoliciesByLayerResponse(
        String layerId,
        List<PolicySummary> policies,
        int totalCount
) {

    @Builder(toBuilder = true)
    public record PolicySummary(
            String policyId,
            String name,
            String description,
            String sourcePath,
            String filterType,
            String status,
            int priority
    ) {
    }
}
