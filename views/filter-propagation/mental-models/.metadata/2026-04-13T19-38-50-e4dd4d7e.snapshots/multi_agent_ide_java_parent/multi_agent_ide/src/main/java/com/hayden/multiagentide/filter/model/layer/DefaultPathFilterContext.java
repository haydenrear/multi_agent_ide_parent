package com.hayden.multiagentide.filter.model.layer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.multiagentide.filter.config.FilterConfigProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.springframework.beans.factory.annotation.Autowired;

@Data
public final class DefaultPathFilterContext implements FilterContext.PathFilterContext {

    private String layerId;

    private FilterContext filterContext;

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Autowired
    private FilterConfigProperties filterConfigProperties;

    @JsonIgnore
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private ObjectMapper objectMapper;

    @Override
    public FilterConfigProperties filterConfigProperties() {
        return filterConfigProperties;
    }

    @Override
    public ArtifactKey key() {
        return filterContext.key();
    }

    @Override
    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    public DefaultPathFilterContext(String layerId,
                                    FilterContext filterContext) {
        this.layerId = layerId;
        this.filterContext = filterContext;
        if (filterContext != null) {
            this.filterConfigProperties = filterContext.filterConfigProperties();
            this.objectMapper = filterContext.objectMapper();
        }
    }

    @Override
    public String layerId() {
        return layerId;
    }

    public FilterContext filterContext() {
        return filterContext;
    }

}
