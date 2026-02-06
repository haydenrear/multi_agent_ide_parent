# Feature Specification: ACP Session Lifecycle Management

**Feature Branch**: `011-session-cleanup`  
**Created**: 2026-02-06  
**Status**: Implemented  
**Input**: User description: "When we receive a goal done event, we have to close all sessions of child of that root execution key. Sessions only closed after — if routed back, keep that session. Moreover, if a node is routed back to, find the previous key associated with that node type if it is a top level node. If it's a dispatched node which we create many of, we create new. However, we want to use the previous session if not, so we recycle the artifact key instead of opening new session every time."

## User Scenarios & Testing

### User Story 1 - Session Recycling for Route-Back Agents (Priority: P1)

As a workflow engine, I need to reuse existing ACP sessions when routing back to top-level agents (orchestrators, collectors, dispatchers) so that I avoid creating duplicate sessions for agents that are revisited during multi-phase workflows.

**Why this priority**: Without session recycling, every route-back creates a new ACP process, leading to unbounded session growth proportional to workflow depth. This is the primary prevention mechanism.

**Acceptance Scenarios**:

1. **Given** a PlanningCollectorRequest routes back to DiscoveryOrchestratorRequest, **When** the DiscoveryOrchestratorRequest is enriched, **Then** it receives the same contextId as the previous DiscoveryOrchestratorRequest from BlackboardHistory.
2. **Given** a DiscoveryAgentRequest is created (dispatched agent), **When** it is enriched, **Then** it always receives a new contextId (never recycled).
3. **Given** a PlanningAgentRequest is created (dispatched agent), **When** it is enriched, **Then** it always receives a new contextId.
4. **Given** a TicketAgentRequest is created (dispatched agent), **When** it is enriched, **Then** it always receives a new contextId.
5. **Given** an OrchestratorRequest has been previously enriched, **When** a second OrchestratorRequest is enriched in the same workflow, **Then** it receives the same contextId as the first.
6. **Given** an InterruptRequest targets an existing agent, **When** it is enriched, **Then** it receives the recycled contextId from the corresponding previous request in BlackboardHistory.

---

### User Story 2 - Dispatched Agent Session Cleanup (Priority: P1)

As a resource manager, I need ACP sessions for dispatched agents (discovery, planning, ticket) to be closed immediately when their result is produced so that system resources are released promptly and do not accumulate across long-running workflows.

**Why this priority**: Dispatched agents are the most numerous — each dispatch creates N parallel agents. Without cleanup, these sessions leak for the entire workflow duration.

**Acceptance Scenarios**:

1. **Given** a DiscoveryAgentResult is emitted via ActionCompletedEvent, **When** the cleanup service processes the event, **Then** the ACP session matching that result's contextId is closed and removed from the session map.
2. **Given** a PlanningAgentResult is emitted via ActionCompletedEvent, **When** the cleanup service processes the event, **Then** the corresponding session is closed.
3. **Given** a TicketAgentResult is emitted via ActionCompletedEvent, **When** the cleanup service processes the event, **Then** the corresponding session is closed.
4. **Given** an OrchestratorAgentResult is emitted via ActionCompletedEvent, **When** the cleanup service processes the event, **Then** no session is closed (orchestrator sessions persist until workflow completion).
5. **Given** a dispatched agent session is closed, **Then** a ChatSessionClosedEvent is published to the event bus.

---

### User Story 3 - Root Workflow Session Cleanup (Priority: P1)

As a resource manager, I need all remaining ACP sessions in a workflow to be closed when the root OrchestratorCollectorResult completes, so that no sessions leak after a workflow finishes.

**Why this priority**: This is the final cleanup pass that catches all sessions not individually closed by dispatched agent cleanup — orchestrators, collectors, dispatchers, context managers, reviewers, mergers.

**Acceptance Scenarios**:

1. **Given** an OrchestratorCollectorResult is emitted via GoalCompletedEvent, **When** the cleanup service processes the event, **Then** all sessions whose ArtifactKey is a descendant of the root key are closed.
2. **Given** an OrchestratorCollectorResult is emitted via GoalCompletedEvent, **When** the cleanup service processes the event, **Then** the root session itself is also closed.
3. **Given** two concurrent workflows with independent root keys, **When** workflow A completes, **Then** only workflow A's sessions are closed; workflow B's sessions remain.
4. **Given** a deeply nested session hierarchy (root → child → grandchild → great-grandchild), **When** the root completes, **Then** all levels are cleaned up.

---

### Edge Cases

- What happens if a session is already closed when cleanup attempts to close it again?
- What happens if an ActionCompletedEvent has a null agentModel?
- What happens if the sessionContexts map contains keys that are not valid ArtifactKeys?
- What happens if a GoalCompletedEvent carries a non-OrchestratorCollectorResult model?
- What happens if protocol.close() throws an exception during session teardown?

## Requirements

### Functional Requirements

