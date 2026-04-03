# Internal Contracts: Centralize LLM Execution with Blackboard-History-Driven Retry

**Branch**: `001-agent-executor-retry` | **Date**: 2026-04-02

This feature is entirely internal — no new REST/API endpoints are introduced. All contracts are internal Java interfaces and method signatures.

## ActionRetryListener Implementation Contract

```
Interface: com.embabel.agent.spi.common.ActionRetryListener
Method: onActionRetry(RetryContext, Throwable, AgentProcess)

CRITICAL — Session Key Resolution:
  The AgentProcess passed to this listener only has the ROOT orchestrator's session ID
  (e.g., ak:ROOT). It does NOT have the ACP session key for the specific action that
  failed. Each action has its own ACP session (child key under the root). We must
  walk backwards through BlackboardHistory to resolve the actual ACP session.

  Session key resolution algorithm:
    1. Extract BlackboardHistory from agentProcess (agentProcess implements Blackboard,
       retrieve via agentProcess.last(BlackboardHistory.class) or from the objects map)
    2. Get the most recent entry: history.copyOfEntries().getLast()
       → This entry's contextId() returns the ArtifactKey (ACP session key) for the
         action that just failed
    3. The entry's actionName() identifies which action errored
    4. The entry's contextId() IS the chatModelKey (ACP session key) — this is the
       same key used in ChatSessionCreatedEvent.chatModelId()

  Why this works:
    - BlackboardHistory.Entry implements HasContextId
    - Each entry stores the ArtifactKey of the action that produced it
    - The ArtifactKey hierarchy: ak:ROOT/CHILD_A is the agent's session key
    - When an action fails, its request was the last thing written to history
    - The contextId on that request IS the agent's chatModelKey

  Session identity model (from session_identity_model.md):
    ak:ROOT                 ← root orchestrator (AgentProcess.contextIdString)
    ak:ROOT/CHILD_A         ← discovery orchestrator's OWN ACP session
    ak:ROOT/CHILD_B         ← discovery agent #1's OWN ACP session
    ak:ROOT/CHILD_C         ← planning orchestrator's OWN ACP session
    ...
    Each agent has its own unique ArtifactKey as its chatModelKey.
    Some agents recycle their key on route-back (same ACP session reused).
    Dispatched agents (discovery/planning/ticket agents) always get new keys.

Behavior:
  1. Extract BlackboardHistory from agentProcess
  2. Resolve the ACP session key (chatId): history.copyOfEntries().getLast().contextId()
     → This gives the ArtifactKey for the specific action's ACP session
  3. Resolve the action name: history.copyOfEntries().getLast().actionName()
  4. Resolve the start event's nodeId via AcpRetryEventListener:
     → retryListener.retryContextFor(chatId.value()).lastStartNodeId
     → This is the AgentExecutorStartEvent's nodeId (child of chatId)
     → The error's contextId = lastStartNodeId.createChild()
  5. Classify the throwable into an ErrorDescriptor variant, passing the
     error contextId (lastStartNodeId.createChild()) into the constructor
  6. For CompactionError: poll the ACP session (using the resolved session key)
     until compaction completes (see below)
  7. Call blackboardHistory.addError(errorDescriptor)
  8. Return — framework proceeds to next retry attempt

ArtifactKey hierarchy produced by this listener:
  chatId (session key, resolved in step 2)
    └── startEventNodeId (from AcpRetryEventListener, resolved in step 4)
          └── errorDescriptor.contextId = startEventNodeId.createChild()
  This means errorDescriptor.contextId().parent() → start event's nodeId
  and errorDescriptor.contextId().parent().parent() → chatId

Classification rules:
  - Throwable is CompactionException or message contains "compacting"/"Compacting" → CompactionError
  - Throwable message contains "Prompt is too long" → CompactionError
  - Throwable is JsonParseException or message contains "parse" → ParseError
  - Throwable is TimeoutException or message contains "timeout" → TimeoutError
  - Otherwise → generic retry error (use ParseError with raw message as fallback)

Compaction wait behavior:
  When error is classified as CompactionError:
  - The listener BLOCKS and polls the ACP session for compaction completion
  - Uses the RESOLVED session key (from step 2 above), NOT the root AgentProcess key
  - Poll loop: up to 20 iterations, 10s interval (matches existing behavior)
  - Check: send lightweight "continue" to the resolved ACP session, inspect response
    for compaction markers
  - If "compaction completed" seen → set compactionCompleted=true on the ErrorDescriptor, return
  - If max polls exceeded → set compactionCompleted=false, return (framework retries anyway,
    will hit another CompactionError and poll again)
  - This is the ONLY place where polling/waiting happens — AcpChatModel no longer polls

  Why poll in the listener (not in AgentExecutor or AcpChatModel):
  - The listener runs synchronously inside the Spring Retry onError callback
  - It blocks the retry thread BEFORE the next attempt fires
  - This is the same thread that was previously blocked by the AcpChatModel polling loop
  - No thread starvation risk — same concurrency model as before
  - AgentExecutor runs AFTER the wait is done, so it sees the completed compaction state

Dependencies (injected into ActionRetryListenerImpl):
  - AcpRetryEventListener — to resolve the last start event's nodeId for ErrorDescriptor contextId creation
  - EventStreamRepository — to optionally verify session key via ChatSessionCreatedEvent
  - AcpSessionManager or AcpChatModel — to send "continue" polls for compaction wait
  - (BlackboardHistory comes from the AgentProcess, not injected)
```

