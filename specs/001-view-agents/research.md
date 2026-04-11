# Research: View Agents

**Branch**: `001-view-agents` | **Date**: 2026-04-10

## R-001: Metadata File Format — JSON vs YAML vs Custom

**Decision**: JSON (one file per metadata snapshot)

**Rationale**: JSON is natively supported by Pydantic for serialization/deserialization with zero config. The metadata files contain structured data (diff entries with pointers, git hashes, section paths, and optional source-file spans using `start_line` + `line_count`) — JSON models this cleanly. Each metadata file is a single JSON document, immutable after creation. Naming convention: `<ISO-timestamp>-<short-hash>.json` where `short-hash` is a truncated SHA-256 of the file content for uniqueness.

**Alternatives considered**:
- YAML: More human-readable but slower to parse, ambiguous type coercion, and the project already uses JSON for event data and API contracts.
- SQLite: Would provide query capability but adds a dependency, violates the "flat collection of immutable files" design, and complicates git tracking.
- Custom binary: No benefit given the small file sizes.

## R-002: Metadata File Naming and Head Pointer

**Decision**: Content-hash naming with a `HEAD` symlink

**Rationale**: Each metadata file is named `<ISO-timestamp>-<content-sha256-prefix-8>.json`. A `HEAD` symlink in the `mental-models/` directory points to the current head metadata file. This avoids needing a separate index file and makes the head pointer atomic (symlink update). The symlink approach mirrors git's own `HEAD` reference pattern.

**Alternatives considered**:
- Separate `head.txt` file pointing to the current metadata file: Extra file to manage, non-atomic update. Rejected.
- Lexicographic ordering by filename: Fragile if clocks differ; content hash is deterministic. Rejected.
- Directory-per-metadata-file (metadata.json + snapshots/ inside): Considered but adds nesting depth. Instead, snapshot directories are named to match their metadata file (e.g., `<timestamp>-<hash>.snapshots/`).

## R-003: Snapshot Directory Strategy

**Decision**: One snapshot directory per metadata file, named `<metadata-filename>.snapshots/`

**Rationale**: Each metadata file has a sibling directory containing eagerly-copied input files. The directory is named identically to the metadata file (minus `.json`) with `.snapshots/` appended. Files inside the snapshot directory preserve their relative path structure from the repo root (e.g., `src/main/java/Foo.java` inside the snapshot dir). This keeps snapshots self-contained and co-located with their metadata file.

**Alternatives considered**:
- Content-addressable blob store (git-object-style): Would enable deduplication across snapshots, but adds complexity and the spec defers deduplication to a later iteration. Rejected for MVP.
- Single shared snapshots directory with reference counting: Too complex for the immutable model. Each metadata file owning its snapshots is simpler and aligns with the "fully self-contained" requirement.

## R-004: Python CLI Framework

**Decision**: `click` for CLI, `pydantic` for models (in both packages)

**Rationale**: `click` is the standard Python CLI framework — well-documented, supports subcommands, handles argument parsing, help text, and error formatting. `pydantic` provides typed models with JSON serialization, validation, and schema generation — ideal for the MetadataFile, DiffEntry, and Response models. Both packages use `click` for their respective CLI entrypoints and `pydantic` for shared models.

**Alternatives considered**:
- `argparse`: Built-in but verbose for subcommand hierarchies. `click` is more ergonomic. Rejected.
- `typer`: Built on `click` — adds type hints but also adds a dependency layer. Unnecessary overhead for this use case. Rejected.
- `dataclasses` instead of `pydantic`: No built-in JSON serialization or validation. Rejected.

## R-005: Ollama Integration Pattern

**Decision**: Use `ollama` Python SDK in `view_agent_exec` only, access host Ollama at `http://host.docker.internal:11434`

**Rationale**: The project already uses Ollama with `host.docker.internal:11434` in Docker compositions (see `docker-compose.yml` for Hindsight). The `ollama` Python SDK provides a clean client API for chat completions. Inside Docker containers, the agent connects to the host Ollama instance. The `--model` CLI flag specifies which Ollama model to use. Only the `view_agent_exec` package depends on the Ollama SDK — `view_agents_utils` has no Ollama dependency.

**Alternatives considered**:
- Raw HTTP requests to Ollama API: Works but the SDK handles streaming, retries, model validation. Rejected.
- Bundling Ollama inside the Docker image: Defeats the purpose of using the host's GPU and pre-pulled models. Rejected.
- Putting Ollama SDK in both packages: Only exec needs it. Rejected.

## R-006: Docker Images — Two Images from app_template

**Decision**: Two Docker images, both scaffolded from `app_template`

**Rationale**: `view-agents-utils:latest` contains all metadata, custody, and helper script logic. `view-agent-exec:latest` contains the Ollama query execution logic and depends on `view_agents_utils` as a workspace dependency (installed inside the exec image at build time). Two separate images keeps the utils image lightweight (no Ollama SDK, smaller footprint) and allows them to be versioned/updated independently. Both use `ghcr.io/astral-sh/uv:python3.11-bookworm-slim` as the base image, following the `app_template/docker/Dockerfile_base` pattern. Git CLI is installed in both images for hash resolution. The container contract mounts the repository read-only at `/repo` and overlays only the target `mental-models/` directory as read-write.

