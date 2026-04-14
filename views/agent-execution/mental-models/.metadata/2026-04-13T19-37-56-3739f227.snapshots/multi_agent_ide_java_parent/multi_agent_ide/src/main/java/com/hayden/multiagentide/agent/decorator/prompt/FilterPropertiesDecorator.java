package com.hayden.multiagentide.agent.decorator.prompt;

import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.core.AgentProcess;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.agent.*;
import com.hayden.multiagentide.prompt.contributor.NodeMappings;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.lang.annotation.Annotation;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Component
public class FilterPropertiesDecorator implements LlmCallDecorator {

    private static final Set<Class<? extends Annotation>> ALL_INTERRUPT_ROUTE_ANNOTATIONS = Set.of(
            OrchestratorRoute.class,
            DiscoveryRoute.class,
            PlanningRoute.class,
            TicketRoute.class,
            ContextManagerRoute.class,
            OrchestratorCollectorRoute.class,
            DiscoveryCollectorRoute.class,
            PlanningCollectorRoute.class,
            TicketCollectorRoute.class,
            DiscoveryDispatchRoute.class,
            PlanningDispatchRoute.class,
            TicketDispatchRoute.class
    );

    private final ConcurrentMap<String, Events.InterruptRequestEvent> interruptEvents = new ConcurrentHashMap<>();

    public void storeEvent(Events.InterruptRequestEvent event) {
        if (event == null || event.nodeId() == null || event.nodeId().isBlank()) {
            return;
        }
        interruptEvents.putIfAbsent(event.nodeId(), event);
    }

    @Override
    public int order() {
        return Ordered.HIGHEST_PRECEDENCE + 50;
    }

    @Override
    public <T> LlmCallContext<T> decorate(LlmCallContext<T> ctx) {
        var operations = ctx.templateOperations();

        if (ctx.templateOperations() == null)
            return ctx;

        if (ctx.promptContext() == null) {
            return ctx.withTemplateOperations(operations.withAnnotationFilter(SkipPropertyFilter.class));
        }

        if (!(ctx.promptContext().currentRequest() instanceof AgentModels.InterruptRequest)) {
            return ctx.withTemplateOperations(operations.withAnnotationFilter(SkipPropertyFilter.class));
        }

        Class<? extends Annotation> targetRoute = resolveTargetRoute(ctx);
        if (targetRoute == null) {
            return ctx.withTemplateOperations(operations.withAnnotationFilter(SkipPropertyFilter.class));
        }

        Set<Class<? extends Annotation>> toFilter
                = new LinkedHashSet<>(ALL_INTERRUPT_ROUTE_ANNOTATIONS);

        toFilter.remove(targetRoute);

        for (Class<? extends Annotation> annotation : toFilter) {
            operations = operations.withAnnotationFilter(annotation);
        }

        return ctx.withTemplateOperations(operations.withAnnotationFilter(SkipPropertyFilter.class));
    }

    private <T> Class<? extends Annotation> resolveTargetRoute(LlmCallContext<T> ctx) {
        var annotation = tryAnnotationFromInterruptRequest(ctx);
        if (annotation != null) return annotation;

        var s = mapRequestToRoute(ctx.promptContext().previousRequest());

        if (s != null)
            return s;

        s = Optional.ofNullable(NodeMappings.agentTypeFromRequest(ctx.promptContext().previousRequest()))
                .map(AgentType::wireValue)
                .map(FilterPropertiesDecorator::mapAgentTypeToRoute)
                .orElse(null);

        if (s != null)
            return s;

        var e = BlackboardHistory.getEntireBlackboardHistory(ctx.op());

        if (e == null)
            return ContextManagerRoute.class;

        s =  e.getLastMatching(ent -> {
                            //
                            if (ent.input() instanceof AgentModels.AgentRequest req) {
                                return Optional.ofNullable(mapRequestToRoute(req));
                            }

                            return Optional.empty();
                        });

        if (s != null)
            return s;

        return ContextManagerRoute.class;
    }

    private <T> @Nullable Class<? extends Annotation> tryAnnotationFromInterruptRequest(LlmCallContext<T> ctx) {
        var event = resolveNodeId(ctx);

        if (event == null)
            return null;

        Class<? extends Annotation> annotation = mapAgentTypeToRoute(event.rerouteToAgentType());
        return annotation;
    }

    synchronized  <T> Events.InterruptRequestEvent resolveNodeId(LlmCallContext<T> ctx) {
        return Optional.ofNullable(ctx.promptContext().currentContextId())
                .map(ArtifactKey::value)
                .filter(v -> !v.isBlank())
                .flatMap(this::findThisOrChildOfKey)
                .or(() -> Optional.ofNullable(ctx.op())
                        .map(OperationContext::getAgentProcess)
                        .map(AgentProcess::getId)
                        .filter(v -> !v.isBlank())
                        .flatMap(this::findThisOrChildOfKey)
                )
                .orElse(null);
    }

