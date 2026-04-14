package com.hayden.multiagentide.ui.state;

import com.hayden.acp_cdc_ai.acp.events.Events;
import lombok.Builder;
import lombok.With;

import java.nio.file.Path;
import java.util.List;

@Builder(toBuilder = true)
@With
public record UiSessionState(
        List<Events.GraphEvent> events,
        int selectedIndex,
        int scrollOffset,
        boolean autoFollow,
        boolean detailOpen,
        String detailEventId,
        String chatInput,
        UiChatSearch chatSearch,
        Path repo
) {
    public UiSessionState {
        if (repo == null)
            throw new IllegalArgumentException("");
        if (events == null) {
            events = List.of();
        }
        if (chatInput == null) {
            chatInput = "";
        }
        if (chatSearch == null) {
            chatSearch = UiChatSearch.inactive();
        }
    }

    public static UiSessionState initial(Path repo) {
        return new UiSessionState(
                List.of(),
                0,
                0,
                true,
                false,
                null,
                "",
                UiChatSearch.inactive(),
                repo
        );
    }
}
