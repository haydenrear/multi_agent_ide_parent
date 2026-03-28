# Tasks: Inter-Agent Communication Topology & Conversational Structured Channels

**Input**: Design documents from `/specs/001-agent-topology/`

**Prerequisites**: plan.md, spec.md, data-model.md, contracts/, research.md, quickstart.md

**Tests**: Integration tests deferred per spec — test_graph tests not generated. Unit tests included where critical for correctness.

**Organization**: Tasks organized by the quickstart's phased approach: Interrupt Simplification (P0) → Communication Foundation → Inter-Agent Tools → Call Controller → Polling → Conversational Topology Docs.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US9, US1, US7)
- Include exact file paths in descriptions

## Path Conventions

- Java app module: `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/`
- Java lib module: `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/`
- ACP module: `multi_agent_ide_java_parent/acp-cdc-ai/src/main/`
- Events: `multi_agent_ide_java_parent/acp-cdc-ai/src/main/java/com/hayden/acp_cdc_ai/acp/events/Events.java`
- Controller skill: `skills/multi_agent_ide_skills/multi_agent_ide_controller/`
- Config: `multi_agent_ide_java_parent/multi_agent_ide/src/main/resources/application.yml`

---

## Phase 1: Setup

**Purpose**: Verify current state and prepare branch for modifications

- [x] T001 Verify project compiles on `001-agent-topology` branch — run `./gradlew compileJava` from `multi_agent_ide_java_parent/`
- [x] T002 Identify all files containing `ReviewRequest`, `MergerRequest`, `ReviewRouting`, `MergerRouting`, `ReviewRoute`, `MergerRoute`, `performReview`, `performMerge` references across `multi_agent_ide_java_parent/` via grep

---

## Phase 2: Interrupt Simplification — P0 Pre-requisite (Story 9)

**Purpose**: Remove agent-initiated interrupts. MUST complete before any other user story work.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete. This is the P0 pre-requisite per spec.

### Model & Annotation Removal

- [x] T003 [US9] Remove `ReviewRequest`, `MergerRequest`, `ReviewRouting`, `MergerRouting` records and all references from sealed interfaces in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AgentModels.java`
- [x] T004 [US9] Remove `reviewRequest` and `mergerRequest` fields from `InterruptRouting`, `OrchestratorCollectorRouting`, `DiscoveryCollectorRouting`, `PlanningCollectorRouting`, `TicketCollectorRouting` in `AgentModels.java`
- [x] T005 [P] [US9] Delete `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/ReviewRoute.java`
- [x] T006 [P] [US9] Delete `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/MergerRoute.java`

### Agent Action Removal

- [x] T007 [US9] Remove `performReview()`, `performMerge()`, `mapReview()`, `mapMerge()` methods from `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java`
- [x] T008 [US9] Remove `ranTicketAgentResult`, `ranPlanningAgent`, `ranDiscoveryAgent` passthrough result constants and FilterLayerCatalog entries from dispatch subagents in `AgentInterfaces.java` and `FilterLayerCatalog.java` (FR-032d)
- [x] T009 [US9] Fill in `resolveInterruptRouteConfig` null arms for `DiscoveryAgentInterruptRequest`, `PlanningAgentInterruptRequest`, `TicketAgentInterruptRequest`, `QuestionAnswerInterruptRequest` in `AgentInterfaces.java` — subagent cases resolve origin node from graph (DiscoveryNode/PlanningNode/TicketNode with interruptibleContext) with fallback to dispatch node

### Subagent Routing Separation

- [x] T010 [US9] Separate subagent routing types (`DiscoveryAgentRouting`, `PlanningAgentRouting`, `TicketAgentRouting`) from `Routing`/`SomeOf` sealed hierarchy into `DispatchedAgentRouting` interface in `AgentModels.java` (FR-032b) — already existed
- [x] T011 [US9] Remove `contextManagerRequest` fields from all routing types in `AgentModels.java` (FR-032c) — removed from all Routing and DispatchedAgentRouting types + fixed cascading compilation errors in ArtifactEnrichmentDecorator, WorkflowGraphResultDecorator
- [x] T012 [US9] Add `@SkipPropertyFilter agentInterruptRequest` field on dispatch routing objects for interrupt bubble-up (FR-032e) in `AgentModels.java` — already present on all three dispatch routing types

### Prompt Contributor Cleanup

- [x] T013 [P] [US9] Delete `InterruptPromptContributorFactory.java` and its test `InterruptPromptContributorFactoryTest.java` (FR-024)
- [x] T014 [P] [US9] Delete `OrchestratorRouteBackInterruptPromptContributorFactory.java` (FR-025)
- [x] T015 [US9] Remove `ReviewRequest`/`MergerRequest` cases from switch in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/prompt/contributor/InterruptLoopBreakerPromptContributorFactory.java` (FR-027)

### FilterPropertiesDecorator Update

- [x] T016 [US9] Remove `ReviewRoute.class` and `MergerRoute.class` from `ALL_INTERRUPT_ROUTE_ANNOTATIONS` in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/decorator/prompt/FilterPropertiesDecorator.java`
- [x] T017 [US9] Remove `REVIEW_MERGER_RETURN_ROUTE_ANNOTATIONS` set, `resolveReviewMergerTargetRoute()` method, and `mapRequestToRoute()` cases for ReviewRequest/MergerRequest in `FilterPropertiesDecorator.java`
- [x] T018 [US9] Remove `mapAgentTypeToRoute()` cases for `REVIEW_AGENT`/`MERGER_AGENT` in `FilterPropertiesDecorator.java`

### Interrupt Controller Update

- [x] T019 [US9] Add `rerouteToAgentType` field (String) to `InterruptController.InterruptRequest` record in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/controller/InterruptController.java` (FR-028a)
- [x] T020 [US9] Wire `rerouteToAgentType` from REST payload through to `InterruptRequestEvent.rerouteToAgentType` in `InterruptController.java` (FR-028b)
- [x] T021 [US9] Implement AddMessage interrupt injection: compose override schema `SomeOf(InterruptRequest)` + reason + context + instructions into AddMessage payload using dedicated decorators in `InterruptController.java` (FR-028c, FR-028d)
- [x] T022 [US9] Generate `SomeOf(InterruptRequest)` override schema using victools-based `SpecialJsonSchemaGenerator` with custom SchemaFilter for interrupt-only fields (FR-029) — add filter class alongside existing filters in utility module

