package com.hayden.multiagentide.service;

import com.embabel.agent.api.annotation.support.ActionQosPropertyProvider;
import com.embabel.agent.api.common.ActionContext;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.core.ActionQos;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.spi.config.spring.AgentPlatformProperties;
import com.embabel.agent.spi.support.springai.SpringAiRetryPolicy;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.multiagentide.agent.ErrorDescriptor;
import com.hayden.multiagentide.agent.ErrorTemplates;
import com.hayden.multiagentide.agent.ResolvedTemplate;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.acp_cdc_ai.permission.IPermissionGate;
import com.hayden.multiagentide.agent.AgentInterfaces;
import com.hayden.multiagentide.agent.AgentInterfaces.MultiAgentIdeMetadata.AgentMetadata.AgentActionMetadata;
import com.hayden.multiagentide.agent.decorator.prompt.LlmCallDecorator;
import com.hayden.multiagentide.agent.decorator.request.DecorateRequestResults;
import com.hayden.multiagentide.embabel.EmbabelUtil;
import com.hayden.multiagentide.gate.PermissionGate;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.agent.AgentPretty;
import com.hayden.multiagentide.agent.AgentType;
import com.hayden.multiagentide.agent.BlackboardHistory;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.llm.AgentLlmExecutor;
import com.hayden.multiagentide.llm.LlmRunner;
import com.hayden.multiagentide.model.nodes.GraphNode;
import com.hayden.multiagentide.prompt.PromptContext;
import com.hayden.multiagentide.prompt.PromptContextFactory;
import com.hayden.multiagentide.prompt.PromptContributorService;
import com.hayden.multiagentide.prompt.contributor.NodeMappings;
import com.hayden.multiagentide.tool.ToolContext;
import com.hayden.utilitymodule.stream.StreamUtil;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * Colocates decorator/LLM/interrupt logic so callers don't duplicate the pipeline.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentExecutor implements AgentLlmExecutor {

    public record TemplateModelArgs<T extends AgentModels.AgentRequest>(
            T enrichedRequest,
            AgentModels.AgentRequest previousRequest,
            OperationContext operationContext
    ) {}

    @FunctionalInterface
    public interface TemplateModelProducer<T extends AgentModels.AgentRequest> {
        Map<String, Object> produce(TemplateModelArgs<T> args);
    }

    @Builder(toBuilder = true)
    public record AgentExecutorArgs<T extends AgentModels.AgentRequest, U extends AgentModels.AgentRouting, V extends AgentModels.AgentResult, RES>(
            Class<RES> responseClazz,
            AgentActionMetadata<T, U, V> agentActionMetadata,
            AgentModels.AgentRequest previousRequest,
            T currentRequest,
            TemplateModelProducer<T> templateModelProducer,
            OperationContext operationContext,
            ToolContext baseToolContext
    ) {}

    @Builder(toBuilder = true)
    public record ControllerExecutionArgs(
            AgentActionMetadata<AgentModels.AgentToControllerRequest, ?, ?> agentActionMetadata,
            AgentModels.AgentRequest previousRequest,
            AgentModels.AgentToControllerRequest currentRequest,
            Map<String, Object> templateModel,
            OperationContext operationContext,
            String interruptId,
            String originNodeId,
            Events.InterruptType interruptType
    ) {}

    private final DecorateRequestResults requestResultsDecorator;
    private final LlmRunner llmRunner;
    private final PromptContextFactory promptContextFactory;
    private final ObjectProvider<PromptContributorService> promptContributorServiceProvider;
    private final PermissionGate permissionGate;
    private final InterruptService interruptService;
    private final AgentPlatform agentPlatform;
    private final EventBus eventBus;
    private final GraphRepository graphRepository;
    private final List<LlmCallDecorator> llmCallDecorators;
    private final ActionQosPropertyProvider propertyProvider;


    public <T extends AgentModels.AgentRequest, U extends AgentModels.AgentRouting, V extends AgentModels.AgentResult> U run(
            AgentExecutorArgs<T, U, V, U> args
    ) {
        var meta = args.agentActionMetadata();
        var context = args.operationContext();

        // 1. Decorate request — ResultsRequest types get results+request decoration
        //    (which already includes the RequestDecorator chain internally);
        //    plain AgentRequest types get request decoration only.
        @SuppressWarnings("unchecked")
        T enrichedRequest = (args.currentRequest() instanceof AgentModels.ResultsRequest rq)
                ? (T) requestResultsDecorator.decorateResultsRequest(
                        new DecorateRequestResults.DecorateResultsRequestArgs<>(
                                rq, context,
                                meta.agentName(), meta.actionName(),
                                meta.methodName(), args.previousRequest()))
                : requestResultsDecorator.decorateRequest(
                        new DecorateRequestResults.DecorateRequestArgs<>(
                                args.currentRequest(), context, meta.agentName(), meta.actionName(),
                                meta.methodName(), args.previousRequest()));

        // 2. Produce template model from decorated request
        Map<String, Object> templateModel = args.templateModelProducer().produce(
                new TemplateModelArgs<>(enrichedRequest, args.previousRequest(), context));

        // 3. Build/decorate prompt context
        BlackboardHistory history = BlackboardHistory.getEntireBlackboardHistory(context);
        ErrorDescriptor errorDescriptor = history.errorType(PromptContext.chatId(enrichedRequest).value());
        DecoratorContext decoratorContext = new DecoratorContext(
                context, meta.agentName(), meta.actionName(), meta.methodName(),
                args.previousRequest(), enrichedRequest,
                Artifact.HashContext.defaultHashContext(), errorDescriptor
        );
        PromptContext promptContext = promptContextFactory.build(
                meta.agentType(), enrichedRequest, args.previousRequest(), enrichedRequest,
                history, meta.template(), templateModel, context, decoratorContext
        );
        promptContext = requestResultsDecorator.decoratePromptContext(
                new DecorateRequestResults.DecoratePromptContextArgs(promptContext, decoratorContext)
        );

        // 4. Build/decorate tool context
        ToolContext baseToolCtx = args.baseToolContext() != null ? args.baseToolContext() : ToolContext.empty();
        ToolContext toolContext = requestResultsDecorator.decorateToolContext(
                new DecorateRequestResults.DecorateToolArgs(
                        baseToolCtx, enrichedRequest, args.previousRequest(), context,
                        meta.agentName(), meta.actionName(), meta.methodName()
                )
        );

        // 5. Emit executor start event
        String sessionKey = promptContext.chatId() != null ? promptContext.chatId().value() : "";
        // requestContextId is the agentRequest's contextId — same across retries for the same request
        ArtifactKey requestContextId = promptContext.currentContextId() != null
                ? promptContext.currentContextId() : ArtifactKey.createRoot();
        // nodeId is unique per execution attempt (child of requestContextId)
        ArtifactKey nodeKey = requestContextId.createChild();
        String nodeId = nodeKey.value();
        eventBus.publish(new Events.AgentExecutorStartEvent(
                java.util.UUID.randomUUID().toString(), java.time.Instant.now(),
                nodeId, sessionKey, meta.actionName(), requestContextId.value()));

        // 6. Resolve template — use error-specific override if available
        ResolvedTemplate resolved = resolveTemplate(meta, errorDescriptor);

        // 7. Call LLM — error templates replace the template model entirely
        String effectiveTemplate = resolved.templateName();
        Map<String, Object> effectiveModel = switch (resolved) {
            case ResolvedTemplate.NoErrorTemplate _ -> templateModel;
            case ResolvedTemplate.ErrorTemplate et -> et.model();
        };
        U result = llmRunner.runWithTemplate(
                effectiveTemplate, promptContext, effectiveModel, toolContext,
                args.responseClazz(), context
        );

        // 8. Emit executor complete event — startNodeId links back to this start's unique nodeId
        eventBus.publish(new Events.AgentExecutorCompleteEvent(
                java.util.UUID.randomUUID().toString(), java.time.Instant.now(),
                nodeId, sessionKey, meta.actionName(), requestContextId.value(), nodeId));

        // 9. Decorate result
        DecorateRequestResults.DecorateRoutingArgs<U> uDecorateRoutingArgs = new DecorateRequestResults.DecorateRoutingArgs<U>(
                result, context, meta.agentName(),
                meta.actionName(), meta.methodName(), args.previousRequest()
        );

        U decorated = requestResultsDecorator.decorateRouting(uDecorateRoutingArgs);
        return decorated;
    }

    // =========================================================================
    // Dispatch agent methods: subprocess iteration + results decoration + LLM
    // =========================================================================

    /**
     * Dispatches discovery agent requests: iterates subprocesses, collects results,
     * decorates via ResultsRequestDecorator chain, then runs the consolidation LLM call.
     */
    public AgentModels.DiscoveryAgentDispatchRouting runDiscoveryDispatch(
            AgentModels.DiscoveryAgentRequests input,
            ActionContext context,
            AgentModels.DiscoveryOrchestratorRequest lastRequest
    ) {
        var meta = AgentInterfaces.MultiAgentIdeMetadata.AgentMetadata.DISPATCH_DISCOVERY_AGENTS;

        // Decorate the container request (e.g. WorktreeContextRequestDecorator sets worktree)
        input = requestResultsDecorator.decorateRequest(
                new DecorateRequestResults.DecorateRequestArgs<>(
                        input, context, meta.agentName(), meta.actionName(),
                        meta.methodName(), lastRequest));

        String goal = resolveDiscoveryGoal(context, input);
        List<AgentModels.DiscoveryAgentResult> discoveryResults = new ArrayList<>();
        var discoveryDispatchAgent = context.agentPlatform().agents()
                .stream().filter(a -> Objects.equals(a.getName(), AgentInterfaces.WORKFLOW_DISCOVERY_DISPATCH_SUBAGENT))
                .findAny().orElse(null);
        log.info("runDiscoveryDispatch: container contextId={} | requestCount={}",
                input.contextId() != null ? input.contextId().value() : "null",
                input.requests() != null ? input.requests().size() : 0);
        int idx = 0;
        for (AgentModels.DiscoveryAgentRequest request : StreamUtil.toStream(input.requests()).toList()) {
            if (request == null) {
                continue;
            }

            log.info("runDiscoveryDispatch: DISPATCHING child[{}] | childContextId={} | subdomain={}",
                    idx, request.contextId() != null ? request.contextId().value() : "null",
                    request.subdomainFocus());
            idx++;

            context.addObject(input);

            AgentModels.DiscoveryAgentRouting response = runSubProcess(
                    context, request, discoveryDispatchAgent, AgentModels.DiscoveryAgentRouting.class);

            if (response.interruptRequest() != null) {
                return decorateRouting(AgentModels.DiscoveryAgentDispatchRouting.builder()
                        .agentInterruptRequest(response.interruptRequest())
                        .build(), context, meta, lastRequest);
            }

            if (response.agentResult() != null)
                discoveryResults.add(response.agentResult());
        }

        var d = AgentModels.DiscoveryAgentResults.builder()
                .result(discoveryResults)
                .worktreeContext(input.worktreeContext())
                .build();

        return run(AgentExecutorArgs.<AgentModels.DiscoveryAgentResults, AgentModels.DiscoveryAgentDispatchRouting, AgentModels.AgentResult, AgentModels.DiscoveryAgentDispatchRouting>builder()
                .responseClazz(AgentModels.DiscoveryAgentDispatchRouting.class)
                .agentActionMetadata(meta)
                .previousRequest(lastRequest)
                .currentRequest(d)
                .templateModelProducer(a -> Map.of(
                        "goal", goal,
                        "discoveryResults", a.enrichedRequest().prettyPrint(new AgentPretty.AgentSerializationCtx.CollectorSerialization())))
                .operationContext(context)
                .baseToolContext(ToolContext.empty())
                .build());
    }

    /**
     * Dispatches planning agent requests: iterates subprocesses, collects results,
     * decorates via ResultsRequestDecorator chain, then runs the consolidation LLM call.
     */
    public AgentModels.PlanningAgentDispatchRouting runPlanningDispatch(
            AgentModels.PlanningAgentRequests input,
            ActionContext context,
            AgentModels.PlanningOrchestratorRequest lastRequest
    ) {
        var meta = AgentInterfaces.MultiAgentIdeMetadata.AgentMetadata.DISPATCH_PLANNING_AGENTS;

        // Decorate the container request (e.g. WorktreeContextRequestDecorator sets worktree)
        input = requestResultsDecorator.decorateRequest(
                new DecorateRequestResults.DecorateRequestArgs<>(
                        input, context, meta.agentName(), meta.actionName(),
                        meta.methodName(), lastRequest));

        String goal = input.goal();

        var planningDispatchAgent = context.agentPlatform().agents()
                .stream().filter(a -> Objects.equals(a.getName(), AgentInterfaces.WORKFLOW_PLANNING_DISPATCH_SUBAGENT))
                .findAny().orElse(null);

        List<AgentModels.PlanningAgentResult> planningResults = new ArrayList<>();

        for (AgentModels.PlanningAgentRequest request : input.requests()) {
            if (request == null) {
                continue;
            }

            context.addObject(input);

            AgentModels.PlanningAgentRouting response = runSubProcess(
                    context, request, planningDispatchAgent, AgentModels.PlanningAgentRouting.class);

            if (response.interruptRequest() != null)
                return decorateRouting(AgentModels.PlanningAgentDispatchRouting.builder()
                        .agentInterruptRequest(response.interruptRequest())
                        .build(), context, meta, lastRequest);

            if (response.agentResult() != null)
                planningResults.add(response.agentResult());
        }

        AgentModels.PlanningAgentResults planningAgentResults = AgentModels.PlanningAgentResults.builder()
                .planningAgentResults(planningResults)
                .worktreeContext(input.worktreeContext())
                .build();

        return run(AgentExecutorArgs.<AgentModels.PlanningAgentResults, AgentModels.PlanningAgentDispatchRouting, AgentModels.AgentResult, AgentModels.PlanningAgentDispatchRouting>builder()
                .responseClazz(AgentModels.PlanningAgentDispatchRouting.class)
                .agentActionMetadata(meta)
                .previousRequest(lastRequest)
                .currentRequest(planningAgentResults)
                .templateModelProducer(a -> {
                    var m = new HashMap<String, Object>();
                    Optional.ofNullable(goal).ifPresent(g -> m.put("goal", g));
                    m.put("planningResults", a.enrichedRequest().prettyPrint(new AgentPretty.AgentSerializationCtx.CollectorSerialization()));
                    return m;
                })
                .operationContext(context)
                .baseToolContext(ToolContext.empty())
                .build());
    }

    /**
     * Dispatches ticket agent requests: iterates subprocesses, collects results,
     * decorates via ResultsRequestDecorator chain, then runs the consolidation LLM call.
     */
    public AgentModels.TicketAgentDispatchRouting runTicketDispatch(
            AgentModels.TicketAgentRequests input,
            ActionContext context,
            AgentModels.TicketOrchestratorRequest lastRequest
    ) {
        var meta = AgentInterfaces.MultiAgentIdeMetadata.AgentMetadata.DISPATCH_TICKET_AGENTS;

        // Decorate the container request (e.g. WorktreeContextRequestDecorator sets worktree)
        input = requestResultsDecorator.decorateRequest(
                new DecorateRequestResults.DecorateRequestArgs<>(
                        input, context, meta.agentName(), meta.actionName(),
                        meta.methodName(), lastRequest));

        String goal = java.util.Optional.ofNullable(input.goal())
                .or(() -> java.util.Optional.ofNullable(lastRequest.goal()))
                .orElse("");

        List<AgentModels.TicketAgentResult> ticketResults = new ArrayList<>();

        var ticketDispatchAgent = context.agentPlatform().agents()
                .stream().filter(a -> Objects.equals(a.getName(), AgentInterfaces.WORKFLOW_TICKET_DISPATCH_SUBAGENT))
                .findAny().orElse(null);

        for (AgentModels.TicketAgentRequest request : input.requests()) {
            if (request == null) {
                continue;
            }

            context.addObject(input);

            AgentModels.TicketAgentRouting agentResult = runSubProcess(
                    context, request, ticketDispatchAgent, AgentModels.TicketAgentRouting.class);

            if (agentResult.interruptRequest() != null)
                return decorateRouting(AgentModels.TicketAgentDispatchRouting.builder()
                        .agentInterruptRequest(agentResult.interruptRequest())
                        .build(), context, meta, lastRequest);

            if (agentResult.agentResult() != null)
                ticketResults.add(agentResult.agentResult());
        }

        var ticketAgentResults = AgentModels.TicketAgentResults.builder()
                .ticketAgentResults(ticketResults)
                .worktreeContext(input.worktreeContext())
                .build();

        return run(AgentExecutorArgs.<AgentModels.TicketAgentResults, AgentModels.TicketAgentDispatchRouting, AgentModels.AgentResult, AgentModels.TicketAgentDispatchRouting>builder()
                .responseClazz(AgentModels.TicketAgentDispatchRouting.class)
                .agentActionMetadata(meta)
                .previousRequest(lastRequest)
                .currentRequest(ticketAgentResults)
                .templateModelProducer(a -> Map.of(
                        "goal", goal,
                        "ticketResults", a.enrichedRequest().prettyPrint(new AgentPretty.AgentSerializationCtx.CollectorSerialization())))
                .operationContext(context)
                .baseToolContext(ToolContext.empty())
                .build());
    }

    private <U extends AgentModels.AgentRouting> U decorateRouting(
            U routing, OperationContext context, AgentActionMetadata<?, U, ?> meta,
            AgentModels.AgentRequest lastRequest
    ) {
        return requestResultsDecorator.decorateRouting(
                new DecorateRequestResults.DecorateRoutingArgs<>(
                        routing, context, meta.agentName(), meta.actionName(),
                        meta.methodName(), lastRequest));
    }

    private <T> T runSubProcess(
            ActionContext context,
            Object request,
            com.embabel.agent.core.Agent agent,
            Class<T> outputClass
    ) {
        if (request == null) {
            return null;
        }
        try {
            context.addObject(request);
            T result = context.asSubProcess(outputClass, agent);
            context.hide(result);
            return result;
        } catch (Exception e) {
            eventBus.publish(Events.NodeErrorEvent.err("Error when running %s: %s"
                    .formatted(outputClass, request), new ArtifactKey(context.getAgentProcess().getId())));
            return null;
        }
    }

    private static String resolveDiscoveryGoal(ActionContext context, AgentModels.DiscoveryAgentRequests input) {
        String resolved = input != null
                ? input.prettyPrint(new AgentPretty.AgentSerializationCtx.GoalResolutionSerialization())
                : "";
        if (resolved != null && !resolved.isBlank()) {
            return resolved;
        }
        AgentModels.DiscoveryOrchestratorRequest orchestratorRequest =
                BlackboardHistory.getLastFromHistory(context, AgentModels.DiscoveryOrchestratorRequest.class);
        return firstNonBlank(
                orchestratorRequest != null ? orchestratorRequest.goal() : null,
                resolveRootGoal(context));
    }

    private static String resolveRootGoal(ActionContext context) {
        AgentModels.OrchestratorRequest rootRequest =
                BlackboardHistory.getLastFromHistory(context, AgentModels.OrchestratorRequest.class);
        return rootRequest != null ? rootRequest.goal() : "Continue workflow";
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }

    /**
     * Runs the full decoration pipeline (request → prompt context → prompt contributors)
     * but instead of calling the LLM, publishes an interrupt with the rendered prompt text
     * and blocks until the interrupt is resolved. Returns the resolution text.
     */
    public String controllerExecution(
            ControllerExecutionArgs args
    ) {
        var meta = args.agentActionMetadata();
        var context = args.operationContext();

        // 1. Decorate request
        AgentModels.AgentToControllerRequest enrichedRequest = requestResultsDecorator.decorateRequest(
                new DecorateRequestResults.DecorateRequestArgs<>(
                        args.currentRequest(), context, meta.agentName(), meta.actionName(),
                        meta.methodName(), args.previousRequest()
                ));

        // 2. Build/decorate prompt context
        BlackboardHistory history = BlackboardHistory.getEntireBlackboardHistory(context);
        ErrorDescriptor errorDescriptor = history.errorType(PromptContext.chatId(enrichedRequest).value());
        DecoratorContext decoratorContext = new DecoratorContext(
                context, meta.agentName(), meta.actionName(), meta.methodName(),
                args.previousRequest(), enrichedRequest,
                Artifact.HashContext.defaultHashContext(), errorDescriptor
        );
        PromptContext promptContext = promptContextFactory.build(
                meta.agentType(), enrichedRequest, args.previousRequest(), enrichedRequest,
                history, meta.template(), args.templateModel(), context, decoratorContext
        );
        promptContext = requestResultsDecorator.decoratePromptContext(
                new DecorateRequestResults.DecoratePromptContextArgs(promptContext, decoratorContext)
        );

        // 3. Assemble prompt: rendered template + prompt contributors
        String enrichedPrompt = assemblePrompt(promptContext, args.templateModel(), context);

        // 3b. Run LLM call decorators for side effects (e.g. PromptReceivedEvent emission)
        var llmCallContext = new LlmCallDecorator.LlmCallContext<>(promptContext, ToolContext.empty(), null, args.templateModel(), context);
        for (var decorator : llmCallDecorators) {
            llmCallContext = decorator.decorate(llmCallContext);
        }

        // 4. Publish interrupt (with interruptibleContext on origin node) and block
        return interruptService.publishAndAwaitControllerInterrupt(
                args.interruptId(),
                args.originNodeId(),
                args.interruptType(),
                enrichedPrompt
        );
    }

    @Builder(toBuilder = true)
    public record ControllerResponseArgs(
            String interruptId,
            String originNodeId,
            String message,
            String checklistAction,
            boolean expectResponse,
            String targetAgentKey
    ) {}

    public record ControllerResponseResult(
            boolean resolved,
            String errorMessage
    ) {
        public static ControllerResponseResult success() {
            return new ControllerResponseResult(true, null);
        }
        public static ControllerResponseResult error(String message) {
            return new ControllerResponseResult(false, message);
        }
    }

    /**
     * Mirror of {@link #controllerExecution}: processes the controller's response back to the agent
     * through the full decoration pipeline (request → prompt context → template + prompt contributors),
     * resolves the interrupt with the rendered text, and emits observability events.
     *
     * Key hierarchy: The interruptId IS the contextId of the original AgentToControllerRequest
     * (callingKey.createChild()). The controller response is a child of that conversation node:
     * - contextId = interruptKey.createChild() (new child under the conversation)
     * - chatId = interruptKey.parent() (the agent's session key — target of this response)
     * - sourceKey = same as contextId (controller's side of the conversation)
     */
    public ControllerResponseResult controllerResponseExecution(ControllerResponseArgs args) {
        // 1. Derive key hierarchy from the interruptId
        //    interruptId = callingKey.createChild() (set in AgentTopologyTools.callController)
        ArtifactKey interruptKey = new ArtifactKey(args.interruptId());
        ArtifactKey contextId = interruptKey.createChild();
        ArtifactKey agentChatId = interruptKey.parent().orElse(interruptKey);

        // 2. Resolve OperationContext from the origin node's artifact key
        ArtifactKey originKey = new ArtifactKey(args.originNodeId());
        OperationContext operationContext = EmbabelUtil.resolveOperationContext(
                agentPlatform, originKey, AgentInterfaces.AGENT_NAME_CONTROLLER_RESPONSE);

        // 3. Resolve target agent type from graph
        GraphNode targetNode = args.targetAgentKey() != null
                ? graphRepository.findById(args.targetAgentKey()).orElse(null)
                : graphRepository.findById(args.originNodeId()).orElse(null);
        AgentType targetAgentType = targetNode != null ? NodeMappings.agentTypeFromNode(targetNode) : null;

        if (operationContext == null) {
            return ControllerResponseResult.error(
                    "No OperationContext available for controller response to " + args.originNodeId()
                            + ". Cannot render response template.");
        }

        // 4. Retrieve the AgentToControllerRequest that initiated this conversation as lastRequest
        AgentModels.AgentRequest lastRequest = BlackboardHistory.getLastFromHistory(
                operationContext, AgentModels.AgentToControllerRequest.class);
        if (lastRequest == null) {
            return ControllerResponseResult.error(
                    "No AgentToControllerRequest found in blackboard history for " + args.originNodeId()
                            + ". Cannot process controller response without the originating request.");
        }

        // 5. Build template model
        Map<String, Object> templateModel = new HashMap<>();
        templateModel.put("message", args.message());
        templateModel.put("targetAgentKey", args.targetAgentKey() != null ? args.targetAgentKey() : args.originNodeId());
        if (targetAgentType != null) {
            templateModel.put("targetAgentType", targetAgentType.wireValue());
        }
        if (args.checklistAction() != null) {
            templateModel.put("checklistAction", args.checklistAction());
        }

        // 6. Build ControllerToAgentRequest with correct key hierarchy
        AgentModels.ChecklistAction checklistActionRecord = args.checklistAction() != null
                ? new AgentModels.ChecklistAction(args.checklistAction(), null, null)
                : null;
        ArtifactKey targetKey = args.targetAgentKey() != null ? new ArtifactKey(args.targetAgentKey()) : originKey;
        AgentModels.ControllerToAgentRequest request = AgentModels.ControllerToAgentRequest.builder()
                .contextId(contextId)
                .sourceKey(contextId)
                .targetAgentKey(targetKey)
                .targetAgentType(targetAgentType)
                .chatId(agentChatId)
                .message(args.message())
                .checklistAction(checklistActionRecord)
                .build();

        // 7. Render through decoration pipeline
        var meta = AgentInterfaces.MultiAgentIdeMetadata.AgentMetadata.RESPOND_TO_AGENT;

        AgentModels.ControllerToAgentRequest enrichedRequest = requestResultsDecorator.decorateRequest(
                new DecorateRequestResults.DecorateRequestArgs<>(
                        request, operationContext, meta.agentName(), meta.actionName(),
                        meta.methodName(), lastRequest
                ));

        BlackboardHistory history = BlackboardHistory.getEntireBlackboardHistory(operationContext);
        ErrorDescriptor errorDescriptor = history.errorType(PromptContext.chatId(enrichedRequest).value());
        DecoratorContext decoratorContext = new DecoratorContext(
                operationContext, meta.agentName(), meta.actionName(), meta.methodName(),
                lastRequest, enrichedRequest,
                Artifact.HashContext.defaultHashContext(), errorDescriptor
        );
        PromptContext promptContext = promptContextFactory.build(
                meta.agentType(), enrichedRequest, lastRequest, enrichedRequest,
                history, meta.template(), templateModel, operationContext, decoratorContext
        );
        promptContext = requestResultsDecorator.decoratePromptContext(
                new DecorateRequestResults.DecoratePromptContextArgs(promptContext, decoratorContext)
        );

        String resolvedMessage = assemblePrompt(promptContext, templateModel, operationContext);

        // 7b. Run LLM call decorators for side effects (e.g. PromptReceivedEvent emission)
        var llmCallContext = new LlmCallDecorator.LlmCallContext<>(promptContext, ToolContext.empty(), null, templateModel, operationContext);
        for (var decorator : llmCallDecorators) {
            llmCallContext = decorator.decorate(llmCallContext);
        }

        // 8. Resolve the interrupt with the fully rendered text
        boolean resolved = permissionGate.resolveInterrupt(
                args.interruptId(),
                IPermissionGate.ResolutionType.RESOLVED,
                resolvedMessage,
                (IPermissionGate.InterruptResult) null
        );

        if (!resolved) {
            return ControllerResponseResult.error("Failed to resolve interrupt");
        }

        // 9. Emit observability event
        eventBus.publish(new Events.AgentCallEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                args.originNodeId(),
                Events.AgentCallEventType.RETURNED,
                "controller",
                null,
                args.originNodeId(),
                null,
                List.of(),
                null,
                null,
                args.message(),
                null,
                args.checklistAction()
        ));

        return ControllerResponseResult.success();
    }

    // =========================================================================
    // Direct LLM execution: callers that already build their own contexts
    // =========================================================================

    /**
     * Execute an LLM call with agent executor semantics (events, error descriptors, error templates)
     * for callers that already build their own PromptContext and ToolContext.
     * <p>
     * This is the lighter-weight entry point compared to {@link #run(AgentExecutorArgs)} —
     * it skips request/prompt/tool/result decoration but provides event emission,
     * error descriptor resolution from blackboard history, and error-specific template overrides.
     */
    @Override
    public <T> T runDirect(DirectExecutorArgs<T> args) {
        ActionQos qos = Optional.ofNullable(propertyProvider.getBound("embabel.agent.platform.direct-executions"))
                .orElse(new AgentPlatformProperties.ActionQosProperties.ActionProperties())
                .toActionQos(new ActionQos());

        RetryTemplate retryTemplate = qos.retryTemplate(args.agentName() + "-" + args.actionName() + "-" + args.methodName());

        retryTemplate.setRetryPolicy(new SpringAiRetryPolicy(qos.getMaxAttempts(), Set.of("rate limit", "rate-limit")));

        return retryTemplate.execute(ctx -> {
            DirectExecutorArgs<T> effective = args;
            if (ctx.getRetryCount() > 0 && args.refresh() != null) {
                effective = args.refresh().get();
            }
            return doRunDirect(effective);
        });
    }

    private <T> T doRunDirect(DirectExecutorArgs<T> args) {
        var operationContext = args.operationContext();
        var promptContext = args.promptContext();

        // 1. Resolve error descriptor from blackboard
        BlackboardHistory history = BlackboardHistory.getEntireBlackboardHistory(operationContext);
        String chatIdValue = promptContext.chatId() != null ? promptContext.chatId().value() : "";
        ErrorDescriptor errorDescriptor = history.errorType(chatIdValue);

        // 2. Emit start event
        ArtifactKey requestContextId = promptContext.currentContextId() != null
                ? promptContext.currentContextId() : ArtifactKey.createRoot();
        ArtifactKey nodeKey = requestContextId.createChild();
        String nodeId = nodeKey.value();
        String sessionKey = chatIdValue;
        eventBus.publish(new Events.AgentExecutorStartEvent(
                UUID.randomUUID().toString(), java.time.Instant.now(),
                nodeId, sessionKey, args.actionName(), requestContextId.value()));

        // 3. Resolve error template via AgentActionMetadata lookup
        var meta = AgentInterfaces.MultiAgentIdeMetadata.AgentMetadata.findByAction(
                args.agentName(), args.actionName(), args.methodName());

        String effectiveTemplate = args.template();

        Map<String, Object> effectiveModel = args.templateModel();

        ResolvedTemplate resolved = resolveTemplate(meta, errorDescriptor);
        effectiveTemplate = resolved.templateName() != null ? resolved.templateName() : effectiveTemplate;
        if (resolved instanceof ResolvedTemplate.ErrorTemplate et) {
            effectiveModel = et.model();
        }

        // 4. Call LLM
        T result = llmRunner.runWithTemplate(
                effectiveTemplate, promptContext, effectiveModel,
                args.toolContext() != null ? args.toolContext() : ToolContext.empty(),
                args.responseClazz(), operationContext
        );

        // 5. Emit complete event
        eventBus.publish(new Events.AgentExecutorCompleteEvent(
                UUID.randomUUID().toString(), java.time.Instant.now(),
                nodeId, sessionKey, args.actionName(), requestContextId.value(), nodeId));

        return result;
    }

    /**
     * Resolves the effective template based on error state. Returns a {@link ResolvedTemplate}
     * that either preserves the normal template (NoErrorTemplate) or replaces it with an
     * error-specific template and model (ErrorTemplate).
     *
     * <p>Reads template names from the action's {@link ErrorTemplates}; when a field is null,
     * falls back to the default template. The error model contains {@code errorDetail}
     * from the throwable message.
     */
    ResolvedTemplate resolveTemplate(AgentActionMetadata<?, ?, ?> meta, ErrorDescriptor errorDescriptor) {
        if (errorDescriptor == null)
            return new ResolvedTemplate.NoErrorTemplate(meta.template());

        ErrorTemplates templates;
        if (meta == null)  {
            log.error("Didn't find agent action metadata. Should have found one!");
            templates = ErrorTemplates.DEFAULT;
        } else {
            templates = meta.errorTemplates();
        }


        String retryTemplate = switch (errorDescriptor) {
            case ErrorDescriptor.CompactionError ce ->
                    ce.compactionStatus() == ErrorDescriptor.CompactionStatus.MULTIPLE
                            ? templates.compactionMultipleTemplate()
                            : templates.compactionFirstTemplate();
            case ErrorDescriptor.ParseError _ -> templates.parseErrorTemplate();
            case ErrorDescriptor.TimeoutError _ -> templates.timeoutTemplate();
            case ErrorDescriptor.UnparsedToolCallError _ -> templates.unparsedToolCallTemplate();
            case ErrorDescriptor.NullResultError _ -> templates.nullResultTemplate();
            case ErrorDescriptor.IncompleteJsonError _ -> templates.incompleteJsonTemplate();
            case ErrorDescriptor.NoError _ -> null;
        };

        if (retryTemplate == null) {
            return new ResolvedTemplate.NoErrorTemplate(meta.template());
        }

        String errorDetail = errorDescriptor.errorDetail();
        Map<String, Object> errorModel = Map.of("errorDetail", errorDetail != null ? errorDetail : "");
        return new ResolvedTemplate.ErrorTemplate(retryTemplate, errorModel);
    }

    /**
     * Assembles the full prompt text by delegating to PromptContributorService.
     */
    private String assemblePrompt(PromptContext promptContext, Map<String, Object> templateModel, OperationContext context) {
        PromptContributorService contributorService = promptContributorServiceProvider.getIfAvailable();
        if (contributorService != null) {
            return contributorService.assemblePrompt(promptContext, templateModel, context);
        }
        // Fallback if service unavailable — just return goal or empty
        if (promptContext.currentRequest() != null) {
            String goal = promptContext.currentRequest().goalExtraction();
            if (goal != null && !goal.isBlank()) {
                return goal;
            }
        }
        return "";
    }

}
