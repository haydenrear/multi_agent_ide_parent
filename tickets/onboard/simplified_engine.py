from __future__ import annotations

from collections import Counter
from dataclasses import dataclass
from typing import Any


@dataclass
class _WindowStats:
    changed_files: int
    top_level_counter: Counter
    total_insertions: int
    total_deletions: int
    merges: int
    author_changes: int
    max_time_gap_seconds: int


@dataclass
class Proposal:
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


class SimplifiedEngine:
    """
    A deterministic baseline engine.

    Policy:
    - work over a linear commit stream from oldest -> newest
    - produce one contiguous range starting at request.cursor
    - prefer grouping nearby commits while respecting remaining budget
    - split more aggressively at strong boundaries:
        * tag boundary
        * merge commit
        * large time gap
        * author change + low path overlap
        * root/top-level path shift

    This engine intentionally ignores repository shape beyond commit metadata.
    It is meant to be cheap, explainable, and easy to swap out.
    """

    def produce_next_commit(self, request):
        commits = request.commits
        i = request.cursor
        cfg = request.engine_config or {}

        max_group_size = int(cfg.get("max_group_size", 12))
        min_group_size = int(cfg.get("min_group_size", 1))
        large_gap_seconds = int(cfg.get("large_gap_seconds", 60 * 60 * 24 * 7))
        target_groups = request.remaining_budget

        if target_groups is not None and target_groups <= 0:
            raise ValueError("remaining_budget must be positive when provided")

        if target_groups is not None:
            ideal = max(1, -(-request.commits_remaining // target_groups))
            max_group_size = min(max_group_size, ideal)
            min_group_size = min(min_group_size, max_group_size)

        end = i
        previous = commits[i]
        previous_roots = self._roots(previous.files)
        rationale_bits: list[str] = []

        for j in range(i + 1, min(len(commits), i + max_group_size)):
            current = commits[j]
            current_roots = self._roots(current.files)
            time_gap = max(0, current.author_ts - previous.author_ts)
            path_overlap = len(previous_roots & current_roots)
            hard_boundary = False
            soft_boundary = False
            reasons: list[str] = []

            if previous.tags or current.tags:
                hard_boundary = True
                reasons.append("tag boundary")
            if current.is_merge:
                hard_boundary = True
                reasons.append("merge commit")
            if time_gap >= large_gap_seconds:
                soft_boundary = True
                reasons.append("large time gap")
            if previous.author_email != current.author_email and path_overlap == 0:
                soft_boundary = True
                reasons.append("author change with path shift")
            if path_overlap == 0 and j > i + min_group_size - 1:
                soft_boundary = True
                reasons.append("top-level path shift")

            if hard_boundary and (end - i + 1) >= min_group_size:
                rationale_bits.extend(reasons)
                break

            if soft_boundary and (end - i + 1) >= min_group_size:
                rationale_bits.extend(reasons)
                break

            end = j
            previous = current
            previous_roots = current_roots

        selected = commits[i : end + 1]
        stats = self._stats(selected)

        title = self._title(selected, stats)
        rationale = self._rationale(selected, stats, rationale_bits)

        return self._proposal(
            request=request,
            start_index=i,
            end_index=end,
            title=title,
            rationale=rationale,
            stats=stats,
        )

    def _proposal(self, request, start_index: int, end_index: int, title: str, rationale: str, stats: _WindowStats):
        commits = request.commits
        start = commits[start_index]
        end = commits[end_index]
        return Proposal(
            engine=f"{self.__class__.__module__}.{self.__class__.__name__}",
            start_index=start_index,
            end_index=end_index,
            start_sha=start.sha,
            end_sha=end.sha,
            commit_count=end_index - start_index + 1,
            title=title,
            rationale=rationale,
            metrics={
                "changed_files": stats.changed_files,
                "top_level_paths": dict(stats.top_level_counter),
                "total_insertions": stats.total_insertions,
                "total_deletions": stats.total_deletions,
                "merge_count": stats.merges,
                "author_changes": stats.author_changes,
                "max_time_gap_seconds": stats.max_time_gap_seconds,
            },
            input_last_encoded_commit=request.last_encoded_commit,
            last_encoded_commit=end.sha,
        )

    def _stats(self, commits) -> _WindowStats:
        files = set()
        roots = Counter()
        total_insertions = 0
        total_deletions = 0
        merges = 0
        author_changes = 0
        max_gap = 0

        prev_author = None
        prev_ts = None

        for c in commits:
            files.update(c.files)
            roots.update(self._roots(c.files))
            total_insertions += c.insertions
            total_deletions += c.deletions
            merges += 1 if c.is_merge else 0
            if prev_author is not None and c.author_email != prev_author:
                author_changes += 1
            if prev_ts is not None:
                max_gap = max(max_gap, max(0, c.author_ts - prev_ts))
            prev_author = c.author_email
            prev_ts = c.author_ts

        return _WindowStats(
            changed_files=len(files),
            top_level_counter=roots,
            total_insertions=total_insertions,
            total_deletions=total_deletions,
            merges=merges,
            author_changes=author_changes,
            max_time_gap_seconds=max_gap,
        )

    def _roots(self, files: list[str]) -> set[str]:
        roots = set()
        for path in files:
            if not path:
                continue
            root = path.split("/", 1)[0]
            roots.add(root)
        return roots

    def _title(self, commits, stats: _WindowStats) -> str:
        if len(commits) == 1:
            return commits[0].subject[:120]

        roots = [root for root, _ in stats.top_level_counter.most_common(2)]
        root_part = ", ".join(roots) if roots else "misc"
        return f"Episode touching {root_part} ({len(commits)} commits)"

    def _rationale(self, commits, stats: _WindowStats, rationale_bits: list[str]) -> str:
        reasons = []
        if rationale_bits:
            reasons.append("boundary due to " + ", ".join(dict.fromkeys(rationale_bits)))
        reasons.append(f"grouped {len(commits)} contiguous commits")
        reasons.append(f"{stats.changed_files} unique files changed")
        if stats.top_level_counter:
            dominant = ", ".join(
                f"{root}:{count}" for root, count in stats.top_level_counter.most_common(3)
            )
            reasons.append(f"dominant top-level paths {dominant}")
        if stats.author_changes:
            reasons.append(f"{stats.author_changes} author transitions inside window")
        if stats.merges:
            reasons.append(f"contains {stats.merges} merge commits")
        return "; ".join(reasons)
