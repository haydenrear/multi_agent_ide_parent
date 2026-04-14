package com.hayden.multiagentide.filter.service;

import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.multiagentide.agent.AgentInterfaces;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.agent.AgentType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Single source of truth for filter layer IDs and the agent/action metadata that resolves to them.
 */
public final class FilterLayerCatalog {

    public static final String METADATA_LAYER_ID = "layerId";

    public static final String CONTROLLER = "controller";
    public static final String CONTROLLER_UI_EVENT_POLL = "controller-ui-event-poll";
    public static final String WORKFLOW_AGENT = "workflow-agent";
    public static final String DISCOVERY_DISPATCH_SUBAGENT = "discovery-dispatch-subagent";
    public static final String PLANNING_DISPATCH_SUBAGENT = "planning-dispatch-subagent";
    public static final String TICKET_DISPATCH_SUBAGENT = "ticket-dispatch-subagent";
    public static final String INTERRUPT_SERVICE = "interrupt-service";
    public static final String WORKTREE_AUTO_COMMIT = "worktree-auto-commit";
    public static final String WORKTREE_MERGE_CONFLICT = "worktree-merge-conflict";
    public static final String AI_FILTER = "ai-filter";
    public static final String AI_PROPAGATOR = "ai-propagator";
    public static final String AI_TRANSFORMER = "ai-transformer";
    public static final String PROMPT_HEALTH_CHECK = "prompt-health-check";

    public record LayerDefinition(
            String layerId,
            FilterEnums.LayerType layerType,
            String layerKey,
            String parentLayerId,
            int depth
    ) {}

    public record ActionDefinition(
            String agentName,
            String actionName,
            String methodName,
            AgentType agentType,
            String parentLayerId,
            String layerId,
            Set<Class<?>> requestTypes
    ) {}

    private static final List<LayerDefinition> ROOT_LAYERS = List.of(
            layer(CONTROLLER, FilterEnums.LayerType.CONTROLLER, CONTROLLER, null, 0),
            layer(CONTROLLER_UI_EVENT_POLL, FilterEnums.LayerType.CONTROLLER_UI_EVENT_POLL, CONTROLLER_UI_EVENT_POLL, CONTROLLER, 1),
            layer(WORKFLOW_AGENT, FilterEnums.LayerType.WORKFLOW_AGENT, WORKFLOW_AGENT, CONTROLLER, 1),
            layer(DISCOVERY_DISPATCH_SUBAGENT, FilterEnums.LayerType.WORKFLOW_AGENT, DISCOVERY_DISPATCH_SUBAGENT, WORKFLOW_AGENT, 2),
            layer(PLANNING_DISPATCH_SUBAGENT, FilterEnums.LayerType.WORKFLOW_AGENT, PLANNING_DISPATCH_SUBAGENT, WORKFLOW_AGENT, 2),
            layer(TICKET_DISPATCH_SUBAGENT, FilterEnums.LayerType.WORKFLOW_AGENT, TICKET_DISPATCH_SUBAGENT, WORKFLOW_AGENT, 2),
            layer(INTERRUPT_SERVICE, FilterEnums.LayerType.WORKFLOW_AGENT, INTERRUPT_SERVICE, CONTROLLER, 1),
            layer(WORKTREE_AUTO_COMMIT, FilterEnums.LayerType.WORKFLOW_AGENT, WORKTREE_AUTO_COMMIT, CONTROLLER, 1),
            layer(WORKTREE_MERGE_CONFLICT, FilterEnums.LayerType.WORKFLOW_AGENT, WORKTREE_MERGE_CONFLICT, CONTROLLER, 1),
            layer(AI_FILTER, FilterEnums.LayerType.WORKFLOW_AGENT, AI_FILTER, CONTROLLER, 1),
            layer(AI_PROPAGATOR, FilterEnums.LayerType.WORKFLOW_AGENT, AI_PROPAGATOR, CONTROLLER, 1),
            layer(AI_TRANSFORMER, FilterEnums.LayerType.WORKFLOW_AGENT, AI_TRANSFORMER, CONTROLLER, 1),
            layer(PROMPT_HEALTH_CHECK, FilterEnums.LayerType.WORKFLOW_AGENT, PROMPT_HEALTH_CHECK, CONTROLLER, 1)
    );

