package com.hayden.multiagentide.prompt;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactHashing;
import com.hayden.acp_cdc_ai.acp.filter.FilteredObject;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public interface PromptContributor extends FilteredObject, RetryAware {

    String name();

    boolean include(PromptContext promptContext);

    String contribute(PromptContext context);

    String template();

    default Map<String, Object> args() {
        return Map.of();
    }

    int priority();

    default String templateStaticId() {
        return name();
    }

    default String templateText() {
        return template();
    }

    @JsonIgnore
    default Optional<String> contentHash() {
        String text = templateText();
        return text == null ? Optional.empty() : Optional.of(ArtifactHashing.hashText(text));
    }

    default Map<String, String> metadata() {
        return Map.of();
    }

    default List<Artifact> children() {
        return List.of();
    }

    default PromptContributorDescriptor descriptor() {
        return PromptContributorDescriptor.fromContributor(this, "REGISTRY");
    }

    default boolean isApplicable(PromptContext agentType) {
        if (agentType == null) {
            return false;
        }
        return include(agentType);
    }
}
