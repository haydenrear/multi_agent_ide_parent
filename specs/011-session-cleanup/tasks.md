# Tasks: ACP Session Lifecycle Management

**Input**: Design documents from `/specs/011-session-cleanup/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Status**: All tasks completed.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)

## Phase 1: Event Model Enhancements

**Purpose**: Add required event fields and new event types

- [x] T001 [US2] Add `agentModel` (Artifact.AgentModel) field to `ActionCompletedEvent` in `acp-cdc-ai/src/main/java/com/hayden/acp_cdc_ai/acp/events/Events.java`
- [x] T002 [P] [US2] Add `ChatSessionClosedEvent` record to `acp-cdc-ai/src/main/java/com/hayden/acp_cdc_ai/acp/events/Events.java`
- [x] T003 [P] [US2] Add `ChatSessionClosedEvent` case to exhaustive switch in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/BlackboardHistory.java`
- [x] T004 [P] [US2] Add `ChatSessionClosedEvent` case to exhaustive switch in `multi_agent_ide/src/main/java/com/hayden/multiagentide/infrastructure/AgentEventListener.java`

**Checkpoint**: Event model updated — ActionCompletedEvent carries agentModel, ChatSessionClosedEvent defined, all exhaustive switches pass compilation

---

## Phase 2: Event Emission (EmitActionCompletedResultDecorator)

**Purpose**: Emit ActionCompletedEvent with agentModel from the result decorator

- [x] T005 [US2] Update `EmitActionCompletedResultDecorator.java` to implement `DispatchedAgentResultDecorator` in `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/decorator/result/EmitActionCompletedResultDecorator.java`
- [x] T006 [US2] Update `decorate(AgentResult)` to pass result as `agentModel` in ActionCompletedEvent
- [x] T007 [US2] Update `decorateRequestResult(AgentRequest)` to pass request as `agentModel` in ActionCompletedEvent
- [x] T008 [US2] Emit GoalCompletedEvent only for OrchestratorCollectorResult, passing result as model

**Checkpoint**: ActionCompletedEvent emitted with correct agentModel for all result types; GoalCompletedEvent emitted only for OrchestratorCollectorResult

---

## Phase 3: Session Recycling (RequestEnrichment)

**Purpose**: Add contextId recycling for non-dispatched agents

- [x] T009 [US1] Add `resolveContextId(OperationContext, AgentModels.AgentRequest, Artifact.AgentModel)` overload in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/service/RequestEnrichment.java`
- [x] T010 [P] [US1] Add `shouldCreateNewSession(Artifact.AgentModel)` method
- [x] T011 [P] [US1] Add `findPreviousContextId(BlackboardHistory, AgentModels.AgentRequest)` method
- [x] T012 [US1] Update all `enrich*Request()` methods to use new resolveContextId overload (OrchestratorRequest, OrchestratorCollectorRequest, DiscoveryOrchestratorRequest, DiscoveryAgentRequest, DiscoveryCollectorRequest, PlanningOrchestratorRequest, PlanningAgentRequest, PlanningCollectorRequest, TicketOrchestratorRequest, TicketAgentRequest, TicketCollectorRequest, ReviewRequest, MergerRequest)
- [x] T013 [US1] Update `enrichDiscoveryAgentResults`, `enrichPlanningAgentResults`, `enrichTicketAgentResults` to use new overload
- [x] T014 [US1] Update `enrichDiscoveryAgentRequests`, `enrichPlanningAgentRequests`, `enrichTicketAgentRequests` to use new overload
- [x] T015 [US1] Update `enrichContextManagerRequest` and `ContextManagerRoutingRequest` inline case to use new overload
- [x] T016 [US1] Update all `enrichInterruptRequest` cases to use new overload

**Checkpoint**: All request types use recycling overload; only DiscoveryAgentRequest, PlanningAgentRequest, TicketAgentRequest create new sessions

---

## Phase 4: Session Cleanup Service

**Purpose**: Create the EventListener that closes sessions on agent/workflow completion

- [x] T017 [US2] [US3] Create `AcpSessionCleanupService.java` in `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AcpSessionCleanupService.java`
- [x] T018 [US2] Implement `handleActionCompleted` — close session for DiscoveryAgentResult, PlanningAgentResult, TicketAgentResult
- [x] T019 [US3] Implement `handleGoalCompleted` — close all descendant sessions for OrchestratorCollectorResult
- [x] T020 [US3] Implement `closeDescendantSessions` using ArtifactKey.isDescendantOf() iteration

**Checkpoint**: Cleanup service handles both dispatched agent cleanup and root workflow cleanup

---

## Phase 5: Testing

**Purpose**: Verify cleanup behavior

- [ ] T021 [US2] [US3] Create `AcpSessionCleanupServiceTest.java` in `multi_agent_ide/src/test/java/com/hayden/multiagentide/agent/AcpSessionCleanupServiceTest.java`
- [ ] T022 [US2] Test dispatched agent session closure (DiscoveryAgentResult, PlanningAgentResult, TicketAgentResult)
- [ ] T023 [US2] Test non-dispatched agent results do NOT trigger closure
- [ ] T024 [US3] Test descendant session cleanup on GoalCompletedEvent
- [ ] T025 [US3] Test unrelated workflow sessions are not affected
- [ ] T026 [US3] Test deeply nested descendant cleanup

**Checkpoint**: All cleanup paths verified with unit tests

---

## Phase 6: Compilation & Validation

**Purpose**: Ensure all changes compile and existing tests pass

- [x] T027 Verify full project compilation (`./gradlew compileJava compileKotlin`)

**Checkpoint**: Build green, no regressions

---

## Dependencies & Execution Order

- Phase 1 (Events) must complete before Phase 2 (Emission) and Phase 4 (Cleanup)
- Phase 2 (Emission) is independent of Phase 3 (Recycling)
- Phase 3 (Recycling) is independent of Phase 2 (Emission) and Phase 4 (Cleanup)
- Phase 4 (Cleanup) depends on Phase 1 (Events)
- Phase 5 (Testing) depends on Phase 4 (Cleanup)
- Phase 6 (Validation) depends on all other phases

## Parallel Opportunities

- T002, T003, T004 can run in parallel (different files, no dependencies between them)
- T010, T011 can run in parallel (helper methods with no cross-dependencies)
- Phase 2 and Phase 3 can run in parallel (different files, different concerns)