#### Session Recycling at Request Enrichment Time

- **FR-001**: System MUST reuse contextIds for all non-dispatched agent request types by looking up the previous request of the same type in BlackboardHistory.
- **FR-002**: System MUST always create new contextIds for DiscoveryAgentRequest, PlanningAgentRequest, and TicketAgentRequest (dispatched agents).
- **FR-003**: Session recycling MUST use pattern matching on request class types (instanceof), not AgentType enum.
- **FR-004**: Session recycling MUST apply to all enrichment paths: AgentRequest, InterruptRequest, ResultsRequest, and ContextManagerRoutingRequest.
- **FR-005**: When no previous request of the same type exists in BlackboardHistory, the system MUST generate a new contextId.
- **FR-006**: BlackboardHistory is shared across parent workflow and sub-processes; recycling lookups MUST use the full shared history.

#### Dispatched Agent Session Cleanup

- **FR-007**: System MUST close the ACP session for a dispatched agent when an ActionCompletedEvent is received with agentModel being DiscoveryAgentResult, PlanningAgentResult, or TicketAgentResult.
- **FR-008**: Session closure MUST remove the entry from AcpSessionManager.sessionContexts.
- **FR-009**: Session closure MUST call protocol.close() to terminate the ACP transport.
- **FR-010**: Session closure MUST publish a ChatSessionClosedEvent to the event bus.
- **FR-011**: ActionCompletedEvent with any other agentModel type MUST NOT trigger session closure.

#### Root Workflow Session Cleanup

- **FR-012**: System MUST close all descendant sessions when a GoalCompletedEvent is received with an OrchestratorCollectorResult model.
- **FR-013**: Descendant identification MUST use ArtifactKey.isDescendantOf() for hierarchy matching.
- **FR-014**: The root session itself (matching by equality) MUST also be closed.
- **FR-015**: Sessions from unrelated workflows (different root ArtifactKey) MUST NOT be affected.
- **FR-016**: GoalCompletedEvent with any model other than OrchestratorCollectorResult MUST NOT trigger cleanup.

#### Event Model Enhancements

- **FR-017**: ActionCompletedEvent MUST carry an agentModel field (Artifact.AgentModel) identifying the result or request that triggered the event.
- **FR-018**: A new ChatSessionClosedEvent MUST be defined as a GraphEvent with a sessionId field.
- **FR-019**: EmitActionCompletedResultDecorator MUST pass the AgentResult directly as the agentModel when emitting ActionCompletedEvent from the decorate(AgentResult) method.
- **FR-020**: EmitActionCompletedResultDecorator MUST implement DispatchedAgentResultDecorator to receive dispatched agent results.

### Key Entities

- **ArtifactKey**: Hierarchical ULID-based identifier (format: `ak:<ulid>/<ulid>/...`) with built-in parent/child/descendant methods. Serves as both nodeId and contextId (they are the same thing).
- **AcpSessionManager**: Holds active sessions in `ConcurrentHashMap<Any, AcpSessionContext>`. No hierarchy tracking — hierarchy is derived from ArtifactKey structure.
- **AcpSessionContext**: Represents an active ACP session including scope, transport, protocol, client, and session. Closed via `client.protocol.close()`.
- **BlackboardHistory**: Shared event log across parent workflow and sub-processes. Used to look up previous requests of the same type for contextId recycling.
- **ActionCompletedEvent**: Emitted for every agent result and request completion. Now carries agentModel for type-based cleanup matching.
- **GoalCompletedEvent**: Emitted only for OrchestratorCollectorResult. Triggers root workflow cleanup.
- **ChatSessionClosedEvent**: New event published when a session is closed, enabling downstream observability.

## Success Criteria

### Measurable Outcomes

- **SC-001**: Zero session leaks after workflow completion — all sessions closed when OrchestratorCollectorResult fires.
- **SC-002**: Dispatched agent sessions are closed within the same event processing cycle as their result emission.
- **SC-003**: Non-dispatched agents (orchestrators, collectors, dispatchers, context managers, reviewers, mergers) reuse the same contextId across route-backs, verified by contextId equality in BlackboardHistory.
- **SC-004**: Concurrent workflows maintain session isolation — completing one workflow does not affect another's sessions.
- **SC-005**: Protocol.close() errors are caught and logged without preventing cleanup of remaining sessions.

## Assumptions

- ArtifactKey = nodeId = contextId — they are the same identifier used as the session map key.
- AcpChatModel.getOrCreateSession() uses computeIfAbsent, so recycled contextIds naturally map to existing sessions.
- BlackboardHistory is shared across parent workflow and dispatched sub-processes.
- GoalCompletedEvent is only emitted for OrchestratorCollectorResult (not for dispatched agent completions).
- EmitActionCompletedResultDecorator extends DispatchedAgentResultDecorator to receive dispatched agent results.
- The existing EventBus infrastructure supports the EventListener subscription pattern used by the cleanup service.
