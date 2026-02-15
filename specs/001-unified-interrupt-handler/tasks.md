# Tasks: Unified Interrupt Handler

**Input**: Design documents from `/specs/001-unified-interrupt-handler/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/interrupt-routing-contract.md, quickstart.md

**Tests**: Integration tests via test_graph Cucumber framework as specified in spec.md.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- `multi_agent_ide_lib/` = `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/`
- `multi_agent_ide/` = `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/`
- `acp-cdc-ai/` = `multi_agent_ide_java_parent/acp-cdc-ai/src/main/java/com/hayden/acp_cdc_ai/`
- `prompts/` = `multi_agent_ide_java_parent/multi_agent_ide/src/main/resources/prompts/`
- `test_graph/` = `test_graph/src/test/resources/features/unified-interrupt-handler/`

---

## Phase 1: Setup

**Purpose**: Create the 14 route marker annotations — foundational types that InterruptRouting depends on.

- [ ] T001 [P] Create `@OrchestratorRoute` annotation in `multi_agent_ide_lib/agent/OrchestratorRoute.java` — `@Retention(RUNTIME) @Target({FIELD, RECORD_COMPONENT})`, following the pattern in `SkipPropertyFilter.java`
- [ ] T002 [P] Create `@DiscoveryRoute` annotation in `multi_agent_ide_lib/agent/DiscoveryRoute.java`
- [ ] T003 [P] Create `@PlanningRoute` annotation in `multi_agent_ide_lib/agent/PlanningRoute.java`
- [ ] T004 [P] Create `@TicketRoute` annotation in `multi_agent_ide_lib/agent/TicketRoute.java`
- [ ] T005 [P] Create `@ReviewRoute` annotation in `multi_agent_ide_lib/agent/ReviewRoute.java`
- [ ] T006 [P] Create `@MergerRoute` annotation in `multi_agent_ide_lib/agent/MergerRoute.java`
- [ ] T007 [P] Create `@ContextManagerRoute` annotation in `multi_agent_ide_lib/agent/ContextManagerRoute.java`
- [ ] T008 [P] Create `@OrchestratorCollectorRoute` annotation in `multi_agent_ide_lib/agent/OrchestratorCollectorRoute.java`
- [ ] T009 [P] Create `@DiscoveryCollectorRoute` annotation in `multi_agent_ide_lib/agent/DiscoveryCollectorRoute.java`
- [ ] T010 [P] Create `@PlanningCollectorRoute` annotation in `multi_agent_ide_lib/agent/PlanningCollectorRoute.java`
- [ ] T011 [P] Create `@TicketCollectorRoute` annotation in `multi_agent_ide_lib/agent/TicketCollectorRoute.java`
- [ ] T012 [P] Create `@DiscoveryDispatchRoute` annotation in `multi_agent_ide_lib/agent/DiscoveryDispatchRoute.java`
- [ ] T013 [P] Create `@PlanningDispatchRoute` annotation in `multi_agent_ide_lib/agent/PlanningDispatchRoute.java`
- [ ] T014 [P] Create `@TicketDispatchRoute` annotation in `multi_agent_ide_lib/agent/TicketDispatchRoute.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Add the InterruptRouting record to the Routing sealed interface. Per-agent InterruptRequest subtypes are **kept as-is** — no changes to the InterruptRequest sealed interface or existing Routing records.

**CRITICAL**: No user story work can begin until this phase is complete.

- [ ] T015 Add `InterruptRouting` record implementing `Routing` to `multi_agent_ide_lib/agent/AgentModels.java`. Add 14 nullable fields with route marker annotations per data-model.md Entity 2 (orchestratorRequest @OrchestratorRoute, discoveryOrchestratorRequest @DiscoveryRoute, planningOrchestratorRequest @PlanningRoute, ticketOrchestratorRequest @TicketRoute, reviewRequest @ReviewRoute, mergerRequest @MergerRoute, contextManagerRequest @ContextManagerRoute, orchestratorCollectorRequest @OrchestratorCollectorRoute, discoveryCollectorRequest @DiscoveryCollectorRoute, planningCollectorRequest @PlanningCollectorRoute, ticketCollectorRequest @TicketCollectorRoute, discoveryAgentRequests @DiscoveryDispatchRoute, planningAgentRequests @PlanningDispatchRoute, ticketAgentRequests @TicketDispatchRoute). Add `InterruptRouting` to the `Routing` sealed interface permits list.

**Checkpoint**: Foundation ready — InterruptRouting compiles, user story implementation can begin.

