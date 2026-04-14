package com.hayden.multiagentide.tool;

import com.hayden.utilitymodule.stream.StreamUtil;
import lombok.Builder;
import lombok.With;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tool container for LLM runs.
 */
@Builder(toBuilder = true)
@With
public record ToolContext(List<ToolAbstraction> tools) {

    public ToolContext {
        Objects.requireNonNull(tools, "tools");
        tools = List.copyOf(tools);
    }

    public ToolContext withTool(ToolAbstraction tool) {
        var t = new ArrayList<>(tools);
        t.add(tool);
        return new ToolContext(t);
    }

    public static ToolContext empty() {
        return new ToolContext(List.of());
    }

    public static ToolContext of(ToolAbstraction... tools) {
        return new ToolContext(StreamUtil.toStream(tools).toList());
    }
}
