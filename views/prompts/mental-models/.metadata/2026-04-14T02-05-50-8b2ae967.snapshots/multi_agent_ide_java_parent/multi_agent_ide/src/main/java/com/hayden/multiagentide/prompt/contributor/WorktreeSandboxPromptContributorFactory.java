package com.hayden.multiagentide.prompt.contributor;

import com.google.common.collect.Lists;
import com.hayden.multiagentide.agent.AgentType;
import com.hayden.multiagentide.agent.BlackboardHistory;
import com.hayden.multiagentide.model.worktree.MainWorktreeContext;
import com.hayden.multiagentide.model.worktree.SubmoduleWorktreeContext;
import com.hayden.multiagentide.model.worktree.WorktreeSandboxContext;
import com.hayden.multiagentide.prompt.PromptContext;
import com.hayden.multiagentide.prompt.PromptContributor;
import com.hayden.multiagentide.prompt.PromptContributorDescriptor;
import com.hayden.multiagentide.prompt.PromptContributorFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Contributes worktree sandbox information to the prompt whenever a
 * WorktreeSandboxContext is available on the current or previous request.
 * This ensures all agents are aware of the sandbox boundaries and
 * worktree paths they should operate within.
 */
@Component
public class WorktreeSandboxPromptContributorFactory implements PromptContributorFactory {

    @Override
    public Set<PromptContributorDescriptor> descriptors() {
        return Set.of(PromptContributorDescriptor.of(
                "worktree-sandbox-context",
                WorktreeSandboxPromptContributor.class,
                "FACTORY",
                80,
                "worktree-sandbox-context"));
    }

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null) {
            return List.of();
        }

        if (BlackboardHistory.isNonWorkflowRequest(context.currentRequest())) {
            return List.of();
        }

        // Internal automation LLM calls (AI_PROPAGATOR, AI_FILTER, AI_TRANSFORMER) analyse
        // or transform content — injecting worktree sandbox instructions into their prompts
        // would cause confusion since they are not file-operation agents.
        var agentType = context.agentType();
        if (agentType == AgentType.AI_PROPAGATOR
                || agentType == AgentType.AI_FILTER
                || agentType == AgentType.AI_TRANSFORMER) {
            return List.of();
        }

        WorktreeSandboxContext worktreeContext = resolveWorktreeContext(context);
        if (worktreeContext == null || worktreeContext.mainWorktree() == null) {
            return List.of();
        }

        return Lists.newArrayList(new WorktreeSandboxPromptContributor(worktreeContext));
    }

    private WorktreeSandboxContext resolveWorktreeContext(PromptContext context) {
        if (context.currentRequest() != null && context.currentRequest().worktreeContext() != null) {
            return context.currentRequest().worktreeContext();
        }
        if (context.previousRequest() != null && context.previousRequest().worktreeContext() != null) {
            return context.previousRequest().worktreeContext();
        }
        return null;
    }

    public record WorktreeSandboxPromptContributor(
            WorktreeSandboxContext worktreeContext
    ) implements PromptContributor {

        @Override
        public String name() {
            return "worktree-sandbox-context";
        }

        @Override
        public boolean include(PromptContext promptContext) {
            return true;
        }

        @Override
        public String contribute(PromptContext context) {
            MainWorktreeContext main = worktreeContext.mainWorktree();

            String mainPath = main.worktreePath() != null ? main.worktreePath().toString() : "(unknown)";
            String baseBranch = main.baseBranch() != null ? main.baseBranch() : "(unknown)";
            String derivedBranch = main.derivedBranch() != null ? main.derivedBranch() : "(unknown)";
            String repoUrl = main.repositoryUrl() != null ? main.repositoryUrl() : "(unknown)";

            String submoduleInfo = buildSubmoduleInfo(worktreeContext.submoduleWorktrees());

            return template()
                    .replace("{{repository_path}}", repoUrl)
                    .replace("{{main_worktree_path}}", mainPath)
                    .replace("{{derived_branch}}", derivedBranch)
                    .replace("{{submodule_info}}", submoduleInfo);
        }

        private String buildSubmoduleInfo(List<SubmoduleWorktreeContext> submodules) {
            if (submodules == null || submodules.isEmpty()) {
                return "No submodule worktrees.";
            }
            return submodules.stream()
                    .map(sub -> "  - **%s**: %s (branch: %s)".formatted(
                            sub.submoduleName() != null ? sub.submoduleName() : "(unknown)",
                            sub.worktreePath() != null ? sub.worktreePath().toString() : "(unknown)",
                            sub.baseBranch() != null ? sub.baseBranch() : "(unknown)"
                    ))
                    .collect(Collectors.joining("\n"));
        }

        @Override
        public String template() {
            return """
                    ## Worktree Sandbox Context
                    
                    All file operations must be performed within the sandbox worktree boundaries.
                    Do not read or write files outside of these paths.
                    
                    ### Main Worktree
                    - **Path**: {{main_worktree_path}}
                    - **Branch**: {{derived_branch}}
                    
                    ### Submodule Worktrees
                    {{submodule_info}}
                    
                    
                    ## Notes on Tool Calls
                    You should be making those tool calls on the worktree path, instead of the
                    repository path, {{repository_path}}, especially the writes, because we will then merge those changes into
                    the main repository.
                    
                    This is especially because
                    """;
        }

        @Override
        public int priority() {
            return 80;
        }
    }
}
