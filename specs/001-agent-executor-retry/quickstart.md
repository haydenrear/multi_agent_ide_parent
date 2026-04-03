# Quickstart: Centralize LLM Execution with Blackboard-History-Driven Retry

**Branch**: `001-agent-executor-retry` | **Date**: 2026-04-02

## Overview

This feature centralizes all LLM calls through `AgentExecutor.run()` and adds blackboard-history-driven retry with per-action error templates and prompt contributor filtering.

## Stage 1: Centralize LLM Calls

### What changes

1. **AgentInterfaces action methods**: Each of the ~17 action methods that currently call `llmRunner.runWithTemplate(...)` directly will instead construct `AgentExecutorArgs` and call `agentExecutor.run()`.

2. **No behavioral change**: The decoration pipeline (request → prompt → tool → LLM → result) is identical. This is a pure refactor.

### Key files to modify

- `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java` — migrate all `llmRunner.runWithTemplate(...)` calls
- `multi_agent_ide/src/main/java/com/hayden/multiagentide/service/AgentExecutor.java` — absorb LlmCallDecorator orchestration, prompt contributor resolution, emit start/complete events
- `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/decorator/request/DecorateRequestResults.java` — add `DecorateLlmCallArgs` + `decorateLlmCall()` for LlmCallDecorator pipeline
- `multi_agent_ide/src/main/java/com/hayden/multiagentide/service/DefaultLlmRunner.java` — simplify to thin pass-through (ACP options, query build, template execution only)

## Stage 2: Blackboard-History-Driven Retry

### New files to create

1. **ErrorDescriptor sealed interface** — `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/ErrorDescriptor.java`
2. **CompactionStatus enum** — same file or alongside
3. **ErrorTemplates record** — `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/ErrorTemplates.java`
4. **RetryAware interface** — `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/prompt/RetryAware.java`
5. **ActionRetryListenerImpl** — `multi_agent_ide/src/main/java/com/hayden/multiagentide/service/ActionRetryListenerImpl.java`

### Files to modify

1. **AgentActionMetadata** — add `ErrorTemplates errorTemplates` field
2. **PromptContext** — add `ErrorDescriptor errorDescriptor` field
3. **DecoratorContext** — add `ErrorDescriptor errorDescriptor` field
4. **BlackboardHistory** — add `compactionStatus()`, `errorType()`, `addError()` methods
5. **AgentExecutor.run()** — add error state query, template resolution, delegate prompt assembly to PromptContributorService
6. **PromptContributorService** — absorb `assemblePrompt()` from AgentExecutor, add two-level RetryAware filtering (factory + contributor)
7. **FilteredPromptContributorAdapterFactory** — implement RetryAware (return true for all error types)
6. **AcpChatModel** — remove retry loops from `invokeChat`, `handleCompactingSession`, `handleIncompleteJson`

### Verification

After Stage 1:
- All agent actions produce identical results
- No `llmRunner.runWithTemplate` calls remain in AgentInterfaces

## Stage 3: Session-Level Retry Context

### New files to create

1. **AcpSessionRetryContext** — `acp-cdc-ai/src/main/kotlin/com/hayden/acp_cdc_ai/acp/AcpSessionRetryContext.kt` (or `.java`)
2. **AcpRetryEventListener** — `acp-cdc-ai/src/main/kotlin/com/hayden/acp_cdc_ai/acp/events/AcpRetryEventListener.java`

### Files to modify

1. **Events.java** — add `AgentExecutorStartEvent` and `AgentExecutorCompleteEvent`
2. **DefaultLlmRunner.java** — emit start/complete events around LLM calls
3. **AcpChatModel.kt** — inject `AcpRetryEventListener`, check `isRetry()` before calls

### Verification

After Stage 3:
- `AgentExecutorStartEvent` emitted before each LLM call
- `AgentExecutorCompleteEvent` emitted after successful response
- `AcpRetryEventListener` tracks retry count and error type per session
- On compaction mid-stream, `AcpChatModel` sends CONTINUE instead of full prompt

After all stages:
- Simulate compaction → verify FIRST/MULTIPLE status in blackboard history
- Verify RetryAware contributor filtering on retry (non-RetryAware excluded, RetryAware consulted per error type)
- Verify template switching on retry
- Verify AcpChatModel has no retry loops
