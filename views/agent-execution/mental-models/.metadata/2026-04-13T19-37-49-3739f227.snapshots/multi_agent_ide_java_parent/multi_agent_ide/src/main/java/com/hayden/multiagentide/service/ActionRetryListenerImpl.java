package com.hayden.multiagentide.service;

import com.embabel.agent.core.AgentProcess;
import com.hayden.multiagentide.retry.RetryConfigProperties;
import org.jspecify.annotations.NonNull;
import org.springframework.context.annotation.Lazy;
import com.embabel.agent.spi.common.ActionRetryListener;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.agent.BlackboardHistory;
import com.hayden.acp_cdc_ai.acp.CompactionException;
import com.hayden.multiagentide.agent.ErrorDescriptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.RetryContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeoutException;

/**
 * Captures errors from the Embabel framework retry loop into BlackboardHistory.
 * Classifies throwables into ErrorDescriptor variants so that AgentExecutor and
 * PromptContributors can adapt behavior on retry.
 *
 * <p>Resolves action context from the last unmatched {@link Events.AgentExecutorStartEvent}
 * on the blackboard (one whose {@code requestContextId} has no corresponding
 * {@link Events.AgentExecutorCompleteEvent}). This is the authoritative source for
 * the current LLM call's identity.
 *
 * <p>Returns early (no-op) if:
 * <ul>
 *   <li>No {@code BlackboardHistory} on the process</li>
 *   <li>No unmatched {@code AgentExecutorStartEvent} on the blackboard</li>
 * </ul>
 *
 * <p>Each error gets a unique {@code contextId} created as a child of the start event's
 * {@code nodeId}, preserving the hierarchical ArtifactKey structure.
 *
 * <p>Error counts (for retryCount fields) are scoped to the current execution —
 * only errors since the last {@code AgentExecutorStartEvent} are counted.
 *
 * <p>For compaction errors, polls the ACP session for compaction completion before
 * returning (blocking the retry thread), matching the concurrency model of the
 * existing polling loop in AcpChatModel.
 */
@Component
@Slf4j
public class ActionRetryListenerImpl implements ActionRetryListener {

    @Autowired
    @Lazy
    private EventBus eventBus;

    @Autowired
    private RetryConfigProperties retryConfigProperties;

    @Override
    public void onActionRetry(RetryContext context, Throwable throwable, AgentProcess agentProcess) {
        log.info("ActionRetryListener: running action retry listener: {}",
                agentProcess.getId());

        BlackboardHistory history = agentProcess.last(BlackboardHistory.class);

        if (history == null) {
            log.warn("ActionRetryListener: no BlackboardHistory on AgentProcess {} — cannot record error",
                    agentProcess.getId());
            return;
        }

        // Find the last unmatched AgentExecutorStartEvent — one whose requestContextId
        // has no corresponding AgentExecutorCompleteEvent. This is the current in-flight
        // execution that is being retried.
        Events.AgentExecutorStartEvent startEvent = findLastUnmatchedStart(history);
        if (startEvent == null) {
            log.warn("ActionRetryListener: no unmatched AgentExecutorStartEvent on blackboard for process {} — skipping",
                    agentProcess.getId());
            return;
        }

        String actionName = startEvent.actionName();
        String sessionKey = startEvent.sessionKey();
        // Each error gets its own unique contextId as a child of the start event's nodeId
        ArtifactKey parentContextId = new ArtifactKey(startEvent.nodeId());
        ArtifactKey errorContextId = parentContextId.createChild();

        ErrorDescriptor errorDescriptor = classify(throwable, actionName, sessionKey, errorContextId, history);

        if (history.didRetryAlreadyRunForThisExactExceptionInHierarchy(errorDescriptor))
            return;

        log.info("ActionRetryListener: recorded {} for action={} sessionKey={} retryCount={} on process={}, errorContextId={}, causes={}",
                errorDescriptor.getClass().getSimpleName(), actionName, sessionKey,
                context.getRetryCount(), agentProcess.getId(), errorContextId,
                parseCauses(throwable));

        history.addError(errorDescriptor);

        // Emit the error event for observability
        Events.GraphEvent event = errorDescriptor.toEvent();
        if (event != null) {
            eventBus.publish(event);
        }

        if (errorDescriptor instanceof ErrorDescriptor.CompactionError compactionError) {
            waitForCompaction(compactionError, parentContextId);
        }
    }

