package com.hayden.multiagentide.agent.decorator.request;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.multiagentide.agent.decorator.prompt.PromptContextDecorator;
import com.hayden.multiagentide.agent.decorator.tools.ToolContextDecorator;
import com.hayden.multiagentide.agent.decorator.result.FinalResultDecorator;
import com.hayden.multiagentide.agent.decorator.result.ResultDecorator;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.agent.BlackboardHistory;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.prompt.PromptContext;
import com.hayden.multiagentide.tool.ToolContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Central decorator hub.  All static implementations that previously lived in
 * {@code AgentInterfaces} now live here as {@code public static} methods, keeping
 * the list-passing API intact for callers that still manage their own decorator lists
 * (e.g. the dispatch sub-agents in {@code AgentInterfaces}).
 *
 * <p>The instance methods accept arg-record wrappers and use the Spring-managed
 * decorator lists so callers can omit their own list fields entirely.
 */
@Component
public class DecorateRequestResults {

    // ---- autowired decorator lists (instance API) ----

    @Autowired(required = false)
    @Lazy
    private List<RequestDecorator> requestDecorators = new ArrayList<>();

    @Autowired(required = false)
    @Lazy
    private List<PromptContextDecorator> promptContextDecorators = new ArrayList<>();

    @Autowired(required = false)
    @Lazy
    private List<ToolContextDecorator> toolContextDecorators = new ArrayList<>();

    @Autowired(required = false)
    @Lazy
    private List<ResultDecorator> resultDecorators = new ArrayList<>();

    @Autowired(required = false)
    @Lazy
    private List<FinalResultDecorator> finalResultDecorators = new ArrayList<>();

    @Autowired(required = false)
    @Lazy
    private List<ResultsRequestDecorator> resultsRequestDecorators = new ArrayList<>();

    // =========================================================================
    // Arg records
    // =========================================================================

    public record DecorateRequestArgs<T extends AgentModels.AgentRequest>(
            T request,
            OperationContext context,
            String agentName,
            String actionName,
            String methodName,
            AgentModels.AgentRequest lastRequest
    ) {}

    public record DecoratePromptContextArgs(
            PromptContext promptContext,
            DecoratorContext decoratorContext
    ) {}

    public record DecorateToolArgs(
            ToolContext toolContext,
            AgentModels.AgentRequest enrichedRequest,
            AgentModels.AgentRequest lastRequest,
            OperationContext context,
            String agentName,
            String actionName,
            String methodName
    ) {}

    public record DecorateResultArgs<T extends AgentModels.AgentResult>(
            T result,
            OperationContext context,
            String agentName,
            String actionName,
            String methodName,
            Artifact.AgentModel lastRequest
    ) {}

    public record DecorateRoutingArgs<T extends AgentModels.AgentRouting>(
            T routing,
            OperationContext context,
            String agentName,
            String actionName,
            String methodName,
            Artifact.AgentModel lastRequest
    ) {}

    public record DecorateFinalResultArgs<T extends AgentModels.AgentResult>(
            T result,
            AgentModels.AgentRequest enrichedRequest,
            AgentModels.AgentRequest lastRequest,
            OperationContext context,
            String agentName,
            String actionName,
            String methodName
    ) {}

    public record DecorateResultsRequestArgs<T extends AgentModels.ResultsRequest>(
            T resultsRequest,
            OperationContext context,
            String agentName,
            String actionName,
            String methodName,
            AgentModels.AgentRequest lastRequest
    ) {}

    // =========================================================================
    // Instance methods (use Spring-managed lists)
    // =========================================================================

    public <T extends AgentModels.AgentRequest> T decorateRequest(DecorateRequestArgs<T> args) {
        return decorateRequest(
                args.request,
                args.context,
                requestDecorators,
                args.agentName,
                args.actionName,
                args.methodName,
                args.lastRequest);
    }

    public PromptContext decoratePromptContext(DecoratePromptContextArgs args) {
        return decoratePromptContext(
                args.promptContext,
                promptContextDecorators,
                args.decoratorContext());
    }

    public ToolContext decorateToolContext(DecorateToolArgs args) {
        return decorateToolContext(
                args.toolContext,
                args.enrichedRequest,
                args.lastRequest,
                args.context,
                toolContextDecorators,
                args.agentName,
                args.actionName,
                args.methodName);
    }

    public <T extends AgentModels.AgentResult> T decorateResult(DecorateResultArgs<T> args) {
        return decorateResult(
                args.result,
                args.context,
                resultDecorators,
                args.agentName,
                args.actionName,
                args.methodName,
                args.lastRequest);
    }

    public <T extends AgentModels.AgentRouting> T decorateRouting(DecorateRoutingArgs<T> args) {
        return decorateRouting(
                args.routing,
                args.context,
                resultDecorators,
                args.agentName,
                args.actionName,
                args.methodName,
                args.lastRequest);
    }

