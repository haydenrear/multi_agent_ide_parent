package com.hayden.multiagentide.prompt.contributor;

import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.agent.AgentModels.InterruptRequest;
import com.hayden.multiagentide.agent.AgentType;
import com.hayden.multiagentide.model.nodes.*;
import com.hayden.multiagentide.model.nodes.*;
import org.jspecify.annotations.Nullable;

import java.util.*;

/**
 * Centralized registry of agent node type metadata: display names, routing field names, and
 * request classes. All prompt contributors and factories that need to display or route to
 * agent types should reference this interface rather than maintaining their own mappings.
 *
 * <p>The {@code fieldName} corresponds to the JSON property name on routing records
 * (e.g., {@link AgentModels.ContextManagerResultRouting}). It is {@code null} for types
 * that do not appear as routable fields (e.g., abstract interfaces, interrupt base type).</p>
 */
public interface NodeMappings {

    /**
     * Map of request types to their corresponding routing types
     */
    static Map<Class<?>, Class<? extends AgentModels.AgentRouting>> getRequestToRoutingMap() {
        Map<Class<?>, Class<? extends AgentModels.AgentRouting>> map = new LinkedHashMap<>();

//        TODO: retrieve this information using sealed hierarchy introspection
//        AgentModels.AgentRequest.class.getPermittedSubclasses()

        // Main orchestrator
        map.put(AgentModels.OrchestratorRequest.class, AgentModels.OrchestratorRouting.class);
        map.put(AgentModels.OrchestratorCollectorRequest.class, AgentModels.OrchestratorCollectorRouting.class);

        // Discovery phase
        map.put(AgentModels.DiscoveryOrchestratorRequest.class, AgentModels.DiscoveryOrchestratorRouting.class);
        map.put(AgentModels.DiscoveryAgentRequest.class, AgentModels.DiscoveryAgentRouting.class);
        map.put(AgentModels.DiscoveryAgentRequests.class, AgentModels.DiscoveryAgentDispatchRouting.class);
        map.put(AgentModels.DiscoveryCollectorRequest.class, AgentModels.DiscoveryCollectorRouting.class);

        // Planning phase
        map.put(AgentModels.PlanningOrchestratorRequest.class, AgentModels.PlanningOrchestratorRouting.class);
        map.put(AgentModels.PlanningAgentRequest.class, AgentModels.PlanningAgentRouting.class);
        map.put(AgentModels.PlanningAgentRequests.class, AgentModels.PlanningAgentDispatchRouting.class);
        map.put(AgentModels.PlanningCollectorRequest.class, AgentModels.PlanningCollectorRouting.class);

        // Ticket phase
        map.put(AgentModels.TicketOrchestratorRequest.class, AgentModels.TicketOrchestratorRouting.class);
        map.put(AgentModels.TicketAgentRequest.class, AgentModels.TicketAgentRouting.class);
        map.put(AgentModels.TicketAgentRequests.class, AgentModels.TicketAgentDispatchRouting.class);
        map.put(AgentModels.TicketCollectorRequest.class, AgentModels.TicketCollectorRouting.class);

        // Context manager
        map.put(AgentModels.ContextManagerRequest.class, AgentModels.ContextManagerResultRouting.class);

        // Interrupt requests
        map.put(InterruptRequest.OrchestratorInterruptRequest.class, AgentModels.InterruptRouting.class);
        map.put(InterruptRequest.OrchestratorCollectorInterruptRequest.class, AgentModels.InterruptRouting.class);
        map.put(InterruptRequest.DiscoveryOrchestratorInterruptRequest.class, AgentModels.InterruptRouting.class);
        map.put(InterruptRequest.DiscoveryAgentDispatchInterruptRequest.class, AgentModels.InterruptRouting.class);
        map.put(InterruptRequest.DiscoveryCollectorInterruptRequest.class, AgentModels.InterruptRouting.class);
        map.put(InterruptRequest.PlanningOrchestratorInterruptRequest.class, AgentModels.InterruptRouting.class);
        map.put(InterruptRequest.PlanningAgentDispatchInterruptRequest.class, AgentModels.InterruptRouting.class);
        map.put(InterruptRequest.PlanningCollectorInterruptRequest.class, AgentModels.InterruptRouting.class);
        map.put(InterruptRequest.TicketOrchestratorInterruptRequest.class, AgentModels.InterruptRouting.class);
        map.put(InterruptRequest.TicketAgentDispatchInterruptRequest.class, AgentModels.InterruptRouting.class);
        map.put(InterruptRequest.TicketCollectorInterruptRequest.class, AgentModels.InterruptRouting.class);

        map.put(InterruptRequest.TicketAgentInterruptRequest.class, AgentModels.TicketAgentRouting.class);
        map.put(InterruptRequest.PlanningAgentInterruptRequest.class, AgentModels.PlanningAgentRouting.class);
        map.put(InterruptRequest.DiscoveryAgentInterruptRequest.class, AgentModels.DiscoveryAgentRouting.class);

        return map;
    }

