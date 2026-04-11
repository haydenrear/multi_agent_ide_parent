# Tasks: View Agents — Repository Views & Parallel Local-Model Sub-Agents

**Input**: Design documents from `/specs/001-view-agents/`

**Prerequisites**: plan.md, spec.md, data-model.md, contracts/, research.md, quickstart.md

**Tests**: Included as pytest tasks within each phase (spec requires pytest for unit + integration).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Python parent**: `multi_agent_ide_python_parent/`
- **Packages**: `multi_agent_ide_python_parent/packages/`
- **Utils package**: `multi_agent_ide_python_parent/packages/view_agents_utils/`
- **Exec package**: `multi_agent_ide_python_parent/packages/view_agent_exec/`
- **Skills**: `skills/multi_agent_ide_skills/`
- **App template**: `multi_agent_ide_python_parent/packages/app_template/`

---

## Phase 1: Setup (Project Scaffolding)

**Purpose**: Scaffold both Python packages from `app_template`, configure workspace, create project structure

- [ ] T001 Scaffold `view_agents_utils` package from `app_template` into `multi_agent_ide_python_parent/packages/view_agents_utils/` — copy directory structure, rename package references, create `src/view_agents_utils/__init__.py`
- [ ] T002 Scaffold `view_agent_exec` package from `app_template` into `multi_agent_ide_python_parent/packages/view_agent_exec/` — copy directory structure, rename package references, create `src/view_agent_exec/__init__.py`
- [ ] T003 Configure `view_agents_utils/pyproject.toml` — set project name, add dependencies: `click`, `pydantic`, `gitpython`, `python_di`, `python_util`; set entrypoints for `view-model` and `view-custody` CLI commands
- [ ] T004 Configure `view_agent_exec/pyproject.toml` — set project name, add dependencies: `ollama`, `click`, `pydantic`, `gitpython`, `view_agents_utils` (workspace dep); set entrypoint for `view-agent` CLI command
- [ ] T005 Update `multi_agent_ide_python_parent/pyproject.toml` — add `view_agents_utils` and `view_agent_exec` to `[tool.uv.sources]` as workspace dependencies
- [ ] T006 Create source directory trees per plan.md: `view_agents_utils/src/view_agents_utils/{models,custody,helper}/__init__.py` and `view_agent_exec/src/view_agent_exec/{agent,refresh}/__init__.py`
- [ ] T007 Create test directory trees: `view_agents_utils/tests/conftest.py` and `view_agent_exec/tests/conftest.py`
- [ ] T008 [P] Create `view_agents_utils/docker/Dockerfile` — base from `ghcr.io/astral-sh/uv:python3.11-bookworm-slim`, install `git`, copy workspace, install `view_agents_utils` via uv
- [ ] T009 [P] Create `view_agent_exec/docker/Dockerfile` — base from `ghcr.io/astral-sh/uv:python3.11-bookworm-slim`, install `git`, copy workspace, install both `view_agents_utils` and `view_agent_exec` via uv
- [ ] T010 [P] Create `view_agents_utils/docker/build.sh` and `view_agent_exec/docker/build.sh` — shell scripts to build Docker images with correct tags (`view-agents-utils:latest`, `view-agent-exec:latest`)
- [ ] T011 [P] Create `view_agents_utils/build.gradle.kts` and `view_agent_exec/build.gradle.kts` — Gradle integration following `app_template/build.gradle.kts` pattern
- [ ] T012 [P] Create resource files: `view_agents_utils/resources/application.yml`, `view_agents_utils/resources/application-docker.yml`, `view_agent_exec/resources/application.yml`, `view_agent_exec/resources/application-docker.yml`

**Checkpoint**: Both packages are scaffolded, workspace is configured, `uv pip install -e .` succeeds for both packages, Docker images build successfully.

---

## Phase 2: Foundational (Pydantic Models & Core Data Types)

**Purpose**: Implement the shared Pydantic data models that ALL user stories depend on. These models are the schema contract for metadata files, responses, and all CLI operations.

**CRITICAL**: No user story work can begin until this phase is complete.

