# Quickstart: ACP Session Lifecycle Management

## Prerequisites

- Java 21
- Gradle 8.x

## Build & Verify

```bash
cd multi_agent_ide_java_parent
./gradlew compileJava compileKotlin
```

## Run Unit Tests

```bash
./gradlew :multi_agent_ide_java_parent:multi_agent_ide:test --tests "com.hayden.multiagentide.agent.AcpSessionCleanupServiceTest"
```

## Manual Verification Checklist

### 1. Session Recycling Verification

1. Set a breakpoint in `RequestEnrichment.resolveContextId(OperationContext, AgentModels.AgentRequest, Artifact.AgentModel)`
2. Run a workflow that routes back (e.g., planning collector → discovery orchestrator)
3. Verify the second DiscoveryOrchestratorRequest receives the same contextId as the first
4. Verify DiscoveryAgentRequest always receives a new contextId (shouldCreateNewSession returns true)

### 2. Dispatched Agent Cleanup Verification

1. Set a breakpoint in `AcpSessionCleanupService.handleActionCompleted`
2. Run a workflow with dispatched discovery agents
3. Verify ActionCompletedEvent fires with DiscoveryAgentResult as agentModel
4. Verify the session is removed from `sessionManager.sessionContexts`
5. Verify ChatSessionClosedEvent is published

### 3. Root Workflow Cleanup Verification

1. Set a breakpoint in `AcpSessionCleanupService.handleGoalCompleted`
2. Run a full workflow to completion (OrchestratorCollectorResult)
3. Verify GoalCompletedEvent fires with OrchestratorCollectorResult
4. Verify all sessions with descendant ArtifactKeys are removed
5. Verify `sessionManager.sessionContexts` is empty after cleanup

### 4. Concurrent Workflow Isolation

1. Start two workflows concurrently (different root ArtifactKeys)
2. Complete workflow A
3. Verify workflow B's sessions remain in `sessionManager.sessionContexts`

## Key Files

| File | Description |
|------|-------------|
| `multi_agent_ide/src/main/java/.../agent/AcpSessionCleanupService.java` | EventListener that closes sessions on agent/workflow completion |
| `multi_agent_ide_lib/src/main/java/.../service/RequestEnrichment.java` | Session recycling via resolveContextId overload |
| `multi_agent_ide/src/main/java/.../decorator/result/EmitActionCompletedResultDecorator.java` | Emits ActionCompletedEvent with agentModel |
| `acp-cdc-ai/src/main/java/.../events/Events.java` | Event definitions (ActionCompletedEvent, ChatSessionClosedEvent) |
| `acp-cdc-ai/src/main/kotlin/.../acp/AcpSessionManager.kt` | Session storage (ConcurrentHashMap) |
| `acp-cdc-ai/src/main/kotlin/.../acp/AcpChatModel.kt` | Session creation via computeIfAbsent (no changes needed) |
