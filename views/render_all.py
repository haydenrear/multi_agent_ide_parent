#!/usr/bin/env python3
"""Render all views — convenience wrapper around `view-model render --all`.

Prefer using the CLI directly:
    uv run --project multi_agent_ide_python_parent view-model render --all --repo .

    # Specific views only:
    uv run --project multi_agent_ide_python_parent view-model render --all --views orchestration-model prompts --repo .

This script exists for backwards compatibility and quick invocation from the
repo root. All logic lives in `view_agents_utils.helper.render._render_all_views`.
"""
from __future__ import annotations

import argparse
import subprocess
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Render all views from content JSON to markdown + JSON"
    )
    parser.add_argument(
        "--repo",
        default=str(REPO_ROOT),
        help="Repository root (default: parent of views/)",
    )
    parser.add_argument(
        "--views",
        nargs="*",
        default=None,
        help="Specific view names to render (default: all)",
    )
    args = parser.parse_args()

    cmd = [
        "uv", "run", "--project", "multi_agent_ide_python_parent",
        "view-model", "render", "--all", "--repo", args.repo,
    ]
    if args.views:
        for v in args.views:
            cmd.extend(["--views", v])

    sys.exit(subprocess.call(cmd))


if __name__ == "__main__":
    main()