## AgentExecutor.run() Extended Contract

```
Existing: AgentExecutor.run(AgentExecutorArgs) → U
Extended behavior (between steps 2 and 3 of current pipeline):

  ArtifactKey setup (before step 2a):
      ArtifactKey chatId = promptContext.chatId();
      ArtifactKey startNodeId = chatId.createChild();
      // Emit AgentExecutorStartEvent with nodeId = startNodeId.value()
      // This startNodeId becomes the parent for all ErrorDescriptors in this execution

  2a. Build ErrorDescriptor from BlackboardHistory:
      ErrorDescriptor errorDesc = history.errorType()   // includes compactionStatus
      // If NoError, create: new NoError(startNodeId.createChild())
      // If error exists, its contextId was already set by ActionRetryListenerImpl
      //   as a child of this (or a previous) start event's nodeId
      → This is the SINGLE SOURCE OF TRUTH for all retry decisions

  2b. Set errorDescriptor on PromptContext and DecoratorContext
      → All downstream logic (template selection, contributor filtering, decorator
         adaptation) destructures this one object. No separate queries needed.

  2c. Resolve effective template by destructuring PromptContext.errorDescriptor():
      switch (promptContext.errorDescriptor()) {
        case NoError _                          → meta.template()
        case CompactionError(FIRST)             → meta.errorTemplates().compactionFirstTemplate()    ?: meta.template()
        case CompactionError(MULTIPLE)          → meta.errorTemplates().compactionMultipleTemplate() ?: meta.template()
        case ParseError _                       → meta.errorTemplates().parseErrorTemplate()          ?: meta.template()
        case TimeoutError _, UnparsedToolCallError _ → meta.errorTemplates().defaultRetryTemplate()  ?: meta.template()
      }

  2d. Delegate prompt assembly to PromptContributorService (centralized):
      → PromptContributorService handles factory-level and contributor-level RetryAware
        filtering internally, using the errorDescriptor from PromptContext.
      → AgentExecutor no longer contains assemblePrompt() — that logic moves to
        PromptContributorService.

  RetryAware filtering (inside PromptContributorService.retrievePromptContributors):
    Phase 1 — Factory filtering:
      For each factory:
        case NoError → call factory.create(promptContext) normally
        case any error variant →
          if factory does NOT implement RetryAware → SKIP (no contributors created)
          else → destructure and call matching method; skip if false

    Phase 2 — Contributor filtering (after assembly + sort, before adapter wrap):
      For each contributor:
        case NoError → include (normal path)
        case any error variant →
          if contributor does NOT implement RetryAware → EXCLUDE
          else → destructure and call matching method:
            CompactionError(FIRST)    → contributor.includeOnCompaction(error)
            CompactionError(MULTIPLE) → contributor.includeOnCompactionMultiple(error)
            ParseError                → contributor.includeOnParseError(error)
            TimeoutError              → contributor.includeOnTimeout(error)
            UnparsedToolCallError     → contributor.includeOnUnparsedToolCall(error)
```

## RetryAware Interface Contract

