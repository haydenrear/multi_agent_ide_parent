package com.hayden.multiagentide.tool;

import com.embabel.agent.api.tool.ToolObject;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.tool.ToolCallback;

import java.util.List;
import java.util.Optional;

public interface EmbabelToolObjectProvider {

    static @NonNull List<ToolObject> parseToolObjects(String name, List<ToolCallback> tc) {
        var t = tc.stream().map(tcb -> new ToolObject(tcb, to -> name + "." + to, s -> true)).toList();
        return t;
    }

    Optional<List<ToolObject>> tool(String name);

}
