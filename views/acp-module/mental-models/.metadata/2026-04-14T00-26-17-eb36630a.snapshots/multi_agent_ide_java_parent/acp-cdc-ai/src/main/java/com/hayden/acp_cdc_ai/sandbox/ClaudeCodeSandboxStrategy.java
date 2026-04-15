package com.hayden.acp_cdc_ai.sandbox;

import com.hayden.acp_cdc_ai.acp.config.AcpProvider;
import com.hayden.acp_cdc_ai.repository.RequestContext;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.*;

import static com.hayden.acp_cdc_ai.sandbox.SandboxArgUtils.*;

/**
 * Sandbox translation strategy for Claude Code (via claude-code-acp).
 * 
 * <p>Claude Code CLI options used:</p>
 * <ul>
 *   <li>{@code --add-dir <path>} - Add additional working directories for Claude to access</li>
 *   <li>{@code --permission-mode <mode>} - Set permission mode (default, acceptEdits, bypassPermissions, plan)</li>
 *   <li>{@code --dangerously-skip-permissions} - Bypass all permission prompts</li>
 * </ul>
 * 
 * <p>Environment variables:</p>
 * <ul>
 *   <li>{@code CLAUDE_CODE_EXECUTABLE} - Custom path to Claude Code executable (optional)</li>
 * </ul>
 * 
 * <p>Working directory is set via session {@code cwd} parameter.</p>
 * 
 * @see <a href="https://code.claude.com/docs/en/cli-reference">Claude Code CLI Reference</a>
 * @see <a href="https://github.com/zed-industries/claude-code-acp">claude-code-acp</a>
 */
@Component
public class ClaudeCodeSandboxStrategy implements SandboxTranslationStrategy {

    @Override
    public String providerKey() {
        return AcpProvider.CLAUDE.providerKey();
    }

    @Override
    public SandboxTranslation translate(RequestContext context, List<String> acpArgs, String modelName) {
        var acp = SandboxTranslationStrategy.parseFromAcpArgsClaude(acpArgs, modelName);
        var args = acp.args();
        if (context == null || context.mainWorktreePath() == null) {
            return SandboxTranslation.empty();
        }
        
        String mainPath = context.mainWorktreePath().toString();
        List<Path> submodulePaths = context.submoduleWorktreePaths();
        
        Map<String, String> env = new HashMap<>();

        // Add main worktree as an additional directory if not already specified
        if (!hasFlagValuePair(acpArgs, mainPath, "--add-dir")) {
            args.add("--add-dir");
            args.add(mainPath);
        }
        
        // Add each submodule worktree as an additional directory
        if (submodulePaths != null && !submodulePaths.isEmpty()) {
            for (Path submodulePath : submodulePaths) {
                String submodulePathStr = submodulePath.toString();
                if (!hasFlagValuePair(acpArgs, submodulePathStr, "--add-dir")) {
                    args.add("--add-dir");
                    args.add(submodulePathStr);
                }
            }
        }
        
        // Set permission mode to acceptEdits for automated workflows
        // This auto-accepts file modifications while prompting for other actions
        if (!hasFlag(acpArgs, "--permission-mode")) {
            args.add("--permission-mode");
            args.add("acceptEdits");
        }

        return new SandboxTranslation(env, args, mainPath, acp.model());
    }
}