### Route-Back Simplification via Controller Conference

- [x] T022a [P] [US9] Delete `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/prompt/contributor/RouteBackInterruptPromptContributorFactory.java` (FR-026 — replaced by controller conference + AddMessage route-back schema injection)
- [x] T022b [US9] Remove `CollectorDecision` enum and `collectorDecision` field from all `*CollectorResult` types (`PlanningCollectorResult`, `DiscoveryCollectorResult`, `TicketCollectorResult`, `OrchestratorCollectorResult`) in `AgentModels.java` — collectors always route forward; route-back handled by controller (FR-032g). Also removed `HasRouteBack`, `HasOrchestratorRequestRouteBack` marker interfaces (no consumers after RouteBackInterruptPromptContributorFactory deletion).
- [x] T022c [US9] Remove `CollectorDecision` switch/match cases from all result decorators and branch handlers — simplified `BlackboardRoutingPlanner` (OrchestratorCollectorResult always routes to finalCollectorResult), removed `wrapCollectorDecision` from `RequestEnrichment`, simplified all 4 collector branch handlers in `AgentInterfaces` to always advance forward, removed `CollectorDecision` from `AgentModelMixin`, `ConsolidationTemplate`, `CliEventFormatter`, collector node types, and all test files.
- [x] T022d [US9] Add `@SkipPropertyFilter` annotation to all route-back request fields on collector routing objects in `AgentModels.java` (FR-032f): added to `orchestratorRequest` on `DiscoveryCollectorRouting`, `PlanningCollectorRouting`, `TicketCollectorRouting` (others already had it).
- [x] T022e [US9] Add `@SkipPropertyFilter` annotation to all `interruptRequest` fields on all routing objects in `AgentModels.java` (FR-028) — added to all 16 routing types (OrchestratorRouting, OrchestratorCollectorRouting, DiscoveryOrchestratorRouting, DiscoveryAgentRouting, DiscoveryCollectorRouting, DiscoveryAgentDispatchRouting, PlanningOrchestratorRouting, PlanningAgentRouting, PlanningCollectorRouting, PlanningAgentDispatchRouting, TicketOrchestratorRouting, TicketAgentRouting, TicketCollectorRouting, TicketAgentDispatchRouting, ContextManagerResultRouting). `agentInterruptRequest` fields on dispatch types already had it.
- [x] T022f [US9] Updated `InterruptController.InterruptRequest`: changed `rerouteToAgentType` from optional String to required `AgentType` enum. Removed `routeBack` — route-back belongs on the conversational topology controller endpoint (Story 7), not on the interrupt endpoint.
- [ ] T022g [US7] Implement `routeBack` on the conversational topology controller endpoint (Story 7) — when the controller sets `routeBack=true` during a conference with a collector agent, resolve the collector's routing object via `NodeMappings`, generate victools schema for only the route-back request field (e.g., `Routing(PlanningOrchestratorRequest)`), inject via AddMessage (FR-032h, FR-032i, FR-032j). Return error if agent is not a collector (FR-032k). Depends on Phase 3+ conversational topology infrastructure.
- [x] T022h [US9] Extend `InterruptSchemaGenerator` to support route-back schemas via `generateRouteBackSchema(routingClass, routeBackFieldType)` — same victools field-filter mechanism, different predicate (FR-032i). Static metadata (`AGENT_TYPE_TO_ROUTING`, `agentTypeFromNode`) moved to `NodeMappings` in lib module.

### Cleanup & Verification

- [x] T023 [US9] Remove review/merger routing references from `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/WorkflowGraphService.java`
- [x] T024 [US9] Remove review/merger mixins if present from `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/config/AgentModelMixin.java`
- [x] T025 [US9] Grep entire `multi_agent_ide_java_parent/` for remaining `ReviewRequest`, `MergerRequest`, `ReviewRouting`, `MergerRouting`, `ReviewRoute`, `MergerRoute`, `performReview`, `performMerge` references and remove all remaining occurrences
- [x] T026 [US9] Verify compilation passes — run `./gradlew compileJava` from `multi_agent_ide_java_parent/`

**Checkpoint**: Phase 2 (Interrupt Simplification) COMPLETE. All review/merger code removed. Interrupt simplified to controller-initiated rerouting only with required `AgentType`. `CollectorDecision` removed — collectors always route forward. Route-back deferred to conversational topology (T022g, Story 7). All `interruptRequest` and route-back fields annotated with `@SkipPropertyFilter`. Schema generation via victools working. Ready for Phase 3+.

---

## Phase 3: Communication Foundation (Stories 1-8 shared infrastructure)

**Purpose**: Add AgentRequest subtypes, GraphNode types, and decorator audit — the types that all communication tools depend on.

**⚠️ CRITICAL**: Must complete before any tool implementation.

### Communication AgentRequest Subtypes

