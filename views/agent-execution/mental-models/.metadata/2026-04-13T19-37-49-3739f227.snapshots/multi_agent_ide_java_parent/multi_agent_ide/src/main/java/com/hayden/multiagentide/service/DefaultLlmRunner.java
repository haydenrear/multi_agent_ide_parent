package com.hayden.multiagentide.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.embabel.agent.api.common.ContextualPromptElement;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.api.common.PromptRunner;
import com.embabel.agent.api.tool.ToolObject;
import com.hayden.acp_cdc_ai.acp.config.AcpChatOptionsString;
import com.hayden.multiagentide.agent.AskUserQuestionToolAdapter;
import com.hayden.multiagentide.agent.decorator.prompt.LlmCallDecorator;
import com.hayden.multiagentide.config.LlmModelSelectionProperties;
import com.hayden.multiagentide.llm.LlmRunner;
import com.hayden.multiagentide.prompt.PromptContributorService;
import com.hayden.multiagentide.prompt.PromptContext;
import com.hayden.multiagentide.tool.ToolAbstraction;
import com.hayden.multiagentide.tool.ToolContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Default implementation of LlmRunner using Embabel's native prompt contribution pattern.
 * 
 * This implementation:
 * 1. Retrieves applicable PromptContributors based on AgentType
 * 2. Converts them to Embabel's ContextualPromptElement
 * 3. Uses withPromptElements() to inject dynamic content
 * 4. Uses withTemplate() for Jinja template rendering
 * 5. Returns structured responses via createObject()
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultLlmRunner implements LlmRunner {
    
    private final AskUserQuestionToolAdapter askUserQuestionToolAdapter;
    private final ObjectMapper objectMapper;
    private final LlmModelSelectionProperties modelSelectionProperties;
    private final PromptContributorService promptContributorService;

    @Autowired(required = false)
    private List<LlmCallDecorator> llmCallDecorators = new ArrayList<>();

    @Override
    public <T> T runWithTemplate(
            String templateName,
            PromptContext promptContext,
            Map<String, Object> model,
            ToolContext toolContext,
            Class<T> responseClass,
            OperationContext context
    ) {
        String encodedAcpOptions = resolveEncodedAcpOptions(promptContext);
        List<ContextualPromptElement> promptElements = promptContributorService == null
                ? promptContext.promptContributors()
                : promptContributorService.getContributors(promptContext);
        // Get applicable prompt contributors using the full PromptContext
        var aiQuery = context
                .ai()
                .withFirstAvailableLlmOf("acp-chat-model", encodedAcpOptions)
                .withPromptElements(promptElements.toArray(ContextualPromptElement[]::new));

        aiQuery = applyToolContext(aiQuery, toolContext);

        var aiQueryWithTemplate = aiQuery
                .creating(responseClass);

        var llmCallContext = new LlmCallDecorator.LlmCallContext<>(promptContext, toolContext, aiQueryWithTemplate, model, context);

        for (var l : llmCallDecorators) {
            llmCallContext = l.decorate(llmCallContext);
        }

        // Execute and return
        var tObjectCreator = llmCallContext
                .templateOperations();

        T result = tObjectCreator
                .fromTemplate(templateName, model);
        
        return result;
    }

    String resolveEncodedAcpOptions(PromptContext promptContext) {
        if (AcpChatOptionsString.looksLikeEncodedModel(promptContext.modelName())) {
            log.info("Using pre-encoded ACP model for chatId={}", promptContext.chatId().value());
            return promptContext.modelName();
        }

        String requestedModel = normalize(promptContext.modelName());
        String requestedProvider = promptContext.metadata() == null
                ? null
                : normalize(Optional.ofNullable(promptContext.metadata().get("acpProvider"))
                .map(Object::toString)
                .orElse(null));

        if (requestedModel == null) {
            var resolved = modelSelectionProperties.resolve(
                    promptContext.agentType(), promptContext.templateName());
            requestedModel = normalize(resolved.model());
            if (requestedProvider == null) {
                requestedProvider = normalize(resolved.providerWireValue());
            }
            log.info(
                    "Resolved default LLM selection for chatId={} agentType={} template={} model={} provider={}",
                    promptContext.chatId().value(),
                    promptContext.agentType(),
                    promptContext.templateName(),
                    requestedModel,
                    requestedProvider
            );
        } else {
            log.info(
                    "Using explicit LLM selection for chatId={} agentType={} template={} model={} provider={}",
                    promptContext.chatId().value(),
                    promptContext.agentType(),
                    promptContext.templateName(),
                    requestedModel,
                    requestedProvider
            );
        }

        Map<String, Object> runtimeOptions = Map.of();

        if (promptContext.metadata() != null && promptContext.metadata().get("acpOptions") instanceof Map<?, ?> rawOptions) {
            runtimeOptions = rawOptions.entrySet().stream()
                    .filter(entry -> entry.getKey() != null)
                    .collect(java.util.stream.Collectors.toMap(
                            entry -> entry.getKey().toString(),
                            Map.Entry::getValue
                    ));
        }

        return AcpChatOptionsString.create(promptContext.chatId().value(), requestedModel, requestedProvider, runtimeOptions)
                .encodeModel(objectMapper);
    }

    private String normalize(String value) {
        if (value == null || value.isBlank() || AcpChatOptionsString.DEFAULT_MODEL_NAME.equalsIgnoreCase(value)) {
            return null;
        }
        return value;
    }

    private PromptRunner applyToolContext(PromptRunner promptRunner, ToolContext toolContext) {
        ToolContext mergedContext = mergeToolContext(toolContext);
        if (mergedContext.tools().isEmpty()) {
            return promptRunner;
        }
        PromptRunner updated = promptRunner;
        for (ToolAbstraction tool : mergedContext.tools()) {
            updated = switch (tool) {
                case ToolAbstraction.SpringToolCallback value ->
                        updated.withToolObject(new ToolObject(value.toolCallback()));
                case ToolAbstraction.SpringToolCallbackProvider value ->
                        applyToolCallbacks(updated, value.toolCallbackProvider().getToolCallbacks());
                case ToolAbstraction.EmbabelTool value ->
                        updated.withTool(value.tool());
                case ToolAbstraction.EmbabelToolObject value ->
                        updated.withToolObject(value.toolObject());
                case ToolAbstraction.EmbabelToolGroup value ->
                        updated.withToolGroup(value.toolGroup());
                case ToolAbstraction.EmbabelToolGroupRequirement value ->
                        updated.withToolGroup(value.requirement());
                case ToolAbstraction.ToolGroupStrings value ->
                        updated.withToolGroups(value.toolGroups());
                case ToolAbstraction.SkillReference skillReference ->
//                      we're not using the tool, we do a custom prompt contributor
                        updated;
            };
        }
        return updated;
    }

    private ToolContext mergeToolContext(ToolContext toolContext) {
        List<ToolAbstraction> merged = new ArrayList<>();
        merged.add(ToolAbstraction.fromToolCarrier(askUserQuestionToolAdapter));
        if (toolContext != null && !toolContext.tools().isEmpty()) {
            merged.addAll(toolContext.tools());
        }
        return new ToolContext(merged);
    }

    public static PromptRunner applyToolCallbacks(PromptRunner promptRunner, ToolCallback[] toolCallbacks) {
        PromptRunner updated = promptRunner;
        for (ToolCallback toolCallback : toolCallbacks) {
            updated = updated.withToolObject(new ToolObject(toolCallback));
        }
        return updated;
    }
}
