#!/usr/bin/env python3
"""Regenerate the testing view.

Testing infrastructure: integration test base classes, queue-based LLM
mocking, end-to-end ACP integration tests, worktree merge tests, test
event listeners, test support utilities, test-matrix documentation, and
MCP server test setup.

Symlinks are grouped by mental-model section so the directory layout mirrors
the mental model headings.
"""
import os
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent.parent
VIEW_DIR = Path(__file__).resolve().parent

JAVA = "multi_agent_ide_java_parent"
BASE = f"{JAVA}/multi_agent_ide/src/main/java/com/hayden/multiagentide"
TEST = f"{JAVA}/multi_agent_ide/src/test/java/com/hayden/multiagentide"
ACP = f"{JAVA}/acp-cdc-ai/src/main"
SKILLS = "skills/multi_agent_ide_skills"

# ── Files grouped by mental-model section ────────────────────────────────
SECTION_FILES = [
    # ── Test Infrastructure ──────────────────────────────────────────
    ("test-infrastructure", f"{TEST}/support/AgentTestBase.java"),
    ("test-infrastructure", f"{TEST}/support/QueuedLlmRunner.java"),
    ("test-infrastructure", f"{TEST}/support/TestEventListener.java"),

    # ── Integration Tests: Queued Workflow ────────────────────────────
    ("integration-queued-workflow", f"{TEST}/integration/WorkflowAgentQueuedTest.java"),

    # ── Integration Tests: Worktree Merge ─────────────────────────────
    ("integration-worktree-merge", f"{TEST}/integration/WorkflowAgentWorktreeMergeIntTest.java"),

    # ── ACP End-to-End Tests ──────────────────────────────────────────
    ("acp-end-to-end", f"{TEST}/acp_tests/AcpChatModelCodexIntegrationTest.java"),

    # ── MCP Server Test Setup ─────────────────────────────────────────
    ("mcp-server-test-setup", f"{BASE}/config/SpringMcpConfig.java"),
    ("mcp-server-test-setup", f"{BASE}/tool/McpToolObjectRegistrar.java"),
    ("mcp-server-test-setup", f"{BASE}/tool/EmbabelToolObjectRegistry.java"),

    # ── Agent Communication Tests ─────────────────────────────────────
    ("agent-communication-tests", f"{TEST}/topology/CommunicationTopologyConfigTest.java"),
    ("agent-communication-tests", f"{TEST}/service/AgentCommunicationServiceTest.java"),

    # ── Test Matrix Reference ─────────────────────────────────────────
    ("test-matrix-reference", ".claude/commands/test-matrix.md"),
    ("test-matrix-reference", f"{JAVA}/multi_agent_ide/src/test/resources/SURFACE.md"),
    ("test-matrix-reference", f"{JAVA}/multi_agent_ide/src/test/resources/INVARIANTS.md"),
    ("test-matrix-reference", f"{JAVA}/multi_agent_ide/src/test/resources/EXPLORATION.md"),

    # ── Validation Skill ───────────────────────────────────────────────
    ("validation-skill", f"{SKILLS}/multi_agent_ide_validation/SKILL.md"),
    ("validation-skill", f"{SKILLS}/multi_agent_ide_validation/workflows/validation_workflow.md"),

    # ── Debug Skill ────────────────────────────────────────────────────
    ("debug-skill", f"{SKILLS}/multi_agent_ide_debug/SKILL.md"),
    ("debug-skill", f"{SKILLS}/multi_agent_ide_debug/executables/error_search.py"),
    ("debug-skill", f"{SKILLS}/multi_agent_ide_debug/executables/error_patterns.csv"),
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
