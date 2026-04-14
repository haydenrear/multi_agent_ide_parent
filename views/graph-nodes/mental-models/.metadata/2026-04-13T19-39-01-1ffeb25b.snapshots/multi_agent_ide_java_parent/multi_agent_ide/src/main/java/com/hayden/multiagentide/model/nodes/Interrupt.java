package com.hayden.multiagentide.model.nodes;

import com.hayden.acp_cdc_ai.acp.events.Events;

/**
 * Marker mixin for nodes that represent an interrupt.
 */
public interface Interrupt {

    InterruptContext interruptContext();

    default Events.InterruptType interruptType() {
        InterruptContext context = interruptContext();
        return context != null ? context.type() : null;
    }

    default String interruptReason() {
        InterruptContext context = interruptContext();
        return context != null ? context.reason() : null;
    }

    default String interruptOriginNodeId() {
        InterruptContext context = interruptContext();
        return context != null ? context.originNodeId() : null;
    }

    default String interruptResumeNodeId() {
        InterruptContext context = interruptContext();
        return context != null ? context.resumeNodeId() : null;
    }

    default String interruptNodeId() {
        InterruptContext context = interruptContext();
        return context != null ? context.interruptNodeId() : null;
    }
}