    record NodeMapping(
            Class<?> requestType,
            String displayName,
            @Nullable String fieldName
    ) {}

    List<NodeMapping> ALL_MAPPINGS = initAllMappings();

    /**
     * The AgentRequest -> Routing
     */
    Map<Class<?>, Class<? extends AgentModels.AgentRouting>> REQUEST_TO_ROUTING = getRequestToRoutingMap();

    /**
     * TODO:
     */
    Map<Class<? extends AgentModels.AgentRouting>, List<? extends AgentModels.AgentRequest>> ROUTING_TO_REQUESTS  = new HashMap<>();
//            = getRequestToRoutingMap();

    /** Display name lookup by request type. */
    Map<Class<?>, String> DISPLAY_NAMES = initDisplayNames();

    /** Field name lookup by request type (only types with a routable field). */
    Map<Class<?>, String> FIELD_NAMES = initFieldNames();

    static String displayName(Class<?> type) {
        return DISPLAY_NAMES.getOrDefault(type, type.getSimpleName());
    }

    static @Nullable String fieldName(Class<?> type) {
        return FIELD_NAMES.get(type);
    }

    static @Nullable NodeMapping findByType(Class<?> type) {
        for (NodeMapping m : ALL_MAPPINGS) {
            if (m.requestType().equals(type)) {
                return m;
            }
        }
        return null;
    }

    /**
     * AgentType → primary (non-interrupt) routing class. Used by schema generators
     * to resolve the Routing type for a given agent when generating filtered schemas
     * (e.g., SomeOf(InterruptRequest) = Routing filtered to interrupt fields only).
     */
    Map<AgentType, Class<? extends AgentModels.AgentRouting>> AGENT_TYPE_TO_ROUTING = initAgentTypeToRouting();

    static @Nullable Class<? extends AgentModels.AgentRouting> routingClassForAgentType(AgentType agentType) {
        return AGENT_TYPE_TO_ROUTING.get(agentType);
    }

    static @Nullable AgentType agentTypeFromRequest(AgentModels.AgentRequest node) {
        return switch(node) {
            case AgentModels.AiFilterRequest ignored -> AgentType.AI_FILTER;
            case AgentModels.AiPropagatorRequest ignored -> AgentType.AI_PROPAGATOR;
            case AgentModels.AiTransformerRequest ignored -> AgentType.AI_TRANSFORMER;
            case AgentModels.CommitAgentRequest ignored -> AgentType.COMMIT_AGENT;
            case AgentModels.MergeConflictRequest ignored -> AgentType.MERGE_CONFLICT_AGENT;
            case AgentModels.ContextManagerRequest ignored -> AgentType.CONTEXT_MANAGER;
            case AgentModels.ContextManagerRoutingRequest ignored -> AgentType.CONTEXT_MANAGER;
            case AgentModels.OrchestratorRequest ignored -> AgentType.ORCHESTRATOR;
            case AgentModels.OrchestratorCollectorRequest ignored -> AgentType.ORCHESTRATOR_COLLECTOR;
            case AgentModels.DiscoveryOrchestratorRequest ignored -> AgentType.DISCOVERY_ORCHESTRATOR;
            case AgentModels.DiscoveryAgentRequest ignored -> AgentType.DISCOVERY_AGENT;
            case AgentModels.DiscoveryAgentRequests ignored -> AgentType.DISCOVERY_AGENT_DISPATCH;
            case AgentModels.DiscoveryCollectorRequest ignored -> AgentType.DISCOVERY_COLLECTOR;
            case AgentModels.PlanningOrchestratorRequest ignored -> AgentType.PLANNING_ORCHESTRATOR;
            case AgentModels.PlanningAgentRequest ignored -> AgentType.PLANNING_AGENT;
            case AgentModels.PlanningAgentRequests ignored -> AgentType.PLANNING_AGENT_DISPATCH;
            case AgentModels.PlanningCollectorRequest ignored -> AgentType.PLANNING_COLLECTOR;
            case AgentModels.TicketOrchestratorRequest ignored -> AgentType.TICKET_ORCHESTRATOR;
            case AgentModels.TicketAgentRequest ignored -> AgentType.TICKET_AGENT;
            case AgentModels.TicketAgentRequests ignored -> AgentType.TICKET_AGENT_DISPATCH;
            case AgentModels.TicketCollectorRequest ignored -> AgentType.TICKET_COLLECTOR;
            case AgentModels.ResultsRequest ignored -> null;
            case InterruptRequest ignored -> null;
            case AgentModels.AgentToAgentRequest r -> r.sourceAgentType();
            case AgentModels.AgentToControllerRequest r -> r.sourceAgentType();
            case AgentModels.ControllerToAgentRequest r -> r.targetAgentType();
        };
    }