**Alternatives considered**:
- Single Docker image: Simpler but mixes concerns. The utils image would carry the Ollama SDK even when only doing metadata operations. Rejected.
- Three images (one per skill): The view generation skill is instructional only — no image needed. Two images is the right number. Rejected.

## R-007: View Generation — Agent-Generated Per-View Python Scripts

**Decision**: Agents generate per-view Python scripts (one `regen.py` per view directory) that create and maintain views. No generic tooling in a package.

**Rationale**: View generation is project-specific — the optimal file groupings depend on the repository's structure, conventions, and domain. Rather than building a one-size-fits-all generator, the `multi_agent_ide_view_generation` skill instructs agents to generate Python scripts tailored to each individual view. Each view directory (e.g., `views/api-module/regen.py`, `views/core-lib/regen.py`) contains its own `regen.py`. Per-view scripts (rather than one global script) provide two key benefits: (1) **granular root invalidation** — when `views/api-module/regen.py` changes, only the root mental model diff entries referencing `api-module` are flagged, not every view, and (2) **self-documenting staleness** — the staleness check can tell the agent exactly which view's script changed and reference the script diff for review.

**Alternatives considered**:
- One global `regen.py` per repo: Simpler but loses granularity — any script change would flag ALL views' mental models and ALL root diff entries. Rejected because a single view restructure shouldn't invalidate unrelated views.
- Generic view generator in a Python package: Would need to handle every possible repo structure. Over-engineering for MVP when the agent can generate project-specific scripts. Rejected.
- Shell scripts for view generation: Less portable, harder to test, and the spec requires Python. Rejected.
- Static configuration files (YAML/JSON) describing views: Would still need a script to execute them. Generating the script directly is simpler. Rejected.

## R-008: Symlink Strategy — Relative Symlinks for Docker Portability

**Decision**: `regen.py` creates **relative** symlinks (`os.path.relpath`); repo root mounted at `/repo` inside containers

**Rationale**: View symlinks must resolve on both the host (developer browsing) and inside Docker containers. The container therefore receives the repository root at `/repo` and targets a view path under that mount (for example `/repo/views/api-module`), preserving the same directory structure the relative symlinks expect. Absolute symlinks (e.g., `/Users/hayde/repo/src/...`) would dangle inside the container. Relative symlinks (e.g., `../../src/main/java/api/`) resolve correctly because the directory structure is preserved. The `multi_agent_ide_view_generation` skill MUST instruct agents to always use `os.path.relpath(target, link_parent)` when creating symlinks. In view mode, the target view's `mental-models/` subdirectory is mounted read-write. In root mode, `views/mental-models/` is mounted read-write. Everything else remains read-only.

**Alternatives considered**:
- Absolute symlinks: Simple on the host but dangle inside Docker containers where the repo is mounted at a different path. Rejected — portability is a hard requirement.
- Copy files into view directories instead of symlinks: Wastes disk space and creates staleness issues. Rejected.
- Docker Desktop symlink resolution at mount time: Only resolves symlinks for the mount root itself, not for symlinks within the mounted directory that point outside it. Not sufficient. Rejected.
- Use Docker volumes instead of bind mounts: Bind mounts are simpler and provide direct host filesystem access for mental model persistence. Rejected.

## R-009: Staleness Check — Container-Side, Including View Script Hash

**Decision**: Container-side staleness check (git installed in container), including view generation script hash for view-level models and child mental-model status for the root model

**Rationale**: The spec requires the staleness check to be the first step of every view-agent invocation (FR-015a). The container needs git to compute current file hashes and compare against the metadata file's recorded hashes. For view-level models, each metadata file records the hash of that view's `regen.py` script (per-view, not global) — if the script has changed, all mental-model sections for that view are flagged for review because the view's file composition may have changed. For the root model, there is no `regen.py`; staleness is computed from child head metadata IDs and child section status. Installing `git` in the Docker image adds ~20MB but keeps the CLI self-contained. The container accesses the repo's `.git` directory through the bind mount (repo root mounted read-only at `/repo`).

**Alternatives considered**:
- Host-side pre-check that passes a "stale sections" list to the container: Splits staleness logic across host and container. Rejected.
- Ignoring view script changes: Would leave mental models stale when the view is restructured. Rejected.

## R-010: Three Skills — Separation of Concerns

**Decision**: Three skills: instructional (view generation), operations (mental models), execution (Ollama queries)

**Rationale**: Separating into three skills maps cleanly to three distinct responsibilities:
1. **`multi_agent_ide_view_generation`** — Instructional only. Documents what views are, how agents should generate Python scripts to create them, the folder hierarchy, when to regenerate. No wrapper scripts — the agent generates the per-repo scripts.
2. **`multi_agent_ide_view_agents`** — Python scripts that invoke the `view-agents-utils` container for all mental model operations (show, files, update, add-ref, status, search). Doesn't need Ollama.
3. **`multi_agent_ide_view_agent_exec`** — Python scripts that invoke the `view-agent-exec` container for Ollama queries (per-view, root, two-phase fan-out). This is where the discovery agent integration prompt lives.

