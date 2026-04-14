package com.hayden.multiagentide.ui.shared;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record UiActionCommand(
        String actionId,
        String nodeId,
        ActionType actionType,
        Map<String, Object> payload,
        Instant requestedAt
) {
    public UiActionCommand {
        if (actionId == null || actionId.isBlank()) {
            actionId = UUID.randomUUID().toString();
        }
        if (payload == null) {
            payload = Map.of();
        }
        if (requestedAt == null) {
            requestedAt = Instant.now();
        }
        if (actionType == null) {
            throw new IllegalArgumentException("actionType is required");
        }
    }

    public enum ActionType {
        FOCUS_CHAT_INPUT,
        FOCUS_EVENT_STREAM,
        FOCUS_SESSION_LIST,
        EVENT_STREAM_MOVE_SELECTION,
        EVENT_STREAM_SCROLL,
        EVENT_STREAM_OPEN_DETAIL,
        EVENT_STREAM_CLOSE_DETAIL,
        CHAT_INPUT_CHANGED,
        CHAT_INPUT_SUBMITTED,
        CHAT_SEARCH_OPENED,
        CHAT_SEARCH_QUERY_CHANGED,
        CHAT_SEARCH_RESULT_NAVIGATE,
        CHAT_SEARCH_CLOSED,
        SESSION_SELECTED,
        SESSION_CREATED
    }
}
