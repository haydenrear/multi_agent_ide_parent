package com.hayden.multiagentide.agent.decorator.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.tool.ToolContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * When the intellij profile is NOT active, writes a .claude/settings.local.json
 * into each worktree root that denies all JetBrains MCP tools. This prevents
 * spawned ACP/Claude Code agent sessions from inheriting the host's JetBrains
 * MCP server connection.
 */
@Slf4j
@Component
@Profile("!intellij")
@RequiredArgsConstructor
public class RemoveIntellij implements ToolContextDecorator {

    private static final List<String> DENY_RULE = List.of(
            "mcp__jetbrains__execute_run_configuration",
            "mcp__jetbrains__get_run_configurations",
            "mcp__jetbrains__build_project",
            "mcp__jetbrains__get_file_problems",
            "mcp__jetbrains__get_project_dependencies",
            "mcp__jetbrains__get_project_modules",
            "mcp__jetbrains__create_new_file",
            "mcp__jetbrains__find_files_by_glob",
            "mcp__jetbrains__find_files_by_name_keyword",
            "mcp__jetbrains__get_all_open_file_paths",
            "mcp__jetbrains__list_directory_tree",
            "mcp__jetbrains__open_file_in_editor",
            "mcp__jetbrains__reformat_file",
            "mcp__jetbrains__get_file_text_by_path",
            "mcp__jetbrains__replace_text_in_file",
            "mcp__jetbrains__search_in_files_by_regex",
            "mcp__jetbrains__search_in_files_by_text",
            "mcp__jetbrains__get_symbol_info",
            "mcp__jetbrains__rename_refactoring",
            "mcp__jetbrains__execute_terminal_command",
            "mcp__jetbrains__get_repositories",
            "mcp__jetbrains__runNotebookCell",
            "mcp__jetbrains__permission_prompt"
    );

    private final Set<Path> writtenPaths = ConcurrentHashMap.newKeySet();

    @Autowired(required = false)
    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public int order() {
        return -10_001; // run before AddIntellij's order (-10_000)
    }

    @Override
    public ToolContext decorate(ToolContext t, DecoratorContext decoratorContext) {
        resolveMainWorktreePath(decoratorContext)
                .map(Path::toAbsolutePath)
                .map(Path::normalize)
                .ifPresent(this::ensureDenyRuleWritten);
        return t;
    }

    public void ensureDenyRuleWritten(Path worktreePath) {
        if (!Files.isDirectory(worktreePath)) {
            return;
        }
        if (!writtenPaths.add(worktreePath)) {
            return; // already written for this worktree
        }

        Path claudeDir = worktreePath.resolve(".claude");
        Path settingsFile = claudeDir.resolve("settings.local.json");

        try {
            Files.createDirectories(claudeDir);

            ObjectNode root;
            if (Files.exists(settingsFile)) {
                root = (ObjectNode) objectMapper.readTree(settingsFile.toFile());
            } else {
                root = objectMapper.createObjectNode();
            }

            ObjectNode permissions = root.has("permissions")
                    ? (ObjectNode) root.get("permissions")
                    : root.putObject("permissions");

            ArrayNode deny = permissions.has("deny")
                    ? (ArrayNode) permissions.get("deny")
                    : permissions.putArray("deny");

            for (String d : DENY_RULE) {
                if (isAlreadyPresent(deny, d)) {
                    continue;
                }

                deny.add(d);
                log.info("Wrote JetBrains MCP deny rule to {}", settingsFile);
            }

            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(settingsFile.toFile(), root);
        } catch (IOException e) {
            writtenPaths.remove(worktreePath);
            log.warn("Failed to write .claude/settings.local.json to worktree '{}': {}", worktreePath, e.getMessage());
        }
    }

    private static boolean isAlreadyPresent(ArrayNode deny, String toTest) {
        boolean alreadyPresent = false;
        for (int i = 0; i < deny.size(); i++) {
            if (toTest.equals(deny.get(i).asText())) {
                alreadyPresent = true;
                break;
            }
        }
        return alreadyPresent;
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
