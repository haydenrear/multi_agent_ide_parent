package com.hayden.multiagentide.filter.controller.dto;

import lombok.Builder;

import java.time.Instant;

@Builder(toBuilder = true)
public record DeactivatePolicyResponse(
        boolean ok,
        String policyId,
        String status,
        Instant deactivatedAt,
        String message
) {
}
