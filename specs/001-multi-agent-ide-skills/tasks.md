# Tasks: Multi-Agent IDE Skill Ergonomics Refactor

**Input**: Design documents from `/specs/001-multi-agent-ide-skills/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/openapi-consolidated.yaml

**Tests**: No automated tests requested in the spec. Validation is manual per quickstart.md.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Java controllers**: `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/`
- **Skills root**: `skills/`
- **Source skill**: `skills/multi_agent_test_supervisor/`
- **Scripts shared libs**: `_client.py`, `_result.py`, `__init__.py` (copied to each new scripts dir that needs them)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Create directory structure for all four new skills and prepare shared dependencies

- [x] T001 Create skill directory structure: `mkdir -p skills/deploy_multi_agent_ide/scripts skills/multi_agent_ide_api/scripts skills/multi_agent_ide_debug skills/multi_agent_ide_controller`
- [x] T002 [P] Copy shared script utilities (`_client.py`, `_result.py`, `__init__.py`) from `skills/multi_agent_test_supervisor/scripts/` to `skills/deploy_multi_agent_ide/scripts/` and `skills/multi_agent_ide_api/scripts/`
- [x] T003 [P] Add `springdoc-openapi-starter-webmvc-ui` dependency to `multi_agent_ide_java_parent/multi_agent_ide/build.gradle.kts`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Consolidate shared Java types and restructure controller paths — MUST complete before US2 (OpenAPI annotations depend on these)

**⚠️ CRITICAL**: US2 (OpenAPI annotations + progressive disclosure) cannot begin until path restructuring and type consolidation are complete

- [x] T004 Create shared controller model package and move `NodeIdRequest` record to `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/controller/model/NodeIdRequest.java` — add `@Schema(description="...")` annotation on the class and `nodeId` field for OpenAPI. Remove duplicate local records from `LlmDebugUiController.java` and `LlmDebugRunsController.java`, update all imports
- [x] T005 Create `RunIdRequest` record in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/controller/model/RunIdRequest.java` — add `@Schema(description="...")` annotation on the class and `runId` field for OpenAPI. Remove duplicate from `LlmDebugRunsController.java` (only location — not in `OrchestrationController`), update all imports
- [x] T006 Verify `StartGoalRequest` in `OrchestrationController.java` is the canonical type used by all callers (`LlmDebugUiController`, `LlmDebugRunsController`) — refactor any local duplicates to use `OrchestrationController.StartGoalRequest`
- [x] T007 Restructure `LlmDebugUiController.java` path from `/api/llm-debug/ui` to `/api/ui` — update `@RequestMapping` in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/controller/LlmDebugUiController.java`. Note: `UiController.java` is already at `/api/ui` — verify endpoint subpaths don't conflict between the two controllers sharing the same base path
- [x] T008 [P] Restructure `LlmDebugRunsController.java` path from `/api/llm-debug/runs` to `/api/runs` — update `@RequestMapping` in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/controller/LlmDebugRunsController.java`
- [x] T009 [P] Restructure `LlmDebugPromptsController.java` path from `/api/llm-debug/prompts` to `/api/prompts` — update `@RequestMapping` in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/controller/LlmDebugPromptsController.java`

**Controllers already conforming (no changes needed)**:
- `EventStreamController`: `/api/events` ✓
- `PermissionController`: `/api/permissions` ✓
- `InterruptController`: `/api/interrupts` ✓
- `UiController`: `/api/ui` ✓
- `OrchestrationController`: `/api/orchestrator` ✓
- `AgentControlController`: `/api/agents` ✓
- `FilterPolicyController`: `/api/filters` ✓
- `PropagatorController`: `/api/propagators` ✓
- `PropagationController`: `/api/propagations/items` ✓
- `PropagationRecordController`: `/api/propagations/records` ✓
- `TransformerController`: `/api/transformers` ✓
- `TransformationRecordController`: `/api/transformations/records` ✓

**Caller impact**: The only consumer of the old `/api/llm-debug/` paths is `skills/multi_agent_test_supervisor/` (scripts and SKILL.md), which is being completely deleted and replaced by the four new skills. No frontend or external callers use these paths. No additional caller update task is needed.

**Checkpoint**: All controller paths are hierarchical, all shared types consolidated. OpenAPI annotation work can now begin.

---

## Phase 3: User Story 1 — Clone, Sync, Validate, and Deploy to Tmp (Priority: P1) 🎯 MVP

**Goal**: An operator can clone/sync the repo to `/tmp`, validate repo state via verification gate, and deploy — all with a single script command.

**Independent Test**: Run `clone_or_pull.py` against a clean `/tmp` directory for clone, then intentionally desync a submodule and run the sync path — confirm the verification gate catches it and returns an actionable error.

### Implementation for User Story 1

- [x] T010 [US1] Create `clone_or_pull.py` in `skills/deploy_multi_agent_ide/scripts/clone_or_pull.py` — implement Phase 1 (clone or sync) per research.md: `git clone --recurse-submodules`, `git submodule foreach --recursive 'git reset --hard || true'`, `mkdir -p bin`, sync path with `--ff-only`, support `--force-reclone` and `--repo-url` and `--tmp-dir` flags
- [x] T011 [US1] Implement verification gate (Phase 2) in `clone_or_pull.py` — checks: (a) no uncommitted changes via `git status --short` + submodule foreach, (b) all on `main` via `git symbolic-ref --short HEAD`, (c) collect SHAs via `git rev-parse --short HEAD`. Return structured JSON errors with per-repo remediation actions per research.md format
- [x] T012 [US1] Implement deploy delegation (Phase 3) in `clone_or_pull.py` — after verification passes, delegate to `deploy_restart.py --project-root <tmp-repo>`. Implement `--dry-run`, `--verify-only`, and `--project-root` override flags. Exit codes: 0=success, 1=clone/sync failed, 2=verification failed, 3=health-check failed
- [x] T013 [US1] Migrate `deploy_restart.py` from `skills/multi_agent_test_supervisor/scripts/deploy_restart.py` to `skills/deploy_multi_agent_ide/scripts/deploy_restart.py` — audit and update `repo_root()` path calculation (`Path(__file__).resolve().parents[3]`) for new directory depth, and verify all `__file__`-relative path references still resolve correctly
- [x] T014 [US1] Create `skills/deploy_multi_agent_ide/SKILL.md` — migrate Deploy operations, Tmp repo persistence, and Operating mode sections from `skills/multi_agent_test_supervisor/SKILL.md`. Add clone_or_pull.py usage docs, health-check instructions, `--dry-run` workflow, verification gate explanation, and log file paths returned by deploy

**Checkpoint**: US1 complete — operator can clone/sync/verify/deploy with a single command. All verification gate edge cases handled.

---

## Phase 4: User Story 2 — Progressive API Discovery via Swagger (Priority: P2)

**Goal**: Operator can progressively discover the API at 4 granularity levels using a script that reads live OpenAPI JSON. Controllers are annotated with springdoc-openapi.

**Independent Test**: Start the application, run `api_schema.py` at each granularity level (no args, `--controller runs`, `--controller runs --path /start`, `--all`), confirm output narrows correctly.

### Implementation for User Story 2

- [x] T015 [P] [US2] Add `@Tag` annotation to `LlmDebugRunsController.java` (tag: `runs`, description: "Debug run lifecycle") and add `@Operation`/`@ApiResponse`/`@Schema` annotations to all endpoints in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/controller/LlmDebugRunsController.java`
- [x] T016 [P] [US2] Add `@Tag` annotation to `LlmDebugUiController.java` and `UiController.java` (tag: `ui`, description: "UI state snapshots and actions") and add `@Operation`/`@ApiResponse`/`@Schema` annotations to all endpoints in both controllers
- [x] T017 [P] [US2] Add `@Tag` annotation to `AgentControlController.java` (tag: `agents`, description: "Agent control actions") and add `@Operation`/`@ApiResponse`/`@Schema` annotations to all endpoints in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/controller/AgentControlController.java`
- [x] T018 [P] [US2] Add `@Tag` annotation to `OrchestrationController.java` (tag: `orchestrator`, description: "Goal orchestration") and add `@Operation`/`@ApiResponse`/`@Schema` annotations to all endpoints in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/controller/OrchestrationController.java`
- [x] T019 [P] [US2] Add `@Tag` annotation to `EventStreamController.java` (tag: `events`, description: "SSE event stream") and add `@Operation` annotations in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/controller/EventStreamController.java`
- [x] T020 [P] [US2] Add `@Tag` annotations to `InterruptController.java` (tag: `interrupts`), `PermissionController.java` (tag: `permissions`), and `LlmDebugPromptsController.java` (tag: `prompts`) — add `@Operation`/`@ApiResponse` annotations to all endpoints in each
- [x] T021 [P] [US2] Add `@Tag` annotation to `FilterPolicyController.java` (tag: `filters`, description: "Data-layer filter policy CRUD") and add `@Operation`/`@ApiResponse`/`@Schema` annotations in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/controller/FilterPolicyController.java`
- [x] T022 [P] [US2] Add `@Tag` annotations to `PropagatorController.java`, `PropagationController.java`, `PropagationRecordController.java` (tag: `propagation`) — add `@Operation`/`@ApiResponse`/`@Schema` annotations in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/controller/`
- [x] T023 [P] [US2] Add `@Tag` annotations to `TransformerController.java`, `TransformationRecordController.java` (tag: `transformation`) — add `@Operation`/`@ApiResponse`/`@Schema` annotations in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/controller/`
- [x] T024 [US2] Create `api_schema.py` in `skills/multi_agent_ide_api/scripts/api_schema.py` — implement progressive disclosure: Level 0 (groups from path prefix), Level 1 (`--controller` endpoints), Level 2 (`--controller --path` full schema), Level 3 (`--all`). Support `--url`, `--format` (table/json/curl). Query `/v3/api-docs` live. Hardcode nothing about endpoints. Return clear error with exit code 1 when app is unreachable (not a Python traceback).
- [x] T025 [US2] Migrate scripts from `skills/multi_agent_test_supervisor/scripts/` to `skills/multi_agent_ide_api/scripts/`: `quick_action.py`, `filter_action.py`, `run_action.py`, `run_history.py`, `run_persistence_validate.py`, `run_start.py`, `run_status.py`, `run_timeline.py`, `ui_action.py`, `ui_state.py` — audit each script's `__file__`-relative path calculations and update for new directory location. The `multi_agent_test_supervisor` skill is being completely deleted, so all scripts must work from their new home.
- [x] T026 [US2] Create `skills/multi_agent_ide_api/SKILL.md` — migrate Script Catalog, Script schemas, UI request/response schema, Supported actions, and Prompt locations sections from `skills/multi_agent_test_supervisor/SKILL.md`. Add Swagger URL (`/swagger-ui.html`, `/v3/api-docs`), `api_schema.py` usage docs with progressive disclosure examples, endpoint quick-reference, and best practices for API interaction

