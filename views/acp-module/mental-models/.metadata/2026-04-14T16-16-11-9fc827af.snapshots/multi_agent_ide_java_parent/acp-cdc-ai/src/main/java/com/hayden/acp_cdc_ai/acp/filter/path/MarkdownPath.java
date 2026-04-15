package com.hayden.acp_cdc_ai.acp.filter.path;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import lombok.Builder;

@Builder(toBuilder = true)
@JsonClassDescription("Targets a markdown heading or markdown-structured section within text content.")
public record MarkdownPath(
        @JsonPropertyDescription("The markdown path expression used to identify the target heading or section.")
        String expression
) implements Path {

    @Override
    public FilterEnums.PathType pathType() {
        return FilterEnums.PathType.MARKDOWN_PATH;
    }
}
