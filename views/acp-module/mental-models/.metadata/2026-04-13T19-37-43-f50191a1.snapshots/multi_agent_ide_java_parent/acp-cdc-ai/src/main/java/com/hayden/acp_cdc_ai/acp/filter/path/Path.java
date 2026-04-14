package com.hayden.acp_cdc_ai.acp.filter.path;

import com.fasterxml.jackson.annotation.JsonClassDescription;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;

/**
 * Sealed path type for targeting document structure locations.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "pathType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = RegexPath.class, name = "REGEX"),
        @JsonSubTypes.Type(value = MarkdownPath.class, name = "MARKDOWN_PATH"),
        @JsonSubTypes.Type(value = JsonPath.class, name = "JSON_PATH")
})
@JsonClassDescription("A polymorphic path selector used to target structured or textual content for filtering.")
public sealed interface Path
        permits RegexPath, MarkdownPath, JsonPath {

    @JsonPropertyDescription("The path type discriminator used for schema generation and polymorphic deserialization.")
    FilterEnums.PathType pathType();

    @JsonPropertyDescription("The path expression interpreted according to the selected pathType.")
    String expression();
}