- [x] T027 [P] [US1] Add `AgentToAgentRequest` record implementing `AgentRequest` in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AgentModels.java` with fields: `sourceAgentKey`, `sourceAgentType`, `targetAgentKey`, `targetAgentType`, `message`, `callChain`, `goal`, `key` (child of sourceAgentKey)
- [x] T028 [P] [US7] Add `AgentToControllerRequest` record implementing `AgentRequest` in `AgentModels.java` with fields: `sourceAgentKey`, `sourceAgentType`, `justificationMessage`, `goal`, `key` (child of sourceAgentKey)
- [x] T029 [P] [US7] Add `ControllerToAgentRequest` record implementing `AgentRequest` in `AgentModels.java` with fields: `sourceKey`, `targetAgentKey`, `targetAgentType`, `message`, `checklistAction`, `goal`, `key` (child of sourceKey)
- [x] T030 [P] [US7] Add `ChecklistAction` record to `AgentModels.java` with fields: `actionType`, `completedStep`, `stepDescription`
- [x] T031 [P] [US7] Add `Artifact.ControllerChecklistTurn` record to `AgentModels.java` with fields: `targetAgentKey`, `targetAgentType`, `checklistAction`, `controllerMessage`, `conversationKey`, `timestamp`

### Graph Node Types

- [x] T032 [P] [US1] Add `AgentToAgentConversationNode` record to sealed permits list in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/model/nodes/GraphNode.java` with proper fields: `sourceAgentKey`, `sourceAgentType`, `targetAgentKey`, `targetAgentType`
- [x] T033 [P] [US7] Add `AgentToControllerConversationNode` record to sealed permits list in `GraphNode.java` with proper fields: `sourceAgentKey`, `sourceAgentType`
- [x] T034 [P] [US7] Add `ControllerToAgentConversationNode` record to sealed permits list in `GraphNode.java` with proper fields: `targetAgentKey`, `targetAgentType`

### NodeType & NodeStatus Extensions

- [x] T035 [US1] Add `AGENT_TO_AGENT_CONVERSATION`, `AGENT_TO_CONTROLLER_CONVERSATION`, `CONTROLLER_TO_AGENT_CONVERSATION` to `Events.NodeType` enum in `multi_agent_ide_java_parent/acp-cdc-ai/src/main/java/com/hayden/acp_cdc_ai/acp/events/Events.java`

### PromptContext Update

- [x] T036 [US1] Add `chatId()` cases for `AgentToAgentRequest` → `targetAgentKey`, `AgentToControllerRequest` → controller per-target key, `ControllerToAgentRequest` → `targetAgentKey` in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/prompt/PromptContext.java`

### Decorator Audit

- [x] T037 [US1] Add cases for all three new request types to `FilterPropertiesDecorator.mapRequestToRoute()` → return null in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/decorator/prompt/FilterPropertiesDecorator.java`
- [x] T038 [US1] Add cases for all three new request types to `InterruptLoopBreakerPromptContributorFactory.resolveMapping()` → return null in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/prompt/contributor/InterruptLoopBreakerPromptContributorFactory.java`
- [x] T039 [US1] Add cases for all three new request types to `WeAreHerePromptContributor` → "you are in a conversation with [agent/controller]" context
- [x] T040 [US1] Add cases for all three new request types to `CurationHistoryContextContributorFactory` → include conversation history
- [x] T041 [US1] Audit all `RequestDecorator` implementations — add cases to handle or pass through the three new `AgentRequest` subtypes
- [x] T042 [US1] Audit all `ResultDecorator` implementations — add cases to handle or pass through (result is `String`, not routing record)
- [x] T043 [US1] Verify compilation passes with all new types and decorator cases — run `./gradlew compileJava`

**Checkpoint**: Communication type system complete. All three AgentRequest subtypes, three GraphNode types, and decorator audit in place. Ready for tool implementation.

### Communication Routing & Result Types (FR-014a, FR-014b, FR-014c)

- [x] T043a [P] [US2] Add `AgentCallRouting` record to `AgentModels.java` implementing `AgentRouting` — field: `response` (String). Add `AgentCallResult` record implementing `AgentResult` — fields: `contextId`, `response`, `worktreeContext`. Add `AGENT_CALL` to `AgentType`, add constants to `AgentInterfaces` (`agent-call`, `callAgent`, `communication/agent_call`). Create prompt template `prompts/communication/agent_call.jinja`. Update all exhaustive switches on `AgentRouting` and `AgentResult`.
- [x] T043b [P] [US7] Add `ControllerCallRouting` record to `AgentModels.java` implementing `AgentRouting` — fields: `response` (String), `controllerConversationKey` (String). Add `ControllerCallResult` record implementing `AgentResult` — fields: `contextId`, `response`, `controllerConversationKey`, `worktreeContext`. Add `CONTROLLER_CALL` to `AgentType`, add constants to `AgentInterfaces` (`controller-call`, `callController`, `communication/controller_call`). Create prompt template `prompts/communication/controller_call.jinja`. Update all exhaustive switches.
- [x] T043c [P] [US7] Add `ControllerResponseRouting` record to `AgentModels.java` implementing `AgentRouting` — fields: `response` (String), `controllerConversationKey` (String). Add `ControllerResponseResult` record implementing `AgentResult` — fields: `contextId`, `response`, `controllerConversationKey`, `worktreeContext`. Add `CONTROLLER_RESPONSE` to `AgentType`, add constants to `AgentInterfaces` (`controller-response`, `respondToAgent`, `communication/controller_response`). Create prompt template `prompts/communication/controller_response.jinja`. Update all exhaustive switches.

**Checkpoint (extended)**: Communication routing/result type system complete for all three communication paths (agent→agent, agent→controller, controller→agent).

---

## Phase 4: User Story 9 Support — Interrupt AddMessage Infrastructure (Story 9)

**Goal**: Complete the AddMessage injection pipeline for human-initiated interrupts.

**Independent Test**: Send an interrupt request from the human, verify the agent receives `SomeOf(InterruptRequest)` override via AddMessage, returns InterruptRequest, and system routes via `rerouteToAgentType`.

- [x] T044 [US9] Implement interrupt AddMessage decorators that compose the injected payload: override schema + reason + context + "structured response type has changed" verbiage. Create decorator class(es) in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/decorator/` (FR-028d, FR-030)
- [x] T045 [US9] Wire the AddMessage interrupt injection in `InterruptController.java` to use the new decorators and the existing ACP AddMessage infrastructure — no new LLM call, no new session (FR-028c)
- [x] T046 [US9] Verify end-to-end interrupt flow: REST `rerouteToAgentType` → `InterruptRequestEvent` → AddMessage injection → agent returns `InterruptRequest` → `FilterPropertiesDecorator.resolveTargetRoute()` routes correctly