**Checkpoint**: US2 complete — all controllers annotated, hierarchical paths serve progressive discovery, `api_schema.py` works at all 4 granularity levels, no duplicate schema types.

---

## Phase 5: User Story 4 — Controller Skill as Executable Workflow (Priority: P2)

**Goal**: AI agent can load the controller skill and follow its step-by-step workflow to validate, test, and improve the application. The skill is a living executable document, not a static reference card.

**Independent Test**: Load the controller skill, walk through the workflow end-to-end on a real deployment, confirm each step references the correct sub-skill and the testing matrix is complete.

### Implementation for User Story 4

- [x] T027 [US4] Create `skills/multi_agent_ide_controller/SKILL.md` — migrate Typical loop, Polling pattern, Program context, Embabel action routing semantics, and Goal tagging policy from `skills/multi_agent_test_supervisor/SKILL.md`. Structure as an executable workflow document:
  - Step-by-step "typical loop" (push/sync → deploy → start goal → poll → resolve permissions/interrupts → triage errors → redeploy)
  - Testing matrix with test suite names, approximate durations, recommended bash timeouts, key caveats
  - Polling dynamics: what to check at each interval, status evaluation (progressing / stalled / failed), distinguishing real stalls from agents waiting for input
  - References to `deploy_multi_agent_ide`, `multi_agent_ide_api`, `multi_agent_ide_debug` by name
  - Self-improvement instruction: agent flags workflow issues/improvements to user

