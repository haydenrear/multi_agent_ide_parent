package com.hayden.multiagentide.prompt.contributor;

import com.hayden.multiagentide.tool.McpToolObjectRegistrar;
import com.hayden.multiagentide.agent.BlackboardHistory;
import com.hayden.multiagentide.model.worktree.MainWorktreeContext;
import com.hayden.multiagentide.model.worktree.WorktreeSandboxContext;
import com.hayden.multiagentide.prompt.PromptContext;
import com.hayden.multiagentide.prompt.PromptContributor;
import com.hayden.multiagentide.prompt.PromptContributorDescriptor;
import com.hayden.multiagentide.prompt.PromptContributorFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
@Profile("intellij")
@RequiredArgsConstructor
public class IntellijPromptContributorFactory implements PromptContributorFactory {


    @Override
    public Set<PromptContributorDescriptor> descriptors() {
        return Set.of(PromptContributorDescriptor.of(
                IntellijPromptContributor.class.getSimpleName(),
                IntellijPromptContributor.class,
                "FACTORY",
                10_000,
                IntellijPromptContributor.class.getSimpleName()));
    }

    private final McpToolObjectRegistrar registrar;

    public boolean include() {
        return this.registrar.tool("intellij").isPresent();
    }

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (!include())
            return new ArrayList<>();

        if (context == null) {
            return List.of();
        }

        if (BlackboardHistory.isNonWorkflowRequest(context.currentRequest())) {
            return List.of();
        }

        return resolveMainWorktree(context)
                .map(IntellijPromptContributor::new)
                .<List<PromptContributor>>map(List::of)
                .orElseGet(List::of);
    }


    private Optional<MainWorktreeContext> resolveMainWorktree(PromptContext context) {
        return Optional.ofNullable(context.currentRequest())
                .map(ar -> ar.worktreeContext())
                .map(WorktreeSandboxContext::mainWorktree)
                .or(() -> Optional.ofNullable(context.previousRequest())
                        .map(ar -> ar.worktreeContext())
                        .map(WorktreeSandboxContext::mainWorktree));
    }

    public record IntellijPromptContributor(MainWorktreeContext main) implements PromptContributor {

        @Override
        public String name() {
            return this.getClass().getSimpleName();
        }

        @Override
        public boolean include(PromptContext promptContext) {
            return true;
        }

        @Override
        public String contribute(PromptContext context) {
            String mainPath = main.worktreePath() != null ? main.worktreePath().toString() : "(unknown)";
            String repoUrl = main.repositoryUrl() != null ? main.repositoryUrl() : "(unknown)";
            return template()
                    .replace("{{intellij_project_path}}", mainPath)
                    .replace("{{source_repository_path}}", repoUrl);
        }

        @Override
        public String template() {
            return """
                    For IntelliJ MCP tool calls, always target the worktree project opened in IntelliJ.

                    We run `idea .` from the worktree root, which adds that worktree as a project IntelliJ MCP can query.

                    For every IntelliJ MCP request:
                    - Set `projectPath` to the worktree path below.
                    - Do not set `projectPath` to the original repository path.

                    Worktree path (use this as `projectPath`): {{intellij_project_path}}
                    Original repository path (reference only): {{source_repository_path}}
                    """;
        }

        @Override
        public int priority() {
            return 10_000;
        }
    }

}