- [ ] T013 Implement `LineSpan` model in `multi_agent_ide_python_parent/packages/view_agents_utils/src/view_agents_utils/models/metadata.py` — Pydantic model with `start_line: int` (ge=1), `line_count: int` (ge=1) per `contracts/metadata-file.schema.json`
- [ ] T014 Implement `SourceFileRef` model in `multi_agent_ide_python_parent/packages/view_agents_utils/src/view_agents_utils/models/metadata.py` — `repo_path: str`, `content_hash: str` (40-char hex), `snapshot_path: str`, `line_span: LineSpan | None`
- [ ] T015 Implement `ChildModelRef` model in `multi_agent_ide_python_parent/packages/view_agents_utils/src/view_agents_utils/models/metadata.py` — `view_name: str`, `child_head_metadata_id: str`, `child_section_paths: list[str]` (min 1), `snapshot_path: str`
- [ ] T016 Implement `SupersedesRef` model in `multi_agent_ide_python_parent/packages/view_agents_utils/src/view_agents_utils/models/metadata.py` — `metadata_file_id: str`, `section_path: str`
- [ ] T017 Implement `ViewDiffEntry` and `RootDiffEntry` models in `multi_agent_ide_python_parent/packages/view_agents_utils/src/view_agents_utils/models/metadata.py` — `ViewDiffEntry`: `section_path`, `offset`, `source_files: list[SourceFileRef]` (min 1), `supersedes: SupersedesRef | None`; `RootDiffEntry`: `section_path`, `offset`, `child_models: list[ChildModelRef]` (min 1), `supersedes: SupersedesRef | None`. Add Pydantic validation for mutual exclusivity per schema.
- [ ] T018 Implement `MetadataFile` model in `multi_agent_ide_python_parent/packages/view_agents_utils/src/view_agents_utils/models/metadata.py` — `id: str` (pattern-validated), `created_at: datetime`, `mental_model: Literal["mental-models.md"]`, `scope: Literal["view", "root"]`, `owner_name: str`, `view_script_hash: str | None`, `diff_entries: list[ViewDiffEntry] | list[RootDiffEntry]`, `snapshot_dir: str`. Add Pydantic validators: scope=view requires non-null hash (40 hex), scope=root requires null hash and owner_name="root".
- [ ] T019 Implement `ViewAgentResponse` model in `multi_agent_ide_python_parent/packages/view_agents_utils/src/view_agents_utils/models/response.py` — `view_name`, `mode: Literal["view", "root"]`, `query`, `response`, `mental_model_updated: bool`, `stale_sections_refreshed: list[str]`, `metadata_files_created: list[str]`, `error: str | None` per `contracts/view-agent-response.schema.json`
- [ ] T020 Write unit tests for all Pydantic models in `multi_agent_ide_python_parent/packages/view_agents_utils/tests/test_models.py` — validate serialization/deserialization, schema enforcement (scope constraints, hash patterns, minItems), and rejection of invalid data

**Checkpoint**: All Pydantic models serialize/deserialize correctly, schema validation matches `contracts/metadata-file.schema.json` and `contracts/view-agent-response.schema.json`. Tests pass.

---

## Phase 3: User Story 1 — Generate Repository Views (Priority: P1) — MVP Part 1

**Goal**: The `multi_agent_ide_view_generation` skill documents the view concept so agents can generate per-view `regen.py` Python scripts that create symlink-based views with relative paths.

**Independent Test**: Point an agent at a sample repository. Verify `views/` directory is created with named view subdirectories, each containing relative symlinks and a `regen.py`. Re-running `regen.py` recreates the view.

**Cucumber Test Tag**: `@view-generation`

### Implementation for User Story 1

- [ ] T021 [US1] Create skill directory `skills/multi_agent_ide_skills/multi_agent_ide_view_generation/SKILL.md` — document the view concept: what views are, folder hierarchy (`views/<name>/regen.py`, `views/<name>/mental-models/`), relative symlink requirement (`os.path.relpath`), per-view script model, when regeneration is needed (post-ticket-merge, file structure changes), expected `regen.py` interface (entry point, arguments, output). Include sample `regen.py` from quickstart.md.

**Checkpoint**: Skill documentation is complete. An agent loading this skill has enough information to generate per-view `regen.py` scripts for any repository.

---

