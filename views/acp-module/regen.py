#!/usr/bin/env python3
"""Regenerate the acp-module view.

ACP chat model and session lifecycle, event/artifact system, sandbox
translation, MCP integration, filter instruction types, configuration,
and request context.

Symlinks are grouped by mental-model section so the directory layout mirrors
the mental model headings.
"""
import os
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
VIEW_DIR = Path(__file__).resolve().parent

JAVA = "multi_agent_ide_java_parent"
ACP = f"{JAVA}/acp-cdc-ai/src/main"

# ── Files grouped by mental-model section ────────────────────────────────
SECTION_FILES = [
    # ── ACP Chat Model and Session Lifecycle ──────────────────────────
    ("acp-chat-model-session", f"{ACP}/kotlin/com/hayden/acp_cdc_ai/acp/AcpChatModel.kt"),
    ("acp-chat-model-session", f"{ACP}/kotlin/com/hayden/acp_cdc_ai/acp/AcpSessionManager.kt"),
    ("acp-chat-model-session", f"{ACP}/kotlin/com/hayden/acp_cdc_ai/acp/AcpStreamWindowBuffer.kt"),
    ("acp-chat-model-session", f"{ACP}/kotlin/com/hayden/acp_cdc_ai/acp/AcpSerializerTransport.kt"),
    ("acp-chat-model-session", f"{ACP}/kotlin/com/hayden/acp_cdc_ai/acp/AcpUtils.kt"),
    ("acp-chat-model-session", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/AcpRetryEventListener.java"),
    ("acp-chat-model-session", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/AcpSessionRetryContext.java"),
    ("acp-chat-model-session", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/ChatMemoryContext.java"),
    ("acp-chat-model-session", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/CompactionException.java"),

    # ── Event and Artifact System ─────────────────────────────────────
    ("event-artifact-system", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/events/EventBus.java"),
    ("event-artifact-system", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/events/EventListener.java"),
    ("event-artifact-system", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/events/Events.java"),
    ("event-artifact-system", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/events/EventNode.java"),
    ("event-artifact-system", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/events/Artifact.java"),
    ("event-artifact-system", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/events/ArtifactKey.java"),
    ("event-artifact-system", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/events/ArtifactHashing.java"),
    ("event-artifact-system", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/events/CanonicalJson.java"),
    ("event-artifact-system", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/events/HasContextId.java"),
    ("event-artifact-system", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/events/McpRequestContext.java"),
    ("event-artifact-system", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/events/MessageStreamArtifact.java"),
    ("event-artifact-system", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/events/Templated.java"),
    ("event-artifact-system", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/events/AgUiEventMappingRegistry.java"),
    ("event-artifact-system", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/events/AgUiSerdes.java"),

    # ── Sandbox Translation ───────────────────────────────────────────
    ("sandbox-translation", f"{ACP}/java/com/hayden/acp_cdc_ai/sandbox/SandboxTranslationRegistry.java"),
    ("sandbox-translation", f"{ACP}/java/com/hayden/acp_cdc_ai/sandbox/SandboxTranslationStrategy.java"),
    ("sandbox-translation", f"{ACP}/java/com/hayden/acp_cdc_ai/sandbox/SandboxTranslation.java"),
    ("sandbox-translation", f"{ACP}/java/com/hayden/acp_cdc_ai/sandbox/SandboxContext.java"),
    ("sandbox-translation", f"{ACP}/java/com/hayden/acp_cdc_ai/sandbox/SandboxArgUtils.java"),
    ("sandbox-translation", f"{ACP}/java/com/hayden/acp_cdc_ai/sandbox/ClaudeCodeSandboxStrategy.java"),
    ("sandbox-translation", f"{ACP}/java/com/hayden/acp_cdc_ai/sandbox/CodexSandboxStrategy.java"),

    # ── MCP Integration ───────────────────────────────────────────────
    ("mcp-integration", f"{ACP}/java/com/hayden/acp_cdc_ai/mcp/DynamicMcpToolCallbackProvider.java"),
    ("mcp-integration", f"{ACP}/java/com/hayden/acp_cdc_ai/mcp/RequiredProtocolProperties.java"),
    ("mcp-integration", f"{ACP}/java/io/modelcontextprotocol/client/transport/DelegatingHttpClientStreamableHttpTransport.java"),
    ("mcp-integration", f"{ACP}/java/io/modelcontextprotocol/server/IdeMcpAsyncServer.java"),
    ("mcp-integration", f"{ACP}/java/io/modelcontextprotocol/server/IdeMcpServer.java"),

    # ── Filter Instruction Types ──────────────────────────────────────
    ("filter-instruction-types", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/filter/FilteredObject.java"),
    ("filter-instruction-types", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/filter/FilterEnums.java"),
    ("filter-instruction-types", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/filter/Instruction.java"),
    ("filter-instruction-types", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/filter/InstructionJson.java"),
    ("filter-instruction-types", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/filter/InstructionMatcher.java"),
    ("filter-instruction-types", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/filter/path/JsonPath.java"),
    ("filter-instruction-types", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/filter/path/MarkdownPath.java"),
    ("filter-instruction-types", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/filter/path/Path.java"),
    ("filter-instruction-types", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/filter/path/RegexPath.java"),

    # ── ACP Configuration ─────────────────────────────────────────────
    ("acp-configuration", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/config/AcpModelProperties.java"),
    ("acp-configuration", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/config/AcpProvider.java"),
    ("acp-configuration", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/config/AcpProviderConverter.java"),
    ("acp-configuration", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/config/AcpProviderDefinition.java"),
    ("acp-configuration", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/config/AcpResolvedCall.java"),
    ("acp-configuration", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/config/AcpSessionRoutingKey.java"),
    ("acp-configuration", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/config/AcpChatOptionsString.java"),
    ("acp-configuration", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/config/McpProperties.java"),
    ("acp-configuration", f"{ACP}/kotlin/com/hayden/acp_cdc_ai/permission/IPermissionGate.kt"),

    # ── Request Context ───────────────────────────────────────────────
    ("request-context", f"{ACP}/java/com/hayden/acp_cdc_ai/repository/RequestContext.java"),
    ("request-context", f"{ACP}/java/com/hayden/acp_cdc_ai/repository/RequestContextRepository.java"),
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
