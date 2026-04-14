package com.hayden.multiagentide.propagation.service;

import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.core.AgentPlatform;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.config.AcpChatOptionsString;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.agent.decorator.request.DecorateRequestResults;
import com.hayden.multiagentide.model.*;
import com.hayden.multiagentide.propagation.repository.PropagatorRegistrationEntity;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.filter.service.AiFilterSessionResolver;
import com.hayden.multiagentide.filter.service.FilterLayerCatalog;
import com.hayden.multiagentide.propagation.model.Propagation;
import com.hayden.multiagentide.propagation.repository.PropagationRecordEntity;
import com.hayden.multiagentide.propagation.repository.PropagationRecordRepository;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.agent.AgentPretty;
import com.hayden.multiagentide.agent.AgentType;
import com.hayden.multiagentide.agent.BlackboardHistory;
import com.hayden.multiagentide.filter.config.FilterContextFactory;
import com.hayden.multiagentide.llm.AgentLlmExecutor;
import com.hayden.multiagentide.model.executor.AiPropagatorTool;
import com.hayden.multiagentide.model.layer.AiPropagatorContext;
import com.hayden.multiagentide.model.layer.DefaultPropagationContext;
import com.hayden.multiagentide.prompt.PromptContext;
import com.hayden.multiagentide.tool.ToolContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PropagationExecutionService {

    public static final String AI_PROPAGATOR_AGENT_NAME = FilterLayerCatalog.AI_PROPAGATOR;
    public static final String AI_PROPAGATOR_ACTION_NAME = "propagate-action";
    public static final String AI_PROPAGATOR_METHOD_NAME = "runAiPropagator";
    public static final String AI_PROPAGATOR_TEMPLATE_NAME = AiPropagatorTool.TEMPLATE_NAME;

    private final PropagatorDiscoveryService discoveryService;
    private final PropagationItemService propagationItemService;
    private final PropagationRecordRepository recordRepository;
    private final ObjectMapper objectMapper;
    private final AgentPlatform agentPlatform;
    private final AiFilterSessionResolver aiFilterSessionResolver;
    private final FilterContextFactory filterContextFactory;
    private final DecorateRequestResults decorateRequestResults;

    @Autowired
    @Lazy
    private EventBus eventBus;

    @Autowired
    private AiPropagatorToolHydration aiPropagatorToolHydration;

    public void execute(String layerId,
                        PropagatorMatchOn stage,
                        Object payload,
                        String sourceNodeId,
                        String sourceName,
                        OperationContext operationContext) {
        String beforePayload = toJson(payload);
        ArtifactKey key = resolveKey(sourceNodeId);
        List<PropagatorRegistrationEntity> activePropagatorsByLayer = discoveryService.getActivePropagatorsByLayer(layerId);
        for (var entity : activePropagatorsByLayer) {
            List<PropagatorLayerBinding> propagatorLayerBindings = discoveryService.applicableBindings(entity, layerId, stage);
            if (propagatorLayerBindings.isEmpty()) {
                continue;
            }
            try {
                Optional<Propagator<?, ?, ?>> modelOpt = discoveryService.deserialize(entity);
                if (modelOpt.isEmpty()) {
                    continue;
                }
                Propagator<?, ?, ?> model = modelOpt.get();
                PropagationOutput output = run(model, beforePayload, layerId, key, stage, sourceName, sourceNodeId, payload, operationContext);
                if (output == null) {
                    output = failedOutput(beforePayload, "invalid - returned null");
                }
                String correlationKey = correlationKey(entity.getRegistrationId(), layerId, stage, sourceNodeId, beforePayload);
                String llmOut = output.propagatedText() != null && !output.propagatedText().isBlank()
                        ? output.propagatedText()
                        : (output.errorMessage() != null ? "LLM propagator failed: " + output.errorMessage() : "LLM propagator returned no output");
                String itemPayload = toJson(new Propagation(llmOut, parseJsonNode(beforePayload)));
                warnIfRawSummary(output.summaryText(), entity.getRegistrationId(), layerId);
                var item = propagationItemService.createItemIfNeeded(
                        entity.getRegistrationId(),
                        layerId,
                        sourceNodeId,
                        sourceName,
                        output.summaryText(),
                        itemPayload,
                        stage != null ? stage.name() : null,
                        correlationKey
                );
                PropagationAction action = item.isPresent()
                        ? PropagationAction.ITEM_CREATED
                        : transformed(beforePayload, output.propagatedText()) ? PropagationAction.TRANSFORMED : PropagationAction.PASSTHROUGH;
                Instant now = Instant.now();
                recordRepository.save(PropagationRecordEntity.builder()
                        .recordId("prop-record-" + UUID.randomUUID())
                        .registrationId(entity.getRegistrationId())
                        .layerId(layerId)
                        .sourceNodeId(sourceNodeId)
                        .sourceType(Optional.ofNullable(stage).map(Enum::name).orElse(null))
                        .action(action.name())
                        .beforePayload(beforePayload)
                        .afterPayload(output.propagatedText())
                        .correlationKey(correlationKey)
                        .errorMessage(output.errorMessage())
                        .createdAt(now)
                        .build());
                emitPropagationEvent(entity.getRegistrationId(), layerId, stage, action, sourceNodeId, sourceName, payload, correlationKey, now);
            } catch (Exception e) {
                log.error("Propagation execution failed for layer={} stage={} registration={}", layerId, stage, entity.getRegistrationId(), e);
                Instant now = Instant.now();
                String failurePayload = toJson(new Propagation("Propagation execution failed: " + e.getMessage(), parseJsonNode(beforePayload)));
                String correlationKey = correlationKey(entity.getRegistrationId(), layerId, stage, sourceNodeId, beforePayload);
                propagationItemService.createItemIfNeeded(
                        entity.getRegistrationId(),
                        layerId,
                        sourceNodeId,
                        sourceName,
                        "Propagation execution failed: " + e.getMessage(),
                        failurePayload,
                        stage != null ? stage.name() : null,
                        correlationKey
                );
                recordRepository.save(PropagationRecordEntity.builder()
                        .recordId("prop-record-" + UUID.randomUUID())
                        .registrationId(entity.getRegistrationId())
                        .layerId(layerId)
                        .sourceNodeId(sourceNodeId)
                        .sourceType(stage.name())
                        .action(PropagationAction.FAILED.name())
                        .beforePayload(beforePayload)
                        .afterPayload(failurePayload)
                        .errorMessage(e.getMessage())
                        .createdAt(now)
                        .build());
                emitPropagationEvent(entity.getRegistrationId(), layerId, stage, PropagationAction.FAILED, sourceNodeId, sourceName, payload, correlationKey, now);
            }
        }
    }

    private PropagationOutput run(Propagator<?, ?, ?> model,
                                  String beforePayload,
                                  String layerId,
                                  ArtifactKey key,
                                  PropagatorMatchOn stage,
                                  String sourceName,
                                  String sourceNodeId,
                                  Object originalPayload,
                                  OperationContext operationContext) {
        DefaultPropagationContext context = filterContextFactory.get(
                () -> new DefaultPropagationContext(layerId, key, stage, sourceName, sourceNodeId, originalPayload, beforePayload)
        );
        if (model instanceof TextPropagator textPropagator) {
            return textPropagator.apply(beforePayload, context);
        }
        if (model instanceof AiTextPropagator aiTextPropagator) {
            return runAiPropagator(aiTextPropagator, context, beforePayload, originalPayload, operationContext);
        }
        return PropagationOutput.builder().propagatedText(beforePayload).summaryText(beforePayload).build();
    }

    private PropagationOutput runAiPropagator(AiTextPropagator aiTextPropagator,
                                              DefaultPropagationContext context,
                                              String beforePayload,
                                              Object originalPayload,
                                              OperationContext operationContext) {
        if (!(aiTextPropagator.executor() instanceof AiPropagatorTool aiExecutor)) {
            return PropagationOutput.builder()
                    .propagatedText(beforePayload)
                    .summaryText(beforePayload)
                    .errorMessage("AI propagator executor is not an AiPropagatorTool")
                    .build();
        }

        aiPropagatorToolHydration.hydrate(aiExecutor);

        OperationContext resolvedOperationContext = resolveOperationContext(operationContext, context.key());
        if (resolvedOperationContext == null) {
            log.warn("Skipping AI propagator for sourceNodeId={} because no OperationContext was available.", context.sourceNodeId());
            return failedOutput(beforePayload, "AI propagator requires a live OperationContext");
        }

        var argsAndRequest = buildPropagatorArgs(aiTextPropagator, aiExecutor, context, beforePayload, originalPayload, resolvedOperationContext);

        AiPropagatorContext aiContext = AiPropagatorContext.builder()
                .propagationContext(context)
                .templateName(AI_PROPAGATOR_TEMPLATE_NAME)
                .promptContext(argsAndRequest.args().promptContext())
                .toolContext(argsAndRequest.args().toolContext())
                .model(argsAndRequest.args().templateModel())
                .responseClass(AgentModels.AiPropagatorResult.class)
                .context(resolvedOperationContext)
                .directExecutorArgs(argsAndRequest.args()
                        .toBuilder()
                        .refresh(() -> buildPropagatorArgs(aiTextPropagator, aiExecutor, context, beforePayload, originalPayload, resolvedOperationContext).args())
                        .build())
                .build();

        AgentModels.AiPropagatorResult result = aiTextPropagator.apply(argsAndRequest.decoratedRequest(), aiContext);
        result = decorateRequestResults.decorateResult(
                new DecorateRequestResults.DecorateResultArgs<>(
                        result,
                        resolvedOperationContext,
                        AI_PROPAGATOR_AGENT_NAME,
                        AI_PROPAGATOR_ACTION_NAME,
                        AI_PROPAGATOR_METHOD_NAME,
                        argsAndRequest.decoratedRequest()));
        return result.toOutput();
    }

    private record PropagatorArgsAndRequest(
            AgentLlmExecutor.DirectExecutorArgs<AgentModels.AiPropagatorResult> args,
            AgentModels.AiPropagatorRequest decoratedRequest
    ) {}

    private PropagatorArgsAndRequest buildPropagatorArgs(
            AiTextPropagator aiTextPropagator,
            AiPropagatorTool aiExecutor,
            DefaultPropagationContext context,
            String beforePayload,
            Object originalPayload,
            OperationContext resolvedOperationContext
    ) {
        AgentModels.AgentRequest parentRequest = resolveParentRequest(originalPayload, resolvedOperationContext);
        PromptContext parentPromptContext = PromptContext.builder()
                .agentType(AgentType.AI_PROPAGATOR)
                .currentContextId(resolveContextId(parentRequest, context.key()))
                .blackboardHistory(BlackboardHistory.getEntireBlackboardHistory(resolvedOperationContext))
                .previousRequest(parentRequest)
                .currentRequest(parentRequest)
                .hashContext(Artifact.HashContext.defaultHashContext())
                .operationContext(resolvedOperationContext)
                .build();
        ArtifactKey chatKey = aiFilterSessionResolver.resolveSessionKey(
                aiTextPropagator.id(),
                aiExecutor.sessionMode(),
                parentPromptContext);
        ArtifactKey contextId = parentPromptContext.currentContextId() != null
                ? parentPromptContext.currentContextId().createChild()
                : chatKey;
        AgentModels.AiPropagatorRequest request = AgentModels.AiPropagatorRequest.builder()
                .contextId(contextId)
                .chatKey(chatKey)
                .worktreeContext(parentRequest == null ? null : parentRequest.worktreeContext())
                .goal(buildGoal(context))
                .input(beforePayload)
                .sourceName(context.sourceName())
                .sourceNodeId(context.sourceNodeId())
                .metadata(Map.of(
                        "layerId", safe(context.layerId()),
                        "stage", context.actionStage() == null ? "" : context.actionStage().name()))
                .build();

        AgentModels.AiPropagatorRequest decoratedRequest = decorateRequestResults.decorateRequest(
                new DecorateRequestResults.DecorateRequestArgs<>(
                        request,
                        resolvedOperationContext,
                        AI_PROPAGATOR_AGENT_NAME,
                        AI_PROPAGATOR_ACTION_NAME,
                        AI_PROPAGATOR_METHOD_NAME,
                        parentRequest));

        Map<String, Object> model = buildAiModel(aiExecutor, beforePayload, context, parentRequest);
        String resolvedModelName = AcpChatOptionsString.DEFAULT_MODEL_NAME;

        PromptContext promptContext = PromptContext.builder()
                .agentType(AgentType.AI_PROPAGATOR)
                .currentContextId(decoratedRequest.contextId())
                .blackboardHistory(BlackboardHistory.getEntireBlackboardHistory(resolvedOperationContext))
                .previousRequest(parentRequest)
                .currentRequest(decoratedRequest)
                .hashContext(Artifact.HashContext.defaultHashContext())
                .model(model)
                .modelName(resolvedModelName)
                .templateName(AI_PROPAGATOR_TEMPLATE_NAME)
                .operationContext(resolvedOperationContext)
                .build();

        PromptContext decoratedPromptContext = decorateRequestResults.decoratePromptContext(
                new DecorateRequestResults.DecoratePromptContextArgs(
                        promptContext,
                        new DecoratorContext(
                                resolvedOperationContext, AI_PROPAGATOR_AGENT_NAME, AI_PROPAGATOR_ACTION_NAME, AI_PROPAGATOR_METHOD_NAME, parentRequest, decoratedRequest
                        )));

        ToolContext toolContext = decorateRequestResults.decorateToolContext(
                new DecorateRequestResults.DecorateToolArgs(
                        ToolContext.empty(),
                        decoratedRequest,
                        parentRequest,
                        resolvedOperationContext,
                        AI_PROPAGATOR_AGENT_NAME,
                        AI_PROPAGATOR_ACTION_NAME,
                        AI_PROPAGATOR_METHOD_NAME));

        var args = AgentLlmExecutor.DirectExecutorArgs.<AgentModels.AiPropagatorResult>builder()
                .responseClazz(AgentModels.AiPropagatorResult.class)
                .agentName(AI_PROPAGATOR_AGENT_NAME)
                .actionName(AI_PROPAGATOR_ACTION_NAME)
                .methodName(AI_PROPAGATOR_METHOD_NAME)
                .template(AI_PROPAGATOR_TEMPLATE_NAME)
                .promptContext(decoratedPromptContext)
                .templateModel(model)
                .toolContext(toolContext)
                .operationContext(resolvedOperationContext)
                .build();

        return new PropagatorArgsAndRequest(args, decoratedRequest);
    }

    private Map<String, Object> buildAiModel(AiPropagatorTool aiExecutor,
                                             String beforePayload,
                                             DefaultPropagationContext context,
                                             AgentModels.AgentRequest parentRequest) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("input", beforePayload);
        model.put("sourceName", context.sourceName());
        model.put("sourceNodeId", context.sourceNodeId());
        if (aiExecutor.registrarPrompt() != null && !aiExecutor.registrarPrompt().isBlank()) {
            model.put("registrarPrompt", aiExecutor.registrarPrompt());
        }
        if (context.actionStage() != null) {
            model.put("stage", context.actionStage().name());
        }
        if (parentRequest != null) {
            model.put("contextRequest", parentRequest.prettyPrint(
                    new AgentPretty.AgentSerializationCtx.PropagatorSerialization()));
        }
        return model;
    }

    private String buildGoal(DefaultPropagationContext context) {
        return "Monitor smaller-model output for out-of-bounds behavior and escalate to the controller or human when needed"
                + " [layer=" + safe(context.layerId()) + ", stage="
                + (context.actionStage() == null ? "unknown" : context.actionStage().name()) + "]";
    }

    private AgentModels.AgentRequest resolveParentRequest(Object originalPayload, OperationContext operationContext) {
        if (originalPayload instanceof AgentModels.AgentRequest agentRequest) {
            return agentRequest;
        }
        if (operationContext == null) {
            return null;
        }
        return BlackboardHistory.getLastFromHistory(operationContext, AgentModels.AgentRequest.class);
    }

    private ArtifactKey resolveContextId(AgentModels.AgentRequest parentRequest, ArtifactKey fallbackKey) {
        if (parentRequest != null && parentRequest.contextId() != null) {
            return parentRequest.contextId();
        }
        if (fallbackKey != null) {
            return fallbackKey;
        }
        ArtifactKey root = ArtifactKey.createRoot();
        emitNodeError("Propagator context key could not be resolved: parentRequest="
                + (parentRequest == null ? "null" : parentRequest.getClass().getSimpleName() + "(contextId=null)")
                + ", fallbackKey=null. Falling back to fresh root " + root.value()
                + ". Propagator artifacts will be orphaned.", root);
        return root;
    }

    private OperationContext resolveOperationContext(OperationContext operationContext, ArtifactKey key) {
        return com.hayden.multiagentide.embabel.EmbabelUtil.resolveOperationContext(
                agentPlatform, operationContext, key, AI_PROPAGATOR_AGENT_NAME);
    }

    private void emitNodeError(String message, ArtifactKey key) {
        log.error(message);
        if (eventBus != null) {
            eventBus.publish(Events.NodeErrorEvent.err(message, key));
        }
    }

    private PropagationOutput failedOutput(String beforePayload, String message) {
        return PropagationOutput.builder()
                .propagatedText(beforePayload)
                .summaryText(message)
                .errorMessage(message)
                .build();
    }

    /**
     * Warn when a propagator writes a raw payload as summaryText (>500 chars).
     * summaryText should be a short human-readable digest; if it's large it's
     * almost certainly a raw payload dump rather than a proper summary.
     */
    private void warnIfRawSummary(String summaryText, String registrationId, String layerId) {
        if (summaryText != null && summaryText.length() > 500) {
            log.warn("Propagator summaryText is {} chars (expected <500) — propagator may be writing raw payload instead of a digest. registrationId={} layerId={}",
                    summaryText.length(), registrationId, layerId);
        }
    }

    private JsonNode parseJsonNode(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return objectMapper.valueToTree(json);
        }
    }

    private boolean transformed(String beforePayload, String afterPayload) {
        return afterPayload != null && !afterPayload.equals(beforePayload);
    }

    private String correlationKey(String registrationId, String layerId, PropagatorMatchOn stage, String sourceNodeId, String payload) {
        String raw = String.join("|",
                safe(registrationId),
                safe(layerId),
                stage == null ? "" : stage.name(),
                safe(sourceNodeId),
                payloadHash(payload));
        return raw;
    }

    private String payloadHash(String payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(safe(payload).getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                builder.append(String.format("%02x", b));
            }
            return builder.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash propagation payload", e);
        }
    }

    private ArtifactKey resolveKey(String sourceNodeId) {
        if (sourceNodeId != null && ArtifactKey.isValid(sourceNodeId)) {
            return new ArtifactKey(sourceNodeId);
        }
        ArtifactKey root = ArtifactKey.createRoot();
        emitNodeError("Propagator source key could not be resolved: sourceNodeId="
                + (sourceNodeId == null ? "null" : "\"" + sourceNodeId + "\" (invalid format)")
                + ". Falling back to fresh root " + root.value()
                + ". Propagator artifacts will be orphaned.", root);
        return root;
    }

    private String toJson(Object value) {
        try {
            return value instanceof String s ? s : objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return String.valueOf(value);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void emitPropagationEvent(String registrationId, String layerId, PropagatorMatchOn stage,
                                      PropagationAction action,
                                      String sourceNodeId, String sourceName, Object payload,
                                      String correlationKey, Instant timestamp) {

        if (sourceNodeId == null) {
            ArtifactKey root = ArtifactKey.createRoot();
            emitNodeError("Error - could not find node ID for %s.".formatted(payload == null ? "Unknown payload" :payload.getClass().getSimpleName()), root);
            sourceNodeId = root.value();
        }

        try {
            eventBus.publish(new Events.PropagationEvent(
                    "prop-event-" + UUID.randomUUID(),
                    timestamp,
                    sourceNodeId,
                    registrationId,
                    layerId,
                    stage != null ? stage.name() : null,
                    action.name(),
                    new ArtifactKey(sourceNodeId).createChild().value(),
                    sourceName,
                    payload == null ? null : payload.getClass().getName(),
                    payload,
                    correlationKey
            ));
        } catch (Exception e) {
            log.warn("Failed to emit propagation event for registration={}", registrationId, e);
        }
    }
}
