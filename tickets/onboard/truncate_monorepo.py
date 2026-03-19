"""
truncate_monorepo.py

Clone a Git repository with nested submodules, recursively truncate each repo
to history relevant to its current HEAD paths, then materialize submodules into
the parent tree to produce a truncated monorepo.

Requirements:
  - git
  - git-filter-repo installed and on PATH
  - Python 3.10+

Usage:
  python truncate_monorepo.py \
      --source /path/to/repo-or-url \
      --output /tmp/truncated-monorepo \
      --branch main

Notes:
  - This preserves the final HEAD path layout of each repo/submodule.
  - It does NOT preserve original commit hashes.
  - It uses a path-survivorship approximation:
      keep current HEAD paths + historical names discovered with --follow.
  - It intentionally does not yet do episode squashing.
"""

from __future__ import annotations

import argparse
import json
import os
import shutil
import subprocess
import sys
import tempfile
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Iterable, List, Optional


# -----------------------------
# subprocess helpers
# -----------------------------

def run(
        cmd: list[str],
        cwd: Optional[Path] = None,
        check: bool = True,
        capture: bool = True,
        env: Optional[dict[str, str]] = None,
) -> subprocess.CompletedProcess:
    result = subprocess.run(
        cmd,
        cwd=str(cwd) if cwd else None,
        check=False,
        text=True,
        stdout=subprocess.PIPE if capture else None,
        stderr=subprocess.PIPE if capture else None,
        env=env,
    )
    if check and result.returncode != 0:
        raise RuntimeError(
            f"Command failed ({result.returncode}): {' '.join(cmd)}\n"
            f"CWD: {cwd}\n"
            f"STDOUT:\n{result.stdout or ''}\n"
            f"STDERR:\n{result.stderr or ''}"
        )
    return result


def git(repo: Path, *args: str, check: bool = True, capture: bool = True) -> subprocess.CompletedProcess:
    return run(["git", *args], cwd=repo, check=check, capture=capture)


# -----------------------------
# data model
# -----------------------------

@dataclass
class RepoResult:
    repo_path: str
    repo_name: str
    original_head: str
    rewritten_head: str
    current_paths_count: int
    kept_paths_count: int
    submodules: list["RepoResult"]


# -----------------------------
# discovery helpers
# -----------------------------

def ensure_tools() -> None:
    for tool in ["git", "git-filter-repo"]:
        if shutil.which(tool) is None:
            raise RuntimeError(f"Required tool not found on PATH: {tool}")


def clone_repo(source: str, target: Path, branch: Optional[str]) -> None:
    cmd = ["git", "clone", "--recurse-submodules"]
    if branch:
        cmd += ["--branch", branch]
    cmd += [source, str(target)]
    run(cmd, capture=True)


def update_submodules_recursive(repo: Path) -> None:
    git(repo, "submodule", "update", "--init", "--recursive")


def current_head(repo: Path) -> str:
    return git(repo, "rev-parse", "HEAD").stdout.strip()


def repo_name(repo: Path) -> str:
    return repo.name


def list_current_paths(repo: Path) -> list[str]:
    out = git(repo, "ls-tree", "-r", "--name-only", "HEAD").stdout
    return [line for line in out.splitlines() if line.strip()]


def list_submodule_paths(repo: Path) -> list[str]:
    """
    Returns submodule paths from .gitmodules if present.
    """
    gitmodules = repo / ".gitmodules"
    if not gitmodules.exists():
        return []

    # git config -f .gitmodules --get-regexp path
    proc = git(repo, "config", "-f", ".gitmodules", "--get-regexp", r"^submodule\..*\.path$")
    paths: list[str] = []
    for line in proc.stdout.splitlines():
        parts = line.strip().split(maxsplit=1)
        if len(parts) == 2:
            paths.append(parts[1])
    return paths


