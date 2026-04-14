package com.hayden.multiagentide.filter.model.executor;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.acp_cdc_ai.acp.filter.Instruction;
import com.hayden.multiagentide.filter.model.layer.FilterContext;
import com.hayden.multiagentide.filter.model.layer.DefaultPathFilterContext;
import com.hayden.multiagentide.filter.service.FilterDescriptor;
import com.hayden.multiagentide.filter.service.FilterResult;
import com.hayden.multiagentide.model.executor.AiPropagatorTool;
import com.hayden.multiagentide.model.executor.AiTransformerTool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

/**
 * Produces either transformed payload output, instruction list, or both.
 * Sealed interface with four permitted executor strategies.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "executorType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = BinaryExecutor.class, name = "BINARY"),
        @JsonSubTypes.Type(value = JavaFunctionExecutor.class, name = "JAVA_FUNCTION"),
        @JsonSubTypes.Type(value = PythonExecutor.class, name = "PYTHON"),
        @JsonSubTypes.Type(value = AiFilterTool.class, name = "AI"),
        @JsonSubTypes.Type(value = AiPropagatorTool.class, name = "AI_PROPAGATOR"),
        @JsonSubTypes.Type(value = AiTransformerTool.class, name = "AI_TRANSFORMER")
})
public interface ExecutableTool<I, O, CTX extends FilterContext> extends BiFunction<I, CTX, FilterResult<O>> {

    FilterEnums.ExecutorType executorType();

    String configVersion();

    static <I, O, CTX extends FilterContext> O parseExecutorResponse(String response,
                                                                     I input,
                                                                     CTX filterContext,
                                                                     ObjectMapper objectMapper) throws Exception {
        if (response == null || response.isBlank()) {
            return null;
        }

        if (filterContext instanceof DefaultPathFilterContext) {
            return (O) objectMapper.readValue(
                    response,
                    new TypeReference<List<Instruction>>() {
                    }
            );
        }
        if (input instanceof Events.GraphEvent graphEvent) {
            JsonNode responseJson = objectMapper.readTree(response);
            return (O) objectMapper.treeToValue(withGraphEventType(responseJson, graphEvent.eventType()), Events.GraphEvent.class);
        }
        return objectMapper.readValue(response, new TypeReference<O>() {
        });
    }

    @SuppressWarnings("unchecked")
    static <I, O, CTX extends FilterContext> O coerceExecutorOutput(Object output,
                                                                    I input,
                                                                    CTX filterContext,
                                                                    ObjectMapper objectMapper) throws Exception {
        if (output == null) {
            return null;
        }
        if (output instanceof String response) {
            return parseExecutorResponse(response, input, filterContext, objectMapper);
        }
        if (filterContext instanceof DefaultPathFilterContext) {
            return (O) objectMapper.convertValue(
                    output,
                    new TypeReference<List<Instruction>>() {
                    }
            );
        }
        if (input instanceof Events.GraphEvent graphEvent) {
            JsonNode responseJson = objectMapper.valueToTree(output);
            return (O) objectMapper.treeToValue(withGraphEventType(responseJson, graphEvent.eventType()), Events.GraphEvent.class);
        }
        return objectMapper.convertValue(output, new TypeReference<O>() {
        });
    }

    static Object serializeExecutorInput(Object input, ObjectMapper objectMapper) {
        if (input instanceof Events.GraphEvent graphEvent) {
            JsonNode inputJson = objectMapper.valueToTree(input);
            return withGraphEventType(inputJson, graphEvent.eventType());
        }
        return input;
    }

    static ObjectMapper contextObjectMapper(FilterContext filterContext, ObjectMapper fallbackObjectMapper) {
        if (filterContext != null && filterContext.objectMapper() != null) {
            return filterContext.objectMapper();
        }
        return fallbackObjectMapper;
    }

    static <I, O, CTX extends FilterContext> O fallbackOutput(I input,
                                                              CTX filterContext,
                                                              ObjectMapper objectMapper) {
        if (filterContext instanceof FilterContext.PathFilterContext) {
            return (O) List.of();
        }
        try {
            return coerceExecutorOutput(input, input, filterContext, objectMapper);
        } catch (Exception ignored) {
            return null;
        }
    }

    static FilterDescriptor executorErrorDescriptor(Throwable throwable,
                                                    FilterEnums.ExecutorType executorType,
                                                    Map<String, String> details) {
        Map<String, String> resolvedDetails = new LinkedHashMap<>();
        if (details != null && !details.isEmpty()) {
            resolvedDetails.putAll(details);
        }

        FilterDescriptor.Entry entry = new FilterDescriptor.Entry(
                "ERROR",
                null,
                null,
                null,
                null,
                null,
                "ERROR",
                executorType == null ? null : executorType.name(),
                resolvedDetails,
                List.of()
        );
        return new FilterDescriptor.ErrorFilterDescriptor(throwable, List.of(), entry);
    }

    private static JsonNode withGraphEventType(JsonNode node, String eventType) {
        if (node instanceof ObjectNode objectNode && !objectNode.hasNonNull("eventType")) {
            objectNode.put("eventType", eventType);
        }
        return node;
    }

}
