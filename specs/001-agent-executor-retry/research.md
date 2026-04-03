# Research: Centralize LLM Execution with Blackboard-History-Driven Retry

**Branch**: `001-agent-executor-retry` | **Date**: 2026-04-02

## R-001: How does the embabel-agent ActionRetryListener integrate?

**Decision**: Implement `ActionRetryListener` as a Spring `@Component` bean. The framework's `DefaultAgentPlatform` / `AbstractAgentProcess` auto-discovers it and injects it into the retry template. When an action retry fires, `onActionRetry(context, throwable, agentProcess)` is called. From the `AgentProcess` (which implements `Blackboard`), we can access the blackboard → blackboard history and write the error record.

**Rationale**: The framework's `ActionQos.retryTemplate()` already wires `ActionRetryListener` into the Spring Retry `onError` callback. A single `@Component` implementing this interface is automatically picked up. No framework modification needed — just `mvn clean install` on `libs/embabel-agent` if the interface changed (it hasn't).

**Alternatives considered**:
- Custom Spring Retry interceptor: rejected — would require modifying the framework's retry mechanism
- Event-based error capture via EventBus: rejected — events are async and may not be recorded before the retry fires; ActionRetryListener is synchronous within the retry loop

## R-002: How to retrieve BlackboardHistory from AgentProcess?

**Decision**: `AgentProcess` implements `Blackboard`. The blackboard holds objects including `BlackboardHistory`. Use `agentProcess.objects` to find the `BlackboardHistory` instance, or use the existing `BlackboardHistory.getEntireBlackboardHistory(OperationContext)` static method by resolving the `OperationContext` from the `AgentProcess.processContext`.

**Rationale**: `BlackboardHistory` already has `ensureSubscribed(EventBus, OperationContext, ...)` and is stored as a blackboard entry. The `AbstractAgentProcess` delegates `Blackboard` methods, so `agentProcess` acts as the blackboard directly.

**Alternatives considered**:
- Injecting BlackboardHistory directly: rejected — it's per-session, not a singleton bean
- Looking up via OperationContext: viable but requires resolving context from process; the direct blackboard access is simpler

## R-003: How are the ~17 agent actions structured for centralization?

**Decision**: Each action follows the same pattern: (1) get blackboard history, (2) find last request, (3) build enriched request via `decorateRequest`, (4) build template model map, (5) build prompt context via `buildPromptContext`, (6) call `llmRunner.runWithTemplate(...)`, (7) decorate routing via `decorateRouting`, (8) add result to blackboard. AgentExecutor.run() already implements steps 1-7. Migration means each action constructs `AgentExecutorArgs` and calls `agentExecutor.run()` instead of doing steps 3-7 inline.

**Rationale**: The existing `AgentExecutor.run()` was built for exactly this — some actions already use it. The remaining ~17 actions that still call llmRunner directly need to be migrated. The method signatures and decorator pipeline are identical.

**Alternatives considered**:
- AOP-based interception of llmRunner calls: rejected — doesn't give per-action control over templates/contributors
- Keeping llmRunner calls but adding a decorator: rejected — still duplicated across 17 call sites

## R-004: What error types need to be classified?

**Decision**: Start with a sealed interface hierarchy:
- `ErrorDescriptor` (sealed)
  - `NoError` — happy path, no errors
  - `CompactionError(CompactionStatus)` — ACP session compaction detected
  - `ParseError(String rawOutput)` — LLM returned unparseable JSON
  - `TimeoutError` — LLM call timed out or returned empty after retries
  - `UnparsedToolCallError(String toolCallText)` — LLM returned tool call as structured output

`CompactionStatus` enum: `FIRST`, `MULTIPLE`

**Rationale**: These are the four error types currently handled inline in AcpChatModel's `invokeChat`. Mapping them 1:1 preserves existing behavior while making it matchable.

**Alternatives considered**:
- Single generic "RetryError" type: rejected — loses the ability to vary template/contributor behavior per error type
- Exception class hierarchy: rejected — errors are data, not control flow; they need to be stored in blackboard history

## R-005: How should per-action error templates work?

**Decision**: Add an `ErrorTemplates` field to `AgentActionMetadata` that maps `ErrorDescriptor` subtype + `CompactionStatus` to template name overrides. When AgentExecutor detects a retry via blackboard history error state, it resolves the template from `ErrorTemplates` instead of the default `template()`. If no mapping exists, fall back to the default template.

**Rationale**: Different agent types have very different prompt structures — a planning orchestrator retry template differs from a discovery agent retry template. Per-action configuration lets each define its own minimal retry template.

**Alternatives considered**:
- Global retry template: rejected — one-size-fits-all loses action-specific schema requirements
- Convention-based naming (e.g., `{template}-retry`): workable but less explicit; error templates mapping is clearer

## R-006: How should retry-aware prompt contributor filtering work?

**Decision**: Add a `RetryAware` interface extending `PromptContributor` with a method per error type (`includeOnCompaction`, `includeOnCompactionMultiple`, `includeOnParseError`, `includeOnTimeout`, `includeOnUnparsedToolCall`), each defaulting to `false`. Contributors that do NOT implement `RetryAware` are excluded by default on any retry. Where prompt contributors are resolved, destructure the `ErrorDescriptor` from `PromptContext` and call the matching `includeOn*()` method on each `RetryAware` contributor.

**Rationale**: Each contributor owns its own retry policy. The JSON schema contributor returns `true` for all methods. A goal contributor might return `true` for first compaction but `false` for multiple. Discovery context contributors simply don't implement `RetryAware` and are auto-excluded. This is cleaner than marker interfaces because: (1) a single interface replaces two markers, (2) contributors make granular per-error-type decisions rather than binary skip/keep, (3) default-exclude-on-retry is the safe default — you must opt in.

**Alternatives considered**:
- Dual marker interfaces (SkipOnCompaction + RetainAlways): rejected — two markers still don't capture per-error-type granularity; adding a third error type would require a third marker
- Priority-based filtering (drop contributors below threshold): rejected — doesn't capture the semantic difference between error types
- External filtering rules in AgentExecutor: rejected — pushes retry knowledge away from the contributor that understands its own cost/value tradeoff