    private static final Map<String, LayerDefinition> ROOT_LAYER_BY_ID = new LinkedHashMap<>();
    private static final List<ActionDefinition> ACTIONS = new ArrayList<>();
    private static final Map<String, ActionDefinition> ACTION_BY_AGENT_AND_METHOD = new LinkedHashMap<>();
    private static final Map<String, ActionDefinition> ACTION_BY_AGENT_AND_ACTION = new LinkedHashMap<>();
    private static final Map<Class<?>, ActionDefinition> ACTION_BY_REQUEST_TYPE = new LinkedHashMap<>();
    private static final Map<AgentType, String> ROOT_LAYER_BY_AGENT_TYPE = new LinkedHashMap<>();
    private static final Set<String> INTERNAL_AUTOMATION_ROOTS = Set.of(
            AI_FILTER,
            AI_PROPAGATOR,
            AI_TRANSFORMER
    );

    static {
        ROOT_LAYERS.forEach(layer -> ROOT_LAYER_BY_ID.put(layer.layerId(), layer));

        registerAction(action(
                AgentInterfaces.WORKFLOW_AGENT_NAME,
                AgentInterfaces.ACTION_CONTEXT_MANAGER_ROUTE,
                AgentInterfaces.METHOD_ROUTE_TO_CONTEXT_MANAGER,
                AgentType.CONTEXT_MANAGER,
                WORKFLOW_AGENT,
                AgentModels.ContextManagerRoutingRequest.class
        ));
        registerAction(action(
                AgentInterfaces.WORKFLOW_AGENT_NAME,
                AgentInterfaces.ACTION_CONTEXT_MANAGER,
                AgentInterfaces.METHOD_CONTEXT_MANAGER,
                AgentType.CONTEXT_MANAGER,
                WORKFLOW_AGENT,
                AgentModels.ContextManagerRequest.class
        ));
        registerAction(action(
                AgentInterfaces.WORKFLOW_AGENT_NAME,
                AgentInterfaces.ACTION_ORCHESTRATOR,
                AgentInterfaces.METHOD_COORDINATE_WORKFLOW,
                AgentType.ORCHESTRATOR,
                WORKFLOW_AGENT,
                AgentModels.OrchestratorRequest.class
        ));
        registerAction(action(
                AgentInterfaces.WORKFLOW_AGENT_NAME,
                AgentInterfaces.ACTION_UNIFIED_INTERRUPT,
                AgentInterfaces.METHOD_HANDLE_UNIFIED_INTERRUPT,
                AgentType.ORCHESTRATOR,
                WORKFLOW_AGENT,
                AgentModels.InterruptRequest.OrchestratorInterruptRequest.class,
                AgentModels.InterruptRequest.OrchestratorCollectorInterruptRequest.class,
                AgentModels.InterruptRequest.DiscoveryOrchestratorInterruptRequest.class,
                AgentModels.InterruptRequest.DiscoveryAgentDispatchInterruptRequest.class,
                AgentModels.InterruptRequest.DiscoveryCollectorInterruptRequest.class,
                AgentModels.InterruptRequest.PlanningOrchestratorInterruptRequest.class,
                AgentModels.InterruptRequest.PlanningAgentDispatchInterruptRequest.class,
                AgentModels.InterruptRequest.PlanningCollectorInterruptRequest.class,
                AgentModels.InterruptRequest.TicketOrchestratorInterruptRequest.class,
                AgentModels.InterruptRequest.TicketAgentDispatchInterruptRequest.class,
                AgentModels.InterruptRequest.TicketCollectorInterruptRequest.class,
                AgentModels.InterruptRequest.ContextManagerInterruptRequest.class,
                AgentModels.InterruptRequest.QuestionAnswerInterruptRequest.class
        ));
        registerAction(action(
                AgentInterfaces.WORKFLOW_AGENT_NAME,
                AgentInterfaces.ACTION_ORCHESTRATOR_COLLECTOR,
                AgentInterfaces.METHOD_FINAL_COLLECTOR_RESULT,
                AgentType.ORCHESTRATOR_COLLECTOR,
                WORKFLOW_AGENT
        ));
        registerAction(action(
                AgentInterfaces.WORKFLOW_AGENT_NAME,
                AgentInterfaces.ACTION_ORCHESTRATOR_COLLECTOR,
                AgentInterfaces.METHOD_CONSOLIDATE_WORKFLOW_OUTPUTS,
                AgentType.ORCHESTRATOR_COLLECTOR,
                WORKFLOW_AGENT,
                AgentModels.OrchestratorCollectorRequest.class
        ));
        registerAction(action(
                AgentInterfaces.WORKFLOW_AGENT_NAME,
                AgentInterfaces.ACTION_DISCOVERY_COLLECTOR,
                AgentInterfaces.METHOD_CONSOLIDATE_DISCOVERY_FINDINGS,
                AgentType.DISCOVERY_COLLECTOR,
                WORKFLOW_AGENT,
                AgentModels.DiscoveryCollectorRequest.class
        ));
        registerAction(action(
                AgentInterfaces.WORKFLOW_AGENT_NAME,
                AgentInterfaces.ACTION_PLANNING_COLLECTOR,
                AgentInterfaces.METHOD_CONSOLIDATE_PLANS_INTO_TICKETS,
                AgentType.PLANNING_COLLECTOR,
                WORKFLOW_AGENT,
                AgentModels.PlanningCollectorRequest.class
        ));
        registerAction(action(
                AgentInterfaces.WORKFLOW_AGENT_NAME,
                AgentInterfaces.ACTION_TICKET_COLLECTOR,
                AgentInterfaces.METHOD_CONSOLIDATE_TICKET_RESULTS,
                AgentType.TICKET_COLLECTOR,
                WORKFLOW_AGENT,
                AgentModels.TicketCollectorRequest.class
        ));
        registerAction(action(
                AgentInterfaces.WORKFLOW_AGENT_NAME,
                AgentInterfaces.ACTION_DISCOVERY_ORCHESTRATOR,
                AgentInterfaces.METHOD_KICK_OFF_ANY_NUMBER_OF_AGENTS_FOR_CODE_SEARCH,
                AgentType.DISCOVERY_ORCHESTRATOR,
                WORKFLOW_AGENT,
                AgentModels.DiscoveryOrchestratorRequest.class
        ));
        registerAction(action(
                AgentInterfaces.WORKFLOW_AGENT_NAME,
                AgentInterfaces.ACTION_DISCOVERY_DISPATCH,
                AgentInterfaces.METHOD_DISPATCH_DISCOVERY_AGENT_REQUESTS,
                AgentType.DISCOVERY_AGENT_DISPATCH,
                WORKFLOW_AGENT,
                AgentModels.DiscoveryAgentRequests.class
        ));
        registerAction(action(
                AgentInterfaces.WORKFLOW_AGENT_NAME,
                AgentInterfaces.ACTION_PLANNING_ORCHESTRATOR,
                AgentInterfaces.METHOD_DECOMPOSE_PLAN_AND_CREATE_WORK_ITEMS,
                AgentType.PLANNING_ORCHESTRATOR,
                WORKFLOW_AGENT,
                AgentModels.PlanningOrchestratorRequest.class
        ));
        registerAction(action(
                AgentInterfaces.WORKFLOW_AGENT_NAME,
                AgentInterfaces.ACTION_PLANNING_DISPATCH,
                AgentInterfaces.METHOD_DISPATCH_PLANNING_AGENT_REQUESTS,
                AgentType.PLANNING_AGENT_DISPATCH,
                WORKFLOW_AGENT,
                AgentModels.PlanningAgentRequests.class
        ));
        registerAction(action(
                AgentInterfaces.WORKFLOW_AGENT_NAME,
                AgentInterfaces.ACTION_TICKET_ORCHESTRATOR,
                AgentInterfaces.METHOD_FINALIZE_TICKET_ORCHESTRATOR,
                AgentType.TICKET_ORCHESTRATOR,
                WORKFLOW_AGENT
        ));
        registerAction(action(
                AgentInterfaces.WORKFLOW_AGENT_NAME,
                AgentInterfaces.ACTION_TICKET_ORCHESTRATOR,
                AgentInterfaces.METHOD_ORCHESTRATE_TICKET_EXECUTION,
                AgentType.TICKET_ORCHESTRATOR,
                WORKFLOW_AGENT,
                AgentModels.TicketOrchestratorRequest.class
        ));
        registerAction(action(
                AgentInterfaces.WORKFLOW_AGENT_NAME,
                AgentInterfaces.ACTION_TICKET_DISPATCH,
                AgentInterfaces.METHOD_DISPATCH_TICKET_AGENT_REQUESTS,
                AgentType.TICKET_AGENT_DISPATCH,
                WORKFLOW_AGENT,
                AgentModels.TicketAgentRequests.class
        ));
        registerAction(action(
                AgentInterfaces.WORKFLOW_AGENT_NAME,
                AgentInterfaces.ACTION_TICKET_COLLECTOR_ROUTING_BRANCH,
                AgentInterfaces.METHOD_HANDLE_TICKET_COLLECTOR_BRANCH,
                AgentType.TICKET_COLLECTOR,
                WORKFLOW_AGENT
        ));
        registerAction(action(
                AgentInterfaces.WORKFLOW_AGENT_NAME,
                AgentInterfaces.ACTION_DISCOVERY_COLLECTOR,
                AgentInterfaces.METHOD_HANDLE_DISCOVERY_COLLECTOR_BRANCH,
                AgentType.DISCOVERY_COLLECTOR,
                WORKFLOW_AGENT
        ));
        registerAction(action(
                AgentInterfaces.WORKFLOW_AGENT_NAME,
                AgentInterfaces.ACTION_ORCHESTRATOR_COLLECTOR,
                AgentInterfaces.METHOD_HANDLE_ORCHESTRATOR_COLLECTOR_BRANCH,
                AgentType.ORCHESTRATOR_COLLECTOR,
                WORKFLOW_AGENT
        ));
        registerAction(action(
                AgentInterfaces.WORKFLOW_AGENT_NAME,
                AgentInterfaces.ACTION_PLANNING_COLLECTOR,
                AgentInterfaces.METHOD_HANDLE_PLANNING_COLLECTOR_BRANCH,
                AgentType.PLANNING_COLLECTOR,
                WORKFLOW_AGENT
        ));

        registerAction(action(
                AgentInterfaces.WORKFLOW_DISCOVERY_DISPATCH_SUBAGENT,
                AgentInterfaces.ACTION_DISCOVERY_AGENT_INTERRUPT,
                AgentInterfaces.METHOD_TRANSITION_TO_INTERRUPT_STATE,
                AgentType.DISCOVERY_AGENT,
                DISCOVERY_DISPATCH_SUBAGENT,
                AgentModels.InterruptRequest.DiscoveryAgentInterruptRequest.class
        ));
        registerAction(action(
                AgentInterfaces.WORKFLOW_DISCOVERY_DISPATCH_SUBAGENT,
                AgentInterfaces.ACTION_DISCOVERY_AGENT,
                AgentInterfaces.METHOD_RUN_DISCOVERY_AGENT,
                AgentType.DISCOVERY_AGENT,
                DISCOVERY_DISPATCH_SUBAGENT,
                AgentModels.DiscoveryAgentRequest.class
        ));
        registerAction(action(
                AgentInterfaces.WORKFLOW_PLANNING_DISPATCH_SUBAGENT,
                AgentInterfaces.ACTION_PLANNING_AGENT_INTERRUPT,
                AgentInterfaces.METHOD_TRANSITION_TO_INTERRUPT_STATE,
                AgentType.PLANNING_AGENT,
                PLANNING_DISPATCH_SUBAGENT,
                AgentModels.InterruptRequest.PlanningAgentInterruptRequest.class
        ));
        registerAction(action(
                AgentInterfaces.WORKFLOW_PLANNING_DISPATCH_SUBAGENT,
                AgentInterfaces.ACTION_PLANNING_AGENT,
                AgentInterfaces.METHOD_RUN_PLANNING_AGENT,
                AgentType.PLANNING_AGENT,
                PLANNING_DISPATCH_SUBAGENT,
                AgentModels.PlanningAgentRequest.class
        ));
        registerAction(action(
                AgentInterfaces.WORKFLOW_TICKET_DISPATCH_SUBAGENT,
                AgentInterfaces.ACTION_TICKET_AGENT_INTERRUPT,
                AgentInterfaces.METHOD_TRANSITION_TO_INTERRUPT_STATE,
                AgentType.TICKET_AGENT,
                TICKET_DISPATCH_SUBAGENT,
                AgentModels.InterruptRequest.TicketAgentInterruptRequest.class
        ));
        registerAction(action(
                AgentInterfaces.WORKFLOW_TICKET_DISPATCH_SUBAGENT,
                AgentInterfaces.ACTION_TICKET_AGENT,
                AgentInterfaces.METHOD_RUN_TICKET_AGENT,
                AgentType.TICKET_AGENT,
                TICKET_DISPATCH_SUBAGENT,
                AgentModels.TicketAgentRequest.class
        ));

        registerAction(action(
                INTERRUPT_SERVICE,
                "agent-review",
                "runInterruptAgentReview",
                AgentType.REVIEW_RESOLUTION_AGENT,
                INTERRUPT_SERVICE
        ));
        registerAction(action(
                WORKTREE_AUTO_COMMIT,
                "commit-agent",
                "runCommitAgent",
                AgentType.COMMIT_AGENT,
                WORKTREE_AUTO_COMMIT,
                AgentModels.CommitAgentRequest.class
        ));
        registerAction(action(
                WORKTREE_MERGE_CONFLICT,
                "merge-conflict-agent",
                "runMergeConflictAgent",
                AgentType.MERGE_CONFLICT_AGENT,
                WORKTREE_MERGE_CONFLICT,
                AgentModels.MergeConflictRequest.class
        ));
        registerAction(action(
                AI_FILTER,
                "path-filter",
                "runAiFilter",
                AgentType.AI_FILTER,
                AI_FILTER,
                AgentModels.AiFilterRequest.class
        ));
        registerAction(action(
                AI_PROPAGATOR,
                "propagate-action",
                "runAiPropagator",
                AgentType.AI_PROPAGATOR,
                AI_PROPAGATOR,
                AgentModels.AiPropagatorRequest.class
        ));
        registerAction(action(
                AI_TRANSFORMER,
                "transform-controller-response",
                "runAiTransformer",
                AgentType.AI_TRANSFORMER,
                AI_TRANSFORMER,
                AgentModels.AiTransformerRequest.class
        ));
        registerAction(action(
                PROMPT_HEALTH_CHECK,
                "prompt-health-check",
                "checkPromptHealth",
                AgentType.AI_PROPAGATOR,
                PROMPT_HEALTH_CHECK
        ));

        mapRoot(AgentType.ORCHESTRATOR, WORKFLOW_AGENT);
        mapRoot(AgentType.ORCHESTRATOR_COLLECTOR, WORKFLOW_AGENT);
        mapRoot(AgentType.DISCOVERY_ORCHESTRATOR, WORKFLOW_AGENT);
        mapRoot(AgentType.DISCOVERY_AGENT_DISPATCH, WORKFLOW_AGENT);
        mapRoot(AgentType.DISCOVERY_COLLECTOR, WORKFLOW_AGENT);
        mapRoot(AgentType.PLANNING_ORCHESTRATOR, WORKFLOW_AGENT);
        mapRoot(AgentType.PLANNING_AGENT_DISPATCH, WORKFLOW_AGENT);
        mapRoot(AgentType.PLANNING_COLLECTOR, WORKFLOW_AGENT);
        mapRoot(AgentType.TICKET_ORCHESTRATOR, WORKFLOW_AGENT);
        mapRoot(AgentType.TICKET_AGENT_DISPATCH, WORKFLOW_AGENT);
        mapRoot(AgentType.TICKET_COLLECTOR, WORKFLOW_AGENT);
        mapRoot(AgentType.REVIEW_AGENT, WORKFLOW_AGENT);
        mapRoot(AgentType.MERGER_AGENT, WORKFLOW_AGENT);
        mapRoot(AgentType.CONTEXT_MANAGER, WORKFLOW_AGENT);
        mapRoot(AgentType.DISCOVERY_AGENT, DISCOVERY_DISPATCH_SUBAGENT);
        mapRoot(AgentType.PLANNING_AGENT, PLANNING_DISPATCH_SUBAGENT);
        mapRoot(AgentType.TICKET_AGENT, TICKET_DISPATCH_SUBAGENT);
        mapRoot(AgentType.REVIEW_RESOLUTION_AGENT, INTERRUPT_SERVICE);
        mapRoot(AgentType.COMMIT_AGENT, WORKTREE_AUTO_COMMIT);
        mapRoot(AgentType.MERGE_CONFLICT_AGENT, WORKTREE_MERGE_CONFLICT);
        mapRoot(AgentType.AI_FILTER, AI_FILTER);
        mapRoot(AgentType.AI_PROPAGATOR, AI_PROPAGATOR);
        mapRoot(AgentType.AI_TRANSFORMER, AI_TRANSFORMER);
    }

