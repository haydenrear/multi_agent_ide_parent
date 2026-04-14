package com.hayden.multiagentide.ui.shared;

import java.util.List;

public record UiSessionSnapshot(
        int selectedIndex,
        int scrollOffset,
        boolean autoFollow,
        boolean detailOpen,
        String detailEventId,
        String chatInput,
        UiChatSearchSnapshot chatSearch,
        String repo,
        int eventCount
) {

    public record UiChatSearchSnapshot(
            boolean active,
            String query,
            List<Integer> resultIndices,
            int selectedResultIndex
    ) {
    }
}
