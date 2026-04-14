package com.hayden.multiagentide.agent.decorator.result;

import com.hayden.acp_cdc_ai.acp.events.HasContextId;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.agent.BlackboardHistoryService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Request decorator that registers the input in blackboard history and hides it.
 * Uses order 0 (default) to run after emitActionStarted but before other decorators.
 *
 * Skips hideInput for non-workflow results (propagators, filters, transformers, etc.)
 * to avoid clearing the blackboard before the routing result can be consumed by the planner.
 */
@Component
@RequiredArgsConstructor
public class RegisterAndHideInputResultDecorator implements DispatchedAgentResultDecorator, ResultDecorator, FinalResultDecorator {

    private static final Logger log = LoggerFactory.getLogger(RegisterAndHideInputResultDecorator.class);

    private static final Set<Class<? extends AgentModels.AgentResult>> NON_WORKFLOW_RESULT_TYPES = Set.of(
            AgentModels.AiPropagatorResult.class,
            AgentModels.AiFilterResult.class,
            AgentModels.AiTransformerResult.class,
            AgentModels.CommitAgentResult.class,
            AgentModels.MergeConflictResult.class,
            AgentModels.AgentCallResult.class,
            AgentModels.ControllerCallResult.class,
            AgentModels.ControllerResponseResult.class
    );

    private static final Set<Class<? extends AgentModels.AgentRouting>> NON_WORKFLOW_ROUTING_TYPES = Set.of(
            AgentModels.AgentCallRouting.class,
            AgentModels.ControllerCallRouting.class,
            AgentModels.ControllerResponseRouting.class
    );

    private static final Set<Class<? extends AgentModels.AgentRequest>> NON_WORKFLOW_REQUEST_TYPES = Set.of(
            AgentModels.CommitAgentRequest.class,
            AgentModels.AiFilterRequest.class,
            AgentModels.AiPropagatorRequest.class,
            AgentModels.AiTransformerRequest.class,
            AgentModels.MergeConflictRequest.class,
            AgentModels.AgentToAgentRequest.class,
            AgentModels.AgentToControllerRequest.class,
            AgentModels.ControllerToAgentRequest.class
    );

    private final BlackboardHistoryService blackboardHistoryService;

    private boolean isNonWorkflowResult(AgentModels.AgentResult result) {
        return NON_WORKFLOW_RESULT_TYPES.stream().anyMatch(t -> t.isInstance(result));
    }

    private boolean isNonWorkflowRouting(AgentModels.AgentRouting routing) {
        return NON_WORKFLOW_ROUTING_TYPES.stream().anyMatch(t -> t.isInstance(routing));
    }

    private boolean isNonWorkflowRequest(AgentModels.AgentRequest request) {
        return NON_WORKFLOW_REQUEST_TYPES.stream().anyMatch(t -> t.isInstance(request));
    }

    /**
     * This one had to happen after every other decorator, because if any of
     * of the other decorators failed or threw an exception, then it wouldn't be able to
     * retry.
     */
    @Override
    public int order() {
        return 10_005;
    }

    @Override
    public <T extends AgentModels.AgentResult> T decorate(T request, DecoratorContext context) {
        if (request == null) {
            return null;
        }

        log.info("hideInput (result) — agent={} action={} resultType={}",
                context.agentName(), context.actionName(),
                request.getClass().getSimpleName());

        blackboardHistoryService.registerResult(context.operationContext(), context.actionName(), request);

        if (!isNonWorkflowResult(request)) {
            blackboardHistoryService.hideInput(
                    context.operationContext()
            );
        } else {
            log.info("Skipping hideInput for non-workflow result type: {}", request.getClass().getSimpleName());
        }

        return request;
    }

    @Override
    public <T extends AgentModels.AgentRouting> T decorate(T t, DecoratorContext context) {
        log.info("hideInput (routing) — agent={} action={} routingType={}",
                context.agentName(), context.actionName(),
                t != null ? t.getClass().getSimpleName() : "(null)");

        Optional.ofNullable(t).map(s -> s.getClass())
                .stream()
                .flatMap(s -> Arrays.stream(s.getRecordComponents()))
                .flatMap(rc -> {
                    try {
                        return Stream.of(rc.getAccessor().invoke(t));
                    } catch (
                            IllegalAccessException |
                            InvocationTargetException e) {
                        log.error("Error when trying to retrieve record components of {}.", t);
                        return Stream.empty();
                    }
                })
                .filter(Objects::nonNull)
                .flatMap(s -> s instanceof HasContextId h ? Stream.of(h) : Stream.empty())
                .forEach(h -> blackboardHistoryService.registerResult(context.operationContext(), context.actionName(), h));

        if (t == null || !isNonWorkflowRouting(t)) {
            blackboardHistoryService.hideInput(
                    context.operationContext()
            );
        } else {
            log.info("Skipping hideInput for non-workflow routing type: {}", t.getClass().getSimpleName());
        }

        return t;
    }

    @Override
    public <T extends AgentModels.AgentRequest> T decorateRequestResult(T t, DecoratorContext context) {
        log.info("hideInput (requestResult) — agent={} action={} requestType={}",
                context.agentName(), context.actionName(),
                t != null ? t.getClass().getSimpleName() : "(null)");

        if (t == null || !isNonWorkflowRequest(t)) {
            blackboardHistoryService.hideInput(
                    context.operationContext()
            );
        } else {
            log.info("Skipping hideInput for non-workflow request type: {}", t.getClass().getSimpleName());
        }

        return t;
    }

    @Override
    public <T extends AgentModels.AgentResult> T decorateFinalResult(T t, FinalResultDecoratorContext context) {
        log.info("hideInput (finalResult) — agent={} action={} resultType={}",
                context.decoratorContext().agentName(), context.decoratorContext().actionName(),
                t != null ? t.getClass().getSimpleName() : "(null)");
        blackboardHistoryService.registerResult(context.decoratorContext().operationContext(), context.decoratorContext().actionName(), t);

        if (t == null || !isNonWorkflowResult(t)) {
            blackboardHistoryService.hideInput(
                    context.decoratorContext().operationContext()
            );
        } else {
            log.info("Skipping hideInput for non-workflow final result type: {}", t.getClass().getSimpleName());
        }

        return t;
    }
}
