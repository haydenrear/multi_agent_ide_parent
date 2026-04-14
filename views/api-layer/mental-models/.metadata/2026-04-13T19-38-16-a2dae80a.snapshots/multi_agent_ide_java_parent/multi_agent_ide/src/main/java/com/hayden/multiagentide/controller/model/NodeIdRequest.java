package com.hayden.multiagentide.controller.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Request containing a node identifier")
public record NodeIdRequest(
        @NotBlank @Schema(description = "The node identifier (ArtifactKey format, e.g. ak:01KJ...)", example = "ak:01KJ1234567890ABCDEF") String nodeId
) {
}
