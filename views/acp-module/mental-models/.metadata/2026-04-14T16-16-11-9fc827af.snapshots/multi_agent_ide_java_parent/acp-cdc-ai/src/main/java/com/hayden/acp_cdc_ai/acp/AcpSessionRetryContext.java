package com.hayden.acp_cdc_ai.acp;

/**
 * Per-session retry state holder. Tracks whether the current executor attempt
 * is a retry and what error triggered it.
 *
 * <p>Uses local enums rather than ErrorDescriptor from multi_agent_ide_lib
 * to avoid a cross-module dependency.
 */
public class AcpSessionRetryContext {

    public enum ErrorCategory {
        NONE, COMPACTION, PARSE, TIMEOUT, UNPARSED_TOOL_CALL, NULL_RESULT, INCOMPLETE_JSON
    }

    public enum CompactionStatus {
        NONE, SINGLE, MULTIPLE;

        public CompactionStatus next() {
            return switch (this) {
                case NONE -> SINGLE;
                case SINGLE, MULTIPLE -> MULTIPLE;
            };
        }
    }

    private final String sessionKey;
    private volatile ErrorCategory currentError = ErrorCategory.NONE;
    private volatile CompactionStatus compactionStatus = CompactionStatus.NONE;
    private volatile String errorDetail;
    private volatile int retryCount = 0;

    public AcpSessionRetryContext(String sessionKey) {
        this.sessionKey = sessionKey;
    }

    public synchronized void recordCompaction(String contextId) {
        this.currentError = ErrorCategory.COMPACTION;
        this.compactionStatus = this.compactionStatus.next();
        this.errorDetail = contextId;
        this.retryCount++;
    }

    public synchronized void recordError(ErrorCategory category, String detail) {
        this.currentError = category;
        this.errorDetail = detail;
        this.retryCount++;
    }

    public synchronized void clearOnSuccess() {
        this.currentError = ErrorCategory.NONE;
        this.compactionStatus = CompactionStatus.NONE;
        this.errorDetail = null;
        this.retryCount = 0;
    }

    public boolean isRetry() {
        return retryCount > 0 && currentError != ErrorCategory.NONE;
    }

    public ErrorCategory errorCategory() {
        return currentError;
    }

    public CompactionStatus compactionStatus() {
        return compactionStatus;
    }

    public String errorDetail() {
        return errorDetail;
    }

    public int retryCount() {
        return retryCount;
    }

    public String sessionKey() {
        return sessionKey;
    }
}
