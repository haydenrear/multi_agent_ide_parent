package com.hayden.multiagentide.agent;

import com.hayden.acp_cdc_ai.acp.AcpSessionManager;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.EventListener;
import com.hayden.acp_cdc_ai.acp.events.Events;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Listens for agent completion events and closes ACP sessions accordingly.
 *
 * Two cleanup paths:
 * - ActionCompletedEvent with DiscoveryAgentResult/PlanningAgentResult/TicketAgentResult:
 *   closes that dispatched agent's session immediately.
 * - GoalCompletedEvent with OrchestratorCollectorResult at root:
 *   closes all descendant sessions in the workflow.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AcpSessionCleanupService implements EventListener {

    private final AcpSessionManager sessionManager;

    private EventBus eventBus;

    @Autowired
    @Lazy
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public String listenerId() {
        return "acp-session-cleanup";
    }

    @Override
    public boolean isInterestedIn(Events.GraphEvent eventType) {
        return eventType instanceof Events.ActionCompletedEvent
            || eventType instanceof Events.GoalCompletedEvent;
    }

    @Override
    public void onEvent(Events.GraphEvent event) {
        switch (event) {
            case Events.ActionCompletedEvent e -> handleActionCompleted(e);
            case Events.GoalCompletedEvent e -> handleGoalCompleted(e);
            default -> {}
        }
    }

    private void handleActionCompleted(Events.ActionCompletedEvent event) {
        var model = event.agentModel();
        if (model == null) {
            return;
        }

        switch (model) {
            case AgentModels.DiscoveryAgentResult r -> closeSessionForModel(r);
            case AgentModels.PlanningAgentResult r -> closeSessionForModel(r);
            case AgentModels.TicketAgentResult r -> closeSessionForModel(r);
            default -> {}
        }
    }

    private void handleGoalCompleted(Events.GoalCompletedEvent event) {
        var model = event.model();
        if (model instanceof AgentModels.OrchestratorCollectorResult result) {
            var rootKey = result.key();
            if (rootKey != null) {
                closeDescendantSessions(rootKey);
            }
        }
    }

    private void closeSessionForModel(AgentModels.AgentResult result) {
        var contextId = result.key();
        if (contextId != null) {
            closeSession(contextId);
        }
    }

    private void closeSession(ArtifactKey contextId) {
        var session = sessionManager.getSessionContexts().remove(contextId.value());
        if (session != null) {
            log.info("Closing session for contextId {}", contextId.value());
            try {
                session.getClient().getProtocol().close();
            } catch (Exception e) {
                log.warn("Error closing session for contextId {}: {}", contextId.value(), e.getMessage());
            }
            eventBus.publish(new Events.ChatSessionClosedEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    contextId.value()
            ));
        }
    }

    private void closeDescendantSessions(ArtifactKey rootKey) {
        log.info("Closing all sessions descended from {}", rootKey.value());
        for (var entry : sessionManager.getSessionContexts().entrySet()) {
            try {
                var artifactKey = new ArtifactKey(entry.getKey().toString());
                if (artifactKey.isDescendantOf(rootKey) || artifactKey.equals(rootKey)) {
                    closeSession(artifactKey);
                }
            } catch (IllegalArgumentException e) {
                // Key is not a valid ArtifactKey, skip
            }
        }
    }
}
