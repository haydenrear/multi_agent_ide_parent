package com.hayden.multiagentide.propagation.controller.dto;

import com.hayden.multiagentide.filter.model.executor.AiFilterTool;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Response from bulk-updating the session mode across all registered propagators.")
public record UpdateAllSessionModeResponse(
        @Schema(description = "Whether the bulk update completed without fatal errors.")
        boolean ok,
        @Schema(description = "Number of propagator registrations successfully updated.")
        int updated,
        @Schema(description = "Number of propagator registrations that failed to update.")
        int failed,
        @Schema(description = "The session mode that was applied.")
        AiFilterTool.SessionMode sessionMode
) {
}
