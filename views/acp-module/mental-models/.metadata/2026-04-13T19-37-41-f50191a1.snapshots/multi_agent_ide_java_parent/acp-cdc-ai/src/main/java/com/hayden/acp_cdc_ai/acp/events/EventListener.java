package com.hayden.acp_cdc_ai.acp.events;


/**
 * Listener for graph events.
 * Implementations handle specific event types.
 */
public interface EventListener {

    /**
     * Get a unique ID for this listener.
     * @return listener ID
     */
    String listenerId();

    /**
     * Handle an event.
     * @param event the event to handle
     */
    void onEvent(Events.GraphEvent event);

    /**
     * Check if this listener is interested in an event type.
     * @param eventType the event type
     * @return true if interested
     */
    default boolean isInterestedIn(String eventType) {
        return true;
    }

    default boolean isInterestedIn(Events.GraphEvent eventType) {
        return isInterestedIn(eventType.eventType());
    }

    /**
     * Listener execution order. Lower values run first.
     */
    default int order() {
        return 0;
    }

    /**
     * Called when listener is subscribed.
     */
    default void onSubscribed() {
    }

    /**
     * Called when listener is unsubscribed.
     */
    default void onUnsubscribed() {
    }
}
