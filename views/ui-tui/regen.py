#!/usr/bin/env python3
"""Regenerate the ui-tui view.

The TUI: immutable state tree, pure reducer, two independent consumers
(TuiSession + UiStateStore), TUI view hierarchy, and event-to-UI flow.

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
    # ── The State Tree ───────────────────────────────────────────────
    ("state-tree", f"{BASE}/ui/state/UiState.java"),
    ("state-tree", f"{BASE}/ui/state/UiSessionState.java"),
    ("state-tree", f"{BASE}/ui/state/UiFocus.java"),
    ("state-tree", f"{BASE}/ui/state/UiViewport.java"),
    ("state-tree", f"{BASE}/ui/state/UiChatSearch.java"),

    # ── The Pure Reducer ─────────────────────────────────────────────
    ("pure-reducer", f"{BASE}/ui/state/UiStateReducer.java"),

    # ── Two Independent Consumers ────────────────────────────────────
    ("two-consumers", f"{BASE}/ui/shared/UiStateStore.java"),
    ("two-consumers", f"{BASE}/tui/TuiSession.java"),
    ("two-consumers", f"{BASE}/ui/shared/UiStateSnapshot.java"),
    ("two-consumers", f"{BASE}/ui/shared/UiSessionSnapshot.java"),

    # ── Event Flow ───────────────────────────────────────────────────
    ("event-flow", f"{BASE}/ui/shared/SharedUiInteractionService.java"),
    ("event-flow", f"{BASE}/ui/shared/SharedUiInteractionServiceImpl.java"),
    ("event-flow", f"{BASE}/ui/shared/UiActionCommand.java"),
    ("event-flow", f"{BASE}/ui/shared/UiActionMapper.java"),

    # ── TUI View Hierarchy ───────────────────────────────────────────
    ("tui-view-hierarchy", f"{BASE}/tui/TuiChatView.java"),
    ("tui-view-hierarchy", f"{BASE}/tui/TuiDetailTextView.java"),
    ("tui-view-hierarchy", f"{BASE}/tui/TuiHeaderView.java"),
    ("tui-view-hierarchy", f"{BASE}/tui/TuiMessageStreamView.java"),
    ("tui-view-hierarchy", f"{BASE}/tui/TuiSessionMenuView.java"),
    ("tui-view-hierarchy", f"{BASE}/tui/TuiSessionView.java"),
    ("tui-view-hierarchy", f"{BASE}/tui/TuiTerminalView.java"),
    ("tui-view-hierarchy", f"{BASE}/tui/TuiTextLayout.java"),

    # ── Skill References ──────────────────────────────────────────────
    ("state-tree", f"{SKILLS}/multi_agent_ide_ui_test/SKILL.md"),
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
