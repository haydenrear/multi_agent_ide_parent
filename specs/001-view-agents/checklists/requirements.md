# Specification Quality Checklist: View Agents

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-04-09
**Updated**: 2026-04-10 (v11 — relative symlinks, 1:1 view:model, section path identity, SourceFileRef/ChildModelRef split, content hash)
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Notes

- The spec intentionally mentions Python/Ollama/Docker in the CLI stories because the user explicitly requested a Python CLI using Ollama in Docker containers — these are user requirements, not leaked implementation details.
- Gherkin feature files were skipped per user request.
- The spec covers four key concerns: (1) view generation, (2) mental models with chain-of-custody traceability, (3) Docker-scoped sub-agent CLI with two-phase discovery integration, and (4) custody history/search.
- Story 2 (Mental Models) was promoted from P2 to P1 because chain of custody is essential for MVP — stale mental models are worse than none.
- Chain of custody tracks only the **curated subset** of files the LLM selected as important — not every file in the view. This is the emergent curation mechanism: by forcing the LLM to choose, the system discovers what's architecturally significant.
- Chain of custody uses **immutable metadata files** in a linked-list structure (not an append-only log). Each metadata file contains diff entries keyed by section path, each pointing back to the previous metadata file it supersedes. Input files are eagerly copied to snapshot directories alongside each metadata file. Walking backward = following pointers. Flat structure like a hash map.
- The mechanical operations (create metadata file, copy snapshots, link diff entries) are reusable across view-level (input = source files) and root-level (input = child view mental-model sections) — same structure, different inputs.
- Story 5 (Custody History & Search) added as P2 — search tool traverses the metadata chain. Visualization (static HTML/SVG) deferred to a future iteration.
- Former Story 6 (View Update on Repository Change) removed entirely — staleness detection and mental model refresh are now the first step of every view-agent invocation (Story 3, FR-015a). No separate update process exists.
- Lightweight staleness resolution (updating line references/diffs without full regeneration) is specified as a SHOULD, not a MUST — it's an optimization for small diffs.
- **Helper scripts** (`view-model show/files/update/add-ref/status`) abstract all metadata mechanics so the LLM and all other consumers never interact with metadata files directly. These are specified in FR-009-show through FR-009-abstract and are the same reusable tool referenced in FR-009f.
- **View generation is agent-generated**: Agents produce per-repo Python scripts (`regen.py`) that create/maintain symlink views. These are project-specific artifacts, not generic tooling. The `multi_agent_ide_view_generation` skill is instructional only.
- **Per-view regen.py**: Each view directory has its own `regen.py` script (not one global script per repo). This enables granular root invalidation: when `views/api-module/regen.py` changes, only that view's mental models and root diff entries referencing `api-module` are flagged — other views are unaffected. The staleness message identifies which specific view's script changed and references the diff. Specified in FR-003, FR-009, FR-009b, FR-009c, FR-015a.
- **Three skills, two packages, two Docker images**: `multi_agent_ide_view_generation` (instructional), `multi_agent_ide_view_agents` (metadata/helper operations via `view-agents-utils` container), `multi_agent_ide_view_agent_exec` (Ollama queries via `view-agent-exec` container). Both packages scaffolded from `app_template`.
- **Relative symlinks**: `regen.py` scripts MUST use `os.path.relpath` to create relative symlinks that work both on the host and inside Docker containers (where the repo is mounted at `/repo`). Specified in FR-001 and documented in the view generation skill.
- **1:1 View:MentalModel**: Each view has exactly one mental model document (`mental-models.md`), one HEAD, one metadata chain. No ambiguity about which HEAD belongs to which document. Specified in FR-006, FR-007, FR-008.
- **Section path identity**: Sections are identified by unique heading paths (e.g., `Architecture Overview > Data Flow`), not line ranges. Offsets are display metadata only. `view-model add-section` validates uniqueness. Heading renames = delete old chain + create new. Specified in FR-009a, FR-009-addsection.
- **SourceFileRef vs ChildModelRef**: View-level DiffEntries use SourceFileRef (repo_path + content_hash). Root-level DiffEntries use ChildModelRef (view_name + child_section_paths + child_head_metadata_id). Separate types with different invalidation semantics. Specified in FR-009c.
- **Content hash not commit hash**: Staleness uses `git hash-object` (working tree blob hash) which catches both committed and uncommitted changes. Specified in FR-009b, FR-015a.
- The spec intentionally mentions Python scripts, Docker images, and package names in Assumptions because these are user requirements about the implementation structure, not leaked implementation details.
