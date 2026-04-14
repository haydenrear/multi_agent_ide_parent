package com.hayden.multiagentide.filter.model.layer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.multiagentide.filter.config.FilterConfigProperties;
import com.hayden.multiagentide.prompt.PromptContext;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Filter context for prompt contributor filtering during agent prompt assembly.
 */
@Data
public final class PromptContributorContext implements FilterContext {

    private String layerId;
    private PromptContext ctx;
    private ArtifactKey key;

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private FilterConfigProperties filterConfigProperties;

    @JsonIgnore
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private ObjectMapper objectMapper;

    public PromptContributorContext(String layerId, PromptContext ctx) {
        this.layerId = layerId;
        this.ctx = ctx;
        this.key = ctx.currentContextId();
    }

    @Override
    public String layerId() {
        return layerId;
    }

    @Override
    public ArtifactKey key() {
        return key;
    }

    public PromptContext ctx() {
        return ctx;
    }

    @Override
    public FilterConfigProperties filterConfigProperties() {
        return filterConfigProperties;
    }

    @Override
    public ObjectMapper objectMapper() {
        return objectMapper;
    }

}
