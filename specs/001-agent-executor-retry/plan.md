# Implementation Plan: Centralize LLM Execution with Blackboard-History-Driven Retry

**Branch**: `001-agent-executor-retry` | **Date**: 2026-04-02 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-agent-executor-retry/spec.md`

## Summary

Centralize all ~17 scattered `llmRunner.runWithTemplate(...)` calls from individual agent action methods in `AgentInterfaces` into the existing `AgentExecutor.run()` pipeline. Then add blackboard-history-driven retry: implement `ActionRetryListener` (from embabel-agent framework) as a Spring bean to capture errors into `BlackboardHistory`, add error type classification via a sealed `ErrorDescriptor` hierarchy, and enable per-action retry template selection and prompt contributor filtering based on compaction status and error type.

## Technical Context

**Language/Version**: Java 21 (primary), Kotlin (ACP module, embabel-agent framework)
**Primary Dependencies**: Spring Boot 3.x, LangChain4j-Agentic, Embabel Agent Framework (`ActionRetryListener`, `AgentProcess`, `Blackboard`, `ActionQos`), Lombok, Jackson
**Storage**: N/A (in-memory `BlackboardHistory` per session, existing `GraphRepository`)
**Testing**: JUnit 5, Mockito, AssertJ
**Target Platform**: JVM (Spring Boot application)
**Project Type**: Multi-module Gradle project with git submodules
**Performance Goals**: Retry overhead < 100ms per decision; no regression in happy-path latency
**Constraints**: Must not modify embabel-agent framework source (only implement its SPI); `libs/embabel-agent` loaded from `.m2` local repo
**Scale/Scope**: ~17 agent action methods to migrate; 4 error types; 2 marker interfaces

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Computation Graph with State Machine | PASS | Error state stored in BlackboardHistory (part of the existing state machine); no new node types |
| II. Three-Phase Workflow Pattern | PASS | No change to workflow phases; retry is within a single action execution |
| III. Orchestrator/Work/Collector Pattern | PASS | No change to node patterns; centralization is internal to action execution |
| IV. Event-Driven Node Lifecycle | PASS | Errors published as events via EventBus; ActionRetryListener captures them synchronously in retry loop |
| V. Artifact-Driven Context Propagation | PASS | Error descriptors stored in BlackboardHistory entries (existing mechanism); no new artifact files |
| VI. Worktree Isolation | N/A | No worktree changes |
| VII. Tool Integration | N/A | No new tools |
| VIII. Human-in-the-Loop | N/A | No change to review gates |
| API Design (no path variables) | N/A | No new API endpoints |

**Post-design re-check**: All gates still pass. The `ErrorDescriptor` sealed hierarchy and `SkipOnCompaction`/`RetainAlways` marker interfaces are internal to the execution pipeline and don't affect node lifecycle, graph structure, or artifact propagation.

## Project Structure

### Documentation (this feature)

```text
specs/001-agent-executor-retry/
├── spec.md
├── plan.md                          # This file
├── research.md                      # Phase 0 output
├── data-model.md                    # Phase 1 output
├── quickstart.md                    # Phase 1 output
├── contracts/
│   └── internal-contracts.md        # Phase 1 output (internal-only, no REST API)
└── checklists/
    └── requirements.md              # Spec validation checklist
```

### Source Code (repository root)

```text
multi_agent_ide_java_parent/
├── multi_agent_ide_lib/                              # Shared lib (data model additions)
│   └── src/main/java/com/hayden/multiagentidelib/
│       ├── agent/
│       │   ├── ErrorDescriptor.java                  # NEW: sealed interface + variants
│       │   ├── ErrorTemplates.java                   # NEW: per-action error template config
│       │   ├── BlackboardHistory.java                # MODIFIED: add error state methods
│       │   └── DecoratorContext.java                 # MODIFIED: add errorDescriptor field
│       └── prompt/
│           ├── PromptContext.java                     # MODIFIED: add errorDescriptor field
│           ├── PromptContributorService.java          # MODIFIED: absorb assemblePrompt, add RetryAware filtering
│           └── RetryAware.java                        # NEW: interface with includeOn*() methods per error type
│
├── multi_agent_ide/                                   # Main application
│   └── src/main/java/com/hayden/multiagentide/
│       ├── agent/
│       │   └── AgentInterfaces.java                   # MODIFIED: migrate llmRunner calls to AgentExecutor
│       ├── service/
│       │   ├── AgentExecutor.java                     # MODIFIED: absorb LlmCallDecorators, emit events, error-aware logic
│       │   ├── DefaultLlmRunner.java                  # MODIFIED: simplified to thin pass-through
│       │   └── ActionRetryListenerImpl.java           # NEW: Spring bean implementing ActionRetryListener
│       └── agent/decorator/request/
│           └── DecorateRequestResults.java            # MODIFIED: add DecorateLlmCallArgs + decorateLlmCall()
│
├── acp-cdc-ai/                                        # ACP module
│   └── src/main/kotlin/com/hayden/acp_cdc_ai/acp/
│       ├── AcpChatModel.kt                            # MODIFIED: remove retry loops, inject AcpRetryEventListener
│       ├── AcpSessionRetryContext.kt                  # NEW: per-session retry state holder
│       └── events/
│           └── AcpRetryEventListener.java             # NEW: @Component tracking session retry state

