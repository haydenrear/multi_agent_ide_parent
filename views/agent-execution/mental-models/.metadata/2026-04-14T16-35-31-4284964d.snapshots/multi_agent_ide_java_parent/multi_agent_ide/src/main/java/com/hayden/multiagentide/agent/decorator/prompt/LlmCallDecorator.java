package com.hayden.multiagentide.agent.decorator.prompt;

import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.api.common.PromptRunner;
import com.hayden.multiagentide.tool.ToolContext;
import com.hayden.multiagentide.prompt.PromptContext;
import lombok.Builder;
import lombok.With;

import java.util.Map;

public interface LlmCallDecorator {

    default int order() {
        return 0;
    }

    @Builder(toBuilder = true)
    @With
    record LlmCallContext<T>(
            PromptContext promptContext,
            ToolContext tcc,
            PromptRunner.Creating<T> templateOperations,
            Map<String, Object> templateArgs,
            OperationContext op
    ) {}

    default <T> LlmCallContext<T> decorate(LlmCallContext<T> promptContext) {
        return promptContext;
    }
}