    public <T extends AgentModels.AgentResult> T decorateFinalResult(DecorateFinalResultArgs<T> args) {
        return decorateFinalResult(
                args.result,
                args.enrichedRequest,
                args.lastRequest,
                args.context,
                finalResultDecorators,
                args.agentName,
                args.actionName,
                args.methodName);
    }

    public <T extends AgentModels.ResultsRequest> T decorateResultsRequest(DecorateResultsRequestArgs<T> args) {
        return decorateResultsRequest(
                args.resultsRequest,
                args.context,
                resultsRequestDecorators,
                requestDecorators,
                args.agentName,
                args.actionName,
                args.methodName,
                args.lastRequest);
    }

    // =========================================================================
    // Static implementations (moved from AgentInterfaces)
    // =========================================================================

    public static PromptContext decoratePromptContext(
            PromptContext promptContext,
            List<? extends PromptContextDecorator> decorators,
            DecoratorContext decoratorContext
    ) {
        if (promptContext == null || decorators == null || decorators.isEmpty()) {
            return promptContext;
        }
        List<? extends PromptContextDecorator> sortedDecorators = decorators.stream()
                .filter(d -> d != null)
                .sorted(Comparator.comparingInt(PromptContextDecorator::order))
                .toList();
        PromptContext decorated = promptContext;
        for (PromptContextDecorator decorator : sortedDecorators) {
            decorated = decorator.decorate(decorated, decoratorContext);
        }
        return decorated;
    }

    public static <T extends AgentModels.AgentRouting> T decorateRouting(
            T routing,
            OperationContext context,
            List<? extends ResultDecorator> decorators,
            String agentName,
            String actionName,
            Artifact.AgentModel lastRequest
    ) {
        return decorateRouting(routing, context, decorators, agentName, actionName, "", lastRequest);
    }

    public static <T extends AgentModels.AgentRouting> T decorateRouting(
            T routing,
            OperationContext context,
            List<? extends ResultDecorator> decorators,
            String agentName,
            String actionName,
            String methodName,
            Artifact.AgentModel lastRequest
    ) {
        if (routing == null || decorators == null || decorators.isEmpty()) {
            return routing;
        }

        AgentModels.AgentRequest currentRequest = BlackboardHistory.getLastFromHistory(context, AgentModels.AgentRequest.class);;
        DecoratorContext decoratorContext = new DecoratorContext(
                context, agentName, actionName, methodName, lastRequest, currentRequest
        );
        T decorated = routing;
        List<? extends ResultDecorator> sortedDecorators = decorators.stream()
                .filter(d -> d != null)
                .sorted(Comparator.comparingInt(ResultDecorator::order))
                .toList();
        for (ResultDecorator decorator : sortedDecorators) {
            decorated = decorator.decorate(decorated, decoratorContext);
        }
        return decorated;
    }

    public static <T extends AgentModels.AgentResult> T decorateResult(
            T result,
            OperationContext context,
            List<? extends ResultDecorator> decorators,
            String agentName,
            String actionName,
            String methodName,
            Artifact.AgentModel lastRequest
    ) {
        if (result == null || decorators == null || decorators.isEmpty()) {
            return result;
        }
        DecoratorContext decoratorContext = new DecoratorContext(
                context, agentName, actionName, methodName, lastRequest, result
        );
        List<? extends ResultDecorator> sortedDecorators = decorators.stream()
                .filter(d -> d != null)
                .sorted(Comparator.comparingInt(ResultDecorator::order))
                .toList();
        T decorated = result;
        for (ResultDecorator decorator : sortedDecorators) {
            decorated = decorator.decorate(decorated, decoratorContext);
        }
        return decorated;
    }

    public static <T extends AgentModels.AgentRequest> T decorateRequestResult(
            T result,
            OperationContext context,
            List<? extends ResultDecorator> decorators,
            String agentName,
            String actionName,
            String methodName,
            Artifact.AgentModel lastRequest
    ) {
        if (result == null || decorators == null || decorators.isEmpty()) {
            return result;
        }
        DecoratorContext decoratorContext = new DecoratorContext(
                context, agentName, actionName, methodName, lastRequest, result
        );
        List<? extends ResultDecorator> sortedDecorators = decorators.stream()
                .filter(d -> d != null)
                .sorted(Comparator.comparingInt(ResultDecorator::order))
                .toList();
        T decorated = result;
        for (ResultDecorator decorator : sortedDecorators) {
            decorated = decorator.decorateRequestResult(decorated, decoratorContext);
        }
        return decorated;
    }

