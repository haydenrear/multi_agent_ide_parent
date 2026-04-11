# Implementation Plan: View Agents — Repository Views & Parallel Local-Model Sub-Agents

**Branch**: `001-view-agents` | **Date**: 2026-04-10 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-view-agents/spec.md`

## Summary

Build a view-agents system with four layers: (1) agent-generated per-view Python scripts that create and maintain repository views (symlink directories), each view's script monitored independently for changes to enable granular mental model invalidation, (2) a chain-of-custody system using immutable metadata files and snapshot directories to track mental model provenance, with helper scripts (`view-model show/files/update/add-ref/status`) abstracting all metadata mechanics for both view and root mental models, (3) a Docker-scoped Python CLI that queries host Ollama and auto-refreshes stale mental models on each invocation, and (4) a discovery-agent integration delivered through skills and prompt updates that can fan out parallel Docker containers per view then synthesize at root level.

Two Python packages are scaffolded from the existing `app_template`: **`view_agents_utils`** (metadata, custody, helper scripts) and **`view_agent_exec`** (Ollama query execution). Each produces its own Docker image. Three skills provide the interface layer: **`multi_agent_ide_view_generation`** (instructional — documents the view concept and how agents generate Python scripts for views), **`multi_agent_ide_view_agents`** (Python scripts invoking the `view_agents_utils` container for mental model operations), and **`multi_agent_ide_view_agent_exec`** (Python scripts invoking the `view_agent_exec` container for Ollama queries and two-phase fan-out). Discovery integration is achieved by loading those skills and updating prompts, not by introducing a new hard-coded Java orchestration path. The integration-test prompt/profile explicitly prefers this workflow whenever generated views are present.

## Technical Context

**Language/Version**: Python 3.11+ (two packages, two Docker images); discovery integration via skills/prompt updates
**Base Package**: Both packages scaffolded from `multi_agent_ide_python_parent/packages/app_template` (Gradle integration, Docker build, uv workspace setup)
**Primary Dependencies**:
- `view_agents_utils`: `pydantic` (metadata models/validation), `gitpython` (commit hash resolution), `click` (CLI framework)
- `view_agent_exec`: `ollama` Python SDK (Ollama API client), `click` (CLI framework), `pydantic`, `gitpython`, `view_agents_utils` (workspace dependency)
**Storage**: Filesystem — immutable JSON metadata files + snapshot directories in `mental-models/` per view; no database
**Testing**: `pytest` (unit + integration), manual Docker invocation tests
**Target Platform**: macOS/Linux developer machines with Docker + Ollama
**Project Type**: Two Python CLI packages, each producing a Docker image, invoked via skill Python scripts
**Performance Goals**: View generation <60s for 5k files; staleness check <5s for 100 tracked files; search <2s for 100 metadata files
**Constraints**: Docker containers mount the repository read-only at a stable path so relative symlinks resolve; only the target `mental-models/` directory is mounted read-write; Ollama accessed via `host.docker.internal`
**Scale/Scope**: Repositories up to 5k files; up to ~20 views per repo; mental model chains up to 100 metadata files

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Computation Graph with State Machine | **N/A** | No new node types — discovery agent invokes skill scripts as tool calls from existing DiscoveryAgentNode |
| II. Three-Phase Workflow Pattern | **PASS** | Not introducing new phases; view-agents are a tool available to existing discovery phase |
| III. Orchestrator, Work, Collector Pattern | **N/A** | View-agent parallelism is managed externally via Docker, not via new graph nodes |
| IV. Event-Driven Node Lifecycle | **N/A** | No new event types; Docker container results consumed by discovery agent tool output |
| V. Artifact-Driven Context Propagation | **PASS** | Mental models are markdown artifact files; metadata files are structured JSON — both are filesystem artifacts |
| VI. Worktree Isolation & Feature Branching | **PASS** | Views must be regenerated after ticket completion (worktree merge changes files); view generation scripts handle this |
| VII. Tool Integration & Agent Capabilities | **PASS** | Discovery agent invokes skill scripts via existing shell execution tools; no new Java tools needed |
| VIII. Human-in-the-Loop Review Gates | **N/A** | View-agents produce informational artifacts, not code changes requiring review |
| API Design: No path variables for slash IDs | **N/A** | No REST endpoints introduced |
| Schema Changes: Liquibase | **N/A** | No database schema changes |

**Gate result**: **PASS** — no constitution violations.

## Architecture: Two Packages, Two Images, Three Skills

### Two Python Packages (both scaffolded from `app_template`)

**Package 1: `view_agents_utils`** — Metadata, chain of custody, helper scripts
- Pydantic models for MetadataFile, DiffEntry, SourceFileRef, ChildModelRef, SupersedesRef
- Metadata operations: create immutable metadata files, copy snapshots, link entries
- Staleness detection: compare content hashes (working tree) + view generation script hash for view models; compare child head metadata IDs and child section status for the root model
- Helper CLI commands: `view-model show/files/update/add-ref/add-section/status`
- Section identity: unique section paths (heading hierarchy joined by ` > `), validated by `add-section`
- Search CLI: `view-custody search` — traverse metadata chain
- Docker image: `view-agents-utils:latest`

**Package 2: `view_agent_exec`** — Ollama query execution
- Staleness-check-then-query flow (uses `view_agents_utils` as workspace dependency)
- Ollama integration via Python SDK
- Per-view query mode and `--mode root` synthesis mode
- Structured JSON response output (contract: `contracts/view-agent-response.schema.json`)
- Docker image: `view-agent-exec:latest`

### Three Skills

**Skill 1: `multi_agent_ide_view_generation`** — Instructional only (no scripts)
- Documents the view concept: what views are, folder hierarchy, **relative** symlink structure
- Instructs agents to generate per-view Python scripts that create/maintain individual views using **relative symlinks** (`os.path.relpath`) for Docker portability
- Each view directory gets its own `regen.py` — scripts are per-view, not per-repo
- The scripts are monitored independently — when a view's `regen.py` changes, only that view's mental models and root references to it are flagged for staleness review (granular invalidation)
- Documents when view regeneration is needed (post-ticket-merge, file moves/adds/deletes)

**Skill 2: `multi_agent_ide_view_agents`** — Mental model operations
- Python scripts that invoke `docker run` on the `view-agents-utils:latest` image
- `scripts/view_model.py` — invokes `view-model show/files/update/add-ref/status` in container
- `scripts/search_custody.py` — invokes `view-custody search` in container
- All scripts mount the repository read-only and the target `mental-models/` directory read-write

**Skill 3: `multi_agent_ide_view_agent_exec`** — Ollama query execution
- Python scripts that invoke `docker run` on the `view-agent-exec:latest` image
- `scripts/query_view.py` — runs a query against a single target view path under the repository mount
- `scripts/query_root.py` — runs root synthesis query across all views
- `scripts/fan_out.py` — orchestrates the two-phase discovery flow (parallel per-view containers, then root)
- `prompts/discovery-integration.md` — instructional prompt for discovery agent

### Invocation Flow

```
Discovery Agent
  └─→ skill script (multi_agent_ide_view_agent_exec/scripts/query_view.py)
        └─→ docker run view-agent-exec:latest query --view /repo/views/<name> --model llama3.2 ...
              ├─→ staleness check (uses view_agents_utils internally)
              ├─→ refresh stale sections (calls Ollama, writes new metadata file)
              └─→ query processing (calls Ollama, outputs JSON response)

