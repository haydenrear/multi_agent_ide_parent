package com.hayden.multiagentide.agent.decorator.tools;

import com.hayden.multiagentide.tool.McpToolObjectRegistrar;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.tool.ToolAbstraction;
import com.hayden.multiagentide.tool.ToolContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

@Slf4j
// @Component
@RequiredArgsConstructor
public class AddMemoryToolCallDecorator implements ToolContextDecorator {

    private final McpToolObjectRegistrar toolObjectRegistry;

    @Override
    public int order() {
        return -10_000;
    }

    @Override
    public ToolContext decorate(ToolContext t, DecoratorContext decoratorContext) {
        var tools = new ArrayList<>(t.tools());

        toolObjectRegistry.tool("hindsight")
                .flatMap(to -> {
                    var obj = to.stream().filter(Objects::nonNull)
                            .map(ToolAbstraction.EmbabelToolObject::new).toList();

                    return Optional.of(obj)
                            .filter(Predicate.not(CollectionUtils::isEmpty));
                })
                .ifPresentOrElse(
                        tools::addAll,
                        () -> log.error("Could not find hindsight tool."));

        return new ToolContext(tools);
    }
}
