package com.hayden.multiagentide.ui.shared;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventListener;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.ui.state.UiSessionState;
import com.hayden.multiagentide.ui.state.UiState;
import com.hayden.multiagentide.ui.state.UiViewport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class UiStateStore implements EventListener {

    private static final int DEFAULT_VIEWPORT_HEIGHT = 50;

    private final SharedUiInteractionService sharedUiInteractionService;
    private final Map<String, UiState> statesByScopeNodeId = new ConcurrentHashMap<>();
    private final Path defaultRepo = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();

    @Override
    public String listenerId() {
        return "llm-debug-ui-state-store";
    }

    @Override
    public void onEvent(Events.GraphEvent event) {
        String eventNodeId = sharedUiInteractionService.resolveNodeId(event, null);
        if (eventNodeId == null || eventNodeId.isBlank()) {
            return;
        }

        for (String scopeNodeId : scopeNodeIds(eventNodeId)) {
            statesByScopeNodeId.compute(scopeNodeId, (ignored, existing) -> {
                UiState current = existing == null ? initialState(scopeNodeId) : existing;
                String reducedNodeId = sharedUiInteractionService.resolveNodeId(event, scopeNodeId);
                return sharedUiInteractionService.reduce(
                        current,
                        event,
                        new UiViewport(DEFAULT_VIEWPORT_HEIGHT),
                        reducedNodeId
                );
            });
        }
    }

    public UiStateSnapshot snapshot(String nodeId) {
        String scopeNodeId = sanitizeNodeId(nodeId);
        UiState state = statesByScopeNodeId.computeIfAbsent(scopeNodeId, this::initialState);
        UiStateSnapshot snapshot = sharedUiInteractionService.toSnapshot(state);
        return new UiStateSnapshot(
                scopeNodeId,
                snapshot.activeNodeId(),
                snapshot.nodeOrder(),
                snapshot.nodes(),
                snapshot.focus(),
                snapshot.chatScrollOffset(),
                snapshot.repo(),
                snapshot.revision(),
                snapshot.capturedAt()
        );
    }

    private UiState initialState(String nodeId) {
        Map<String, UiSessionState> sessions = new LinkedHashMap<>();
        sessions.put(nodeId, UiSessionState.initial(defaultRepo));
        return UiState.initial(
                nodeId,
                nodeId,
                List.of(nodeId),
                sessions,
                defaultRepo
        );
    }

    private List<String> scopeNodeIds(String nodeId) {
        try {
            ArtifactKey key = new ArtifactKey(nodeId);
            List<String> scopeIds = new ArrayList<>();
            ArtifactKey current = key;
            scopeIds.add(current.value());
            while (current.parent().isPresent()) {
                current = current.parent().orElseThrow();
                scopeIds.add(current.value());
            }
            return scopeIds;
        } catch (Exception ignored) {
            return List.of(nodeId);
        }
    }

    private String sanitizeNodeId(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return "unknown";
        }
        return nodeId;
    }
}