Agent needing mental model info
  └─→ skill script (multi_agent_ide_view_agents/scripts/view_model.py show ...)
        └─→ docker run view-agents-utils:latest view-model show ...
              └─→ reads HEAD metadata, resolves staleness, renders annotated output

Post-Ticket-Merge
  └─→ agent runs each view's regen.py independently (documented in multi_agent_ide_view_generation)
        └─→ each view's Python script regenerates its own symlinks
              └─→ only the changed view's mental models (and root refs to it) flagged stale
```

## Project Structure

### Documentation (this feature)

```text
specs/001-view-agents/
├── plan.md                                     # This file
├── research.md                                 # Phase 0 output
├── data-model.md                               # Phase 1 output
├── quickstart.md                               # Phase 1 output
├── contracts/                                  # Phase 1 output (CLI interface contracts)
└── tasks.md                                    # Phase 2 output (/speckit.tasks)
```

### Source Code (repository root)

```text
multi_agent_ide_parent/
├── multi_agent_ide_python_parent/              # Existing Python parent (GIT SUBMODULE)
│   ├── pyproject.toml                          # Updated: add view_agents_utils + view_agent_exec to workspace
│   └── packages/
│       ├── app_template/                       # Existing template — used as scaffold base for both packages
│       ├── python_util/                        # Existing shared lib
│       ├── python_di/                          # Existing shared lib
│       │
│       ├── view_agents_utils/                  # NEW: scaffolded from app_template
│       │   ├── pyproject.toml                  # deps: click, pydantic, gitpython, python_di, python_util
│       │   ├── build.gradle.kts                # Gradle integration (from app_template)
│       │   ├── .env
│       │   ├── resources/
│       │   │   ├── application.yml
│       │   │   └── application-docker.yml
│       │   ├── src/
│       │   │   └── view_agents_utils/
│       │   │       ├── __init__.py
│       │   │       ├── cli.py                  # Click CLI: view-model, view-custody entrypoints
│       │   │       ├── models/                 # Pydantic data models
│       │   │       │   ├── __init__.py
│       │   │       │   ├── metadata.py         # MetadataFile, DiffEntry, SourceFileRef, ChildModelRef, SupersedesRef
│       │   │       │   └── response.py         # Helper script response schemas
│       │   │       ├── custody/                # Chain of custody operations
│       │   │       │   ├── __init__.py
│       │   │       │   ├── metadata_ops.py     # Create metadata file, copy snapshots, link entries
│       │   │       │   ├── staleness.py        # Compare content hashes + view script hash, flag stale
│       │   │       │   └── search.py           # Traverse metadata chain, filter queries
│       │   │       └── helper/                 # view-model helper scripts
│       │   │           ├── __init__.py
│       │   │           ├── show.py             # view-model show
│       │   │           ├── files.py            # view-model files
│       │   │           ├── update.py           # view-model update
│       │   │           ├── add_ref.py          # view-model add-ref
│       │   │           ├── add_section.py     # view-model add-section (validates unique section paths)
│       │   │           └── status.py           # view-model status
│       │   ├── tests/
│       │   │   ├── conftest.py
│       │   │   ├── test_metadata_ops.py
│       │   │   ├── test_staleness.py
│       │   │   ├── test_search.py
│       │   │   └── test_helper_scripts.py
│       │   └── docker/
│       │       ├── Dockerfile                  # Image: view-agents-utils:latest
│       │       └── build.sh
│       │
│       └── view_agent_exec/                    # NEW: scaffolded from app_template
│           ├── pyproject.toml                  # deps: ollama, click, pydantic, gitpython, view_agents_utils
│           ├── build.gradle.kts
│           ├── .env
│           ├── resources/
│           │   ├── application.yml
│           │   └── application-docker.yml
│           ├── src/
│           │   └── view_agent_exec/
│           │       ├── __init__.py
│           │       ├── cli.py                  # Click CLI: view-agent query entrypoint
│           │       ├── agent/
│           │       │   ├── __init__.py
│           │       │   ├── runner.py           # Load view, construct prompt, call Ollama
│           │       │   └── root_runner.py      # --mode root: synthesize across views
│           │       └── refresh/
│           │           ├── __init__.py
│           │           └── staleness_refresh.py # Staleness check + Ollama-driven refresh
│           ├── tests/
│           │   ├── conftest.py
│           │   ├── test_runner.py
│           │   ├── test_root_runner.py
│           │   ├── test_staleness_refresh.py
│           │   └── test_cli.py
│           └── docker/
│               ├── Dockerfile                  # Image: view-agent-exec:latest
│               └── build.sh
│
├── skills/multi_agent_ide_skills/              # Existing skills (GIT SUBMODULE)
│   ├── multi_agent_ide_view_generation/        # NEW: instructional only (no scripts)
│   │   └── SKILL.md                            # View concept, folder hierarchy, Python script generation
│   │
│   ├── multi_agent_ide_view_agents/            # NEW: mental model operations
│   │   ├── SKILL.md                            # Usage instructions
│   │   └── scripts/
│   │       ├── view_model.py                   # docker run view-agents-utils:latest view-model <subcmd>
│   │       └── search_custody.py               # docker run view-agents-utils:latest view-custody search
│   │
│   └── multi_agent_ide_view_agent_exec/        # NEW: Ollama query execution
│       ├── SKILL.md                            # Usage instructions
│       ├── scripts/
│       │   ├── query_view.py                   # docker run view-agent-exec:latest query ...
│       │   ├── query_root.py                   # docker run view-agent-exec:latest query --mode root ...
│       │   └── fan_out.py                      # Two-phase discovery: parallel per-view, then root
│       └── prompts/
│           └── discovery-integration.md        # Instructional prompt for discovery agent
```

**Structure Decision**: Two Python packages scaffolded from `packages/app_template`, each producing its own Docker image. `view_agents_utils` owns all metadata/custody/helper logic for both view and root mental models. `view_agent_exec` owns the Ollama query flow and depends on `view_agents_utils` as a workspace dependency. Three skills: `multi_agent_ide_view_generation` is instructional only (agents generate per-view Python scripts — one `regen.py` per view directory), `multi_agent_ide_view_agents` has Python scripts invoking the `view-agents-utils` container, `multi_agent_ide_view_agent_exec` has Python scripts invoking the `view-agent-exec` container. The container contract mounts the repo at a stable read-only path and grants write access only to the target `mental-models/` directory.

## View Generation: Agent-Generated Python Scripts

View generation is **not** a static shell script — the agent generates per-view Python scripts that:

1. Analyze a specific view's file grouping within the repository
2. Create the view directory with symbolic links to a coherent file subset
3. Each view directory contains its own `regen.py` — one script per view, not one per repo
4. Are stored inside the view directories under `views/` in the target repository

These scripts are project-specific artifacts, not generic tooling. Each view's `regen.py` is independent — changing one view's script does not affect other views. This enables granular staleness: if `views/api-module/regen.py` changes, only `api-module`'s mental models and the root sections referencing `api-module` are flagged.

The `multi_agent_ide_view_generation` skill documents:
- What views are and the expected folder hierarchy
- That agents should generate per-view Python scripts
- The expected interface of those scripts (entry point, arguments, output)
- When regeneration is needed (post-ticket-merge, file structure changes)

## View Script Staleness & Chain of Custody

Each view has its own `regen.py` script. When a view's script changes, that view's mental models may need updating — but other views are unaffected.

1. **Per-view script hash tracked in metadata**: Each metadata file records the hash of its view's `regen.py` at the time of creation. Because scripts are per-view, each view's metadata tracks only its own script.
2. **Staleness check includes view script**: The staleness check (FR-015a) compares the recorded script hash against the current `regen.py` hash for that view. If the script changed, all sections in that view's mental models are flagged for review. The staleness message identifies which specific view's script changed and references the diff.
3. **Granular root invalidation**: At the root level, only the diff entries referencing the changed view's mental-model sections are flagged — other views' root entries remain current. The root has no `regen.py`; it becomes stale only through child mental-model references.
4. **Post-ticket-merge trigger**: After a ticket is completed and merged, the per-view `regen.py` scripts are re-run individually. If a view's symlink set changes, that view's mental models are flagged stale.

## Complexity Tracking

> No constitution violations — table not applicable.
