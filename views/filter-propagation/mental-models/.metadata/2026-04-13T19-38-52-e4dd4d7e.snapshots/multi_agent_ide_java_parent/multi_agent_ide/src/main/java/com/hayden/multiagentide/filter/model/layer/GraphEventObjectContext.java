package com.hayden.multiagentide.filter.model.layer;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.filter.config.FilterConfigProperties;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Filter context for controller event filtering before user-facing emission.
 */
@Slf4j
@Data
public final class GraphEventObjectContext implements FilterContext {


    private final String layerId;

    private ArtifactKey key;

    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    @Autowired
    private FilterConfigProperties filterConfigProperties;

    @JsonIgnore
    @EqualsAndHashCode.Exclude
    @ToString.Exclude
    private ObjectMapper objectMapper;

    @Override
    public ArtifactKey key() {
        return key;
    }

    @Override
    public FilterConfigProperties filterConfigProperties() {
        return filterConfigProperties;
    }

    @Override
    public ObjectMapper objectMapper() {
        return objectMapper;
    }

    /**
     *
     */
    public GraphEventObjectContext(
            String layerId,
            Events.GraphEvent key
    ) {
        this.layerId = layerId;
        if (key == null || key.nodeId() == null || !ArtifactKey.isValid(key.nodeId())) {
            log.debug("Skipping graph event artifact key creation for non-artifact nodeId {}", key == null ? null : key.nodeId());
            return;
        }
        this.key = new ArtifactKey(key.nodeId());
    }

    @Override
    public String layerId() {
        return layerId;
    }


}
