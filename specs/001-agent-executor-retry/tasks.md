# Tasks: Centralize LLM Execution with Blackboard-History-Driven Retry

**Input**: Design documents from `/specs/001-agent-executor-retry/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/internal-contracts.md

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- `LIB` = `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib`
- `APP` = `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide`
- `ACP` = `multi_agent_ide_java_parent/acp-cdc-ai/src/main/kotlin/com/hayden/acp_cdc_ai/acp`

## Phase 1: Setup

**Purpose**: No new project structure needed — this feature modifies existing modules. Setup is limited to branch verification.

- [x] T001 Verify `001-agent-executor-retry` branch is checked out and all submodules are up to date across `multi_agent_ide_java_parent/multi_agent_ide`, `multi_agent_ide_java_parent/multi_agent_ide_lib`, and `multi_agent_ide_java_parent/acp-cdc-ai`

---

## Phase 2: Foundational (Data Model — Blocking Prerequisites)

**Purpose**: Create the `ErrorDescriptor` sealed hierarchy, `ErrorTemplates`, `RetryAware` interface, and extend `BlackboardHistory`, `PromptContext`, and `DecoratorContext` with the `errorDescriptor` field. These types are required by ALL user stories.

- [x] T002 [P] Create `ErrorDescriptor` sealed interface with variants (`NoError`, `CompactionError`, `ParseError`, `TimeoutError`, `UnparsedToolCallError`, `NullResultError`, `IncompleteJsonError`) and `CompactionStatus` enum (`NONE`, `FIRST`, `MULTIPLE`) in `LIB/agent/ErrorDescriptor.java`. `ErrorDescriptor` must implement `HasContextId` so it can be stored as a `BlackboardHistory.Entry`. All error variants (except `NoError`) include an `actionName: String` field (auto-detected from last BlackboardHistory entry or AgentExecutorStartEvent). `CompactionError` also has `compactionStatus` and `compactionCompleted`. Added `ErrorContext` for cumulative error history chain.
- [x] T003 [P] Create `ErrorTemplates` record with nullable fields `compactionFirstTemplate`, `compactionMultipleTemplate`, `parseErrorTemplate`, `defaultRetryTemplate` in `LIB/agent/ErrorTemplates.java`
- [x] T004 [P] Create `RetryAware` interface (standalone, not extending PromptContributor) with default-false methods `includeOnCompaction(CompactionError)`, `includeOnCompactionMultiple(CompactionError)`, `includeOnParseError(ParseError)`, `includeOnTimeout(TimeoutError)`, `includeOnUnparsedToolCall(UnparsedToolCallError)`, `includeOnNullResult(NullResultError)`, `includeOnIncompleteJson(IncompleteJsonError)` in `LIB/prompt/RetryAware.java`. This interface can be implemented by both `PromptContributor` and `PromptContributorFactory` implementations.
- [x] T005 Add `errorDescriptor` field (type `ErrorDescriptor`, default `NoError`) to `PromptContext` record in `LIB/prompt/PromptContext.java`. Use `@Builder.Default` for the default value.
- [x] T006 Add `errorDescriptor` field (type `ErrorDescriptor`) to `DecoratorContext` record in `LIB/agent/DecoratorContext.java`. Add an overloaded constructor that defaults to `NoError` for backwards compatibility with existing callers.
- [x] T007 Add error state methods to `BlackboardHistory` in `LIB/agent/BlackboardHistory.java`: `addError(ErrorDescriptor)` stores the error as a history entry, `errorType()` returns the most recent `ErrorDescriptor` (or `NoError`), `compactionStatus()` scans entries for `CompactionError` instances (0→NONE, 1→FIRST, 2+→MULTIPLE), `errorCount(Class<? extends ErrorDescriptor>)` counts errors of a specific type. **Fixed `onEvent` guard to use `ArtifactKey.isDescendantOf` instead of strict equality so child-nodeId events (executor start/complete) reach the blackboard.**
- [x] T008 Add `errorTemplates` field (type `ErrorTemplates`, nullable) to the `AgentActionMetadata` record in `APP/agent/AgentInterfaces.java`. Update all existing `AgentActionMetadata` constants to pass `null` for `errorTemplates` (no change in behavior).

**Checkpoint**: Foundation ready — all new types compile, existing tests pass with no behavioral change. ✅

---

## Phase 3: User Story 1 — Centralize All LLM Calls in AgentExecutor (Priority: P1)

**Goal**: Move all ~17 `llmRunner.runWithTemplate(...)` calls from agent action methods in `AgentInterfaces` into `AgentExecutor.run()`. Zero behavioral change on the happy path.

**Independent Test**: Run any agent action (orchestrator, discovery, planning, ticket) and verify output is identical. No direct `llmRunner` calls remain in `AgentInterfaces`.

### Implementation for User Story 1

- [x] T009 [US1] Audit all `llmRunner.runWithTemplate(...)` call sites in `APP/agent/AgentInterfaces.java` — list every action method, the `AgentActionMetadata` constant it maps to, the tool context it builds, and any per-action template model setup. Document findings as comments in AgentExecutor or in a temporary audit note.
- [x] T010 [US1] Ensure `AgentExecutor.run()` in `APP/service/AgentExecutor.java` can accept all tool context variations used by the ~17 actions. If any action builds a specialized `ToolContext` (e.g., with `contextManagerTools`, `discoveryTools`), verify that `AgentExecutorArgs.baseToolContext` supports passing these through. Adjust `AgentExecutorArgs` if needed.
- [x] T011 [US1] Migrate the **workflow agent** LLM-calling actions to `AgentExecutor.run()` in `APP/agent/AgentInterfaces.java`: `handleStuck` (CONTEXT_MANAGER_STUCK), `contextManager` (CONTEXT_MANAGER_REQUEST), `coordinateWorkflow` (COORDINATE_WORKFLOW). For each: construct `AgentExecutorArgs` from the existing local variables and replace the inline `decorateRequest` → `buildPromptContext` → `llmRunner.runWithTemplate` → `decorateRouting` block with a single `agentExecutor.run(args)` call.
- [x] T012 [US1] Migrate the **collector** actions to `AgentExecutor.run()`: `consolidateWorkflowOutputs` (CONSOLIDATE_WORKFLOW_OUTPUTS), `consolidateDiscoveryFindings` (CONSOLIDATE_DISCOVERY_FINDINGS), `consolidatePlansIntoTickets` (CONSOLIDATE_PLANS_INTO_TICKETS), `consolidateTicketResults` (CONSOLIDATE_TICKET_RESULTS).
- [x] T013 [US1] Migrate the **orchestrator** actions to `AgentExecutor.run()`: `kickOffDiscoveryAgents` (KICK_OFF_DISCOVERY_AGENTS), `decomposePlan` (DECOMPOSE_PLAN), `orchestrateTicketExecution` (ORCHESTRATE_TICKET_EXECUTION).
- [x] T014 [US1] Migrate the **dispatch** actions to `AgentExecutor.run()`: `dispatchDiscoveryAgentRequests` (DISPATCH_DISCOVERY_AGENTS), `dispatchPlanningAgentRequests` (DISPATCH_PLANNING_AGENTS), `dispatchTicketAgentRequests` (DISPATCH_TICKET_AGENTS).
- [x] T015 [US1] Migrate the **subagent** actions to `AgentExecutor.run()`: `runTicketAgent` (RUN_TICKET_AGENT), `runPlanningAgent` (RUN_PLANNING_AGENT — if exists), `runDiscoveryAgent` (RUN_DISCOVERY_AGENT — if exists). These may have slightly different patterns (e.g., subagent-specific tool contexts); adapt `AgentExecutorArgs` as needed.
- [x] T016 [US1] Remove now-unused helper methods from `AgentInterfaces.java`: `buildPromptContext(...)`, `buildToolContext(...)`, `decorateRequest(...)`, `decorateRouting(...)` — only if ALL call sites have been migrated. If any non-LLM call sites still use these helpers (e.g., routing-only actions), keep them.
- [ ] T052 [US1] Move `LlmCallDecorator` pipeline from `DefaultLlmRunner` into `DecorateRequestResults`: add `DecorateLlmCallArgs` record (matching arg-record pattern), add `@Autowired(required=false) @Lazy List<LlmCallDecorator>`, add `decorateLlmCall()` method. Remove `LlmCallDecorator` loop and field from `DefaultLlmRunner`.
- [ ] T053 [US1] Move prompt contributor resolution from `DefaultLlmRunner` (lines 63-66) into `AgentExecutor.run()` — call `promptContributorService.getContributors(promptContext)` in AgentExecutor between prompt context decoration and the LLM call. Update `DefaultLlmRunner.runWithTemplate()` to accept pre-resolved prompt elements (or have AgentExecutor pass them through).
- [ ] T054 [US1] Simplify `DefaultLlmRunner` — remove `PromptContributorService` dependency, remove `LlmCallDecorator` list. It becomes a thin pass-through: resolve ACP options, build AI query with prompt elements, apply tool context, execute `fromTemplate()`. Update `LlmRunner` interface signature as needed (no deprecation of old methods).
- [x] T055 [US1] Emit `AgentExecutorStartEvent` before LLM call and `AgentExecutorCompleteEvent` after successful result in `AgentExecutor.run()`. Use existing `EventBus` (line 85). Fields: sessionKey (from promptContext.chatId()), actionName (from meta.actionName()), nodeId (unique per execution attempt via requestContextId.createChild()), requestContextId (from promptContext.currentContextId()), startNodeId (on complete event, links back to start's nodeId).
- [ ] T056 [US1] Update integration and unit tests: integration tests that mock `DefaultLlmRunner` will now have `LlmCallDecorators` running (since they're in `DecorateRequestResults`/`AgentExecutor`). Verify decorators execute. Update `DefaultLlmRunner` unit tests for simplified scope.
- [x] T017 [US1] Verify: grep for `llmRunner.runWithTemplate` in `APP/agent/` — zero results expected. Run existing unit tests to confirm no behavioral regressions.

**Checkpoint**: All LLM calls go through `AgentExecutor.run()`. AgentInterfaces action methods are slim delegation methods. Existing behavior is identical. ✅ (core migration complete; T052-T054 LlmCallDecorator/prompt contributor extraction deferred)

---

## Phase 4: User Story 2 — Blackboard History Error and Compaction State Tracking (Priority: P2)

**Goal**: Implement `ActionRetryListener` as a Spring bean that captures errors into `BlackboardHistory`. For compaction errors, poll for compaction completion before returning.

**Independent Test**: Simulate an LLM error → verify `BlackboardHistory` records the correct `ErrorDescriptor` and reports the right `compactionStatus()` and `errorType()`.

### Implementation for User Story 2

- [x] T018 [US2] Create `ActionRetryListenerImpl` as a `@Component` implementing `com.embabel.agent.spi.common.ActionRetryListener` in `APP/service/ActionRetryListenerImpl.java`. In `onActionRetry(RetryContext, Throwable, AgentProcess)`: extract `BlackboardHistory` from `agentProcess` (which implements `Blackboard`). Find last unmatched `AgentExecutorStartEvent` via `findLastUnmatchedStart(history)` — matches by `startEvent.nodeId()` ↔ `completeEvent.startNodeId()` to handle retries correctly. Classify the throwable into an `ErrorDescriptor` variant, call `blackboardHistory.addError(errorDescriptor)`.
- [x] T019 [US2] Implement throwable classification logic in `ActionRetryListenerImpl`: message contains "compacting"/"Compacting" or "Prompt is too long" → `CompactionError`; `JsonParseException` or message contains "parse" → `ParseError`; `TimeoutException` or message contains "timeout" → `TimeoutError`; contains "tool call" → `UnparsedToolCallError`; contains "null result" → `NullResultError`; contains "incomplete" JSON → `IncompleteJsonError`; otherwise → `ParseError` with raw message as fallback.
- [x] T020 [US2] Implement compaction wait behavior in `ActionRetryListenerImpl.onActionRetry()`: when the classified error is `CompactionError`, poll the ACP session for compaction completion (max 20 polls, 10s interval) before returning. Uses start event's nodeId as parent context. Once "compaction completed" is seen, set `compactionCompleted=true` on the `CompactionError` and return.
- [x] T021 [US2] Create `CompactionException` (extends `RuntimeException`) in `ACP/CompactionException.kt` or `LIB/agent/CompactionException.java` — typed exception thrown by `AcpChatModel` when compaction is detected. This makes classification in `ActionRetryListenerImpl` clean (`instanceof` check rather than message parsing).
- [x] T022 [US2] Write unit tests for `BlackboardHistory.addError()`, `errorType()`, `compactionStatus()`, `errorCount()` — verify: adding `CompactionError` once → `FIRST`, twice → `MULTIPLE`; adding `ParseError` → `errorType()` returns it; no errors → `NoError` and `NONE`. Also added tests for `findFirstUnmatchedStartIndex(sessionKey)` scoping and `errorContext` accumulation across retries.
- [x] T023 [US2] Write unit test for `ActionRetryListenerImpl` — verify: (a) correct `ErrorDescriptor` variant is classified and added for each throwable type (all 6 error types), (b) `findLastUnmatchedStart` matches by `nodeId`↔`startNodeId` (not `requestContextId`), (c) compaction polling targets the resolved session key. Also added `differentStartNodeId_doesNotCrossMatch` test.

**Checkpoint**: Errors from the framework retry loop are captured in BlackboardHistory with correct classification. Compaction waits for completion before retry fires. ✅

---

## Phase 4b: Session-Level Retry Context (ACP Layer)

**Purpose**: Track per-session retry state for errors that don't propagate to the framework retry. Uses `AgentExecutorStartEvent`/`AgentExecutorCompleteEvent` pair to bracket each executor attempt, and counts retries between them.

- [x] T044 [P] Create `AcpSessionRetryContext` in `ACP/AcpSessionRetryContext.kt` (or `.java`) — per-session retry state holder with fields: `sessionKey: String`, `currentError: ErrorDescriptor` (default `NoError`), `compactionStatus: CompactionStatus` (default `NONE`), `retryCount: int` (default 0). Methods: `recordError(ErrorDescriptor)` (increments retryCount, updates compactionStatus if CompactionError), `clearOnSuccess()` (resets all to defaults), `isRetry(): boolean`, `errorType(): ErrorDescriptor`, `compactionStatus(): CompactionStatus`.
- [x] T045 [P] Add `AgentExecutorStartEvent` and `AgentExecutorCompleteEvent` to Events — fields: `sessionKey`, `actionName`, `nodeId`, `requestContextId`. `AgentExecutorCompleteEvent` also has `startNodeId` for correct start↔complete pairing across retries.
- [ ] T051 [P] Fix `ChatSessionClosedEvent` in Events.java — add a separate `sessionId` (or `chatId`) field for the actual chat session ID (`chatModelKey`). The `nodeId` should be created as a child of that sessionId (for event hierarchy consistency). Update `AcpRetryEventListener` to use `sessionId` for cleanup, not `nodeId`. Update all existing callers that construct this event.
- [x] T046 Emit `AgentExecutorStartEvent` and `AgentExecutorCompleteEvent` in `AgentExecutor.run()` (not DefaultLlmRunner) — emit start before step 4 (LLM call) and complete after successful result. AgentExecutor already has EventBus (line 85). These events serve as the authoritative signal for which action is executing (actionName field used by ActionRetryListenerImpl to scope error descriptors).
- [x] T047 Create `AcpRetryEventListener` as `@Component` implementing `EventListener` in `ACP/events/AcpRetryEventListener.java`. Maintains `ConcurrentHashMap<String, AcpSessionRetryContext>`. Subscribes to: `ChatSessionCreatedEvent` (create entry), `AgentExecutorStartEvent` (reset retry state for new attempt), `CompactionEvent` (record CompactionError), `NodeErrorEvent` (record error), `AgentExecutorCompleteEvent` (clear retry state on success), `ChatSessionClosedEvent` (remove entry). Exposes `retryContextFor(sessionKey)` for AcpChatModel to query.
- [ ] T048 Inject `AcpRetryEventListener` into `AcpChatModel.kt`. Before calling `doPerformPrompt`, check `retryListener.retryContextFor(sessionKey).isRetry()` — if true, send CONTINUE instead of full prompt. Destructure `errorType()` to decide specific behavior (compaction → CONTINUE + wait, parse/timeout → CONTINUE).
- [x] T049 Write unit tests for `AcpSessionRetryContext` — verify: `recordError(CompactionError)` increments retryCount and updates compactionStatus; `clearOnSuccess()` resets all fields; `isRetry()` returns true after error, false after clear.
- [x] T050 Write unit test for `AcpRetryEventListener` — mock event stream: `ChatSessionCreatedEvent` → `AgentExecutorStartEvent` → `CompactionEvent` → verify `retryContextFor()` shows retry state → `AgentExecutorCompleteEvent` → verify retry state cleared.

**Checkpoint**: Session-level retry context tracks errors within individual `invokeChat` calls. ✅ (core tracking complete; T048 AcpChatModel integration and T051 ChatSessionClosedEvent fix deferred)

---

## Phase 5: User Story 3 — Retry-Aware Template and Prompt Contributor Selection (Priority: P3)

**Goal**: Make `AgentExecutor.run()` query error state from BlackboardHistory, set `ErrorDescriptor` on `PromptContext` (single source of truth), resolve effective template by destructuring it, and filter prompt contributors via `RetryAware`.

**Independent Test**: Set up a `BlackboardHistory` with known error state → invoke `AgentExecutor.run()` → verify correct template selected and correct contributors filtered.

### Implementation for User Story 3

- [x] T024 [US3] Modify `AgentExecutor.run()` in `APP/service/AgentExecutor.java`: after building `BlackboardHistory` (step 2 in the existing pipeline), query `history.errorType()` to build the `ErrorDescriptor`. Set it on the `PromptContext` (via `withErrorDescriptor()` or builder) and `DecoratorContext`. This is the single source of truth — all downstream logic destructures this field.
- [x] T025 [US3] Add template resolution logic in `AgentExecutor.run()`: `resolveTemplate(meta, errorDescriptor)` returns `ResolvedTemplate` sealed interface (`NoErrorTemplate` → normal model, `ErrorTemplate` → error-specific template + model with `errorDetail`). Error templates read from `meta.errorTemplates()` with `ErrorTemplates.DEFAULT` providing generic retry templates (`retry/compaction`, `retry/parse_error`, etc.). Added `errorDetail` field to all `ErrorDescriptor` error variants populated from `throwable.getMessage()`.
- [x] T026 [US3] Centralize prompt assembly in `PromptContributorService` — two-level RetryAware filtering in `assemblePromptText()`: (1) Factory level — before calling `factory.create()`, check if factory implements `RetryAware`; if error state active and factory returns `false` → skip factory entirely. (2) Contributor level — after assembly and sort: if contributor does NOT implement `RetryAware` → exclude; if it does → call matching `includeOn*(error)` method. The `PromptContext.errorDescriptor()` is the source — no new parameters needed.
- [x] T027 [US3] Update `AgentExecutor.controllerExecution()` and `controllerResponseExecution()` in `APP/service/AgentExecutor.java` with error descriptor propagation — `errorDescriptor` set on `DecoratorContext` via `history.errorType(PromptContext.chatId(enrichedRequest).value())` in all three execution paths (run, controllerExecution, controllerResponseExecution).
- [ ] T028 [US3] Write unit test for `AgentExecutor.run()` retry-aware behavior: mock `BlackboardHistory` to return `CompactionError(FIRST)` → verify template is resolved from `errorTemplates`, non-`RetryAware` contributors are excluded, `RetryAware` contributors have `includeOnCompaction()` called.
- [ ] T029 [US3] Write unit test for the `NoError` path: verify no filtering is applied, default template is used, all contributors are included.

**Checkpoint**: AgentExecutor adapts template and contributor selection based on error state. The `ErrorDescriptor` on `PromptContext` is the single source of truth for all retry decisions. ✅ (T024/T025/T026/T027 done; T028/T029 unit tests deferred to integration test phase)

---

## Phase 5b: requestContextId vs nodeId Semantics and Test Matrix

**Purpose**: Fix identity semantics so retries are correctly tracked, and validate with comprehensive test matrix.

- [x] T057 Fix `requestContextId` vs `nodeId` semantics in `AgentExecutor.run()`: `requestContextId` = `promptContext.currentContextId()` (stable across retries), `nodeId` = `requestContextId.createChild()` (unique per execution attempt).
- [x] T058 Add `startNodeId` field to `AgentExecutorCompleteEvent` for correct start↔complete pairing across retries. Update `ActionRetryListenerImpl.findLastUnmatchedStart()` and `BlackboardHistory.findFirstUnmatchedStartIndex()` / `isLastExecutionComplete()` to match by `nodeId`↔`startNodeId` instead of `requestContextId`.
- [x] T059 Fix `BlackboardHistory.onEvent()` guard: replace strict `nodeId.equals(event.nodeId())` with `ArtifactKey.isDescendantOf` check so executor start/complete events with child nodeIds reach the blackboard.
- [x] T060 Update test matrix: SURFACE.md (S11-S14), INVARIANTS.md (S-INV11-14), EXPLORATION.md (F-E19-22). Add integration test assertions for S-INV11 in `WorkflowAgentQueuedTest`.
- [x] T061 Add trace data improvements: `startNodeId` in `CliEventFormatter`, `errorContext` chain in `QueuedLlmRunner` blackboard dump.
- [x] T062 Verify all 6 error types recorded in blackboard via integration test trace data (ParseError, IncompleteJsonError, TimeoutError, UnparsedToolCallError, NullResultError, CompactionError).

**Checkpoint**: Identity semantics correct, all events reach blackboard, trace data confirms error recording. ✅

---

## Phase 6: User Story 4 — Remove Retry Logic from AcpChatModel (Priority: P4)

**Goal**: Remove all retry loops from `AcpChatModel`. Convert compaction handling to detect-and-throw. AcpChatModel becomes a clean pass-through.

**Independent Test**: Trigger a compaction → verify AcpChatModel throws `CompactionException` instead of polling. Verify no retry loops remain.

### Implementation for User Story 4

- [ ] T030 [US4] Remove the null/empty retry loop from `AcpChatModel.invokeChat()` in `ACP/AcpChatModel.kt` (lines ~238-255): the `while` loop that sends "Please continue." up to 5 times with `Thread.sleep(5_000)`. If the response is null/empty, let it propagate — the framework retry + `ActionRetryListener` will handle it.
- [ ] T031 [US4] Remove the unparsed tool call retry from `AcpChatModel.invokeChat()` in `ACP/AcpChatModel.kt` (lines ~257-280): the block that detects `detectUnparsedToolCallInLastMessage` and sends a corrective message. Instead, throw an appropriate exception that `ActionRetryListenerImpl` classifies as `UnparsedToolCallError`.
- [ ] T032 [US4] Remove `handleIncompleteJson()` method and its call site from `AcpChatModel.kt` (lines ~286-289, ~359-428). Incomplete JSON is a parse error — let it propagate as an exception for `ActionRetryListenerImpl` to classify.
- [ ] T033 [US4] Convert `handleCompactingSession()` in `AcpChatModel.kt` to detect-and-throw: keep `isSessionCompacting()` for detection. When compaction is detected, emit `CompactionEvent` via EventBus, then throw `CompactionException`. Remove the polling loop (lines ~325-348). The `ActionRetryListenerImpl` handles the compaction wait.
- [ ] T034 [US4] Remove the hash-based retry detection and `isRetry` parameter from `AcpChatModel.kt`: remove `lastPromptHashPerSession` map and SHA-256 prompt hashing (lines ~143-156), remove `isRetry` parameter from `invokeChat()` and the CONTINUE logic (lines ~205-211). Retry detection is now handled by `AcpRetryEventListener.retryContextFor(sessionKey).isRetry()` (injected in Stage 3). Update all callers of `invokeChat()` to remove the `isRetry` argument.
- [ ] T035 [US4] Keep `sanitizeGenerationText()`, `stripMarkdownCodeFences()`, and `isSessionCompacting()` in `AcpChatModel.kt` — these are response cleanup and detection utilities, not retry logic.
- [ ] T036 [US4] Verify: grep `AcpChatModel.kt` for `while`, `Thread.sleep`, `retry`, `nullRetryCount`, `continueCount`, `pollCount` — zero results expected (except in comments if any). Run existing tests.

**Checkpoint**: AcpChatModel is a clean pass-through. Errors are thrown as typed exceptions. All retry behavior is in the ActionRetryListener + AgentExecutor pipeline.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Mark existing prompt contributors with `RetryAware`, add error templates to key actions, final validation.

- [ ] T037 Implement `RetryAware` on `JsonOutputFormatPromptContributorFactory` (factory level) and its inner `JsonOutputFormatPromptContributor` (contributor level) in `LIB/prompt/contributor/JsonOutputFormatPromptContributorFactory.java` — both return `true` for ALL `includeOn*()` methods (always included on retry). This is the formatting instructions contributor (priority 10001).
- [ ] T038 [P] Implement `RetryAware` on `FilteredPromptContributorAdapterFactory` in `APP/filter/prompt/FilteredPromptContributorAdapterFactory.java` — return `true` for all `includeOn*()` methods (filter policies still apply during retry).
- [ ] T039 [P] Implement `RetryAware` on `WeAreHerePromptContributor` in `LIB/prompt/contributor/WeAreHerePromptContributor.java` — return `true` for `includeOnCompaction`, `false` for `includeOnCompactionMultiple`, `includeOnTimeout`, `includeOnUnparsedToolCall`. Also implement `RetryAware` on `JustificationPromptContributorFactory` in `APP/prompt/contributor/JustificationPromptContributorFactory.java` — factory returns `true` for `includeOnCompaction` and `includeOnParseError`, `false` for `includeOnCompactionMultiple`.
- [ ] T040 Add `ErrorTemplates` to key `AgentActionMetadata` constants in `APP/agent/AgentInterfaces.java`: at minimum, set `compactionMultipleTemplate` for orchestrator and collector actions to a minimal retry template containing only the output schema.
- [ ] T041 [P] Create minimal retry Jinja templates (e.g., `workflow-orchestrator-retry.jinja2`, `workflow-collector-retry.jinja2`) that contain only the output schema and a short instruction to complete the task. Place in the existing template directory.
- [ ] T042 End-to-end validation: run a workflow that triggers a compaction → verify the full pipeline: AcpChatModel throws → ActionRetryListener captures and polls → BlackboardHistory records error → AgentExecutor reads error descriptor → template switches → contributors filtered → retry succeeds.
- [ ] T043 Code cleanup: remove any dead code, unused imports, or stale comments related to the old retry mechanisms across all modified files.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: No dependencies — start immediately ✅
- **Phase 2 (Foundational)**: Depends on Phase 1 — BLOCKS all user stories ✅
- **Phase 3 (US1 — Centralize)**: Depends on Phase 2 (needs `AgentActionMetadata` with `errorTemplates` field, though it's null initially) ✅ (core complete)
- **Phase 4 (US2 — Error Tracking)**: Depends on Phase 2 (needs `ErrorDescriptor`, `BlackboardHistory` methods) ✅
- **Phase 4b (Session-Level Retry)**: Depends on Phase 2 (needs `ErrorDescriptor`). Can run in parallel with Phase 3 (US1) and Phase 4 (US2) since it touches different files (ACP module). ✅ (core complete)
- **Phase 5 (US3 — Retry-Aware)**: Depends on Phase 3 (centralized calls) AND Phase 4 (error state available) — ✅ (T024/T025/T026/T027 done; T028/T029 tests deferred)
- **Phase 5b (Identity & Test Matrix)**: Depends on Phase 4/4b — ✅
- **Phase 6 (US4 — ACP Cleanup)**: Depends on Phase 4 (needs `CompactionException`, `ActionRetryListenerImpl`) AND Phase 4b (session-level retry must be in place) AND Phase 5 (retry-aware pipeline must be active before removing old retry) — **NEXT**
- **Phase 7 (Polish)**: Depends on all user stories complete

### User Story Dependencies

- **US1 (P1)**: Can start after Phase 2. No dependency on other stories. ✅ (core complete)
- **US2 (P2)**: Can start after Phase 2. No dependency on US1 (error tracking works independently of centralized calls). **However**, US2 can run in parallel with US1 since they modify different files. ✅
- **US3 (P3)**: Depends on US1 (calls must be centralized) AND US2 (errors must be trackable). **IN PROGRESS**
- **US4 (P4)**: Depends on US2 (needs `CompactionException` and `ActionRetryListenerImpl`) AND US3 (retry-aware pipeline must handle retries before old retry is removed).

### Within Each User Story

- Read/audit existing code before modifying
- Core logic before edge cases
- Implementation before verification (grep/test)

### Parallel Opportunities

- T002, T003, T004 can run in parallel (different new files) ✅
- T005, T006, T007, T008 are independent modifications to different existing files — can run in parallel ✅
- T011, T012, T013, T014, T015 migrate different action groups — can run in parallel after T009/T010 ✅
- US1, US2, and Phase 4b can all run in parallel (different modules: AgentInterfaces vs ActionRetryListenerImpl vs ACP layer) ✅
- T044, T045 can run in parallel (different new files) ✅
- T038, T039, T041 can run in parallel (different prompt contributor files and template files)

---

## Parallel Example: Phase 2 (Foundational)

```
# All new files can be created in parallel:
T002: Create ErrorDescriptor.java ✅
T003: Create ErrorTemplates.java ✅
T004: Create RetryAware.java ✅