```
Interface: com.hayden.multiagentidelib.prompt.RetryAware
Standalone interface (not extending PromptContributor or PromptContributorFactory)

Purpose:
  - Applicable to BOTH PromptContributor AND PromptContributorFactory implementations.
  - PromptContributors/factories that do NOT implement RetryAware are excluded by default
    on ANY retry.
  - Those that need to participate in retries implement RetryAware and declare
    which error types they should be included for.

Methods (all default to false):
  boolean includeOnCompaction(CompactionError error)
  boolean includeOnCompactionMultiple(CompactionError error)
  boolean includeOnParseError(ParseError error)
  boolean includeOnTimeout(TimeoutError error)
  boolean includeOnUnparsedToolCall(UnparsedToolCallError error)

Two-level filtering (centralized in PromptContributorService):

  Level 1 — Factory filtering:
    Before calling factory.create(promptContext):
      if errorDescriptor is NoError → call factory normally
      else if factory implements RetryAware → call matching includeOn*(), skip if false
      else → skip factory entirely (none of its contributors created)

  Level 2 — Contributor filtering:
    After all contributors assembled and sorted:
      if errorDescriptor is NoError → include all
      else if contributor implements RetryAware → call matching includeOn*(), exclude if false
      else → exclude (default: excluded on retry)

Example implementations:
  - JsonOutputFormatPromptContributorFactory + JsonOutputFormatPromptContributor:
    Both implement RetryAware, ALL methods return true (always included)
  - FilteredPromptContributorAdapterFactory:
    Implements RetryAware, ALL methods return true (filter policies still apply during retry)
  - WeAreHerePromptContributor:
    Implements RetryAware, includeOnCompaction → true, includeOnCompactionMultiple → false
  - JustificationPromptContributorFactory:
    Implements RetryAware on factory, includeOnCompaction → true, includeOnCompactionMultiple → false
  - CurationHistoryContextContributorFactory:
    Does NOT implement RetryAware → auto-excluded on any retry
```

## AcpRetryEventListener Contract

```
Class: com.hayden.acp_cdc_ai.acp.events.AcpRetryEventListener
Implements: EventListener
Annotation: @Component

State:
  ConcurrentHashMap<String, AcpSessionRetryContext> sessionRetryContexts

ArtifactKey resolution for ErrorDescriptor creation:
  Each agent session is single-threaded. The root ArtifactKey (ArtifactKey.root())
  identifies the session. When AgentExecutorStartEvent arrives, its chatId and nodeId
  are stored on the AcpSessionRetryContext. When an error event arrives later:
    1. Extract root from event's contextId: event.contextId().root().value()
    2. Look up the AcpSessionRetryContext (keyed by sessionKey, which maps to root)
    3. Call context.createErrorContextId() → lastStartNodeId.createChild()
    4. Pass chatId + generated contextId into the ErrorDescriptor constructor

  The last AgentExecutorStartEvent for a given root is always correct because
  sessions are single-threaded — no concurrent executions per session.

Subscribed events and behavior:

  ChatSessionCreatedEvent:
    → sessionRetryContexts.put(sessionKey, new AcpSessionRetryContext(sessionKey))

  AgentExecutorStartEvent:
    → context = sessionRetryContexts.get(event.sessionKey)
    → if context exists:
        context.clearOnSuccess()  // reset for new attempt
        context.recordStartEvent(
            new ArtifactKey(event.sessionKey()),  // chatId
            new ArtifactKey(event.nodeId())        // startNodeId — parent for errors
        )
    → if not: create new entry (defensive), then recordStartEvent

  CompactionEvent:
    → resolve root: event.contextId().root().value()
    → context = find context by root (or sessionKey from CompactionEvent)
    → errorContextId = context.createErrorContextId()  // lastStartNodeId.createChild()
    → context.recordError(new CompactionError(
        actionName, context.compactionStatus().next(), false, errorContextId))

  NodeErrorEvent:
    → resolve root: event.contextId().root().value()
    → context = find context by root
    → errorContextId = context.createErrorContextId()
    → classify event.error into ErrorDescriptor variant with errorContextId
    → context.recordError(errorDescriptor)

  AgentExecutorCompleteEvent:
    → context = sessionRetryContexts.get(event.sessionKey)
    → context.clearOnSuccess()   // execution succeeded, reset retry state

  ChatSessionClosedEvent:
    → sessionRetryContexts.remove(sessionKey)

Public query method:
  AcpSessionRetryContext retryContextFor(String sessionKey)
    → returns the context, or a static NoRetry sentinel if not found
```

## AgentExecutorStartEvent / AgentExecutorCompleteEvent Contract

