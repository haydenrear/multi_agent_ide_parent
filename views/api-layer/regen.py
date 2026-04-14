#!/usr/bin/env python3
"""Regenerate the api-layer view.

REST endpoint map, goal execution flow, agent control, agent-to-controller
conversation flow, CLI in-process controller call, and configuration.

Symlinks are grouped by mental-model section so the directory layout mirrors
the mental model headings.
"""
import os
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
VIEW_DIR = Path(__file__).resolve().parent

JAVA = "multi_agent_ide_java_parent"
BASE = f"{JAVA}/multi_agent_ide/src/main/java/com/hayden/multiagentide"
SKILLS = "skills/multi_agent_ide_skills"

# ── Files grouped by mental-model section ────────────────────────────────
SECTION_FILES = [
    # ── Goal Execution Flow ──────────────────────────────────────────
    ("goal-execution-flow", f"{BASE}/controller/LlmOrchestrationController.java"),
    ("goal-execution-flow", f"{BASE}/controller/OrchestrationController.java"),
    ("goal-execution-flow", f"{BASE}/controller/GoalExecutor.java"),

    # ── Controller Endpoint Map ──────────────────────────────────────
    ("controller-endpoint-map", f"{BASE}/controller/EventStreamController.java"),
    ("controller-endpoint-map", f"{BASE}/controller/InterruptController.java"),
    ("controller-endpoint-map", f"{BASE}/controller/PermissionController.java"),
    ("controller-endpoint-map", f"{BASE}/controller/AgentControlController.java"),
    ("controller-endpoint-map", f"{BASE}/controller/UiController.java"),
    ("controller-endpoint-map", f"{BASE}/controller/model/NodeIdRequest.java"),
    ("controller-endpoint-map", f"{BASE}/controller/model/RunIdRequest.java"),

    # ── Agent Control ────────────────────────────────────────────────
    ("agent-control", f"{BASE}/service/AgentControlService.java"),
    ("agent-control", f"{BASE}/service/InterruptService.java"),
    ("agent-control", f"{BASE}/service/InterruptSchemaGenerator.java"),
    ("agent-control", f"{BASE}/service/PermissionGateService.java"),
    ("agent-control", f"{BASE}/service/SessionKeyResolutionService.java"),

    # ── Agent-to-Controller Conversation ─────────────────────────────
    ("agent-controller-conversation", f"{BASE}/controller/AgentConversationController.java"),
    ("agent-controller-conversation", f"{BASE}/controller/ActivityCheckController.java"),
    ("agent-controller-conversation", f"{BASE}/service/AgentCommunicationService.java"),

    # ── CLI ──────────────────────────────────────────────────────────
    ("cli", f"{BASE}/cli/ArtifactKeyFormatter.java"),
    ("cli", f"{BASE}/cli/CliEventFormatter.java"),
    ("cli", f"{BASE}/cli/CliTuiRunner.java"),
    ("cli", f"{BASE}/cli/ToolCallRenderer.java"),
    ("cli", f"{BASE}/cli/ToolCallRendererFactory.java"),

    # ── Configuration ────────────────────────────────────────────────
    ("configuration", f"{BASE}/config/AgentModelMixin.java"),
    ("configuration", f"{BASE}/config/ArtifactConfig.java"),
    ("configuration", f"{BASE}/config/ArtifactMixin.java"),
    ("configuration", f"{BASE}/config/BlackboardRoutingPlanner.java"),
    ("configuration", f"{BASE}/config/BlackboardRoutingPlannerFactory.java"),
    ("configuration", f"{BASE}/config/CliModeConfig.java"),
    ("configuration", f"{BASE}/config/LlmModelSelectionProperties.java"),
    ("configuration", f"{BASE}/config/MultiAgentEmbabelConfig.java"),
    ("configuration", f"{BASE}/config/MultiAgentIdeConfig.java"),
    ("configuration", f"{BASE}/config/SerdesConfiguration.java"),
    ("configuration", f"{BASE}/config/SpringMcpConfig.java"),
    ("configuration", f"{BASE}/config/ValidationExceptionHandler.java"),
    ("configuration", f"{BASE}/config/WebSocketConfig.java"),

    # ── Services ─────────────────────────────────────────────────────
    ("services", f"{BASE}/service/AgentExecutor.java"),
    ("services", f"{BASE}/service/ActionRetryListenerImpl.java"),
    ("services", f"{BASE}/service/RequestEnrichment.java"),
    ("services", f"{BASE}/service/DefaultLlmRunner.java"),
    ("services", f"{BASE}/service/UiStateService.java"),
    ("services", f"{BASE}/service/GitMergeService.java"),
    ("services", f"{BASE}/service/GitWorktreeService.java"),
    ("services", f"{BASE}/service/WorktreeAutoCommitService.java"),
    ("services", f"{BASE}/service/WorktreeMergeConflictService.java"),
    ("services", f"{BASE}/service/WorktreeService.java"),

    # ── Adapters ─────────────────────────────────────────────────────
    ("adapters", f"{BASE}/adapter/SaveEventToEventRepositoryListener.java"),
    ("adapters", f"{BASE}/adapter/SaveGraphEventToGraphRepositoryListener.java"),
    ("adapters", f"{BASE}/adapter/SseEventAdapter.java"),
    ("adapters", f"{BASE}/adapter/WebSocketEventAdapter.java"),

    # ── Skill References ──────────────────────────────────────────────
    ("controller-endpoint-map", f"{SKILLS}/multi_agent_ide_api/scripts/api_schema.py"),
    ("controller-endpoint-map", f"{SKILLS}/multi_agent_ide_api/scripts/_client.py"),
    ("controller-endpoint-map", f"{SKILLS}/multi_agent_ide_api/SKILL.md"),
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
