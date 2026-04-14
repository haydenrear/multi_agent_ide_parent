package com.hayden.multiagentide.service;

import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonModule;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import com.github.victools.jsonschema.module.swagger2.Swagger2Module;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.agent.AgentType;
import com.hayden.multiagentide.prompt.contributor.NodeMappings;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.ai.util.json.schema.SpringAiSchemaModule;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Generates JSON schemas for interrupt request types and route-back routing types
 * using victools. These schemas are injected into running ACP sessions via AddMessage
 * when the controller initiates an interrupt or route-back.
 *
 * <p>The "SomeOf(InterruptRequest)" schema is NOT just InterruptRequest in isolation —
 * it is the agent's actual Routing type, filtered so only the interruptRequest field is visible.
 * The LLM's structured response is always the Routing object; we just restrict visible fields.</p>
 */
@Slf4j
@Service
public class InterruptSchemaGenerator {

    /** Cache: routing class → interrupt-only schema string. */
    private final ConcurrentHashMap<Class<?>, String> interruptSchemaCache = new ConcurrentHashMap<>();

    /** Cache: routing class + field type → route-back schema string. */
    private final ConcurrentHashMap<String, String> routeBackSchemaCache = new ConcurrentHashMap<>();

    /**
     * Generate the SomeOf(InterruptRequest) schema for a given agent type.
     * This generates the agent's Routing type schema, but filtered to only include
     * fields whose type is assignable from {@link AgentModels.InterruptRequest}.
     *
     * @param agentType The agent type whose routing schema to filter
     * @return JSON schema string with only the interrupt field(s) visible, or null if agent type has no known routing
     */
    public @Nullable String generateInterruptSchema(AgentType agentType) {
        Class<? extends AgentModels.AgentRouting> routingClass = NodeMappings.routingClassForAgentType(agentType);
        if (routingClass == null) {
            log.warn("No routing class found for agent type: {}", agentType);
            return null;
        }
        return generateInterruptSchemaForRouting(routingClass);
    }

    /**
     * Generate the SomeOf(InterruptRequest) schema for a given agent type (by wire value string).
     */
    public @Nullable String generateInterruptSchema(String agentTypeWireValue) {
        try {
            AgentType agentType = AgentType.fromWireValue(agentTypeWireValue);
            return generateInterruptSchema(agentType);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown agent type wire value: {}", agentTypeWireValue);
            return null;
        }
    }

    /**
     * Generate a filtered schema for the given routing class, keeping only fields
     * whose declared type is assignable from {@link AgentModels.InterruptRequest}.
     */
    public String generateInterruptSchemaForRouting(Class<? extends AgentModels.AgentRouting> routingClass) {
        return interruptSchemaCache.computeIfAbsent(routingClass, clazz -> {
            String schema = generateFilteredSchema(clazz,
                    fieldType -> AgentModels.InterruptRequest.class.isAssignableFrom(fieldType));
            log.debug("Generated interrupt schema for {}: {}", clazz.getSimpleName(), schema);
            return schema;
        });
    }

    /**
     * Generate a route-back schema for a collector routing type, keeping only the
     * route-back request field (the field whose type matches the given route-back type).
     *
     * @param routingClass The collector routing class (e.g., PlanningCollectorRouting)
     * @param routeBackFieldType The specific route-back request type to include (e.g., PlanningOrchestratorRequest.class)
     * @return JSON schema string with only the route-back field visible
     */
    public String generateRouteBackSchema(Class<? extends AgentModels.AgentRouting> routingClass,
                                           Class<?> routeBackFieldType) {
        String cacheKey = routingClass.getName() + ":" + routeBackFieldType.getName();
        return routeBackSchemaCache.computeIfAbsent(cacheKey, key -> {
            String schema = generateFilteredSchema(routingClass,
                    fieldType -> routeBackFieldType.isAssignableFrom(fieldType));
            log.debug("Generated route-back schema for {} (field {}): {}",
                    routingClass.getSimpleName(), routeBackFieldType.getSimpleName(), schema);
            return schema;
        });
    }

    /**
     * Generate a victools schema for the given type, filtering out fields whose declared
     * type does NOT match the given predicate.
     */
    private String generateFilteredSchema(Class<?> type, Predicate<Class<?>> keepFieldType) {
        SchemaGeneratorConfigBuilder configBuilder = new SchemaGeneratorConfigBuilder(
                SchemaVersion.DRAFT_2020_12, OptionPreset.PLAIN_JSON)
                .with(new JacksonModule(JacksonOption.RESPECT_JSONPROPERTY_REQUIRED))
                .with(new Swagger2Module())
                .with(new SpringAiSchemaModule())
                .with(Option.EXTRA_OPEN_API_FORMAT_VALUES)
                .with(Option.PLAIN_DEFINITION_KEYS);

        // Filter: ignore fields whose type is NOT matched by the predicate
        configBuilder.forFields()
                .withIgnoreCheck(field -> {
                    Class<?> fieldType = field.getType().getErasedType();
                    return !keepFieldType.test(fieldType);
                });

        configBuilder.forFields()
                .withRequiredCheck(field -> false);

        SchemaGenerator generator = new SchemaGenerator(configBuilder.build());
        return generator.generateSchema(type).toPrettyString();
    }
}
