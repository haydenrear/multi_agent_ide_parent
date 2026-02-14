# Tasks: LLM Debug UI Skill and Shared UI Abstraction

**Input**: Design documents from `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-multi-agent-test-supervisor/`

**Prerequisites**: `plan.md` (required), `spec.md` (required), `research.md`, `data-model.md`, `contracts/`

**Tests**: Test tasks are included because UI parity and script loop behavior are explicit acceptance needs.

**Organization**: Tasks are grouped by user story so each story can be implemented and validated independently.

## Format: `[ID] [P?] [Story] Description`

## Phase 1: Setup (Skill + Spec Scaffolding)

**Purpose**: Create and organize skill and feature documentation structure.

- [x] T001 Create skill package structure in `skills/multi_agent_test_supervisor/{scripts,references,assets,tests}`
- [x] T002 [P] Create base script helper modules in `skills/multi_agent_test_supervisor/scripts/_client.py` and `skills/multi_agent_test_supervisor/scripts/_result.py`
- [x] T003 [P] Add per-script schema references in `skills/multi_agent_test_supervisor/references/deploy_restart.schema.md`, `skills/multi_agent_test_supervisor/references/quick_action.schema.md`, `skills/multi_agent_test_supervisor/references/ui_state.schema.md`, and `skills/multi_agent_test_supervisor/references/ui_action.schema.md`
- [x] T004 Record deferred `test_graph` mapping note in `specs/001-multi-agent-test-supervisor/testgraph-deferred.md`

---

## Phase 2: Foundational (Shared UI Abstraction)

**Purpose**: Establish shared, interface-neutral state and reducer infrastructure.

**⚠️ CRITICAL**: User story work depends on this phase.

- [x] T005 Rename and adopt shared state records in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/ui/state/UiState.java` and `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/ui/state/UiSessionState.java`
- [x] T006 Refactor reducer to shared naming in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/ui/state/UiStateReducer.java`
- [x] T007 [P] Implement shared snapshot/action contracts in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/ui/shared/UiStateSnapshot.java`, `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/ui/shared/UiSessionSnapshot.java`, and `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/ui/shared/UiActionCommand.java`
- [x] T008 [P] Implement materialized UI state store in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/ui/shared/UiStateStore.java`
- [x] T009 Route TUI adapter through shared abstraction in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/tui/TuiSession.java`

---

## Phase 3: User Story 1 - Shared UI Abstraction Parity (Priority: P1) 🎯 MVP

**Goal**: TUI and LLM-facing UI interfaces apply the same state/action semantics.

**Independent Test**: Equivalent interactions through TUI and LLM endpoints produce semantically equivalent state outcomes.

- [x] T010 [P] [US1] Add shared reducer/service tests in `multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/ui/shared/SharedUiInteractionServiceTest.java`
- [x] T011 [US1] Implement materialized state endpoint in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/controller/LlmDebugUiController.java`
- [x] T012 [US1] Implement UI action endpoint in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/controller/LlmDebugUiController.java`
- [x] T013 [US1] Ensure node-scope state/event behavior is driven without replay in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/ui/shared/UiStateStore.java` and `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/controller/LlmDebugUiController.java`
- [x] T014 [US1] Add parity integration coverage in `multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/integration/UiParityIntegrationTest.java`

---

## Phase 4: User Story 2 - Script-First Debug Loop (Priority: P1)

**Goal**: Provide deploy/start/poll/expand/state/action script loop for LLM-driven debugging.

**Independent Test**: Each script command validates args, emits JSON contracts, and maps to the intended endpoint/process operation.

- [x] T015 [US2] Add goal start, quick action, and event query endpoints in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/controller/LlmDebugUiController.java`
- [x] T016 [US2] Restrict run action support to SEND_MESSAGE and mark other run actions TODO in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/service/DebugRunQueryService.java`
- [x] T017 [P] [US2] Implement deploy/restart process script in `skills/multi_agent_test_supervisor/scripts/deploy_restart.py`
- [x] T018 [P] [US2] Implement quick agent operation script in `skills/multi_agent_test_supervisor/scripts/quick_action.py`
- [x] T019 [P] [US2] Implement UI operation scripts in `skills/multi_agent_test_supervisor/scripts/ui_state.py` and `skills/multi_agent_test_supervisor/scripts/ui_action.py`
- [x] T020 [US2] Update skill command catalog and scope in `skills/multi_agent_test_supervisor/SKILL.md`
- [x] T021 [US2] Update script matrix and scope notes in `skills/multi_agent_test_supervisor/references/scripts.md`
- [x] T022 [US2] Add script command contract tests in `skills/multi_agent_test_supervisor/tests/test_scripts.py`

