package com.hayden.multiagentide.transformation.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.hayden.multiagentide.model.AiTextTransformer;
import com.hayden.multiagentide.model.TextTransformer;
import com.hayden.multiagentide.model.executor.AiTransformerTool;
import com.hayden.multiagentide.model.layer.AiTransformerContext;
import com.hayden.multiagentide.model.layer.ControllerEndpointTransformationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TransformationModelModule {

    @Bean
    public Module transformationJacksonModule() {
        SimpleModule module = new SimpleModule("TransformationModelModule");
        module.registerSubtypes(
                new NamedType(TextTransformer.class, "TEXT"),
                new NamedType(AiTextTransformer.class, "AI_TEXT"),
                new NamedType(AiTransformerTool.class, "AI_TRANSFORMER"),
                new NamedType(ControllerEndpointTransformationContext.class, "CONTROLLER_ENDPOINT_TRANSFORMATION_CONTEXT"),
                new NamedType(AiTransformerContext.class, "AI_TRANSFORMER_CONTEXT")
        );
        return module;
    }
}
