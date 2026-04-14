package com.hayden.multiagentide.propagation.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.hayden.multiagentide.model.AiTextPropagator;
import com.hayden.multiagentide.model.TextPropagator;
import com.hayden.multiagentide.model.executor.AiPropagatorTool;
import com.hayden.multiagentide.model.layer.AiPropagatorContext;
import com.hayden.multiagentide.model.layer.DefaultPropagationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PropagationModelModule {

    @Bean
    public Module propagationJacksonModule() {
        SimpleModule module = new SimpleModule("PropagationModelModule");
        module.registerSubtypes(
                new NamedType(TextPropagator.class, "TEXT"),
                new NamedType(AiTextPropagator.class, "AI_TEXT"),
                new NamedType(AiPropagatorTool.class, "AI_PROPAGATOR"),
                new NamedType(DefaultPropagationContext.class, "DEFAULT_PROPAGATION_CONTEXT"),
                new NamedType(AiPropagatorContext.class, "AI_PROPAGATOR_CONTEXT")
        );
        return module;
    }
}