**Checkpoint**: US4 complete — controller skill provides a walkable workflow that references sub-skills.

---

## Phase 6: User Story 3 — Debug Using Best-Practice Instructions (Priority: P3)

**Goal**: Operator loads the debug skill and follows structured instructions to triage errors using log file paths, API endpoint references, and a triage checklist.

**Independent Test**: Load the debug skill, follow the triage checklist for a simulated node error, confirm instructions point to correct log locations and API endpoints.

### Implementation for User Story 3

- [x] T028 [US3] Create `skills/multi_agent_ide_debug/SKILL.md` — migrate Node error triage, Data-Layer Filters, Propagators and Transformers, Context manager recovery model, and Session identity model sections from `skills/multi_agent_test_supervisor/SKILL.md`. Add:
  - Log file locations section: build log (`<project-root>/build-log.log`), runtime log (`<project-root>/multi-agent-ide.log`), ACP errors (`<project-root>/multi_agent_ide_java_parent/multi_agent_ide/claude-agent-acp-errs.log`), with notes on what each contains
  - Triage checklist for common error patterns (node stalls, ACP errors, context manager recovery, build failures)
  - References to `multi_agent_ide_api` endpoints (events, workflow-graph) for runtime state inspection — NOT duplicating endpoint details
  - Note that deploy script output includes log/build file paths
  - Note that missing log files mean application hasn't started — point back to deploy skill
  - Empty "Tips & Learnings" section designed to be updated over time

