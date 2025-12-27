# Tasks: Collector Orchestrator Routing

**Input**: Design documents from `/specs/001-collector-orchestrator-routing/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, test_graph/src/test/resources/features/multi_agent_ide

**Tests**: Not explicitly requested; only update test_graph assets.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Ensure specification and test assets are aligned before implementation

- [X] T001 Align user stories and tags in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-collector-orchestrator-routing/spec.md`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Shared model and agent plumbing required for all stories

- [X] T002 [P] Add collector decision type and ticket collector result in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentModels.java`
- [X] T003 [P] Add collector mixin and collected node snapshot type in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/model/nodes/Collector.java` and `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/model/nodes/CollectedNodeStatus.java`
- [X] T004 [P] Add ticket collector node model in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/model/nodes/TicketCollectorNode.java`
- [X] T005 Update node permits and collector node fields to implement Collector and store collected nodes/decisions in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/model/nodes/GraphNode.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/model/nodes/Viewable.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/model/nodes/CollectorNode.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/model/nodes/DiscoveryCollectorNode.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/model/nodes/PlanningCollectorNode.java`
- [X] T006 Add ticket collector agent wiring and updated result casting in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/config/LangChain4jConfiguration.java`
- [X] T007 Add ticket collector lifecycle hooks in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentLifecycleHandler.java`

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - Collector Routing Decisions (Priority: P1) 🎯 MVP

**Goal**: Collect child node status snapshots, include routing decisions in collector responses, and route back to orchestrators when required.

**Independent Test**: Run the workflow and verify collector completion triggers a rerun or phase advance based on the decision, using `@collector_orchestrator_reentry` in `test_graph/src/test/resources/features/multi_agent_ide/collector_orchestrator_routing.feature`.

### Implementation for User Story 1

- [X] T008 [US1] Add ticket collector interface and update collector prompts to request routing decisions in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java`
- [X] T009 [US1] Capture collected child node status snapshots on collector execution in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/infrastructure/AgentRunner.java`
- [X] T010 [US1] Add helper for building collected node snapshots in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/infrastructure/AgentRunner.java`
- [X] T011 [US1] Create and execute ticket collector after ticket workflow completion in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/infrastructure/AgentRunner.java`
- [X] T012 [US1] Apply collector routing decision to rerun or advance phases in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/infrastructure/AgentRunner.java`
- [X] T013 [US1] Route collector completion events through decision handling in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/infrastructure/AgentEventListener.java`
- [X] T014 [US1] Update mock responses with collector decisions in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/test_graph/src/test/resources/multi_agent_ide/collector_orchestrator_routing.json`

**Checkpoint**: Collector routing decisions work end-to-end for discovery, planning, ticket, and orchestrator collectors

---

## Phase 4: User Story 2 - Human Review Gate for Collectors (Priority: P2)

**Goal**: Allow optional human review to pause collector completion and apply reviewer decisions before routing.

**Independent Test**: Run `@collector_human_review_gate` in `test_graph/src/test/resources/features/multi_agent_ide/collector_orchestrator_routing.feature` and verify review request pauses execution and applies the chosen action.

### Implementation for User Story 2

- [X] T015 [US2] Add collector review gate metadata handling in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/infrastructure/AgentRunner.java`
- [X] T016 [US2] Emit review requests and apply review decisions to routing in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/infrastructure/AgentRunner.java`
- [X] T017 [US2] Ensure review events are emitted for collector review requests in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/orchestration/ComputationGraphOrchestrator.java`
- [X] T018 [US2] Update feature scenario expectations in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/test_graph/src/test/resources/features/multi_agent_ide/collector_orchestrator_routing.feature`

**Checkpoint**: Collector review gate can pause and redirect routing without bypassing orchestrators

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Keep docs and integration assets current

- [X] T019 [P] Update quickstart validation notes in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-collector-orchestrator-routing/quickstart.md`
- [X] T020 Confirm no new step definitions are needed or update shared steps in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/test_graph/src/test/java/com/hayden/test_graph/multi_agent_ide/step_def/MultiAgentIdeStepDefs.java`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - blocks all user stories
- **User Story 1 (Phase 3)**: Depends on Foundational completion
- **User Story 2 (Phase 4)**: Depends on Foundational completion; can run after US1 if routing primitives are stable
- **Polish (Phase 5)**: Depends on desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational - no dependency on US2
- **User Story 2 (P2)**: Can start after Foundational - relies on collector decision plumbing from US1

### Parallel Opportunities

- Foundational tasks T002-T004 can run in parallel
- US1 tasks T009 and T010 can run in parallel with interface/prompt updates (T008)
- Polish tasks T019 and T020 can run in parallel

---

## Parallel Example: User Story 1

```bash
Task: "Add ticket collector interface and update collector prompts" (AgentInterfaces.java)
Task: "Capture collected child node status snapshots" (AgentRunner.java)
Task: "Update mock responses with collector decisions" (collector_orchestrator_routing.json)
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1
4. Validate the rerun/advance routing with `@collector_orchestrator_reentry`

### Incremental Delivery

1. Foundation ready (Phases 1-2)
2. Deliver US1 routing decisions and ticket collector
3. Deliver US2 human review gate
4. Polish docs and integration assets
