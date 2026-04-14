#!/usr/bin/env python3
"""Regenerate the persistence view.

Two-tier architecture: in-memory ConcurrentHashMap repositories + JPA/PostgreSQL
entities. Artifact persistence flow, ArtifactKey hierarchy, semantic
representations, and direct persistence bypass.

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
    # ── Two-Tier Architecture ────────────────────────────────────────
    ("two-tier-architecture", f"{BASE}/repository/GraphRepository.java"),
    ("two-tier-architecture", f"{BASE}/repository/InMemoryGraphRepository.java"),
    ("two-tier-architecture", f"{BASE}/repository/EventStreamRepository.java"),
    ("two-tier-architecture", f"{BASE}/repository/InMemoryEventStreamRepository.java"),
    ("two-tier-architecture", f"{BASE}/repository/WorktreeRepository.java"),
    ("two-tier-architecture", f"{BASE}/repository/InMemoryWorktreeRepository.java"),
    ("two-tier-architecture", f"{BASE}/repository/InMemoryRequestContextRepository.java"),
    ("two-tier-architecture", f"{BASE}/repository/RunPersistenceCheckRepository.java"),
    ("two-tier-architecture", f"{BASE}/repository/InMemoryRunPersistenceCheckRepository.java"),

    # ── Artifact Persistence Flow ────────────────────────────────────
    ("artifact-persistence-flow", f"{BASE}/artifacts/ArtifactEmissionService.java"),
    ("artifact-persistence-flow", f"{BASE}/artifacts/ArtifactEventListener.java"),
    ("artifact-persistence-flow", f"{BASE}/artifacts/ArtifactService.java"),
    ("artifact-persistence-flow", f"{BASE}/artifacts/ArtifactTreeBuilder.java"),
    ("artifact-persistence-flow", f"{BASE}/artifacts/EventArtifactMapper.java"),
    ("artifact-persistence-flow", f"{BASE}/artifacts/ExecutionScopeService.java"),
    ("artifact-persistence-flow", f"{BASE}/artifacts/ArtifactNode.java"),

    # ── ArtifactKey ──────────────────────────────────────────────────
    ("artifact-key", f"{BASE}/artifacts/entity/ArtifactEntity.java"),
    ("artifact-key", f"{BASE}/artifacts/repository/ArtifactRepository.java"),

    # ── SemanticRepresentation ───────────────────────────────────────
    ("semantic-representation", f"{BASE}/artifacts/semantic/SemanticRepresentationEntity.java"),
    ("semantic-representation", f"{BASE}/artifacts/semantic/SemanticRepresentationRepository.java"),
    ("semantic-representation", f"{BASE}/artifacts/semantic/SemanticRepresentationService.java"),
    ("semantic-representation", f"{BASE}/artifact/SemanticRepresentation.java"),

    # ── Direct Persistence Bypass ────────────────────────────────────
    ("direct-persistence-bypass", f"{BASE}/artifacts/PolicyLifecycleArtifactService.java"),
    ("direct-persistence-bypass", f"{BASE}/artifact/PromptTemplateVersion.java"),

    # ── Skill References ──────────────────────────────────────────────
    ("two-tier-architecture", f"{SKILLS}/multi_agent_ide_debug/SKILL.md"),
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