**Checkpoint**: Full interrupt simplification complete — human-only routing via AddMessage injection.

---

## Phase 5: User Story 1 — List Available Agents (Priority: P1) 🎯 MVP

**Goal**: Agents can discover available peers via `list_agents` tool with topology filtering and self-call exclusion.

**Independent Test**: Open multiple agent sessions, invoke `list_agents` from one, verify only open non-dispatched sessions appear with correct roles and topology filtering.

### Topology Configuration

- [x] T047 [P] [US1] Create `CommunicationTopologyConfig` record with `@ConfigurationProperties(prefix = "multi-agent-ide.topology")` in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/topology/CommunicationTopologyConfig.java` — fields: `maxCallChainDepth` (default 5), `messageBudget` (default 3), `allowedCommunications` Map
- [x] T048 [P] [US1] Add default topology to `multi_agent_ide_java_parent/multi_agent_ide/src/main/resources/application.yml` — include all agent types: workflow agents, COMMIT_AGENT, MERGE_CONFLICT_AGENT, AI_FILTER, AI_PROPAGATOR, AI_TRANSFORMER per `contracts/topology-configuration.md`

### SessionKeyResolutionService

- [x] T049 [US1] Create `SessionKeyResolutionService` in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/service/SessionKeyResolutionService.java` — absorb session key resolution logic from `AiFilterSessionResolver` (FR-013a): scope-based resolution for all SessionMode values, SessionScopeKey cache, lifecycle eviction on GoalCompletedEvent/ActionCompletedEvent
- [x] T050 [US1] Add `resolveSessionForMessage(MessageOutputChannelEvent)` method to `SessionKeyResolutionService` — absorb message-to-session routing from `MultiAgentEmbabelConfig.llmOutputChannel()` lines 208-260 (FR-013b)
- [x] T051 [US1] Add `filterSelfCalls(callingKey, candidateKeys)` method to `SessionKeyResolutionService` — filters candidates that share an actively operating chat session with the caller to prevent session contention (multiple agents can resolve to the same chatId via `PromptContext.chatId()` → `AiFilterSessionResolver` → `resolveSessionKey()`; calling into a busy session would interrupt the active thread and corrupt the structured response). Uses `resolveOwningNodeId()` to map session keys via `ChatSessionCreatedEvent` and detect overlap. Also filters active call-chain cycles via `isInActiveCallChain()` BFS, with `registerCall()`/`unregisterCall()` helpers. NOTE: cycle detection depends on `AgentCallStartedEvent`/`AgentCallCompletedEvent` being emitted from `call_agent` (T059) and `call_controller` (T069) — events are defined but not yet emitted (FR-013c)
- [x] T052 [US1] Add event enrichment to `SessionKeyResolutionService` — include source node, agent type, session mode, and `FromMessageChannelEvent` provenance marker on all published session events (FR-013d)
- [x] T053 [US1] Update `AiFilterSessionResolver` to delegate to `SessionKeyResolutionService` instead of owning the resolution logic — keep `AiFilterSessionResolver` as a thin wrapper for backward compatibility in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/service/AiFilterSessionResolver.java`
- [x] T054 [US1] Update `MultiAgentEmbabelConfig.llmOutputChannel()` to delegate to `SessionKeyResolutionService.resolveSessionForMessage()` instead of inline lambda in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/config/MultiAgentEmbabelConfig.java`

### AgentCommunicationService & list_agents Tool

