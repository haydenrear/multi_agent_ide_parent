#!/usr/bin/env python3
"""Regenerate the event-messaging view.

The event system: synchronous in-process pub/sub bus, event type taxonomy,
listener ordering, SSE/WebSocket adapters, and the AgentRunner event-to-agent
bridge.

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
    # ── DefaultEventBus ──────────────────────────────────────────────
    ("default-event-bus", f"{BASE}/infrastructure/DefaultEventBus.java"),
    ("default-event-bus", f"{BASE}/infrastructure/EventAdapter.java"),

    # ── Subscriber Table ─────────────────────────────────────────────
    ("subscriber-table", f"{BASE}/infrastructure/AgentEventListener.java"),
    ("subscriber-table", f"{BASE}/infrastructure/InterruptRequestEventListener.java"),
    ("subscriber-table", f"{BASE}/adapter/SaveEventToEventRepositoryListener.java"),
    ("subscriber-table", f"{BASE}/adapter/SaveGraphEventToGraphRepositoryListener.java"),
    ("subscriber-table", f"{BASE}/artifacts/ArtifactEventListener.java"),

    # ── AgentRunner ──────────────────────────────────────────────────
    ("agent-runner", f"{BASE}/infrastructure/AgentRunner.java"),

    # ── SSE vs WebSocket ─────────────────────────────────────────────
    ("sse-vs-websocket", f"{BASE}/adapter/SseEventAdapter.java"),
    ("sse-vs-websocket", f"{BASE}/adapter/WebSocketEventAdapter.java"),

    # ── Skill References ──────────────────────────────────────────────
    ("default-event-bus",  f"{SKILLS}/multi_agent_ide_debug/executables/error_search.py"),
    ("default-event-bus",  f"{SKILLS}/multi_agent_ide_debug/executables/error_patterns.csv"),
    ("subscriber-table",   f"{SKILLS}/multi_agent_ide_debug/references/common-exceptions.md"),
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
