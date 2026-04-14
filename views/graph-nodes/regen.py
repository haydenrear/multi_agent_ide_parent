#!/usr/bin/env python3
"""Regenerate the graph-nodes view.

The sealed GraphNode type hierarchy, three-phase fan-out pattern, node
lifecycle/status transitions, interrupt flow, WorkflowGraphState routing
table, and side-channel nodes.

Symlinks are grouped by mental-model section so the directory layout mirrors
the mental model headings.
"""
import os
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
VIEW_DIR = Path(__file__).resolve().parent

JAVA = "multi_agent_ide_java_parent"
BASE = f"{JAVA}/multi_agent_ide/src/main/java/com/hayden/multiagentide"

# ── Files grouped by mental-model section ────────────────────────────────
SECTION_FILES = [
    # ── Sealed Type Hierarchy ────────────────────────────────────────
    ("sealed-type-hierarchy", f"{BASE}/model/nodes/GraphNode.java"),
    ("sealed-type-hierarchy", f"{BASE}/model/nodes/GraphNodeBuilderHelper.java"),
    ("sealed-type-hierarchy", f"{BASE}/model/nodes/ExecutionNode.java"),
    ("sealed-type-hierarchy", f"{BASE}/model/nodes/Orchestrator.java"),
    ("sealed-type-hierarchy", f"{BASE}/model/nodes/Collector.java"),
    ("sealed-type-hierarchy", f"{BASE}/model/nodes/CollectorNode.java"),
    ("sealed-type-hierarchy", f"{BASE}/model/nodes/Interruptible.java"),
    ("sealed-type-hierarchy", f"{BASE}/model/nodes/HasWorkflowContext.java"),
    ("sealed-type-hierarchy", f"{BASE}/model/nodes/HasChatId.java"),
    ("sealed-type-hierarchy", f"{BASE}/model/nodes/Viewable.java"),

    # ── Three-Phase Fan-Out ──────────────────────────────────────────
    ("three-phase-fan-out", f"{BASE}/model/nodes/OrchestratorNode.java"),
    ("three-phase-fan-out", f"{BASE}/model/nodes/DiscoveryOrchestratorNode.java"),
    ("three-phase-fan-out", f"{BASE}/model/nodes/DiscoveryDispatchAgentNode.java"),
    ("three-phase-fan-out", f"{BASE}/model/nodes/DiscoveryNode.java"),
    ("three-phase-fan-out", f"{BASE}/model/nodes/DiscoveryCollectorNode.java"),
    ("three-phase-fan-out", f"{BASE}/model/nodes/PlanningOrchestratorNode.java"),
    ("three-phase-fan-out", f"{BASE}/model/nodes/PlanningDispatchAgentNode.java"),
    ("three-phase-fan-out", f"{BASE}/model/nodes/PlanningNode.java"),
    ("three-phase-fan-out", f"{BASE}/model/nodes/PlanningCollectorNode.java"),
    ("three-phase-fan-out", f"{BASE}/model/nodes/TicketOrchestratorNode.java"),
    ("three-phase-fan-out", f"{BASE}/model/nodes/TicketDispatchAgentNode.java"),
    ("three-phase-fan-out", f"{BASE}/model/nodes/TicketNode.java"),
    ("three-phase-fan-out", f"{BASE}/model/nodes/TicketCollectorNode.java"),

    # ── Node Lifecycle ───────────────────────────────────────────────
    ("node-lifecycle", f"{BASE}/model/nodes/CollectedNodeStatus.java"),
    ("node-lifecycle", f"{BASE}/model/nodes/WorkflowContext.java"),

    # ── Interrupt Flow ───────────────────────────────────────────────
    ("interrupt-flow", f"{BASE}/model/nodes/Interrupt.java"),
    ("interrupt-flow", f"{BASE}/model/nodes/InterruptContext.java"),
    ("interrupt-flow", f"{BASE}/model/nodes/InterruptNode.java"),
    ("interrupt-flow", f"{BASE}/model/nodes/InterruptRecord.java"),
    ("interrupt-flow", f"{BASE}/model/nodes/ReviewNode.java"),

    # ── Side-Channel Nodes ───────────────────────────────────────────
    ("side-channel-nodes", f"{BASE}/model/nodes/DataLayerOperationNode.java"),
    ("side-channel-nodes", f"{BASE}/model/nodes/AgentToAgentConversationNode.java"),
    ("side-channel-nodes", f"{BASE}/model/nodes/AgentToControllerConversationNode.java"),
    ("side-channel-nodes", f"{BASE}/model/nodes/ControllerToAgentConversationNode.java"),
    ("side-channel-nodes", f"{BASE}/model/nodes/AskPermissionNode.java"),
    ("side-channel-nodes", f"{BASE}/model/nodes/MergeNode.java"),
    ("side-channel-nodes", f"{BASE}/model/nodes/SubmoduleNode.java"),
    ("side-channel-nodes", f"{BASE}/model/nodes/SummaryNode.java"),
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