- [x] T055 [US1] Create `AgentCommunicationService` in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/service/AgentCommunicationService.java` — session queries via `AcpSessionManager`, topology enforcement via `CommunicationTopologyConfig`, delegates to `SessionKeyResolutionService.filterSelfCalls()` for self-call filtering
- [x] T056 [US1] Add `listAvailableAgents(callingKey, callingAgentType)` method to `AgentCommunicationService` — return `List<AgentAvailabilityEntry>` filtered by (topology-permitted) AND (session-open) AND (not self)
- [x] T057 [US1] Add `list_agents` `@Tool`-annotated method to `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AcpTooling.java` with `@SetFromHeader(MCP_SESSION_HEADER)` — delegates to `AgentCommunicationService.listAvailableAgents()`

**Checkpoint**: `list_agents` tool functional — returns open, non-dispatched, topology-permitted agents with self-call filtering.

---

## Phase 6: User Story 2 — Call Another Agent (Priority: P1)

**Goal**: Agents can send messages to other agents via `call_agent` tool with topology validation, busy detection, and error handling.

**Independent Test**: Open two agent sessions, call from one to the other, verify message delivery and response return. Error cases: call unavailable agent, call topology-violating target.

- [x] T058 [US2] Add `validateCall(callingKey, callingType, targetKey, targetType, callChain)` method to `AgentCommunicationService` — checks topology, busy, self-call, loop detection, max depth
- [x] T059 [US2] Add `call_agent` `@Tool`-annotated method to `AgentTopologyTools.java` with `@SetFromHeader(MCP_SESSION_HEADER)` — parameters: targetAgentKey, message. Builds `AgentToAgentRequest`, persists `AgentToAgentConversationNode` via StartWorkflowRequestDecorator, builds `PromptContext`, runs through decorator pipeline + `LlmRunner` with `AgentCallRouting.class` response type. `AgentCallStartedEvent` emitted by StartWorkflowRequestDecorator, `AgentCallCompletedEvent` emitted by WorkflowGraphResultDecorator.
- [x] T060 [US2] Implement error handling in `call_agent`: topology violation, busy, unavailable, loop detected, max depth — return descriptive error strings per contracts/agent-communication-tools.md. `AgentCallCompletedEvent` emitted via WorkflowGraphResultDecorator on all paths.

**Checkpoint**: `call_agent` tool functional — delivers messages, returns responses, rejects invalid calls with clear errors.

---

## Phase 7: User Story 3 — Configurable Communication Topology (Priority: P1)

**Goal**: Topology matrix is configurable and enforced at runtime, refreshable without restart.

**Independent Test**: Define different topology configs, verify `call_agent` enforces correctly.

- [x] T061 [US3] Add runtime reconfiguration support via `CommunicationTopologyProvider` wrapping `CommunicationTopologyConfig` — uses `Binder.get(environment)` for refresh since Spring Cloud `@RefreshScope` is not on the classpath. Provider delegates `isCommunicationAllowed()`, `maxCallChainDepth()`, `messageBudget()` and supports `refresh()` for SC-008. `AgentCommunicationService` updated to use provider instead of config directly.
- [x] T062 [US3] Add topology validation on startup via `@PostConstruct` in `CommunicationTopologyProvider` — logs warning if `allowedCommunications` is empty (no inter-agent communication allowed), logs info with config summary otherwise. Safe default: empty topology = no communication.

**Checkpoint**: Topology configurable, refreshable, and enforced with safe defaults.

---

## Phase 8: User Story 4 — Loop Detection (Priority: P2)

**Goal**: System detects and rejects communication loops by tracking call chains.

**Independent Test**: Set up circular topology, attempt A→B→C→A loop, verify detection and rejection with full chain in error.

- [x] T063 [US4] Cycle detection uses graph-based approach instead of event-based `registerCall`/`unregisterCall`. `SessionKeyResolutionService.buildCallChainFromGraph()` walks `AgentToAgentConversationNode` ancestors. `filterSelfCalls()` uses this chain for cycle detection. No separate `CallChainTracker` needed — `SessionKeyResolutionService` suffices. `AgentTopologyTools.callAgent()` derives the chain from the graph automatically.
- [x] T064 [US4] Loop detection already integrated in `AgentCommunicationService.validateCall()`: (1) `filterSelfCalls(callingKey, Set.of(targetKey), callChain)` for shared-session and graph-derived cycle detection, (2) explicit call chain loop check with `formatCallChain()` for descriptive error message including full chain.

**Checkpoint**: Loop detection functional — direct recursion and indirect loops both caught.

---

## Phase 8b: Call Chain Entry Enrichment (Loop Detection support)

**Goal**: Enrich `CallChainEntry` with target agent info so every hop captures both source and target. Persist the full chain in `AgentToAgentConversationNode` (already has the field — ensure `buildCallChainFromGraph` also reads target info when constructing entries).

- [x] T064a [US4] Add `targetAgentKey` (ArtifactKey) and `targetAgentType` (AgentType) fields to `AgentModels.CallChainEntry` — each hop now records source→target. Update `SessionKeyResolutionService.buildCallChainFromGraph()` to populate the new fields from `AgentToAgentConversationNode.targetAgentKey()`/`targetAgentType()`. Update `AgentCommunicationService.validateCall()` loop detection to also check `targetAgentKey` in the chain. Update `AgentTopologyTools.emitCallEvent()` chain serialization to include target info.

**Checkpoint**: Call chain entries capture both source and target for each hop, improving loop detection accuracy and observability.

---

## Phase 9: User Story 5 — Communication Event Emission (Priority: P2)

**Goal**: Structured events emitted for all communication actions for observability.

**Independent Test**: Perform agent calls, verify correct events emitted with call chains and available agent lists.

- [x] T065 [P] [US5] Add `AgentCallEvent` record and `AgentCallEventType` enum to `multi_agent_ide_java_parent/acp-cdc-ai/src/main/java/com/hayden/acp_cdc_ai/acp/events/Events.java` — fields per contracts/communication-events.md including `checklistAction` string field
- [x] T066 [US5] Add event emission to `call_agent` in `AcpTooling.java` — emit INITIATED on send, RETURNED on success, ERROR on failure. Include full call chain and available agents list.
- [x] T067 [US5] Add `AgentCallEvent` case to `BlackboardHistory` event handling in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/BlackboardHistory.java` (if not auto-handled by existing `GraphEvent` subscription)

**Checkpoint**: Communication events flowing — all call_agent actions produce observable events.

---

## Phase 10: User Story 6 — Agent Communication Prompt Contributor (Priority: P2)

**Goal**: Agents receive communication context (available targets + topology rules) in their prompt.

**Independent Test**: Open agent sessions, verify prompt context includes callable agents and topology rules.

- [x] T068 [US6] Create `AgentTopologyPromptContributorFactory` in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/prompt/contributor/AgentTopologyPromptContributorFactory.java` — implements `PromptContributorFactory`, creates contributor that injects available communication targets and topology rules into agent context by querying `AgentCommunicationService.listAvailableAgents()`

**Checkpoint**: Agents know their communication options without calling `list_agents` first.

---

## Phase 11: User Story 7 — Call Controller with Structured Justification (Priority: P1)

**Goal**: Agents can call the controller via `call_controller` tool, which publishes an interrupt-like request. Controller responds via dedicated REST endpoint.

**Independent Test**: Agent invokes `call_controller` with justification message, controller receives it, responds, agent receives response.

### call_controller Tool

- [x] T069 [US7] Add `call_controller` `@Tool`-annotated method to `AgentTopologyTools.java` with `@SetFromHeader(MCP_SESSION_HEADER)` — parameters: justificationMessage. Builds `AgentToControllerRequest`, persists `AgentToControllerConversationNode`, resolves controller conversation key (create via `root.createChild()` if not found), builds `PromptContext`, runs through decorators + `LlmRunner` with `ControllerCallRouting.class` response type (FR-014a), publishes `HUMAN_REVIEW` interrupt via `PermissionGate.publishInterrupt()`, blocks until resolved, returns response with conversation key. `AgentCallStartedEvent` emitted by `StartWorkflowRequestDecorator`, `AgentCallCompletedEvent` emitted by `WorkflowGraphResultDecorator`.
- [x] T070 [US7] Add message budget tracking to `call_controller` in `AcpTooling.java` — count messages per conversation key, escalate to user when budget exceeded (FR-017)
- [x] T071 [US7] Add event emission to `call_controller` — emit `AgentCallEvent` INITIATED on send, RETURNED on controller response (FR-006). Ensure `AgentCallCompletedEvent` is emitted even on error paths.

### AgentConversationController (REST)

- [x] T072 [US7] Create `AgentConversationController` in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/controller/AgentConversationController.java` — `POST /api/agent-conversations/respond` endpoint. Uses `@RequestBody` only (GC-001). Implements controller conversation key resolution algorithm: validate if provided, lookup by targetAgentKey, create if not found. Builds `ControllerToAgentRequest`, persists `ControllerToAgentConversationNode`, emits `Artifact.ControllerChecklistTurn` if `checklistAction` present, runs through decorator pipeline with `ControllerResponseRouting.class` response type (FR-014a), delivers to agent session, resolves pending interrupt
- [x] T073 [US7] Add `POST /api/agent-conversations/list` endpoint to `AgentConversationController` — accepts `{ "nodeId": "..." }`, walks ArtifactKey child hierarchy from given nodeId, returns conversation summaries (target key, agent type, message count, last message preview, pending status)