    /**
     * Resolve the AgentType for a GraphNode subtype. Returns null for node types
     * that don't map to an LLM-calling agent (e.g., InterruptNode, ReviewNode).
     */
    static @Nullable AgentType agentTypeFromNode(GraphNode node) {
        return switch (node) {
            case OrchestratorNode ignored -> AgentType.ORCHESTRATOR;
            case CollectorNode ignored -> AgentType.ORCHESTRATOR_COLLECTOR;
            case DiscoveryOrchestratorNode ignored -> AgentType.DISCOVERY_ORCHESTRATOR;
            case DiscoveryDispatchAgentNode ignored -> AgentType.DISCOVERY_AGENT_DISPATCH;
            case DiscoveryNode ignored -> AgentType.DISCOVERY_AGENT;
            case DiscoveryCollectorNode ignored -> AgentType.DISCOVERY_COLLECTOR;
            case PlanningOrchestratorNode ignored -> AgentType.PLANNING_ORCHESTRATOR;
            case PlanningDispatchAgentNode ignored -> AgentType.PLANNING_AGENT_DISPATCH;
            case PlanningNode ignored -> AgentType.PLANNING_AGENT;
            case PlanningCollectorNode ignored -> AgentType.PLANNING_COLLECTOR;
            case TicketOrchestratorNode ignored -> AgentType.TICKET_ORCHESTRATOR;
            case TicketDispatchAgentNode ignored -> AgentType.TICKET_AGENT_DISPATCH;
            case TicketNode ignored -> AgentType.TICKET_AGENT;
            case TicketCollectorNode ignored -> AgentType.TICKET_COLLECTOR;
            case InterruptNode ignored -> null;
            case ReviewNode ignored -> null;
            case MergeNode ignored -> null;
            case SummaryNode ignored -> null;
            case AskPermissionNode ignored -> null;
            case AgentToAgentConversationNode n -> n.sourceAgentType();
            case AgentToControllerConversationNode n -> n.sourceAgentType();
            case ControllerToAgentConversationNode n -> n.targetAgentType();
            case DataLayerOperationNode ignored -> null;
        };
    }

    // -----------------------------------------------------------
    // Initialization
    // -----------------------------------------------------------

    private static List<NodeMapping> initAllMappings() {
        List<NodeMapping> m = new ArrayList<>();

        // Orchestrator level
        m.add(new NodeMapping(AgentModels.OrchestratorRequest.class, "Orchestrator", "orchestratorRequest"));
        m.add(new NodeMapping(AgentModels.OrchestratorCollectorRequest.class, "Orchestrator Collector", "orchestratorCollectorRequest"));

        // Discovery phase
        m.add(new NodeMapping(AgentModels.DiscoveryOrchestratorRequest.class, "Discovery Orchestrator", "discoveryOrchestratorRequest"));
        m.add(new NodeMapping(AgentModels.DiscoveryAgentRequests.class, "Discovery Agent Dispatch", "discoveryAgentRequests"));
        m.add(new NodeMapping(AgentModels.DiscoveryAgentRequest.class, "Discovery Agents", "discoveryAgentRequest"));
        m.add(new NodeMapping(AgentModels.DiscoveryCollectorRequest.class, "Discovery Collector", "discoveryCollectorRequest"));
        m.add(new NodeMapping(AgentModels.DiscoveryAgentResults.class, "Discovery Agent Results Dispatch", "discoveryAgentResults"));

        // Planning phase
        m.add(new NodeMapping(AgentModels.PlanningOrchestratorRequest.class, "Planning Orchestrator", "planningOrchestratorRequest"));
        m.add(new NodeMapping(AgentModels.PlanningAgentRequests.class, "Planning Agent Dispatch", "planningAgentRequests"));
        m.add(new NodeMapping(AgentModels.PlanningAgentRequest.class, "Planning Agents", "planningAgentRequest"));
        m.add(new NodeMapping(AgentModels.PlanningCollectorRequest.class, "Planning Collector", "planningCollectorRequest"));
        m.add(new NodeMapping(AgentModels.PlanningAgentResults.class, "Planning Agent Results Dispatch", "planningAgentResults"));

        // Ticket phase
        m.add(new NodeMapping(AgentModels.TicketOrchestratorRequest.class, "Ticket Orchestrator", "ticketOrchestratorRequest"));
        m.add(new NodeMapping(AgentModels.TicketAgentRequests.class, "Ticket Agent Dispatch", "ticketAgentRequests"));
        m.add(new NodeMapping(AgentModels.TicketAgentRequest.class, "Ticket Agents", "ticketAgentRequest"));
        m.add(new NodeMapping(AgentModels.TicketCollectorRequest.class, "Ticket Collector", "ticketCollectorRequest"));
        m.add(new NodeMapping(AgentModels.TicketAgentResults.class, "Ticket Agent Results Dispatch", "ticketAgentResults"));

        // Context Manager
        m.add(new NodeMapping(AgentModels.ContextManagerRequest.class, "Context Manager", null));
        m.add(new NodeMapping(AgentModels.ContextManagerRoutingRequest.class, "Context Manager Routing Request", "contextOrchestratorRequest"));
        m.add(new NodeMapping(AgentModels.ResultsRequest.class, "Results Request", null));

        // Interrupt types
        m.add(new NodeMapping(InterruptRequest.class, "Interrupt Request", null));
        m.add(new NodeMapping(InterruptRequest.OrchestratorInterruptRequest.class, "Orchestrator Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.OrchestratorCollectorInterruptRequest.class, "Orchestrator Collector Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.DiscoveryOrchestratorInterruptRequest.class, "Discovery Orchestrator Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.DiscoveryAgentInterruptRequest.class, "Discovery Agent Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.DiscoveryCollectorInterruptRequest.class, "Discovery Collector Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.DiscoveryAgentDispatchInterruptRequest.class, "Discovery Agent Dispatch Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.PlanningOrchestratorInterruptRequest.class, "Planning Orchestrator Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.PlanningAgentInterruptRequest.class, "Planning Agent Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.PlanningCollectorInterruptRequest.class, "Planning Collector Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.PlanningAgentDispatchInterruptRequest.class, "Planning Agent Dispatch Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.TicketOrchestratorInterruptRequest.class, "Ticket Orchestrator Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.TicketAgentInterruptRequest.class, "Ticket Agent Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.TicketCollectorInterruptRequest.class, "Ticket Collector Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.TicketAgentDispatchInterruptRequest.class, "Ticket Agent Dispatch Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.ContextManagerInterruptRequest.class, "Context Manager Interrupt", null));
        m.add(new NodeMapping(InterruptRequest.QuestionAnswerInterruptRequest.class, "Question Answer Interrupt", null));

        return Collections.unmodifiableList(m);
    }

