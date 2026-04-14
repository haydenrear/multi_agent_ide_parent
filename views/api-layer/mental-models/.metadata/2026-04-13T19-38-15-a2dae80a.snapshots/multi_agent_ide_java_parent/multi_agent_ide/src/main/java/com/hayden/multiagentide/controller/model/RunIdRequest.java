package com.hayden.multiagentide.controller.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request containing a debug run identifier")
public record RunIdRequest(
        @NotBlank @Schema(description = "The debug run identifier (UUID)", example = "550e8400-e29b-41d4-a716-446655440000") String runId
) {
}
