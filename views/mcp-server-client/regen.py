#!/usr/bin/env python3
"""Regenerate the mcp-server-client view.

ACP session lifecycle, MCP server/client tool registration and routing,
sandbox translation strategies, permission gating, and streaming/event
emission.

Symlinks are grouped by mental-model section so the directory layout mirrors
the mental model headings.
"""
import os
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
VIEW_DIR = Path(__file__).resolve().parent

JAVA = "multi_agent_ide_java_parent"
ACP_BASE = f"{JAVA}/acp-cdc-ai/src/main"
MAIN_BASE = f"{JAVA}/multi_agent_ide/src/main/java/com/hayden/multiagentide"

# ── Files grouped by mental-model section ────────────────────────────────
SECTION_FILES = [
    # ── ACP Session Lifecycle ────────────────────────────────────────
    ("acp-session-lifecycle", f"{ACP_BASE}/kotlin/com/hayden/acp_cdc_ai/acp/AcpChatModel.kt"),
    ("acp-session-lifecycle", f"{ACP_BASE}/kotlin/com/hayden/acp_cdc_ai/acp/AcpSessionManager.kt"),
    ("acp-session-lifecycle", f"{ACP_BASE}/kotlin/com/hayden/acp_cdc_ai/acp/AcpSerializerTransport.kt"),
    ("acp-session-lifecycle", f"{ACP_BASE}/kotlin/com/hayden/acp_cdc_ai/acp/AcpStreamWindowBuffer.kt"),
    ("acp-session-lifecycle", f"{ACP_BASE}/kotlin/com/hayden/acp_cdc_ai/acp/AcpUtils.kt"),
    ("acp-session-lifecycle", f"{ACP_BASE}/java/com/hayden/acp_cdc_ai/acp/AcpRetryEventListener.java"),
    ("acp-session-lifecycle", f"{ACP_BASE}/java/com/hayden/acp_cdc_ai/acp/AcpSessionRetryContext.java"),
    ("acp-session-lifecycle", f"{ACP_BASE}/java/com/hayden/acp_cdc_ai/acp/ChatMemoryContext.java"),
    ("acp-session-lifecycle", f"{ACP_BASE}/java/com/hayden/acp_cdc_ai/acp/CompactionException.java"),
    ("acp-session-lifecycle", f"{ACP_BASE}/java/com/hayden/acp_cdc_ai/acp/config/AcpModelProperties.java"),
    ("acp-session-lifecycle", f"{ACP_BASE}/java/com/hayden/acp_cdc_ai/acp/config/AcpProvider.java"),
    ("acp-session-lifecycle", f"{ACP_BASE}/java/com/hayden/acp_cdc_ai/acp/config/AcpProviderConverter.java"),
    ("acp-session-lifecycle", f"{ACP_BASE}/java/com/hayden/acp_cdc_ai/acp/config/AcpProviderDefinition.java"),
    ("acp-session-lifecycle", f"{ACP_BASE}/java/com/hayden/acp_cdc_ai/acp/config/AcpResolvedCall.java"),
    ("acp-session-lifecycle", f"{ACP_BASE}/java/com/hayden/acp_cdc_ai/acp/config/AcpSessionRoutingKey.java"),
    ("acp-session-lifecycle", f"{ACP_BASE}/java/com/hayden/acp_cdc_ai/acp/config/AcpChatOptionsString.java"),
    ("acp-session-lifecycle", f"{ACP_BASE}/java/com/hayden/acp_cdc_ai/acp/config/McpProperties.java"),
    ("acp-session-lifecycle", f"{MAIN_BASE}/agent/AcpSessionCleanupService.java"),

    # ── MCP Server ───────────────────────────────────────────────────
    ("mcp-server", f"{ACP_BASE}/java/io/modelcontextprotocol/server/IdeMcpServer.java"),
    ("mcp-server", f"{ACP_BASE}/java/io/modelcontextprotocol/server/IdeMcpAsyncServer.java"),
    ("mcp-server", f"{ACP_BASE}/java/com/hayden/acp_cdc_ai/mcp/DynamicMcpToolCallbackProvider.java"),
    ("mcp-server", f"{ACP_BASE}/java/com/hayden/acp_cdc_ai/mcp/RequiredProtocolProperties.java"),
    ("mcp-server", f"{MAIN_BASE}/config/SpringMcpConfig.java"),

    # ── MCP Client ───────────────────────────────────────────────────
    ("mcp-client", f"{ACP_BASE}/java/io/modelcontextprotocol/client/transport/DelegatingHttpClientStreamableHttpTransport.java"),

    # ── Sandbox Translation ──────────────────────────────────────────
    ("sandbox-translation", f"{ACP_BASE}/java/com/hayden/acp_cdc_ai/sandbox/SandboxTranslationRegistry.java"),
    ("sandbox-translation", f"{ACP_BASE}/java/com/hayden/acp_cdc_ai/sandbox/SandboxTranslation.java"),
    ("sandbox-translation", f"{ACP_BASE}/java/com/hayden/acp_cdc_ai/sandbox/SandboxTranslationStrategy.java"),
    ("sandbox-translation", f"{ACP_BASE}/java/com/hayden/acp_cdc_ai/sandbox/SandboxContext.java"),
    ("sandbox-translation", f"{ACP_BASE}/java/com/hayden/acp_cdc_ai/sandbox/SandboxArgUtils.java"),
    ("sandbox-translation", f"{ACP_BASE}/java/com/hayden/acp_cdc_ai/sandbox/ClaudeCodeSandboxStrategy.java"),
    ("sandbox-translation", f"{ACP_BASE}/java/com/hayden/acp_cdc_ai/sandbox/CodexSandboxStrategy.java"),

    # ── Permission Gating ────────────────────────────────────────────
    ("permission-gating", f"{ACP_BASE}/kotlin/com/hayden/acp_cdc_ai/permission/IPermissionGate.kt"),
    ("permission-gating", f"{ACP_BASE}/java/com/hayden/acp_cdc_ai/repository/RequestContext.java"),
    ("permission-gating", f"{ACP_BASE}/java/com/hayden/acp_cdc_ai/repository/RequestContextRepository.java"),

    # ── Streaming ────────────────────────────────────────────────────
    ("streaming", f"{MAIN_BASE}/tool/EmbabelToolObjectProvider.java"),
    ("streaming", f"{MAIN_BASE}/tool/EmbabelToolObjectRegistry.java"),
    ("streaming", f"{MAIN_BASE}/tool/LazyToolObjectRegistration.java"),
    ("streaming", f"{MAIN_BASE}/tool/McpToolObjectRegistrar.java"),
    ("streaming", f"{MAIN_BASE}/tool/ToolAbstraction.java"),
    ("streaming", f"{MAIN_BASE}/tool/ToolContext.java"),
    ("streaming", f"{MAIN_BASE}/agent/AcpTooling.java"),
]


def _cleanup_stale_symlinks(view_dir: Path):
    """Remove all symlinks under the view directory (except in mental-models/)."""
    for item in sorted(view_dir.rglob("*")):
        if item.is_symlink() and "mental-models" not in item.parts:
            item.unlink()
    for item in sorted(view_dir.rglob("*"), reverse=True):
        if item.is_dir() and "mental-models" not in str(item.relative_to(view_dir)):
            try:
                item.rmdir()
            except OSError:
                pass


def regenerate():
    (VIEW_DIR / "mental-models").mkdir(exist_ok=True)
    _cleanup_stale_symlinks(VIEW_DIR)

    for section_dir, rel_path in SECTION_FILES:
        source = REPO_ROOT / rel_path
        if not source.exists():
            print(f"  WARN: {rel_path} does not exist, skipping")
            continue
        link_dir = VIEW_DIR / section_dir
        link = link_dir / source.name
        link_dir.mkdir(parents=True, exist_ok=True)
        if not link.exists():
            rel_target = os.path.relpath(source, link.parent)
            link.symlink_to(rel_target)


if __name__ == "__main__":
    regenerate()