    /**
     * @deprecated Use {@link #decorateRouting(AgentModels.Routing, OperationContext, List, String, String, Artifact.AgentModel)} instead.
     */
    @Deprecated
    public static <T extends AgentModels.AgentRouting> T decorateRouting(
            T routing,
            OperationContext context,
            List<ResultDecorator> decorators,
            Artifact.AgentModel lastRequest
    ) {
        return decorateRouting(routing, context, decorators, "", "", "", lastRequest);
    }

    public static ToolContext decorateToolContext(
            ToolContext toolContext,
            AgentModels.AgentRequest enrichedRequest,
            AgentModels.AgentRequest lastRequest,
            OperationContext context,
            List<ToolContextDecorator> decorators,
            String agentName,
            String actionName,
            String methodName
    ) {
        if (toolContext == null) {
            toolContext = ToolContext.of();
        }
        if (decorators == null || decorators.isEmpty()) {
            return toolContext;
        }
        DecoratorContext decoratorContext = new DecoratorContext(
                context, agentName, actionName, methodName, lastRequest, enrichedRequest);
        for (var d : decorators) {
            toolContext = d.decorate(toolContext, decoratorContext);
        }
        return toolContext;
    }

    public static <T extends AgentModels.AgentResult> T decorateFinalResult(
            T finalResult,
            AgentModels.AgentRequest enrichedRequest,
            AgentModels.AgentRequest lastRequest,
            OperationContext context,
            List<FinalResultDecorator> decorators,
            String agentName,
            String actionName,
            String methodName
    ) {
        if (finalResult == null || decorators == null || decorators.isEmpty()) {
            return finalResult;
        }
        DecoratorContext decoratorContext = new DecoratorContext(
                context, agentName, actionName, methodName, lastRequest, enrichedRequest);
        FinalResultDecorator.FinalResultDecoratorContext finalCtx =
                new FinalResultDecorator.FinalResultDecoratorContext(enrichedRequest, decoratorContext);
        List<FinalResultDecorator> sortedDecorators = decorators.stream()
                .filter(d -> d != null)
                .sorted(Comparator.comparingInt(FinalResultDecorator::order))
                .toList();
        T decorated = finalResult;
        for (FinalResultDecorator decorator : sortedDecorators) {
            try {
                decorated = decorator.decorateFinalResult(decorated, finalCtx);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(DecorateRequestResults.class)
                        .error("FinalResultDecorator {} failed for agent={} action={} method={}",
                                decorator.getClass().getName(), agentName, actionName, methodName, e);
            }
        }
        return decorated;
    }

    public static <T extends AgentModels.AgentRequest> T decorateRequest(
            T request,
            OperationContext context,
            List<? extends RequestDecorator> decorators,
            String agentName,
            String actionName,
            String methodName,
            AgentModels.AgentRequest lastRequest
    ) {
        if (request == null || decorators == null || decorators.isEmpty()) {
            return request;
        }
        DecoratorContext decoratorContext = new DecoratorContext(
                context, agentName, actionName, methodName, lastRequest, request
        );
        List<? extends RequestDecorator> sortedDecorators = decorators.stream()
                .filter(d -> d != null)
                .sorted(Comparator.comparingInt(RequestDecorator::order))
                .toList();
        T decorated = request;
        for (RequestDecorator decorator : sortedDecorators) {
            decorated = decorator.decorate(decorated, decoratorContext);
        }
        return decorated;
    }

    public static <T extends AgentModels.ResultsRequest> T decorateResultsRequest(
            T resultsRequest,
            OperationContext context,
            List<? extends ResultsRequestDecorator> decorators,
            List<? extends RequestDecorator> r,
            String agentName,
            String actionName,
            String methodName,
            AgentModels.AgentRequest lastRequest
    ) {
        if (resultsRequest == null || decorators == null || decorators.isEmpty()) {
            return resultsRequest;
        }
        DecoratorContext decoratorContext = new DecoratorContext(
                context, agentName, actionName, methodName, lastRequest, resultsRequest
        );
        List<? extends ResultsRequestDecorator> sortedDecorators = decorators.stream()
                .filter(d -> d != null)
                .sorted(Comparator.comparingInt(ResultsRequestDecorator::order))
                .toList();
        T decorated = resultsRequest;
        for (ResultsRequestDecorator decorator : sortedDecorators) {
            decorated = decorator.decorate(decorated, decoratorContext);
        }
        List<? extends RequestDecorator> sortedRequest = r.stream()
                .filter(d -> d != null)
                .sorted(Comparator.comparingInt(RequestDecorator::order))
                .filter(c -> !(c instanceof ResultsRequestDecorator))
                .toList();
        for (RequestDecorator decorator : sortedRequest) {
            decorated = decorator.decorate(decorated, decoratorContext);
        }
        return decorated;
    }
}
