package com.hayden.multiagentide.service;

import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.core.Blackboard;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.agent.AgentType;
import com.hayden.multiagentide.agent.BlackboardHistory;
import com.hayden.multiagentide.prompt.ContextIdService;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for enriching request objects with ArtifactKey.
 * Centralizes the logic for setting contextId fields consistently across all agent types.
 */
@Slf4j
@Service
public class RequestEnrichment {

    private final ContextIdService contextIdService;

    public RequestEnrichment(ContextIdService contextIdService) {
        this.contextIdService = contextIdService;
    }

    public <T extends Artifact.AgentModel> T enrich(T input, OperationContext context) {
        if (input == null) {
            return null;
        }

        if (input.key() != null)
            log.error("Found input without key.");

        Artifact.AgentModel parent = findParentForInput(input, context);
        T enrich = enrich(input, context, parent);
        return enrich;
    }

    public <T> T enrich(T input, OperationContext context, Artifact.AgentModel parent) {

        if (input instanceof AgentModels.AgentRequest a) {
            return (T) enrichAgentRequests(a, context, parent);
        }
        if (input instanceof AgentModels.AgentResult r) {
            return (T) enrichAgentResult(r, context, parent);
        }
        if (input instanceof AgentModels.Routing model) {
            return (T) enrichRouting(model, context, parent);
        }
        if (input instanceof Artifact.AgentModel model) {
            return (T) enrichAgentModel(model, context, parent);
        }

        return input;
    }

    private Artifact.AgentModel findParentForInput(Artifact.AgentModel input, Blackboard history) {
        if (input == null || history == null) {
            return null;
        }

        return switch (input) {
            case AgentModels.AgentRequest req ->
                    findParentForAgentRequests(history, req);
            case AgentModels.AgentResult res ->
                    findParentForAgentResultTypes(history, res);
            default -> {
                log.error("Found unknown agent model input {} in request enrichment. Returning it without enriching.", input.getClass().getSimpleName());
                yield input;
            }
        };
    }

    private Artifact.AgentModel findParentForAgentResultTypes(Blackboard history, AgentModels.AgentResult res) {
        return switch (res) {
            case AgentModels.CommitAgentResult ignored ->
                    findLastFromHistory(history, AgentModels.CommitAgentRequest.class);
            case AgentModels.MergeConflictResult ignored ->
                    findLastFromHistory(history, AgentModels.MergeConflictRequest.class);
            case AgentModels.MergerAgentResult ignored ->
                    findLastFromHistory(history, AgentModels.AgentRequest.class);
            case AgentModels.DiscoveryAgentResult ignored ->
                    findLastFromHistory(history, AgentModels.DiscoveryAgentRequest.class);
            case AgentModels.DiscoveryCollectorResult ignored ->
                    findLastFromHistory(history, AgentModels.DiscoveryCollectorRequest.class);
            case AgentModels.DiscoveryOrchestratorResult ignored ->
                    findLastFromHistory(history, AgentModels.DiscoveryOrchestratorRequest.class);
            case AgentModels.OrchestratorAgentResult ignored ->
                    findLastFromHistory(history, AgentModels.OrchestratorRequest.class);
            case AgentModels.OrchestratorCollectorResult ignored ->
                    findLastFromHistory(history, AgentModels.OrchestratorCollectorRequest.class);
            case AgentModels.PlanningAgentResult ignored ->
                    findLastFromHistory(history, AgentModels.PlanningAgentRequest.class);
            case AgentModels.PlanningCollectorResult ignored ->
                    findLastFromHistory(history, AgentModels.PlanningCollectorRequest.class);
            case AgentModels.PlanningOrchestratorResult ignored ->
                    findLastFromHistory(history, AgentModels.PlanningOrchestratorRequest.class);
            case AgentModels.ReviewAgentResult ignored ->
                    findLastFromHistory(history, AgentModels.AgentRequest.class);
            case AgentModels.TicketAgentResult ignored ->
                    findLastFromHistory(history, AgentModels.TicketAgentRequest.class);
            case AgentModels.TicketCollectorResult ignored ->
                    findLastFromHistory(history, AgentModels.TicketCollectorRequest.class);
            case AgentModels.TicketOrchestratorResult ignored ->
                    findLastFromHistory(history, AgentModels.TicketOrchestratorRequest.class);
            case AgentModels.AiFilterResult ignored ->
                    findLastFromHistory(history, AgentModels.AiFilterRequest.class);
            case AgentModels.AiPropagatorResult ignored ->
                    findLastFromHistory(history, AgentModels.AiPropagatorRequest.class);
            case AgentModels.AiTransformerResult ignored ->
                    findLastFromHistory(history, AgentModels.AiTransformerRequest.class);
            case AgentModels.AgentCallResult ignored ->
                    findLastFromHistory(history, AgentModels.AgentToAgentRequest.class);
            case AgentModels.ControllerCallResult ignored ->
                    findLastFromHistory(history, AgentModels.AgentToControllerRequest.class);
            case AgentModels.ControllerResponseResult ignored ->
                    findLastFromHistory(history, AgentModels.ControllerToAgentRequest.class);
        };
    }

