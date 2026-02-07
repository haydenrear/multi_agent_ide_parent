# Tasks: CLI mode interactive event rendering

**Input**: Design documents from `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-cli-mode-events/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/, quickstart.md

**Tests**: Not requested in the spec; no automated test tasks included.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: CLI scaffolding and configuration baseline

- [X] T001 Create synchronized CLI output helper in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/cli/CliOutputWriter.java`
- [X] T002 [P] Add CLI profile config file `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/resources/application-cli.yml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core CLI wiring required by all user stories

- [X] T003 Add CLI Spring configuration with `cli` profile beans in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/config/CliModeConfig.java`
- [X] T004 Implement base event formatting + unknown-event fallback in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/cli/CliEventFormatter.java`

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - Run a goal from the CLI (Priority: P1) 🎯 MVP

**Goal**: Prompt for a goal, start execution, and render the event stream (including tool calls) in CLI mode only.

**Independent Test**: Start with `--spring.profiles.active=cli`, enter a goal, and observe event output without GUI.

### Implementation for User Story 1

- [X] T005 [US1] Implement CLI goal runner (prompt + start goal) in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/cli/CliGoalRunner.java`
- [X] T006 [US1] Add goal validation + re-prompt + error handling in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/cli/CliGoalRunner.java`
- [X] T007 [US1] Implement CLI event listener wired to the event bus in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/cli/CliEventListener.java`
- [X] T008 [US1] Add tool-call rendering in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/cli/CliEventFormatter.java`

**Checkpoint**: CLI goal run works and tool calls render in CLI output

---

## Phase 4: User Story 2 - Handle interrupts interactively (Priority: P1)

**Goal**: Prompt for interrupt input and resume workflows from the CLI.

**Independent Test**: Run a goal that triggers interrupts and confirm input resumes execution.

### Implementation for User Story 2

- [X] T009 [US2] Implement CLI interrupt/permission polling loop using `PermissionGateAdapter` in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/cli/CliInteractionLoop.java`
- [X] T010 [US2] Wire interrupt loop into CLI startup flow in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/cli/CliGoalRunner.java`

**Checkpoint**: Interrupt prompts appear and resume events are rendered

---

## Phase 5: User Story 3 - See action lifecycle with hierarchy (Priority: P2)

**Goal**: Render action started/completed events with artifact-key hierarchy context.

**Independent Test**: Run a goal with multiple actions and verify hierarchy context is visible.

### Implementation for User Story 3

- [X] T011 [US3] Add artifact-key hierarchy formatter in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/cli/ArtifactKeyFormatter.java`
- [X] T012 [US3] Render action-started/action-completed with hierarchy context in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/cli/CliEventFormatter.java`

**Checkpoint**: Action lifecycle events show hierarchy derived from artifact keys

---

## Phase 6: User Story 4 - See diverse event types in the CLI (Priority: P2)

**Goal**: Render major event categories defined in Events.java.

**Independent Test**: Run a goal that emits multiple event categories and verify each is rendered.

### Implementation for User Story 4

- [X] T013 [US4] Map and render node/interrupt/permission/worktree/plan/UI/user-message events in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/cli/CliEventFormatter.java`
- [X] T014 [US4] Add category labels/ordering and fallback formatting in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/cli/CliEventListener.java`

**Checkpoint**: CLI renders diverse event categories with consistent formatting

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Documentation and manual validation

- [X] T015 [P] Update and validate CLI instructions in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-cli-mode-events/quickstart.md`
- [X] T016 [P] Add CLI-mode usage note in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/resources/application.yml`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
- **Polish (Phase 7)**: Depends on all desired user stories being complete

### User Story Dependencies

- **US1 (P1)**: Can start after Foundational - no dependencies on other stories
- **US2 (P1)**: Can start after Foundational - integrates with US1 runtime flow
- **US3 (P2)**: Can start after Foundational - builds on event rendering
- **US4 (P2)**: Can start after Foundational - extends event rendering coverage

### Parallel Opportunities

- T001 and T002 can run in parallel
- US3 and US4 can be implemented in parallel after US1/US2 baseline rendering is in place
- T015 and T016 can run in parallel after implementation stabilizes

---

## Parallel Example: User Story 1

```bash
Task: "Implement CLI goal runner (prompt + start goal) in /Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/cli/CliGoalRunner.java"
Task: "Implement CLI event listener wired to the event bus in /Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/cli/CliEventListener.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1
4. Validate CLI goal prompt + event rendering manually (tests not requested)
5. Stop and confirm MVP behavior

### Incremental Delivery

1. Setup + Foundational → CLI foundation ready
2. Add US1 → verify manual run
3. Add US2 → verify interrupt handling
4. Add US3 → verify action hierarchy rendering
5. Add US4 → verify broader event coverage
6. Polish documentation

---

## Notes

- No test_graph tasks included because the spec explicitly deferred test_graph work.
- Unknown or new event types must continue to render via the fallback formatter.
