package com.hayden.multiagentidelib.prompt.contributor;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generates routing schema text from Java routing record types via reflection.
 * Shared between {@link RoutingSchemaPromptContributorFactory} (for prompt injection)
 * and {@link WeAreHerePromptContributor} (for guidance options).
 *
 * <p>For each field of a routing record, this generator produces a bullet with the field
 * name, a shape descriptor (the record components of the field's type), and the
 * {@link JsonPropertyDescription} annotation value as description.</p>
 */
public final class RoutingSchemaGenerator {

    private RoutingSchemaGenerator() {}

    /**
     * Infrastructure fields that the LLM should not set; excluded from shape output.
     */
    private static final Set<String> INFRASTRUCTURE_FIELDS = Set.of(
            "contextId", "worktreeContext", "previousContext", "mergeDescriptor",
            "mergeAggregation", "metadata"
    );

    /**
     * Generate the full "Return a routing object with exactly one of:" block for a
     * given routing record type.
     *
     * @param routingType the routing record class (e.g., OrchestratorRouting.class)
     * @return formatted text block ready for LLM consumption
     */
    public static String generateSchema(Class<?> routingType) {
        if (!routingType.isRecord()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Return a routing object with exactly one of:\n");

        for (RecordComponent component : routingType.getRecordComponents()) {
            String fieldName = component.getName();
            Class<?> fieldType = component.getType();
            String description = getFieldDescription(component);

            sb.append("- **").append(fieldName).append("**");

            String shape = generateShape(fieldType);
            if (shape != null && !shape.isEmpty()) {
                sb.append(": ").append(shape);
            }

            if (description != null && !description.isEmpty()) {
                sb.append(" — ").append(description);
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Generate a simpler routing options list for use in guidance text.
     * Lists field names with their {@link JsonPropertyDescription} descriptions.
     *
     * @param routingType the routing record class
     * @return formatted routing options text
     */
    public static String generateGuidanceOptions(Class<?> routingType) {
        if (!routingType.isRecord()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**Routing options:**\n");

        for (RecordComponent component : routingType.getRecordComponents()) {
            String fieldName = component.getName();
            String description = getFieldDescription(component);

            sb.append("- `").append(fieldName).append("`");
            if (description != null && !description.isEmpty()) {
                sb.append(" — ").append(description);
            }
            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * Generate the compact field-shape string for a routing field's type.
     * E.g., "{ type, reason, contextForDecision, choices, confirmationItems, phase, goal }"
     *
     * <p>For interrupt request types, this naturally includes both base and agent-specific fields
     * since the specific subtype's record components contain all of them.</p>
     *
     * @param fieldType the type of the routing field
     * @return shape string like "{ field1, field2, ... }" or null if not a record
     */
    static String generateShape(Class<?> fieldType) {
        if (!fieldType.isRecord()) {
            return null;
        }

        RecordComponent[] components = fieldType.getRecordComponents();
        if (components.length == 0) {
            return "{}";
        }

        List<String> fieldNames = Arrays.stream(components)
                .map(RecordComponent::getName)
                .filter(name -> !INFRASTRUCTURE_FIELDS.contains(name))
                .collect(Collectors.toList());

        if (fieldNames.isEmpty()) {
            return "{}";
        }

        return "{ " + String.join(", ", fieldNames) + " }";
    }

    /**
     * Get the {@link JsonPropertyDescription} annotation value for a record component.
     *
     * @param component the record component
     * @return the description string, or empty string if not annotated
     */
    private static String getFieldDescription(RecordComponent component) {
        JsonPropertyDescription ann = component.getAnnotation(JsonPropertyDescription.class);
        return ann != null ? ann.value() : "";
    }
}
