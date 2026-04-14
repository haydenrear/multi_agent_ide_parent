package com.hayden.multiagentide.ui.state;

import java.util.List;

public record UiChatSearch(
        boolean active,
        String query,
        List<Integer> resultIndices,
        int selectedResultIndex
) {
    public UiChatSearch {
        if (resultIndices == null) {
            resultIndices = List.of();
        }
    }

    public static UiChatSearch inactive() {
        return new UiChatSearch(false, "", List.of(), -1);
    }
}