```
Events emitted by AgentExecutor.run() (EventBus already injected):

  ArtifactKey hierarchy:
    chatId = promptContext.chatId()                          // session key
    startNodeId = chatId.createChild()                       // AgentExecutorStartEvent.nodeId
    completeNodeId = chatId.createChild()                    // AgentExecutorCompleteEvent.nodeId
    errorContextId = startNodeId.createChild()               // ErrorDescriptor.contextId (created by ActionRetryListenerImpl)

    The start event's nodeId is the PARENT of all ErrorDescriptors for that execution.
    Both start and complete events are children of the chatId (siblings).

  AgentExecutorStartEvent:
    Emitted: in AgentExecutor.run(), before the LLM call (step 4)
    Fields: sessionKey (chatId.value()), actionName, nodeId (chatId.createChild().value())
    Purpose: signals AcpRetryEventListener to initialize/reset retry tracking;
             also serves as authoritative signal for which action is currently executing
             (used by ActionRetryListenerImpl to scope error descriptors via actionName).
             The nodeId becomes the parent for all ErrorDescriptors in this execution.

  AgentExecutorCompleteEvent:
    Emitted: in AgentExecutor.run(), after the LLM call returns a valid result
    Fields: sessionKey (chatId.value()), actionName, nodeId (chatId.createChild().value())
    Purpose: signals AcpRetryEventListener to clear retry state (success)

  Lifecycle:
    Start → [0..N CompactionEvents / NodeErrorEvents] → Complete (success)
    Start → [0..N CompactionEvents / NodeErrorEvents] → (exception propagates to framework retry)
           → ActionRetryListener fires → framework re-invokes action → Start (new attempt)

  The start/complete pair brackets each executor attempt. Between them,
  AcpRetryEventListener counts retries and classifies errors. AcpChatModel
  checks retryContextFor(sessionKey).isRetry() to decide whether to send
  CONTINUE vs full prompt.
```

## AcpChatModel Session-Level Retry Contract

```
Injection: AcpRetryEventListener injected into AcpChatModel

Replaces: The existing hash-based retry detection in AcpChatModel (lines 143-156).
  - Remove lastPromptHashPerSession map
  - Remove SHA-256 prompt hashing logic
  - Remove isRetry parameter from invokeChat()
  - AcpSessionRetryContext.isRetry() is the sole source of truth

Before calling doPerformPrompt:
  val retryCtx = retryListener.retryContextFor(sessionKey)
  if (retryCtx.isRetry()) {
    when (retryCtx.errorType()) {
      is CompactionError → send CONTINUE, wait for compaction completion
      is ParseError, TimeoutError → send CONTINUE
      else → send CONTINUE
    }
  } else {
    → proceed normally with full prompt
  }

Key distinction:
  - This is session-level retry: same session, CONTINUE message, no prompt re-assembly
  - AgentExecutor-level retry: full action re-invocation, new prompt, contributor filtering
  - Both use the same ErrorDescriptor sealed hierarchy for consistency
```

## ChatSessionClosedEvent Fix Contract

```
Current (broken): ChatSessionClosedEvent has field sessionId, but nodeId() returns sessionId.
  There is no separate child nodeId — the sessionId IS used as the nodeId.

Required fix:
  record ChatSessionClosedEvent(
      String eventId,
      Instant timestamp,
      String nodeId,          // child of sessionId (for event hierarchy consistency)
      String sessionId        // NEW: the actual chat session ID (chatModelKey)
  ) implements Events.GraphEvent

  The nodeId should be created as a child of sessionId (matching the pattern where
  events have their own nodeId under the session they belong to).
  The sessionId field is what AcpRetryEventListener uses for cleanup.
```

## LlmCallDecorator Migration Contract

```
Current location: DefaultLlmRunner (lines 78-82)
  - LlmCallDecorator loop runs inside runWithTemplate()
  - Integration tests mock DefaultLlmRunner → decorators DON'T execute during tests

New location: DecorateRequestResults
  - New record:
    record DecorateLlmCallArgs<T>(
        PromptContext promptContext,
        ToolContext toolContext,
        ObjectCreator<T> templateOperations,
        Map<String, Object> templateModel,
        OperationContext operationContext
    ) {}

  - New autowired field:
    @Autowired(required = false) @Lazy
    private List<LlmCallDecorator> llmCallDecorators = new ArrayList<>();

  - New method:
    <T> LlmCallDecorator.LlmCallContext<T> decorateLlmCall(DecorateLlmCallArgs<T> args)
      → builds LlmCallContext from args
      → iterates llmCallDecorators in order, calling decorate()
      → returns decorated LlmCallContext

  Called from: AgentExecutor.run() step 4, between query build and template execution

Impact on DefaultLlmRunner:
  - Remove List<LlmCallDecorator> field and decorator loop
  - Remove PromptContributorService resolution (moved to AgentExecutor)
  - Simplified signature: receives pre-resolved prompt elements, pre-decorated context
  - Becomes thin: ACP options → AI query → tools → fromTemplate()

Impact on tests:
  - Integration tests mock DefaultLlmRunner but NOT DecorateRequestResults
  - LlmCallDecorators now execute during integration tests (desired behavior)
  - Unit tests for DefaultLlmRunner simplified (no decorator assertions)
```