## Phase 4: User Story 2 — Mental Models with Chain of Custody (Priority: P1) — MVP Part 2

**Goal**: Implement the full chain-of-custody system: metadata operations, staleness detection, helper scripts (`view-model show/files/update/add-ref/add-section/status`), and the `view-custody search` tool. All consumers interact with mental models exclusively through these helper scripts.

**Independent Test**: Generate views, create a mental model for a view, modify a source file. Run `view-model status` — verify stale sections are flagged. Run `view-model show` — verify annotations. Run `view-model update` — verify new metadata file created. Run `view-custody search` — verify history traversal.

**Cucumber Test Tag**: `@view-mental-models`

### Implementation for User Story 2

#### Core Custody Operations

- [ ] T022 [US2] Implement metadata ID generation in `multi_agent_ide_python_parent/packages/view_agents_utils/src/view_agents_utils/custody/metadata_ops.py` — generate `<ISO-timestamp>-<content-sha256-prefix-8>` IDs, create immutable MetadataFile JSON, create snapshot directories, copy curated input files preserving relative paths, manage HEAD symlink (atomic update)
- [ ] T023 [US2] Implement staleness detection in `multi_agent_ide_python_parent/packages/view_agents_utils/src/view_agents_utils/custody/staleness.py` — for view-level: compare `git hash-object` of working-tree files against recorded `content_hash` in SourceFileRefs, compare recorded `view_script_hash` against current `regen.py` hash (if script changed, flag ALL sections); for root-level: compare recorded `child_head_metadata_id` against current child HEAD, check child section staleness. Return per-section status (CURRENT/STALE) with reason.
- [ ] T024 [US2] Implement chain search in `multi_agent_ide_python_parent/packages/view_agents_utils/src/view_agents_utils/custody/search.py` — traverse backward from HEAD following `supersedes` pointers, filter by: `section_path`, `source file path` (view-level), `child view name` (root-level), `child section path` within a child view (root-level — matches against ChildModelRef.child_section_paths), `date range`, `content-hash range` (view-level only), `max_depth`. Return matching metadata file summaries with snapshot references.

#### Helper Scripts (CLI)

- [ ] T025 [US2] Implement Click CLI entrypoint in `multi_agent_ide_python_parent/packages/view_agents_utils/src/view_agents_utils/cli.py` — register `view-model` group (subcommands: show, files, update, add-ref, add-section, status) and `view-custody` group (subcommand: search). Wire to pyproject.toml `[project.scripts]`.
- [ ] T026 [US2] Implement `view-model show` in `multi_agent_ide_python_parent/packages/view_agents_utils/src/view_agents_utils/helper/show.py` — read HEAD metadata, run staleness check, render mental model with per-section CURRENT/STALE annotations plus curated references. Support `--format markdown|json` and `--repo PATH`. View-level output shows source files; root-level shows child model refs. No metadata file paths exposed. Per contract in `contracts/view-model-cli.md`.
- [ ] T027 [US2] Implement `view-model files` in `multi_agent_ide_python_parent/packages/view_agents_utils/src/view_agents_utils/helper/files.py` — list curated references with status. Support `--section-path`, `--format text|json`. View-level: source file paths. Root-level: child view references. Per contract in `contracts/view-model-cli.md`.
- [ ] T028 [US2] Implement `view-model update` in `multi_agent_ide_python_parent/packages/view_agents_utils/src/view_agents_utils/helper/update.py` — accept `--content FILE` and `--refs FILE` (JSON). Validate refs scope matches model scope (view→source_files, root→child_models). Read current HEAD, compute hashes, create staged metadata file with DiffEntries + supersedes links + snapshots, atomically commit (write markdown, install metadata + snapshots, flip HEAD). Per-mental-model locking. Exit codes: 0 success, 1 error, 5 ref not found, 7 invalid scope. Per contract in `contracts/view-model-cli.md`.
- [ ] T029 [US2] Implement `view-model add-ref` in `multi_agent_ide_python_parent/packages/view_agents_utils/src/view_agents_utils/helper/add_ref.py` — accept `--section-path`, `--file` (view-level) or `--child-view`+`--child-section-path` (root-level), optional `--line-span`. Validate exactly one ref kind per invocation + scope match. Create new metadata file with expanded curated set. No content change. Per contract in `contracts/view-model-cli.md`.
- [ ] T030 [US2] Implement `view-model add-section` in `multi_agent_ide_python_parent/packages/view_agents_utils/src/view_agents_utils/helper/add_section.py` — accept `--heading`, `--parent` (optional), `--content FILE`, `--refs FILE`. Compute full section path (`<parent> > <heading>` or just `<heading>`). Validate uniqueness — reject with exit code 6 if duplicate. Append section to markdown at correct heading depth. Create metadata file with DiffEntry (no supersedes — new chain). Atomic commit. Per contract in `contracts/view-model-cli.md`.
- [ ] T031 [US2] Implement `view-model status` in `multi_agent_ide_python_parent/packages/view_agents_utils/src/view_agents_utils/helper/status.py` — report per-section CURRENT/STALE without rendering content. Include MODEL SCOPE (VIEW/ROOT) and VIEW SCRIPT status. Support `--format text|json`. Per contract in `contracts/view-model-cli.md`.

