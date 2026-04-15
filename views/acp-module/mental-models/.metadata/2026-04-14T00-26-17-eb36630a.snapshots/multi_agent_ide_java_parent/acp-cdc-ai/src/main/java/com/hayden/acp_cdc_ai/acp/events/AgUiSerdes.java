package com.hayden.acp_cdc_ai.acp.events;

import com.agui.core.types.BaseEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import kotlinx.serialization.json.Json;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class AgUiSerdes {

    private final Json json = Json.Default;

    public String serializeEvent(@Nullable BaseEvent baseEvent) {
        if (baseEvent == null) {
            return "{}";
        }
        try {
            return json.encodeToString(BaseEvent.Companion.serializer(), baseEvent);
        } catch (Exception e) {
            log.error("Failed to serialize ag-ui event.", e);
            return "{}";
        }
    }

}