**Checkpoint**: US3 complete — debug skill is a self-contained instructional guide referencing API and deploy skills.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Remove source skill, validate all skills work independently, run quickstart validation

- [x] T029 Verify no content duplication across the four new skill files — each section must appear in exactly one skill per the ownership mapping in data-model.md
- [x] T030 Delete `skills/multi_agent_test_supervisor/` directory entirely — this skill is being completely replaced by the four new skills. All scripts have been migrated to their new homes, all SKILL.md content has been redistributed. Verify no other files reference it (check CLAUDE.md, other skill files, any scripts)
- [x] T031 Run quickstart.md validation checklist: (1) Swagger UI at `/swagger-ui.html`, (2) `api_schema.py` at all 4 levels, (3) `clone_or_pull.py --dry-run`, (4) each skill loads independently, (5) controller skill references all three sub-skills, (6) check `/v3/api-docs` JSON for zero duplicate schema type definitions (SC-005)
- [x] T032 Update any references to `multi_agent_test_supervisor` in `CLAUDE.md` or other project files to point to the new skill names

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on T003 (springdoc dep) — BLOCKS US2 (OpenAPI annotations need shared types + hierarchical paths)
- **US1 (Phase 3)**: Depends on T001, T002 (directory structure + shared libs) — independent of Foundational
- **US2 (Phase 4)**: Depends on Foundational (Phase 2) completion — shared types and paths must be in place
- **US4 (Phase 5)**: Depends on US1 + US2 skill files existing (references them) — can write the SKILL.md once sub-skill names are established
- **US3 (Phase 6)**: Depends on US2 skill file existing (references API endpoints from it) — can write SKILL.md once API skill is established
- **Polish (Phase 7)**: Depends on all four skills being complete

### User Story Dependencies

- **US1 (P1)**: Can start after Setup (Phase 1) — no dependency on Foundational or other stories
- **US2 (P2)**: Must wait for Foundational (Phase 2) — controller annotations require shared types and hierarchical paths
- **US4 (P2)**: Lightweight dependency on US1/US2 skill names existing (just references by name) — can be written in parallel with US2 if needed
- **US3 (P3)**: Lightweight dependency on US2 skill name (references API endpoints) — can be written in parallel with US4

