package com.hayden.multiagentide.prompt;

import com.hayden.multiagentide.agent.ErrorDescriptor;

/**
 * Standalone interface for prompt contributors and prompt contributor factories that need
 * to control their inclusion during retry scenarios. Applicable to both
 * {@link PromptContributor} and {@link PromptContributorFactory} implementations.
 *
 * <p>Contributors/factories that do NOT implement {@code RetryAware} are excluded by default
 * on any retry — they must opt in to retry inclusion by implementing this interface.
 *
 * <p>Each method receives the specific {@link ErrorDescriptor} variant and returns
 * {@code true} to include this contributor/factory during that retry type,
 * {@code false} to exclude. All methods default to {@code false}.
 */
public interface RetryAware {

    default boolean includeOnCompaction(ErrorDescriptor.CompactionError error) {
        return false;
    }

    default boolean includeOnCompactionMultiple(ErrorDescriptor.CompactionError error) {
        return false;
    }

    default boolean includeOnParseError(ErrorDescriptor.ParseError error) {
        return false;
    }

    default boolean includeOnTimeout(ErrorDescriptor.TimeoutError error) {
        return false;
    }

    default boolean includeOnUnparsedToolCall(ErrorDescriptor.UnparsedToolCallError error) {
        return false;
    }

    default boolean includeOnNullResult(ErrorDescriptor.NullResultError error) {
        return false;
    }

    default boolean includeOnIncompleteJson(ErrorDescriptor.IncompleteJsonError error) {
        return false;
    }
}
