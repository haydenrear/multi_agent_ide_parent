package com.hayden.acp_cdc_ai.acp.filter;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import lombok.Builder;

@JsonClassDescription("A value matcher used by conditional filter instructions to decide whether a mutation should apply.")
@Builder(toBuilder = true)
public record InstructionMatcher(
        @JsonPropertyDescription("The matcher algorithm to apply, such as exact equality or full-string regular expression matching.")
        FilterEnums.MatcherType matcherType,
        @JsonPropertyDescription("The comparison value or pattern consumed by the matcher.")
        String value
) {
}