# All existing file modifications can run in parallel:
T005: Add errorDescriptor to PromptContext.java ✅
T006: Add errorDescriptor to DecoratorContext.java ✅
T007: Add error methods to BlackboardHistory.java ✅
T008: Add errorTemplates to AgentActionMetadata ✅
```

## Parallel Example: User Story 1 (migration batches)

```
# After T009 (audit) and T010 (AgentExecutor readiness):
T011: Migrate workflow agent actions ✅
T012: Migrate collector actions ✅
T013: Migrate orchestrator actions ✅
T014: Migrate dispatch actions ✅
T015: Migrate subagent actions ✅
# All modify different methods in AgentInterfaces — can run in parallel
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup ✅
2. Complete Phase 2: Foundational (data model types) ✅
3. Complete Phase 3: User Story 1 (centralize calls) ✅
4. **STOP and VALIDATE**: All agent actions produce identical outputs through AgentExecutor. Zero `llmRunner.runWithTemplate` calls in AgentInterfaces. ✅
5. This is a safe stopping point — system works identically, just through a centralized path.

### Incremental Delivery

1. Phase 2 → Foundation ready ✅
2. Phase 3 (US1) → Centralized calls → Validate → Safe to deploy (no behavior change) ✅
3. Phase 4 (US2) + Phase 4b (Session-Level Retry) → Error tracking active at both levels → Safe to deploy ✅
4. Phase 5 (US3) → Retry-aware pipeline active → Retries now use smart templates/filtering → ✅
5. Phase 5b → Identity semantics fixed, test matrix validated ✅
6. Phase 6 (US4) → Old retry removed → AcpChatModel clean → **NEXT**
7. Phase 7 → Polish → Final deploy

### Key Risk Mitigation

- US1 is a pure refactor with zero behavior change — safest to deploy first ✅
- US2 adds error capture but doesn't change retry behavior — safe incremental step ✅
- US3 activates new retry behavior — test thoroughly before proceeding to US4
- US4 removes old retry — ONLY do this after US3 is confirmed working
