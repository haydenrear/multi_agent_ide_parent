package com.hayden.multiagentide.filter.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.hayden.multiagentide.filter.model.executor.ExecutableTool;

import java.time.Instant;
import java.util.function.BiFunction;

/**
 * Typed policy target and execution entrypoint.
 * Sealed interface with two permitted branches: ObjectSerializerFilter and PathFilter.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "filterType")
@JsonSubTypes({
        @JsonSubTypes.Type(value = PathFilter.class, name = "PATH"),
        @JsonSubTypes.Type(value = AiPathFilter.class, name = "AI"),
})
public sealed interface Filter<I, O, CTX> extends BiFunction<I, CTX, O>
        permits PathFilter, AiPathFilter {

    String id();
    String name();
    String description();
    String sourcePath();
    com.hayden.acp_cdc_ai.acp.filter.FilterEnums.PolicyStatus status();
    int priority();
    Instant createdAt();
    Instant updatedAt();

    ExecutableTool executor();


}
