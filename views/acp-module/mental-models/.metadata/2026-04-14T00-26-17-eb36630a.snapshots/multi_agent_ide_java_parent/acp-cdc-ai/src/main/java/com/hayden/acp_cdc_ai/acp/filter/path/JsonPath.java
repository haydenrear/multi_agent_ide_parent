package com.hayden.acp_cdc_ai.acp.filter.path;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import lombok.Builder;

@Builder(toBuilder = true)
@JsonClassDescription("Targets structured content using a JSONPath-style expression.")
public record JsonPath(
        @JsonPropertyDescription("The JSON path expression used to identify the target field or element.")
        String expression
) implements Path {

    @Override
    public FilterEnums.PathType pathType() {
        return FilterEnums.PathType.JSON_PATH;
    }
}
