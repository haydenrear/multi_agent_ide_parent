package com.hayden.multiagentide.filter.config;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Provides the ObjectMapper instance used for runtime filter context serialization/deserialization.
 * Kept separate from the primary app ObjectMapper bean to avoid global mapper side effects.
 */
public interface ContextObjectMapperProvider {

    ObjectMapper objectMapper();

}

