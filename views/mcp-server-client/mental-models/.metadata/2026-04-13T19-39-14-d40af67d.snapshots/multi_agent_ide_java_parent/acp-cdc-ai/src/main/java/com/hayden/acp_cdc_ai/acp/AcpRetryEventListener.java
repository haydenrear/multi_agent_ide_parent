package com.hayden.acp_cdc_ai.acp;

import com.hayden.acp_cdc_ai.acp.events.EventListener;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Listens for executor lifecycle events and maintains per-session retry context.
 * AcpChatModel queries this to determine if a session is in retry state.
 *
 * <p>This bean does NOT get auto-collected into {@code DefaultEventBus}'s
 * {@code List<EventListener>} constructor injection — it subscribes itself
 * lazily via {@code ContextRefreshedEvent} to break the circular dependency
 * (DefaultEventBus → collects EventListeners → this bean → needs EventBus).
 *
 * <p>Invariant violations (missing session context, duplicate creates, orphan
 * closes) are surfaced as {@link Events.NodeErrorEvent} on the event bus so
 * downstream listeners and the TUI can observe them.
 *
 * <p>Event flow:
 * <ol>
 *   <li>{@code ChatSessionCreatedEvent} → create entry</li>
 *   <li>{@code AgentExecutorStartEvent} → reset retry state for new attempt</li>
 *   <li>{@code CompactionEvent} → record compaction</li>
 *   <li>{@code AgentExecutorCompleteEvent} → clear retry state on success</li>
 *   <li>{@code ChatSessionClosedEvent} → remove entry</li>
 * </ol>
 */
@Component
@Slf4j
public class AcpRetryEventListener implements EventListener {

    private final ConcurrentHashMap<String, AcpSessionRetryContext> contexts = new ConcurrentHashMap<>();
    private final AtomicBoolean subscribed = new AtomicBoolean(false);
    private final AtomicReference<EventBus> eventBusRef = new AtomicReference<>();

    /**
     * Subscribe after the full application context is ready.
     * Retrieves EventBus from the ApplicationContext directly to avoid
     * any field/constructor injection of EventBus on this bean (which
     * would trigger a circular reference with DefaultEventBus).
     */
    @org.springframework.context.event.EventListener
    void onContextRefreshed(ContextRefreshedEvent event) {
        if (subscribed.compareAndSet(false, true)) {
            EventBus eventBus = event.getApplicationContext().getBean(EventBus.class);
            eventBusRef.set(eventBus);
            eventBus.subscribe(this);
        }
    }

    public @Nullable AcpSessionRetryContext retryContextFor(String sessionKey) {
        return contexts.get(sessionKey);
    }

    @Override
    public String listenerId() {
        return "AcpRetryEventListener";
    }

