# Tasks: Agent Graph UI

**Input**: Design documents from `/specs/002-ag-ui/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, test_graph/src/test/resources/features/multi_agent_ide/ag_ui.feature

**Tests**: Not explicitly requested; omit unless needed during implementation.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [X] T001 Create Next.js app scaffold in `multi_agent_ide/fe/src/app/layout.tsx` and `multi_agent_ide/fe/src/app/page.tsx`
- [X] T002 [P] Add CopilotKit and ag-ui dependencies in `multi_agent_ide/fe/package.json`
- [X] T003 [P] Add base global styles in `multi_agent_ide/fe/src/app/globals.css`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

- [X] T004 Implement ag-ui event mapping registry in `multi_agent_ide/src/main/java/com/hayden/multiagentide/adapter/AgUiEventMappingRegistry.java`
- [X] T005 Implement ag-ui event mapper and hook into `Events.mapToEvent` in `multi_agent_ide/src/main/java/com/hayden/multiagentide/model/events/Events.java`
- [X] T006 Implement SSE event adapter in `multi_agent_ide/src/main/java/com/hayden/multiagentide/adapter/SseEventAdapter.java`
- [X] T007 Update event bus adapter wiring for multiple adapters in `multi_agent_ide/src/main/java/com/hayden/multiagentide/infrastructure/EventAdapter.java` and `multi_agent_ide/src/main/java/com/hayden/multiagentide/infrastructure/DefaultEventBus.java`
- [X] T008 Implement text/event-stream endpoint in `multi_agent_ide/src/main/java/com/hayden/multiagentide/controller/EventStreamController.java`
- [X] T009 [P] Add SSE client wrapper in `multi_agent_ide/fe/src/lib/eventStream.ts`
- [X] T010 [P] Add graph state store in `multi_agent_ide/fe/src/state/graphStore.ts`
- [X] T011 [P] Add viewer plugin types and registry in `multi_agent_ide/fe/src/plugins/types.ts` and `multi_agent_ide/fe/src/plugins/registry.ts`

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - Live Graph Updates (Priority: P1) 🎯 MVP

**Goal**: Render live graph updates from the event stream with node/worktree updates and unknown event handling.

**Independent Test**: Publish graph events via the stream and verify nodes/status/worktrees appear and update without refresh.

### Implementation for User Story 1

- [X] T012 [US1] Wire SSE client to graph store updates in `multi_agent_ide/fe/src/app/page.tsx`
- [X] T013 [P] [US1] Implement graph view component in `multi_agent_ide/fe/src/components/GraphView.tsx`
- [X] T014 [P] [US1] Implement graph node card rendering in `multi_agent_ide/fe/src/components/GraphNodeCard.tsx`
- [X] T015 [US1] Add event reconciliation (timestamp ordering, de-dupe) in `multi_agent_ide/fe/src/state/graphStore.ts`
- [X] T016 [US1] Add generic unknown event rendering in `multi_agent_ide/fe/src/components/GraphView.tsx`

**Checkpoint**: User Story 1 fully functional and independently testable

---

## Phase 4: User Story 2 - Inspect Node Details (Priority: P2)

**Goal**: Provide node detail inspection with event history, messages, and viewer plugins.

**Independent Test**: Select a node and confirm event history, latest message, and appropriate viewer plugin are displayed.

### Implementation for User Story 2

- [X] T017 [P] [US2] Implement node details panel in `multi_agent_ide/fe/src/components/NodeDetailsPanel.tsx`
- [X] T018 [P] [US2] Implement default and merge review viewers in `multi_agent_ide/fe/src/plugins/DefaultViewer.tsx` and `multi_agent_ide/fe/src/plugins/MergeReviewViewer.tsx`
- [X] T019 [P] [US2] Implement tool call and stream viewers in `multi_agent_ide/fe/src/plugins/ToolCallViewer.tsx` and `multi_agent_ide/fe/src/plugins/StreamViewer.tsx`
- [X] T020 [US2] Wire plugin selection logic in `multi_agent_ide/fe/src/plugins/registry.ts` and `multi_agent_ide/fe/src/components/NodeDetailsPanel.tsx`
- [X] T021 [US2] Add node selection state and details layout in `multi_agent_ide/fe/src/state/graphStore.ts` and `multi_agent_ide/fe/src/app/page.tsx`

**Checkpoint**: User Stories 1 and 2 both work independently

---

## Phase 5: User Story 3 - Control Agent Execution (Priority: P3)

**Goal**: Enable pause/interrupt/review control actions from the UI with backend endpoints.

**Independent Test**: Trigger a control action from the UI and verify a corresponding control event is emitted and displayed.

### Implementation for User Story 3

- [X] T022 [P] [US3] Add control endpoints in `multi_agent_ide/src/main/java/com/hayden/multiagentide/controller/AgentControlController.java`
- [X] T023 [US3] Emit control events via service in `multi_agent_ide/src/main/java/com/hayden/multiagentide/service/AgentControlService.java`
- [X] T024 [P] [US3] Add control API client in `multi_agent_ide/fe/src/lib/agentControls.ts`
- [X] T025 [US3] Add pause/interrupt/review UI actions in `multi_agent_ide/fe/src/components/NodeDetailsPanel.tsx`

**Checkpoint**: All user stories independently functional

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [X] T026 [P] Document viewer plugin registration in `multi_agent_ide/fe/README.md`
- [X] T027 Validate quickstart steps and adjust if needed in `specs/002-ag-ui/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P2)**: Can start after Foundational (Phase 2) - May integrate with US1 but independently testable
- **User Story 3 (P3)**: Can start after Foundational (Phase 2) - May integrate with US1/US2 but independently testable

### Parallel Opportunities

- Setup tasks T002-T003 can run in parallel
- Foundational tasks T009-T011 can run in parallel
- User Story 1 tasks T013-T014 can run in parallel
- User Story 2 tasks T017-T019 can run in parallel
- User Story 3 tasks T022 and T024 can run in parallel

---

## Parallel Example: User Story 1

```bash
Task: "Implement graph view component in multi_agent_ide/fe/src/components/GraphView.tsx"
Task: "Implement graph node card rendering in multi_agent_ide/fe/src/components/GraphNodeCard.tsx"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1
4. Validate live updates via event stream

### Incremental Delivery

1. Setup + Foundational → Foundation ready
2. User Story 1 → Test independently → Demo MVP
3. User Story 2 → Test independently → Demo
4. User Story 3 → Test independently → Demo
5. Polish tasks as needed
