package com.hayden.multiagentide.ui.shared;

import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.ui.state.UiSessionState;
import com.hayden.multiagentide.ui.state.UiState;
import com.hayden.multiagentide.ui.state.UiStateReducer;
import com.hayden.multiagentide.ui.state.UiViewport;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SharedUiInteractionServiceImpl implements SharedUiInteractionService {

    private final UiStateReducer reducer = new UiStateReducer();
    private final UiActionMapper actionMapper;

    public SharedUiInteractionServiceImpl(UiActionMapper actionMapper) {
        this.actionMapper = actionMapper;
    }

    @Override
    public UiState reduce(UiState state, Events.GraphEvent event, UiViewport viewport, String nodeId) {
        return reducer.reduce(state, event, viewport, nodeId);
    }

    @Override
    public UiStateSnapshot toSnapshot(UiState state) {
        Map<String, UiSessionSnapshot> sessions = new LinkedHashMap<>();
        for (Map.Entry<String, UiSessionState> entry : state.sessions().entrySet()) {
            UiSessionState sessionState = entry.getValue();
            sessions.put(entry.getKey(), new UiSessionSnapshot(
                    sessionState.selectedIndex(),
                    sessionState.scrollOffset(),
                    sessionState.autoFollow(),
                    sessionState.detailOpen(),
                    sessionState.detailEventId(),
                    sessionState.chatInput(),
                    new UiSessionSnapshot.UiChatSearchSnapshot(
                            sessionState.chatSearch().active(),
                            sessionState.chatSearch().query(),
                            sessionState.chatSearch().resultIndices(),
                            sessionState.chatSearch().selectedResultIndex()
                    ),
                    sessionState.repo() == null ? null : sessionState.repo().toString(),
                    sessionState.events().size()
            ));
        }

        return new UiStateSnapshot(
                state.activeSessionId(),
                state.activeSessionId(),
                state.sessionOrder(),
                Map.copyOf(sessions),
                state.focus().name(),
                state.chatScrollOffset(),
                state.repo().toString(),
                "rev-" + Instant.now().toEpochMilli(),
                Instant.now()
        );
    }

    @Override
    public Events.UiInteractionEvent toInteractionEvent(UiActionCommand command) {
        return actionMapper.map(command);
    }

    @Override
    public String resolveNodeId(Events.GraphEvent event, String fallbackNodeId) {
        if (event instanceof Events.TuiInteractionGraphEvent interaction) {
            if (interaction.nodeId() != null && !interaction.nodeId().isBlank()) {
                return interaction.nodeId();
            }
            if (interaction.sessionId() != null && !interaction.sessionId().isBlank()) {
                return interaction.sessionId();
            }
        }
        String nodeId = event == null ? null : event.nodeId();
        if (nodeId == null || nodeId.isBlank()) {
            return fallbackNodeId;
        }
        return nodeId;
    }
}