def build_keep_paths(repo: Path, include_rename_history: bool = True) -> list[str]:
    """
    Keep current HEAD paths and optionally historical names discovered via
    git log --follow --name-only -- <path>.
    """
    current_paths = list_current_paths(repo)
    keep: set[str] = set(current_paths)

    if include_rename_history:
        for p in current_paths:
            # --follow works on single paths, mainly files; that's fine for v1.
            proc = git(
                repo,
                "log",
                "--follow",
                "--format=",
                "--name-only",
                "--",
                p,
                check=False,
            )
            if proc.returncode == 0 and proc.stdout:
                for line in proc.stdout.splitlines():
                    line = line.strip()
                    if line:
                        keep.add(line)

    return sorted(keep)


def write_paths_manifest(paths: Iterable[str], manifest_path: Path) -> None:
    manifest_path.write_text("".join(f"{p}\n" for p in paths), encoding="utf-8")


def verify_head_paths_equal(repo_before_paths: list[str], repo_after: Path) -> None:
    after_paths = sorted(list_current_paths(repo_after))
    before_sorted = sorted(repo_before_paths)
    if before_sorted != after_paths:
        missing = sorted(set(before_sorted) - set(after_paths))
        extra = sorted(set(after_paths) - set(before_sorted))
        raise RuntimeError(
            "HEAD path verification failed after filter-repo.\n"
            f"Missing paths: {missing[:50]}\n"
            f"Extra paths: {extra[:50]}\n"
            f"Missing count={len(missing)}, Extra count={len(extra)}"
        )


# -----------------------------
# truncation
# -----------------------------

def truncate_repo_in_place(repo: Path, include_rename_history: bool = True) -> RepoResult:
    before_head = current_head(repo)
    before_paths = list_current_paths(repo)
    keep_paths = build_keep_paths(repo, include_rename_history=include_rename_history)

    # Analyze is optional but useful for future debugging.
    git(repo, "filter-repo", "--analyze", check=False)

    with tempfile.NamedTemporaryFile("w", delete=False, encoding="utf-8") as tf:
        manifest_file = Path(tf.name)
        write_paths_manifest(keep_paths, manifest_file)

    try:
        # Important: no --path-rename, no --to-subdirectory-filter, etc.
        # That preserves selected path names as-is.
        git(
            repo,
            "filter-repo",
            "--paths-from-file",
            str(manifest_file),
            "--force",
        )
    finally:
        manifest_file.unlink(missing_ok=True)

    verify_head_paths_equal(before_paths, repo)
    after_head = current_head(repo)

    return RepoResult(
        repo_path=str(repo),
        repo_name=repo_name(repo),
        original_head=before_head,
        rewritten_head=after_head,
        current_paths_count=len(before_paths),
        kept_paths_count=len(keep_paths),
        submodules=[],
    )


# -----------------------------
# recursive processing
# -----------------------------

def process_repo_recursive(repo: Path, include_rename_history: bool = True) -> RepoResult:
    """
    Deepest-first:
      1) recurse into child submodules
      2) truncate each child
      3) materialize child trees into parent
      4) remove submodule metadata/glinks
      5) truncate parent
    """
    update_submodules_recursive(repo)

    child_results: list[RepoResult] = []
    submodule_paths = list_submodule_paths(repo)

    # Recurse into children first
    for sm_rel in submodule_paths:
        sm_path = repo / sm_rel
        if not sm_path.exists():
            continue
        child_result = process_repo_recursive(sm_path, include_rename_history=include_rename_history)
        child_results.append(child_result)

    # Convert submodules into normal directories in the parent tree.
    # At this point each child repo has already been truncated in place.
    if submodule_paths:
        materialize_submodules_into_parent(repo, submodule_paths)

    # Now truncate the parent repo after submodules are plain directories.
    this_result = truncate_repo_in_place(repo, include_rename_history=include_rename_history)
    this_result.submodules = child_results
    return this_result


