package com.hayden.multiagentide.prompt.contributor;

import com.hayden.multiagentide.agent.AgentType;
import com.hayden.multiagentide.agent.BlackboardHistory;
import com.hayden.multiagentide.prompt.PromptContext;
import com.hayden.multiagentide.prompt.PromptContributor;
import com.hayden.multiagentide.prompt.PromptContributorDescriptor;
import com.hayden.multiagentide.prompt.PromptContributorFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Contributes git branch instructions to ticket agent prompts.
 * Tells the agent to commit changes to the current branch and never switch branches.
 */
@Component
public class GitPromptContributorFactory implements PromptContributorFactory {

    @Override
    public Set<PromptContributorDescriptor> descriptors() {
        return Set.of(PromptContributorDescriptor.of(
                "git-branch-instructions",
                GitPromptContributor.class,
                "FACTORY",
                85,
                "git-branch-instructions"));
    }

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null) {
            return List.of();
        }

        if (BlackboardHistory.isNonWorkflowRequest(context.currentRequest())) {
            return List.of();
        }

        var agentType = context.agentType();
        if (agentType != AgentType.TICKET_AGENT) {
            return List.of();
        }

        return List.of(new GitPromptContributor());
    }

    record GitPromptContributor() implements PromptContributor {

        @Override
        public String name() {
            return "git-branch-instructions";
        }

        @Override
        public boolean include(PromptContext promptContext) {
            return true;
        }

        @Override
        public String contribute(PromptContext context) {
            return template();
        }

        @Override
        public String template() {
            return """
                    ## Git Branch Instructions

                    You are working on a worktree with its own branch. Follow these rules strictly:

                    - **Add and commit all your changes to the current branch.** Do not create new branches or switch branches.
                    - Use `git add` and `git commit` to save your work on the branch you are already on.
                    - Do NOT run `git checkout`, `git switch`, or `git branch` to change branches.
                    - If you need to check which branch you are on, use `git branch --show-current`.
                    - Commit frequently as you make progress — do not leave uncommitted changes.
                    """;
        }

        @Override
        public int priority() {
            return 85;
        }
    }
}
