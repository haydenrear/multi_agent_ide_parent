package com.hayden.multiagentide.ui.state;

import lombok.Builder;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Builder(toBuilder = true)
public record UiState(
        String sessionId,
        String activeSessionId,
        List<String> sessionOrder,
        Map<String, UiSessionState> sessions,
        UiFocus focus,
        int chatScrollOffset,
        Path repo
) {
    public UiState {
        if (repo == null)
            throw new IllegalArgumentException("");
        if (focus == null) {
            focus = UiFocus.CHAT_INPUT;
        }
        if (sessionOrder == null) {
            sessionOrder = List.of();
        }
        if (sessions == null) {
            sessions = Map.of();
        }
    }

    public static UiState initial(String sessionId, String activeSessionId, List<String> sessionOrder, Map<String, UiSessionState> sessions,
                                   Path repo) {
        return UiState.builder()
                .sessionId(sessionId)
                .activeSessionId(activeSessionId)
                .sessionOrder(sessionOrder)
                .sessions(sessions)
                .focus(UiFocus.CHAT_INPUT)
                .chatScrollOffset(0)
                .repo(repo)
                .build();
    }
}
