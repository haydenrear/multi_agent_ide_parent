package com.hayden.acp_cdc_ai.acp.events;

import com.fasterxml.jackson.annotation.JsonInclude;

public interface HasContextId {

    /**
     * Hierarchical primary key across various types of datasets.
     * @return the artifact key
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    ArtifactKey contextId();

}