    private FilterLayerCatalog() {}

    public static List<ActionDefinition> actionDefinitions() {
        return List.copyOf(ACTIONS);
    }

    public static List<ActionDefinition> userAttachableActionDefinitions() {
        return ACTIONS.stream()
                .filter(action -> !isInternalAutomationAction(action))
                .toList();
    }

    public static List<LayerDefinition> layerDefinitions() {
        List<LayerDefinition> definitions = new ArrayList<>(ROOT_LAYERS);
        for (ActionDefinition action : ACTIONS) {
            LayerDefinition parent = ROOT_LAYER_BY_ID.get(action.parentLayerId());
            definitions.add(layer(
                    action.layerId(),
                    FilterEnums.LayerType.WORKFLOW_AGENT_ACTION,
                    action.methodName(),
                    action.parentLayerId(),
                    parent == null ? 1 : parent.depth() + 1
            ));
        }
        return List.copyOf(definitions);
    }

    public static Optional<String> resolveActionLayer(String agentName, String actionName, String methodName) {
        String key = key(agentName, methodName);
        ActionDefinition byMethod = ACTION_BY_AGENT_AND_METHOD.get(key);
        if (byMethod != null) {
            return Optional.of(byMethod.layerId());
        }
        ActionDefinition byAction = ACTION_BY_AGENT_AND_ACTION.get(key(agentName, actionName));
        if (byAction != null) {
            return Optional.of(byAction.layerId());
        }
        return Optional.empty();
    }

