# Internal Contracts: ACP Session Lifecycle Management

## Overview

Session lifecycle management introduces contracts across three layers: request enrichment (recycling), event emission (agentModel propagation), and event handling (cleanup).

## Contract 1: Session Recycling in RequestEnrichment

**Applies to**: All `enrichAgentRequests()`, `enrichInterruptRequest()`, and inline enrichment paths

**New Overload**:
```java
private ArtifactKey resolveContextId(
    OperationContext context,
    AgentModels.AgentRequest currentRequest,
    Artifact.AgentModel parent
)
```

**Behavior**:
1. If `contextIdService == null`, return null
2. If `shouldCreateNewSession(currentRequest)` returns true → generate new contextId
3. Otherwise → look up `BlackboardHistory.getEntireBlackboardHistory(blackboard)`
4. If history contains a previous request of the same class → return its contextId
5. Otherwise → generate new contextId as fallback

**shouldCreateNewSession contract**:
- Returns `true` for: `DiscoveryAgentRequest`, `PlanningAgentRequest`, `TicketAgentRequest`
- Returns `false` for all other `Artifact.AgentModel` types

**findPreviousContextId contract**:
- Calls `history.getLastOfType(currentRequest.getClass())`
- Returns `previous.contextId()` if non-null, else null

**Old overload preserved**:
```java
private ArtifactKey resolveContextId(OperationContext context, AgentType agentType, Artifact.AgentModel parent)
```
This is retained for the `enrichAgentResult()` path which handles `AgentResult` types that don't participate in session recycling.

---

## Contract 2: ActionCompletedEvent with AgentModel

**Applies to**: EmitActionCompletedResultDecorator

**Modified Record**:
```java
record ActionCompletedEvent(
    String eventId,
    Instant timestamp,
    String nodeId,
    String agentName,
    String actionName,
    String outcomeType,
    Artifact.AgentModel agentModel  // NEW
) implements AgentEvent
```

**Emission Rules**:
- `decorate(AgentResult)`: emits ActionCompletedEvent with `agentModel = t` (the result itself)
- `decorateRequestResult(AgentRequest)`: emits ActionCompletedEvent with `agentModel = t` (the request itself)
- `decorate(Routing)`: does NOT emit ActionCompletedEvent (routing types only handle unsubscription)

**GoalCompletedEvent** is emitted only when `t instanceof OrchestratorCollectorResult`, passing the result as the model.

---

## Contract 3: AcpSessionCleanupService EventListener

**Applies to**: AcpSessionCleanupService

**Subscription**:
- Registered as a Spring `@Component` implementing `EventListener`
- `listenerId()` returns `"acp-session-cleanup"`
- `isInterestedIn(GraphEvent)` returns true for `ActionCompletedEvent` and `GoalCompletedEvent`

**ActionCompletedEvent Handling**:
```
agentModel match:
  DiscoveryAgentResult  → closeSession(result.key())
  PlanningAgentResult   → closeSession(result.key())
  TicketAgentResult     → closeSession(result.key())
  _                     → no action
```

**GoalCompletedEvent Handling**:
```
model match:
  OrchestratorCollectorResult → closeDescendantSessions(result.key())
  _                           → no action
```

**closeSession contract**:
1. Remove entry from `sessionManager.sessionContexts` by `contextId.value()`
2. If entry existed:
   a. Call `session.getClient().getProtocol().close()` (catch and log exceptions)
   b. Publish `ChatSessionClosedEvent` with the contextId

**closeDescendantSessions contract**:
1. Iterate all entries in `sessionManager.sessionContexts`
2. For each key, parse as ArtifactKey
3. If `artifactKey.isDescendantOf(rootKey)` or `artifactKey.equals(rootKey)` → closeSession
4. Invalid keys (non-ArtifactKey format) are silently skipped

---

## Contract 4: ChatSessionClosedEvent

**New Record**:
```java
record ChatSessionClosedEvent(
    String eventId,
    Instant timestamp,
    String sessionId
) implements GraphEvent
```

**Published by**: AcpSessionCleanupService after successful session removal  
**nodeId()**: returns `sessionId`  
**eventType()**: returns `"CHAT_SESSION_CLOSED"`

**Exhaustive Switch Coverage**: All files with exhaustive switches over `GraphEvent` subtypes must include a case for `ChatSessionClosedEvent`:
- `BlackboardHistory.classifyEventTargets()` → returns empty list
- `AgentEventListener.onEvent()` → no-op case
- `ArtifactEventListener.onEvent()` → covered by `default` case

---

## Backward Compatibility

| Change | Impact | Mitigation |
|--------|--------|------------|
| ActionCompletedEvent gains agentModel field | All constructors must pass the field | Record field added at end; existing code updated |
| New ChatSessionClosedEvent | Exhaustive switches break | Cases added to all affected files |
| resolveContextId new overload | No impact on existing calls | Old overload preserved for results/interrupts path |
| EmitActionCompletedResultDecorator implements DispatchedAgentResultDecorator | Receives dispatched agent results it previously didn't | By design — this is the mechanism for ActionCompletedEvent to carry dispatched agent results |