    @Override
    public void onEvent(Events.GraphEvent event) {
        switch (event) {
            case Events.ChatSessionCreatedEvent e -> {
                String key = e.chatModelId().value();
                var previous = contexts.putIfAbsent(key, new AcpSessionRetryContext(key));
                if (previous != null) {
                    String msg = "Duplicate ChatSessionCreatedEvent for session %s; "
                            + "context already exists (retryCount=%d, errorCategory=%s)"
                            .formatted(key, previous.retryCount(), previous.errorCategory());
                    log.warn("AcpRetryEventListener: {}", msg);
                    publishError(key, msg);
                } else {
                    log.debug("AcpRetryEventListener: session created {}", key);
                }
            }
            case Events.AgentExecutorStartEvent e -> {
                var ctx = contexts.get(e.sessionKey());
                if (ctx != null) {
                    ctx.clearOnSuccess();
                    log.debug("AcpRetryEventListener: executor start, cleared retry state for {}",
                            e.sessionKey());
                } else {
                    String msg = "AgentExecutorStartEvent for unknown session %s; "
                            + "no ChatSessionCreatedEvent preceded this (action=%s)"
                            .formatted(e.sessionKey(), e.actionName());
                    log.warn("AcpRetryEventListener: {}", msg);
                    publishError(e.nodeId(), msg);
                }
            }
            case Events.CompactionEvent e -> {
                var ctx = contexts.get(e.nodeId());
                if (ctx != null) {
                    ctx.recordCompaction(e.contextId() != null ? e.contextId().value() : "");
                    log.debug("AcpRetryEventListener: compaction recorded for {}", e.nodeId());
                } else {
                    String msg = "CompactionEvent for unknown session %s; "
                            + "no ChatSessionCreatedEvent preceded this"
                            .formatted(e.nodeId());
                    log.warn("AcpRetryEventListener: {}", msg);
                    publishError(e.nodeId(), msg);
                }
            }
            case Events.AgentExecutorCompleteEvent e -> {
                var ctx = contexts.get(e.sessionKey());
                if (ctx != null) {
                    ctx.clearOnSuccess();
                    log.debug("AcpRetryEventListener: executor complete, cleared retry for {}",
                            e.sessionKey());
                } else {
                    String msg = "AgentExecutorCompleteEvent for unknown session %s; "
                            + "no ChatSessionCreatedEvent preceded this (action=%s)"
                            .formatted(e.sessionKey(), e.actionName());
                    log.warn("AcpRetryEventListener: {}", msg);
                    publishError(e.nodeId(), msg);
                }
            }
            case Events.NullResultEvent e -> {
                var ctx = contexts.get(e.sessionKey());
                if (ctx != null) {
                    ctx.recordError(AcpSessionRetryContext.ErrorCategory.NULL_RESULT,
                            "retry %d/%d".formatted(e.retryCount(), e.maxRetries()));
                    log.debug("AcpRetryEventListener: null result recorded for {} (retry {}/{})",
                            e.sessionKey(), e.retryCount(), e.maxRetries());
                } else {
                    String msg = "NullResultEvent for unknown session %s; no ChatSessionCreatedEvent preceded this"
                            .formatted(e.sessionKey());
                    log.warn("AcpRetryEventListener: {}", msg);
                    publishError(e.nodeId(), msg);
                }
            }
            case Events.IncompleteJsonEvent e -> {
                var ctx = contexts.get(e.sessionKey());
                if (ctx != null) {
                    ctx.recordError(AcpSessionRetryContext.ErrorCategory.INCOMPLETE_JSON,
                            "retry %d, fragment length=%d".formatted(e.retryCount(),
                                    e.rawFragment() != null ? e.rawFragment().length() : 0));
                    log.debug("AcpRetryEventListener: incomplete JSON recorded for {} (retry {})",
                            e.sessionKey(), e.retryCount());
                } else {
                    String msg = "IncompleteJsonEvent for unknown session %s; no ChatSessionCreatedEvent preceded this"
                            .formatted(e.sessionKey());
                    log.warn("AcpRetryEventListener: {}", msg);
                    publishError(e.nodeId(), msg);
                }
            }
            case Events.UnparsedToolCallEvent e -> {
                var ctx = contexts.get(e.sessionKey());
                if (ctx != null) {
                    ctx.recordError(AcpSessionRetryContext.ErrorCategory.UNPARSED_TOOL_CALL,
                            e.toolCallText());
                    log.debug("AcpRetryEventListener: unparsed tool call recorded for {}", e.sessionKey());
                } else {
                    String msg = "UnparsedToolCallEvent for unknown session %s; no ChatSessionCreatedEvent preceded this"
                            .formatted(e.sessionKey());
                    log.warn("AcpRetryEventListener: {}", msg);
                    publishError(e.nodeId(), msg);
                }
            }
            case Events.TimeoutEvent e -> {
                var ctx = contexts.get(e.sessionKey());
                if (ctx != null) {
                    ctx.recordError(AcpSessionRetryContext.ErrorCategory.TIMEOUT,
                            "retry %d: %s".formatted(e.retryCount(), e.detail()));
                    log.debug("AcpRetryEventListener: timeout recorded for {} (retry {})",
                            e.sessionKey(), e.retryCount());
                } else {
                    String msg = "TimeoutEvent for unknown session %s; no ChatSessionCreatedEvent preceded this"
                            .formatted(e.sessionKey());
                    log.warn("AcpRetryEventListener: {}", msg);
                    publishError(e.nodeId(), msg);
                }
            }
            case Events.ChatSessionClosedEvent e -> {
                var removed = contexts.remove(e.sessionId());
                if (removed == null) {
                    String msg = "ChatSessionClosedEvent for unknown session %s; "
                            + "no matching context found (double-close or missed create?)"
                            .formatted(e.sessionId());
                    log.warn("AcpRetryEventListener: {}", msg);
                    publishError(e.sessionId(), msg);
                } else {
                    log.debug("AcpRetryEventListener: session closed, removed {}", e.sessionId());
                }
            }
            default -> {
                // Ignore other events
            }
        }
    }

    private void publishError(String nodeId, String message) {
        EventBus bus = eventBusRef.get();
        if (bus == null) {
            log.debug("AcpRetryEventListener: eventBus not yet available, cannot publish NodeErrorEvent");
            return;
        }
        bus.publish(Events.NodeErrorEvent.builder()
                .eventId(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .nodeId(nodeId)
                .nodeTitle("Retry listener invariant violation")
                .nodeType(Events.NodeType.SUMMARY)
                .message(message)
                .build());
    }
}
