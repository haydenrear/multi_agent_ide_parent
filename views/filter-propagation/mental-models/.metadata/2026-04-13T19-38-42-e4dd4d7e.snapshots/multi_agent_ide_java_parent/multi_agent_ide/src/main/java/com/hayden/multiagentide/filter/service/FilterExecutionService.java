package com.hayden.multiagentide.filter.service;

import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.core.AgentPlatform;
import com.hayden.acp_cdc_ai.acp.config.AcpChatOptionsString;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.events.*;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.multiagentide.agent.AgentInterfaces;
import com.hayden.multiagentide.agent.decorator.request.DecorateRequestResults;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.artifacts.ExecutionScopeService;
import com.hayden.multiagentide.filter.model.AiPathFilter;
import com.hayden.multiagentide.filter.model.Filter;
import com.hayden.multiagentide.filter.model.FilterSource;
import com.hayden.multiagentide.filter.model.PathFilter;
import com.hayden.multiagentide.filter.model.executor.*;
import com.hayden.multiagentide.llm.AgentLlmExecutor;
import com.hayden.multiagentide.filter.repository.FilterDecisionRecordEntity;
import com.hayden.multiagentide.filter.repository.FilterDecisionRecordRepository;
import com.hayden.multiagentide.filter.repository.PolicyRegistrationEntity;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.agent.AgentType;
import com.hayden.multiagentide.agent.BlackboardHistory;
import com.hayden.multiagentide.filter.model.*;
import com.hayden.acp_cdc_ai.acp.filter.Instruction;
import com.hayden.acp_cdc_ai.acp.filter.InstructionJson;
import com.hayden.multiagentide.filter.model.layer.FilterContext;
import com.hayden.multiagentide.filter.model.layer.GraphEventObjectContext;
import com.hayden.multiagentide.filter.model.layer.DefaultPathFilterContext;
import com.hayden.multiagentide.filter.model.layer.PromptContributorContext;
import com.hayden.multiagentide.filter.model.policy.PolicyLayerBinding;
import com.hayden.multiagentide.prompt.PromptContext;
import com.hayden.multiagentide.tool.ToolContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Executes applicable active filters for a given layer context.
 * Records FilterDecisionRecord entries for each execution.
 * Deterministic ordering: priority ascending, then policy ID tie-breaker.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FilterExecutionService {

    public static final String AI_FILTER_AGENT_NAME = "ai-filter";
    public static final String AI_FILTER_ACTION_NAME = "path-filter";
    public static final String AI_FILTER_METHOD_NAME = "runAiFilter";
    public static final String AI_FILTER_TEMPLATE_NAME = AiFilterTool.TEMPLATE_NAME;

    private final PolicyDiscoveryService policyDiscoveryService;
    private final FilterDecisionRecordRepository decisionRecordRepository;
    private final ObjectMapper objectMapper;
    private final ExecutionScopeService executionScopeService;
    private final AgentPlatform agentPlatform;
    private final AiFilterSessionResolver aiFilterSessionResolver;
    private final DecorateRequestResults decorateRequestResults;

    @Autowired @Lazy
    private EventBus eventBus;


    @Autowired
    private AiFilterToolHydration aiFilterToolHydration;

    /**
     * Execute all applicable active filters of the requested kinds for a layer against a payload.
     * Returns the final output after all filters have been applied.
     */
    public <T> FilterResult<T> executeFilters(String layerId,
                                              T payload,
                                              FilterContext ctx,
                                              FilterSource source,
                                              Set<FilterEnums.FilterKind> allowedKinds) {
        if (layerId == null || layerId.isBlank() || source == null) {
            return new FilterResult<>(payload, new FilterDescriptor.NoOpFilterDescriptor());
        }

        List<PolicyRegistrationEntity> policies = policyDiscoveryService.getActivePoliciesByLayer(layerId);

        policies.sort(Comparator.comparingInt(this::extractPriority).thenComparing(PolicyRegistrationEntity::getRegistrationId));

        FilterResult<T> current = new FilterResult<>(payload, new FilterDescriptor.NoOpFilterDescriptor());

        for (PolicyRegistrationEntity policy : policies) {
            if (current.t() == null) {
                break;
            }
            FilterEnums.FilterKind kind = parseFilterKind(policy);

            if (kind == null) {
                continue;
            }

            if (!isAllowedKind(kind, allowedKinds)) {
                continue;
            }

            if (!matchesBinding(policy, layerId, source)) {
                continue;
            }

            try {
                AppliedFilterResult applied = applyPolicy(policy, ctx, current.t(), source);
                FilterDescriptor appliedDescriptor = safeDescriptor(applied.descriptor());
                List<Throwable> descriptorErrors = appliedDescriptor.errors();
                Throwable descriptorError = descriptorErrors.isEmpty() ? null : descriptorErrors.getFirst();
                FilterEnums.FilterAction decisionAction =
                        descriptorError == null ? applied.action() : FilterEnums.FilterAction.ERROR;

                current = new FilterResult<>(
                        (T) applied.output(),
                        safeDescriptor(current.descriptor()).and(appliedDescriptor)
                );
                recordDecision(
                        policy,
                        layerId,
                        decisionAction,
                        applied.input(),
                        applied.output(),
                        appliedDescriptor.instructions(),
                        ctx,
                        descriptorError == null ? applied.error() : descriptorError.getMessage()
                );
                if (descriptorError != null) {
                    log.error("Filter execution error descriptor for policy {}", policy.getRegistrationId(), descriptorError);
                    publishFilterErrorEvent(policy.getRegistrationId(), layerId, source, descriptorError);
                }
            } catch (Exception e) {
                log.error("Filter execution failed for policy {}", policy.getRegistrationId(), e);
                publishFilterErrorEvent(policy.getRegistrationId(), layerId, source, e);
                recordDecision(
                        policy,
                        layerId,
                        FilterEnums.FilterAction.ERROR,
                        current.t(),
                        null,
                        List.of(),
                        ctx,
                        e.getMessage()
                );
            }
        }

        return current;
    }

    private AppliedFilterResult applyPolicy(PolicyRegistrationEntity policy,
                                            FilterContext filterContext,
                                            Object payload,
                                            FilterSource source) throws Exception {
        var filter = objectMapper.readValue(policy.getFilterJson(), Filter.class);

        return switch (filter) {
            case PathFilter p -> {
                if (!(payload instanceof String payloadString))  {
                    yield new AppliedFilterResult(payload, payload, FilterEnums.FilterAction.PASSTHROUGH);
                }

                if (!(filterContext instanceof FilterContext.PathFilterContext)) {
                    filterContext = new DefaultPathFilterContext(filterContext == null ? null : filterContext.layerId(), filterContext);
                }

                DefaultPathFilterContext pathFilterContext = (DefaultPathFilterContext) filterContext;

                yield switch (source) {
                    case FilterSource.GraphEventSource ges ->
                            applyPathFilter(policy, p, pathFilterContext, payloadString);
                    case FilterSource.PromptContributorSource pcs ->
                            applyPathFilter(policy, p, pathFilterContext, payloadString);
                };
            }
            case AiPathFilter aiPathFilter -> {
                if (!(payload instanceof String payloadString)) {
                    yield new AppliedFilterResult(payload, payload, FilterEnums.FilterAction.PASSTHROUGH);
                }

                var aiResult = runAiFilter(aiPathFilter, filterContext, payloadString, policy);

                if (aiResult.isEmpty()) {
                    yield new AppliedFilterResult(payload, payload, FilterEnums.FilterAction.PASSTHROUGH);
                }

                AiPathFilter.AiPathFilterResult result = aiResult.get();
                var action = toAction(payloadString, result.r());
                FilterDescriptor policyDescriptor = descriptorForPolicy(
                        policy,
                        aiPathFilter,
                        action,
                        result.instructions(),
                        filterContext,
                        "AI_PATH"
                );

                yield new AppliedFilterResult(
                        payloadString,
                        result.r(),
                        action,
                        policyDescriptor.and(safeDescriptor(result.descriptor()))
                );
            }
        };
    }

    private AppliedFilterResult applyPathFilter(PolicyRegistrationEntity policy,
                                                PathFilter pathFilter,
                                                DefaultPathFilterContext filterContext,
                                                String payload) {
        var output = pathFilter.apply(payload, filterContext);
        var action = toAction(payload, output.r());
        FilterDescriptor policyDescriptor = descriptorForPolicy(
                policy,
                pathFilter,
                action,
                output.instructions(),
                filterContext,
                "PATH"
        );

        return new AppliedFilterResult(
                payload,
                output.r(),
                action,
                policyDescriptor.and(safeDescriptor(output.descriptor()))
        );
    }


    private Optional<AiPathFilter.AiPathFilterResult> runAiFilter(AiPathFilter p,
                                                                  FilterContext filterContext,
                                                                  String payload,
                                                                  PolicyRegistrationEntity policy) {
        var aiExecutor = p.executor();
        aiFilterToolHydration.hydrate(aiExecutor);

        PromptContext promptContext = getPromptContext(filterContext);
        OperationContext operationContext = getOpContext(filterContext, promptContext);

        if (promptContext.operationContext() == null)
            promptContext = promptContext.toBuilder().operationContext(operationContext).build();

        if (promptContext == null || operationContext == null) {
            log.warn("AI filter for policy {} skipped: no PromptContext/OperationContext available in filter context.",
                    policy.getRegistrationId());
            return Optional.empty();
        }

        var argsAndRequest = buildFilterArgs(p, aiExecutor, filterContext, promptContext, operationContext, payload, policy);

        FilterContext.AiFilterContext aiFilterContext = FilterContext.AiFilterContext.builder()
                .filterContext(filterContext)
                .templateName(AI_FILTER_TEMPLATE_NAME)
                .promptContext(argsAndRequest.args().promptContext())
                .model(argsAndRequest.args().templateModel())
                .toolContext(argsAndRequest.args().toolContext())
                .responseClass(AgentModels.AiFilterResult.class)
                .context(operationContext)
                .directExecutorArgs(argsAndRequest.args()
                        .toBuilder()
                        .refresh(() -> buildFilterArgs(p, aiExecutor, filterContext, getPromptContext(filterContext),
                                getOpContext(filterContext, getPromptContext(filterContext)), payload, policy).args())
                        .build())
                .build();

        var result = p.apply(argsAndRequest.decoratedRequest(), aiFilterContext);

        if (result.res() != null) {
            return Optional.of(
                    result.toBuilder()
                            .res(decorateRequestResults.decorateResult(
                                    new DecorateRequestResults.DecorateResultArgs<>(
                                            result.res(),
                                            operationContext,
                                            AI_FILTER_AGENT_NAME,
                                            AI_FILTER_ACTION_NAME,
                                            AI_FILTER_METHOD_NAME,
                                            argsAndRequest.decoratedRequest())))
                            .build());
        }

        return Optional.of(result);
    }

    private record FilterArgsAndRequest(
            AgentLlmExecutor.DirectExecutorArgs<AgentModels.AiFilterResult> args,
            AgentModels.AiFilterRequest decoratedRequest
    ) {}

    private FilterArgsAndRequest buildFilterArgs(
            AiPathFilter p,
            AiFilterTool<?, ?> aiExecutor,
            FilterContext filterContext,
            PromptContext promptContext,
            OperationContext operationContext,
            String payload,
            PolicyRegistrationEntity policy
    ) {
        AgentModels.AgentRequest parentRequest = promptContext.currentRequest();

        ArtifactKey chatKey = aiFilterSessionResolver.resolveSessionKey(
                policy.getRegistrationId(),
                aiExecutor.sessionMode(),
                promptContext);
        ArtifactKey contextId = promptContext.currentContextId() != null
                ? promptContext.currentContextId().createChild()
                : chatKey;
        AgentModels.AiFilterRequest aiSessionRequest = decorateRequestResults.decorateRequest(
                new DecorateRequestResults.DecorateRequestArgs<>(
                        AgentModels.AiFilterRequest.builder()
                                .contextId(contextId)
                                .chatKey(chatKey)
                                .input(payload)
                                .goal("AI filter execution for policy " + policy.getRegistrationId())
                                .build(),
                        operationContext,
                        AI_FILTER_AGENT_NAME,
                        AI_FILTER_ACTION_NAME,
                        AI_FILTER_METHOD_NAME,
                        parentRequest
                ));

        Map<String, Object> model = buildAiModel(aiExecutor, payload, policy, parentRequest);

        PromptContext aiPromptContext = promptContext
                .toBuilder()
                .templateName(AI_FILTER_TEMPLATE_NAME)
                .modelName(AcpChatOptionsString.DEFAULT_MODEL_NAME)
                .currentRequest(aiSessionRequest)
                .previousRequest(parentRequest)
                .model(model)
                .build();

        PromptContext decoratedPromptContext = decorateRequestResults.decoratePromptContext(
                new DecorateRequestResults.DecoratePromptContextArgs(
                        aiPromptContext,
                        new DecoratorContext(
                                operationContext, AI_FILTER_AGENT_NAME, AI_FILTER_ACTION_NAME, AI_FILTER_METHOD_NAME, parentRequest, aiSessionRequest
                        )));

        ToolContext decoratedToolContext = decorateRequestResults.decorateToolContext(
                new DecorateRequestResults.DecorateToolArgs(
                        ToolContext.empty(),
                        aiSessionRequest,
                        parentRequest,
                        operationContext,
                        AI_FILTER_AGENT_NAME,
                        AI_FILTER_ACTION_NAME,
                        AI_FILTER_METHOD_NAME
                ));

        var args = AgentLlmExecutor.DirectExecutorArgs.<AgentModels.AiFilterResult>builder()
                .responseClazz(AgentModels.AiFilterResult.class)
                .agentName(AI_FILTER_AGENT_NAME)
                .actionName(AI_FILTER_ACTION_NAME)
                .methodName(AI_FILTER_METHOD_NAME)
                .template(AI_FILTER_TEMPLATE_NAME)
                .promptContext(decoratedPromptContext)
                .templateModel(model)
                .toolContext(decoratedToolContext)
                .operationContext(operationContext)
                .build();

        return new FilterArgsAndRequest(args, aiSessionRequest);
    }

    private PromptContext getPromptContext(FilterContext filterContext) {
        var promptContextBuilder = PromptContext.builder();
        if (filterContext instanceof PromptContributorContext promptContributorContext
                && promptContributorContext.ctx() != null) {
            return promptContributorContext.ctx();
        } else if (filterContext instanceof FilterContext.PathFilterContext pathCtx
                && pathCtx.filterContext() instanceof PromptContributorContext promptContributorContext
                && promptContributorContext.ctx() != null) {
            return promptContributorContext.ctx();
        } else if (filterContext instanceof GraphEventObjectContext graphEventObjectContext) {
            return buildPromptContextFromGraphEvent(graphEventObjectContext);
        } else if (filterContext instanceof FilterContext.PathFilterContext pathCtx
                && pathCtx.filterContext() instanceof GraphEventObjectContext graphEventObjectContext) {
            return buildPromptContextFromGraphEvent(graphEventObjectContext);
        } else {
            log.error("Could not build prompt context successfully for {}.", filterContext);
        }

        return promptContextBuilder.build();
    }

    private record ResolvedAgentContext(
            OperationContext operationContext,
            BlackboardHistory blackboardHistory
    ) {}

    private @Nullable ResolvedAgentContext resolveAgentContext(ArtifactKey key) {
        ArtifactKey searchThrough = key;
        while (searchThrough != null) {
            var proc = agentPlatform.getAgentProcess(searchThrough.value());
            if (proc != null) {
                OperationContext opCtx = AgentInterfaces.buildOpContext(proc, AI_FILTER_AGENT_NAME);
                BlackboardHistory history = opCtx != null
                        ? BlackboardHistory.getEntireBlackboardHistory(opCtx)
                        : null;
                return new ResolvedAgentContext(opCtx, history);
            }
            searchThrough = searchThrough.parent().orElse(null);
        }
        return null;
    }

    private PromptContext buildPromptContextFromGraphEvent(GraphEventObjectContext graphCtx) {
        ArtifactKey key = graphCtx.key();
        if (key == null) {
            return PromptContext.builder()
                    .agentType(AgentType.AI_FILTER)
                    .hashContext(Artifact.HashContext.defaultHashContext())
                    .build();
        }

        ResolvedAgentContext resolved = resolveAgentContext(key);
        if (resolved == null) {
            return PromptContext.builder()
                    .agentType(AgentType.AI_FILTER)
                    .currentContextId(key)
                    .hashContext(Artifact.HashContext.defaultHashContext())
                    .build();
        }

        AgentModels.AgentRequest lastRequest =
                BlackboardHistory.findLastWorkflowRequest(resolved.blackboardHistory());

        return PromptContext.builder()
                .agentType(AgentType.AI_FILTER)
                .currentContextId(key)
                .blackboardHistory(resolved.blackboardHistory())
                .previousRequest(lastRequest)
                .currentRequest(lastRequest)
                .hashContext(Artifact.HashContext.defaultHashContext())
                .operationContext(resolved.operationContext())
                .build();
    }

    private @Nullable OperationContext getOpContext(FilterContext filterContext, PromptContext pc) {
        if (pc.operationContext() != null)
            return pc.operationContext();

        return buildOpContext(filterContext);
    }

    private OperationContext buildOpContext(FilterContext filterContext) {
        return switch (filterContext) {
            case PromptContributorContext promptContributorContext ->
                    getOpContext(promptContributorContext.getKey(), true);
            case FilterContext.PathFilterContext pathCtx when pathCtx.filterContext() instanceof PromptContributorContext promptContributorContext ->
                    getOpContext(promptContributorContext.getKey(), true);
            case GraphEventObjectContext graphEventObjectContext ->
                    getOpContext(graphEventObjectContext.getKey(), false);
            case FilterContext.PathFilterContext pathCtx when pathCtx.filterContext() instanceof GraphEventObjectContext graphEventObjectContext ->
                    getOpContext(graphEventObjectContext.getKey(), false);
            default ->
                    getOpContext(filterContext.key(), true);
        };
    }

    private OperationContext getOpContext(ArtifactKey key, boolean emitMissingProcessError) {

        ArtifactKey searchThrough = key;

        while (searchThrough != null) {
            var p = tryGetOpContext(searchThrough);
            if (p.isPresent())
                return p.get();

            searchThrough = searchThrough.parent().orElse(null);
        }

        if (emitMissingProcessError) {
            log.warn("Could not resolve live agent process for artifact key {}; emitting NodeError.", key);
            publishAgentProcessNotFoundError(key);
        } else {
            log.debug("Skipping OperationContext lookup for graph event key {} because no live agent process exists.", key);
        }
        return null;
    }

    public Optional<OperationContext> tryGetOpContext(ArtifactKey key) {
        return Optional.ofNullable(agentPlatform.getAgentProcess(key.value()))
                .map(ap -> AgentInterfaces.buildOpContext(ap, "ai-filter"));
    }

    private Map<String, Object> buildAiModel(AiFilterTool<?, ?> aiExecutor,
                                             String payload,
                                             PolicyRegistrationEntity policy,
                                             AgentModels.AgentRequest decoratedRequest) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("payload", payload);
        model.put("input", payload);
        model.put("policyId", policy.getRegistrationId());
        model.put("policyName", policy.getRegistrationId());
        if (aiExecutor.registrarPrompt() != null && !aiExecutor.registrarPrompt().isBlank()) {
            model.put("registrarPrompt", aiExecutor.registrarPrompt());
        }
        if (decoratedRequest != null) {
            model.put("contextRequest", decoratedRequest.prettyPrint());
        }
        return model;
    }


    private FilterDescriptor descriptorForPolicy(PolicyRegistrationEntity policy,
                                                 Filter<?, ?, ?> filter,
                                                 FilterEnums.FilterAction action,
                                                 List<Instruction> instructions,
                                                 FilterContext filterContext,
                                                 String descriptorType) {
        FilterDescriptor.Entry entry = new FilterDescriptor.Entry(
                descriptorType,
                policy.getRegistrationId(),
                filter.id(),
                filter.name(),
                policy.getFilterKind(),
                filter.sourcePath(),
                action == null ? null : action.name(),
                filter.executor() == null ? null : filter.executor().executorType().name(),
                buildExecutorDetails(filter.executor(), filterContext),
                instructions == null ? List.of() : instructions
        );

        if ("PATH".equals(descriptorType)) {
            return new FilterDescriptor.InstructionsFilterDescriptor(
                    instructions == null ? List.of() : instructions,
                    List.of(),
                    entry
            );
        }

        return new FilterDescriptor.SerdesFilterDescriptor(
                action == null ? null : action.name(),
                List.of(),
                entry
        );
    }

    private Map<String, String> buildExecutorDetails(ExecutableTool<?, ?, ?> executor, FilterContext filterContext) {
        if (executor == null) {
            return Map.of();
        }

        Map<String, String> details = new LinkedHashMap<>();
        putIfPresent(details, "configVersion", executor.configVersion());

        switch (executor) {
            case BinaryExecutor<?, ?, ?> binary -> addBinaryDetails(details, binary, filterContext);
            case PythonExecutor<?, ?, ?> python -> addPythonDetails(details, python, filterContext);
            case JavaFunctionExecutor<?, ?, ?> javaFn -> addJavaFunctionDetails(details, javaFn);
            case AiFilterTool ai -> addAiDetails(details, ai);
            default -> details.put("executorType", executor.getClass().getSimpleName());
        }
        return details;
    }

    private void addBinaryDetails(Map<String, String> details,
                                  BinaryExecutor<?, ?, ?> binary,
                                  FilterContext filterContext) {
        if (binary.command() != null && !binary.command().isEmpty()) {
            details.put("command", String.join(" ", binary.command()));
        }
        putIfPresent(details, "workingDirectory", binary.workingDirectory());
        putIfPresent(details, "outputParserRef", binary.outputParserRef());

        Path binaryPath = resolveBinaryPath(binary, filterContext);
        if (binaryPath != null) {
            details.put("binaryPath", binaryPath.toString());
            putIfPresent(details, "binaryHash", hashFile(binaryPath));
        }
    }

    private Path resolveBinaryPath(BinaryExecutor<?, ?, ?> binary, FilterContext filterContext) {
        if (binary.command() == null || binary.command().isEmpty() || binary.command().getFirst() == null) {
            return null;
        }
        String first = binary.command().getFirst();
        Path raw = Paths.get(first);
        if (raw.isAbsolute()) {
            return raw.normalize();
        }
        if (filterContext != null
                && filterContext.filterConfigProperties() != null
                && filterContext.filterConfigProperties().getBins() != null) {
            return filterContext.filterConfigProperties().getBins().resolve(raw).normalize();
        }
        return raw.normalize();
    }

    private void addPythonDetails(Map<String, String> details,
                                  PythonExecutor<?, ?, ?> python,
                                  FilterContext filterContext) {
        putIfPresent(details, "scriptPath", python.scriptPath());
        putIfPresent(details, "entryFunction", python.entryFunction());
        if (python.runtimeArgsSchema() != null) {
            details.put("runtimeArgsSchemaJson", toJsonSafely(python.runtimeArgsSchema()));
        }

        Path scriptPath = resolvePythonScriptPath(python, filterContext);
        if (scriptPath == null) {
            return;
        }
        details.put("resolvedScriptPath", scriptPath.toString());
        putIfPresent(details, "scriptHash", hashFile(scriptPath));
        putIfPresent(details, "scriptText", readFileText(scriptPath));
    }

    private Path resolvePythonScriptPath(PythonExecutor<?, ?, ?> python, FilterContext filterContext) {
        if (python.scriptPath() == null || python.scriptPath().isBlank()) {
            return null;
        }
        Path raw = Paths.get(python.scriptPath());
        if (raw.isAbsolute()) {
            return raw.normalize();
        }
        if (filterContext != null
                && filterContext.filterConfigProperties() != null
                && filterContext.filterConfigProperties().getBins() != null) {
            return filterContext.filterConfigProperties().getBins().resolve(raw).normalize();
        }
        return raw.normalize();
    }

    private void addJavaFunctionDetails(Map<String, String> details,
                                        JavaFunctionExecutor<?, ?, ?> javaFn) {
        putIfPresent(details, "functionRef", javaFn.functionRef());
        putIfPresent(details, "className", javaFn.className());
        putIfPresent(details, "methodName", javaFn.methodName());
    }

    private void addAiDetails(Map<String, String> details,
                              AiFilterTool ai) {
        putIfPresent(details, "sessionMode", ai.sessionMode() == null ? null : ai.sessionMode().name());
        putIfPresent(details, "registrarPrompt", ai.registrarPrompt());
        details.put("templateName", ai.templateName());
    }

    private String hashFile(Path path) {
        try {
            if (path == null || !Files.exists(path) || !Files.isRegularFile(path)) {
                return null;
            }
            return ArtifactHashing.hashBytes(Files.readAllBytes(path));
        } catch (Exception e) {
            log.warn("Failed to hash file at {}", path, e);
            return null;
        }
    }

    private String readFileText(Path path) {
        try {
            if (path == null || !Files.exists(path) || !Files.isRegularFile(path)) {
                return null;
            }
            byte[] bytes = Files.readAllBytes(path);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to read file text at {}", path, e);
            return null;
        }
    }

    private void putIfPresent(Map<String, String> target, String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            return;
        }
        target.put(key, value);
    }

    private FilterDescriptor safeDescriptor(FilterDescriptor descriptor) {
        return descriptor == null ? new FilterDescriptor.NoOpFilterDescriptor() : descriptor;
    }

    private void publishFilterErrorEvent(String policyId,
                                         String layerId,
                                         FilterSource source,
                                         Throwable throwable) {
        try {
            String nodeId = resolveNodeId(layerId, source);
            String message = buildFilterErrorMessage(policyId, layerId, throwable);
            eventBus.publish(new Events.NodeErrorEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    nodeId,
                    "Filter Policy Error",
                    Events.NodeType.WORK,
                    message
            ));
        } catch (Exception publishError) {
            log.error("Failed to publish filter error event for policy {}", policyId, publishError);
        }
    }

    private void publishAgentProcessNotFoundError(ArtifactKey key) {
        try {
            String nodeId = key != null ? key.value() : ArtifactKey.createRoot().value();
            eventBus.publish(new Events.NodeErrorEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    nodeId,
                    "AI Filter: Agent Process Not Found",
                    Events.NodeType.WORK,
                    "Could not resolve agent process for artifact key " + nodeId
                            + ". AI filter execution will be skipped for this context."
            ));
        } catch (Exception e) {
            log.error("Failed to publish agent-process-not-found error for key {}", key, e);
        }
    }

    private String resolveNodeId(String layerId, FilterSource source) {
        if (source instanceof FilterSource.GraphEventSource graphEventSource
                && graphEventSource.event() != null
                && graphEventSource.event().nodeId() != null
                && ArtifactKey.isValid(graphEventSource.event().nodeId())) {
            return graphEventSource.event().nodeId();
        }
        if (layerId != null && ArtifactKey.isValid(layerId)) {
            return layerId;
        }
        ArtifactKey root = ArtifactKey.createRoot();
        String message = "Filter node id could not be resolved: layerId="
                + (layerId == null ? "null" : "\"" + layerId + "\" (invalid format)")
                + ", source=" + (source == null ? "null" : source.getClass().getSimpleName())
                + ". Falling back to fresh root " + root.value() + ".";
        log.error(message);
        if (eventBus != null) {
            eventBus.publish(Events.NodeErrorEvent.err(message, root));
        }
        return root.value();
    }

    private String buildFilterErrorMessage(String policyId, String layerId, Throwable throwable) {
        StringBuilder message = new StringBuilder("Filter policy execution error");
        if (policyId != null && !policyId.isBlank()) {
            message.append(" [policyId=").append(policyId).append("]");
        }
        if (layerId != null && !layerId.isBlank()) {
            message.append(" [layerId=").append(layerId).append("]");
        }
        if (throwable != null) {
            message.append(": ").append(throwable.getClass().getSimpleName());
            if (throwable.getMessage() != null && !throwable.getMessage().isBlank()) {
                message.append(" - ").append(throwable.getMessage());
            }
        }
        return message.toString();
    }

    private FilterEnums.FilterAction toAction(Object input, Object output) {
        if (output == null) {
            return FilterEnums.FilterAction.DROPPED;
        }
        if (!sameValue(input, output)) {
            return FilterEnums.FilterAction.TRANSFORMED;
        }
        return FilterEnums.FilterAction.PASSTHROUGH;
    }

    private boolean sameValue(Object left, Object right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equals(right) || String.valueOf(left).equals(String.valueOf(right));
    }

    private FilterEnums.FilterKind parseFilterKind(PolicyRegistrationEntity policy) {
        String filterKind = policy.getFilterKind();
        if (filterKind == null || filterKind.isBlank()) {
            return null;
        }
        try {
            return FilterEnums.FilterKind.valueOf(filterKind);
        } catch (IllegalArgumentException e) {
            log.warn("Unknown filter kind on policy {}: {}", policy.getRegistrationId(), filterKind);
            return null;
        }
    }

    private boolean matchesBinding(PolicyRegistrationEntity policy, String layerId, FilterSource source) {
        try {
            List<PolicyLayerBinding> bindings = objectMapper.readValue(policy.getLayerBindingsJson(), new TypeReference<>() {});

            boolean matched = false;
            for (PolicyLayerBinding binding : bindings) {
                if (!binding.enabled() || !binding.layerId().equals(layerId)) {
                    continue;
                }
                if (binding.matchOn() == null || binding.matchOn() != source.matchOn()) {
                    continue;
                }

                boolean bindingMatched = matchesSource(binding, source);
                logPromptBindingEvaluation(policy, layerId, source, binding, bindingMatched);
                if (bindingMatched) {
                    matched = true;
                    break;
                }
            }
            return matched;
        } catch (Exception e) {
            log.error("Failed to parse bindings for policy {}", policy.getRegistrationId(), e);
            return false;
        }
    }

    private boolean matchesSource(PolicyLayerBinding binding, FilterSource source) {
        if (source instanceof FilterSource.GraphEventSource graphEventSource
                && binding.matcherKey() == FilterEnums.MatcherKey.NAME) {
            return matchesGraphEventName(binding, graphEventSource);
        }

        String valueToMatch = source.matcherValue(binding.matcherKey());
        if (valueToMatch == null) {
            return true;
        }

        return switch (binding.matcherType()) {
            case EQUALS -> binding.matcherText().equals(valueToMatch);
            case REGEX -> matchesRegex(binding.matcherText(), valueToMatch);
            case CONTAINS -> valueToMatch.contains(binding.matcherText());
        };
    }

    public static boolean matchesGraphEventName(PolicyLayerBinding binding, FilterSource.GraphEventSource source) {
        List<String> candidates = source.nameCandidates();
        if (candidates.isEmpty()) {
            return true;
        }
        return switch (binding.matcherType()) {
            case EQUALS -> candidates.stream().anyMatch(binding.matcherText()::equals);
            case REGEX -> candidates.stream().anyMatch(candidate -> matchesRegex(binding.matcherText(), candidate));
            case CONTAINS -> candidates.stream().anyMatch(candidate -> candidate.contains(binding.matcherText()));
        };
    }

    private static boolean matchesRegex(String pattern, String valueToMatch) {
        try {
            return Pattern.compile(pattern).matcher(valueToMatch).matches();
        } catch (Exception e) {
            log.warn("Invalid regex in matcher: {}", pattern);
            return false;
        }
    }

    private void logPromptBindingEvaluation(PolicyRegistrationEntity policy,
                                            String layerId,
                                            FilterSource source,
                                            PolicyLayerBinding binding,
                                            boolean matched) {
        if (!log.isDebugEnabled() || source.matchOn() != FilterEnums.MatchOn.PROMPT_CONTRIBUTOR) {
            return;
        }

        String sourceName = source.matcherValue(FilterEnums.MatcherKey.NAME);
        String sourceText = abbreviate(source.matcherValue(FilterEnums.MatcherKey.TEXT), 160);
        String contributorClass = source instanceof FilterSource.PromptContributorSource promptSource
                && promptSource.contributor() != null
                ? promptSource.contributor().getClass().getName()
                : "unknown";

        log.debug(
                "Prompt filter binding eval policy={} layer={} contributorClass={} bindingKey={} bindingType={} bindingText='{}' sourceName='{}' sourceText='{}' matched={}",
                policy.getRegistrationId(),
                layerId,
                contributorClass,
                binding.matcherKey(),
                binding.matcherType(),
                binding.matcherText(),
                sourceName,
                sourceText,
                matched
        );
    }

    private String abbreviate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength) + "...";
    }

    private void recordDecision(PolicyRegistrationEntity policy,
                                String layerId,
                                FilterEnums.FilterAction action,
                                Object input,
                                Object output,
                                List<Instruction> appliedInstructions,
                                FilterContext filterContext,
                                String error) {
        try {
            String inputJson = toJsonSafely(input);
            String outputJson = toJsonSafely(output);
            String instructionsJson = InstructionJson.toJsonSafely(objectMapper, appliedInstructions);
            FilterDecisionRecordEntity record = FilterDecisionRecordEntity.builder()
                    .decisionId("dec-" + UUID.randomUUID())
                    .policyId(policy.getRegistrationId())
                    .filterType(policy.getFilterKind())
                    .layerId(layerId)
                    .action(action.name())
                    .inputJson(inputJson)
                    .outputJson(outputJson)
                    .appliedInstructionsJson(instructionsJson)
                    .errorMessage(error)
                    .createdAt(Instant.now())
                    .build();

            decisionRecordRepository.save(record);

            var hash = ArtifactHashing.hashMap(Map.of(
                    "inputJson", Optional.ofNullable(inputJson).orElse(""),
                    "outputJson", Optional.ofNullable(outputJson).orElse(""),
                    "instructionJson", Optional.ofNullable(instructionsJson).orElse("")
            ));

            executionScopeService.emitArtifact(
                    Artifact.FilterDecisionRecordArtifact.builder()
                            .hash(hash)
                            .artifactKey(filterContext.key().createChild())
                            .inputJson(inputJson)
                            .outputJson(outputJson)
                            .instructionsJson(instructionsJson)
                            .children(new ArrayList<>())
                            .build(),
                    filterContext.key()
            );

        } catch (Exception e) {
            log.error("Failed to record filter decision", e);
        }
    }

    private String toJsonSafely(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            try {
                return objectMapper.writeValueAsString(String.valueOf(value));
            } catch (Exception ignored) {
                return String.valueOf(value);
            }
        }
    }

    private int extractPriority(PolicyRegistrationEntity policy) {
        try {
            var node = objectMapper.readTree(policy.getFilterJson());
            return node.has("priority") ? node.get("priority").asInt(0) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean isAllowedKind(FilterEnums.FilterKind kind, Set<FilterEnums.FilterKind> allowedKinds) {
        if (allowedKinds == null || allowedKinds.isEmpty()) {
            return true;
        }
        return allowedKinds.contains(kind);
    }

    private record AppliedFilterResult(
            Object input,
            Object output,
            FilterEnums.FilterAction action,
            FilterDescriptor descriptor,
            String error
    ) {

        public AppliedFilterResult(Object input, Object output, FilterEnums.FilterAction action) {
            this(input, output, action, new FilterDescriptor.NoOpFilterDescriptor(), null);
        }

        public AppliedFilterResult(Object input, Object output, FilterEnums.FilterAction action, List<Instruction> instructions) {
            this(input, output, action, new FilterDescriptor.InstructionsFilterDescriptor(instructions, new ArrayList<>()), null);
        }

        public AppliedFilterResult(Object input, Object output, FilterEnums.FilterAction action, FilterDescriptor descriptor) {
            this(input, output, action, descriptor, null);
        }
    }

}
