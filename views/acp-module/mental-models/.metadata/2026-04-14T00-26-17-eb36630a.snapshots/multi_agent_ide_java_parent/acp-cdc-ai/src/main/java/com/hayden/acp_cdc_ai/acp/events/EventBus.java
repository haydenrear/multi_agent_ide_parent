package com.hayden.acp_cdc_ai.acp.events;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Event bus for publishing and subscribing to graph events.
 * Provides multicast event publishing to multiple subscribers.
 */
public interface EventBus {

    ThreadLocal<AgentNodeKey> Process = new InheritableThreadLocal<>();

    /**
     * Subscribe a listener to events.
     * @param listener the event listener
     */
    void subscribe(EventListener listener);

    /**
     * Unsubscribe a listener from events.
     * @param listener the event listener
     */
    void unsubscribe(EventListener listener);

    /**
     * Publish an event to all subscribers.
     * @param event the event to publish
     */
    void publish(Events.GraphEvent event);

    /**
     * Get all current subscribers.
     * @return list of subscribers
     */
    List<EventListener> getSubscribers();

    /**
     * Clear all subscribers.
     */
    void clear();

    /**
     * Check if has any subscribers.
     * @return true if has subscribers
     */
    boolean hasSubscribers();

    record AgentNodeKey(String id) {}
}
