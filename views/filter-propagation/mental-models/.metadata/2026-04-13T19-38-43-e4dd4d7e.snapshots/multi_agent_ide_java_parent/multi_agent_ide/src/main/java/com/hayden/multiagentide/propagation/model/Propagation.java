package com.hayden.multiagentide.propagation.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Combined propagation payload stored in {@code propagatedText}.
 * <p>
 * Both the original request/result object ({@code propagationRequest}) and the
 * LLM assessment ({@code llmOutput}) are captured here so the controller has the
 * full picture without needing a separate /records lookup.
 * <p>
 * {@code llmOutput} is set to a failure message string when the LLM call did not
 * succeed, ensuring an item is always created regardless of AI availability.
 * <p>
 * {@code propagationRequest} is stored as a structured JSON object (not a string),
 * so it can be inspected directly without double-parsing.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record Propagation(
        @Schema(description = "LLM propagator output or failure message")
        String llmOutput,
        @Schema(description = "Original propagation request/response payload as a structured object")
        JsonNode propagationRequest
) {
}
