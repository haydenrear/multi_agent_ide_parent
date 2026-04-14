package com.hayden.multiagentide.agent.decorator.tools;

import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.skills.SkillFinder;
import com.hayden.multiagentide.tool.ToolAbstraction;
import com.hayden.multiagentide.tool.ToolContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AddSkillToolContextDecorator implements ToolContextDecorator {

    private final SkillFinder skillFinder;

    @Override
    public int order() {
        return ToolContextDecorator.super.order();
    }

    @Override
    public ToolContext decorate(ToolContext t, DecoratorContext decoratorContext) {
        return skillFinder.findSkillDecorator(decoratorContext.agentRequest().getClass().getSimpleName())
                .map(sd -> t.withTool(new ToolAbstraction.SkillReference(sd)))
                .orElse(t);
    }
}