    private static Map<Class<?>, String> initDisplayNames() {
        Map<Class<?>, String> map = new HashMap<>();
        for (NodeMapping m : ALL_MAPPINGS) {
            map.put(m.requestType(), m.displayName());
        }
        return Collections.unmodifiableMap(map);
    }

    private static Map<Class<?>, String> initFieldNames() {
        Map<Class<?>, String> map = new HashMap<>();
        for (NodeMapping m : ALL_MAPPINGS) {
            if (m.fieldName() != null) {
                map.put(m.requestType(), m.fieldName());
            }
        }
        return Collections.unmodifiableMap(map);
    }

    private static Map<AgentType, Class<? extends AgentModels.AgentRouting>> initAgentTypeToRouting() {
        Map<AgentType, Class<? extends AgentModels.AgentRouting>> map = new LinkedHashMap<>();
        map.put(AgentType.ORCHESTRATOR, AgentModels.OrchestratorRouting.class);
        map.put(AgentType.ORCHESTRATOR_COLLECTOR, AgentModels.OrchestratorCollectorRouting.class);
        map.put(AgentType.DISCOVERY_ORCHESTRATOR, AgentModels.DiscoveryOrchestratorRouting.class);
        map.put(AgentType.DISCOVERY_AGENT_DISPATCH, AgentModels.DiscoveryAgentDispatchRouting.class);
        map.put(AgentType.DISCOVERY_AGENT, AgentModels.DiscoveryAgentRouting.class);
        map.put(AgentType.DISCOVERY_COLLECTOR, AgentModels.DiscoveryCollectorRouting.class);
        map.put(AgentType.PLANNING_ORCHESTRATOR, AgentModels.PlanningOrchestratorRouting.class);
        map.put(AgentType.PLANNING_AGENT_DISPATCH, AgentModels.PlanningAgentDispatchRouting.class);
        map.put(AgentType.PLANNING_AGENT, AgentModels.PlanningAgentRouting.class);
        map.put(AgentType.PLANNING_COLLECTOR, AgentModels.PlanningCollectorRouting.class);
        map.put(AgentType.TICKET_ORCHESTRATOR, AgentModels.TicketOrchestratorRouting.class);
        map.put(AgentType.TICKET_AGENT_DISPATCH, AgentModels.TicketAgentDispatchRouting.class);
        map.put(AgentType.TICKET_AGENT, AgentModels.TicketAgentRouting.class);
        map.put(AgentType.TICKET_COLLECTOR, AgentModels.TicketCollectorRouting.class);
        map.put(AgentType.CONTEXT_MANAGER, AgentModels.ContextManagerResultRouting.class);
        return Collections.unmodifiableMap(map);
    }
}