---

## Phase 5: User Story 3 - Built-In Program and Routing Context (Priority: P1)

**Goal**: Ship reusable routing/prompt-context references so LLMs can interpret workflow execution and safely evolve prompts without re-deriving internals each run.

**Independent Test**: Referenced docs explain routing, prompt locations, and contributor/decorator extension behavior sufficiently for event interpretation and prompt updates.

- [x] T023 [P] [US3] Add workflow routing/program model reference (including `AgentInterfaces`, `AgentModels`, `BlackboardRoutingPlanner`, `ContextManagerTools`, and `BlackboardHistory`) in `skills/multi_agent_test_supervisor/references/program_model.md`
- [x] T024 [P] [US3] Add we-are-here contributor reference with subgraph intuition and branch guidance in `skills/multi_agent_test_supervisor/references/we_are_here_prompt.md`
- [x] T025 [P] [US3] Add prompt architecture and evolution reference in `skills/multi_agent_test_supervisor/references/prompt_architecture.md`
- [x] T026 [US3] Link routing/planner/context-manager and prompt references in `skills/multi_agent_test_supervisor/SKILL.md` and `skills/multi_agent_test_supervisor/references/scripts.md`

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Bring contracts/spec artifacts in sync with implemented scope.

- [x] T027 [P] Update API contract to UI-first scope in `specs/001-multi-agent-test-supervisor/contracts/llm-debug-ui-openapi.yaml`
- [x] T028 [P] Align feature artifacts with current scope in `specs/001-multi-agent-test-supervisor/spec.md`, `specs/001-multi-agent-test-supervisor/plan.md`, `specs/001-multi-agent-test-supervisor/research.md`, `specs/001-multi-agent-test-supervisor/data-model.md`, and `specs/001-multi-agent-test-supervisor/quickstart.md`
- [x] T029 Update completion evidence and command outcomes in `specs/001-multi-agent-test-supervisor/checklists/implementation.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: starts immediately.
- **Phase 2 (Foundational)**: depends on Phase 1 and blocks user stories.
- **Phase 3 (US1)**: depends on Phase 2.
- **Phase 4 (US2)**: depends on Phase 2 and uses US1 endpoints for full loop behavior.
- **Phase 5 (US3)**: depends on Phase 1 and is validated alongside US2 operational usage.
- **Phase 6 (Polish)**: depends on selected story completion.

### User Story Dependencies

- **US1 (P1)**: establishes abstraction parity baseline.
- **US2 (P1)**: operational debug loop on top of shared abstraction.
- **US3 (P1)**: interpretation context used during US2 event analysis.

### test_graph Mapping Status

- Cucumber tags from `spec.md` (`@ui_abstraction_parity`, `@script_interface`, `@workflow_context_docs`) currently have no mapped feature files in `test_graph`.
- `test_graph` implementation remains intentionally deferred in this ticket and tracked by `specs/001-multi-agent-test-supervisor/testgraph-deferred.md`.

---

## Parallel Execution Examples

### User Story 1

- Run in parallel: T010 and T011.
- Run in parallel: T012 and T013.

### User Story 2

- Run in parallel: T017, T018, and T019.
- Run in parallel: T020 and T021.

### User Story 3

- Run in parallel: T023, T024, and T025.

---

## Implementation Strategy

### MVP First (User Story 1)

1. Complete Phase 1 and Phase 2.
2. Deliver US1 parity endpoints + tests.
3. Validate node-scoped materialized state behavior.

### Incremental Delivery

1. Add US2 script loop for deploy/start/poll/inspect/action.
2. Add US3 routing + prompt-architecture docs to improve interpretation and prompt evolution quality.
3. Complete Polish phase to lock contracts/spec alignment.

### Team Parallelization

1. One engineer owns shared abstraction + controller integration.
2. One engineer owns skill scripts + schema docs.
3. One engineer owns routing/program references and doc polish.
