#!/usr/bin/env python3
from __future__ import annotations

import argparse
import importlib
import importlib.util
import json
import os
import subprocess
import sys
from dataclasses import asdict, dataclass, field
from pathlib import Path
from typing import Any, Protocol, runtime_checkable


@dataclass
class CommitRecord:
    sha: str
    parents: list[str]
    author_name: str
    author_email: str
    author_ts: int
    subject: str
    body: str
    files: list[str] = field(default_factory=list)
    insertions: int = 0
    deletions: int = 0
    is_merge: bool = False
    tags: list[str] = field(default_factory=list)


@dataclass
class CommitStream:
    repo: str
    ref: str
    commits: list[CommitRecord]


@dataclass
class EngineRequest:
    repo: str
    ref: str
    cursor: int
    remaining_budget: int | None
    commits_remaining: int
    commits: list[CommitRecord]
    prior_segments: list[dict[str, Any]]
    engine_config: dict[str, Any]
    last_encoded_commit: str | None


@dataclass
class NextCommitProposal:
    engine: str
    start_index: int
    end_index: int
    start_sha: str
    end_sha: str
    commit_count: int
    title: str
    rationale: str
    metrics: dict[str, Any]
    input_last_encoded_commit: str | None = None
    last_encoded_commit: str | None = None


@runtime_checkable
class NextCommitEngine(Protocol):
    def produce_next_commit(self, request: EngineRequest) -> NextCommitProposal:
        ...


