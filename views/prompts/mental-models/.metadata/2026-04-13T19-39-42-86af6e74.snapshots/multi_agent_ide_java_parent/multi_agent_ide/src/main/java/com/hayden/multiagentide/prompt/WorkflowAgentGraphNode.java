package com.hayden.multiagentide.prompt;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a node in the workflow agent graph.
 * Used for building visual representations of where we are in the workflow
 * and what routing options are available.
 */
public interface WorkflowAgentGraphNode {

    /**
     * The name of this node (e.g., "OrchestratorRouting", "DiscoveryCollectorRouting")
     */
    String nodeName();

    /**
     * The request type that leads to this routing
     */
    Class<?> requestType();

    /**
     * The routing type that defines the branches from this node
     */
    Class<?> routingType();

    /**
     * Get all possible branch targets from this routing type
     */
    List<RoutingBranch> branches();

    /**
     * Represents a single branch option from a routing node
     */
    record RoutingBranch(
            String fieldName,
            Class<?> targetType,
            String description
    ) {}

    /**
     * Build routing branches by reflecting on a routing type's record components
     */
    static List<RoutingBranch> buildBranchesFromRouting(Class<?> routingType) {
        List<RoutingBranch> branches = new ArrayList<>();
        
        if (!routingType.isRecord()) {
            return branches;
        }

        for (RecordComponent component : routingType.getRecordComponents()) {
            String fieldName = component.getName();
            Class<?> fieldType = component.getType();
            String description = describeBranch(fieldName, fieldType);
            branches.add(new RoutingBranch(fieldName, fieldType, description));
        }

        return branches;
    }

    /**
     * Generate a human-readable description for a routing branch
     */
    static String describeBranch(String fieldName, Class<?> fieldType) {
        // Handle interrupt requests
        if (fieldName.contains("interrupt") || fieldType.getSimpleName().contains("InterruptRequest")) {
            return "Pause workflow for human input";
        }

        // Handle collector results (final output of a phase)
        if (fieldType.getSimpleName().contains("CollectorResult")) {
            return "Produce final result for this phase";
        }

        // Handle collector requests (consolidation step)
        if (fieldType.getSimpleName().contains("CollectorRequest")) {
            return "Consolidate agent results";
        }

        // Handle agent requests (delegation to sub-agents)
        if (fieldType.getSimpleName().contains("AgentRequests")) {
            return "Delegate work to sub-agents";
        }

        // Handle orchestrator requests (phase transitions)
        if (fieldType.getSimpleName().contains("OrchestratorRequest")) {
            String phase = fieldType.getSimpleName()
                    .replace("OrchestratorRequest", "")
                    .replace("Request", "");
            if (phase.isEmpty()) {
                return "Return to main orchestrator";
            }
            return "Route to " + phase + " phase";
        }

        if (fieldType.getSimpleName().contains("ContextManagerRoutingRequest")) {
            return "Request context reconstruction";
        }

        // Default
        return "Route to " + fieldType.getSimpleName();
    }

    /**
     * Get a display name for a request type
     */
    static String getDisplayName(Class<?> type) {
        String name = type.getSimpleName();
        
        // Remove common suffixes for cleaner display
        name = name.replace("Request", "")
                   .replace("Requests", " (dispatch)")
                   .replace("Result", " Result")
                   .replace("Routing", "");
        
        // Add spaces before capitals for readability
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (i > 0 && Character.isUpperCase(c) && !Character.isUpperCase(name.charAt(i - 1))) {
                result.append(' ');
            }
            result.append(c);
        }
        
        return result.toString().trim();
    }
}