libs/embabel-agent/                                    # Framework (READ ONLY — do not modify)
└── embabel-agent-api/src/main/kotlin/com/embabel/agent/spi/common/
    └── ActionRetryListener.kt                         # SPI interface we implement
```

**Structure Decision**: All new types go in `multi_agent_ide_lib` (shared library) except for `ActionRetryListenerImpl` which goes in the main application module since it depends on application-level beans. `AgentActionMetadata` is in `AgentInterfaces` in the main module — it gets the new `ErrorTemplates` field there.

## Complexity Tracking

No constitution violations to justify. This feature adds:
- 1 sealed interface with 5 variants (ErrorDescriptor)
- 1 record (ErrorTemplates)
- 1 interface with 5 default methods (RetryAware)
- 1 Spring component (ActionRetryListenerImpl) — framework-level retry
- 1 class (AcpSessionRetryContext) — session-level retry state
- 1 Spring component (AcpRetryEventListener) — session-level retry tracking
- 2 events (AgentExecutorStartEvent, AgentExecutorCompleteEvent) — executor lifecycle

All are minimal types supporting the two-layer retry mechanism. No new abstractions beyond what the spec calls for.

## Implementation Stages

### Stage 1: Centralize LLM Calls in AgentExecutor + Move LlmCallDecorators

**Goal**: Move all `llmRunner.runWithTemplate(...)` calls from AgentInterfaces action methods into `AgentExecutor.run()`. Also move the LlmCallDecorator pipeline and prompt contributor resolution from `DefaultLlmRunner` into `AgentExecutor`/`DecorateRequestResults`, so that integration tests (which mock DefaultLlmRunner) still exercise the decorator pipeline.

**Approach**:
1. For each of the ~16 agent action methods that call `llmRunner.runWithTemplate(...)`:
   - Construct `AgentExecutorArgs` from the existing local variables (request, template model, operation context, tool context, response class, metadata)
   - Replace the inline decoration + llmRunner call with `agentExecutor.run(args)`
   - The `AgentActionMetadata` constants already exist for each action and carry the template name, agent type, action name, method name, request/routing/result types
2. **Move LlmCallDecorator pipeline into DecorateRequestResults**: Create a new `DecorateLlmCallArgs` record and `decorateLlmCall()` method in `DecorateRequestResults` following the same arg-record pattern as the existing decorator methods. The `List<LlmCallDecorator>` is already autowired in `AgentExecutor` (line 87) — move it to `DecorateRequestResults` with `@Autowired(required = false) @Lazy` (matching the existing pattern). The LlmCallDecorator loop (DefaultLlmRunner lines 80-82) moves into this new method.
3. **Move prompt contributor resolution into AgentExecutor**: The prompt contributor service call (DefaultLlmRunner lines 63-66) moves into `AgentExecutor.run()` between prompt context decoration and the LLM call. This is part of centralizing prompt assembly in PromptContributorService (Stage 4).
4. **Simplify DefaultLlmRunner**: After the moves, DefaultLlmRunner becomes a thin pass-through: resolve ACP options → build AI query → apply tools → execute `fromTemplate()`. No decorators, no contributor resolution. The `LlmRunner` interface signature can be simplified (no need to deprecate old methods).
5. **Emit events from AgentExecutor.run()**: Emit `AgentExecutorStartEvent` before step 4 (LLM call) and `AgentExecutorCompleteEvent` after successful result. AgentExecutor already has `EventBus` (line 85).
6. Remove the now-unused `buildPromptContext`, `buildToolContext`, `decorateRequest`, `decorateRouting` helper methods from AgentInterfaces (they are superseded by AgentExecutor's pipeline)
7. **Update tests**: Integration tests that mock `DefaultLlmRunner` will now have LlmCallDecorators running (since they're in AgentExecutor/DecorateRequestResults, not in the mocked runner). Unit tests for DefaultLlmRunner need updating to reflect its simplified scope.

**Files changed**:
- `AgentInterfaces.java` — replace ~16 inline llmRunner call blocks with AgentExecutor.run() calls
- `AgentExecutor.java` — absorb LlmCallDecorator orchestration, emit start/complete events, prompt contributor resolution
- `DecorateRequestResults.java` — add `DecorateLlmCallArgs` record and `decorateLlmCall()` method, move `List<LlmCallDecorator>` here
- `DefaultLlmRunner.java` — simplify to thin pass-through (remove LlmCallDecorator loop, remove contributor resolution)
- `LlmRunner.java` (interface) — simplify signature if needed (no deprecation)
- Integration/unit tests — update for LlmCallDecorators now running in AgentExecutor

**Verification**: All agent actions produce identical outputs. No `llmRunner.runWithTemplate` calls remain in AgentInterfaces. LlmCallDecorators execute during integration tests.

### Stage 2: Error Descriptor Data Model

**Goal**: Create the error type hierarchy and extend BlackboardHistory, PromptContext, DecoratorContext.

**Approach**:
1. Create `ErrorDescriptor` sealed interface with `NoError`, `CompactionError`, `ParseError`, `TimeoutError`, `UnparsedToolCallError` variants
2. Create `CompactionStatus` enum (`NONE`, `FIRST`, `MULTIPLE`)
3. Create `ErrorTemplates` record
4. Add `errorDescriptor` field to `PromptContext` (with `@Builder.Default` defaulting to `NoError`)
5. Add `errorDescriptor` field to `DecoratorContext` (with overloaded constructor for backwards compat)
6. Add `compactionStatus()`, `errorType()`, `addError()`, `errorCount()` methods to `BlackboardHistory`
7. Create `RetryAware` interface extending `PromptContributor` with default-false methods: `includeOnCompaction`, `includeOnCompactionMultiple`, `includeOnParseError`, `includeOnTimeout`, `includeOnUnparsedToolCall`. Both `PromptContributor` implementations and `PromptContributorFactory` implementations can implement `RetryAware` — if a factory implements it and returns false, none of its contributors are created; if a contributor implements it and returns false, that individual contributor is excluded.
8. Add `actionName` field to `ErrorDescriptor` variants (auto-detected from the last BlackboardHistory entry or from `AgentExecutorStartEvent`). This scopes errors to the specific action that produced them.

**Files changed**:
- New: `ErrorDescriptor.java`, `ErrorTemplates.java`, `RetryAware.java`
- Modified: `BlackboardHistory.java`, `PromptContext.java`, `DecoratorContext.java`

### Stage 3: Session-Level Retry Context (ACP Layer)

**Goal**: Keep a per-session retry state inside the ACP layer for errors that don't propagate up to the framework retry (e.g., compaction detected mid-stream, incomplete JSON within a single `invokeChat` call). AcpChatModel checks this state before making calls and handles retries locally with CONTINUE.

**Rationale**: Not all errors bubble up to the embabel-agent `ActionRetryListener`. Some are caught and handled within a single `invokeChat` call — compaction detected in the response stream, empty generations, etc. These need a retry context at the session level so AcpChatModel can adapt (send CONTINUE, wait for compaction) without the full prompt contributor machinery.

**Approach**:
1. Create `AcpSessionRetryContext` in `ACP/` — a per-session retry state holder with:
   - `ErrorDescriptor` current error (reuses the same sealed hierarchy from Stage 2)
   - `CompactionStatus` compaction status for this session
   - Methods: `recordError(ErrorDescriptor)`, `clearOnSuccess()`, `errorType()`, `compactionStatus()`, `isRetry()`
   - Keyed by session ID (chat model key)

2. Create `AcpRetryEventListener` as a `@Component` implementing `EventListener` in `ACP/events/AcpRetryEventListener.java`:
   - Subscribes to: `ChatSessionCreatedEvent` (create entry), `ActionStartedEvent` (mark action active), `ActionCompletedEvent` (track completion), `CompactionEvent` (record compaction error), `NodeErrorEvent` (record error), `ChatSessionClosedEvent` (cleanup)
   - Uses the hierarchical relationship between nodeId, sessionId, and artifactKey to track which session each event belongs to
   - Maintains a `ConcurrentHashMap<String, AcpSessionRetryContext>` keyed by session key
   - Clears retry state when it sees a successful serialization (the response was parsed correctly by the caller)

3. Add `AgentExecutorStartEvent` and `AgentExecutorCompleteEvent` to `Events.java` — emitted by `AgentExecutor.run()` (not DefaultLlmRunner) at the start and end of LLM execution. AgentExecutor already has `EventBus` injected. `AcpRetryEventListener` uses start to initialize retry tracking for the session, and complete to clear the retry state. This pair gives a clean lifecycle: between start and complete, the listener counts retries and classifies errors. On complete, it resets. On a subsequent start (re-invocation by the framework retry), the listener sees a fresh start and knows the previous attempt failed. These events also serve as the authoritative signal for which action is currently executing — `ActionRetryListenerImpl` can use the most recent `AgentExecutorStartEvent`'s actionName to scope error descriptors.

4. Inject `AcpRetryEventListener` into `AcpChatModel`. This **replaces** AcpChatModel's existing hash-based retry detection (lines 143-156 where it hashes the prompt and compares to the last hash). Before calling `doPerformPrompt`, check `retryListener.retryContextFor(sessionKey)`:
   - If `isRetry()` is true and error is compaction: send CONTINUE instead of full prompt, wait for compaction completion
   - If `isRetry()` is true and error is parse/timeout: send CONTINUE instead of full prompt
   - If not a retry: proceed normally
   - This is the **session-level** retry — no prompt contributors involved, just CONTINUE
   - Remove the `lastPromptHashPerSession` map and hash-based `isRetry` detection entirely — `AcpSessionRetryContext.isRetry()` is the sole source of truth

**Key distinction from the AgentExecutor-level retry**:
- **AgentExecutor-level** (Stages 4-5): Framework retry re-invokes the entire action. ErrorDescriptor on PromptContext drives template selection and RetryAware contributor filtering. Full prompt re-assembly.
- **Session-level** (this stage): Error within a single `invokeChat` call. AcpChatModel sends CONTINUE to the same session. No prompt re-assembly, no contributor filtering. Just nudge the session to complete its response.

**Files changed**:
- New: `ACP/AcpSessionRetryContext.java` (or `.kt`)
- New: `ACP/events/AcpRetryEventListener.java`
- Modified: `ACP/events/Events.java` — add `AgentExecutorStartEvent` and `AgentExecutorCompleteEvent`
- Modified: `ACP/AcpChatModel.kt` — inject `AcpRetryEventListener`, check retry state before calls
- Modified: `APP/service/DefaultLlmRunner.java` — simplified to thin pass-through (ACP options resolution, query build, template execution only). LlmCallDecorator pipeline and prompt contributor resolution moved out to AgentExecutor/DecorateRequestResults.

### Stage 4: ActionRetryListener Implementation (Framework Level)

**Goal**: Capture errors from the embabel-agent framework retry mechanism into BlackboardHistory. This handles errors that DO propagate up to the framework.

**Approach**:
1. Create `ActionRetryListenerImpl` as a `@Component` implementing `ActionRetryListener`
2. In `onActionRetry(RetryContext, Throwable, AgentProcess)`:
   - Access the blackboard from `agentProcess` (AgentProcess implements Blackboard)
   - **Resolve the ACP session key**: The `AgentProcess` only has the root orchestrator key (`ak:ROOT`). Each action has its own ACP session (child key). Walk backwards through `BlackboardHistory` to find the actual session:
     - `history.copyOfEntries().getLast().contextId()` → returns the `ArtifactKey` for the specific action's ACP session
     - `history.copyOfEntries().getLast().actionName()` → identifies which action errored
     - This works because BlackboardHistory.Entry implements `HasContextId` and stores the action's own `ArtifactKey` (which is the `chatModelKey` for that agent's ACP session)
   - Classify the throwable into an `ErrorDescriptor` variant based on message content
   - For `CompactionError`: poll the resolved ACP session (not the root) for compaction completion
   - Call `blackboardHistory.addError(errorDescriptor)`
3. The framework auto-discovers this bean via Spring and passes it to `AbstractAgentProcess` → `ActionQos.retryTemplate()`

**Session identity context** (from session_identity_model.md):
- `ak:ROOT` = root orchestrator (all that `AgentProcess.contextIdString` gives us)
- `ak:ROOT/CHILD_A` = a specific action's ACP session (what we need for compaction polling)
- Each agent action has its own unique `ArtifactKey` as its `chatModelKey`
- The `BlackboardHistory.Entry.contextId()` stores exactly this per-action key

**Files changed**:
- New: `ActionRetryListenerImpl.java`

**Key detail**: The `ActionRetryListener` is called synchronously within the Spring Retry `onError` callback, before the next retry attempt. This means the error is recorded in BlackboardHistory before `AgentExecutor.run()` fires again on retry.

### Stage 4: Error-Aware AgentExecutor + Centralized Prompt Assembly

**Goal**: Make AgentExecutor.run() query error state, adapt template selection, and centralize all prompt assembly (including RetryAware filtering) in `PromptContributorService`.

**Approach**:
1. Add `ErrorTemplates` field to `AgentActionMetadata` record (nullable, defaults to null in existing constants)
2. In `AgentExecutor.run()`, between steps 2 (prompt context build) and 4 (LLM call):
   - Query `history.errorType()` and `history.compactionStatus()` to build the `ErrorDescriptor`. Auto-detect which action produced the error from the last BlackboardHistory entry (no parallel execution per agent session) and from the most recent `AgentExecutorStartEvent`
   - Set the `ErrorDescriptor` on the `PromptContext` and `DecoratorContext` — this is the **single source of truth** for all downstream decisions
   - Resolve effective template: destructure the `ErrorDescriptor` from `PromptContext` to look up the template override from `meta.errorTemplates()`. If no override exists, use `meta.template()`
3. **Centralize prompt assembly in `PromptContributorService`**: Move `assemblePrompt()` logic from `AgentExecutor` into `PromptContributorService`. This service becomes the single place where:
   - Contributors are retrieved from registry and factories
   - **Factory-level RetryAware filtering**: Before calling `factory.create(promptContext)`, check if factory implements `RetryAware`. If error state is active and factory returns false for the error type → skip the factory entirely (none of its contributors are created)
   - **Contributor-level RetryAware filtering**: After all contributors are assembled and sorted, filter them: if error state is active and contributor does NOT implement `RetryAware` → exclude; if it does → call the matching `includeOn*(error)` method
   - Contributors are wrapped through `PromptContributorAdapterFactory` (which should also implement `RetryAware` for its own filtering)
   - Template rendering happens here (moved from AgentExecutor.assemblePrompt)
   - Error handling for contributor failures (skip-and-log) stays here
4. Update `AgentActionMetadata` constants with `ErrorTemplates` where appropriate (can start with null/defaults and add specific retry templates incrementally)

**Files changed**:
- Modified: `AgentExecutor.java` — error descriptor query, template resolution, delegates prompt assembly to PromptContributorService
- Modified: `PromptContributorService.java` — absorbs assemblePrompt logic, adds RetryAware filtering at factory and contributor levels
- Modified: `FilteredPromptContributorAdapterFactory.java` — implement RetryAware
- Modified: `AgentInterfaces.java` (AgentActionMetadata record + constants)

### Stage 5: AcpChatModel Cleanup & Error-Type-Specific Wait Behavior

**Goal**: Remove all retry logic from AcpChatModel. AcpChatModel becomes a clean pass-through for LLM communication — detect errors, throw typed exceptions, and let the framework retry + ActionRetryListener handle the rest. For certain error types (especially compaction), the system must wait an appropriate amount of time before retrying.

**Approach — AcpChatModel changes**:

1. **Remove from `invokeChat()`**:
   - The null/empty retry loop (lines ~238-255) — currently polls with `Thread.sleep(5_000)` up to 5 times
   - The unparsed tool call retry (lines ~257-280) — currently sends a corrective message and collects response
   - The incomplete JSON handling retry (line ~286-289 + `handleIncompleteJson()` method) — currently sends "please continue" and concatenates
   - The `isRetry` parameter and CONTINUE logic (lines ~205-211) — replaced by `AcpRetryEventListener.retryContextFor(sessionKey).isRetry()`
   - The hash-based retry detection (lines ~143-156) — the `lastPromptHashPerSession` map and SHA-256 prompt hashing. This is replaced by `AcpSessionRetryContext.isRetry()` from the event listener

2. **Convert `handleCompactingSession()` to detect-and-throw**:
   - Keep compaction detection (`isSessionCompacting()`)
   - When compaction is detected: emit `CompactionEvent` via EventBus, then throw a typed `CompactionException`
   - Do NOT poll — the `ActionRetryListener` captures the exception, writes `CompactionError` to BlackboardHistory, and the framework's exponential backoff handles the wait
   - Remove the polling loop (lines ~325-348) entirely

3. **Remove `handleIncompleteJson()` entirely** — parse errors will surface as exceptions, captured by ActionRetryListener as `ParseError`

4. **Keep** `sanitizeGenerationText()` and `stripMarkdownCodeFences()` — these are response cleanup, not retry logic

5. **Keep** `isSessionCompacting()` — still needed for detection, just not for polling

**Approach — Error-type-specific wait behavior**:

Certain errors require waiting before retry. The framework's `ActionQos` provides exponential backoff (default: 10s → 50s → 60s cap), but some error types need specific wait strategies:

6. **Compaction wait**: The framework backoff handles the initial delay. However, compaction can take 30-60+ seconds. The `ActionRetryListenerImpl.onActionRetry()` should, for `CompactionError`, poll the ACP session for compaction completion status before returning. This blocks the retry thread until compaction finishes (or a max poll timeout is reached), ensuring the next retry attempt doesn't fire while compaction is still in progress. This is the one place where polling is appropriate — inside the listener, before the framework fires the next attempt.

   ```
   In ActionRetryListenerImpl.onActionRetry():
     if error is CompactionError:
       poll session for compaction completion (max 20 polls, 10s interval)
       once "compaction completed" is seen → return (framework proceeds to retry)
       if max polls exceeded → let framework retry anyway (it will get another compaction and loop)
   ```

7. **Rate limit wait**: Already handled by `ActionQos` — `isRateLimitError()` check in the framework uses the standard exponential backoff.

8. **Parse/timeout errors**: No special wait needed — the framework's default backoff is sufficient. These errors are typically immediate (bad output, not a waiting condition).

**Files changed**:
- Modified: `AcpChatModel.kt` — remove retry loops, convert compaction handling to detect-and-throw
- Modified: `ActionRetryListenerImpl.java` — add compaction polling in `onActionRetry()` for `CompactionError`
- New (optional): `CompactionException.java` — typed exception for compaction detection (or reuse existing exception with message-based classification)

**Risk**: This is the most delicate stage. Must verify that:
- The framework retry + ActionRetryListener + compaction polling produces equivalent or better behavior than the inline retry loops
- Compaction polling in the listener doesn't cause thread starvation (it's in the retry thread, which was already blocked by the old polling loop)
- The `isRetry` flag removal doesn't break session state management in AcpChatModel

### Stage 6: Implement RetryAware on Existing PromptContributors and Factories

**Goal**: Add `RetryAware` to prompt contributors and factories that should participate in retries, with per-error-type inclusion logic.

**Approach**:
RetryAware applies at **both** levels — factories decide whether to create any contributors at all, and individual contributors decide whether to include themselves.

1. **`JsonOutputFormatPromptContributorFactory`** + its inner `JsonOutputFormatPromptContributor`: Both implement `RetryAware`, return `true` for ALL `includeOn*()` methods (always included on retry). This is the formatting instructions contributor (priority 10001). Note: the actual JSON response schema comes from the structured output parser in the LLM runner and is always present.
2. **`FilteredPromptContributorAdapterFactory`**: Implement `RetryAware` — this is the data-layer filter policy adapter. Should return `true` for all error types (filters should still apply during retry).
3. **`WeAreHerePromptContributor`**: Implement `RetryAware`, return `true` for `includeOnCompaction` (first retry needs position context), `false` for `includeOnCompactionMultiple`.
4. **`JustificationPromptContributorFactory`**: Implement `RetryAware` on factory, return `true` for `includeOnCompaction` and `includeOnParseError`, `false` for `includeOnCompactionMultiple`.
5. **All other contributors/factories** (`SkillPromptContributorFactory`, `AgentTopologyPromptContributorFactory`, `GitPromptContributorFactory`, `IntellijPromptContributorFactory`, `WorktreeSandboxPromptContributorFactory`, `EpisodicMemoryPromptContributor`, `CurationHistoryContextContributorFactory`, `ContextManagerPromptContributorFactory`, `PermissionEscalationPromptContributorFactory`, `RouteToContextManagerPromptContributorFactory`, `FirstOrchestratorPromptContributorFactory`, `ActiveFiltersPromptContributorFactory`): Do NOT implement `RetryAware` → automatically excluded on any retry by default.

**Files changed**:
- `JsonOutputFormatPromptContributorFactory.java` — implement RetryAware on factory and inner contributor
- `FilteredPromptContributorAdapterFactory.java` — implement RetryAware
- `WeAreHerePromptContributor.java` — implement RetryAware
- `JustificationPromptContributorFactory.java` — implement RetryAware on factory
