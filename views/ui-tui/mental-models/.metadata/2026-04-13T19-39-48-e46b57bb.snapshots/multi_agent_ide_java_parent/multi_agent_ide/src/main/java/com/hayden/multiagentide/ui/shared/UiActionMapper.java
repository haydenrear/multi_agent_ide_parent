package com.hayden.multiagentide.ui.shared;

import com.hayden.acp_cdc_ai.acp.events.Events;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class UiActionMapper {

    public Events.UiInteractionEvent map(UiActionCommand command) {
        UiActionCommand.ActionType type = command.actionType();
        Map<String, Object> payload = command.payload();
        return switch (type) {
            case FOCUS_CHAT_INPUT -> new Events.FocusChatInput(string(payload, "previousFocus", "LLM"));
            case FOCUS_EVENT_STREAM -> new Events.FocusEventStream(string(payload, "previousFocus", "LLM"));
            case FOCUS_SESSION_LIST -> new Events.FocusSessionList(string(payload, "previousFocus", "LLM"));
            case EVENT_STREAM_MOVE_SELECTION -> new Events.EventStreamMoveSelection(
                    integer(payload, "delta", 0),
                    integer(payload, "newSelectedIndex", integer(payload, "selectedIndex", 0))
            );
            case EVENT_STREAM_SCROLL -> new Events.EventStreamScroll(
                    integer(payload, "delta", 0),
                    integer(payload, "newScrollOffset", integer(payload, "scrollOffset", 0))
            );
            case EVENT_STREAM_OPEN_DETAIL -> new Events.EventStreamOpenDetail(string(payload, "eventId", null));
            case EVENT_STREAM_CLOSE_DETAIL -> new Events.EventStreamCloseDetail(string(payload, "eventId", null));
            case CHAT_INPUT_CHANGED -> {
                String text = string(payload, "text", "");
                yield new Events.ChatInputChanged(text, integer(payload, "cursorPosition", text.length()));
            }
            case CHAT_INPUT_SUBMITTED -> new Events.ChatInputSubmitted(string(payload, "text", ""));
            case CHAT_SEARCH_OPENED -> new Events.ChatSearchOpened(string(payload, "initialQuery", ""));
            case CHAT_SEARCH_QUERY_CHANGED -> {
                String query = string(payload, "query", "");
                yield new Events.ChatSearchQueryChanged(query, integer(payload, "cursorPosition", query.length()));
            }
            case CHAT_SEARCH_RESULT_NAVIGATE -> new Events.ChatSearchResultNavigate(
                    integer(payload, "delta", 0),
                    integer(payload, "resultIndex", 0)
            );
            case CHAT_SEARCH_CLOSED -> new Events.ChatSearchClosed(string(payload, "query", ""));
            case SESSION_SELECTED -> new Events.SessionSelected(
                    string(payload, "nodeId", command.nodeId())
            );
            case SESSION_CREATED -> new Events.SessionCreated(
                    string(payload, "nodeId", command.nodeId())
            );
        };
    }

    private static int integer(Map<String, Object> payload, String key, int fallback) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private static String string(Map<String, Object> payload, String key, String fallback) {
        Object value = payload.get(key);
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? fallback : text;
    }
}