    private Artifact.AgentModel findParentForAgentRequests(Blackboard history, AgentModels.AgentRequest req) {
        return switch (req) {
            // Existing cases (migrated from old switch)
            case AgentModels.ContextManagerRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.ContextManagerRoutingRequest.class,
                            AgentModels.AgentRequest.class,
                            AgentModels.AgentResult.class);
            case AgentModels.ContextManagerRoutingRequest ignored ->
                    findLastFromHistory(history, AgentModels.AgentRequest.class, AgentModels.AgentResult.class);
            case AgentModels.DiscoveryAgentRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.DiscoveryAgentRequests.class);
            case AgentModels.DiscoveryAgentRequests ignored ->
                    findLastFromHistory(history,
                            AgentModels.DiscoveryOrchestratorRequest.class);
            case AgentModels.DiscoveryAgentResults ignored ->
                    findLastFromHistory(history,
                            AgentModels.DiscoveryAgentRequests.class);
            case AgentModels.DiscoveryCollectorRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.DiscoveryOrchestratorRequest.class);
            case AgentModels.DiscoveryOrchestratorRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.OrchestratorRequest.class);
            case AgentModels.InterruptRequest interruptRequest ->
                    findParentForInterruptTypes(history, interruptRequest);
            case AgentModels.OrchestratorCollectorRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.OrchestratorRequest.class);
            case AgentModels.OrchestratorRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.OrchestratorRequest.class);
            case AgentModels.PlanningAgentRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.PlanningOrchestratorRequest.class);
            case AgentModels.PlanningAgentRequests ignored ->
                    findLastFromHistory(history,
                            AgentModels.PlanningOrchestratorRequest.class);
            case AgentModels.PlanningAgentResults ignored ->
                    findLastFromHistory(history,
                            AgentModels.PlanningAgentRequests.class);
            case AgentModels.PlanningCollectorRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.PlanningOrchestratorRequest.class);
            case AgentModels.PlanningOrchestratorRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.OrchestratorRequest.class);
            case AgentModels.TicketAgentRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.TicketOrchestratorRequest.class);
            case AgentModels.CommitAgentRequest commitReq ->
                    commitReq.routedFromRequest();
            case AgentModels.MergeConflictRequest mergeConflictRequest ->
                    mergeConflictRequest.routedFromRequest();
            case AgentModels.TicketAgentRequests ignored ->
                    findLastFromHistory(history,
                            AgentModels.TicketOrchestratorRequest.class);
            case AgentModels.TicketAgentResults ignored ->
                    findLastFromHistory(history,
                            AgentModels.TicketAgentRequests.class);
            case AgentModels.TicketCollectorRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.TicketOrchestratorRequest.class);
            case AgentModels.TicketOrchestratorRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.OrchestratorRequest.class);
            case AgentModels.AiFilterRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.AgentRequest.class);
            case AgentModels.AiPropagatorRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.AgentRequest.class);
            case AgentModels.AiTransformerRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.AgentRequest.class);
            case AgentModels.AgentToAgentRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.AgentRequest.class);
            case AgentModels.AgentToControllerRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.AgentRequest.class);
            case AgentModels.ControllerToAgentRequest ignored ->
                    findLastFromHistory(history,
                            AgentModels.AgentRequest.class);
        };
    }

    private Artifact.AgentModel findParentForInterruptTypes(Blackboard history, AgentModels.InterruptRequest interruptRequest) {
        return switch (interruptRequest) {
            case AgentModels.InterruptRequest.ContextManagerInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.ContextManagerRequest.class);
            case AgentModels.InterruptRequest.DiscoveryAgentDispatchInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.DiscoveryAgentRequests.class);
            case AgentModels.InterruptRequest.DiscoveryAgentInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.DiscoveryAgentRequest.class);
            case AgentModels.InterruptRequest.DiscoveryCollectorInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.DiscoveryCollectorRequest.class);
            case AgentModels.InterruptRequest.DiscoveryOrchestratorInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.DiscoveryOrchestratorRequest.class);
            case AgentModels.InterruptRequest.OrchestratorCollectorInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.OrchestratorCollectorRequest.class);
            case AgentModels.InterruptRequest.OrchestratorInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.OrchestratorRequest.class);
            case AgentModels.InterruptRequest.PlanningAgentDispatchInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.PlanningAgentRequests.class);
            case AgentModels.InterruptRequest.PlanningAgentInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.PlanningAgentRequest.class);
            case AgentModels.InterruptRequest.PlanningCollectorInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.PlanningCollectorRequest.class);
            case AgentModels.InterruptRequest.PlanningOrchestratorInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.PlanningOrchestratorRequest.class);
            case AgentModels.InterruptRequest.QuestionAnswerInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.ContextManagerRequest.class);
            case AgentModels.InterruptRequest.TicketAgentDispatchInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.TicketAgentRequests.class);
            case AgentModels.InterruptRequest.TicketAgentInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.TicketAgentRequest.class);
            case AgentModels.InterruptRequest.TicketCollectorInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.TicketCollectorRequest.class);
            case AgentModels.InterruptRequest.TicketOrchestratorInterruptRequest ignored ->
                    findLastFromHistory(history, AgentModels.TicketOrchestratorRequest.class);
        };
    }

    private Artifact.AgentModel findLastFromHistory(Blackboard history, Class<?>... types) {

        for (var t : types) {
            var m = BlackboardHistory.getLastFromHistory(history, t);
            if (m instanceof Artifact.AgentModel agentModel)
                return agentModel;
        }

        return null;
    }

    private <T extends AgentModels.AgentRouting> T enrichRouting(T model, OperationContext context, Artifact.AgentModel parent) {
//        doesn't need to be enriched - any request will be enriched in the action it routes to when it routes to that action.
        return model;
    }

    /**
     * Enrich a request object by setting ArtifactKey and PreviousContext fields.
     * Pattern matches on the input type to apply appropriate enrichment.
     *
     * @param input   the request object to enrich
     * @param context the operation context for resolving IDs and previous state
     * @return the enriched request object
     */
    @SuppressWarnings("unchecked")
    public <T extends AgentModels.AgentRequest> T enrichAgentRequests(T input, OperationContext context, Artifact.AgentModel parent) {
        if (input == null) {
            return null;
        }

        return switch (input) {
            case AgentModels.OrchestratorRequest req ->
                    (T) enrichOrchestratorRequest(req, context, parent);
            case AgentModels.OrchestratorCollectorRequest req ->
                    (T) enrichOrchestratorCollectorRequest(req, context, parent);
            case AgentModels.DiscoveryOrchestratorRequest req ->
                    (T) enrichDiscoveryOrchestratorRequest(req, context, parent);
            case AgentModels.DiscoveryAgentRequest req ->
                    (T) enrichDiscoveryAgentRequest(req, context, parent);
            case AgentModels.DiscoveryCollectorRequest req ->
                    (T) enrichDiscoveryCollectorRequest(req, context, parent);
            case AgentModels.PlanningOrchestratorRequest req ->
                    (T) enrichPlanningOrchestratorRequest(req, context, parent);
            case AgentModels.PlanningAgentRequest req ->
                    (T) enrichPlanningAgentRequest(req, context, parent);
            case AgentModels.PlanningCollectorRequest req ->
                    (T) enrichPlanningCollectorRequest(req, context, parent);
            case AgentModels.TicketOrchestratorRequest req ->
                    (T) enrichTicketOrchestratorRequest(req, context, parent);
            case AgentModels.TicketAgentRequest req ->
                    (T) enrichTicketAgentRequest(req, context, parent);
            case AgentModels.CommitAgentRequest req -> {
                Artifact.AgentModel commitParent = req.routedFromRequest() != null ? req.routedFromRequest() : parent;
                AgentModels.AgentRequest routedFrom = req.routedFromRequest() != null
                        ? req.routedFromRequest()
                        : (parent instanceof AgentModels.AgentRequest ar ? ar : null);
                ArtifactKey chatKey = commitParent != null ? commitParent.key() : null;
                yield (T) req.toBuilder()
                        .contextId(resolveContextId(context, req, commitParent))
                        .chatKey(chatKey)
                        .routedFromRequest(routedFrom)
                        .build();
            }
            case AgentModels.MergeConflictRequest req -> {
                Artifact.AgentModel mergeParent = req.routedFromRequest() != null ? req.routedFromRequest() : parent;
                AgentModels.AgentRequest routedFrom = req.routedFromRequest() != null
                        ? req.routedFromRequest()
                        : (parent instanceof AgentModels.AgentRequest ar ? ar : null);
                ArtifactKey chatKey = mergeParent != null ? mergeParent.key() : null;
                yield (T) req.toBuilder()
                        .contextId(resolveContextId(context, req, mergeParent))
                        .chatKey(chatKey)
                        .routedFromRequest(routedFrom)
                        .build();
            }
            case AgentModels.TicketCollectorRequest req ->
                    (T) enrichTicketCollectorRequest(req, context, parent);
            case AgentModels.DiscoveryAgentRequests req ->
                    (T) enrichDiscoveryAgentRequests(req, context, parent);
            case AgentModels.DiscoveryAgentResults req ->
                    (T) enrichDiscoveryAgentResults(req, context, parent);
            case AgentModels.PlanningAgentRequests req ->
                    (T) enrichPlanningAgentRequests(req, context, parent);
            case AgentModels.PlanningAgentResults req ->
                    (T) enrichPlanningAgentResults(req, context, parent);
            case AgentModels.TicketAgentRequests req ->
                    (T) enrichTicketAgentRequests(req, context, parent);
            case AgentModels.TicketAgentResults req ->
                    (T) enrichTicketAgentResults(req, context, parent);
            case AgentModels.InterruptRequest req ->
                    (T) enrichInterruptRequest(req, context, parent);
            case AgentModels.ContextManagerRequest req ->
                    (T) enrichContextManagerRequest(req, context, parent);
            case AgentModels.ContextManagerRoutingRequest req ->
                    (T) req.toBuilder()
                            .contextId(resolveContextId(context, req, parent))
                            .build();
            case AgentModels.AiFilterRequest req ->
                    (T) req;
            case AgentModels.AiPropagatorRequest req ->
                    (T) req;
            case AgentModels.AiTransformerRequest req ->
                    (T) req;
            case AgentModels.AgentToAgentRequest req ->
                    (T) req;
            case AgentModels.AgentToControllerRequest req ->
                    (T) req;
            case AgentModels.ControllerToAgentRequest req ->
                    (T) req;
        };
    }

    private <T extends AgentModels.AgentResult> T enrichAgentResult(T input, OperationContext context, Artifact.AgentModel parent) {
        AgentModels.AgentResult res = switch (input) {
            case AgentModels.DiscoveryAgentResult result -> {
                var enriched = result.toBuilder()
                        .contextId(resolveContextId(context, AgentType.DISCOVERY_AGENT, parent))
                        .build();
                yield withEnrichedChildren(enriched, enriched.children(), context);
            }
            case AgentModels.PlanningOrchestratorResult result -> {
                var enriched = result.toBuilder()
                        .contextId(resolveContextId(context, AgentType.PLANNING_ORCHESTRATOR, parent))
                        .build();
                yield withEnrichedChildren(enriched, enriched.children(), context);
            }
            case AgentModels.DiscoveryOrchestratorResult result -> {
                var enriched = result.toBuilder()
                        .contextId(resolveContextId(context, AgentType.DISCOVERY_ORCHESTRATOR, parent))
                        .build();
                yield withEnrichedChildren(enriched, enriched.children(), context);
            }
            case AgentModels.MergerAgentResult result -> {
                var enriched = result.toBuilder()
                        .contextId(resolveContextId(context, AgentType.MERGER_AGENT, parent))
                        .build();
                yield withEnrichedChildren(enriched, enriched.children(), context);
            }
            case AgentModels.OrchestratorAgentResult result -> {
                var enriched = result.toBuilder()
                        .contextId(resolveContextId(context, AgentType.ORCHESTRATOR, parent))
                        .build();
                yield withEnrichedChildren(enriched, enriched.children(), context);
            }
            case AgentModels.PlanningAgentResult result -> {
                var enriched = result.toBuilder()
                        .contextId(resolveContextId(context, AgentType.PLANNING_AGENT, parent))
                        .build();
                yield withEnrichedChildren(enriched, enriched.children(), context);
            }
            case AgentModels.ReviewAgentResult result -> {
                var enriched = result.toBuilder()
                        .contextId(resolveContextId(context, AgentType.REVIEW_AGENT, parent))
                        .build();
                yield withEnrichedChildren(enriched, enriched.children(), context);
            }
            case AgentModels.TicketAgentResult result -> {
                var enriched = result.toBuilder()
                        .contextId(resolveContextId(context, AgentType.TICKET_AGENT, parent))
                        .build();
                yield withEnrichedChildren(enriched, enriched.children(), context);
            }
            case AgentModels.CommitAgentResult result -> {
                var enriched = result.toBuilder()
                        .contextId(resolveContextId(context, AgentType.COMMIT_AGENT, parent))
                        .build();
                yield withEnrichedChildren(enriched, enriched.children(), context);
            }
            case AgentModels.MergeConflictResult result -> {
                var enriched = result.toBuilder()
                        .contextId(resolveContextId(context, AgentType.ALL, parent))
                        .build();
                yield withEnrichedChildren(enriched, enriched.children(), context);
            }
            case AgentModels.TicketOrchestratorResult result -> {
                var enriched = result.toBuilder()
                        .contextId(resolveContextId(context, AgentType.TICKET_ORCHESTRATOR, parent))
                        .build();
                yield withEnrichedChildren(enriched, enriched.children(), context);
            }
            case AgentModels.TicketCollectorResult collectorResult -> {
                collectorResult = collectorResult.toBuilder()
                        .contextId(resolveContextId(context, AgentType.TICKET_COLLECTOR, parent))
                        .build();
                yield withEnrichedChildren(collectorResult, collectorResult.children(), context);
            }
            case AgentModels.DiscoveryCollectorResult collectorResult -> {
                collectorResult = collectorResult.toBuilder()
                        .contextId(resolveContextId(context, AgentType.DISCOVERY_COLLECTOR, parent))
                        .build();
                yield withEnrichedChildren(collectorResult, collectorResult.children(), context);
            }
            case AgentModels.OrchestratorCollectorResult collectorResult -> {
                collectorResult = collectorResult.toBuilder()
                        .contextId(resolveContextId(context, AgentType.ORCHESTRATOR_COLLECTOR, parent))
                        .build();
                yield withEnrichedChildren(collectorResult, collectorResult.children(), context);
            }
            case AgentModels.PlanningCollectorResult collectorResult -> {
                collectorResult = collectorResult.toBuilder()
                        .contextId(resolveContextId(context, AgentType.PLANNING_COLLECTOR, parent))
                        .build();
                yield withEnrichedChildren(collectorResult, collectorResult.children(), context);
            }
            case AgentModels.AiFilterResult aiFilterResult ->
                    aiFilterResult.toBuilder()
                            .contextId(resolveContextId(context, AgentType.AI_FILTER, parent))
                            .build();
            case AgentModels.AiPropagatorResult aiPropagatorResult ->
                    aiPropagatorResult.toBuilder()
                            .contextId(resolveContextId(context, AgentType.AI_PROPAGATOR, parent))
                            .build();
            case AgentModels.AiTransformerResult aiTransformerResult ->
                    aiTransformerResult.toBuilder()
                            .contextId(resolveContextId(context, AgentType.AI_TRANSFORMER, parent))
                            .build();
            case AgentModels.AgentCallResult agentCallResult ->
                    agentCallResult.toBuilder()
                            .contextId(resolveContextId(context, AgentType.AGENT_CALL, parent))
                            .build();
            case AgentModels.ControllerCallResult controllerCallResult ->
                    controllerCallResult.toBuilder()
                            .contextId(resolveContextId(context, AgentType.CONTROLLER_CALL, parent))
                            .build();
            case AgentModels.ControllerResponseResult controllerResponseResult ->
                    controllerResponseResult.toBuilder()
                            .contextId(resolveContextId(context, AgentType.CONTROLLER_RESPONSE, parent))
                            .build();
        };

        return (T) res;
    }

    private <T extends Artifact.AgentModel> T enrichAgentModel(T input, OperationContext context, Artifact.AgentModel parent) {
        if (input == null) {
            return null;
        }

        T enriched = input;
        if (enriched.key() == null || enriched.key().value() == null) {
            ArtifactKey generatedKey = contextIdService.generate(resolveWorkflowRunId(context), null, parent);
            if (generatedKey != null) {
                enriched = (T) enriched.withContextId(generatedKey);
                log.debug("Assigned missing nested model contextId for {} -> {}",
                        input.getClass().getSimpleName(), generatedKey.value());
            }
        }

        return withEnrichedChildren(enriched, enriched.children(), context);
    }

    private AgentModels.InterruptRequest enrichInterruptRequest(
            AgentModels.InterruptRequest req,
            OperationContext context,
            Artifact.AgentModel parent
    ) {
        if (req == null) {
            return null;
        }
        return switch (req) {
            case AgentModels.InterruptRequest.OrchestratorInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.OrchestratorCollectorInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.DiscoveryOrchestratorInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.DiscoveryAgentInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.DiscoveryCollectorInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.DiscoveryAgentDispatchInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.PlanningOrchestratorInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.PlanningAgentInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.PlanningCollectorInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.PlanningAgentDispatchInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.TicketOrchestratorInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.TicketAgentInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.TicketCollectorInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.TicketAgentDispatchInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.ContextManagerInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
            case AgentModels.InterruptRequest.QuestionAnswerInterruptRequest r ->
                    r.toBuilder().contextId(resolveContextId(context, r, parent)).build();
        };
    }

    private AgentModels.OrchestratorRequest enrichOrchestratorRequest(
            AgentModels.OrchestratorRequest req, OperationContext context, Artifact.AgentModel parent) {
        var reqBuilder = req.toBuilder();
        if ((req.key() == null || req.key().value() == null) && parent == null) {
            String processId = context.getProcessContext().getAgentProcess().getId();
            log.error("Orchestrator request key was null. This isnt' supposed to happen. Manually creating ArtifactKey with ID as OperationContext.processId({})",
                    processId);
            reqBuilder = reqBuilder.contextId(new ArtifactKey(processId));
        } else if (parent != null) {
            if (parent == req) {
                log.error("Found strange instance where was same request.");
            }

            reqBuilder = reqBuilder.contextId(resolveContextId(context, req, parent));
        }

        return withEnrichedChildren(reqBuilder.build(), req.children(), context);
    }

    private AgentModels.OrchestratorCollectorRequest enrichOrchestratorCollectorRequest(
            AgentModels.OrchestratorCollectorRequest req, OperationContext context, Artifact.AgentModel parent) {
        return withEnrichedChildren(req.toBuilder()
                .contextId(resolveContextId(context, req, parent))
                .build(), req.children(), context);
    }

    private AgentModels.DiscoveryOrchestratorRequest enrichDiscoveryOrchestratorRequest(
            AgentModels.DiscoveryOrchestratorRequest req, OperationContext context, Artifact.AgentModel parent) {
        return withEnrichedChildren(req.toBuilder()
                .contextId(resolveContextId(context, req, parent))
                .build(), req.children(), context);
    }

    private AgentModels.DiscoveryAgentRequest enrichDiscoveryAgentRequest(
            AgentModels.DiscoveryAgentRequest req, OperationContext context, Artifact.AgentModel parent) {
        return withEnrichedChildren(req.toBuilder()
                .contextId(resolveContextId(context, req, parent))
                .build(), req.children(), context);
    }

    private AgentModels.DiscoveryCollectorRequest enrichDiscoveryCollectorRequest(
            AgentModels.DiscoveryCollectorRequest req, OperationContext context, Artifact.AgentModel parent) {
        return withEnrichedChildren(req.toBuilder()
                .contextId(resolveContextId(context, req, parent))
                .build(), req.children(), context);
    }

    private AgentModels.PlanningOrchestratorRequest enrichPlanningOrchestratorRequest(
            AgentModels.PlanningOrchestratorRequest req, OperationContext context, Artifact.AgentModel parent) {
        return withEnrichedChildren(req.toBuilder()
                .contextId(resolveContextId(context, req, parent))
                .build(), req.children(), context);
    }

    private AgentModels.PlanningAgentRequest enrichPlanningAgentRequest(
            AgentModels.PlanningAgentRequest req, OperationContext context, Artifact.AgentModel parent) {
        return withEnrichedChildren(req.toBuilder()
                .contextId(resolveContextId(context, req, parent))
                .build(), req.children(), context);
    }

    private AgentModels.PlanningCollectorRequest enrichPlanningCollectorRequest(
            AgentModels.PlanningCollectorRequest req, OperationContext context, Artifact.AgentModel parent) {
        return withEnrichedChildren(req.toBuilder()
                .contextId(resolveContextId(context, req, parent))
                .build(), req.children(), context);
    }

    private AgentModels.TicketOrchestratorRequest enrichTicketOrchestratorRequest(
            AgentModels.TicketOrchestratorRequest req, OperationContext context, Artifact.AgentModel parent) {
        return withEnrichedChildren(req.toBuilder()
                .contextId(resolveContextId(context, req, parent))
                .build(), req.children(), context);
    }

    private AgentModels.TicketAgentRequest enrichTicketAgentRequest(
            AgentModels.TicketAgentRequest req, OperationContext context, Artifact.AgentModel parent) {
        return withEnrichedChildren(req.toBuilder()
                .contextId(resolveContextId(context, req, parent))
                .build(), req.children(), context);
    }

    private AgentModels.TicketCollectorRequest enrichTicketCollectorRequest(
            AgentModels.TicketCollectorRequest req, OperationContext context, Artifact.AgentModel parent) {
        return withEnrichedChildren(req.toBuilder()
                .contextId(resolveContextId(context, req, parent))
                .build(), req.children(), context);
    }

    private AgentModels.DiscoveryAgentResults enrichDiscoveryAgentResults(
            AgentModels.DiscoveryAgentResults req, OperationContext context, Artifact.AgentModel parent) {
        return withEnrichedChildren(req.toBuilder()
                .contextId(resolveContextId(context, req, parent))
                .build(), req.children(), context);
    }

    private AgentModels.PlanningAgentResults enrichPlanningAgentResults(
            AgentModels.PlanningAgentResults req, OperationContext context, Artifact.AgentModel parent) {
        return withEnrichedChildren(req.toBuilder()
                .contextId(resolveContextId(context, req, parent))
                .build(), req.children(), context);
    }


    private AgentModels.DiscoveryAgentRequests enrichDiscoveryAgentRequests(
            AgentModels.DiscoveryAgentRequests req, OperationContext context, Artifact.AgentModel parent) {
        log.info("enrichDiscoveryAgentRequests: BEFORE enrichment | containerContextId={} | childCount={} | parentKey={}",
                req.contextId() != null ? req.contextId().value() : "null",
                req.requests() != null ? req.requests().size() : 0,
                parent != null && parent.key() != null ? parent.key().value() : "null");
        AgentModels.DiscoveryAgentRequests built = req.toBuilder()
                .contextId(resolveContextId(context, req, parent))
                .build();
        log.info("enrichDiscoveryAgentRequests: AFTER container enrichment | containerContextId={}", 
                built.contextId() != null ? built.contextId().value() : "null");
        AgentModels.DiscoveryAgentRequests result = (AgentModels.DiscoveryAgentRequests) built
                .withChildren(enrichChildren(built.children(), context, built));
        if (result.requests() != null) {
            for (int i = 0; i < result.requests().size(); i++) {
                var child = result.requests().get(i);
                log.info("enrichDiscoveryAgentRequests: CHILD[{}] contextId={} | subdomain={}",
                        i, child.contextId() != null ? child.contextId().value() : "null",
                        child.subdomainFocus());
            }
        }
        return result;
    }

    private AgentModels.PlanningAgentRequests enrichPlanningAgentRequests(
            AgentModels.PlanningAgentRequests req, OperationContext context, Artifact.AgentModel parent) {
        AgentModels.PlanningAgentRequests built = req.toBuilder()
                .contextId(resolveContextId(context, req, parent))
                .build();
        return built
                .withChildren(enrichChildren(built.children(), context, built));
    }

    private AgentModels.TicketAgentRequests enrichTicketAgentRequests(
            AgentModels.TicketAgentRequests req, OperationContext context, Artifact.AgentModel parent) {
        AgentModels.TicketAgentRequests build = req.toBuilder()
                .contextId(resolveContextId(context, req, parent))
                .build();
        return build
                .withChildren(enrichChildren(build.children(), context, build));
    }

    private AgentModels.TicketAgentResults enrichTicketAgentResults(
            AgentModels.TicketAgentResults req, OperationContext context, Artifact.AgentModel parent) {
        return withEnrichedChildren(req.toBuilder()
                .contextId(resolveContextId(context, req, parent))
                .build(), req.children(), context);
    }

    private AgentModels.ContextManagerRequest enrichContextManagerRequest(
            AgentModels.ContextManagerRequest req, OperationContext context, Artifact.AgentModel parent) {
        return withEnrichedChildren(
                req.toBuilder()
                        .contextId(resolveContextId(context, req, parent))
                        .build(),
                req.children(),
                context
        );
    }

    private <T extends Artifact.AgentModel> T withEnrichedChildren(
            T model,
            List<Artifact.AgentModel> children,
            OperationContext context
    ) {
        if (model == null) {
            return null;
        }
        if (children == null || children.isEmpty()) {
            return model;
        }

        return model.withChildren(enrichChildren(children, context, model));
    }

    private List<Artifact.AgentModel> enrichChildren(
            List<Artifact.AgentModel> children,
            OperationContext context,
            Artifact.AgentModel parent
    ) {
        if (children == null || children.isEmpty()) {
            return List.of();
        }

        return children.stream()
                .map(child -> enrich(child, context, parent))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private ArtifactKey resolveContextId(OperationContext context, AgentType agentType, Artifact.AgentModel parent) {
        if (contextIdService == null) {
            return null;
        }
        return contextIdService.generate(resolveWorkflowRunId(context), agentType, parent);
    }

    /**
     * Resolve context ID for a request, recycling previous context IDs for non-dispatched agents.
     * <p>
     * Rules:
     * - Dispatched agents (Discovery/Planning/Ticket Agent): Always create new child
     * - All other agents: Try to find previous request of same type and reuse its contextId
     */
    private ArtifactKey resolveContextId(OperationContext context, AgentModels.AgentRequest currentRequest, Artifact.AgentModel parent) {
        if (contextIdService == null) {
            return null;
        }

        String requestType = currentRequest.getClass().getSimpleName();
        String parentKey = parent != null && parent.key() != null ? parent.key().value() : "null";
        String workflowRunId = resolveWorkflowRunId(context);

        if (shouldCreateNewSession(currentRequest)) {
            ArtifactKey newKey = contextIdService.generate(workflowRunId, null, parent);
            log.info("resolveContextId: NEW SESSION for {} | parentKey={} | workflowRunId={} | generatedKey={}",
                    requestType, parentKey, workflowRunId, newKey != null ? newKey.value() : "null");
            return newKey;
        }

        // For orchestrators, collectors, and dispatchers: try to recycle previous contextId
        BlackboardHistory history = BlackboardHistory.getEntireBlackboardHistory(
                context.getAgentProcess()
        );
        if (history != null) {
            ArtifactKey recycled = findPreviousContextId(history, currentRequest);
            if (recycled != null) {
                log.info("resolveContextId: RECYCLED for {} | recycledKey={}", requestType, recycled.value());
                return recycled;
            }
        }

        ArtifactKey fallbackKey = contextIdService.generate(workflowRunId, null, parent);
        log.info("resolveContextId: FALLBACK (no previous) for {} | parentKey={} | workflowRunId={} | generatedKey={}",
                requestType, parentKey, workflowRunId, fallbackKey != null ? fallbackKey.value() : "null");
        return fallbackKey;
    }

    /**
     * Dispatched agents (Discovery/Planning/Ticket Agent) always get new sessions.
     */
    private boolean shouldCreateNewSession(Artifact.AgentModel model) {
        return model instanceof AgentModels.DiscoveryAgentRequest
                || model instanceof AgentModels.PlanningAgentRequest
                || model instanceof AgentModels.TicketAgentRequest
                || model instanceof AgentModels.CommitAgentRequest;
    }

    /**
     * Find a previous request of the same type in BlackboardHistory and return its contextId.
     */
    private ArtifactKey findPreviousContextId(BlackboardHistory history, AgentModels.AgentRequest currentRequest) {
        AgentModels.AgentRequest previous = history.getLastOfType(currentRequest.getClass());
        if (previous != null && (previous.contextId() != null && previous.contextId().value() != null)) {
            return previous.contextId();
        }
        return null;
    }

    private String resolveWorkflowRunId(OperationContext context) {
        return BlackboardHistory.resolveNodeId(context);
    }


}