**Checkpoint**: Full controller conversation flow functional — agent calls controller, controller responds, conversation key lifecycle works.

---

## Phase 12: User Story 8 — Structured Conversational Topology via Prompt Contributors (Priority: P1)

**Goal**: Prompt contributor factories detect justification points and inject conversation-structuring prompts.

**Independent Test**: Advance workflow to justification point, verify correct prompt contributor fires with right template.

- [x] T074 [US8] Create `JustificationPromptContributorFactory` in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/prompt/contributor/JustificationPromptContributorFactory.java` — matches on `AgentToControllerRequest`/`ControllerToAgentRequest` types + blackboard history. Inspects: current request type (agent role), blackboard approval status, workflow phase. Injects role-specific justification template: discovery (interpretation + findings mapping), planning (ticket-to-requirement traceability), ticket (change justification + verification summary) (FR-018, FR-019)
- [x] T075 [US8] Add justification templates — discovery, planning, ticket agent templates as prompt resources in `multi_agent_ide_java_parent/multi_agent_ide/src/main/resources/prompts/justification/`

**Checkpoint**: Justification conversations activated by prompt contributors at correct workflow points.

---

## Phase 13: Controller Conversation Polling (Stories 7-8 support)

**Goal**: Lightweight polling for conversation activity + ergonomic conversation management script.

**Independent Test**: Controller subscribes via `poll.py --subscribe`, activity-check detects pending conversation, full poll returns conversation data within 1s of activity.

### Activity Check Endpoint

- [x] T076 [P] [US7] Create `ActivityCheckController` in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/controller/ActivityCheckController.java` — `POST /api/ui/activity-check` with `@RequestBody { "nodeId": "..." }`. Returns `{ pendingPermissions, pendingInterrupts, pendingConversations, hasActivity }`. Fast — no graph traversal, no propagation queries (FR-041)

### poll.py Updates

- [x] T077 [US7] Add `--subscribe` and `--tick` arguments to `skills/multi_agent_ide_skills/multi_agent_ide_controller/executables/poll.py` — `--subscribe <seconds>` sets max wait duration, `--tick <seconds>` sets activity-check interval (FR-042)
- [x] T078 [US7] Implement subscribe loop in `poll.py` — calls `POST /api/ui/activity-check` every tick interval, calls full `poll_once()` on activity detected or timeout (FR-042, FR-043)
- [x] T079 [US7] Extend `poll_once()` in `poll.py` with `═══ CONVERSATIONS ═══` section showing pending agent-to-controller requests and recent controller-to-agent responses (FR-044)

### conversations.py Script

- [x] T080 [P] [US7] Create `skills/multi_agent_ide_skills/multi_agent_ide_controller/executables/conversations.py` — ergonomic CLI per contracts/agent-communication-tools.md: `<nodeId>` lists active conversations (tree-walk), `--last <N>` retrieves last N messages, `--respond --message "..."` responds to pending request, `--pending` shows only unresponded requests (FR-045)

**Checkpoint**: Controller can poll for conversation activity and manage conversations ergonomically.

---

## Phase 14: User Story 10 — Conversational Topology Documents (Priority: P2)

**Goal**: Controller skill maintains living instructional documents for phase-gate review.

**Independent Test**: Controller skill loads, reads `reference.md`, finds all checklist documents.

- [x] T081 [P] [US10] Create `skills/multi_agent_ide_skills/multi_agent_ide_controller/conversational-topology/checklist.md` — general phase-gate review instructions (extract requirements, map to outputs, flag gaps, escalate to user)
- [x] T082 [P] [US10] Create `skills/multi_agent_ide_skills/multi_agent_ide_controller/conversational-topology/checklist-discovery-agent.md` — discovery agent review criteria with ACTION rows (Step/ACTION/Description/Gate columns)
- [x] T083 [P] [US10] Create `skills/multi_agent_ide_skills/multi_agent_ide_controller/conversational-topology/checklist-planning-agent.md` — planning agent review criteria with ACTION rows
- [x] T084 [P] [US10] Create `skills/multi_agent_ide_skills/multi_agent_ide_controller/conversational-topology/checklist-ticket-agent.md` — ticket agent review criteria with ACTION rows
- [x] T085 [P] [US10] Create `skills/multi_agent_ide_skills/multi_agent_ide_controller/conversational-topology/reference.md` — index of all topology documents with descriptions and change history
- [x] T086 [P] [US10] Create `skills/multi_agent_ide_skills/multi_agent_ide_controller/conversational-topology-history/reference.md` — chronological change log (empty initial state)
- [x] T087 [US10] Update `skills/multi_agent_ide_skills/multi_agent_ide_controller/SKILL.md` with full conversational topology instructions (when to consult, how to update, how to register changes)
- [x] T088 [US10] Update `skills/multi_agent_ide_skills/SKILL_PARENT.md` with brief reference to `conversational-topology/` and `conversational-topology-history/` directories

