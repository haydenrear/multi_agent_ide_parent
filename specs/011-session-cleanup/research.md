# Phase 0 Research: ACP Session Lifecycle Management

## Decision: Split session management into Prevention (recycling) + Cleanup (closing)
**Rationale**: Recycling contextIds at enrichment time prevents duplicate sessions proactively via `computeIfAbsent` in AcpChatModel. Cleanup closes sessions reactively when agents complete. These two mechanisms are independent — recycling works even without cleanup, and cleanup works even without recycling.
**Alternatives considered**: Managing session lifecycle entirely at cleanup time by tracking which sessions should be closed and which should be reused. Rejected because it would require maintaining complex lifecycle state in AcpSessionManager and introduces race conditions between session creation and cleanup.

## Decision: Recycle contextIds in RequestEnrichment.resolveContextId() via BlackboardHistory lookup
**Rationale**: RequestEnrichment already enriches all requests with contextIds. Adding a lookup for previous requests of the same type in BlackboardHistory is minimally invasive. BlackboardHistory is shared across the workflow and sub-processes, so the lookup naturally finds previous contextIds regardless of routing depth.
**Alternatives considered**: (1) Tracking "active sessions per agent type" in AcpSessionManager — rejected because it duplicates state already in BlackboardHistory. (2) Recycling in AcpChatModel — rejected because AcpChatModel doesn't have visibility into agent request types.

## Decision: Use instanceof pattern matching on AgentModel types instead of AgentType enum
**Rationale**: AgentType enum is lossy — multiple concrete types map to the same enum value (e.g., DiscoveryAgentRequest and DiscoveryAgentResult both map to DISCOVERY_AGENT). Pattern matching on the concrete class ensures exact type discrimination for both recycling decisions (shouldCreateNewSession) and cleanup triggers.
**Alternatives considered**: Using AgentType enum with additional discriminators. Rejected because it adds fragile secondary matching and doesn't benefit from compile-time exhaustiveness checking.

## Decision: No hierarchy tracking in AcpSessionManager — use ArtifactKey.isDescendantOf()
**Rationale**: ArtifactKey uses a hierarchical string format (`ak:<ulid>/<ulid>/...`) where descendant detection is a simple string prefix check. Adding a SessionHierarchyNode or parent/child maps would duplicate information already encoded in the key structure.
**Alternatives considered**: Maintaining a tree of SessionHierarchyNode objects in AcpSessionManager with parent pointers and child lists. Rejected as unnecessary complexity given ArtifactKey's built-in hierarchy.

## Decision: Cleanup via EventListener (AcpSessionCleanupService) rather than decorator
**Rationale**: Session cleanup is a cross-cutting infrastructure concern, not a per-agent-action concern. Using an EventListener decouples cleanup from the decorator chain and allows it to react to events from any source. The cleanup service subscribes to ActionCompletedEvent and GoalCompletedEvent and acts on them.
**Alternatives considered**: Adding cleanup logic to EmitActionCompletedResultDecorator. Rejected because it would mix event emission and resource management, violating single responsibility.

## Decision: EmitActionCompletedResultDecorator extends DispatchedAgentResultDecorator
**Rationale**: Rather than deconstructing routing types to extract the underlying model (which requires handling multi-field nullable routing records), the decorator implements DispatchedAgentResultDecorator and receives agent results directly. The agentModel on ActionCompletedEvent is simply the result object itself — no extraction logic needed.
**Alternatives considered**: Keeping the original approach with extractModelFromRouting() that examines each routing type's nullable fields. Rejected because it was complex, error-prone, and unnecessary once the decorator receives results directly.

## Decision: ActionCompletedEvent carries agentModel field (not separate cleanup events)
**Rationale**: Adding the model directly to ActionCompletedEvent avoids introducing new event types for cleanup triggers. The cleanup service simply pattern-matches on the agentModel field to determine cleanup action. This keeps the event hierarchy simple and the cleanup logic self-contained.
**Alternatives considered**: Creating separate DispatchedAgentCompletedEvent and WorkflowCompletedEvent types. Rejected because the existing event types (ActionCompletedEvent, GoalCompletedEvent) are sufficient when enriched with the model field.

## Decision: Close sessions via client.protocol.close() (not transport.close() separately)
**Rationale**: Protocol.close() handles the full shutdown sequence including transport teardown. Calling it ensures clean disconnection from the ACP process. The scope cancellation is handled internally by the protocol shutdown.
**Alternatives considered**: Separately closing transport, cancelling scope, and disconnecting client. Rejected because protocol.close() already orchestrates the full teardown.
