# Data Model: Centralize LLM Execution with Blackboard-History-Driven Retry

**Branch**: `001-agent-executor-retry` | **Date**: 2026-04-02

## New Entities

### ErrorDescriptor (sealed interface)

Represents the error state for a given action execution. Stored in BlackboardHistory entries. Matchable by AgentExecutor and PromptContributors. Implements `HasContextId`.

**ArtifactKey Hierarchy**: Every ErrorDescriptor — including `NoError` — has a `contextId` that encodes its position in the graph. The hierarchy is:

```
chatId (session key, from promptContext.chatId())
  └── AgentExecutorStartEvent.nodeId = chatId.createChild()
        └── ErrorDescriptor.contextId = startEventNodeId.createChild()
        └── AgentExecutorCompleteEvent.nodeId = chatId.createChild()
        └── ... other children of that execution
```

This means you can always walk up from any ErrorDescriptor to find its execution context (`parent()` → start event) and session (`parent().parent()` → chatId). The `ActionRetryListenerImpl` creates the error's contextId by calling `createChild()` on the start event's nodeId, which it resolves from the last BlackboardHistory entry's contextId.

| Variant | Fields | Description |
|---------|--------|-------------|
| `NoError` | `contextId: ArtifactKey` | Default state — no errors detected. Still has a real contextId (child of start event's nodeId) because it is stored in BlackboardHistory. |
| `CompactionError` | `actionName: String`, `compactionStatus: CompactionStatus`, `compactionCompleted: boolean`, `contextId: ArtifactKey` | ACP session compaction was detected. `compactionCompleted` indicates whether the listener confirmed compaction finished (via polling) before the retry fires. |
| `ParseError` | `actionName: String`, `rawOutput: String`, `contextId: ArtifactKey` | LLM returned output that could not be parsed as valid JSON |
| `TimeoutError` | `actionName: String`, `retryCount: int`, `contextId: ArtifactKey` | LLM call returned empty/null after retries |
| `UnparsedToolCallError` | `actionName: String`, `toolCallText: String`, `contextId: ArtifactKey` | LLM emitted a tool call as final structured output instead of the expected response |

All variants include a `contextId: ArtifactKey` field — this is a child of the `AgentExecutorStartEvent.nodeId`, which is itself a child of the `chatId`. All error variants (except `NoError`) also include an `actionName` field that identifies which action produced the error. This is auto-detected from the last BlackboardHistory entry (since we don't support parallel execution per agent session) or from the most recent `AgentExecutorStartEvent`.

### CompactionStatus (enum)

| Value | Description |
|-------|-------------|
| `NONE` | No compaction detected |
| `FIRST` | First compaction event for this action execution |
| `MULTIPLE` | Two or more compaction events for this action execution |

### ErrorTemplates (record)

Per-action configuration mapping error states to alternative template names.

| Field | Type | Description |
|-------|------|-------------|
| `compactionFirstTemplate` | `String` (nullable) | Template to use on first compaction retry. Null = use default template |
| `compactionMultipleTemplate` | `String` (nullable) | Template to use on subsequent compaction retries. Null = use default template |
| `parseErrorTemplate` | `String` (nullable) | Template to use on parse error retry |
| `defaultRetryTemplate` | `String` (nullable) | Fallback retry template for any unmatched error type |

## Modified Entities

### AgentActionMetadata (existing record — add field)

| New Field | Type | Description |
|-----------|------|-------------|
| `errorTemplates` | `ErrorTemplates` (nullable) | Per-action error template configuration. Null = use default template for all retries |

### PromptContext (existing record — add field)

| New Field | Type | Description |
|-----------|------|-------------|
| `errorDescriptor` | `ErrorDescriptor` | **Single source of truth** for retry state. Defaults to `NoError`. Set by AgentExecutor from BlackboardHistory. All downstream decisions — template selection, contributor filtering (via RetryAware), decorator adaptation — are made by destructuring this one field. Contains the error type classification and compaction status (embedded in CompactionError variant). |

### DecoratorContext (existing record — add field)

| New Field | Type | Description |
|-----------|------|-------------|
| `errorDescriptor` | `ErrorDescriptor` | Same ErrorDescriptor from PromptContext. Passed to decorators so they can adapt request/result decoration by destructuring the error variant. |

### PromptContributorService (existing class — absorb prompt assembly)

Centralize all prompt assembly logic here (moved from `AgentExecutor.assemblePrompt()`). This service becomes the single place where contributors are gathered, RetryAware-filtered, sorted, adapted, and rendered.

| New Responsibility | Description |
|-------------------|-------------|
| Factory-level RetryAware filtering | Before calling `factory.create()`, check if factory implements `RetryAware`. If error state active and factory returns `false` → skip |
| Contributor-level RetryAware filtering | After assembly and sort, filter contributors: non-`RetryAware` excluded on retry; `RetryAware` consulted per error type |
| Template rendering | Absorb `assemblePrompt()` / `renderTemplate()` from `AgentExecutor` |
| Error handling | Existing skip-and-log for factory failures continues to apply |

### BlackboardHistory (existing class — add methods)

| New Method | Return Type | Description |
|------------|-------------|-------------|
| `compactionStatus()` | `CompactionStatus` | Returns the compaction status based on error entries for the current action |
| `errorType()` | `ErrorDescriptor` | Returns the most recent error descriptor, or `NoError` if none |
| `addError(ErrorDescriptor)` | `void` | Records an error descriptor as a blackboard history entry |
| `errorCount(Class<? extends ErrorDescriptor>)` | `long` | Counts errors of a specific type in history |

### BlackboardHistory.History (existing inner record — add support)

Error descriptors are stored as `BlackboardHistory.Entry` instances where `input()` returns the `ErrorDescriptor` and `actionName()` identifies which action the error relates to.

## New Interface

### RetryAware (interface — applicable to both PromptContributor and PromptContributorFactory)

Interface for prompt contributors **and** prompt contributor factories that need to control their inclusion during retry scenarios. Applies at two levels:
- **Factory level**: If a `PromptContributorFactory` implements `RetryAware` and returns `false` for the current error type, none of its contributors are created (the factory is skipped entirely).
- **Contributor level**: If a `PromptContributor` implements `RetryAware` and returns `false`, that individual contributor is excluded.
- Contributors/factories that do NOT implement `RetryAware` are **excluded by default on any retry** — they must opt in to retry inclusion by implementing this interface.

Each method receives the specific `ErrorDescriptor` variant (destructured from the sealed type) and returns `boolean` — `true` to include this contributor/factory during that retry type, `false` to exclude.

| Method | Signature | Default Return | Description |
|--------|-----------|----------------|-------------|
| `includeOnCompaction` | `boolean includeOnCompaction(CompactionError error)` | `false` | Called when retry is due to session compaction. Most contributors should return `false` to reduce prompt size. |
| `includeOnCompactionMultiple` | `boolean includeOnCompactionMultiple(CompactionError error)` | `false` | Called when 2+ compaction retries have occurred. Only the most essential contributors (e.g., output schema) should return `true`. |
| `includeOnParseError` | `boolean includeOnParseError(ParseError error)` | `false` | Called when retry is due to unparseable LLM output. |
| `includeOnTimeout` | `boolean includeOnTimeout(TimeoutError error)` | `false` | Called when retry is due to empty/null response. |
| `includeOnUnparsedToolCall` | `boolean includeOnUnparsedToolCall(UnparsedToolCallError error)` | `false` | Called when LLM returned a tool call as final structured output. |

**Filtering logic** (centralized in `PromptContributorService`):
```
// Phase 1: Factory-level filtering
for each factory:
  if errorDescriptor is NoError → call factory.create(promptContext) (normal path)
  else if factory implements RetryAware →
    match errorDescriptor variant, call the corresponding includeOn*() method
    if true → call factory.create(promptContext)
    if false → skip factory entirely (none of its contributors created)
  else → skip factory (default: excluded on retry)

// Phase 2: Contributor-level filtering (on assembled list)
for each contributor:
  if errorDescriptor is NoError → include (normal path)
  else if contributor implements RetryAware →
    match errorDescriptor variant, call the corresponding includeOn*() method
  else → exclude (default: excluded on retry)
```

**Example implementations**:
- `JsonOutputFormatPromptContributorFactory` + `JsonOutputFormatPromptContributor`: Both implement `RetryAware`, returns `true` for ALL methods (always included)
- `FilteredPromptContributorAdapterFactory`: Implements `RetryAware`, returns `true` for ALL methods (filter policies still apply during retry)
- `WeAreHerePromptContributor`: Implements `RetryAware`, returns `true` for `includeOnCompaction`, `false` for `includeOnCompactionMultiple`
- `JustificationPromptContributorFactory`: Implements `RetryAware` on factory, returns `true` for `includeOnCompaction` and `includeOnParseError`, `false` for `includeOnCompactionMultiple`
- `CurationHistoryContextContributorFactory`: Does NOT implement `RetryAware` → automatically excluded on any retry

### AcpSessionRetryContext (new class)

Per-session retry state holder for the ACP layer. Tracks errors that don't propagate to the framework retry (e.g., compaction mid-stream, empty generations within a single `invokeChat` call). Does NOT implement `HasContextId` — it is an in-memory map entry, not a graph node. However, it holds the start event's `chatId` and `nodeId` so the listener can mint child contextIds for ErrorDescriptors.

| Field | Type | Description |
|-------|------|-------------|
| `sessionKey` | `String` | The ACP session key this context tracks |
| `currentError` | `ErrorDescriptor` | Most recent error for this session (reuses the sealed hierarchy from above). Default `NoError` |
| `compactionStatus` | `CompactionStatus` | Compaction count for this session |
| `retryCount` | `int` | Number of retries within the current executor lifecycle (start→complete) |
| `lastStartChatId` | `ArtifactKey` | The `chatId` (sessionKey) from the most recent `AgentExecutorStartEvent`. Used as the session-level parent. |
| `lastStartNodeId` | `ArtifactKey` | The `nodeId` from the most recent `AgentExecutorStartEvent`. `createChild()` on this produces ErrorDescriptor contextIds. |

| Method | Return Type | Description |
|--------|-------------|-------------|
| `recordStartEvent(ArtifactKey chatId, ArtifactKey nodeId)` | `void` | Stores the start event's chatId and nodeId. Called by AcpRetryEventListener on AgentExecutorStartEvent. |
| `recordError(ErrorDescriptor)` | `void` | Records an error, increments retry count, updates compaction status if applicable |
| `createErrorContextId()` | `ArtifactKey` | Returns `lastStartNodeId.createChild()` — the contextId for a new ErrorDescriptor |
| `clearOnSuccess()` | `void` | Resets error state and retry count (called on `AgentExecutorCompleteEvent`) |
| `isRetry()` | `boolean` | True if `retryCount > 0` or `currentError` is not `NoError` |
| `errorType()` | `ErrorDescriptor` | Returns `currentError` |
| `compactionStatus()` | `CompactionStatus` | Returns current compaction status |

### AgentExecutorStartEvent (new event)

Emitted by `AgentExecutor.run()` before the LLM call (step 4). Signals to `AcpRetryEventListener` to initialize (or re-initialize) retry tracking for the session.

| Field | Type | Description |
|-------|------|-------------|
| `sessionKey` | `String` | The ACP session key (from `promptContext.chatId().value()`) |
| `actionName` | `String` | The action being executed |
| `nodeId` | `String` | `chatId.createChild().value()` — a child of the session key. This becomes the parent for all ErrorDescriptors in this execution. |

### AgentExecutorCompleteEvent (new event)

Emitted by `AgentExecutor.run()` after the LLM call returns a successful result. Signals to `AcpRetryEventListener` to clear retry state for the session.

| Field | Type | Description |
|-------|------|-------------|
| `sessionKey` | `String` | The ACP session key (from `promptContext.chatId().value()`) |
| `actionName` | `String` | The action that completed |
| `nodeId` | `String` | `chatId.createChild().value()` — a child of the session key (sibling of the start event's nodeId). |

### AcpRetryEventListener (new @Component)

Spring component implementing `EventListener` that maintains per-session retry state. Injected into `AcpChatModel`.

**ArtifactKey resolution for error creation**: When creating ErrorDescriptors, the listener needs a parent nodeId (the start event's nodeId) to call `createChild()`. It resolves this by storing the last `AgentExecutorStartEvent`'s `chatId` and `nodeId` per root ArtifactKey. Since each agent session is single-threaded and the root ArtifactKey (`ArtifactKey.root()`) identifies the session, the last start event for a given root is always correct.

When `CompactionEvent` or `NodeErrorEvent` arrives:
1. Extract the root from the event's contextId: `event.contextId().root()`
2. Look up the stored start event's nodeId for that root
3. Create the ErrorDescriptor with `contextId = new ArtifactKey(startEventNodeId).createChild()`
4. Pass `chatId` and `nodeId` from the stored start event through to the ErrorDescriptor

| Subscribed Event | Behavior |
|-----------------|----------|
| `ChatSessionCreatedEvent` | Create `AcpSessionRetryContext` entry keyed by session key |
| `AgentExecutorStartEvent` | Initialize/reset retry tracking for the session. **Store `chatId` (sessionKey) and `nodeId`** on the context keyed by root ArtifactKey — these are used to mint child contextIds for ErrorDescriptors. |
| `CompactionEvent` | Resolve root from event contextId → look up stored start event → create `CompactionError` with `contextId = startEventNodeId.createChild()`. Record on session's retry context. |
| `NodeErrorEvent` | Same root resolution → create appropriate `ErrorDescriptor` with `contextId = startEventNodeId.createChild()`. Record on session's retry context. |
| `AgentExecutorCompleteEvent` | Clear retry state — the execution succeeded |
| `ChatSessionClosedEvent` | Remove the session's retry context from the map. **Note**: Current `ChatSessionClosedEvent.nodeId()` returns `sessionId` — this event needs to be updated to have a separate `sessionId`/`chatId` field, with `nodeId` created as a child of that. |

Storage: `ConcurrentHashMap<String, AcpSessionRetryContext>` keyed by session key. Additionally, `ConcurrentHashMap<String, StartEventContext>` keyed by root ArtifactKey value, where `StartEventContext` holds the last start event's `chatId` and `nodeId`.

## Relationships

```
AgentActionMetadata ──has──> ErrorTemplates (nullable, per-action config)
AgentExecutor ──reads──> BlackboardHistory.errorType() / compactionStatus()
AgentExecutor ──selects──> template based on ErrorTemplates + ErrorDescriptor
PromptContributorService ──filters──> PromptContributorFactories via RetryAware (factory level)
PromptContributorService ──filters──> PromptContributors via RetryAware (contributor level)
FilteredPromptContributorAdapterFactory ──implements──> RetryAware (always included on retry)
ActionRetryListenerImpl ──writes──> BlackboardHistory.addError(ErrorDescriptor)
ActionRetryListenerImpl ──reads──> AgentProcess.blackboard → BlackboardHistory
PromptContext ──carries──> ErrorDescriptor (for contributor self-filtering via RetryAware)
DecoratorContext ──carries──> ErrorDescriptor (for decorator adaptation)
AgentExecutor ──emits──> AgentExecutorStartEvent (before LLM call)
AgentExecutor ──emits──> AgentExecutorCompleteEvent (after successful response)
DecorateRequestResults ──runs──> LlmCallDecorator pipeline (moved from DefaultLlmRunner)
AcpRetryEventListener ──maintains──> ConcurrentHashMap<sessionKey, AcpSessionRetryContext>
AcpRetryEventListener ──listens──> AgentExecutorStartEvent, AgentExecutorCompleteEvent, CompactionEvent, NodeErrorEvent, ChatSessionCreatedEvent, ChatSessionClosedEvent
AcpChatModel ──reads──> AcpRetryEventListener.retryContextFor(sessionKey)
```

## State Transitions

```
ArtifactKey creation during execution:
  chatId = promptContext.chatId()                    // session key
  startNodeId = chatId.createChild()                 // AgentExecutorStartEvent.nodeId
  errorContextId = startNodeId.createChild()         // ErrorDescriptor.contextId
  completeNodeId = chatId.createChild()              // AgentExecutorCompleteEvent.nodeId

Initial call:
  AgentExecutor emits AgentExecutorStartEvent(nodeId = chatId.createChild())
  ErrorDescriptor = NoError(contextId = startNodeId.createChild())
  CompactionStatus = NONE
  → Use default template, all prompt contributors included

First error (compaction):
  ActionRetryListener fires → resolves startNodeId from history
  → addError(CompactionError(FIRST, contextId = startNodeId.createChild()))
  ErrorDescriptor = CompactionError(FIRST)
  CompactionStatus = FIRST
  → Use default template (or compactionFirstTemplate if set)
  → Exclude non-RetryAware contributors
  → For RetryAware contributors: call includeOnCompaction(error) to decide

Second error (compaction):
  ActionRetryListener fires → addError(CompactionError(MULTIPLE))
  ErrorDescriptor = CompactionError(MULTIPLE)
  CompactionStatus = MULTIPLE
  → Use compactionMultipleTemplate (or defaultRetryTemplate)
  → Exclude non-RetryAware contributors
  → For RetryAware contributors: call includeOnCompactionMultiple(error) to decide

Parse error:
  ActionRetryListener fires → addError(ParseError(rawOutput))
  → Use parseErrorTemplate (or default template)
  → Exclude non-RetryAware contributors
  → For RetryAware contributors: call includeOnParseError(error) to decide

--- Session-Level Retry (ACP layer) ---

AgentExecutorStartEvent received:
  AcpRetryEventListener initializes/resets AcpSessionRetryContext for sessionKey
  retryCount = 0, currentError = NoError

Compaction mid-stream (within invokeChat):
  CompactionEvent fired → AcpRetryEventListener records CompactionError on session context
  retryCount increments
  AcpChatModel checks isRetry() → true → sends CONTINUE instead of full prompt

AgentExecutorCompleteEvent received:
  AcpRetryEventListener clears session retry state
  retryCount = 0, currentError = NoError

ChatSessionClosedEvent received:
  AcpRetryEventListener removes session context from map entirely
```