#### Tests for User Story 2

- [ ] T032 [P] [US2] Write unit tests for metadata operations in `multi_agent_ide_python_parent/packages/view_agents_utils/tests/test_metadata_ops.py` — test ID generation, metadata file creation, snapshot copying, HEAD symlink management, atomic update semantics, locking
- [ ] T033 [P] [US2] Write unit tests for staleness detection in `multi_agent_ide_python_parent/packages/view_agents_utils/tests/test_staleness.py` — test view-level hash comparison, view script hash change flags all sections, root-level child metadata ID comparison, child section status propagation
- [ ] T034 [P] [US2] Write unit tests for search in `multi_agent_ide_python_parent/packages/view_agents_utils/tests/test_search.py` — test backward traversal, section path filter, file path filter (view-level), child view filter (root-level), child section path filter (root-level — matches ChildModelRef.child_section_paths), date range, hash range, max depth
- [ ] T035 [P] [US2] Write unit tests for helper scripts in `multi_agent_ide_python_parent/packages/view_agents_utils/tests/test_helper_scripts.py` — test show (markdown + JSON output, staleness annotations), files (text + JSON, section filter), update (atomic commit, scope validation, exit codes), add-ref (scope validation, ref kind exclusivity), add-section (uniqueness validation, exit code 6), status (text + JSON output)

**Checkpoint**: All `view-model` and `view-custody` CLI commands work locally. `view-model show`, `files`, `update`, `add-ref`, `add-section`, `status` all produce correct output. Chain traversal via `view-custody search` works. Unit tests pass. Docker image builds and CLI works inside container.

---

## Phase 5: User Story 3 — Docker-Scoped View-Agent CLI (Priority: P1) — MVP Part 3

**Goal**: Implement the `view-agent query` CLI in the `view_agent_exec` package — staleness-check-then-query flow, Ollama integration, structured JSON response, Docker scoping.

**Independent Test**: Create two small test views. Modify a source file in one. Run two `docker run` commands in parallel. Verify stale view detects and refreshes, both return structured JSON responses.

**Cucumber Test Tag**: `@view-agent-cli`

### Implementation for User Story 3

- [ ] T036 [US3] Implement view-mode runner in `multi_agent_ide_python_parent/packages/view_agent_exec/src/view_agent_exec/agent/runner.py` — load view file listing and mental model content, construct prompt for Ollama, call Ollama via Python SDK (`host.docker.internal:11434`), parse response, return `ViewAgentResponse`. Handle Ollama connection errors with exit code 2.
- [ ] T037 [US3] Implement root-mode runner in `multi_agent_ide_python_parent/packages/view_agent_exec/src/view_agent_exec/agent/root_runner.py` — `--mode root` variant: load all views under `views/`, load root mental model from `views/mental-models/mental-models.md`, synthesize per-view mental models, query Ollama, return `ViewAgentResponse`.
- [ ] T038 [US3] Implement staleness refresh in `multi_agent_ide_python_parent/packages/view_agent_exec/src/view_agent_exec/refresh/staleness_refresh.py` — as first step of every invocation: read HEAD metadata, run staleness check (via `view_agents_utils.custody.staleness`). If stale sections found: diff snapshot vs live file/child, construct research prompt, call Ollama, update affected mental model sections via `view-model update` atomic flow, create new metadata file. Then proceed to query.
- [ ] T039 [US3] Implement Click CLI entrypoint in `multi_agent_ide_python_parent/packages/view_agent_exec/src/view_agent_exec/cli.py` — `view-agent query` command with options: `--view PATH` (required), `--model TEXT` (required), `--ollama-url TEXT` (default `http://host.docker.internal:11434`), `--mode view|root` (default view), `--timeout INTEGER` (default 120), `--skip-refresh`. Wire staleness_refresh → runner/root_runner → JSON stdout. Exit codes per contract: 0 success, 1 error, 2 Ollama error, 3 invalid view path.