## PromptContributorService Centralization Contract

```
Class: com.hayden.multiagentidelib.prompt.PromptContributorService
Existing: getContributors(PromptContext) → List<ContextualPromptElement>

Extended responsibilities (absorb from AgentExecutor.assemblePrompt()):
  - Template rendering (renderTemplate from AgentExecutor)
  - Contributor assembly with RetryAware filtering
  - Error handling for contributor/factory failures (skip-and-log)

New method (or extended getContributors):
  List<ContextualPromptElement> getContributors(PromptContext promptContext)

  Updated retrieval logic in retrievePromptContributors():
    1. Get registry contributors (unchanged)
    2. For each factory:
       a. If promptContext.errorDescriptor() is NoError → call factory.create() normally
       b. Else if factory implements RetryAware →
          destructure errorDescriptor, call matching includeOn*()
          if false → skip factory entirely
       c. Else → skip factory (excluded by default on retry)
    3. Sort assembled contributors by priority
    4. For each contributor (between sort at line 84 and adapter wrap at line 88):
       a. If promptContext.errorDescriptor() is NoError → include
       b. Else if contributor implements RetryAware →
          destructure errorDescriptor, call matching includeOn*()
          if false → exclude
       c. Else → exclude (excluded by default on retry)
    5. Wrap through FilteredPromptContributorAdapterFactory (unchanged, but adapter
       also implements RetryAware and returns true for all error types)

  The PromptContext already carries errorDescriptor — no new parameters needed.
```

## ErrorDescriptor in BlackboardHistory Contract

```
Storage: ErrorDescriptor instances stored as BlackboardHistory.Entry
  - actionName = auto-detected from the last entry in history (no parallel execution
    per agent session) or from the most recent AgentExecutorStartEvent
  - input = the ErrorDescriptor instance (implements HasContextId)
  - timestamp = when the error was recorded

  The actionName on each ErrorDescriptor variant scopes it to the specific action.
  Since we don't support parallel execution per agent session, the last entry in
  history always identifies the current action.

ArtifactKey hierarchy for ErrorDescriptor:
  Every ErrorDescriptor (including NoError) has a contextId that is a child of the
  AgentExecutorStartEvent's nodeId:

    chatId (session key)
      └── AgentExecutorStartEvent.nodeId = chatId.createChild()
            └── ErrorDescriptor.contextId = startEventNodeId.createChild()

  This means:
    - errorDescriptor.contextId().parent() → the start event's nodeId
    - errorDescriptor.contextId().parent().parent() → the chatId (session key)
    - BlackboardHistory.Entry.contextId() delegates to errorDescriptor.contextId()
      → the entry IS positioned in the graph under the execution context

  When ActionRetryListenerImpl creates an ErrorDescriptor:
    1. Resolve the start event's nodeId from the last history entry's contextId
       (walk back to find the AgentExecutorStartEvent's contextId)
    2. Call startEventNodeId.createChild() to generate the error's contextId
    3. Pass that contextId into the ErrorDescriptor variant constructor

  When AgentExecutor creates NoError for the initial call:
    1. startNodeId = chatId.createChild() (same key used for AgentExecutorStartEvent)
    2. NoError contextId = startNodeId.createChild()

Query methods:
  compactionStatus() → scans entries for CompactionError instances matching current action
    - 0 found → NONE
    - 1 found → FIRST
    - 2+ found → MULTIPLE

  errorType() → returns the most recent ErrorDescriptor entry, or NoError
    (NoError is built with a contextId that's a child of the current start event's nodeId)

  addError(ErrorDescriptor) → delegates to history.withEntry(actionName, errorDescriptor)
    where actionName comes from the ErrorDescriptor's actionName field
    and contextId comes from the ErrorDescriptor (child of start event's nodeId)
```
