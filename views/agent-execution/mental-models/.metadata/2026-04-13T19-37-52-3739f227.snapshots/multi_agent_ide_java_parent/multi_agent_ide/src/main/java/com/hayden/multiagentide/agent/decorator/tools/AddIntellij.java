package com.hayden.multiagentide.agent.decorator.tools;

import com.embabel.agent.api.tool.ToolObject;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.multiagentide.tool.McpToolObjectRegistrar;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.tool.ToolAbstraction;
import com.hayden.multiagentide.tool.ToolContext;
import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.mcp.SyncMcpToolCallback;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Slf4j
@Component
@Profile("intellij")
@RequiredArgsConstructor
public class AddIntellij implements ToolContextDecorator {

    private static final String INTELLIJ_TOOL_NAME = "intellij";
    private static final String INTELLIJ_CLI = "idea";
    private static final long IDEA_EXIT_CHECK_MILLIS = 300L;

    private final McpToolObjectRegistrar toolObjectRegistry;
    private final Set<Path> openedWorktreePaths = ConcurrentHashMap.newKeySet();

    @Autowired(required = false)
    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public int order() {
        return -10_000;
    }

    @Override
    public ToolContext decorate(ToolContext t, DecoratorContext decoratorContext) {
        resolveMainWorktreePath(decoratorContext)
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .ifPresent(this::openInIntellijIfNeeded);

        var tools = withIntellij(t, decoratorContext);
        return new ToolContext(tools);
    }

    Set<String> enabledTools = Set.of(
        "build_project", "get_file_problems", "get_project_dependencies",
        "get_project_modules", "get_symbol_info", "rename_refactoring", "get_repositories");

    private @NonNull List<ToolAbstraction> withIntellij(ToolContext toolContext, DecoratorContext decoratorContext) {
        var t = new ArrayList<>(toolContext.tools());

        toolObjectRegistry.tool(INTELLIJ_TOOL_NAME)
                .map(to -> {
                    return to.stream()
                            .map(toolObj -> new ToolObject(toolObj.getObjects().stream()
                                    .filter(obj -> obj instanceof SyncMcpToolCallback s && enabledTools.contains(s.getToolDefinition().name()))))
                            .toList();
                })
                .flatMap(to -> {

                    var objectStream = to.stream().flatMap(b -> b.getObjects().stream()).toList();
                    log.info("Intellij found {} matched tools.", objectStream.size());

                    var obj = to.stream().filter(Objects::nonNull)
                            .map(ToolAbstraction.EmbabelToolObject::new).toList();

                    Path worktreePath = resolveMainWorktreePath(decoratorContext).orElse(null);

                    for (ToolObject o : to) {
                        var f = o.getObjects().stream()
                                .flatMap(thisToolObj -> thisToolObj instanceof SyncMcpToolCallback s
                                        ? Stream.of(s) : Stream.empty())
                                .filter(sm -> Objects.equals("build_project", sm.getToolDefinition().name()))
                                .findFirst();

                        if (f.isPresent() && worktreePath != null) {
                            try {
//                              build the project quick - waits until it's fully booted.
                                var called = RetryTemplate.builder()
                                        .maxAttempts(5)
                                        .fixedBackoff(Duration.ofSeconds(3_000))
                                        .build()
                                        .execute(retryCtx -> {
                                            try {
                                                return f.get().call(objectMapper.writeValueAsString(Map.of(
                                                        "projectPath",
                                                        worktreePath.toAbsolutePath().toString())));
                                            } catch (
                                                    JsonProcessingException e) {
                                                throw new RuntimeException(e);
                                            }

                                        });
                                log.info("Performed call on project.");
                                break;
                            } catch (Exception e) {
                                log.error("Error when attempting to boot Intellij tool - skipping all Intellij tools for this workflow.", e);
                                return Optional.empty();
                            }
                        }
                    }

                    return Optional.of(obj)
                            .filter(Predicate.not(CollectionUtils::isEmpty));
                })
                .ifPresentOrElse(
                        t::addAll,
                        () -> log.warn(
                                "Could not find '{}' tool registration. Ensure the IntelliJ MCP server is connected and the worktree project was opened (for example, run `idea .` from the worktree root).",
                                INTELLIJ_TOOL_NAME));

        return t;
    }

    public void openInIntellijIfNeeded(Path worktreePath) {
        if (!Files.isDirectory(worktreePath)) {
            log.warn("Skipping IntelliJ project open; worktree path is not a directory: {}", worktreePath);
            return;
        }

        if (!openedWorktreePaths.add(worktreePath)) {
            return;
        }

        try {
            Process process = new ProcessBuilder(INTELLIJ_CLI, ".")
                    .directory(worktreePath.toFile())
                    .start();

            var b = new String(process.getErrorStream().readAllBytes());

            if (StringUtils.isNotBlank(b))
                log.error("Received err opening intellij: {}", b);

            if (process.waitFor(IDEA_EXIT_CHECK_MILLIS, TimeUnit.MILLISECONDS) && process.exitValue() != 0) {
                openedWorktreePaths.remove(worktreePath);
                log.warn("IntelliJ open command exited with code {} for worktree '{}'.", process.exitValue(), worktreePath);
            }
        } catch (IOException e) {
            openedWorktreePaths.remove(worktreePath);
            log.warn("Failed to run `{} .` from worktree '{}'. Ensure IntelliJ launcher is on PATH.", INTELLIJ_CLI, worktreePath, e);
        } catch (InterruptedException e) {
            openedWorktreePaths.remove(worktreePath);
            Thread.currentThread().interrupt();
            log.warn("Interrupted while trying to open IntelliJ for worktree '{}'.", worktreePath, e);
        }
    }

    private Optional<Path> resolveMainWorktreePath(DecoratorContext decoratorContext) {
        return resolveFromAgentModel(decoratorContext.agentRequest())
                .or(() -> resolveFromAgentModel(decoratorContext.lastRequest()));
    }

    private Optional<Path> resolveFromAgentModel(com.hayden.acp_cdc_ai.acp.events.Artifact.AgentModel model) {
        if (model instanceof AgentModels.AgentRequest ar) {
            return Optional.ofNullable(ar.worktreeContext())
                    .map(ws -> ws.mainWorktree())
                    .map(main -> main.worktreePath());
        }
        return Optional.empty();
    }
}