#### Tests for User Story 3

- [ ] T040 [P] [US3] Write unit tests for runner in `multi_agent_ide_python_parent/packages/view_agent_exec/tests/test_runner.py` — mock Ollama SDK, test prompt construction, response parsing, error handling
- [ ] T041 [P] [US3] Write unit tests for root runner in `multi_agent_ide_python_parent/packages/view_agent_exec/tests/test_root_runner.py` — mock Ollama, test multi-view synthesis, root mental model loading
- [ ] T042 [P] [US3] Write unit tests for staleness refresh in `multi_agent_ide_python_parent/packages/view_agent_exec/tests/test_staleness_refresh.py` — test stale detection triggers refresh, fresh model skips refresh, metadata file created after refresh
- [ ] T043 [US3] Write CLI integration test in `multi_agent_ide_python_parent/packages/view_agent_exec/tests/test_cli.py` — test full CLI invocation with mocked Ollama, verify JSON output matches `view-agent-response.schema.json`, test exit codes

**Checkpoint**: `view-agent query` CLI works locally with Ollama. Staleness check runs automatically. JSON output conforms to schema. Docker image builds and CLI works inside container with `docker run -v /repo:/repo:ro -v <mental-models>:<mental-models>:rw` (read-only repo mount + read-write mental-models overlay, since the view-agent writes updated mental models and metadata files during staleness refresh and query processing).

---

## Phase 6: User Story 4 — Discovery Agent Integration (Skills + Prompt Driven) (Priority: P2)

**Goal**: Create the three skills and the two-phase fan-out orchestration so the discovery agent can parallelize work across views using Docker containers.

**Independent Test**: Configure discovery agent with view-agent skills. Against a repository with generated views, issue a discovery request. Verify parallel Docker containers launch (one per view), root container runs after, consolidated result returned.

**Cucumber Test Tag**: `@view-agent-discovery-integration`

### Implementation for User Story 4

#### Skill: multi_agent_ide_view_agents (metadata operations)

- [ ] T044 [P] [US4] Create `skills/multi_agent_ide_skills/multi_agent_ide_view_agents/SKILL.md` — document usage of `view-model` and `view-custody` helper scripts via Docker container invocation
- [ ] T045 [P] [US4] Implement `skills/multi_agent_ide_skills/multi_agent_ide_view_agents/scripts/view_model.py` — Python script that translates CLI args to `docker run --rm -v <repo>:/repo:ro [-v <mental-models>:<mental-models>:rw] view-agents-utils:latest view-model <subcommand> ...`. Handle read-only vs write operations (show/files/status = ro only; update/add-ref/add-section = rw overlay for mental-models/).
- [ ] T046 [P] [US4] Implement `skills/multi_agent_ide_skills/multi_agent_ide_view_agents/scripts/search_custody.py` — Python script that translates CLI args to `docker run --rm -v <repo>:/repo:ro view-agents-utils:latest view-custody search ...`

#### Skill: multi_agent_ide_view_agent_exec (Ollama query execution)

