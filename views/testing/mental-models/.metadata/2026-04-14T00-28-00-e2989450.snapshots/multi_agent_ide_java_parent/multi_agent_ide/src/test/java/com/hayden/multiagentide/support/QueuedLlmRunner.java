package com.hayden.multiagentide.support;

import com.embabel.agent.api.common.OperationContext;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hayden.multiagentide.llm.LlmRunner;
import com.hayden.multiagentide.tool.ToolContext;
import com.hayden.multiagentide.agent.BlackboardHistory;
import com.hayden.multiagentide.agent.ErrorDescriptor;
import com.hayden.multiagentide.prompt.PromptContext;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.function.BiConsumer;

/**
 * Test implementation of LlmRunner that returns pre-queued responses.
 * 
 * This allows tests to:
 * 1. Queue up expected LLM responses in order
 * 2. Let real agent code execute (no need to mock individual actions)
 * 3. Verify the workflow executes correctly with those responses
 * 4. Execute callbacks when responses are dequeued (to simulate agent side effects)
 * 5. Optionally serialize each call's input/output to a single markdown log file
 * 
 * Usage:
 * <pre>
 * queuedLlmRunner.setLogFile(Path.of("test_work/worktree_merge/my_test.md"));
 * queuedLlmRunner.enqueue(OrchestratorRouting.builder()...build());
 * queuedLlmRunner.enqueue(DiscoveryAgentRouting.builder()...build(), (response, ctx) -&gt; {
 *     // Simulate agent work: commit files to the agent's worktree
 * });
 * // ... run the workflow
 * queuedLlmRunner.assertAllConsumed(); // verify all responses were used
 * </pre>
 */
@Slf4j
public class QueuedLlmRunner implements LlmRunner {

    private static final ObjectMapper LOG_MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .enable(SerializationFeature.INDENT_OUTPUT)
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private record QueuedResponse(Object response, BiConsumer<Object, OperationContext> callback,
                                     RuntimeException errorToThrow) {
        QueuedResponse(Object response) {
            this(response, null, null);
        }

        QueuedResponse(Object response, BiConsumer<Object, OperationContext> callback) {
            this(response, callback, null);
        }

        static QueuedResponse error(RuntimeException exception) {
            return new QueuedResponse(null, null, exception);
        }

        boolean isError() {
            return errorToThrow != null;
        }
    }

    private final Queue<QueuedResponse> responseQueue = new LinkedList<>();
    private int callCount = 0;

    /**
     * When set, each runWithTemplate call appends request/response as a markdown
     * section to this file. The file is created with a metadata header on first write.
     */
    @Getter @Setter
    private Path logFile;

    /**
     * Optional markdown file where a snapshot of blackboard history is appended
     * for each LLM call.
     */
    @Getter @Setter
    private Path blackboardHistoryLogFile;

    /** Test class name written into the markdown header. */
    @Getter @Setter
    private String testClassName;

    /** Test method name written into the markdown header. */
    @Getter @Setter
    private String testMethodName;

    private boolean headerWritten = false;
    private boolean historyHeaderWritten = false;

    @Setter
    private String thread;

    /**
     * Optional trace writer for graph snapshots and event streaming.
     * When set, a graph snapshot is appended after each LLM call.
     */
    @Getter @Setter
    private TestTraceWriter traceWriter;

    /**
     * Graph repository reference for graph snapshots.
     * Must be set alongside traceWriter for snapshots to work.
     */
    @Setter
    private com.hayden.multiagentide.repository.GraphRepository graphRepository;

    /**
     * Collected call records (always populated, regardless of logFile).
     */
    @Getter
    private final List<CallRecord> callRecords = new ArrayList<>();

    public record CallRecord(
            int callIndex,
            String templateName,
            String requestType,
            Object decoratedRequest,
            String responseType,
            Object response,
            Map<String, Object> templateModel
    ) {}

    /**
     * Enqueue a response to be returned by the next runWithTemplate call.
     * Responses are returned in FIFO order.
     */
    public <T> void enqueue(T response) {
        responseQueue.offer(new QueuedResponse(response));
    }

    /**
     * Enqueue a response with a callback that fires when the response is dequeued.
     * The callback receives the response and the OperationContext, and fires BEFORE
     * the response is returned to the caller.
     *
     * <p>Note: The callback fires after {@code decorateRequest} has run on the input
     * (so the request on the blackboard has worktree context), but before
     * {@code decorateRouting} runs on the result. Resolve worktree info from the
     * OperationContext, not from the response.</p>
     */
    @SuppressWarnings("unchecked")
    public <T> void enqueue(T response, BiConsumer<T, OperationContext> onDequeue) {
        responseQueue.offer(new QueuedResponse(response, (BiConsumer<Object, OperationContext>) onDequeue));
    }

