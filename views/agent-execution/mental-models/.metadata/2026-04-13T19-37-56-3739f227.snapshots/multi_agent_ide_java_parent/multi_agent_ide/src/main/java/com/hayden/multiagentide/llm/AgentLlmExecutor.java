package com.hayden.multiagentide.llm;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.multiagentide.prompt.PromptContext;
import com.hayden.multiagentide.tool.ToolContext;
import lombok.Builder;

import java.util.Map;
import java.util.function.Supplier;

/**
 * LLM executor that wraps calls with agent-session-aware infrastructure:
 * event emission (start/complete), error descriptor resolution from blackboard history,
 * and error-specific template overrides.
 * <p>
 * Callers that already build their own PromptContext / ToolContext should use this
 * instead of raw {@link LlmRunner} to get retry observability and error-aware prompt filtering.
 */
public interface AgentLlmExecutor {

    /**
     * Execute an LLM call with agent executor semantics (events, error descriptors, error templates).
     *
     * @param args pre-built contexts and metadata
     * @return the structured response from the LLM
     */
    <T> T runDirect(DirectExecutorArgs<T> args);

    @Builder(toBuilder = true)
    record DirectExecutorArgs<T>(
            Class<T> responseClazz,
            String agentName,
            String actionName,
            String methodName,
            String template,
            PromptContext promptContext,
            Map<String, Object> templateModel,
            ToolContext toolContext,
            OperationContext operationContext,
            /**
             * Optional callback that produces a fresh DirectExecutorArgs on retry.
             * When provided, each retry attempt calls {@code refresh.get()} to re-run
             * the full decoration pipeline (request, prompt, tool decorators) so that
             * error descriptors and decorator state are up-to-date for the new attempt.
             * When null, the same args are reused on retry.
             */
            Supplier<DirectExecutorArgs<T>> refresh
    ) {}
}
