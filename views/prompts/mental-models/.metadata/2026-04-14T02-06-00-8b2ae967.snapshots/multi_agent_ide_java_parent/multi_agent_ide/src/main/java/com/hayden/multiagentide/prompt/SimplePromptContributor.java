package com.hayden.multiagentide.prompt;

import com.hayden.multiagentide.agent.AgentType;

import java.util.Map;
import java.util.Set;

/**
 * A simple prompt contributor with static content.
 * 
 * For truly static contributions (where content never changes), use this class directly.
 * For dynamic contributions with a static template and runtime args, use 
 * {@link TemplatedPromptContributor} instead.
 */
public final class SimplePromptContributor implements PromptContributor {

    private final String name;
    private final String contribution;
    private final Set<AgentType> applicableAgents;
    private final int priority;

    public SimplePromptContributor(
            String name,
            String contribution,
            Set<AgentType> applicableAgents,
            int priority
    ) {
        this.name = name;
        this.contribution = contribution;
        this.applicableAgents = applicableAgents != null ? applicableAgents : Set.of(AgentType.ALL);
        this.priority = priority;
    }

    @Override
    public String name() {
        return name;
    }

    public boolean include(PromptContext promptContext) {
        return applicableAgents.contains(promptContext.agentType());
    }

    @Override
    public String contribute(PromptContext context) {
        return contribution;
    }

    /**
     * For SimplePromptContributor, the template IS the contribution since it's static.
     */
    @Override
    public String template() {
        return contribution;
    }

    @Override
    public Map<String, Object> args() {
        return Map.of();
    }

    @Override
    public int priority() {
        return priority;
    }
}
