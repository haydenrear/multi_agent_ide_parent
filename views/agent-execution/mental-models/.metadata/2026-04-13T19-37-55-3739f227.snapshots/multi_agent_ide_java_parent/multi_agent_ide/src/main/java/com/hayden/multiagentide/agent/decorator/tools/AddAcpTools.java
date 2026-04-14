package com.hayden.multiagentide.agent.decorator.tools;

import com.hayden.multiagentide.agent.AcpTooling;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.tool.ToolAbstraction;
import com.hayden.multiagentide.tool.ToolContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("goose")
public class AddAcpTools implements ToolContextDecorator {

    @Autowired(required = false)
    private AcpTooling tools;

    @Override
    public int order() {
        return -10_000;
    }

    @Override
    public ToolContext decorate(ToolContext t, DecoratorContext decoratorContext) {
        return t.withTool(ToolAbstraction.fromToolCarrier(tools));
    }
}
