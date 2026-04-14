package com.hayden.multiagentide.model.nodes;

import com.hayden.acp_cdc_ai.acp.events.Events;

/**
 * Immutable interrupt metadata stored on interrupt-capable nodes.
 */
public record InterruptContext(
        Events.InterruptType type,
        InterruptStatus status,
        String reason,
        String originNodeId,
        String resumeNodeId,
        String interruptNodeId,
        String resultPayload
) {

    public InterruptContext {
        if (type == null) throw new IllegalArgumentException("type required");
        if (status == null) throw new IllegalArgumentException("status required");
        if (originNodeId == null || originNodeId.isBlank()) {
            throw new IllegalArgumentException("originNodeId required");
        }
        if (resumeNodeId == null || resumeNodeId.isBlank()) {
            resumeNodeId = originNodeId;
        }
    }

    public InterruptContext withStatus(InterruptStatus nextStatus) {
        return new InterruptContext(
                type,
                nextStatus,
                reason,
                originNodeId,
                resumeNodeId,
                interruptNodeId,
                resultPayload
        );
    }

    public InterruptContext withInterruptNodeId(String nodeId) {
        return new InterruptContext(
                type,
                status,
                reason,
                originNodeId,
                resumeNodeId,
                nodeId,
                resultPayload
        );
    }

    public InterruptContext withResultPayload(String payload) {
        return new InterruptContext(
                type,
                status,
                reason,
                originNodeId,
                resumeNodeId,
                interruptNodeId,
                payload
        );
    }

    public enum InterruptStatus {
        REQUESTED,
        RESULT_STORED,
        STATUS_EMITTED,
        RESOLVED
    }
}
