package com.hayden.multiagentide.transformation.service;

import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.core.AgentPlatform;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.config.AcpChatOptionsString;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.agent.decorator.request.DecorateRequestResults;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.filter.service.AiFilterSessionResolver;
import com.hayden.multiagentide.filter.service.FilterLayerCatalog;
import com.hayden.multiagentide.transformation.repository.TransformationRecordEntity;
import com.hayden.multiagentide.transformation.repository.TransformationRecordRepository;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.agent.AgentType;
import com.hayden.multiagentide.agent.BlackboardHistory;
import com.hayden.multiagentide.filter.config.FilterContextFactory;
import com.hayden.multiagentide.prompt.PromptContext;
import com.hayden.multiagentide.tool.ToolContext;
import com.hayden.multiagentide.model.AiTextTransformer;
import com.hayden.multiagentide.model.TextTransformer;
import com.hayden.multiagentide.model.TransformationAction;
import com.hayden.multiagentide.model.Transformer;
import com.hayden.multiagentide.model.TransformerMatchOn;
import com.hayden.multiagentide.llm.AgentLlmExecutor;
import com.hayden.multiagentide.model.executor.AiTransformerTool;
import com.hayden.multiagentide.model.layer.AiTransformerContext;
import com.hayden.multiagentide.model.layer.ControllerEndpointTransformationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransformerExecutionService {

    public static final String AI_TRANSFORMER_AGENT_NAME = FilterLayerCatalog.AI_TRANSFORMER;
    public static final String AI_TRANSFORMER_ACTION_NAME = "transform-controller-response";
    public static final String AI_TRANSFORMER_METHOD_NAME = "runAiTransformer";
    public static final String AI_TRANSFORMER_TEMPLATE_NAME = AiTransformerTool.TEMPLATE_NAME;

    private final TransformerDiscoveryService discoveryService;
    private final TransformationRecordRepository recordRepository;
    private final ObjectMapper objectMapper;
    private final AgentPlatform agentPlatform;
    private final AiFilterSessionResolver aiFilterSessionResolver;
    private final FilterContextFactory filterContextFactory;

    @Autowired
    @Lazy
    private EventBus eventBus;

    @Autowired
    @Lazy
    private DecorateRequestResults decorateRequestResults;

    @Autowired
    private AiTransformerToolHydration aiTransformerToolHydration;

    public TransformationExecutionResult transform(String layerId,
                                                   String controllerId,
                                                   String endpointId,
                                                   Object payload) {
        String currentText = toJson(payload);
        boolean transformed = false;
        for (var entity : discoveryService.getActiveTransformersByLayer(layerId)) {
            boolean endpointMatched = discoveryService.applicableBindings(entity, layerId, TransformerMatchOn.CONTROLLER_ENDPOINT_RESPONSE).stream()
                    .anyMatch(binding -> binding.matcherText() == null || binding.matcherText().isBlank() || endpointId.equals(binding.matcherText()));
            if (!endpointMatched) {
                continue;
            }
            try {
                Optional<Transformer<?, ?, ?>> modelOpt = discoveryService.deserialize(entity);
                if (modelOpt.isEmpty()) {
                    continue;
                }
                Transformer<?, ?, ?> model = modelOpt.get();
                String afterText = applyTransformer(model, layerId, controllerId, endpointId, payload, currentText);
                if (afterText == null) {
                    Instant now = Instant.now();
                    String errorMsg = "invalid - returned null";
                    recordRepository.save(TransformationRecordEntity.builder()
                            .recordId("transform-record-" + UUID.randomUUID())
                            .registrationId(entity.getRegistrationId())
                            .layerId(layerId)
                            .controllerId(controllerId)
                            .endpointId(endpointId)
                            .action(TransformationAction.FAILED.name())
                            .beforePayload(currentText)
                            .afterPayload(currentText)
                            .errorMessage(errorMsg)
                            .createdAt(now)
                            .build());
                    emitTransformationEvent(entity.getRegistrationId(), layerId, controllerId, endpointId, TransformationAction.FAILED, errorMsg, now);
                    continue;
                }
                TransformationAction action = afterText.equals(currentText) ? TransformationAction.PASSTHROUGH : TransformationAction.TRANSFORMED;
                transformed = transformed || action == TransformationAction.TRANSFORMED;
                Instant now = Instant.now();
                recordRepository.save(TransformationRecordEntity.builder()
                        .recordId("transform-record-" + UUID.randomUUID())
                        .registrationId(entity.getRegistrationId())
                        .layerId(layerId)
                        .controllerId(controllerId)
                        .endpointId(endpointId)
                        .action(action.name())
                        .beforePayload(currentText)
                        .afterPayload(afterText)
                        .createdAt(now)
                        .build());
                emitTransformationEvent(entity.getRegistrationId(), layerId, controllerId, endpointId, action, null, now);
                currentText = afterText;
            } catch (Exception e) {
                log.error("Transformation execution failed for controller={} endpoint={} registration={}", controllerId, endpointId, entity.getRegistrationId(), e);
                Instant now = Instant.now();
                recordRepository.save(TransformationRecordEntity.builder()
                        .recordId("transform-record-" + UUID.randomUUID())
                        .registrationId(entity.getRegistrationId())
                        .layerId(layerId)
                        .controllerId(controllerId)
                        .endpointId(endpointId)
                        .action(TransformationAction.FAILED.name())
                        .beforePayload(currentText)
                        .afterPayload(currentText)
                        .errorMessage(e.getMessage())
                        .createdAt(now)
                        .build());
                emitTransformationEvent(entity.getRegistrationId(), layerId, controllerId, endpointId, TransformationAction.FAILED, e.getMessage(), now);
            }
        }
        return new TransformationExecutionResult(transformed ? currentText : payload, transformed);
    }

    private String applyTransformer(Transformer<?, ?, ?> model,
                                    String layerId,
                                    String controllerId,
                                    String endpointId,
                                    Object originalPayload,
                                    String currentText) {
        ControllerEndpointTransformationContext context = filterContextFactory.get(
                () -> new ControllerEndpointTransformationContext(
                        layerId,
                        ArtifactKey.createRoot(),
                        controllerId,
                        endpointId,
                        originalPayload,
                        currentText
                )
        );
        if (model instanceof TextTransformer textTransformer) {
            return textTransformer.apply(currentText, context).transformedText();
        }
        if (model instanceof AiTextTransformer aiTextTransformer) {
            return runAiTransformer(aiTextTransformer, context, currentText);
        }
        return currentText;
    }

    private String runAiTransformer(AiTextTransformer aiTextTransformer,
                                    ControllerEndpointTransformationContext context,
                                    String currentText) {
        if (!(aiTextTransformer.executor() instanceof AiTransformerTool aiExecutor)) {
            log.warn("AI transformer executor was not an AiTransformerTool for controller={} endpoint={}", context.controllerId(), context.endpointId());
            return currentText;
        }

        aiTransformerToolHydration.hydrate(aiExecutor);

        OperationContext operationContext = resolveOperationContext(context.key());
        if (operationContext == null) {
            log.warn("Skipping AI transformer for controller={} endpoint={} because no OperationContext was available.",
                    context.controllerId(), context.endpointId());
            return currentText;
        }

        var argsAndRequest = buildTransformerArgs(aiTextTransformer, aiExecutor, context, currentText, operationContext);

        AiTransformerContext aiContext = AiTransformerContext.builder()
                .transformationContext(context)
                .templateName(AI_TRANSFORMER_TEMPLATE_NAME)
                .promptContext(argsAndRequest.args().promptContext())
                .toolContext(argsAndRequest.args().toolContext())
                .model(argsAndRequest.args().templateModel())
                .responseClass(AgentModels.AiTransformerResult.class)
                .context(operationContext)
                .directExecutorArgs(argsAndRequest.args()
                        .toBuilder()
                        .refresh(() -> buildTransformerArgs(aiTextTransformer, aiExecutor, context, currentText, operationContext).args())
                        .build())
                .build();

        AgentModels.AiTransformerResult result = aiTextTransformer.apply(argsAndRequest.decoratedRequest(), aiContext);
        result = decorateRequestResults.decorateResult(
                new DecorateRequestResults.DecorateResultArgs<>(
                        result,
                        operationContext,
                        AI_TRANSFORMER_AGENT_NAME,
                        AI_TRANSFORMER_ACTION_NAME,
                        AI_TRANSFORMER_METHOD_NAME,
                        argsAndRequest.decoratedRequest()
                ));
        return result.transformedText() == null || result.transformedText().isBlank()
                ? currentText
                : result.transformedText();
    }

    private record TransformerArgsAndRequest(
            AgentLlmExecutor.DirectExecutorArgs<AgentModels.AiTransformerResult> args,
            AgentModels.AiTransformerRequest decoratedRequest
    ) {}

    private TransformerArgsAndRequest buildTransformerArgs(
            AiTextTransformer aiTextTransformer,
            AiTransformerTool aiExecutor,
            ControllerEndpointTransformationContext context,
            String currentText,
            OperationContext operationContext
    ) {
        AgentModels.AgentRequest parentRequest = BlackboardHistory.getLastFromHistory(operationContext, AgentModels.AgentRequest.class);
        PromptContext parentPromptContext = PromptContext.builder()
                .agentType(AgentType.AI_TRANSFORMER)
                .currentContextId(resolveContextId(parentRequest, context.key()))
                .blackboardHistory(BlackboardHistory.getEntireBlackboardHistory(operationContext))
                .previousRequest(parentRequest)
                .currentRequest(parentRequest)
                .hashContext(Artifact.HashContext.defaultHashContext())
                .operationContext(operationContext)
                .build();
        ArtifactKey chatKey = aiFilterSessionResolver.resolveSessionKey(
                aiTextTransformer.id(),
                aiExecutor.sessionMode(),
                parentPromptContext);
        ArtifactKey contextId = parentPromptContext.currentContextId() != null
                ? parentPromptContext.currentContextId().createChild()
                : chatKey;
        AgentModels.AiTransformerRequest request = AgentModels.AiTransformerRequest.builder()
                .contextId(contextId)
                .chatKey(chatKey)
                .worktreeContext(parentRequest == null ? null : parentRequest.worktreeContext())
                .goal(buildGoal(context))
                .input(currentText)
                .controllerId(context.controllerId())
                .endpointId(context.endpointId())
                .metadata(Map.of("layerId", safe(context.layerId())))
                .build();

        AgentModels.AiTransformerRequest decoratedRequest = decorateRequestResults.decorateRequest(
                new DecorateRequestResults.DecorateRequestArgs<>(
                        request,
                        operationContext,
                        AI_TRANSFORMER_AGENT_NAME,
                        AI_TRANSFORMER_ACTION_NAME,
                        AI_TRANSFORMER_METHOD_NAME,
                        parentRequest
                ));

        Map<String, Object> model = buildAiModel(aiExecutor, currentText, context, parentRequest);

        PromptContext promptContext = PromptContext.builder()
                .agentType(AgentType.AI_TRANSFORMER)
                .currentContextId(decoratedRequest.contextId())
                .blackboardHistory(BlackboardHistory.getEntireBlackboardHistory(operationContext))
                .previousRequest(parentRequest)
                .currentRequest(decoratedRequest)
                .hashContext(Artifact.HashContext.defaultHashContext())
                .model(model)
                .modelName(AcpChatOptionsString.DEFAULT_MODEL_NAME)
                .templateName(AI_TRANSFORMER_TEMPLATE_NAME)
                .operationContext(operationContext)
                .build();

        PromptContext decoratedPromptContext = decorateRequestResults.decoratePromptContext(
                new DecorateRequestResults.DecoratePromptContextArgs(
                        promptContext,
                        new DecoratorContext(
                                operationContext, AI_TRANSFORMER_AGENT_NAME, AI_TRANSFORMER_ACTION_NAME, AI_TRANSFORMER_METHOD_NAME, parentRequest, decoratedRequest
                        )));

        ToolContext toolContext = decorateRequestResults.decorateToolContext(
                new DecorateRequestResults.DecorateToolArgs(
                        ToolContext.empty(),
                        decoratedRequest,
                        parentRequest,
                        operationContext,
                        AI_TRANSFORMER_AGENT_NAME,
                        AI_TRANSFORMER_ACTION_NAME,
                        AI_TRANSFORMER_METHOD_NAME
                ));

        var args = AgentLlmExecutor.DirectExecutorArgs.<AgentModels.AiTransformerResult>builder()
                .responseClazz(AgentModels.AiTransformerResult.class)
                .agentName(AI_TRANSFORMER_AGENT_NAME)
                .actionName(AI_TRANSFORMER_ACTION_NAME)
                .methodName(AI_TRANSFORMER_METHOD_NAME)
                .template(AI_TRANSFORMER_TEMPLATE_NAME)
                .promptContext(decoratedPromptContext)
                .templateModel(model)
                .toolContext(toolContext)
                .operationContext(operationContext)
                .build();

        return new TransformerArgsAndRequest(args, decoratedRequest);
    }

    private Map<String, Object> buildAiModel(AiTransformerTool aiExecutor,
                                             String currentText,
                                             ControllerEndpointTransformationContext context,
                                             AgentModels.AgentRequest parentRequest) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("input", currentText);
        model.put("controllerId", context.controllerId());
        model.put("endpointId", context.endpointId());
        if (aiExecutor.registrarPrompt() != null && !aiExecutor.registrarPrompt().isBlank()) {
            model.put("registrarPrompt", aiExecutor.registrarPrompt());
        }
        if (parentRequest != null) {
            model.put("contextRequest", parentRequest.prettyPrint());
        }
        return model;
    }

    private String buildGoal(ControllerEndpointTransformationContext context) {
        return "Transform controller output into the most effective format for the controller or human"
                + " [controller=" + safe(context.controllerId()) + ", endpoint=" + safe(context.endpointId()) + "]";
    }

    private ArtifactKey resolveContextId(AgentModels.AgentRequest parentRequest, ArtifactKey fallbackKey) {
        if (parentRequest != null && parentRequest.contextId() != null) {
            return parentRequest.contextId();
        }
        if (fallbackKey != null) {
            return fallbackKey;
        }
        ArtifactKey root = ArtifactKey.createRoot();
        emitNodeError("Transformer context key could not be resolved: parentRequest="
                + (parentRequest == null ? "null" : parentRequest.getClass().getSimpleName() + "(contextId=null)")
                + ", fallbackKey=null. Falling back to fresh root " + root.value()
                + ". Transformer artifacts will be orphaned.", root);
        return root;
    }

    private void emitNodeError(String message, ArtifactKey key) {
        log.error(message);
        if (eventBus != null) {
            eventBus.publish(Events.NodeErrorEvent.err(message, key));
        }
    }

    private OperationContext resolveOperationContext(ArtifactKey key) {
        return com.hayden.multiagentide.embabel.EmbabelUtil.resolveOperationContext(
                agentPlatform, key, AI_TRANSFORMER_AGENT_NAME);
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

    private void emitTransformationEvent(String registrationId, String layerId,
                                         String controllerId, String endpointId,
                                         TransformationAction action, String errorMessage,
                                         Instant timestamp) {
        try {
            eventBus.publish(new Events.TransformationEvent(
                    "transform-event-" + UUID.randomUUID(),
                    timestamp,
                    "",
                    registrationId,
                    layerId,
                    controllerId,
                    endpointId,
                    action.name(),
                    errorMessage
            ));
        } catch (Exception e) {
            log.warn("Failed to emit transformation event for registration={}", registrationId, e);
        }
    }

    public record TransformationExecutionResult(Object body, boolean transformed) {
    }
}
