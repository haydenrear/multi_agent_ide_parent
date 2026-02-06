# Data Model: ACP Session Lifecycle Management

## Entities

### ArtifactKey (existing, no changes)
- **Fields**:
  - `value` (String) — format: `ak:<ulid>/<ulid>/...`
- **Key Methods**:
  - `createRoot()` → new root key
  - `createChild()` → new child under this parent
  - `parent()` → Optional<ArtifactKey>
  - `isDescendantOf(ArtifactKey ancestor)` → true if this key starts with ancestor + "/"
  - `isChildOf(ArtifactKey parent)` → true if direct child
  - `depth()` → number of segments
  - `isRoot()` → true if depth == 1
- **Role in Session Lifecycle**:
  - Serves as both nodeId and contextId (they are the same thing)
  - Used as the key in AcpSessionManager.sessionContexts
  - `isDescendantOf()` drives root cleanup hierarchy matching

### AcpSessionManager.sessionContexts (existing, no changes)
- **Type**: `ConcurrentHashMap<Any, AcpSessionContext>`
- **Key**: ArtifactKey.value() (String)
- **Value**: AcpSessionContext (inner class of AcpSessionManager)
- **Lifecycle**:
  - Created via `computeIfAbsent` in AcpChatModel.getOrCreateSession()
  - Removed via `remove(key)` in AcpSessionCleanupService.closeSession()

### AcpSessionContext (existing, no changes)
- **Fields**:
  - `scope` (CoroutineScope)
  - `transport` (Transport)
  - `protocol` (Protocol)
  - `client` (Client)
  - `session` (ClientSession)
  - `streamWindows` (AcpStreamWindowBuffer)
  - `messageParent` (ArtifactKey)
- **Cleanup**: `client.protocol.close()` terminates the transport and releases resources

### ActionCompletedEvent (modified)
- **Fields**:
  - `eventId` (String)
  - `timestamp` (Instant)
  - `nodeId` (String)
  - `agentName` (String)
  - `actionName` (String)
  - `outcomeType` (String)
  - `agentModel` (Artifact.AgentModel) — **NEW**: the result or request that triggered this event
- **Implements**: AgentEvent

### ChatSessionClosedEvent (new)
- **Fields**:
  - `eventId` (String)
  - `timestamp` (Instant)
  - `sessionId` (String) — the ArtifactKey.value() of the closed session
- **Implements**: GraphEvent

### GoalCompletedEvent (existing, no changes)
- **Fields**:
  - `eventId` (String)
  - `timestamp` (Instant)
  - `orchestratorNodeId` (String)
  - `workflowId` (String)
  - `model` (Artifact.AgentModel)
- **Implements**: GraphEvent

## Relationships

- `AcpSessionManager` 1→N `AcpSessionContext` (keyed by ArtifactKey.value())
- `ArtifactKey` parent→child (encoded in the key string itself via "/" segments)
- `ActionCompletedEvent` → 1 `Artifact.AgentModel` (the agentModel field)
- `GoalCompletedEvent` → 1 `Artifact.AgentModel` (the model field)
- `BlackboardHistory` → N `AgentRequest` (used for contextId recycling lookup)

## Session State Transitions

```
                    ┌──────────────────────────────────────┐
                    │                                      │
  computeIfAbsent   │         ACTIVE                       │
  ─────────────────►│  (in sessionContexts map)            │
                    │                                      │
                    └──────┬──────────────────┬────────────┘
                           │                  │
              ActionCompletedEvent    GoalCompletedEvent
              (dispatched result)     (OrchestratorCollectorResult)
                           │                  │
                           ▼                  ▼
                    ┌──────────────────────────────────────┐
                    │                                      │
                    │         CLOSED                        │
                    │  - removed from sessionContexts      │
                    │  - protocol.close() called           │
                    │  - ChatSessionClosedEvent published  │
                    │                                      │
                    └──────────────────────────────────────┘
```

## Session Recycling Decision Matrix

| Request Type | New Session? | Rationale |
|---|---|---|
| DiscoveryAgentRequest | YES | Dispatched agent — each gets isolated session |
| PlanningAgentRequest | YES | Dispatched agent — each gets isolated session |
| TicketAgentRequest | YES | Dispatched agent — each gets isolated session |
| All other AgentRequest types | RECYCLE | Top-level agents reuse previous session on route-back |
| All InterruptRequest types | RECYCLE | Interrupts target existing agent sessions |
| All ResultsRequest types | RECYCLE | Dispatch results aggregate into existing sessions |
| ContextManagerRoutingRequest | RECYCLE | Context manager reuses session |

## Cleanup Trigger Matrix

| Event | AgentModel Type | Action |
|---|---|---|
| ActionCompletedEvent | DiscoveryAgentResult | Close that session |
| ActionCompletedEvent | PlanningAgentResult | Close that session |
| ActionCompletedEvent | TicketAgentResult | Close that session |
| ActionCompletedEvent | Any other type | No action |
| GoalCompletedEvent | OrchestratorCollectorResult | Close all descendant + root sessions |
| GoalCompletedEvent | Any other type | No action |
