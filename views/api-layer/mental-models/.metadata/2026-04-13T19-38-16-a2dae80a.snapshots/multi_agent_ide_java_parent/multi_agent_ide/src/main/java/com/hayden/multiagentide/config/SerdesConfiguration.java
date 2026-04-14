package com.hayden.multiagentide.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.multiagentide.filter.config.ContextObjectMapperProvider;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for Jackson serialization/deserialization of Artifacts and AgentModels.
 * Registers mix-ins for polymorphic type handling.
 */
@Configuration
public class SerdesConfiguration {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer artifactSerdesCustomizer() {
        return builder -> {};
    }

//    DO NOT MAKE THIS A BEAN!
    public static Jackson2ObjectMapperBuilderCustomizer artifactAndAgentModelMixIn() {
        return builder -> {
            // Register mix-in for Artifact polymorphic serialization
            builder.mixIn(Artifact.class, ArtifactMixin.WithTypeInfo.class);

            // Register mix-in for AgentModel polymorphic serialization
            builder.mixIn(Artifact.AgentModel.class, AgentModelMixin.WithTypeInfo.class);
        };
    }

    @Bean
    public ContextObjectMapperProvider contextObjectMapperProvider(ObjectMapper objectMapper) {
        ObjectMapper contextMapper = objectMapper.copy();
        contextMapper.addMixIn(Artifact.class, ArtifactMixin.WithTypeInfo.class);
        contextMapper.addMixIn(Artifact.AgentModel.class, AgentModelMixin.WithTypeInfo.class);
        return () -> contextMapper;
    }
}