    public static String canonicalActionName(String agentName, String actionName, String methodName) {
        ActionDefinition byMethod = ACTION_BY_AGENT_AND_METHOD.get(key(agentName, methodName));
        if (byMethod != null) {
            return byMethod.methodName();
        }
        ActionDefinition byAction = ACTION_BY_AGENT_AND_ACTION.get(key(agentName, actionName));
        if (byAction != null) {
            return byAction.methodName();
        }
        if (methodName != null && !methodName.isBlank()) {
            return methodName.trim();
        }
        return actionName == null ? "" : actionName.trim();
    }

    public static Optional<String> resolveActionLayer(AgentModels.AgentRequest request, AgentType agentType) {
        if (request != null) {
            ActionDefinition action = ACTION_BY_REQUEST_TYPE.get(request.getClass());
            if (action != null) {
                return Optional.of(action.layerId());
            }
        }
        return resolveRootLayer(agentType);
    }

    public static Optional<String> resolveRootLayer(AgentType agentType) {
        if (agentType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(ROOT_LAYER_BY_AGENT_TYPE.get(agentType));
    }

    public static boolean isInternalAutomationLayer(String layerId) {
        if (layerId == null || layerId.isBlank()) {
            return false;
        }
        return INTERNAL_AUTOMATION_ROOTS.stream()
                .anyMatch(root -> layerId.equals(root) || layerId.startsWith(root + "/"));
    }

    public static boolean isInternalAutomationAction(String agentName, String actionName, String methodName) {
        return resolveAction(agentName, actionName, methodName)
                .map(FilterLayerCatalog::isInternalAutomationAction)
                .orElse(false);
    }

    public static boolean isInternalAutomationAction(ActionDefinition actionDefinition) {
        return actionDefinition != null
                && (isInternalAutomationLayer(actionDefinition.parentLayerId())
                || isInternalAutomationLayer(actionDefinition.layerId()));
    }

    private static void registerAction(ActionDefinition action) {
        ACTIONS.add(action);
        ACTION_BY_AGENT_AND_METHOD.put(key(action.agentName(), action.methodName()), action);
        ACTION_BY_AGENT_AND_ACTION.put(key(action.agentName(), action.actionName()), action);
        action.requestTypes().forEach(requestType -> ACTION_BY_REQUEST_TYPE.put(requestType, action));
    }

    private static void mapRoot(AgentType agentType, String layerId) {
        ROOT_LAYER_BY_AGENT_TYPE.put(agentType, layerId);
    }

    private static ActionDefinition action(
            String agentName,
            String actionName,
            String methodName,
            AgentType agentType,
            String parentLayerId,
            Class<?>... requestTypes
    ) {
        Set<Class<?>> mappedRequestTypes = new LinkedHashSet<>();
        if (requestTypes != null) {
            for (Class<?> requestType : requestTypes) {
                if (requestType != null) {
                    mappedRequestTypes.add(requestType);
                }
            }
        }
        return new ActionDefinition(
                agentName,
                actionName,
                methodName,
                agentType,
                parentLayerId,
                parentLayerId + "/" + methodName,
                Set.copyOf(mappedRequestTypes)
        );
    }

    private static LayerDefinition layer(
            String layerId,
            FilterEnums.LayerType layerType,
            String layerKey,
            String parentLayerId,
            int depth
    ) {
        return new LayerDefinition(layerId, layerType, layerKey, parentLayerId, depth);
    }

    private static String key(String agentName, String value) {
        return normalize(agentName) + "::" + normalize(value);
    }

    private static Optional<ActionDefinition> resolveAction(String agentName, String actionName, String methodName) {
        ActionDefinition byMethod = ACTION_BY_AGENT_AND_METHOD.get(key(agentName, methodName));
        if (byMethod != null) {
            return Optional.of(byMethod);
        }
        return Optional.ofNullable(ACTION_BY_AGENT_AND_ACTION.get(key(agentName, actionName)));
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static void putIfPresent(Map<String, Object> metadata, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        metadata.put(key, value);
    }
}