**Checkpoint**: Conversational topology documents in place — controller can follow review criteria at phase gates.

---

## Phase 15: Polish & Cross-Cutting Concerns

**Purpose**: Verification, cleanup, and integration validation across all stories

- [x] T089 Verify full compilation — run `./gradlew compileJava` from `multi_agent_ide_java_parent/`
- [x] T090 Run existing unit tests — `./gradlew test` from `multi_agent_ide_java_parent/`
- [x] T091 Verify `ReviewRequest`/`MergerRequest` completely removed — grep returns 0 hits across codebase
- [x] T092 Verify no agent can self-initiate interrupts — grep for interrupt prompt contributors returns only retained one (`InterruptLoopBreakerPromptContributorFactory`). Verify `RouteBackInterruptPromptContributorFactory` is deleted. Verify `CollectorDecision` has 0 grep hits.
- [x] T093 Verify topology reconfigurable without restart (SC-008) — update `application.yml` topology, confirm next `list_agents` reflects change
- [x] T094 Run quickstart.md verification checklist — validate all items pass

---

## Phase 16: Live Integration Testing (claude profile)

**Purpose**: Deploy to tmp repo with claude profile, run real goals, and verify each user story's acceptance scenarios against live agent behavior.

**Pre-requisites**: All previous phases complete. Deploy via `clone_or_pull.py` + `deploy_restart.py --profile claude`. Use `poll.py --subscribe 600` for all monitoring.

### Group A: Review Justification Dialogue + Controller Checklists (TOP PRIORITY)

**Goal**: Verify that every workflow agent calls `call_controller` for justification before completing, and that the controller can use its `conversational-topology/` checklists to conduct structured review.

- [ ] T095 Deploy to tmp repo with claude profile — `clone_or_pull.py` + `deploy_restart.py --profile claude`
- [ ] T096 Submit a simple goal and confirm workflow starts (orchestrator READY, discovery agents dispatched)
- [ ] T097 [US7/US8/US10] Observe first agent (discovery orchestrator or discovery agent) calling `call_controller` with justification message — verify conversation appears in `conversations.py --pending`
- [ ] T098 [US10] When justification arrives, controller loads `conversational-topology/checklist.md` (general) + `checklist-discovery-agent.md` (agent-specific) and uses the ACTION table to review the agent's justification. Respond with checklist-driven questions via `conversations.py --respond --message "..."`. Verify the agent responds to the questions and the dialogue continues.
- [ ] T099 [US10] Repeat checklist-driven review at planning gate — load `checklist-planning-agent.md`, apply ACTION table, ask justification questions, verify agent responds
- [ ] T100 [US10] Repeat checklist-driven review at ticket gate — load `checklist-ticket-agent.md`, apply ACTION table (READ_DIFF, CHECK_COMPLETENESS, etc.), verify agent addresses each point
- [ ] T101 [US10] Identify any gaps or improvements needed in the checklists based on the actual justification dialogues — update checklist files and log changes in `conversational-topology-history/reference.md`

### Group B: US9 — Simplified Interrupts (P0)

**Goal**: During running workflow, test interrupt behavior.

- [ ] T102 [US9] 9.3/9.4 — Passive verification: monitor event stream during discovery phase, confirm no `ReviewRequest`/`MergerRequest` in any routing object serialization and no agent self-initiates an interrupt
- [ ] T103 [US9] 9.1/9.5 — Send HUMAN_REVIEW interrupt to a running agent with `rerouteToAgentType`, verify the agent receives a filtered schema with only the interrupt request field and prompt contributors are injected
- [ ] T104 [US9] 9.2 — After interrupt injection, verify agent returns result with only interrupt field populated and system routes to the correct agent type
- [ ] T105 [US9] 9.8 — Observe collector returning normal result during workflow, verify only forward-routing fields populated and workflow advances (no `CollectorDecision`)
- [ ] T106 [US9] EC.5 — Send HUMAN_REVIEW interrupt with null `rerouteToAgentType` via direct API call, verify 400 error response

### Group C: US1-3 — Agent Discovery & Topology (P1)

**Goal**: During a running workflow, observe agents using list_agents and call_agent tools.

- [ ] T107 [US1] 1.1 — During running workflow, observe an agent using `list_agents` tool in event stream, verify it returns open sessions with roles and reachability
- [ ] T108 [US1] 1.2 — After a dispatched agent (discovery/ticket) completes, verify it no longer appears in `list_agents` results
- [ ] T109 [US1] 1.3 — Verify topology filtering: worker agents see only topology-permitted targets in `list_agents`
- [ ] T110 [US2] 2.1 — Observe a successful `call_agent` interaction between two agents in the event stream, verify message delivered and response returned
- [ ] T111 [US2] 2.3/2.4 — Observe or provoke a topology violation or failed call, verify error returned with AgentCallErrorEvent emitted
- [ ] T112 [US3] 3.1 — Verify default topology enforced at runtime: workers can call orchestrator/collector, orchestrators can call any agent at or below their level

### Group D: US7-8 — Call Controller & Justification Mechanics (P1)

**Goal**: Verify the technical plumbing of call_controller, message budgets, and prompt contributor firing.

- [ ] T113 [US7] 7.3 — Observe message budget enforcement: after k messages without alignment, system escalates to user (HUMAN_REVIEW interrupt published)
- [ ] T114 [US8] 8.1 — At discovery→planning gate, verify `JustificationPromptContributorFactory` fires and injects justification template into discovery agent context
- [ ] T115 [US8] 8.2 — At planning→tickets gate, verify justification prompt fires with traceability template
- [ ] T116 [US8] 8.3 — At tickets→completion gate, verify justification prompt fires with diff review template
- [ ] T117 [US8] 8.4 — After controller approves justification, verify prompt contributor does not fire again on next agent call