def materialize_submodules_into_parent(repo: Path, submodule_paths: list[str]) -> None:
    """
    Replace gitlinks with normal directories containing the child's checked-out files.

    Strategy:
      - deinit each submodule from the parent metadata
      - remove gitlink from index
      - strip nested .git file/dir from child working tree
      - add the directory contents back as normal tracked files
      - remove .gitmodules if empty afterward
      - commit one "materialize submodules" commit if there are changes

    This is intentionally simple and optimized for producing a usable truncated
    monorepo path tree, not preserving the exact parent/submodule relationship.
    """
    for sm_rel in submodule_paths:
        sm = repo / sm_rel
        if not sm.exists():
            continue

        # Deinit from parent metadata. Ignore failures to stay robust.
        git(repo, "submodule", "deinit", "-f", "--", sm_rel, check=False)

        # Remove gitlink from index only; keep working tree files.
        git(repo, "rm", "--cached", "-r", "--ignore-unmatch", sm_rel, check=False)

        # Remove nested .git metadata so the directory becomes plain files.
        nested_git = sm / ".git"
        if nested_git.exists():
            if nested_git.is_dir():
                shutil.rmtree(nested_git)
            else:
                nested_git.unlink()

        # Stage as normal directory content.
        git(repo, "add", "--", sm_rel)

    # If .gitmodules exists, remove entries for materialized submodules.
    gitmodules = repo / ".gitmodules"
    if gitmodules.exists():
        # Simplest first-pass behavior: remove .gitmodules entirely once all submodules are materialized.
        git(repo, "rm", "-f", "--ignore-unmatch", ".gitmodules", check=False)

    # Remove leftover .git/modules metadata if present.
    modules_dir = repo / ".git" / "modules"
    if modules_dir.exists():
        shutil.rmtree(modules_dir, ignore_errors=True)

    # Commit only if there are changes.
    status = git(repo, "status", "--porcelain").stdout.strip()
    if status:
        git(repo, "commit", "-m", "Materialize truncated submodules into monorepo tree", check=False)


# -----------------------------
# top-level orchestration
# -----------------------------

def main() -> int:
    parser = argparse.ArgumentParser(description="Truncate a repo with nested submodules into a truncated monorepo.")
    parser.add_argument("--source", required=True, help="Path or URL to the source Git repo")
    parser.add_argument("--output", required=True, help="Directory for the final truncated monorepo")
    parser.add_argument("--branch", default=None, help="Optional branch to clone")
    parser.add_argument(
        "--no-rename-history",
        action="store_true",
        help="Only keep current HEAD paths; do not include historical names via git log --follow",
    )
    parser.add_argument(
        "--keep-workdir",
        action="store_true",
        help="Keep the temp working directory instead of replacing it with --output copy",
    )
    args = parser.parse_args()

    ensure_tools()

    output = Path(args.output).resolve()
    if output.exists():
        raise RuntimeError(f"Output path already exists: {output}")

    temp_root = Path(tempfile.mkdtemp(prefix="truncate-monorepo-"))
    work_repo = temp_root / "repo"

    try:
        clone_repo(args.source, work_repo, args.branch)
        update_submodules_recursive(work_repo)

        result = process_repo_recursive(
            work_repo,
            include_rename_history=not args.no_rename_history,
        )

        manifest = {
            "source": args.source,
            "branch": args.branch,
            "output_repo": str(output),
            "result": asdict(result),
        }

        if args.keep_workdir:
            shutil.copytree(work_repo, output)
        else:
            shutil.move(str(work_repo), str(output))

        manifest_path = output / "truncation_manifest.json"
        manifest_path.write_text(json.dumps(manifest, indent=2), encoding="utf-8")

        print(str(output))
        print(f"Manifest: {manifest_path}")
        return 0

    finally:
        # If we moved the repo out, temp_root may already be mostly empty.
        if temp_root.exists():
            shutil.rmtree(temp_root, ignore_errors=True)


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(1)
