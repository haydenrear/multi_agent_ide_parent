# Tasks: Worktree Sandbox for Agent File Operations

**Input**: Design documents from `/specs/009-worktree-sandbox/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, test_graph/src/test/resources/features/worktree-sandbox

**Tests**: Tests are OPTIONAL. This plan includes test_graph tasks because Cucumber tags are specified in the feature spec.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Validate module wiring for new components

- [ ] T001 Review/adjust module dependencies for new sandbox/context classes in `multi_agent_ide_java_parent/multi_agent_ide/build.gradle.kts` and `multi_agent_ide_java_parent/multi_agent_ide_lib/build.gradle.kts` and `multi_agent_ide_java_parent/acp-cdc-ai/build.gradle.kts`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure required before user story work

- [ ] T002 Create `RequestContext` record with @With support in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/repository/RequestContext.java`
- [ ] T003 Create `RequestContextRepository` interface in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/repository/RequestContextRepository.java`
- [ ] T004 Implement in-memory repository in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/repository/InMemoryRequestContextRepository.java`
- [ ] T005 Wire repository bean in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/config/RequestContextConfig.java`
- [ ] T006 [P] Add sandbox validation result model in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/model/worktree/SandboxValidationResult.java`
- [ ] T007 [P] Add worktree sandbox validation utility in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/model/worktree/WorktreeSandbox.java`
- [ ] T008 [P] Add sandbox translation SPI + registry in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/sandbox/SandboxTranslationStrategy.java` and `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/sandbox/SandboxTranslationRegistry.java`
- [ ] T009 [P] Add provider translation strategies in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/sandbox/ClaudeCodeSandboxStrategy.java`, `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/sandbox/CodexSandboxStrategy.java`, and `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/sandbox/GooseSandboxStrategy.java`

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - Worktree Information Propagation (Priority: P1) 🎯 MVP

**Goal**: Attach worktree context to all agent requests via decorators

**Independent Test**: Initialize orchestrator with worktree and confirm downstream requests include worktree context

### Implementation for User Story 1

- [ ] T010 [P] [US1] Add worktree context field to all AgentRequest records in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AgentModels.java`
- [ ] T011 [US1] Implement worktree propagation decorator in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/decorator/WorktreeContextRequestDecorator.java`
- [ ] T012 [US1] Persist request context in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/decorator/RequestContextRepositoryDecorator.java`
- [ ] T013 [US1] Register new decorators in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java`

### test_graph for User Story 1

- [ ] T014 [US1] Add worktree propagation scenarios in `test_graph/src/test/resources/features/worktree-sandbox/worktree-propagation.feature`
- [ ] T015 [US1] Implement step definitions in `test_graph/src/test/java/com/hayden/test_graph/steps/worktree/WorktreeSandboxSteps.java`

**Checkpoint**: US1 requests carry worktree context end-to-end

---

## Phase 4: User Story 2 - File System Read Sandboxing (Priority: P1)

**Goal**: Block read operations outside assigned worktree

**Independent Test**: Read inside worktree succeeds; read outside returns sandbox error JSON

### Implementation for User Story 2

- [ ] T016 [US2] Implement read path normalization + allowlist checks in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/model/worktree/WorktreeSandbox.java`
- [ ] T017 [US2] Enforce read sandbox in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AcpTooling.java`

### test_graph for User Story 2

- [ ] T018 [US2] Add sandbox read scenarios in `test_graph/src/test/resources/features/worktree-sandbox/sandbox-read.feature`
- [ ] T019 [US2] Extend step definitions in `test_graph/src/test/java/com/hayden/test_graph/steps/worktree/WorktreeSandboxSteps.java`

**Checkpoint**: Read operations are restricted to the worktree

---

## Phase 5: User Story 3 - File System Write Sandboxing (Priority: P1)

**Goal**: Block write operations outside assigned worktree

**Independent Test**: Write inside worktree succeeds; write outside returns sandbox error JSON

### Implementation for User Story 3

- [ ] T020 [US3] Enforce write sandbox in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AcpTooling.java`

### test_graph for User Story 3

- [ ] T021 [US3] Add sandbox write scenarios in `test_graph/src/test/resources/features/worktree-sandbox/sandbox-write.feature`
- [ ] T022 [US3] Extend step definitions in `test_graph/src/test/java/com/hayden/test_graph/steps/worktree/WorktreeSandboxSteps.java`

**Checkpoint**: Write operations are restricted to the worktree

---

## Phase 6: User Story 4 - File System Edit Sandboxing (Priority: P1)

**Goal**: Block edit operations outside assigned worktree

**Independent Test**: Edit inside worktree succeeds; edit outside returns sandbox error JSON

### Implementation for User Story 4

