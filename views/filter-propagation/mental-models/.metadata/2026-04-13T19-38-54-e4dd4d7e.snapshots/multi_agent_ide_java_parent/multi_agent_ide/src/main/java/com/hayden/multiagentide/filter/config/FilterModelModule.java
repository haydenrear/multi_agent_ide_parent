package com.hayden.multiagentide.filter.config;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import com.hayden.multiagentide.filter.model.AiPathFilter;
import com.hayden.multiagentide.filter.model.PathFilter;
import com.hayden.multiagentide.filter.model.executor.AiFilterTool;
import com.hayden.multiagentide.filter.model.executor.BinaryExecutor;
import com.hayden.multiagentide.filter.model.executor.JavaFunctionExecutor;
import com.hayden.multiagentide.filter.model.executor.PythonExecutor;
import com.hayden.multiagentide.filter.model.*;
import com.hayden.acp_cdc_ai.acp.filter.Instruction;
import com.hayden.multiagentide.filter.model.interpreter.Interpreter;
import com.hayden.multiagentide.filter.model.layer.GraphEventObjectContext;
import com.hayden.multiagentide.filter.model.layer.Layer;
import com.hayden.multiagentide.filter.model.layer.LayerCtx;
import com.hayden.multiagentide.filter.model.layer.PromptContributorContext;
import com.hayden.acp_cdc_ai.acp.filter.path.JsonPath;
import com.hayden.acp_cdc_ai.acp.filter.path.MarkdownPath;
import com.hayden.acp_cdc_ai.acp.filter.path.RegexPath;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Jackson module registration for the filter model sealed type hierarchy.
 * Registers named subtypes so Jackson can serialize/deserialize the sealed interfaces polymorphically.
 */
@Configuration
public class FilterModelModule {

    @Bean
    public Module filterModelJacksonModule() {
        SimpleModule module = new SimpleModule("FilterModelModule");

        // Filter subtypes
        module.registerSubtypes(
                new NamedType(PathFilter.class, "PATH"),
                new NamedType(AiPathFilter.class, "AI")
        );

        // ExecutableTool subtypes
        module.registerSubtypes(
                new NamedType(BinaryExecutor.class, "BINARY"),
                new NamedType(JavaFunctionExecutor.class, "JAVA_FUNCTION"),
                new NamedType(PythonExecutor.class, "PYTHON"),
                new NamedType(AiFilterTool.class, "AI")
        );

        // Interpreter subtypes
        module.registerSubtypes(
                new NamedType(Interpreter.RegexInterpreter.class, "REGEX"),
                new NamedType(Interpreter.MarkdownPathInterpreter.class, "MARKDOWN_PATH"),
                new NamedType(Interpreter.JsonPathInterpreter.class, "JSON_PATH")
        );

        // Path subtypes
        module.registerSubtypes(
                new NamedType(RegexPath.class, "REGEX"),
                new NamedType(MarkdownPath.class, "MARKDOWN_PATH"),
                new NamedType(JsonPath.class, "JSON_PATH")
        );

        // Instruction subtypes
        module.registerSubtypes(
                new NamedType(Instruction.Replace.class, "REPLACE"),
                new NamedType(Instruction.Set.class, "SET"),
                new NamedType(Instruction.Remove.class, "REMOVE"),
                new NamedType(Instruction.ReplaceIfMatch.class, "REPLACE_IF_MATCH"),
                new NamedType(Instruction.RemoveIfMatch.class, "REMOVE_IF_MATCH")
        );

        // Layer subtypes
        module.registerSubtypes(
                new NamedType(Layer.WorkflowAgentLayer.class, "WORKFLOW_AGENT"),
                new NamedType(Layer.WorkflowAgentActionLayer.class, "WORKFLOW_AGENT_ACTION"),
                new NamedType(Layer.ControllerLayer.class, "CONTROLLER"),
                new NamedType(Layer.ControllerUiEventPollLayer.class, "CONTROLLER_UI_EVENT_POLL")
        );

        // LayerCtx subtypes
        module.registerSubtypes(
                new NamedType(LayerCtx.AgentLayerCtx.class, "AGENT"),
                new NamedType(LayerCtx.ActionLayerCtx.class, "ACTION"),
                new NamedType(LayerCtx.ControllerLayerCtx.class, "CONTROLLER")
        );

        // FilterContext subtypes
        module.registerSubtypes(
                new NamedType(PromptContributorContext.class, "PROMPT_CONTRIBUTOR"),
                new NamedType(GraphEventObjectContext.class, "CONTROLLER_EVENT")
        );

        return module;
    }
}
