package com.hayden.multiagentide.model.nodes;

/**
 * Marker for nodes that represent an interrupt (review or explicit interrupt node).
 */
public interface InterruptRecord extends Interrupt {

    default InterruptContext.InterruptStatus interruptStatus() {
        InterruptContext context = interruptContext();
        return context != null ? context.status() : null;
    }
}
