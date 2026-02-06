# Implementation Plan: ACP Session Lifecycle Management

**Branch**: `011-session-cleanup` | **Date**: 2026-02-06 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/011-session-cleanup/spec.md`

## Summary

Implement ACP session lifecycle management through two complementary mechanisms: (1) session recycling at request enrichment time in RequestEnrichment.resolveContextId(), which prevents duplicate sessions by reusing contextIds for all agents except dispatched ones, and (2) session cleanup via an EventListener that closes dispatched agent sessions on ActionCompletedEvent and all remaining sessions on GoalCompletedEvent when the root OrchestratorCollectorResult completes.

## Technical Context

**Language/Version**: Java 21 (some Kotlin in ACP module)  
**Primary Dependencies**: Spring Boot 3.x, LangChain4j-Agentic, Embabel Agent Framework, Lombok  
**Storage**: In-memory ConcurrentHashMap (AcpSessionManager.sessionContexts)  
**Testing**: JUnit 5, Mockito, AssertJ  
**Target Platform**: JVM server (Linux/macOS)  
**Project Type**: Multi-module Gradle (Git submodules)  
**Performance Goals**: Event-driven cleanup with no polling; O(n) descendant scan on workflow completion  
**Constraints**: Backward compatible; must not break existing event/decorator chains  
**Scale/Scope**: All ACP sessions across all workflow types  

## Constitution Check

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Computation Graph with State Machine | PASS | Uses ArtifactKey hierarchy as implicit graph; no new state machine |
| II. Three-Phase Workflow Pattern | PASS | Cleanup respects phase boundaries — dispatched agents cleaned per-phase, root cleaned at workflow end |
| III. Orchestrator, Work, Collector Pattern | PASS | Cleanup aligns with pattern — dispatched workers cleaned individually, orchestrators/collectors cleaned at root |
| IV. Event-Driven Node Lifecycle | PASS | Cleanup driven entirely by ActionCompletedEvent and GoalCompletedEvent |
| V. Artifact-Driven Context Propagation | PASS | Session recycling uses BlackboardHistory artifacts; ArtifactKey hierarchy drives descendant cleanup |
| VI. Worktree Isolation & Feature Branching | PASS | No impact on worktree isolation |
| VII. Tool Integration & Agent Capabilities | PASS | Sessions closed via protocol.close() which terminates the ACP transport cleanly |
| VIII. Human-in-the-Loop Review Gates | PASS | No impact on review gates |

**Gate Result**: PASS - All principles satisfied.

## Project Structure

### Documentation (this feature)

```text
specs/011-session-cleanup/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── contracts/
│   └── internal.md
├── tasks.md
└── quickstart.md
```

### Source Code (repository root)

```text
multi_agent_ide_java_parent/
├── multi_agent_ide/
│   └── src/main/java/com/hayden/multiagentide/
│       ├── agent/
│       │   └── AcpSessionCleanupService.java             # NEW — EventListener for session cleanup
│       ├── agent/decorator/result/
│       │   ├── EmitActionCompletedResultDecorator.java    # MODIFY — pass agentModel, implement DispatchedAgentResultDecorator
│       │   └── DispatchedAgentResultDecorator.java        # EXISTING — now extended by EmitActionCompletedResultDecorator
│       └── infrastructure/
│           └── AgentEventListener.java                    # MODIFY — add ChatSessionClosedEvent case
│
├── multi_agent_ide_lib/
│   └── src/main/java/com/hayden/multiagentidelib/
│       ├── service/
│       │   └── RequestEnrichment.java                     # MODIFY — add resolveContextId overload with recycling
│       └── agent/
│           └── BlackboardHistory.java                     # MODIFY — add ChatSessionClosedEvent case
│
├── acp-cdc-ai/
│   └── src/main/java/com/hayden/acp_cdc_ai/acp/events/
│       └── Events.java                                    # MODIFY — add agentModel to ActionCompletedEvent, add ChatSessionClosedEvent
│
└── multi_agent_ide/
    └── src/test/java/com/hayden/multiagentide/agent/
        └── AcpSessionCleanupServiceTest.java              # NEW — unit tests for cleanup service
```

## Key Design Decisions

### 1. Two-Concern Split: Prevention + Cleanup

Session management is split into two independent mechanisms:
- **Prevention** (RequestEnrichment): Recycle contextIds so that `computeIfAbsent` in AcpChatModel naturally reuses existing sessions
- **Cleanup** (AcpSessionCleanupService): Close sessions when they are no longer needed

This separation means that even if cleanup fails, the system doesn't create duplicate sessions. And even if recycling fails, cleanup eventually releases resources.

### 2. Type-Safe Matching over AgentType Enum

All session decisions use `instanceof` pattern matching on concrete AgentModel types rather than the AgentType enum. This avoids the lossy mapping from type → enum and ensures compile-time exhaustiveness checking.

### 3. No Hierarchy Tracking in AcpSessionManager

ArtifactKey's built-in `isDescendantOf()` method provides hierarchy matching by string prefix comparison. No separate hierarchy map or parent/child tracking is needed in the session manager.

### 4. EmitActionCompletedResultDecorator extends DispatchedAgentResultDecorator

Rather than deconstructing routing types to extract the underlying model, the decorator now implements `DispatchedAgentResultDecorator` and receives dispatched agent results directly via `decorate(AgentResult)`. The `agentModel` field on ActionCompletedEvent is simply the result/request object itself.

## Complexity Notes

- **O(n) descendant scan**: On GoalCompletedEvent, all keys in sessionContexts are scanned to find descendants. This is acceptable because the number of concurrent sessions per workflow is bounded by the number of agent types (typically < 30).
- **ConcurrentHashMap iteration during modification**: `closeDescendantSessions` iterates the map and removes entries. ConcurrentHashMap's weakly consistent iterators handle this safely without ConcurrentModificationException.
