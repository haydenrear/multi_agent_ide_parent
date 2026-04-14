package com.hayden.multiagentide.propagation.controller.dto;

import com.hayden.multiagentide.filter.model.executor.AiFilterTool;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request to update the session mode for a propagator or all propagators.")
public record UpdateSessionModeRequest(
        @NotNull
        @Schema(description = "The new session mode.", allowableValues = {"PER_INVOCATION", "SAME_SESSION_FOR_ACTION", "SAME_SESSION_FOR_ALL", "SAME_SESSION_FOR_AGENT"})
        AiFilterTool.SessionMode sessionMode
) {
}