### Group E: US4-6 — Loop Detection, Events, Prompt Contributor (P2)

**Goal**: Observe event emission and prompt enrichment during agent-to-agent calls.

- [ ] T118 [US5] 5.1 — During a `call_agent` interaction, verify `AgentCallEvent` with `INITIATED` and `RETURNED` types emitted in event stream with call chain
- [ ] T119 [US5] 5.2 — On a failed call, verify `AgentCallErrorEvent` emitted with route, chain, and error details
- [ ] T120 [US4] 4.1/4.2 — If a loop scenario arises (or can be provoked via corrective message), verify system detects and rejects with full chain in error
- [ ] T121 [US6] 6.1 — Inspect agent prompt context (via propagation payload or log) for topology information injected by `AgentTopologyPromptContributorFactory`

### Group F: US9 — Route-Back via Controller Conference (P0, advanced)

**Goal**: Test the route-back mechanism through controller conferences. Requires workflow to reach a collector phase gate.

- [ ] T122 [US9] 9.6 — At a collector conference, respond with `routeBack=true` via `AgentConversationController`, verify route-back schema injected via AddMessage and collector routes back to target orchestrator
- [ ] T123 [US9] 9.7 — Attempt `routeBack=true` on a non-collector agent conference, verify error returned indicating route-back not supported for that agent type

### Group G: Edge Cases (test opportunistically)

- [ ] T124 EC.1 — Verify self-call rejected via `SessionKeyResolutionService.filterSelfCalls()` (observe in logs or provoke via corrective message)
- [ ] T125 EC.2 — Target session closes between list_agents and call_agent — verify graceful error
- [ ] T126 EC.3 — Empty/missing topology config — verify safe default (no communication allowed) and warning logged
- [ ] T127 EC.4 — Call chain exceeds max depth — verify rejection

**Checkpoint**: All live integration tests pass — review justification dialogues verified with checklists, feature validated end-to-end with real LLM agents.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — can start immediately
- **Phase 2 (Interrupt Simplification)**: Depends on Phase 1 — **BLOCKS all subsequent phases**
- **Phase 3 (Communication Foundation)**: Depends on Phase 2 — **BLOCKS all tool phases**
- **Phase 4 (Interrupt AddMessage)**: Depends on Phase 2, can run parallel with Phase 3
- **Phase 5 (US1 list_agents)**: Depends on Phase 3
- **Phase 6 (US2 call_agent)**: Depends on Phase 5 (needs AgentCommunicationService)
- **Phase 7 (US3 Topology Config)**: Depends on Phase 5 (extends topology config)
- **Phase 8 (US4 Loop Detection)**: Depends on Phase 6 (extends call_agent validation)
- **Phase 9 (US5 Events)**: Depends on Phase 6 (emits events from call_agent)
- **Phase 10 (US6 Prompt Contributor)**: Depends on Phase 5 (queries AgentCommunicationService)
- **Phase 11 (US7 Call Controller)**: Depends on Phase 3 + Phase 5
- **Phase 12 (US8 Justification Prompts)**: Depends on Phase 11
- **Phase 13 (Polling)**: Depends on Phase 11 (needs conversation endpoints)
- **Phase 14 (US10 Topology Docs)**: No code dependencies — can start after Phase 2
- **Phase 15 (Polish)**: Depends on all desired phases being complete

### User Story Dependencies

```
US9 (P0 — Interrupt Simplification)
 └── Foundation (Phase 3)
      ├── US1 (P1 — list_agents)
      │    ├── US2 (P1 — call_agent)
      │    │    ├── US4 (P2 — Loop Detection)
      │    │    └── US5 (P2 — Events)
      │    ├── US3 (P1 — Topology Config)
      │    └── US6 (P2 — Prompt Contributor)
      └── US7 (P1 — Call Controller)
           ├── US8 (P1 — Justification Prompts)
           └── Polling (Phase 13)

US10 (P2 — Topology Docs) — independent after US9
```

### Parallel Opportunities

**Within Phase 2 (Interrupt Simplification)**:
- T005, T006 (delete annotation files) can run in parallel
- T013, T014, T022a (delete prompt contributor files) can run in parallel
- T022d, T022e (add @SkipPropertyFilter annotations) can run in parallel after T003/T004
- T022b, T022c (CollectorDecision removal) are sequential (remove field, then remove usages)

**Within Phase 3 (Foundation)**:
- T027, T028, T029, T030, T031 (new AgentRequest subtypes) can run in parallel
- T032, T033, T034 (new GraphNode types) can run in parallel

**Within Phase 5 (list_agents)**:
- T047, T048 (topology config + yml) can run in parallel

**Within Phase 14 (Topology Docs)**:
- T081-T086 (all document creation) can run in parallel

**Cross-phase parallelism**:
- Phase 4 (Interrupt AddMessage) can run parallel with Phase 3
- Phase 14 (Topology Docs) can run parallel with Phases 5-13

---

## Implementation Strategy

### MVP First (Stories 9 + 1 + 2)

1. Complete Phase 1: Setup
2. Complete Phase 2: Interrupt Simplification (P0) — **CRITICAL GATE**
3. Complete Phase 3: Communication Foundation
4. Complete Phase 5: US1 list_agents
5. Complete Phase 6: US2 call_agent
6. **STOP and VALIDATE**: `list_agents` returns correct agents, `call_agent` delivers messages with topology enforcement
7. Deploy/demo if ready

### Incremental Delivery

1. Setup + Interrupt Simplification → P0 gate passed
2. Foundation + US1 (list_agents) + US2 (call_agent) → Core communication working (MVP!)
3. US3 (Topology Config) + US4 (Loop Detection) → Safety mechanisms in place
4. US7 (Call Controller) + US8 (Justification) → Structured conversations working
5. US5 (Events) + US6 (Prompt Contributor) → Observability and agent context
6. Polling (Phase 13) → Real-time controller experience
7. US10 (Topology Docs) → Controller review guidance
8. Polish → Verification and cleanup