---

## Phase 3: User Story 1 — Single Interrupt Handler Replaces Per-Agent Handlers (Priority: P1)

**Goal**: Consolidate 7+ per-agent interrupt handler methods in WorkflowAgent into a single `handleUnifiedInterrupt` @Action method that accepts the sealed `InterruptRequest` interface, determines the originating agent via `instanceof` pattern matching, and routes back correctly via InterruptRouting.

**Key design**: Per-agent InterruptRequest subtypes are kept. The unified handler accepts the sealed interface and uses `instanceof` to determine origin agent type. No existing Routing records change.

**Independent Test**: Trigger an interrupt from any agent phase and verify the unified handler processes it and routes back to the correct request type.

### Implementation for User Story 1

- [ ] T016 [US1] Create an `AgentTypeMapping` configuration (enum or static map) in `multi_agent_ide/agent/AgentInterfaces.java` (or a new helper class nearby) that maps each `AgentType` / request class to its template name, history lookup class, route annotation, and template model builder function. See data-model.md Entity 5 for the full 14-entry mapping table.
- [ ] T017 [US1] Implement `handleUnifiedInterrupt(InterruptRequest request, OperationContext context)` method in WorkflowAgent within `multi_agent_ide/agent/AgentInterfaces.java`. The method should: (1) determine `originAgentType` via `instanceof` pattern matching on the concrete InterruptRequest subtype (e.g., `case OrchestratorInterruptRequest _ -> AgentType.ORCHESTRATOR`), (2) look up the `AgentTypeMapping` for template name and history class, (3) retrieve the last request of the originating type from history (throw `DegenerateLoopException` if not found), (4) decorate the request via `decorateRequest()`, (5) build template model map with agent-specific fields, (6) build PromptContext via `buildPromptContext()`, (7) call `interruptService.handleInterrupt()` with `InterruptRouting.class`, (8) decorate the result via `decorateRouting()`. Annotate with `@Action(canRerun = true, cost = 1)`.
- [ ] T018 [US1] Remove per-agent interrupt handler methods from WorkflowAgent in `multi_agent_ide/agent/AgentInterfaces.java`: `handleOrchestratorInterrupt` (~L1080-1141), `handleDiscoveryInterrupt` (~L1326-1386), `handlePlanningInterrupt` (~L1586-1654), `handleTicketInterrupt` (~L1873-1934), `handleReviewInterrupt` (~L2090-2160), `handleMergerInterrupt` (~L2424-2495), `handleContextManagerInterrupt` (~L511-586). Verify no other code references these methods.
- [ ] T019 [US1] Update `WorkflowAgentGraphNode.java` in `multi_agent_ide_lib/prompt/WorkflowAgentGraphNode.java` — in `getRequestToRoutingMap()`, add a new entry: `InterruptRequest.class → InterruptRouting.class`. Keep existing per-agent interrupt entries as-is (subtypes still exist on existing Routing records).
- [ ] T020 [US1] Update `InterruptLoopBreakerPromptContributorFactory.java` in `multi_agent_ide_lib/prompt/contributor/InterruptLoopBreakerPromptContributorFactory.java` — update the `resolveMapping()` switch so all in-scope cases (OrchestratorRequest, OrchestratorCollectorRequest, DiscoveryOrchestratorRequest, DiscoveryCollectorRequest, PlanningOrchestratorRequest, PlanningCollectorRequest, TicketOrchestratorRequest, TicketCollectorRequest, ReviewRequest, MergerRequest, ContextManagerRequest/ContextManagerRoutingRequest) resolve to the unified `InterruptRequest` type instead of their per-agent InterruptRequest subtypes. Keep dispatched agent cases unchanged.
- [ ] T021 [US1] Update `NodeMappings.java` in `multi_agent_ide_lib/prompt/contributor/NodeMappings.java` — add a unified entry `InterruptRequest.class → "Interrupt Request"` to `initAllMappings()`. Keep the existing 18 per-agent InterruptRequest subtype entries as-is (subtypes still exist).

**Checkpoint**: Unified handler works — any in-scope agent interrupt routes through the single handler and returns to the correct agent. Compile and run unit tests.

---

## Phase 4: User Story 2 — Dynamic Schema Filtering via Annotation Set Difference (Priority: P1)

**Goal**: Implement FilterPropertiesDecorator to dynamically apply `withAnnotationFilter(...)` using set-difference logic, so that InterruptRouting schema only shows the valid return route for the originating agent.