### Within Each User Story

- US1: clone_or_pull.py phases (clone/sync → verify → deploy) are sequential within the script; SKILL.md can be written after script is done
- US2: All `@Tag`/`@Operation` annotation tasks (T015-T023) are parallelizable — different controller files. `api_schema.py` (T024) and script migration (T025) can also run in parallel with annotations. SKILL.md (T026) depends on all prior US2 tasks.
- US4: Single SKILL.md task, depends on source skill content
- US3: Single SKILL.md task, depends on source skill content

### Parallel Opportunities

- T002 + T003: shared lib copy and springdoc dep are independent
- T004 + T005 + T006: type consolidation tasks touch different files
- T007 + T008 + T009: path restructuring for different controllers (3 `llm-debug` controllers)
- T010-T012: sequential within clone_or_pull.py (same file)
- T013 + T014: deploy_restart.py migration and SKILL.md can overlap
- T015-T023: ALL OpenAPI annotation tasks are parallelizable (different controller files)
- T024 + T025: api_schema.py and script migration are independent
- T027 + T028: controller and debug SKILL.md files are independent

---

## Parallel Example: User Story 2

```bash
# Launch all OpenAPI annotation tasks in parallel (different files):
Task: "T015 [P] [US2] Annotate LlmDebugRunsController.java"
Task: "T016 [P] [US2] Annotate LlmDebugUiController.java + UiController.java"
Task: "T017 [P] [US2] Annotate AgentControlController.java"
Task: "T018 [P] [US2] Annotate OrchestrationController.java"
Task: "T019 [P] [US2] Annotate EventStreamController.java"
Task: "T020 [P] [US2] Annotate InterruptController + PermissionController + PromptsController"
Task: "T021 [P] [US2] Annotate FilterPolicyController.java"
Task: "T022 [P] [US2] Annotate PropagatorController + PropagationController + PropagationRecordController"
Task: "T023 [P] [US2] Annotate TransformerController + TransformationRecordController"

# Then, in parallel:
Task: "T024 [US2] Create api_schema.py"
Task: "T025 [US2] Migrate scripts from source skill"

# Finally:
Task: "T026 [US2] Create multi_agent_ide_api SKILL.md"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (directory structure + shared libs)
2. Complete Phase 3: US1 — clone/sync/verify/deploy script + deploy skill SKILL.md
3. **STOP and VALIDATE**: Test clone to clean `/tmp`, test sync with dirty submodule, test `--dry-run`, test `--verify-only`
4. Deploy from `/tmp` using the new script — confirm health check passes

### Incremental Delivery

1. Setup + US1 → clone/sync/verify/deploy works (MVP)
2. Foundational (type consolidation + path restructuring) → controllers ready for annotations
3. US2 → all controllers annotated, progressive disclosure works, API skill complete
4. US4 + US3 → controller workflow + debug guide complete (can be done in parallel)
5. Polish → remove source skill, validate no duplication, run quickstart checklist

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup together
2. Once Setup is done:
   - Developer A: US1 (clone/deploy script + skill) — independent of Foundational
   - Developer B: Foundational (type consolidation + paths) → then US2 (annotations + api_schema.py)
3. Once US1 + US2 skill files exist:
   - Developer A: US4 (controller workflow SKILL.md)
   - Developer B: US3 (debug SKILL.md)
4. Polish: anyone can do cleanup + validation

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- US1 can proceed independently of Foundational phase — it only needs directory structure
- US2 annotation tasks are the biggest parallelization opportunity (9 tasks across different files)
- SKILL.md files are pure migration + restructuring — read source skill, extract relevant sections, restructure for the target skill's domain
- Do NOT rewrite migrated content from scratch — preserve existing operational detail
- `clone_or_pull.py` structured JSON error format is specified in research.md — follow it exactly
- Commit after each task or logical group