- [ ] T047 [P] [US4] Create `skills/multi_agent_ide_skills/multi_agent_ide_view_agent_exec/SKILL.md` — document usage of `query_view.py`, `query_root.py`, `fan_out.py` scripts
- [ ] T048 [P] [US4] Implement `skills/multi_agent_ide_skills/multi_agent_ide_view_agent_exec/scripts/query_view.py` — Python script: `docker run --rm -v <repo>:/repo:ro -v <repo>/views/<name>/mental-models:/repo/views/<name>/mental-models:rw view-agent-exec:latest query --view /repo/views/<name> --model <model> "<query>"`
- [ ] T049 [P] [US4] Implement `skills/multi_agent_ide_skills/multi_agent_ide_view_agent_exec/scripts/query_root.py` — Python script: `docker run --rm -v <repo>:/repo:ro -v <repo>/views/mental-models:/repo/views/mental-models:rw view-agent-exec:latest query --view /repo/views --model <model> --mode root "<query>"`
- [ ] T050 [US4] Implement `skills/multi_agent_ide_skills/multi_agent_ide_view_agent_exec/scripts/fan_out.py` — Python script orchestrating two-phase discovery: detect view directories, launch one `query_view.py` subprocess per view in parallel, wait for all (with `--timeout`), launch `query_root.py` for synthesis, return consolidated JSON. Handle per-view timeouts gracefully (note which views timed out).
- [ ] T051 [US4] Create `skills/multi_agent_ide_skills/multi_agent_ide_view_agent_exec/prompts/discovery-integration.md` — instructional prompt for the discovery agent: when to use view-agent workflow, how to detect available views, the two-phase flow, how to interpret per-view and root responses, fallback when no views exist

**Checkpoint**: All three skills are complete. `fan_out.py` successfully orchestrates parallel per-view containers + root synthesis. Discovery agent can load skills and use the two-phase workflow.

---

## Phase 7: User Story 5 — Chain of Custody History & Search (Priority: P2)

**Goal**: The search/query tool (already implemented in Phase 4 as `view-custody search`) is validated end-to-end with multi-step chains.

**Independent Test**: Generate views, create a mental model, update it twice. Use `view-custody search` to walk backwards for a specific section — verify three metadata files with snapshot directories.

**Cucumber Test Tag**: `@view-custody-history`

### Implementation for User Story 5

- [ ] T052 [US5] Write integration test for multi-step chain traversal in `multi_agent_ide_python_parent/packages/view_agents_utils/tests/test_chain_integration.py` — create initial mental model (metadata file 1), update twice (metadata files 2, 3), walk backwards via `view-custody search` for a specific section, verify 3 entries each with correct snapshot directories and supersedes pointers
- [ ] T053 [US5] Write integration test for cross-view root chain in `multi_agent_ide_python_parent/packages/view_agents_utils/tests/test_root_chain_integration.py` — create two view mental models, create root mental model referencing both, update one view, verify root staleness detects the change via child_head_metadata_id comparison, verify search filters by child view name

**Checkpoint**: History traversal works across view-level and root-level chains. Search filters (section path, file path, child view, date range, hash range) all produce correct results.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Docker integration testing, documentation finalization, quickstart validation