    private static @NonNull List<String> parseCauses(Throwable throwable) {
        List<String> causes = new ArrayList<>();

        Throwable cause = throwable;

        do {
            causes.add(cause.getClass().getName());
            cause = throwable.getCause();
        } while (cause != null);

        return causes;
    }

    /**
     * Finds the last AgentExecutorStartEvent whose nodeId has no matching
     * AgentExecutorCompleteEvent (by startNodeId). Returns null if all starts are matched
     * (or none exist).
     *
     * <p>Scans all start events (not just the last one) because intervening operations
     * like controller calls emit their own start+complete pairs. The actual in-flight
     * execution that is being retried may not be the most recent start event.
     *
     * <p>Matching is by {@code startEvent.nodeId() == completeEvent.startNodeId()},
     * since nodeId is unique per execution attempt while requestContextId can be the same
     * across retries.
     */
    Events.AgentExecutorStartEvent findLastUnmatchedStart(BlackboardHistory history) {
        List<Events.AgentExecutorStartEvent> allStarts = history.fromHistory(
                h -> h.getEntriesOfType(Events.AgentExecutorStartEvent.class));
        if (allStarts == null || allStarts.isEmpty()) {
            return null;
        }

        // Collect all completed nodeIds into a set for O(1) lookup
        List<Events.AgentExecutorCompleteEvent> allCompletes = history.fromHistory(
                h -> h.getEntriesOfType(Events.AgentExecutorCompleteEvent.class));
        Set<String> completedNodeIds = new HashSet<>();
        if (allCompletes != null) {
            for (Events.AgentExecutorCompleteEvent complete : allCompletes) {
                if (complete.startNodeId() != null) {
                    completedNodeIds.add(complete.startNodeId());
                }
            }
        }

        // Walk backwards through starts to find the last one without a matching complete
        for (int i = allStarts.size() - 1; i >= 0; i--) {
            Events.AgentExecutorStartEvent start = allStarts.get(i);
            String nodeId = start.nodeId();
            if (nodeId == null || nodeId.isEmpty()) {
                // Legacy event without nodeId — fall back to positional check
                if (!history.isLastExecutionComplete()) {
                    return start;
                }
                continue;
            }
            if (!completedNodeIds.contains(nodeId)) {
                return start;
            }
        }

        return null;
    }

