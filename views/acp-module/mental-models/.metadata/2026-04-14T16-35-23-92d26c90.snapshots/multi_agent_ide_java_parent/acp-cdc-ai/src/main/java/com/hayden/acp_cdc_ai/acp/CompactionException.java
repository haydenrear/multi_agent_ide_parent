package com.hayden.acp_cdc_ai.acp;

/**
 * Typed exception thrown by AcpChatModel when compaction is detected in an ACP session.
 * This allows {@code ActionRetryListenerImpl} to classify via {@code instanceof} rather
 * than parsing exception messages.
 */
public class CompactionException extends RuntimeException {

    private final String sessionKey;

    public CompactionException(String message, String sessionKey) {
        super(message);
        this.sessionKey = sessionKey;
    }

    public CompactionException(String message, String sessionKey, Throwable cause) {
        super(message, cause);
        this.sessionKey = sessionKey;
    }

    public String sessionKey() {
        return sessionKey;
    }
}
