package com.hayden.multiagentide.service;

import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentControlService {

    private final EventBus eventBus;

    public String requestPause(String nodeId, String message) {
        String eventId = UUID.randomUUID().toString();
        String note = message != null ? message : "Pause requested";
        eventBus.publish(new Events.PauseEvent(eventId, Instant.now(), nodeId, note));
        return eventId;
    }

    public String requestStop(String nodeId) {
        String eventId = UUID.randomUUID().toString();
        eventBus.publish(new Events.StopAgentEvent(eventId, Instant.now(), nodeId));
        return eventId;
    }

    public String requestResume(String nodeId, String message) {
        String eventId = UUID.randomUUID().toString();
        String note = message != null ? message : "Resume requested";
        eventBus.publish(new Events.ResumeEvent(eventId, Instant.now(), nodeId, note));
        eventBus.publish(new Events.AddMessageEvent(UUID.randomUUID().toString(), Instant.now(), nodeId, note));
        return eventId;
    }

    public String requestReview(String nodeId, String message) {
        String eventId = UUID.randomUUID().toString();
        String note = message != null ? message : "Review requested";
        eventBus.publish(new Events.NodeReviewRequestedEvent(
                eventId,
                Instant.now(),
                nodeId,
                nodeId,
                Events.ReviewType.HUMAN,
                note
        ));
        return eventId;
    }

    public String requestPrune(String nodeId, String message) {
        String eventId = UUID.randomUUID().toString();
        String note = message != null ? message : "Prune requested";
        eventBus.publish(new Events.NodePrunedEvent(eventId, Instant.now(), nodeId, note, List.of()));
        return eventId;
    }

    public String requestBranch(String nodeId, String message) {
        String eventId = UUID.randomUUID().toString();
        String note = message != null ? message : "Branch requested";
        eventBus.publish(new Events.NodeBranchRequestedEvent(eventId, Instant.now(), nodeId, note));
        return eventId;
    }

    public String delete(String nodeId) {
//        TODO:
        throw new RuntimeException("TODO:");
    }
}