**Independent Test**: Trigger an interrupt from a specific agent and verify only that agent's return route field is visible in the schema sent to the LLM.

### Implementation for User Story 2

- [ ] T022 [US2] Implement `FilterPropertiesDecorator.decorate(LlmCallContext<T> ctx)` in `multi_agent_ide/agent/decorator/prompt/FilterPropertiesDecorator.java`. When `T` is `InterruptRouting.class`: (1) first check if a stored `InterruptRequestEvent` exists for this node/session ID — if so, use the event's `rerouteToAgentType` to determine the target route annotation (this is the US3 event-driven re-route path; clear/consume the event after use), (2) otherwise, inspect `ctx.promptContext().previousRequest()` using `instanceof` pattern matching on the sealed `InterruptRequest` interface to determine the originating agent type and use its route annotation (normal return-to-origin path), (3) collect all 14 route annotations into a set, (4) remove the determined target annotation (set difference), (5) for each remaining annotation, chain `ctx.templateOperations().withAnnotationFilter(annotation)`, (6) return modified `LlmCallContext`. See contracts/interrupt-routing-contract.md for the full FilterPropertiesDecorator contract.
- [ ] T023 [US2] Verify that `InterruptPromptContributorFactory.java` in `multi_agent_ide/prompt/contributor/InterruptPromptContributorFactory.java` works with the sealed `InterruptRequest` interface — the `instanceof AgentModels.InterruptRequest` check already matches all subtypes. Verify `InterruptResolutionContributor` correctly references `previousRequest` for routing guidance.

**Checkpoint**: Schema filtering works — interrupts from any agent produce a schema with only the valid return route visible and all other route fields and their `$defs` removed.

---

## Phase 5: User Story 3 — InterruptRequestEvent for External Interrupt Triggering (Priority: P2)

**Goal**: Add InterruptRequestEvent to Events.java and implement the flow: send message to running agent via `addMessageToAgent()` asking it to produce an InterruptRequest, store the event so FilterPropertiesDecorator can detect it during interrupt resolution and re-route to the event's target destination.

**Key design**: The running agent's schema is never modified. The agent already has `interruptRequest` as a routing option. The message asks it to choose that option. The re-routing decision is made in FilterPropertiesDecorator when the interrupt handler's InterruptRouting output is being prepared.

**Independent Test**: Publish an InterruptRequestEvent while an agent is running and verify that (a) a message is sent to the agent, (b) the agent produces an InterruptRequest, (c) the interrupt handler's InterruptRouting is filtered to the event's target route, and (d) the workflow continues at the target agent.

### Implementation for User Story 3

- [ ] T024 [US3] Add `InterruptRequestEvent` record to `acp-cdc-ai/acp/events/Events.java` implementing `GraphEvent` and `AgentEvent`. Fields: `eventId` (String), `timestamp` (Instant), `nodeId` (String), `sourceAgentType` (AgentType — the agent currently running), `rerouteToAgentType` (AgentType — where to re-route; nullable, falls back to normal set-difference if absent), `interruptType` (InterruptType), `reason` (String), `choices` (List<StructuredChoice>), `confirmationItems` (List<ConfirmationItem>), `contextForDecision` (String). See data-model.md Entity 4 and contracts/interrupt-routing-contract.md.
- [ ] T025 [US3] Add event storage to `FilterPropertiesDecorator.java` in `multi_agent_ide/agent/decorator/prompt/FilterPropertiesDecorator.java`. Add a thread-safe `Map<String, InterruptRequestEvent>` (keyed by nodeId/sessionId) that stores events for later lookup. Add a public `storeEvent(InterruptRequestEvent event)` method. The lookup and consumption logic is already in T022 (the decorator checks for a stored event before falling back to normal set-difference filtering). See contracts/interrupt-routing-contract.md "Behavior — Event-Driven Re-Route" section.
- [ ] T026 [US3] Implement an event handler (new class or addition to existing `AgentEventListener`) that receives `InterruptRequestEvent`, calls `AgentRunner.addMessageToAgent(reason, nodeId)` to send the interrupt reason to the running agent (asking it to produce an InterruptRequest), and stores the event in `FilterPropertiesDecorator` via `storeEvent()`. Reference `AgentRunner.addMessageToAgent()` and `MultiAgentEmbabelConfig.llmOutputChannel` for the message-sending mechanism.

**Checkpoint**: External interrupts work — publishing an InterruptRequestEvent causes a message to be sent to the running agent, the agent produces an InterruptRequest naturally, and the interrupt handler's InterruptRouting is filtered to the event's target route instead of routing back to the originating agent.

