package com.hayden.acp_cdc_ai.sandbox;

import com.hayden.acp_cdc_ai.acp.config.AcpProvider;
import com.hayden.acp_cdc_ai.repository.RequestContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.*;

/**
 * Sandbox translation strategy for OpenAI Codex CLI (via codex-acp).
 * 
 * <p>Codex-acp uses configuration overrides with the {@code -c 'key=value'} format:</p>
 * <ul>
 *   <li>{@code -c 'cd=<path>'} - Set the working directory for the agent</li>
 *   <li>{@code -c 'sandbox=<policy>'} - Set sandbox policy (read-only, workspace-write, danger-full-access)</li>
 *   <li>{@code -c 'add-dir=<path>'} - Grant write permissions to additional directories</li>
 * </ul>
 * 
 * <p>Environment variables:</p>
 * <ul>
 *   <li>{@code OPENAI_API_KEY} - OpenAI API key for authentication</li>
 *   <li>{@code CODEX_API_KEY} - Alternative Codex API key for authentication</li>
 * </ul>
 * 
 * <p>Working directory is set via session {@code cwd} parameter and {@code -c 'cd=...'} argument.</p>
 * 
 * @see <a href="https://developers.openai.com/codex/cli/reference/">Codex CLI Reference</a>
 * @see <a href="https://github.com/zed-industries/codex-acp">codex-acp</a>
 */
@Slf4j
@Component
public class CodexSandboxStrategy implements SandboxTranslationStrategy {

    @Override
    public String providerKey() {
        return AcpProvider.CODEX.providerKey();
    }

    @Override
    public SandboxTranslation translate(RequestContext context, List<String> acpArgs, String modelName) {
        var acp = SandboxTranslationStrategy.parseFromAcpArgsCodex(acpArgs, modelName);
        var args = acp.args();

        if (context == null || context.mainWorktreePath() == null) {
            return SandboxTranslation.empty();
        }

        String mainPath = context.mainWorktreePath().toString();
        List<Path> submodulePaths = context.submoduleWorktreePaths();

        Map<String, String> env = new HashMap<>();

        // Set the working directory for the agent if not already specified
        if (!hasConfigValue(acpArgs, "cd")) {
            args.add("-c");
            args.add("cd=" + mainPath);
        }

        // Set sandbox policy to workspace-write if not already specified
        if (!hasConfigValue(acpArgs, "sandbox")) {
            args.add("-c");
            args.add("sandbox=workspace-write");
        }

        if (!hasConfigValue(acpArgs, "ask-for-approval")) {
            args.add("-c");
            args.add("ask-for-approval=never");
        }

        // Add each submodule worktree as an additional writable directory
        if (submodulePaths != null && !submodulePaths.isEmpty()) {
            for (Path submodulePath : submodulePaths) {
                String submodulePathStr = submodulePath.toString();
                if (!hasConfigValuePair(acpArgs, "add-dir", submodulePathStr)) {
                    args.add("-c");
                    args.add("add-dir=" + submodulePathStr);
                }
            }
        }

        return new SandboxTranslation(env, args, mainPath, acp.model());
    }

    /**
     * Checks if a config key is already specified in the args using -c 'key=...' format.
     */
    private boolean hasConfigValue(List<String> args, String key) {
        if (args == null || args.isEmpty()) {
            return false;
        }
        String prefix = key + "=";
        for (int i = 0; i < args.size(); i++) {
            if ("-c".equals(args.get(i)) && i + 1 < args.size()) {
                String configValue = args.get(i + 1);
                if (configValue.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Checks if a specific config key=value pair is already specified in the args.
     */
    private boolean hasConfigValuePair(List<String> args, String key, String value) {
        if (args == null || args.isEmpty()) {
            return false;
        }
        String expectedConfig = key + "=" + value;
        for (int i = 0; i < args.size(); i++) {
            if ("-c".equals(args.get(i)) && i + 1 < args.size()) {
                String configValue = args.get(i + 1);
                if (expectedConfig.equals(configValue)) {
                    return true;
                }
            }
        }
        return false;
    }
}
