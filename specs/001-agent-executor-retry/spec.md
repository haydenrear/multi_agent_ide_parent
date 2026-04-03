# Feature Specification: Centralize LLM Execution with Blackboard-History-Driven Retry

**Feature Branch**: `001-agent-executor-retry`
**Created**: 2026-04-02
**Status**: Draft
**Input**: User description: "Centralize LLM execution in AgentExecutor with blackboard-history-driven retry. Remove retry logic from AcpChatModel and instead interpolate error state from BlackboardHistory. Move all DefaultLlmRunner calls and decorator handling from individual agent actions into AgentExecutor. Add error/retry state fields to AgentActionMetadata so each action can specify different templates and prompt contributors for retry scenarios. Use BlackboardHistory to detect compaction status (FIRST, MULTIPLE) and error types via sealed interface. Pass error descriptors into prompt context and decorator context. Stage 1: centralize LLM calls and decorators in AgentExecutor. Stage 2: add blackboard-history-driven retry with per-action error templates and prompt contributor filtering (SkipCompaction interface)."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Centralize All LLM Calls in AgentExecutor (Priority: P1)

As a workflow operator, when any agent action executes, all LLM interaction should go through a single execution point so that retry behavior, prompt assembly, and decorator logic are consistent and maintainable across all ~17 agent action types.

Currently, each agent action in AgentInterfaces independently calls `llmRunner.runWithTemplate(...)`, builds its own prompt context, and runs its own decorator pipeline. This creates duplication and makes it impossible to add cross-cutting retry behavior. By routing every action through AgentExecutor's `run()` method, we consolidate the request decoration, prompt context building, tool context building, LLM invocation, and result decoration into one place.

**Why this priority**: This is the foundational change. Without centralizing LLM calls first, there is no single place to add retry logic. Every other story depends on this.

**Independent Test**: Can be fully tested by running any agent action (e.g., orchestrator, discovery, planning) and verifying that the LLM call goes through AgentExecutor rather than being invoked directly in the agent action method. The observable behavior of each action remains identical.

**Cucumber Test Tag**: `@executor-centralize`

**Acceptance Scenarios**:

1. **Given** the system is running with all agent types active, **When** any agent action fires (e.g., coordinate workflow, consolidate discovery findings, dispatch ticket agents), **Then** the LLM call is routed through AgentExecutor.run() with the correct AgentActionMetadata, and the response is identical to the previous direct-call behavior.
2. **Given** an agent action that previously called llmRunner directly, **When** that action fires, **Then** no direct llmRunner calls exist in the action method — all decoration and invocation is delegated to AgentExecutor.
3. **Given** the AgentExecutor receives a request for any action, **When** it executes the full pipeline (request decoration, prompt context build, tool context build, LLM call, result decoration), **Then** all existing decorators (request, prompt, tool, result) are applied in the same order as before.

---

### User Story 2 - Blackboard History Error and Compaction State Tracking (Priority: P2)

As a system operator, when an LLM call fails (compaction event, parse error, timeout, etc.), the error should be recorded in the blackboard history so that subsequent retry attempts can adapt their behavior based on the type and frequency of errors.

The blackboard history already subscribes to events. This story adds error tracking: when an error occurs during an LLM call, a structured error record is written to the blackboard history. The history then exposes a compaction status (FIRST or MULTIPLE) and an error type classification so that the retry system can make informed decisions about template selection and prompt contributor filtering.

**Why this priority**: This is the data layer for retry intelligence. Without error state in the blackboard history, the retry system has nothing to react to.

**Independent Test**: Can be tested by simulating an LLM error event and verifying that the blackboard history correctly records it, reports the right compaction status, and classifies the error type. No retry behavior needs to be active — just the state tracking.

**Cucumber Test Tag**: `@executor-error-tracking`

**Acceptance Scenarios**:

1. **Given** the blackboard history has no prior errors for an action, **When** a compaction event is detected during an LLM call, **Then** the blackboard history records the error and reports compaction status as FIRST.
2. **Given** the blackboard history already has one compaction error for an action, **When** a second compaction event occurs, **Then** the blackboard history reports compaction status as MULTIPLE.
3. **Given** an LLM call encounters a parse error, timeout, or other categorized failure, **When** the error is recorded, **Then** the blackboard history classifies it with the correct error type and the error type is retrievable for downstream decision-making.
4. **Given** the blackboard history has no errors for an action, **When** the error type is queried, **Then** it returns NoError.

---

### User Story 3 - Retry-Aware Template and Prompt Contributor Selection (Priority: P3)

As a workflow operator, when an agent action is retried after an error, the system should automatically select a different template and/or filter out unnecessary prompt contributors based on the error type and retry count, so that retries are leaner, cheaper, and more likely to succeed.

On a first compaction retry, some prompt contributors are skipped (those marked with SkipCompaction) but the same base template is used. On a second or subsequent retry, all non-essential prompt contributors are skipped and a dedicated retry template is used that contains only the minimum necessary content (primarily the output schema). Each AgentActionMetadata specifies its own error templates so different agent types can have different retry strategies.

