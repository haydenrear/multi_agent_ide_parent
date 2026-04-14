package com.hayden.multiagentide.agent;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.acp_cdc_ai.acp.events.HasContextId;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Sealed interface representing the error state for a given action execution.
 * Stored in BlackboardHistory entries. Matchable by AgentExecutor and PromptContributors.
 *
 * <p>Every variant carries an explicit {@code sessionKey} resolved from the last
 * {@link Events.AgentExecutorStartEvent} on the blackboard — never derived from
 * {@code contextId.parent()}.
 *
 * <p>Every error variant carries an {@link ErrorContext} that accumulates the ordered
 * history of all previous errors in the retry sequence. This lets any consumer inspect
 * the full error chain from a single ErrorDescriptor without walking the blackboard.
 *
 * <p>Every variant — including {@link NoError} — carries a {@code contextId} that is a
 * child of the {@code AgentExecutorStartEvent.nodeId}, which is itself a child of the
 * chat session key ({@code chatId}). This preserves the hierarchical ArtifactKey structure:
 *
 * <pre>
 *   chatId (session key)
 *     └── AgentExecutorStartEvent.nodeId = chatId.createChild()
 *           └── ErrorDescriptor.contextId = startEventNodeId.createChild()
 * </pre>
 */
public sealed interface ErrorDescriptor extends HasContextId
        permits ErrorDescriptor.NoError,
                ErrorDescriptor.CompactionError,
                ErrorDescriptor.ParseError,
                ErrorDescriptor.TimeoutError,
                ErrorDescriptor.UnparsedToolCallError,
                ErrorDescriptor.NullResultError,
                ErrorDescriptor.IncompleteJsonError {

    Throwable lastException();

    /**
     * Converts this error descriptor to the corresponding GraphEvent for event bus emission.
     * Returns null for NoError.
     */
    Events.GraphEvent toEvent();

    /**
     * Returns a human-readable detail string describing this error, suitable for
     * inclusion in retry template models as {@code errorDetail}.
     */
    default String errorDetail() {
        return "";
    }

    /**
     * Returns the accumulated error context from previous errors in this retry sequence.
     * NoError returns EMPTY by default.
     */
    default ErrorContext errorContext() {
        return ErrorContext.EMPTY;
    }

    /**
     * Accumulated history of previous errors in a retry sequence. Each ErrorDescriptor
     * carries the full ordered list of errors that preceded it, so any single descriptor
     * gives a complete picture of the retry chain.
     *
     * <p>The list is flat — entries do not carry their own ErrorContext to avoid nesting.
     * Use {@link #withError} to build the next context from the current one.
     */
    record ErrorContext(List<ErrorEntry> previousErrors) {
        public static final ErrorContext EMPTY = new ErrorContext(List.of());

        /**
         * Creates a new ErrorContext with the given error appended.
         */
        public ErrorContext withError(ErrorDescriptor error) {
            var entry = ErrorEntry.from(error);
            var newList = new ArrayList<>(previousErrors);
            newList.add(entry);
            return new ErrorContext(List.copyOf(newList));
        }

        public boolean hasErrors() {
            return !previousErrors.isEmpty();
        }

        public int errorCount() {
            return previousErrors.size();
        }
    }

    /**
     * A flattened summary of an ErrorDescriptor for use in ErrorContext.
     * Avoids recursive nesting by not carrying its own ErrorContext.
     */
    record ErrorEntry(
            String errorType,
            String actionName,
            String sessionKey,
            String detail,
            Throwable lastException,
            ArtifactKey contextId
    ) {
        public static ErrorEntry from(ErrorDescriptor error) {
            String type = error.getClass().getSimpleName();
            String action = switch (error) {
                case NoError _ -> "";
                case CompactionError e -> e.actionName();
                case ParseError e -> e.actionName();
                case TimeoutError e -> e.actionName();
                case UnparsedToolCallError e -> e.actionName();
                case NullResultError e -> e.actionName();
                case IncompleteJsonError e -> e.actionName();
            };
            String session = switch (error) {
                case NoError e -> e.sessionKey();
                case CompactionError e -> e.sessionKey();
                case ParseError e -> e.sessionKey();
                case TimeoutError e -> e.sessionKey();
                case UnparsedToolCallError e -> e.sessionKey();
                case NullResultError e -> e.sessionKey();
                case IncompleteJsonError e -> e.sessionKey();
            };
            String detail = switch (error) {
                case NoError _ -> "";
                case CompactionError e -> "status=" + e.compactionStatus();
                case ParseError e -> e.rawOutput() != null ? e.rawOutput() : "";
                case TimeoutError e -> "retryCount=" + e.retryCount();
                case UnparsedToolCallError e -> e.toolCallText() != null ? e.toolCallText() : "";
                case NullResultError e -> "retry=" + e.retryCount() + "/" + e.maxRetries();
                case IncompleteJsonError e -> "retry=" + e.retryCount();
            };
            return new ErrorEntry(type, action, session, detail, error.lastException(), error.contextId());
        }
    }

    enum CompactionStatus {
        NONE,
        FIRST,
        MULTIPLE;

        public CompactionStatus next() {
            return switch (this) {
                case NONE -> FIRST;
                case FIRST, MULTIPLE -> MULTIPLE;
            };
        }
    }

    record NoError(String sessionKey, ArtifactKey contextId) implements ErrorDescriptor {
        @Override
        public Throwable lastException() {
            return null;
        }

        @Override
        public Events.GraphEvent toEvent() {
            return null;
        }
    }

    record CompactionError(
            String actionName,
            String sessionKey,
            CompactionStatus compactionStatus,
            boolean compactionCompleted,
            String errorDetail,
            Throwable lastException,
            ArtifactKey contextId,
            ErrorContext errorContext
    ) implements ErrorDescriptor {
        @Override
        public Events.GraphEvent toEvent() {
            return Events.CompactionEvent.of(
                    "CompactionError classified — action=" + actionName
                            + " status=" + compactionStatus,
                    contextId);
        }
    }

    record ParseError(
            String actionName,
            String sessionKey,
            String rawOutput,
            String errorDetail,
            Throwable lastException,
            ArtifactKey contextId,
            ErrorContext errorContext
    ) implements ErrorDescriptor {
        @Override
        public Events.GraphEvent toEvent() {
            return Events.ParseErrorEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .timestamp(Instant.now())
                    .nodeId(contextId.value())
                    .sessionKey(sessionKey)
                    .actionName(actionName)
                    .rawOutput(rawOutput)
                    .build();
        }
    }

    record TimeoutError(
            String actionName,
            String sessionKey,
            int retryCount,
            String errorDetail,
            Throwable lastException,
            ArtifactKey contextId,
            ErrorContext errorContext
    ) implements ErrorDescriptor {
        @Override
        public Events.GraphEvent toEvent() {
            return Events.TimeoutEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .timestamp(Instant.now())
                    .nodeId(contextId.value())
                    .sessionKey(sessionKey)
                    .retryCount(retryCount)
                    .detail(errorDetail)
                    .build();
        }
    }

    record UnparsedToolCallError(
            String actionName,
            String sessionKey,
            int numRetries,
            String toolCallText,
            String errorDetail,
            Throwable lastException,
            ArtifactKey contextId,
            ErrorContext errorContext
    ) implements ErrorDescriptor {
        @Override
        public Events.GraphEvent toEvent() {
            return Events.UnparsedToolCallEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .timestamp(Instant.now())
                    .nodeId(contextId.value())
                    .sessionKey(sessionKey)
                    .toolCallText(toolCallText)
                    .build();
        }
    }

    record NullResultError(
            String actionName,
            String sessionKey,
            int retryCount,
            int maxRetries,
            String errorDetail,
            Throwable lastException,
            ArtifactKey contextId,
            ErrorContext errorContext
    ) implements ErrorDescriptor {
        @Override
        public Events.GraphEvent toEvent() {
            return Events.NullResultEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .timestamp(Instant.now())
                    .nodeId(contextId.value())
                    .sessionKey(sessionKey)
                    .retryCount(retryCount)
                    .maxRetries(maxRetries)
                    .build();
        }
    }

    record IncompleteJsonError(
            String actionName,
            String sessionKey,
            int retryCount,
            String rawFragment,
            String errorDetail,
            Throwable lastException,
            ArtifactKey contextId,
            ErrorContext errorContext
    ) implements ErrorDescriptor {
        @Override
        public Events.GraphEvent toEvent() {
            return Events.IncompleteJsonEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .timestamp(Instant.now())
                    .nodeId(contextId.value())
                    .sessionKey(sessionKey)
                    .retryCount(retryCount)
                    .rawFragment(rawFragment)
                    .build();
        }
    }
}