    /**
     * Classifies a throwable into the appropriate ErrorDescriptor variant.
     * Error counts span the entire retry sequence (from the first unmatched
     * AgentExecutorStartEvent forward), not just the most recent attempt.
     *
     * <p>Each new ErrorDescriptor carries an {@link ErrorDescriptor.ErrorContext} that
     * accumulates all previous errors in the retry sequence, so any single descriptor
     * gives a complete picture of the error chain.
     */
    ErrorDescriptor classify(Throwable throwable, String actionName, String sessionKey,
                             ArtifactKey contextId, BlackboardHistory history) {
        // Build the ErrorContext by grabbing the last error for this session and appending it
        ErrorDescriptor previousError = history.errorType(sessionKey);
        ErrorDescriptor.ErrorContext errCtx = previousError != null
                ? previousError.errorContext().withError(previousError)
                : ErrorDescriptor.ErrorContext.EMPTY;

        // Extract message early so all branches can use it as errorDetail
        String message = throwable.getMessage();
        if (message == null) message = "";
        String lowerMessage = message.toLowerCase();

        if (throwable instanceof CompactionException) {
            ErrorDescriptor.CompactionStatus status = history.compactionStatusForRetrySequence(sessionKey).next();
            return new ErrorDescriptor.CompactionError(actionName, sessionKey, status, false, message, throwable, contextId, errCtx);
        }

        if (lowerMessage.contains("compacting") || lowerMessage.contains("prompt is too long")) {
            ErrorDescriptor.CompactionStatus status = history.compactionStatusForRetrySequence(sessionKey).next();
            return new ErrorDescriptor.CompactionError(actionName, sessionKey, status, false, message, throwable, contextId, errCtx);
        }

        if (lowerMessage.contains("unparsed tool call".toLowerCase())) {
            int retryCount = (int) history.errorCountForRetrySequence(ErrorDescriptor.UnparsedToolCallError.class, sessionKey) + 1;
            return new ErrorDescriptor.UnparsedToolCallError(actionName, sessionKey, retryCount, message, message, throwable, contextId, errCtx);
        }

        if (throwable instanceof TimeoutException || lowerMessage.contains("timeout")) {
            int retryCount = (int) history.errorCountForRetrySequence(ErrorDescriptor.TimeoutError.class, sessionKey) + 1;
            return new ErrorDescriptor.TimeoutError(actionName, sessionKey, retryCount, message, throwable, contextId, errCtx);
        }

        if (lowerMessage.contains("ACP returned unparsed tool call as final structured output:".toLowerCase())) {
            int retryCount = (int) history.errorCountForRetrySequence(ErrorDescriptor.UnparsedToolCallError.class, sessionKey) + 1;
            return new ErrorDescriptor.UnparsedToolCallError(actionName, sessionKey, retryCount, message, message, throwable, contextId, errCtx);
        }

        if (lowerMessage.contains("null") && lowerMessage.contains("result")
                || lowerMessage.contains("empty response")) {
            int retryCount = (int) history.errorCountForRetrySequence(ErrorDescriptor.NullResultError.class, sessionKey) + 1;
            return new ErrorDescriptor.NullResultError(actionName, sessionKey, retryCount, 5, message, throwable, contextId, errCtx);
        }

        if (lowerMessage.contains("incomplete json") || lowerMessage.contains("truncated")) {
            int retryCount = (int) history.errorCountForRetrySequence(ErrorDescriptor.IncompleteJsonError.class, sessionKey) + 1;
            return new ErrorDescriptor.IncompleteJsonError(actionName, sessionKey, retryCount, message, message, throwable, contextId, errCtx);
        }

        if (lowerMessage.contains("could not parse the given text to the desired target type")
                && lowerMessage.contains("\"tool:\"") && lowerMessage.contains("call_controller")) {
            int retryCount = (int) history.errorCountForRetrySequence(ErrorDescriptor.UnparsedToolCallError.class, sessionKey) + 1;
            message += """
                    It seems you tried to return a tool call for the controller instead of using your MCP tools.
                    Please use the MCP tool call to justify, and then return your structured response after validating.
                    Please do not try to return a tool call to the controller.
                    """;
            return new ErrorDescriptor.UnparsedToolCallError(actionName, sessionKey, retryCount, message, message, throwable, contextId, errCtx);
        }

        if (lowerMessage.contains("could not parse the given text to the desired target type")
                && lowerMessage.contains("\"tool:\"")) {
            int retryCount = (int) history.errorCountForRetrySequence(ErrorDescriptor.UnparsedToolCallError.class, sessionKey) + 1;
            message += """
                    It seems you tried to return a tool call for the controller instead of using your MCP tools.
                    Please use the MCP tool call to justify, and then return your structured response after validating.
                    Please do not try to return a tool call to the controller.
                    """;
            return new ErrorDescriptor.UnparsedToolCallError(actionName, sessionKey, retryCount, message, message, throwable, contextId, errCtx);
        }

        if (lowerMessage.contains("could not parse the given text to the desired target type")) {
            int retryCount = (int) history.errorCountForRetrySequence(ErrorDescriptor.IncompleteJsonError.class, sessionKey) + 1;
            return new ErrorDescriptor.IncompleteJsonError(actionName, sessionKey, retryCount, message, message, throwable, contextId, errCtx);
        }

        if (throwable instanceof com.fasterxml.jackson.core.JsonParseException
                || lowerMessage.contains("parse")) {
            return new ErrorDescriptor.ParseError(actionName, sessionKey, message, message, throwable, contextId, errCtx);
        }


        // Fallback: treat as parse error with raw message
        return new ErrorDescriptor.ParseError(actionName, sessionKey, message, message, throwable, contextId, errCtx);
    }

    /**
     * Blocks until compaction is complete or max polls exceeded.
     */
    private void waitForCompaction(ErrorDescriptor.CompactionError error, ArtifactKey contextId) {
        log.info("ActionRetryListener: waiting for compaction to complete for action={}",
                error.actionName());

        for (int poll = 1; poll <= retryConfigProperties.getMaxCompactionPolls(); poll++) {
            try {
                Thread.sleep(retryConfigProperties.getCompactionPollIntervalMs());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("ActionRetryListener: compaction wait interrupted for action={}",
                        error.actionName());
                return;
            }
            log.info("ActionRetryListener: compaction poll {}/{} for action={}",
                    poll, retryConfigProperties.getMaxCompactionPolls(), error.actionName());
        }

        log.warn("ActionRetryListener: compaction still active after {} polls for action={} — returning for framework retry",
                retryConfigProperties.getMaxCompactionPolls(), error.actionName());
    }
}