---

## Phase 6: User Story 4 — Dispatched Agents Skipped in Initial Scope (Priority: P3)

**Goal**: Ensure dispatched sub-agents (TicketDispatchSubagent, PlanningDispatchSubagent, DiscoveryDispatchSubagent) do not participate in the unified interrupt handler. Their existing `transitionToInterruptState` methods are removed or simplified.

**Independent Test**: Verify that dispatched sub-agents do not route to the unified handler, and that interrupting a dispatched agent's work is achieved via the parent orchestrator.

### Implementation for User Story 4

- [ ] T027 [US4] Remove or simplify `transitionToInterruptState` methods in dispatch subagents within `multi_agent_ide/agent/AgentInterfaces.java`: `TicketDispatchSubagent.transitionToInterruptState` (~L2669-2740), `PlanningDispatchSubagent` equivalent, `DiscoveryDispatchSubagent` equivalent. If the methods are referenced by existing dispatch routing records (DiscoveryAgentDispatchRouting, PlanningAgentDispatchRouting, TicketAgentDispatchRouting), leave the per-agent interrupt types on those routing records as-is (out of scope).
- [ ] T028 [US4] Verify that the dispatch agent routing records (`DiscoveryAgentDispatchRouting`, `PlanningAgentDispatchRouting`, `TicketAgentDispatchRouting`) and individual agent routing records (`DiscoveryAgentRouting`, `PlanningAgentRouting`, `TicketAgentRouting`) still compile and function correctly with their existing per-agent interrupt types unchanged.

**Checkpoint**: Dispatched agents are cleanly scoped out — their interrupt paths remain independent and functional.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Update all prompt infrastructure, jinja templates, and documentation to reflect the unified interrupt handler.

- [ ] T029 [P] Update `WeAreHerePromptContributor.java` in `multi_agent_ide_lib/prompt/contributor/WeAreHerePromptContributor.java` — update static TEMPLATE to show single `handleUnifiedInterrupt` node instead of 10+ per-agent nodes. Update routing arrows. Update GUIDANCE_TEMPLATES map entries to reference unified handler. Update INTERRUPT_GUIDANCE string to describe set-difference filtering.
- [ ] T030 [P] Update `_interrupt_guidance.jinja` in `prompts/workflow/_interrupt_guidance.jinja` — replace "Include the agent-specific context fields for your interrupt type" with guidance referencing the unified interrupt handler and the fact that per-agent InterruptRequest subtypes are still used (each subtype has its own structured fields). Remove "your interrupt type" phrasing.
- [ ] T031 [P] Update `orchestrator.jinja` in `prompts/workflow/orchestrator.jinja` — update the `interruptRequest` routing option description (line 25) to reference the unified interrupt handler. Keep the field name `interruptRequest` unchanged.
- [ ] T032 [P] Update `_context_manager_body.jinja` in `prompts/workflow/_context_manager_body.jinja` — update `interruptRequest` routing option description (line 25) to reference unified interrupt handler.
- [ ] T033 Verify `context_manager_interrupt.jinja` in `prompts/workflow/context_manager_interrupt.jinja` works correctly with the unified interrupt flow. Update if needed.
- [ ] T034 Verify `ContextManagerReturnRoutes.java` in `multi_agent_ide_lib/prompt/contributor/ContextManagerReturnRoutes.java` — confirm `findLastNonContextManagerRequest()` correctly filters out InterruptRequest subtypes via `instanceof AgentModels.InterruptRequest` (works unchanged since subtypes are kept).
- [ ] T035 Full compilation check — build entire multi-module project (`multi_agent_ide_lib`, `acp-cdc-ai`, `multi_agent_ide`) and fix any compilation errors.
- [ ] T036 Run existing unit tests and fix any failures caused by the refactoring.
- [ ] T037 Write Cucumber feature files for `test_graph/src/test/resources/features/unified-interrupt-handler/` covering tags `@unified-interrupt-handler`, `@interrupt-schema-filtering`, `@interrupt-request-event`, `@skip-dispatched-interrupts` per spec.md acceptance scenarios. Follow test_graph/instructions.md and test_graph/instructions-features.md. Use minimal generic step definitions: (1) set up starting state with config/services, (2) assert messages received as expected. Use Spring component beans for assertions over context.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — all 14 annotations can be created in parallel
- **Foundational (Phase 2)**: Depends on Phase 1 completion — BLOCKS all user stories
- **US1 (Phase 3)**: Depends on Phase 2 — core handler implementation
- **US2 (Phase 4)**: Depends on Phase 2 and co-requisite with US1 (both P1) — schema filtering
- **US3 (Phase 5)**: Depends on Phase 2, US1, and US2 — external interrupt triggering
- **US4 (Phase 6)**: Depends on Phase 2 and US1 — dispatch agent cleanup
- **Polish (Phase 7)**: Depends on US1 and US2 being complete — prompt infrastructure updates