    private @NonNull Optional<Events.InterruptRequestEvent> findThisOrChildOfKey(String s) {
        var removed = interruptEvents.remove(s);
        if (removed != null)
            return Optional.of(removed);

        if (interruptEvents.isEmpty())
            return Optional.empty();

        try {
            var thisKey = new ArtifactKey(s);
            return findChildOfKey(thisKey);
        } catch (Exception e) {
            log.error("error - artifact key unknown.", e);
            return Optional.empty();
        }
    }

    private @NonNull Optional<Events.InterruptRequestEvent> findChildOfKey(ArtifactKey thisKey) {
        return interruptEvents.keySet().stream().filter(key -> {
                    try {
                        var k = new ArtifactKey(key);
                        if (k.isChildOf(thisKey)) {
                            return true;
                        }

                        return false;
                    } catch (
                            Exception e) {
                        log.error("error - artifact key unknown.", e);
                    }
                    return false;
                })
                .max(Comparator.comparing(String::length))
                .map(interruptEvents::remove);
    }

    private static Class<? extends Annotation> mapRequestToRoute(AgentModels.AgentRequest request) {
        if (request == null) {
            return null;
        }
        return switch (request) {
            case AgentModels.OrchestratorRequest ignored -> OrchestratorRoute.class;
            case AgentModels.DiscoveryOrchestratorRequest ignored -> DiscoveryRoute.class;
            case AgentModels.DiscoveryAgentRequest ignored -> DiscoveryRoute.class;
            case AgentModels.PlanningOrchestratorRequest ignored -> PlanningRoute.class;
            case AgentModels.PlanningAgentRequest ignored -> PlanningRoute.class;
            case AgentModels.TicketOrchestratorRequest ignored -> TicketRoute.class;
            case AgentModels.TicketAgentRequest ignored -> TicketRoute.class;
            case AgentModels.ContextManagerRequest ignored -> ContextManagerRoute.class;
            case AgentModels.ContextManagerRoutingRequest ignored -> ContextManagerRoute.class;
            case AgentModels.OrchestratorCollectorRequest ignored -> OrchestratorCollectorRoute.class;
            case AgentModels.DiscoveryCollectorRequest ignored -> DiscoveryCollectorRoute.class;
            case AgentModels.PlanningCollectorRequest ignored -> PlanningCollectorRoute.class;
            case AgentModels.TicketCollectorRequest ignored -> TicketCollectorRoute.class;
            case AgentModels.DiscoveryAgentRequests ignored -> DiscoveryDispatchRoute.class;
            case AgentModels.DiscoveryAgentResults ignored -> DiscoveryDispatchRoute.class;
            case AgentModels.PlanningAgentRequests ignored -> PlanningDispatchRoute.class;
            case AgentModels.PlanningAgentResults planningAgentResults -> PlanningDispatchRoute.class;
            case AgentModels.TicketAgentRequests ignored -> TicketDispatchRoute.class;
            case AgentModels.TicketAgentResults ticketAgentResults -> TicketDispatchRoute.class;
            case AgentModels.CommitAgentRequest commitAgentRequest -> null;
            case AgentModels.MergeConflictRequest mergeConflictRequest -> null;
            case AgentModels.InterruptRequest interruptRequest -> null;
            case AgentModels.AiFilterRequest ignored -> null;
            case AgentModels.AiPropagatorRequest ignored -> null;
            case AgentModels.AiTransformerRequest ignored -> null;
            case AgentModels.AgentToAgentRequest ignored -> null;
            case AgentModels.AgentToControllerRequest ignored -> null;
            case AgentModels.ControllerToAgentRequest ignored -> null;
        };
    }

    private static Class<? extends Annotation> mapAgentTypeToRoute(String agentTypeValue) {
        AgentType agentType = AgentType.fromWireValue(agentTypeValue);
        if (agentType == null) {
            return null;
        }
        return switch (agentType) {
            case ORCHESTRATOR -> OrchestratorRoute.class;
            case DISCOVERY_ORCHESTRATOR -> DiscoveryRoute.class;
            case PLANNING_ORCHESTRATOR -> PlanningRoute.class;
            case TICKET_ORCHESTRATOR -> TicketRoute.class;
            case CONTEXT_MANAGER -> ContextManagerRoute.class;
            case ORCHESTRATOR_COLLECTOR -> OrchestratorCollectorRoute.class;
            case DISCOVERY_COLLECTOR -> DiscoveryCollectorRoute.class;
            case PLANNING_COLLECTOR -> PlanningCollectorRoute.class;
            case TICKET_COLLECTOR -> TicketCollectorRoute.class;
            case DISCOVERY_AGENT_DISPATCH, DISCOVERY_AGENT -> DiscoveryDispatchRoute.class;
            case PLANNING_AGENT_DISPATCH, PLANNING_AGENT -> PlanningDispatchRoute.class;
            case TICKET_AGENT_DISPATCH, TICKET_AGENT -> TicketDispatchRoute.class;
            case REVIEW_AGENT, REVIEW_RESOLUTION_AGENT,
                 MERGER_AGENT, ALL,
                 COMMIT_AGENT, MERGE_CONFLICT_AGENT, AI_FILTER,
                 AI_PROPAGATOR, AI_TRANSFORMER, AGENT_CALL,
                 CONTROLLER_CALL, CONTROLLER_RESPONSE -> null;
        };
    }

}
