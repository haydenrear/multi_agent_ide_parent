#!/usr/bin/env python3
"""Render all views from their mental-model-content.json to markdown + JSON.

This is a safe, idempotent operation — it reads each view's existing
.metadata/mental-model-content.json and renders it to mental-models.md
and mental-models.json. It does NOT modify the content JSON, create
metadata entries, or run regen.py.

For initial onboarding of a new view, use the individual CLI commands:
  1. view-model init <path>
  2. view-model update <path> --path ... --value ...
  3. view-model add-ref <path> --path ... --file ...
  4. view-model render <path> --write

Usage:
    cd multi_agent_ide_parent
    uv run --project multi_agent_ide_python_parent python views/render_all.py

    # Specific views only:
    uv run --project multi_agent_ide_python_parent python views/render_all.py --views orchestration-model prompts
"""
from __future__ import annotations

from pathlib import Path

from click.testing import CliRunner

from view_agents_utils.cli import view_model

VIEWS_DIR = Path(__file__).resolve().parent
REPO_ROOT = VIEWS_DIR.parent

VIEW_NAMES = [
    "acp-module",
    "agent-execution",
    "agent-framework",
    "api-layer",
    "artifact-key",
    "event-messaging",
    "filter-propagation",
    "graph-nodes",
    "mcp-server-client",
    "orchestration-model",
    "persistence",
    "prompts",
    "testing",
    "ui-tui",
    "utility-module",
]

runner = CliRunner()


def render_view(view_name: str, repo_root: Path) -> bool:
    """Render a single view's mental-models.md from its content JSON."""
    view_dir = VIEWS_DIR / view_name
    mm_dir = view_dir / "mental-models"
    content_json = mm_dir / ".metadata" / "mental-model-content.json"

    if not content_json.exists():
        print(f"  SKIP: no .metadata/mental-model-content.json")
        return False

    result = runner.invoke(
        view_model,
        [
            "render", str(view_dir),
            "--repo", str(repo_root),
            "--format", "markdown",
            "--write",
            "--include-refs",
        ],
        catch_exceptions=False,
    )

    if result.exit_code != 0:
        print(f"  ERROR: render exited {result.exit_code}")
        if result.output.strip():
            print(f"    {result.output.strip()[:300]}")
        return False

    print(f"  rendered: {view_dir}")
    return True


def render_root(repo_root: Path) -> bool:
    """Render the root mental model."""
    mm_dir = VIEWS_DIR / "mental-models"
    content_json = mm_dir / ".metadata" / "mental-model-content.json"

    if not content_json.exists():
        print(f"  SKIP: no root .metadata/mental-model-content.json")
        return False

    result = runner.invoke(
        view_model,
        [
            "render", str(mm_dir),
            "--repo", str(repo_root),
            "--format", "markdown",
            "--write",
            "--include-refs",
        ],
        catch_exceptions=False,
    )

    if result.exit_code != 0:
        print(f"  ERROR: render exited {result.exit_code}")
        if result.output.strip():
            print(f"    {result.output.strip()[:300]}")
        return False

    print(f"  rendered: {mm_dir}")
    return True


def main():
    import argparse

    parser = argparse.ArgumentParser(
        description="Render all views from content JSON to markdown + JSON"
    )
    parser.add_argument(
        "--repo",
        type=Path,
        default=REPO_ROOT,
        help="Repository root (default: parent of views/)",
    )
    parser.add_argument(
        "--views",
        nargs="*",
        default=None,
        help="Specific view names to render (default: all)",
    )
    args = parser.parse_args()
    repo_root = args.repo.resolve()

    views = args.views or VIEW_NAMES

    print(f"Rendering {len(views)} views")
    print(f"Repo root: {repo_root}")

    ok_count = 0
    fail_count = 0
    skip_count = 0

    for view_name in views:
        print(f"\n--- {view_name} ---")
        try:
            if render_view(view_name, repo_root):
                ok_count += 1
            else:
                skip_count += 1
        except Exception as e:
            print(f"  ERROR: {e}")
            fail_count += 1

    # Always render root (it aggregates all views)
    print(f"\n--- root ---")
    try:
        if render_root(repo_root):
            ok_count += 1
        else:
            skip_count += 1
    except Exception as e:
        print(f"  ERROR: {e}")
        fail_count += 1

    print(f"\nDone: {ok_count} rendered, {skip_count} skipped, {fail_count} failed")


if __name__ == "__main__":
    main()
