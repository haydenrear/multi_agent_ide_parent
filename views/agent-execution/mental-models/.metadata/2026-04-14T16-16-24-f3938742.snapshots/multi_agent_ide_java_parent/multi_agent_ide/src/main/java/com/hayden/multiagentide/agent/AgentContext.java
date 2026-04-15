package com.hayden.multiagentide.agent;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;

import java.util.List;

public interface AgentContext extends com.hayden.acp_cdc_ai.acp.events.HasContextId, Artifact.AgentModel, AgentPretty {


    @Override
    @JsonIgnore
    default ArtifactKey key() {
        return contextId();
    }

    @Override
    @JsonIgnore
    default List<Artifact.AgentModel> children() {
        return List.of();
    }

    @Override
    default <T extends Artifact.AgentModel> T withChildren(List<Artifact.AgentModel> c) {
        return (T) this;
    }

}