def run_git(repo: Path, *args: str) -> str:
    proc = subprocess.run(
        ["git", *args],
        cwd=str(repo),
        text=True,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    if proc.returncode != 0:
        raise RuntimeError(
            f"git command failed: git {' '.join(args)}\n"
            f"repo={repo}\nstdout={proc.stdout}\nstderr={proc.stderr}"
        )
    return proc.stdout


def load_engine(engine_spec: str) -> NextCommitEngine:
    """
    Supported forms:
      - module.path:ClassName
      - /abs/or/rel/path/to/file.py:ClassName
    """
    if ":" not in engine_spec:
        raise ValueError(
            "--engine must be in 'module.path:ClassName' or '/path/file.py:ClassName' form"
        )

    target, class_name = engine_spec.split(":", 1)

    if target.endswith(".py") or os.path.sep in target:
        path = Path(target).resolve()
        if not path.exists():
            raise FileNotFoundError(f"Engine file not found: {path}")
        module_name = f"dynamic_engine_{path.stem}"
        spec = importlib.util.spec_from_file_location(module_name, path)
        if spec is None or spec.loader is None:
            raise RuntimeError(f"Could not load engine module from {path}")
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
    else:
        module = importlib.import_module(target)

    cls = getattr(module, class_name, None)
    if cls is None:
        raise AttributeError(f"Class '{class_name}' not found in engine target '{target}'")

    instance = cls()
    if not isinstance(instance, NextCommitEngine):
        raise TypeError(
            f"Loaded engine {engine_spec} does not implement produce_next_commit(request)"
        )
    return instance


def list_linear_commits(repo: Path, ref: str, first_parent: bool) -> list[str]:
    args = ["rev-list", "--reverse"]
    if first_parent:
        args.append("--first-parent")
    args.append(ref)
    out = run_git(repo, *args)
    return [line.strip() for line in out.splitlines() if line.strip()]


def tags_pointing_at(repo: Path, sha: str) -> list[str]:
    out = run_git(repo, "tag", "--points-at", sha)
    return [line.strip() for line in out.splitlines() if line.strip()]


def commit_record(repo: Path, sha: str) -> CommitRecord:
    fmt = "%H%x1f%P%x1f%an%x1f%ae%x1f%at%x1f%s%x1f%b"
    meta = run_git(repo, "show", "-s", f"--format={fmt}", sha).rstrip("\n")
    parts = meta.split("\x1f")
    if len(parts) != 7:
        raise RuntimeError(f"Unexpected git show metadata shape for {sha}: {parts}")

    _, parents_str, author_name, author_email, author_ts, subject, body = parts
    parents = [p for p in parents_str.split() if p]

    stat = run_git(repo, "show", "--numstat", "--format=", sha)
    files: list[str] = []
    insertions = 0
    deletions = 0
    for line in stat.splitlines():
        cols = line.split("\t")
        if len(cols) < 3:
            continue
        ins_raw, del_raw, path = cols[0], cols[1], cols[2]
        files.append(path)
        if ins_raw.isdigit():
            insertions += int(ins_raw)
        if del_raw.isdigit():
            deletions += int(del_raw)

    return CommitRecord(
        sha=sha,
        parents=parents,
        author_name=author_name,
        author_email=author_email,
        author_ts=int(author_ts),
        subject=subject,
        body=body.strip(),
        files=files,
        insertions=insertions,
        deletions=deletions,
        is_merge=len(parents) > 1,
        tags=tags_pointing_at(repo, sha),
    )


def build_commit_stream(repo: Path, ref: str, first_parent: bool) -> CommitStream:
    shas = list_linear_commits(repo, ref, first_parent=first_parent)
    commits = [commit_record(repo, sha) for sha in shas]
    return CommitStream(repo=str(repo), ref=ref, commits=commits)


def load_prior_segments(path: Path | None) -> list[dict[str, Any]]:
    if path is None:
        return []
    payload = json.loads(path.read_text(encoding="utf-8"))
    if isinstance(payload, dict) and "segments" in payload:
        payload = payload["segments"]
    if not isinstance(payload, list):
        raise ValueError("Prior segments JSON must be a list or an object with a 'segments' list")
    return payload


def load_engine_config(config_path: Path | None) -> dict[str, Any]:
    if config_path is None:
        return {}
    data = json.loads(config_path.read_text(encoding="utf-8"))
    if not isinstance(data, dict):
        raise ValueError("Engine config must be a JSON object")
    return data


def index_of_commit(commits: list[CommitRecord], sha: str) -> int:
    for idx, commit in enumerate(commits):
        if commit.sha == sha:
            return idx
    raise ValueError(f"Commit not found in selected history: {sha}")


def derive_cursor(
    commits: list[CommitRecord],
    prior_segments: list[dict[str, Any]],
    explicit_cursor: int | None,
    last_encoded_commit: str | None,
) -> int:
    if explicit_cursor is not None:
        return explicit_cursor
    if last_encoded_commit is not None:
        return index_of_commit(commits, last_encoded_commit) + 1
    if not prior_segments:
        return 0
    last = prior_segments[-1]
    if "last_encoded_commit" in last and last["last_encoded_commit"]:
        return index_of_commit(commits, str(last["last_encoded_commit"])) + 1
    if "end_index" not in last:
        raise ValueError(
            "Prior segment entries must include end_index or last_encoded_commit to derive cursor automatically"
        )
    return int(last["end_index"]) + 1


def validate_last_encoded_commit(
    commits: list[CommitRecord],
    last_encoded_commit: str | None,
    cursor: int,
) -> None:
    if last_encoded_commit is None:
        return
    idx = index_of_commit(commits, last_encoded_commit)
    if cursor != idx + 1:
        raise ValueError(
            "Inconsistent cursor/last_encoded_commit combination: "
            f"cursor={cursor}, expected={idx + 1} for last_encoded_commit={last_encoded_commit}"
        )


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Produce the next synthetic commit proposal using a swappable engine."
    )
    parser.add_argument("--repo", required=True, help="Path to the truncated repo")
    parser.add_argument("--ref", default="HEAD", help="Git ref to linearize, default HEAD")
    parser.add_argument(
        "--engine",
        default="/mnt/data/simplified_engine.py:SimplifiedEngine",
        help="Engine spec: module.path:ClassName or /path/to/file.py:ClassName",
    )
    parser.add_argument(
        "--prior-segments",
        type=Path,
        default=None,
        help="Optional JSON file containing prior produced segments",
    )
    parser.add_argument(
        "--cursor",
        type=int,
        default=None,
        help="Optional explicit starting cursor; otherwise derived from last-encoded-commit or prior segments",
    )
    parser.add_argument(
        "--last-encoded-commit",
        default=None,
        help=(
            "True source commit SHA already encoded in episodic memory. "
            "Partitioning will begin immediately after this commit."
        ),
    )
    parser.add_argument(
        "--remaining-budget",
        type=int,
        default=None,
        help="Optional remaining number of synthetic commit-groups to budget for",
    )
    parser.add_argument(
        "--engine-config",
        type=Path,
        default=None,
        help="Optional JSON object passed through to the engine",
    )
    parser.add_argument(
        "--first-parent",
        action="store_true",
        help="Linearize using first-parent history only",
    )
    args = parser.parse_args()

    repo = Path(args.repo).resolve()
    if not repo.exists():
        raise FileNotFoundError(f"Repo not found: {repo}")

    stream = build_commit_stream(repo, args.ref, first_parent=args.first_parent)
    prior_segments = load_prior_segments(args.prior_segments)
    engine_config = load_engine_config(args.engine_config)
    engine = load_engine(args.engine)

    cursor = derive_cursor(
        commits=stream.commits,
        prior_segments=prior_segments,
        explicit_cursor=args.cursor,
        last_encoded_commit=args.last_encoded_commit,
    )
    validate_last_encoded_commit(stream.commits, args.last_encoded_commit, cursor)

    if cursor < 0 or cursor > len(stream.commits):
        raise ValueError(f"cursor out of range: {cursor}, commit count={len(stream.commits)}")

    if cursor == len(stream.commits):
        payload = {
            "done": True,
            "repo": str(repo),
            "ref": args.ref,
            "cursor": cursor,
            "last_encoded_commit": args.last_encoded_commit,
            "message": "No commits remain after last-encoded-commit.",
        }
        print(json.dumps(payload, indent=2))
        return 0

    request = EngineRequest(
        repo=str(repo),
        ref=args.ref,
        cursor=cursor,
        remaining_budget=args.remaining_budget,
        commits_remaining=len(stream.commits) - cursor,
        commits=stream.commits,
        prior_segments=prior_segments,
        engine_config=engine_config,
        last_encoded_commit=args.last_encoded_commit,
    )
    proposal = engine.produce_next_commit(request)

    if proposal.last_encoded_commit is None:
        proposal.last_encoded_commit = proposal.end_sha
    if proposal.input_last_encoded_commit is None:
        proposal.input_last_encoded_commit = args.last_encoded_commit

    payload = {
        "done": False,
        "repo": str(repo),
        "ref": args.ref,
        "total_commits": len(stream.commits),
        "cursor": cursor,
        "proposal": asdict(proposal),
    }
    print(json.dumps(payload, indent=2))
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main())
    except Exception as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        sys.exit(1)