**Why this priority**: This is the payoff — adaptive retry behavior. It depends on both the centralized execution (Story 1) and the error tracking (Story 2).

**Independent Test**: Can be tested by setting up a blackboard history with a known error state (e.g., one prior compaction), invoking the agent action, and verifying that the correct template is selected and the correct prompt contributors are filtered.

**Cucumber Test Tag**: `@executor-retry-templates`

**Acceptance Scenarios**:

1. **Given** the blackboard history shows a FIRST compaction error for an action, **When** the action retries, **Then** all prompt contributors that do NOT implement RetryAware are excluded, and for those that do, the system calls `includeOnCompaction(error)` — only those returning `true` are included.
2. **Given** the blackboard history shows MULTIPLE compaction errors for an action, **When** the action retries, **Then** the system switches to the action's dedicated retry template, excludes non-RetryAware contributors, and calls `includeOnCompactionMultiple(error)` on RetryAware contributors — typically only the output schema contributor returns `true`.
3. **Given** a specific error type (e.g., parse error), **When** the agent executor processes the retry, **Then** it destructures the error descriptor, calls the matching `includeOn*()` method on each RetryAware contributor, and selects the appropriate template from the action's error templates configuration.
4. **Given** the blackboard history shows NoError, **When** an action executes, **Then** the normal template and full set of prompt contributors are used — no retry filtering is applied.

---

### User Story 4 - Remove Retry Logic from AcpChatModel (Priority: P4)

As a system maintainer, the retry and compaction-handling logic currently embedded in AcpChatModel should be removed, since retry is now handled at the AgentExecutor level using blackboard history state. AcpChatModel becomes a clean pass-through for LLM communication.

The existing retry loops (`invokeChat`, `handleCompactingSession`, `handleIncompleteJson`) are fragile and tightly coupled to the transport layer. By removing this logic, AcpChatModel focuses solely on session management and message transport, while retry decisions are made at the orchestration layer where full context (blackboard history, action metadata, prompt contributors) is available.

**Why this priority**: Cleanup that reduces complexity and prevents the old retry path from conflicting with the new one. Depends on Stories 1-3 being functional first.

**Independent Test**: Can be tested by verifying that AcpChatModel no longer contains retry loops or compaction handling, and that all retry behavior is observable only through the AgentExecutor pathway.

**Cucumber Test Tag**: `@executor-acp-cleanup`

**Acceptance Scenarios**:

1. **Given** AcpChatModel receives an LLM response that would previously trigger a retry (e.g., compaction signal, incomplete JSON), **When** it processes the response, **Then** it returns the raw result without retrying — the error is propagated as an event for the blackboard history to capture.
2. **Given** a workflow is running end-to-end, **When** a compaction error occurs, **Then** the retry is initiated by AgentExecutor (not AcpChatModel) based on the blackboard history state.

---

### Edge Cases

