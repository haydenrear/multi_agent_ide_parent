package com.hayden.multiagentide.agent;

import org.jspecify.annotations.Nullable;

/**
 * Per-action configuration mapping error states to alternative template names.
 * Stored as a field on AgentActionMetadata. When a field is null, resolveTemplate
 * falls back to the generic retry template from {@link #DEFAULT}.
 */
public record ErrorTemplates(
        @Nullable String compactionFirstTemplate,
        @Nullable String compactionMultipleTemplate,
        @Nullable String parseErrorTemplate,
        @Nullable String timeoutTemplate,
        @Nullable String unparsedToolCallTemplate,
        @Nullable String nullResultTemplate,
        @Nullable String incompleteJsonTemplate
) {
    /**
     * Default error templates — generic per error type, not per action.
     * Used by the AgentActionMetadata convenience constructor.
     */
    public static final ErrorTemplates DEFAULT = new ErrorTemplates(
            "retry/compaction",
            "retry/compaction",
            "retry/parse_error",
            "retry/timeout",
            "retry/unparsed_tool_call",
            "retry/null_result",
            "retry/incomplete_json"
    );
}