### User Story Dependencies

- **US1 (P1)**: Can start after Foundational (Phase 2) — no dependencies on other stories
- **US2 (P1)**: Can start after Foundational (Phase 2) — co-requisite with US1 but works on different files (`FilterPropertiesDecorator.java` vs `AgentInterfaces.java`), can proceed in parallel
- **US3 (P2)**: Requires US1 and US2 complete — InterruptRequestEvent flow depends on both unified handler and schema filtering
- **US4 (P3)**: Requires US1 complete — dispatch agent cleanup depends on unified handler existing

### Within Each User Story

- Models/types before implementation
- Core handler logic before prompt/template updates
- Implementation before verification tasks
- Story complete before moving to next priority

### Parallel Opportunities

- All 14 annotation files in Phase 1 (T001-T014) can be created in parallel
- US1 and US2 can largely proceed in parallel (different files)
- Within Phase 7, all prompt/template updates (T029-T033) can proceed in parallel
- T023 (verify InterruptPromptContributorFactory) can run in parallel with T022

---

## Parallel Example: Phase 1 (Annotations)

```
# All 14 annotations can be created simultaneously:
Task: T001 Create @OrchestratorRoute in multi_agent_ide_lib/agent/OrchestratorRoute.java
Task: T002 Create @DiscoveryRoute in multi_agent_ide_lib/agent/DiscoveryRoute.java
Task: T003 Create @PlanningRoute in multi_agent_ide_lib/agent/PlanningRoute.java
... (all 14 in parallel)
```

## Parallel Example: US1 + US2 (Co-requisite P1 stories)

```
# After Phase 2, US1 and US2 work on different files:
# US1 stream:
Task: T016 Create AgentTypeMapping in AgentInterfaces.java
Task: T017 Implement handleUnifiedInterrupt in AgentInterfaces.java
Task: T018 Remove per-agent handlers from AgentInterfaces.java

# US2 stream (parallel):
Task: T022 Implement FilterPropertiesDecorator in FilterPropertiesDecorator.java
Task: T023 Verify InterruptPromptContributorFactory
```

---

## Implementation Strategy

### MVP First (US1 + US2)

1. Complete Phase 1: Setup (14 annotations) — all parallel
2. Complete Phase 2: Foundational (InterruptRouting record) — single task
3. Complete Phase 3 + Phase 4: US1 + US2 in parallel — core handler + schema filtering
4. **STOP and VALIDATE**: Build project, run unit tests, manually verify interrupt flow
5. Deploy/demo if ready — unified handler works for all in-scope agents

### Incremental Delivery

1. Setup + Foundational → types ready
2. US1 + US2 → unified handler with schema filtering → **MVP**
3. US3 → external interrupt triggering via InterruptRequestEvent
4. US4 → dispatch agent cleanup
5. Polish → prompt infrastructure updates, integration tests

### Parallel Team Strategy

With 2 developers:

1. Both complete Phase 1 annotations (split 7 each)
2. Developer A: Phase 2 (InterruptRouting) → Phase 3 (US1 handler)
3. Developer B: waits for Phase 2 → Phase 4 (US2 filtering)
4. Developer A: Phase 5 (US3 events)
5. Developer B: Phase 6 (US4 dispatch) + Phase 7 (prompt updates)
6. Both: T035-T037 (build, test, integration)

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- US1 and US2 are co-requisite P1 stories — both needed for a functional interrupt flow
- **Per-agent InterruptRequest subtypes are KEPT** — the unified handler accepts the sealed `InterruptRequest` interface and uses `instanceof` pattern matching to determine origin
- **No existing Routing records change** — subtypes remain as fields on their respective Routing records
- **No subtypes removed** — avoids polymorphic schema issues, preserves structured per-agent fields
- Dispatched agent interrupt types (out of scope) are left unchanged on their Routing records
- `BlackboardRoutingPlanner.java` needs NO changes — handles InterruptRouting generically
- `ContextManagerReturnRoutes.java` needs NO changes — filters interrupts via `instanceof`