- [ ] T054 Docker integration test: build both images, run `view-agents-utils` container with a sample repo mounted at `/repo:ro`, verify `view-model show` works inside container with relative symlinks resolving correctly
- [ ] T055 Docker integration test: run `view-agent-exec` container with mock Ollama endpoint, verify full staleness-check-then-query flow works with mounted repo
- [ ] T056 Docker parallel test: run two `view-agent-exec` containers simultaneously targeting different views, verify no write conflicts (each writes only to its own `mental-models/` directory)
- [ ] T057 Run `quickstart.md` validation — execute all steps from quickstart.md against a sample repository, verify all commands produce expected output
- [ ] T058 Verify JSON schema compliance — run Pydantic model `.model_json_schema()` against `contracts/metadata-file.schema.json` and `contracts/view-agent-response.schema.json`, confirm compatibility
- [ ] T059 Create Cucumber feature files in `test_graph/src/test/resources/features/view_agents/` for tags `@view-generation`, `@view-mental-models`, `@view-agent-cli`, `@view-agent-discovery-integration`, `@view-custody-history` — define Gherkin scenarios from spec.md acceptance scenarios, create step definitions, add to test graph per `test_graph/instructions.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 (packages must exist) — BLOCKS all user stories
- **US1 (Phase 3)**: Depends on Phase 2 — instructional skill only, no code dependency on models
- **US2 (Phase 4)**: Depends on Phase 2 — needs Pydantic models for all custody operations
- **US3 (Phase 5)**: Depends on Phase 2 + Phase 4 — exec package imports staleness/metadata from utils
- **US4 (Phase 6)**: Depends on Phase 4 + Phase 5 — skill scripts invoke Docker images from both packages
- **US5 (Phase 7)**: Depends on Phase 4 — integration tests exercise the search tool built in Phase 4
- **Polish (Phase 8)**: Depends on all desired user stories being complete

### User Story Dependencies

- **US1 (P1)**: Can start after Foundational — No code dependencies on other stories
- **US2 (P1)**: Can start after Foundational — Independent of US1 (skill doc vs Python implementation)
- **US3 (P1)**: Depends on US2 completion — `view_agent_exec` uses `view_agents_utils` staleness/metadata ops
- **US4 (P2)**: Depends on US2 + US3 — skill scripts wrap Docker images built from both packages
- **US5 (P2)**: Can start after US2 — integration tests for chain traversal (search built in US2)

### Within Each User Story

- Models before operations
- Operations before CLI wiring
- CLI before tests (tests exercise the full CLI)
- Core implementation before integration

### Parallel Opportunities

- T008, T009, T010, T011, T012 (Phase 1 Docker/Gradle/resources) can run in parallel
- T013–T019 (Phase 2 models) are sequential (models build on each other) but T020 (tests) runs after all
- US1 (Phase 3) and US2 (Phase 4) can start in parallel after Foundational
- T032, T033, T034, T035 (US2 tests) can all run in parallel
- T040, T041, T042 (US3 tests) can run in parallel
- T044, T045, T046, T047, T048, T049 (US4 skill scripts) can run in parallel
- US5 (Phase 7) can run in parallel with US4 (Phase 6) once US2 is complete

---

## Parallel Example: User Story 2

```bash
# Launch all tests in parallel after implementation:
Task: "test_metadata_ops.py"     # T032
Task: "test_staleness.py"        # T033
Task: "test_search.py"           # T034
Task: "test_helper_scripts.py"   # T035
```

## Parallel Example: User Story 4

```bash
# Launch all skill scripts in parallel:
Task: "SKILL.md (view_agents)"         # T044
Task: "view_model.py"                  # T045
Task: "search_custody.py"              # T046
Task: "SKILL.md (view_agent_exec)"     # T047
Task: "query_view.py"                  # T048
Task: "query_root.py"                  # T049
```

---

## Implementation Strategy

### MVP First (User Stories 1 + 2 + 3)

1. Complete Phase 1: Setup — scaffold both packages
2. Complete Phase 2: Foundational — Pydantic models
3. Complete Phase 3: US1 — instructional skill for view generation
4. Complete Phase 4: US2 — custody system + helper scripts
5. Complete Phase 5: US3 — view-agent CLI with Ollama
6. **STOP and VALIDATE**: Test all three stories independently, run Docker integration tests
7. Deploy/demo if ready

### Incremental Delivery

1. Phase 1 + 2 → Foundation ready
2. Add US1 + US2 → Mental model system works end-to-end (MVP core!)
3. Add US3 → Full Docker-scoped query pipeline
4. Add US4 → Discovery agent integration with parallel fan-out
5. Add US5 → Chain of custody history fully validated
6. Each story adds value without breaking previous stories

---

## Notes

- Both packages scaffolded from `multi_agent_ide_python_parent/packages/app_template`
- `view_agent_exec` depends on `view_agents_utils` as a UV workspace dependency
- Docker images: `view-agents-utils:latest` (utils only), `view-agent-exec:latest` (both packages)
- All CLI commands use `click` for argument parsing, `pydantic` for data validation
- Metadata files are immutable JSON — never modified after creation
- HEAD pointer is a symlink — atomic update
- Staleness uses `git hash-object` (working-tree blob hash), not commit hash
- View-level uses SourceFileRef, root-level uses ChildModelRef — separate types, separate invalidation
- Section identity is the unique section path (heading hierarchy joined by ` > `)
- All mental model modifications must be atomic with per-mental-model locking (FR-017c)
- Discovery agent integration is skills + prompt driven — no new Java orchestration path
