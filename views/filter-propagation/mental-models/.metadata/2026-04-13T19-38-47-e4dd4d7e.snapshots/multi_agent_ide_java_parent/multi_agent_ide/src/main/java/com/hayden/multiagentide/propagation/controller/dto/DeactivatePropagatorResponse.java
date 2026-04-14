package com.hayden.multiagentide.propagation.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;

@Builder(toBuilder = true)
@Schema(description = "Response from deactivating a propagator registration.")
public record DeactivatePropagatorResponse(
        @Schema(description = "True if the deactivation succeeded") boolean ok,
        @Schema(description = "The registration ID that was deactivated") String registrationId,
        @Schema(description = "New status of the propagator (INACTIVE on success)") String status,
        @Schema(description = "Human-readable result message") String message) {
}