This avoids the confusion of mixing query execution with metadata operations in a single skill.

**Alternatives considered**:
- Two skills (generation + everything else): Mixes metadata operations with Ollama queries. Rejected — too confusing.
- One skill: Even more confusing. Rejected.
- Four skills (generation, metadata, helpers, execution): Over-split. Metadata and helper operations are tightly coupled. Rejected.

## R-011: Skill Scripts — Python, Not Shell

**Decision**: Skill wrapper scripts are Python (.py), not shell (.sh)

**Rationale**: The skill scripts invoke `docker run` on the respective container images. Using Python for these scripts (instead of shell) provides: better argument handling, easier JSON parsing of container output, consistent with the project's Python infrastructure, and the ability to import shared utilities from the workspace. The scripts use `subprocess.run` to invoke `docker run` with the correct mounts and arguments.

**Alternatives considered**:
- Shell scripts (.sh): Less portable, harder to parse JSON output, inconsistent with the Python-centric design. Rejected.

## R-012: view_agent_exec Depends on view_agents_utils

**Decision**: `view_agent_exec` lists `view_agents_utils` as a UV workspace dependency

**Rationale**: The exec package needs access to the Pydantic models (MetadataFile, DiffEntry), staleness detection logic, and metadata operations (creating new metadata files after mental model refresh). Rather than duplicating this code, `view_agent_exec` imports from `view_agents_utils` directly. In the Docker image for exec, both packages are installed at build time via UV. This follows the existing workspace dependency pattern (e.g., `python_di` depending on `python_util`).

**Alternatives considered**:
- Duplicating models/logic in both packages: Violates DRY. Rejected.
- Single package: Would make the utils image carry the Ollama SDK. Rejected per R-006.

## R-013: Section Identity — Unique Section Paths

**Decision**: Section identity is the **section path** (full heading hierarchy joined by ` > `), not line ranges or offsets. Uniqueness is enforced by `view-model add-section`.

**Rationale**: Sections need a stable identity that survives content insertions above them (which shift offsets) and doesn't break lineage when text is added or removed. Line ranges and offsets shift constantly. The section path (`Architecture Overview > Data Flow`) is stable because headings are immutable identifiers. The `view-model add-section` command validates uniqueness at write time. If a heading must be renamed, it's semantically "delete old + create new" — the old chain is preserved in history, the new section starts with no supersedes pointer. Offset is recorded as display metadata only (ordinal position at creation time), not as part of identity or lineage.

**Alternatives considered**:
- Line ranges as identity: Fragile — inserting any text above a section shifts all subsequent ranges. Rejected.
- Stable UUIDs (HTML comments like `<!-- section:a1b2c3 -->`): Works but makes mental model markdown uglier and adds parsing complexity. Deferred — can be introduced later if heading stability becomes a problem.
- Heading text alone (without hierarchy): Doesn't handle duplicate headings at different nesting levels. Rejected.

## R-014: Content Hash (Working Tree) Not Commit Hash

**Decision**: Use `git hash-object <file>` (working tree blob hash) for staleness detection, not committed file hashes

**Rationale**: The spec requires detecting when curated source files have changed since the last metadata snapshot. Using `git hash-object` on the working tree file gives the blob hash of the current content — this catches both committed and uncommitted changes. The previous "commit hash advances" wording was too narrow: if a developer modifies a file but hasn't committed yet, the mental model should still flag it as potentially stale. The snapshot copy serves as the "before" state; at refresh time, diffing snapshot vs. live file produces the change context for the LLM.

**Alternatives considered**:
- Commit hash (`git log` / `git rev-parse`): Only detects committed changes. Misses in-progress work in active worktrees. Rejected.
- File mtime: Unreliable — can be reset by file system operations, varies across machines. Rejected.
- Full file content comparison: Equivalent to blob hash but slower. `git hash-object` is O(file-size) and consistent with git's own identity model. Rejected.

## R-015: Separate Ref Types — SourceFileRef vs ChildModelRef

**Decision**: View-level DiffEntries use `SourceFileRef` (repo_path + content_hash); root-level DiffEntries use `ChildModelRef` (view_name + child_section_paths + child_head_metadata_id). They are separate types, not overloaded.

**Rationale**: View-level and root-level references have fundamentally different invalidation semantics. A view section is stale when a source file's content hash changes. A root section is stale when a child view's head metadata ID changes (meaning the child mental model was updated) or when any of the specifically referenced child sections are stale. Overloading one "CuratedFileRef" type for both cases was confusing — the `repo_path`/`git_hash` fields don't make sense for a child model reference. Splitting into two types makes the schema self-documenting: if you see `source_files`, it's a view-level entry; if you see `child_models`, it's root-level.

**Alternatives considered**:
- Single `CuratedFileRef` with overloaded semantics: Confusing — `repo_path` for a child model would be the path to the mental model file, but invalidation should be based on metadata ID, not file hash. Rejected.
- Discriminated union with a `ref_type` field: More complex than two separate lists, and each DiffEntry only has one type. Rejected.