- [ ] T023 [US4] Enforce edit sandbox in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AcpTooling.java`

### test_graph for User Story 4

- [ ] T024 [US4] Add sandbox edit scenarios in `test_graph/src/test/resources/features/worktree-sandbox/sandbox-edit.feature`
- [ ] T025 [US4] Extend step definitions in `test_graph/src/test/java/com/hayden/test_graph/steps/worktree/WorktreeSandboxSteps.java`

**Checkpoint**: Edit operations are restricted to the worktree

---

## Phase 7: User Story 7 - Ticket Orchestrator Worktree Creation (Priority: P1)

**Goal**: Ticket orchestrator creates per-ticket worktrees and passes them downstream

**Independent Test**: Ticket agents receive unique worktree paths created alongside parent worktree

### Implementation for User Story 7

- [ ] T026 [US7] Add ticket worktree prompt contributor in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/prompt/TicketOrchestratorWorktreePromptContributor.java`
- [ ] T027 [US7] Register prompt contributor in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/config/PromptContributorConfig.java`
- [ ] T028 [US7] Create per-ticket worktrees in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/WorkflowGraphService.java`

### test_graph for User Story 7

- [ ] T029 [US7] Add ticket worktree scenarios in `test_graph/src/test/resources/features/worktree-sandbox/ticket-worktree-creation.feature`
- [ ] T030 [US7] Extend step definitions in `test_graph/src/test/java/com/hayden/test_graph/steps/worktree/WorktreeSandboxSteps.java`

**Checkpoint**: Ticket orchestrator creates and propagates worktree context

---

## Phase 8: User Story 5 - Header-Based Worktree Resolution (Priority: P2)

**Goal**: Resolve worktree context using session header for tooling and ACP provider options

**Independent Test**: Invalid header blocks ops with clear error; valid header configures sandbox options

### Implementation for User Story 5

- [ ] T031 [US5] Add session header lookup + error handling in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AcpTooling.java`
- [ ] T032 [US5] Translate sandbox context into provider options in `multi_agent_ide_java_parent/acp-cdc-ai/src/main/kotlin/com/hayden/acp_cdc_ai/acp/AcpChatModel.kt`

### test_graph for User Story 5

- [ ] T033 [US5] Add header resolution scenarios in `test_graph/src/test/resources/features/worktree-sandbox/header-resolution.feature`
- [ ] T034 [US5] Extend step definitions in `test_graph/src/test/java/com/hayden/test_graph/steps/worktree/WorktreeSandboxSteps.java`

**Checkpoint**: Header-based resolution drives sandbox configuration

---

## Phase 9: User Story 6 - Submodule Worktree Handling (Priority: P2)

**Goal**: Allow access to designated submodule worktrees within sandbox

**Independent Test**: Access to assigned submodule succeeds; other submodules blocked

### Implementation for User Story 6

- [ ] T035 [US6] Populate submodule worktree context in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/decorator/WorktreeContextRequestDecorator.java`
- [ ] T036 [US6] Allow submodule paths in sandbox checks in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/model/worktree/WorktreeSandbox.java`

### test_graph for User Story 6

- [ ] T037 [US6] Add submodule sandbox scenarios in `test_graph/src/test/resources/features/worktree-sandbox/submodule-sandbox.feature`
- [ ] T038 [US6] Extend step definitions in `test_graph/src/test/java/com/hayden/test_graph/steps/worktree/WorktreeSandboxSteps.java`

**Checkpoint**: Submodule worktree access is enforced correctly

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: Documentation and final verification

- [ ] T039 [P] Update workflow documentation in `multi_agent_ide_java_parent/multi_agent_ide/WORKFLOWS.md`
- [ ] T040 Run quickstart validation steps in `specs/009-worktree-sandbox/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **US1 (P1)**: Can start after Foundational - no dependencies
- **US2/US3/US4 (P1)**: Depend on WorktreeSandbox foundations (T006/T007)
- **US7 (P1)**: Depends on US1 context propagation
- **US5 (P2)**: Depends on RequestContextRepository + sandbox strategies (T002–T009)
- **US6 (P2)**: Depends on US1 and WorktreeSandbox implementation

### Parallel Opportunities

- T006–T009 can run in parallel
- Story-specific test_graph feature files can be created in parallel
- US2/US3/US4 tasks can proceed in parallel after WorktreeSandbox checks exist

---

## Parallel Example: User Story 2

```bash
Task: "Add sandbox read scenarios in test_graph/src/test/resources/features/worktree-sandbox/sandbox-read.feature"
Task: "Extend step definitions in test_graph/src/test/java/com/hayden/test_graph/steps/worktree/WorktreeSandboxSteps.java"
```
