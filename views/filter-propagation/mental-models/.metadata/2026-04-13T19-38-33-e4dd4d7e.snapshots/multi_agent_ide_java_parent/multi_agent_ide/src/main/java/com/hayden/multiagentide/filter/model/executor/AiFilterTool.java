package com.hayden.multiagentide.filter.model.executor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.filter.model.layer.FilterContext;
import com.hayden.multiagentide.filter.service.FilterDescriptor;
import com.hayden.multiagentide.filter.service.FilterResult;
import com.hayden.multiagentide.llm.AgentLlmExecutor;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executor that delegates to an AI model for filtering decisions.
 */
@Slf4j
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public final class AiFilterTool<I, O>
        implements ExecutableTool<AgentModels.AiFilterRequest, AgentModels.AiFilterResult, FilterContext.AiFilterContext> {

    public static final String TEMPLATE_NAME = "filter/ai_filter";

    @Schema(description = "Specific guidance for this filter — what it should look for and how to decide. This is the AI instruction prompt.", requiredMode = Schema.RequiredMode.REQUIRED)
    private String registrarPrompt;

    @Schema(description = "Session reuse strategy. PER_INVOCATION=fresh session each time; SAME_SESSION_FOR_ACTION=reuse within one action pair; SAME_SESSION_FOR_ALL=single shared session.", allowableValues = {"PER_INVOCATION", "SAME_SESSION_FOR_ACTION", "SAME_SESSION_FOR_ALL", "SAME_SESSION_FOR_AGENT"})
    private SessionMode sessionMode;

    @Schema(description = "Optional config version for cache-busting filter state.")
    private String configVersion;

    @Autowired
    @JsonIgnore
    private AgentLlmExecutor agentLlmExecutor;

    @JsonCreator
    public AiFilterTool(
            @JsonProperty("registrarPrompt") String registrarPrompt,
            @JsonProperty("sessionMode") SessionMode sessionMode,
            @JsonProperty("configVersion") String configVersion) {
        this.registrarPrompt = registrarPrompt;
        this.sessionMode = sessionMode;
        this.configVersion = configVersion;
    }

    public enum SessionMode {
        PER_INVOCATION,
        SAME_SESSION_FOR_ALL,
        SAME_SESSION_FOR_ACTION,
        SAME_SESSION_FOR_AGENT
    }

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        SimpleModule module = new SimpleModule();
        OBJECT_MAPPER.registerModule(module);
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
    }

    @Override
    public FilterEnums.ExecutorType executorType() {
        return FilterEnums.ExecutorType.AI;
    }

    @Override
    public @NotNull FilterResult<AgentModels.AiFilterResult> apply(AgentModels.AiFilterRequest i, FilterContext.AiFilterContext ctx) {
        if (ctx == null || ctx.directExecutorArgs() == null || agentLlmExecutor == null) {
            return aiFailResult("AI filter context is not fully initialized");
        }
        try {
            AgentModels.AiFilterResult aiResult = agentLlmExecutor.runDirect(ctx.directExecutorArgs());
            if (aiResult == null) {
                return aiFailResult("LLM returned null result");
            }
            return new FilterResult<>(aiResult, buildAiFilterDescriptor());
        } catch (Exception e) {
            log.error("Error when attempting to filter {}.", i, e);
            return aiFailResult("LLM call threw error: %s".formatted(e.getMessage()));
        }
    }

    private @NonNull FilterResult<AgentModels.AiFilterResult> aiFailResult(String message) {
        return new FilterResult<>(
                AgentModels.AiFilterResult.builder()
                        .successful(false)
                        .errorMessage(message)
                        .output(List.of())
                        .build(),
                buildAiFilterDescriptor());
    }

    private FilterDescriptor buildAiFilterDescriptor() {
        Map<String, String> details = new LinkedHashMap<>();
        putIfPresent(details, "registrarPrompt", registrarPrompt);
        putIfPresent(details, "sessionMode", sessionMode == null ? null : sessionMode.name());
        details.put("templateName", TEMPLATE_NAME);
        putIfPresent(details, "configVersion", configVersion);

        FilterDescriptor.Entry entry = new FilterDescriptor.Entry(
                "EXECUTOR", null, null, null, null, null,
                "TRANSFORMED",
                FilterEnums.ExecutorType.AI.name(),
                details,
                List.of()
        );
        return new FilterDescriptor.SimpleFilterDescriptor(List.of(), entry);
    }

    private void putIfPresent(Map<String, String> target, String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            return;
        }
        target.put(key, value);
    }

    @Override
    public String configVersion() {
        return configVersion;
    }

    public String registrarPrompt() {
        return registrarPrompt;
    }

    public SessionMode sessionMode() {
        return sessionMode;
    }

    @JsonIgnore
    public String templateName() {
        return TEMPLATE_NAME;
    }
}
