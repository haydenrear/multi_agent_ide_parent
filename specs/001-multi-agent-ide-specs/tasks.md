---

description: "Task list for Multi-Agent IDE baseline tests"
---

# Tasks: Multi-Agent IDE Baseline Tests

**Input**: Design documents from `/specs/001-multi-agent-ide-specs/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, test_graph/src/test/resources/features/multi_agent_ide

**Tests**: Tests are REQUIRED (goal is to finalize analysis and testing for orchestrator end-to-end behavior).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and verification

- [X] T001 Verify Spring Boot test wiring and agent mocks in multi_agent_ide/src/test/java/com/hayden/multiagentide/support/AgentTestBase.java
- [X] T002 [P] Review event bus/test listener behavior in multi_agent_ide/src/test/java/com/hayden/multiagentide/support/TestEventListener.java
- [X] T003 [P] Review multi_agent_ide test resources wiring in multi_agent_ide/src/test/resources

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared test scaffolding for orchestration flows

- [X] T004 Add helper assertions/utilities for event filtering in multi_agent_ide/src/test/java/com/hayden/multiagentide/support/TestEventListener.java
- [X] T005 Add shared test helpers for orchestrator setup in multi_agent_ide/src/test/java/com/hayden/multiagentide/workflow/OrchestratorEndToEndTest.java

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - End-to-End Orchestration Without Submodules (Priority: P1)

**Goal**: Validate full orchestration flow without submodules.

**Independent Test**: Orchestrator completes; no submodule worktree events emitted.

### Tests for User Story 1

- [X] T006 [US1] Expand orchestrator success assertions in multi_agent_ide/src/test/java/com/hayden/multiagentide/workflow/OrchestratorEndToEndTest.java for main-only worktree events
- [X] T007 [US1] Add explicit node lifecycle/event assertions in multi_agent_ide/src/test/java/com/hayden/multiagentide/workflow/OrchestratorEndToEndTest.java for discovery/planning/review phases

---

## Phase 4: User Story 2 - End-to-End Orchestration With Submodules (Priority: P2)

**Goal**: Validate orchestration flow with submodule worktrees and merge events.

**Independent Test**: Orchestrator completes; main and submodule worktree events emitted.

### Tests for User Story 2

- [X] T008 [US2] Expand submodule coverage assertions in multi_agent_ide/src/test/java/com/hayden/multiagentide/workflow/OrchestratorEndToEndTest.java
- [X] T009 [US2] Add merge event assertions for main and submodule worktrees in multi_agent_ide/src/test/java/com/hayden/multiagentide/workflow/OrchestratorEndToEndTest.java

---

## Phase 5: User Story 3 - Revision Cycle and Failure Handling (Priority: P3)

**Goal**: Validate revision cycles and failure behavior in orchestration.

**Independent Test**: Review rejection or failure yields proper events and status transitions.

### Tests for User Story 3

- [X] T010 [US3] Add review-revision flow test coverage in multi_agent_ide/src/test/java/com/hayden/multiagentide/workflow/OrchestratorEndToEndTest.java
- [X] T011 [US3] Add failure path assertions with error metadata in multi_agent_ide/src/test/java/com/hayden/multiagentide/workflow/OrchestratorEndToEndTest.java

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final validation and cleanup

- [X] T012 [P] Align test expectations with gherkin scenarios in test_graph/src/test/resources/features/multi_agent_ide/multi_agent_ide_baseline.feature
- [X] T013 Run ./gradlew :multi_agent_ide:test and fix any failing tests in multi_agent_ide/src/test/java/com/hayden/multiagentide/workflow/OrchestratorEndToEndTest.java

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
- **Polish (Phase 6)**: Depends on all user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2)
- **User Story 2 (P2)**: Can start after Foundational (Phase 2)
- **User Story 3 (P3)**: Can start after Foundational (Phase 2)

### Parallel Opportunities

- T002 and T003 can run in parallel
- T006 and T007 can run in parallel once Phase 2 completes
- T008 and T009 can run in parallel once Phase 2 completes
- T010 and T011 can run in parallel once Phase 2 completes

---

## Parallel Example: User Story 1

```bash
Task: "Expand orchestrator success assertions in multi_agent_ide/src/test/java/com/hayden/multiagentide/workflow/OrchestratorEndToEndTest.java for main-only worktree events"
Task: "Add explicit node lifecycle/event assertions in multi_agent_ide/src/test/java/com/hayden/multiagentide/workflow/OrchestratorEndToEndTest.java for discovery/planning/review phases"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1
4. Validate with ./gradlew :multi_agent_ide:test

### Incremental Delivery

1. Complete Setup + Foundational
2. Add User Story 1 → validate
3. Add User Story 2 → validate
4. Add User Story 3 → validate
5. Align with test_graph feature expectations
