# Tasks: ACP Model Type Support

**Input**: Design documents from `/specs/001-acp-chatmodel/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, test_graph/src/test/resources/features/multi_agent_ide/multi_agent_ide_baseline.feature

**Tests**: Not requested in the spec; no test tasks included.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [x] T001 Add ACP provider configuration keys in /Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide/src/main/resources/application.yml
- [x] T002 Create ACP configuration properties binding in /Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/config/AcpModelProperties.java

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

- [x] T003 Implement ACP ChatModel adapter in /Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/model/acp/AcpChatModel.java
- [x] T004 Implement ACP StreamingChatModel adapter in /Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/model/acp/AcpStreamingChatModel.java
- [x] T005 Wire model provider selection for ChatModel and StreamingChatModel in /Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/config/LangChain4jConfiguration.java

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - End-to-End Orchestration Without Submodules (Priority: P1) 🎯 MVP

**Goal**: Ensure ACP selection produces the same baseline workflow for non-submodule runs.

**Independent Test**: Execute the non-submodule baseline scenario with MODEL_TYPE=acp and verify the expected events.

### Implementation for User Story 1

- [x] T006 [US1] Update baseline non-submodule scenario to set MODEL_TYPE=acp in /Users/hayde/IdeaProjects/multi_agent_ide_parent/test_graph/src/test/resources/features/multi_agent_ide/multi_agent_ide_baseline.feature
- [x] T007 [US1] Apply test configuration settings (MODEL_TYPE) in /Users/hayde/IdeaProjects/multi_agent_ide_parent/test_graph/src/test/java/com/hayden/test_graph/multi_agent_ide/step_def/MultiAgentIdeStepDefs.java
- [x] T008 [US1] Route ACP chat responses into existing agent output mapping in /Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/config/LangChain4jConfiguration.java

**Checkpoint**: User Story 1 is fully functional and testable independently

---

## Phase 4: User Story 2 - End-to-End Orchestration With Submodules (Priority: P2)

**Goal**: Ensure ACP selection works the same for submodule-enabled workflows.

**Independent Test**: Execute the submodule baseline scenario with MODEL_TYPE=acp and verify worktree and phase events.

### Implementation for User Story 2

- [x] T009 [US2] Update baseline submodule scenario to set MODEL_TYPE=acp in /Users/hayde/IdeaProjects/multi_agent_ide_parent/test_graph/src/test/resources/features/multi_agent_ide/multi_agent_ide_baseline.feature
- [x] T010 [US2] Verify ACP model selection applies to all agent phases in /Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/config/LangChain4jConfiguration.java

**Checkpoint**: User Stories 1 and 2 both work independently

---

## Phase 5: User Story 3 - Revision Cycle and Failure Handling (Priority: P3)

**Goal**: Ensure ACP failures surface through existing failure events.

**Independent Test**: Execute a revision/failure workflow with MODEL_TYPE=acp and confirm failure events are emitted.

### Implementation for User Story 3

- [x] T011 [US3] Map ACP communication failures to existing failure handling in /Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/model/acp/AcpChatModel.java
- [x] T012 [US3] Validate missing ACP configuration is handled deterministically in /Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/config/LangChain4jConfiguration.java

**Checkpoint**: All user stories are independently functional

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [x] T013 [P] Update ACP provider quickstart notes in /Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-acp-chatmodel/quickstart.md
- [x] T014 [P] Sync checklist notes if needed in /Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-acp-chatmodel/checklists/requirements.md

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: Depend on Foundational phase completion
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational - no dependencies on other stories
- **User Story 2 (P2)**: Can start after Foundational - no dependency on US1
- **User Story 3 (P3)**: Can start after Foundational - no dependency on US1/US2

### Within Each User Story

- Model adapter work before provider selection validation
- Feature file configuration before running integration scenarios

### Parallel Opportunities

- T003 and T004 can run in parallel once T001–T002 complete
- T013 and T014 can run in parallel after user stories

---

## Parallel Example: User Story 1

```bash
Task: "Update baseline non-submodule scenario to set MODEL_TYPE=acp in test_graph/src/test/resources/features/multi_agent_ide/multi_agent_ide_baseline.feature"
Task: "Apply test configuration settings (MODEL_TYPE) in test_graph/src/test/java/com/hayden/test_graph/multi_agent_ide/step_def/MultiAgentIdeStepDefs.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1
4. Validate baseline non-submodule workflow with MODEL_TYPE=acp

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Validate independently
3. Add User Story 2 → Validate independently
4. Add User Story 3 → Validate independently
5. Finish polish updates
