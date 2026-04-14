package com.hayden.multiagentide.agent.decorator.tools;

import com.hayden.multiagentide.agent.AgentTopologyTools;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.tool.ToolAbstraction;
import com.hayden.multiagentide.tool.ToolContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Injects AgentTopologyTools (list_agents, call_agent, call_controller) into every
 * workflow agent's tool set. No profile restriction — all agents should be able
 * to discover peers and communicate via the topology.
 */
@Slf4j
@Component
public class AddTopologyTools implements ToolContextDecorator {

    @Autowired
    @Lazy
    private AgentTopologyTools agentTopologyTools;

    @Override
    public int order() {
        return -9_000;
    }

    @Override
    public ToolContext decorate(ToolContext t, DecoratorContext decoratorContext) {
        return t.withTool(ToolAbstraction.fromToolCarrier(agentTopologyTools));
    }
}
