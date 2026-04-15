#!/usr/bin/env python3
"""Regenerate the orchestration-model view.

The orchestration layer: goal submission, workflow-graph polling, the primary
UI controller (LlmOrchestrationController), interrupt/permission handling,
agent conversations, worktree creation, communication topology, and the
computation graph orchestrator.

Symlinks are grouped by mental-model section so the directory layout mirrors
the mental model headings.  The same source file may appear in multiple
section directories when it plays a role in more than one conceptual area.
"""
import os
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
VIEW_DIR = Path(__file__).resolve().parent

JAVA = "multi_agent_ide_java_parent"
BASE = f"{JAVA}/multi_agent_ide/src/main/java/com/hayden/multiagentide"
BASE_KT = f"{JAVA}/multi_agent_ide/src/main/kotlin/com/hayden/multiagentide"
SKILLS = "skills/multi_agent_ide_skills"

# ── Files grouped by mental-model section ────────────────────────────────
#
# Each tuple is (section_directory, source_file_relative_to_REPO_ROOT).
# Section directories correspond to mental-model headings so that browsing
# the view directory mirrors the document structure.

SECTION_FILES = [
    # ── Goal Lifecycle ────────────────────────────────────────────────
    # How a goal is submitted, delegated, and bootstrapped.
    ("goal-lifecycle", f"{BASE}/controller/LlmOrchestrationController.java"),
    ("goal-lifecycle", f"{BASE}/controller/OrchestrationController.java"),
    ("goal-lifecycle", f"{BASE}/controller/GoalExecutor.java"),
    ("goal-lifecycle", f"{BASE}/agent/AgentLifecycleHandler.java"),

    # ── Workflow-Graph Polling (LlmOrchestrationController) ───────────
    # The primary controller the controller-skill polls to monitor
    # workflow progress, node state, events, and blocked items.
    ("workflow-graph-polling", f"{BASE}/controller/LlmOrchestrationController.java"),
    ("workflow-graph-polling", f"{BASE}/controller/model/NodeIdRequest.java"),
    ("workflow-graph-polling", f"{BASE}/cli/CliEventFormatter.java"),
    ("workflow-graph-polling", f"{BASE}/ui/shared/UiStateStore.java"),

    # ── Interrupt & Permission Handling ────────────────────────────────
    # How agents block on human input and how the controller resolves.
    ("interrupt-permission", f"{BASE}/controller/InterruptController.java"),
    ("interrupt-permission", f"{BASE}/controller/PermissionController.java"),
    ("interrupt-permission", f"{BASE}/service/InterruptService.java"),
    ("interrupt-permission", f"{BASE}/service/PermissionGateService.java"),
    ("interrupt-permission", f"{BASE_KT}/gate/PermissionGate.kt"),

    # ── Agent Conversations ───────────────────────────────────────────
    # Structured conversations between agents and the controller.
    ("agent-conversations", f"{BASE}/controller/AgentConversationController.java"),
    ("agent-conversations", f"{BASE}/controller/ActivityCheckController.java"),
    ("agent-conversations", f"{BASE}/agent/AgentTopologyTools.java"),
    ("agent-conversations", f"{BASE}/service/AgentExecutor.java"),
    ("agent-conversations", f"{BASE_KT}/gate/PermissionGate.kt"),

    # ── Computation Graph Orchestrator ────────────────────────────────
    # Node CRUD, event emission, parent-child linking.
    ("computation-graph", f"{BASE}/orchestration/ComputationGraphOrchestrator.java"),
    ("computation-graph", f"{BASE}/agent/WorkflowGraphService.java"),
    ("computation-graph", f"{BASE}/agent/WorkflowGraphState.java"),

    # ── Worktree Creation ─────────────────────────────────────────────
    # Initial worktree setup for agent filesystem isolation.
    ("worktree-creation", f"{BASE}/service/WorktreeService.java"),
    ("worktree-creation", f"{BASE}/service/GitWorktreeService.java"),

    # ── Communication Topology ────────────────────────────────────────
    # Which agents can talk to which, topology enforcement.
    ("communication-topology", f"{BASE}/topology/CommunicationTopologyConfig.java"),
    ("communication-topology", f"{BASE}/topology/CommunicationTopologyProvider.java"),
    ("communication-topology", f"{BASE}/service/AgentCommunicationService.java"),
    ("communication-topology", f"{SKILLS}/multi_agent_ide_controller/conversational-topology/reference.md"),

    # ── Session & Control ─────────────────────────────────────────────
    # ACP session resolution and agent control (pause/stop/resume).
    ("session-control", f"{BASE}/service/SessionKeyResolutionService.java"),
    ("session-control", f"{BASE}/service/AgentControlService.java"),

    # ── Propagation Items ─────────────────────────────────────────────
    # Propagation query surface used by the controller skill.
    ("propagation-items", f"{BASE}/propagation/service/PropagationItemService.java"),
    ("propagation-items", f"{BASE}/propagation/controller/PropagationController.java"),

    # ── MCP Server Flow ─────────────────────────────────────────────
    # How the MCP server is served over REST and how ACP clients connect.
    ("mcp-server-flow", f"{BASE}/config/SpringMcpConfig.java"),
    ("mcp-server-flow", f"{BASE}/tool/McpToolObjectRegistrar.java"),
    ("mcp-server-flow", f"{BASE}/tool/EmbabelToolObjectRegistry.java"),
    ("mcp-server-flow", f"{BASE}/agent/AgentTopologyTools.java"),

    # ── Skill References (per-section) ────────────────────────────────
    ("goal-lifecycle",        f"{SKILLS}/multi_agent_ide_controller/workflows/standard_workflow.md"),
    ("workflow-graph-polling", f"{SKILLS}/multi_agent_ide_controller/executables/poll.py"),
    ("interrupt-permission",  f"{SKILLS}/multi_agent_ide_controller/executables/interrupts.py"),
    ("interrupt-permission",  f"{SKILLS}/multi_agent_ide_controller/executables/permissions.py"),
    ("agent-conversations",   f"{SKILLS}/multi_agent_ide_controller/executables/conversations.py"),
    ("agent-conversations",   f"{SKILLS}/multi_agent_ide_controller/conversational-topology/checklist.md"),
    ("computation-graph",     f"{SKILLS}/multi_agent_ide_controller/references/program_model.md"),
    ("session-control",       f"{SKILLS}/multi_agent_ide_controller/references/session_identity_model.md"),
    ("propagation-items",     f"{SKILLS}/multi_agent_ide_controller/executables/ack_propagations.py"),

    # ── Skill Cross-Reference (one-stop shop) ─────────────────────────
    ("skill-cross-reference", f"{SKILLS}/multi_agent_ide_controller/SKILL.md"),
    ("skill-cross-reference", f"{SKILLS}/multi_agent_ide_deploy/SKILL.md"),
    ("skill-cross-reference", f"{SKILLS}/multi_agent_ide_deploy/scripts/clone_or_pull.py"),
    ("skill-cross-reference", f"{SKILLS}/multi_agent_ide_deploy/scripts/deploy_restart.py"),
    ("skill-cross-reference", f"{SKILLS}/multi_agent_ide_api/SKILL.md"),
    ("skill-cross-reference", f"{SKILLS}/multi_agent_ide_api/scripts/api_schema.py"),
    ("skill-cross-reference", f"{SKILLS}/multi_agent_ide_debug/SKILL.md"),
    ("skill-cross-reference", f"{SKILLS}/multi_agent_ide_debug/executables/error_search.py"),
    ("skill-cross-reference", f"{SKILLS}/multi_agent_ide_debug/executables/error_patterns.csv"),
    ("skill-cross-reference", f"{SKILLS}/multi_agent_ide_validation/SKILL.md"),
    ("skill-cross-reference", f"{SKILLS}/multi_agent_ide_validation/workflows/validation_workflow.md"),
    ("skill-cross-reference", f"{SKILLS}/multi_agent_ide_contracts/SKILL.md"),
]


def _cleanup_stale_symlinks(view_dir: Path):
    """Remove all symlinks under the view directory (except in mental-models/)."""
    for item in sorted(view_dir.rglob("*")):
        if item.is_symlink() and "mental-models" not in item.parts:
            item.unlink()
    # Remove empty directories left behind (bottom-up)
    for item in sorted(view_dir.rglob("*"), reverse=True):
        if item.is_dir() and "mental-models" not in str(item.relative_to(view_dir)):
            try:
                item.rmdir()  # only succeeds if empty
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
