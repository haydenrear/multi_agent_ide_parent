package com.hayden.multiagentide.ui.shared;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record UiStateSnapshot(
        String nodeId,
        String activeNodeId,
        List<String> nodeOrder,
        Map<String, UiSessionSnapshot> nodes,
        String focus,
        int chatScrollOffset,
        String repo,
        String revision,
        Instant capturedAt
) {
}