    /**
     * Enqueue an error that will be thrown instead of returning a response.
     * The exception propagates through AgentExecutor.run() into the Embabel retry
     * framework, which invokes ActionRetryListenerImpl.onActionRetry() to classify
     * the error. On retry, the next item in the queue (typically the real response)
     * is returned.
     *
     * <p>Usage: to test retry behavior, call enqueueError first, then enqueue the
     * real response for the same template. The framework retries and gets the success.</p>
     */
    public void enqueueError(RuntimeException exception) {
        responseQueue.offer(QueuedResponse.error(exception));
    }

    /**
     * Insert a response at the front of the queue, so it will be dequeued next.
     * Useful from within callbacks that need to enqueue responses for nested calls
     * (e.g., agent-to-agent calls made during a workflow step).
     */
    public <T> void enqueueNext(T response) {
        ((LinkedList<QueuedResponse>) responseQueue).addFirst(new QueuedResponse(response));
    }

    public int getCallCount() {
        return callCount;
    }

    public int getRemainingCount() {
        return responseQueue.size();
    }

    public void assertAllConsumed() {
        if (!responseQueue.isEmpty()) {
            throw new AssertionError("Expected all queued responses to be consumed, but " +
                    responseQueue.size() + " remain");
        }

    }

    public void clear() {
        responseQueue.clear();
        callCount = 0;
        callRecords.clear();
        logFile = null;
        blackboardHistoryLogFile = null;
        testClassName = null;
        testMethodName = null;
        headerWritten = false;
        historyHeaderWritten = false;
        if (traceWriter != null) {
            traceWriter.clear();
        }
        traceWriter = null;
        graphRepository = null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T runWithTemplate(
            String templateName,
            PromptContext promptContext,
            Map<String, Object> model,
            ToolContext toolContext,
            Class<T> responseClass,
            OperationContext context
    ) {

        callCount++;

        if (responseQueue.isEmpty()) {
            throw new IllegalStateException(
                    "No more queued responses available. Called runWithTemplate " + callCount +
                    " times with template: " + templateName);
        }

        QueuedResponse queued = responseQueue.poll();

        // If this is an error entry, throw the exception to trigger retry
        if (queued.isError()) {
            log.info("QueuedLlmRunner: throwing enqueued error for template {} (call {}): {}",
                    templateName, callCount, queued.errorToThrow().getMessage());
            throw queued.errorToThrow();
        }

        Object response = queued.response();

        // Fire callback before returning (if present)
        if (queued.callback() != null) {
            try {
                queued.callback().accept(response, context);
            } catch (Exception e) {
                log.error("QueuedLlmRunner callback failed for template: {}", templateName, e);
                throw new IllegalStateException(
                        "QueuedLlmRunner callback failed for template: " + templateName, e);
            }
        }

        // Verify the response type matches what's expected
        if (!responseClass.isInstance(response)) {
            throw new IllegalStateException(
                    "Expected response of type " + responseClass.getName() +
                    " but got " + response.getClass().getName() +
                    " for template: " + templateName);
        }

        // Record and optionally serialize to markdown log
        Object decoratedRequest = promptContext != null ? promptContext.currentRequest() : null;
        CallRecord record = new CallRecord(
                callCount,
                templateName,
                decoratedRequest != null ? decoratedRequest.getClass().getSimpleName() : "null",
                decoratedRequest,
                response.getClass().getSimpleName(),
                response,
                model
        );
        callRecords.add(record);
        appendToLog(record);
        appendBlackboardHistory(record, context);
        appendGraphSnapshot(record);

        return (T) response;
    }

    private void appendGraphSnapshot(CallRecord record) {
        if (traceWriter != null && graphRepository != null) {
            traceWriter.appendGraphSnapshot(record.callIndex(), record.templateName(), graphRepository);
        }
    }

    private void appendToLog(CallRecord record) {
        if (logFile == null) {
            return;
        }
        try {
            Files.createDirectories(logFile.getParent());

            if (!headerWritten) {
                writeHeader();
                headerWritten = true;
            }

            StringBuilder sb = new StringBuilder();
            sb.append("## Call %d: `%s`\n\n".formatted(record.callIndex(), record.templateName()));
            sb.append("**Request type**: `%s`  \n".formatted(record.requestType()));
            sb.append("**Response type**: `%s`  \n\n".formatted(record.responseType()));

            if (record.decoratedRequest() != null) {
                sb.append("### Decorated Request (`%s`)\n\n".formatted(record.requestType()));
                sb.append("```json\n");
                sb.append(safeSerialize(record.decoratedRequest()));
                sb.append("\n```\n\n");
            }

            if (record.templateModel() != null && !record.templateModel().isEmpty()) {
                sb.append("### Template Model\n\n");
                sb.append("```json\n");
                sb.append(safeSerialize(record.templateModel()));
                sb.append("\n```\n\n");
            }

            sb.append("### Response (`%s`)\n\n".formatted(record.responseType()));
            sb.append("```json\n");
            sb.append(safeSerialize(record.response()));
            sb.append("\n```\n\n");

            sb.append("---\n\n");

            Files.writeString(logFile, sb.toString(), StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.warn("Failed to append call record {} to {}", record.callIndex(), logFile, e);
        }
    }

    private void writeHeader() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("# QueuedLlmRunner Call Log\n\n");
            sb.append("| Field | Value |\n");
            sb.append("|-------|-------|\n");
            sb.append("| **Test class** | `%s` |\n".formatted(
                    testClassName != null ? testClassName : "unknown"));
            sb.append("| **Test method** | `%s` |\n".formatted(
                    testMethodName != null ? testMethodName : "unknown"));
            sb.append("| **Started at** | %s |\n".formatted(Instant.now()));
            sb.append("\n---\n\n");

            Files.writeString(logFile, sb.toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            log.warn("Failed to write header to {}", logFile, e);
        }
    }

    private void appendBlackboardHistory(CallRecord record, OperationContext context) {
        if (blackboardHistoryLogFile == null) {
            return;
        }
        try {
            Files.createDirectories(blackboardHistoryLogFile.getParent());
            if (!historyHeaderWritten) {
                writeHistoryHeader();
                historyHeaderWritten = true;
            }

            BlackboardHistory history = context != null ? BlackboardHistory.getEntireBlackboardHistory(context) : null;
            List<BlackboardHistory.Entry> entries = history != null ? history.copyOfEntries() : List.of();

            StringBuilder sb = new StringBuilder();
            sb.append("## Call %d: `%s`\n\n".formatted(record.callIndex(), record.templateName()));
            sb.append("History entries: `%d`\n\n".formatted(entries.size()));
            if (history != null) {
                var lastError = history.errorType(null);
                sb.append("- errorType: `%s`\n".formatted(
                        lastError != null ? lastError.getClass().getSimpleName() : "null"));
                sb.append("- compactionStatus: `%s`\n".formatted(history.compactionStatus()));
                if (lastError != null && lastError.errorContext().hasErrors()) {
                    sb.append("- errorContext: `%d previous errors` [%s]\n".formatted(
                            lastError.errorContext().errorCount(),
                            lastError.errorContext().previousErrors().stream()
                                    .map(ErrorDescriptor.ErrorEntry::errorType)
                                    .collect(java.util.stream.Collectors.joining(" → "))));
                }
                sb.append("\n");
            }
            if (entries.isEmpty()) {
                sb.append("- (none)\n\n");
            } else {
                int index = 1;
                for (BlackboardHistory.Entry entry : entries) {
                    if (entry == null) {
                        continue;
                    }
                    Object input = entry.input();
                    String inputType = input != null ? input.getClass().getSimpleName() : "null";
                    sb.append("### Entry %d\n\n".formatted(index++));
                    sb.append("- action: `%s`\n".formatted(entry.actionName()));
                    sb.append("- inputType: `%s`\n".formatted(inputType));
                    sb.append("- timestamp: `%s`\n\n".formatted(entry.timestamp()));
                    sb.append("```json\n");
                    sb.append(safeSerialize(input));
                    sb.append("\n```\n\n");
                }
            }
            sb.append("---\n\n");

            Files.writeString(blackboardHistoryLogFile, sb.toString(), StandardOpenOption.APPEND);
        } catch (Exception e) {
            log.warn("Failed to append blackboard history for call {} to {}", record.callIndex(), blackboardHistoryLogFile, e);
        }
    }

    private void writeHistoryHeader() {
        try {
            StringBuilder sb = new StringBuilder();
            sb.append("# QueuedLlmRunner Blackboard History Log\n\n");
            sb.append("| Field | Value |\n");
            sb.append("|-------|-------|\n");
            sb.append("| **Test class** | `%s` |\n".formatted(
                    testClassName != null ? testClassName : "unknown"));
            sb.append("| **Test method** | `%s` |\n".formatted(
                    testMethodName != null ? testMethodName : "unknown"));
            sb.append("| **Started at** | %s |\n".formatted(Instant.now()));
            sb.append("\n---\n\n");

            Files.writeString(blackboardHistoryLogFile, sb.toString(),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception e) {
            log.warn("Failed to write blackboard history header to {}", blackboardHistoryLogFile, e);
        }
    }

    private String safeSerialize(Object obj) {
        try {
            return LOG_MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("JSON serialization failed for {}: {}", obj.getClass().getSimpleName(), e.getMessage());
            return "{ \"_serializationError\": \"" + e.getMessage().replace("\"", "'") + "\", \"_type\": \"" + obj.getClass().getName() + "\" }";
        }
    }
}
