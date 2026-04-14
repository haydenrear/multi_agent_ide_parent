package com.hayden.multiagentide.filter.controller.dto;

import lombok.Builder;

@Builder(toBuilder = true)
public record PolicyRegistrationResponse(
        boolean ok,
        String policyId,
        String filterType,
        String status,
        String message
) {
}
