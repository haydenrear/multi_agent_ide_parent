package com.hayden.acp_cdc_ai.acp.filter.path;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import lombok.Builder;

@Builder(toBuilder = true)
@JsonClassDescription("Targets content by applying a regular expression path selector.")
public record RegexPath(
        @JsonPropertyDescription("The regular expression used to identify the target content.")
        String expression
) implements Path {

    @Override
    public FilterEnums.PathType pathType() {
        return FilterEnums.PathType.REGEX;
    }
}
