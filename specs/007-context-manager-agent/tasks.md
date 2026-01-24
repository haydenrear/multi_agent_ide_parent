# Tasks: Context Manager Agent

**Feature**: Context Manager Agent with Blackboard History Tooling
**Branch**: `007-context-manager-agent`

## Phase 1: Setup
**Goal**: Initialize project structure and core model definitions.
**Independent Test**: N/A - compile only.

- [ ] T001 Define `ContextManagerRequest` and `ContextManagerResultRouting` in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AgentModels.java`
- [ ] T002 Define `DegenerateLoopException` in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/events/DegenerateLoopException.java`
- [ ] T003 [P] Add `HistoryNote` record to `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/BlackboardHistory.java`
- [ ] T004 Define Blackboard Tool request/response records in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AgentModels.java`

## Phase 2: Foundational
**Goal**: Core infrastructure changes for loop detection and history tracking.
**Independent Test**: Unit tests for loop detection and event subscription.

- [ ] T005 [P] Implement `EventSubscriber` interface in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/BlackboardHistory.java`
- [ ] T006 Implement loop detection logic and threshold configuration in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/BlackboardHistory.java`
- [ ] T007 [P] Create `ContextManagerTools` class in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/ContextManagerTools.java` skeleton
- [ ] T008 [P] Implement `StuckHandler` interface in `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java` (WorkflowAgent)

## Phase 3: User Stories 1-4 (History Inspection Tools)
**Goal**: Enable reading and searching history.
**Independent Test**: Unit tests for each tool method.

- [ ] T009 [US1] Implement `trace` method in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/ContextManagerTools.java`
- [ ] T010 [US2] Implement `list` method with pagination in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/ContextManagerTools.java`
- [ ] T011 [US3] Implement `search` method in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/ContextManagerTools.java`
- [ ] T012 [US4] Implement `getItem` method in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/ContextManagerTools.java`
- [ ] T013 [US1] Create unit tests for inspection tools in `multi_agent_ide_lib/src/test/java/com/hayden/multiagentidelib/agent/ContextManagerToolsTest.java`

## Phase 4: User Story 6 (Notes)
**Goal**: Enable creating notes.
**Independent Test**: Verify note attachment.

- [ ] T014 [US6] Implement `addNote` method in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/ContextManagerTools.java`
- [ ] T015 [US6] Update `BlackboardHistory` to store and retrieve notes in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/BlackboardHistory.java`
- [ ] T016 [US6] Add unit tests for note tools in `multi_agent_ide_lib/src/test/java/com/hayden/multiagentidelib/agent/ContextManagerToolsTest.java`

## Phase 5: User Stories 7-8 (Events & Loop Recovery)
**Goal**: Capture events and handle stuck states.
**Independent Test**: Simulate loop and verify exception → handler → context manager flow.

- [ ] T018 [US7] Implement event capture logic in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/BlackboardHistory.java`
- [ ] T019 [US8] Update `BlackboardHistory` to throw `DegenerateLoopException` on threshold in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/BlackboardHistory.java`
- [ ] T020 [US8] Implement `handleStuck` in `WorkflowAgent` to invoke `llmRunner` in `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java`
- [ ] T021 [US8] Create `ContextManagerAgent` class in `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/ContextManagerAgent.java`
- [ ] T022 [US8] Implement `workflow/context_manager_recovery` prompt template (location TBD, likely `prompts/` or resource file)

## Phase 6: User Stories 9-12 (Explicit Routing & Polish)
**Goal**: Enable agents to request context and finalize integration.
**Independent Test**: Verify explicit routing requests.

- [ ] T023 [US9] Update `AgentModels` routing records to include `ContextManagerRequest` option in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AgentModels.java`
- [ ] T024 [US10] Implement Review/Merge routing to Context Manager in `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java`
- [ ] T025 [US11] Verify hung state handling in `WorkflowAgent` (reuses loop logic) in `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java`
- [ ] T026 [US12] Ensure Context Manager uses note tool for reasoning in `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/ContextManagerAgent.java`

## Phase 7: Polish
**Goal**: Final verification and cleanup.

- [ ] T027 Run full suite of unit tests for Context Manager features
- [ ] T028 Verify `spec.md` requirements coverage
- [ ] T029 Clean up any temporary files or TODOs

## Dependencies

- Phase 1 & 2 must complete before Phase 3-6.
- Phase 3 & 4 can run in parallel.
- Phase 5 requires Phase 1 & 2.
- Phase 6 requires Phase 1.

## Implementation Strategy
Start with the data models and `BlackboardHistory` tools (Phases 1-4) as they are the core capability. Then implement the reactive parts (events, loop detection) and finally the agent logic and routing.
