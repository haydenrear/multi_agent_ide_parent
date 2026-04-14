#!/usr/bin/env python3
"""Regenerate the artifact-key view.

ArtifactKey hierarchy: ULID-based hierarchical identifiers that unify
session IDs, node IDs, and chat IDs across the execution tree.

Covers: ArtifactKey record, key-to-graph-node mapping, session key
resolution, ACP session routing, chat ID derivation, session lifecycle
events, session cleanup, controller scope-based lookup/fallback, output
channel message targeting, and agent conversation ArtifactKey routing.

Symlinks are grouped by mental-model section so the directory layout mirrors
the mental model headings.
"""
import os
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
VIEW_DIR = Path(__file__).resolve().parent

JAVA = "multi_agent_ide_java_parent"
ACP = f"{JAVA}/acp-cdc-ai/src/main"
MAIN = f"{JAVA}/multi_agent_ide/src/main/java/com/hayden/multiagentide"

# ── Files grouped by mental-model section ────────────────────────────────
SECTION_FILES = [
    # ── ArtifactKey Record ───────────────────────────────────────────
    ("artifact-key-record", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/events/ArtifactKey.java"),
    ("artifact-key-record", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/events/HasContextId.java"),

    # ── Key-to-Node Mapping ──────────────────────────────────────────
    ("key-to-node-mapping", f"{MAIN}/model/nodes/GraphNode.java"),
    ("key-to-node-mapping", f"{MAIN}/model/nodes/ExecutionNode.java"),
    ("key-to-node-mapping", f"{MAIN}/model/nodes/HasChatId.java"),
    ("key-to-node-mapping", f"{MAIN}/agent/GraphNodeFactory.java"),
    ("key-to-node-mapping", f"{MAIN}/cli/ArtifactKeyFormatter.java"),

    # ── Session Key Resolution ───────────────────────────────────────
    ("session-key-resolution", f"{MAIN}/service/SessionKeyResolutionService.java"),
    ("session-key-resolution", f"{MAIN}/filter/service/AiFilterSessionResolver.java"),

    # ── ACP Session Routing ──────────────────────────────────────────
    ("acp-session-routing", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/config/AcpChatOptionsString.java"),
    ("acp-session-routing", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/config/AcpSessionRoutingKey.java"),
    ("acp-session-routing", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/config/AcpResolvedCall.java"),
    ("acp-session-routing", f"{ACP}/kotlin/com/hayden/acp_cdc_ai/acp/AcpSessionManager.kt"),
    ("acp-session-routing", f"{ACP}/kotlin/com/hayden/acp_cdc_ai/acp/AcpChatModel.kt"),

    # ── Chat ID Derivation ───────────────────────────────────────────
    ("chat-id-derivation", f"{MAIN}/prompt/PromptContext.java"),
    ("chat-id-derivation", f"{MAIN}/agent/AgentModels.java"),

    # ── Session Lifecycle Events ─────────────────────────────────────
    ("session-lifecycle-events", f"{ACP}/java/com/hayden/acp_cdc_ai/acp/events/Events.java"),
    ("session-lifecycle-events", f"{MAIN}/artifacts/EventArtifactMapper.java"),
    ("session-lifecycle-events", f"{MAIN}/artifacts/ArtifactEmissionService.java"),

    # ── Session Cleanup ──────────────────────────────────────────────
    ("session-cleanup", f"{MAIN}/agent/AcpSessionCleanupService.java"),

    # ── Controller Scope-Based Lookup ────────────────────────────────
    ("controller-scope-lookup", f"{MAIN}/controller/PermissionController.java"),
    ("controller-scope-lookup", f"{MAIN}/controller/InterruptController.java"),
    ("controller-scope-lookup", f"{MAIN}/controller/LlmOrchestrationController.java"),
    ("controller-scope-lookup", f"{MAIN}/service/PermissionGateService.java"),

    # ── Output Channel Message Targeting ─────────────────────────────
    ("output-channel-targeting", f"{MAIN}/config/MultiAgentEmbabelConfig.java"),
    ("output-channel-targeting", f"{MAIN}/service/SessionKeyResolutionService.java"),

    # ── Agent Conversation Routing ───────────────────────────────────
    ("agent-conversation-routing", f"{MAIN}/controller/AgentConversationController.java"),
    ("agent-conversation-routing", f"{MAIN}/service/AgentExecutor.java"),
    ("agent-conversation-routing", f"{MAIN}/agent/AgentTopologyTools.java"),
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
