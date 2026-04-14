package com.hayden.multiagentide.propagation.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder(toBuilder = true)
@Schema(description = "Response from activating a propagator registration.")
public record ActivatePropagatorResponse(
        @Schema(description = "True if the activation succeeded") boolean ok,
        @Schema(description = "The registration ID that was activated") String registrationId,
        @Schema(description = "New status of the propagator (ACTIVE on success)") String status,
        @Schema(description = "Human-readable result message") String message) {
}
