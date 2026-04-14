package com.hayden.multiagentide.propagation.controller.dto;

import lombok.Builder;

import java.time.Instant;

@Builder(toBuilder = true)
public record ResolvePropagationItemResponse(boolean ok, String itemId, String status, String resolutionType, Instant resolvedAt, String message) {
}
