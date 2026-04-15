package com.hayden.acp_cdc_ai.acp.events;

import com.agui.core.types.BaseEvent;
import com.agui.core.types.CustomEvent;
import com.agui.core.types.Role;
import com.agui.core.types.TextMessageChunkEvent;
import com.agui.core.types.ThinkingTextMessageContentEvent;
import com.agui.core.types.ToolCallArgsEvent;
import com.agui.core.types.ToolCallChunkEvent;
import com.agui.core.types.ToolCallEndEvent;
import com.agui.core.types.ToolCallResultEvent;
import com.agui.core.types.ToolCallStartEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import kotlinx.serialization.json.Json;
import kotlinx.serialization.json.JsonElement;
import kotlinx.serialization.json.JsonNull;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Component
public class AgUiEventMappingRegistry {

    private static volatile AgUiEventMappingRegistry instance;

    private final ObjectMapper objectMapper;
    private final Json json = Json.Default;
    private final Map<String, Function<Events.GraphEvent, BaseEvent>> mappings = new ConcurrentHashMap<>();

    public AgUiEventMappingRegistry(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    public void registerDefaults() {
        instance = this;
        mappings.put("GUI_RENDER", event -> mapWithPayload(event, ((Events.GuiRenderEvent) event).payload()));
        mappings.put("UI_DIFF_APPLIED", event -> mapWithPayload(event, buildDiffPayload((Events.UiDiffAppliedEvent) event)));
        mappings.put("UI_DIFF_REVERTED", event -> mapWithPayload(event, buildRevertPayload((Events.UiDiffRevertedEvent) event)));
        mappings.put("UI_DIFF_REJECTED", event -> mapWithPayload(event, buildRejectPayload((Events.UiDiffRejectedEvent) event)));
        mappings.put("UI_FEEDBACK", event -> mapWithPayload(event, buildFeedbackPayload((Events.UiFeedbackEvent) event)));
        mappings.put("PERMISSION_REQUESTED", event -> mapWithPayload(event, event));
        mappings.put("PERMISSION_RESOLVED", event -> mapWithPayload(event, event));
    }

    public void registerMapping(String eventType, Function<Events.GraphEvent, BaseEvent> mapper) {
        mappings.put(eventType, mapper);
    }

    public static BaseEvent map(Events.GraphEvent event) {
        if (instance == null) {
            return null;
        }
        return instance.mapEvent(event);
    }

    private BaseEvent mapEvent(Events.GraphEvent event) {
        if (event instanceof Events.ToolCallEvent toolCallEvent) {
            return mapToolCallEvent(toolCallEvent);
        }
        if (event instanceof Events.NodeStreamDeltaEvent streamDeltaEvent) {
            return mapStreamDeltaEvent(streamDeltaEvent);
        }
        if (event instanceof Events.NodeThoughtDeltaEvent thoughtDeltaEvent) {
            return mapThoughtDeltaEvent(thoughtDeltaEvent);
        }
        if (event instanceof Events.UserMessageChunkEvent userMessageChunkEvent) {
            return mapUserMessageChunkEvent(userMessageChunkEvent);
        }
        Function<Events.GraphEvent, BaseEvent> mapper = mappings.get(event.eventType());
        BaseEvent mapped = mapper != null ? mapper.apply(event) : mapWithPayload(event, null);
        return mapped;
    }

    private BaseEvent mapWithPayload(Events.GraphEvent event, Object payload) {
        JsonElement rawEvent = toJsonElement(event);
        JsonElement value = payload != null ? toJsonElement(payload) : JsonNull.INSTANCE;
        Instant timestamp = event.timestamp();
        Long timestampValue = timestamp != null ? timestamp.toEpochMilli() : null;
        return new CustomEvent(event.eventType(), value, timestampValue, rawEvent);
    }

    private Map<String, Object> buildDiffPayload(Events.UiDiffAppliedEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("renderTree", event.renderTree());
        payload.put("revision", event.revision());
        payload.put("summary", event.summary());
        return payload;
    }

    private Map<String, Object> buildRevertPayload(Events.UiDiffRevertedEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("renderTree", event.renderTree());
        payload.put("revision", event.revision());
        payload.put("sourceEventId", event.sourceEventId());
        return payload;
    }

    private Map<String, Object> buildRejectPayload(Events.UiDiffRejectedEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("errorCode", event.errorCode());
        payload.put("message", event.message());
        return payload;
    }

    private Map<String, Object> buildFeedbackPayload(Events.UiFeedbackEvent event) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("message", event.message());
        payload.put("snapshot", event.snapshot());
        payload.put("sourceEventId", event.sourceEventId());
        return payload;
    }