- What happens when the blackboard history has errors from a previous unrelated action with the same action name? Error state must be scoped to the current execution context (node + action), not globally by action name.
- How does the system handle rapid consecutive errors where the blackboard history hasn't finished recording the first error before the second occurs? Error recording should be synchronous within the execution pipeline to prevent race conditions.
- What if an action has no retry template configured? The system should fall back to the default template with a warning, rather than failing.
- What happens when the maximum retry count is exceeded? The system should propagate the error as a terminal failure event rather than retrying indefinitely.
- What if a prompt contributor throws an exception during the retry path when it was not filtered? The existing skip-and-log behavior in assemblePrompt should handle this consistently.
- What if compaction takes longer than the max poll timeout (200s = 20 polls x 10s)? The listener returns with `compactionCompleted=false`, the framework fires the next retry attempt, AcpChatModel will likely detect compaction again, and the cycle repeats. The framework's `maxAttempts` (default 5) bounds the total retries.
- What if AcpChatModel is called directly (not via AgentExecutor) after the cleanup? It should still throw typed exceptions — callers are responsible for handling them. The ActionRetryListener path only applies to calls within the embabel-agent action execution framework.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST route all agent LLM calls through a single execution point (AgentExecutor.run) rather than allowing direct llmRunner calls from agent action methods.
- **FR-002**: System MUST preserve the existing decoration pipeline order (request decoration, prompt context build, tool context build, LLM call, result decoration) when centralizing calls.
- **FR-003**: System MUST record LLM call errors (compaction events, parse failures, timeouts) as structured entries in the blackboard history, scoped to the current node and action.
- **FR-004**: System MUST expose a compaction status on the blackboard history that distinguishes between no prior compaction (NONE), a first compaction (FIRST), and multiple compactions (MULTIPLE) for a given action execution.
- **FR-005**: System MUST expose an error type classification on the blackboard history using a sealed type hierarchy (NoError, CompactionError, ParseError, TimeoutError, etc.) that downstream components can match over.
- **FR-006**: System MUST support per-action error template configuration, allowing each AgentActionMetadata to specify alternative templates for different error/retry scenarios.
- **FR-007**: System MUST provide a `RetryAware` interface applicable to both prompt contributors and prompt contributor factories, with a method per error type (compaction, compaction-multiple, parse error, timeout, unparsed tool call), each defaulting to exclude (return false). Contributors and factories that do NOT implement `RetryAware` are excluded by default on any retry. Factory-level filtering skips the entire factory (none of its contributors created); contributor-level filtering excludes individual contributors.
- **FR-008**: System MUST pass an error descriptor into the prompt context and decorator context so that prompt contributors and decorators can adapt their behavior based on the current error state.
- **FR-009**: System MUST remove the existing retry logic from AcpChatModel (invokeChat null/empty retry loop, unparsed tool call retry, handleIncompleteJson, handleCompactingSession polling loop, hash-based isRetry detection via lastPromptHashPerSession) once the new retry mechanism is in place. AcpChatModel should detect errors and throw typed exceptions, not retry. Session-level retry detection is replaced by AcpRetryEventListener.
- **FR-009a**: System MUST wait for compaction to complete before retrying a compaction error. The wait (polling for compaction completion) happens inside the ActionRetryListener before the framework fires the next retry attempt. Other error types (parse, timeout) use the framework's standard exponential backoff without additional waiting.
- **FR-010**: System MUST ensure that on retry, only prompt contributors that implement `RetryAware` and return `true` for the current error type are included. All others are excluded.
- **FR-011**: System MUST ensure `JsonOutputFormatPromptContributorFactory` and its inner `JsonOutputFormatPromptContributor` both implement `RetryAware` and return `true` for all error types — they are never filtered out on retry. The `FilteredPromptContributorAdapterFactory` (data-layer filter policy adapter) must also implement `RetryAware` and return `true` for all error types.
- **FR-013**: System MUST centralize all prompt assembly (contributor gathering, RetryAware filtering, template rendering) in `PromptContributorService`, moving `assemblePrompt()` logic from `AgentExecutor` into the service.
- **FR-014**: System MUST emit `AgentExecutorStartEvent` before each LLM call and `AgentExecutorCompleteEvent` after each successful response in `AgentExecutor.run()`, providing the authoritative signal for action identity and execution lifecycle.
- **FR-016**: System MUST move the `LlmCallDecorator` pipeline from `DefaultLlmRunner` into `DecorateRequestResults` (following the existing arg-record pattern), so that integration tests which mock `DefaultLlmRunner` still exercise the full decorator pipeline. `DefaultLlmRunner` becomes a thin pass-through for ACP options resolution and template execution only.
- **FR-015**: System MUST fix `ChatSessionClosedEvent` to have a separate `sessionId`/`chatId` field, with `nodeId` as a child of that session ID.
- **FR-012**: System MUST propagate errors as events that the blackboard history can capture, rather than handling them silently within the transport layer.

### Key Entities

- **Error Descriptor**: Represents the current error state for an action execution. Contains the error type classification and compaction status. Passed through prompt context and decorator context.
- **Compaction Status**: Enumeration (NONE, FIRST, MULTIPLE) representing how many compaction events have occurred for the current action execution.
- **Error Type**: Sealed type hierarchy classifying the kind of error encountered (NoError, CompactionError, ParseError, TimeoutError, and extensible for future error types).
- **Error Templates**: Per-action configuration mapping error types and compaction statuses to alternative templates. Stored as a field on AgentActionMetadata.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of agent LLM calls are routed through a single execution method, with zero direct llmRunner invocations remaining in agent action methods.
- **SC-002**: All agent actions produce identical outputs before and after centralization when no errors occur — zero behavioral regressions in the happy path.
- **SC-003**: On any retry, only prompt contributors implementing `RetryAware` that return `true` for the specific error type are included — all others are excluded by default, reducing prompt size and cost.
- **SC-004**: On subsequent compaction retries, the system uses a dedicated retry template that is at least 50% smaller than the default template, containing only the essential output schema.
- **SC-005**: Error state is correctly recorded and retrievable from the blackboard history within the same execution cycle — no error events are lost or misclassified.
- **SC-006**: The AcpChatModel contains zero retry loops or compaction-handling logic after cleanup, reducing its responsibility to session management and message transport only.

## Assumptions

- The existing AgentExecutor.run() method already handles the full decoration pipeline for some actions and can be extended to cover all actions without architectural changes.
- The blackboard history's event subscription mechanism is sufficient for recording error events synchronously within the execution pipeline.
- The number of distinct error types needed at launch is small (NoError, CompactionError, ParseError, TimeoutError) and can be extended later via the sealed type hierarchy.
- All ~17 agent actions in AgentInterfaces follow the same pattern (build prompt context, call llmRunner, decorate result) and can be uniformly migrated.
- The retry template for each action needs to contain at minimum the output schema (JSON schema) to guide the LLM's response format.