    private BaseEvent mapStreamDeltaEvent(Events.NodeStreamDeltaEvent event) {
        JsonElement rawEvent = toJsonElement(event);
        return new TextMessageChunkEvent(
                buildMessageId(event.nodeId(), "assistant-stream"),
                Role.ASSISTANT,
                event.deltaContent(),
                toEpochMillis(event.timestamp()),
                rawEvent
        );
    }

    private BaseEvent mapThoughtDeltaEvent(Events.NodeThoughtDeltaEvent event) {
        JsonElement rawEvent = toJsonElement(event);
        return new ThinkingTextMessageContentEvent(
                event.deltaContent(),
                toEpochMillis(event.timestamp()),
                rawEvent
        );
    }

    private BaseEvent mapUserMessageChunkEvent(Events.UserMessageChunkEvent event) {
        JsonElement rawEvent = toJsonElement(event);
        return new TextMessageChunkEvent(
                buildMessageId(event.nodeId(), "user-stream"),
                Role.USER,
                event.content(),
                toEpochMillis(event.timestamp()),
                rawEvent
        );
    }

    private BaseEvent mapToolCallEvent(Events.ToolCallEvent event) {
        JsonElement rawEvent = toJsonElement(event);
        String toolCallId = event.toolCallId() != null ? event.toolCallId() : event.eventId();
        String title = event.title();
        String normalized = event.phase() != null ? event.phase().toUpperCase(Locale.ROOT) : "";
        Long timestampValue = toEpochMillis(event.timestamp());
        switch (normalized) {
            case "START":
                return new ToolCallStartEvent(toolCallId, title, null, timestampValue, rawEvent);
            case "ARGS":
                return new ToolCallArgsEvent(toolCallId, stringifyPayload(event.rawInput()), timestampValue, rawEvent);
            case "END":
                return new ToolCallEndEvent(toolCallId, timestampValue, rawEvent);
            case "RESULT":
                return new ToolCallResultEvent(
                        event.eventId(),
                        toolCallId,
                        stringifyPayload(event.rawOutput()),
                        "tool",
                        timestampValue,
                        rawEvent
                );
            default:
                String delta = stringifyPayload(event.rawInput());
                if (delta.isEmpty()) {
                    delta = stringifyPayload(event.rawOutput());
                }
                return new ToolCallChunkEvent(
                        toolCallId,
                        title,
                        delta,
                        null,
                        timestampValue,
                        rawEvent
                );
        }
    }

    private String stringifyPayload(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "";
        }
    }

    private Long toEpochMillis(Instant timestamp) {
        return timestamp != null ? timestamp.toEpochMilli() : null;
    }

    private String buildMessageId(String nodeId, String suffix) {
        if (nodeId == null || nodeId.isBlank()) {
            return null;
        }
        return nodeId + "-" + suffix;
    }

    private JsonElement toJsonElement(Object value) {
        if (value == null) {
            return JsonNull.INSTANCE;
        }
        try {
            String jsonString = objectMapper.writeValueAsString(value);
            return json.parseToJsonElement(jsonString);
        } catch (JsonProcessingException e) {
            return JsonNull.INSTANCE;
        }
    }
}
