# Data Model: Context Manager Agent

## Core Entities

### 1. AgentModels Extensions

#### `ContextManagerRoutingRequest`
Lightweight routing request returned by agents to ask for context reconstruction. The routing action builds the full ContextManagerRequest.

```java
@Builder(toBuilder=true)
@JsonClassDescription("Lightweight request to route to the context manager.")
record ContextManagerRoutingRequest(
    @JsonPropertyDescription("Reason for requesting context reconstruction.")
    String reason,
    @JsonPropertyDescription("Type of context reconstruction to request.")
    ContextManagerRequestType type
) implements AgentRequest {}
```

#### `ContextManagerRequest`
A new sealed interface permit for `AgentRequest` in `AgentModels.java`. Exactly one `returnTo*` field must be non-null.

```java
@Builder(toBuilder=true)
@JsonClassDescription("Request for Context Manager to reconstruct context.")
record ContextManagerRequest(
    @JsonPropertyDescription("Unique context id for this request.")
    ContextId contextId,
    
    @JsonPropertyDescription("Type of context reconstruction: INTROSPECT_AGENT_CONTEXT or PROCEED.")
    ContextManagerRequestType type,
    
    @JsonPropertyDescription("Reason for context reconstruction.")
    String reason,
    
    @JsonPropertyDescription("Goal of the reconstruction.")
    String goal,
    
    @JsonPropertyDescription("Additional context to guide reconstruction.")
    String additionalContext,
    
    // Explicit return routes (destinations) - exactly one should be non-null
    @JsonPropertyDescription("Route back to orchestrator.")
    OrchestratorRequest returnToOrchestrator,

    @JsonPropertyDescription("Route back to orchestrator collector.")
    OrchestratorCollectorRequest returnToOrchestratorCollector,
    
    @JsonPropertyDescription("Route back to discovery orchestrator.")
    DiscoveryOrchestratorRequest returnToDiscoveryOrchestrator,

    @JsonPropertyDescription("Route back to discovery collector.")
    DiscoveryCollectorRequest returnToDiscoveryCollector,
    
    @JsonPropertyDescription("Route back to planning orchestrator.")
    PlanningOrchestratorRequest returnToPlanningOrchestrator,

    @JsonPropertyDescription("Route back to planning collector.")
    PlanningCollectorRequest returnToPlanningCollector,
    
    @JsonPropertyDescription("Route back to ticket orchestrator.")
    TicketOrchestratorRequest returnToTicketOrchestrator,

    @JsonPropertyDescription("Route back to ticket collector.")
    TicketCollectorRequest returnToTicketCollector,
    
    @JsonPropertyDescription("Route back to review agent.")
    ReviewRequest returnToReview,
    
    @JsonPropertyDescription("Route back to merger agent.")
    MergerRequest returnToMerger,

    @JsonPropertyDescription("Route back to planning agent.")
    PlanningAgentRequest returnToPlanningAgent,

    @JsonPropertyDescription("Route back to planning agent requests.")
    PlanningAgentRequests returnToPlanningAgentRequests,

    @JsonPropertyDescription("Route back to planning agent results.")
    PlanningAgentResults returnToPlanningAgentResults,

    @JsonPropertyDescription("Route back to ticket agent.")
    TicketAgentRequest returnToTicketAgent,

    @JsonPropertyDescription("Route back to ticket agent requests.")
    TicketAgentRequests returnToTicketAgentRequests,

    @JsonPropertyDescription("Route back to ticket agent results.")
    TicketAgentResults returnToTicketAgentResults,

    @JsonPropertyDescription("Route back to discovery agent.")
    DiscoveryAgentRequest returnToDiscoveryAgent,

    @JsonPropertyDescription("Route back to discovery agent requests.")
    DiscoveryAgentRequests returnToDiscoveryAgentRequests,

    @JsonPropertyDescription("Route back to discovery agent results.")
    DiscoveryAgentResults returnToDiscoveryAgentResults,

    @JsonPropertyDescription("Route back to context orchestrator.")
    ContextOrchestratorRequest returnToContextOrchestrator,
    
    @JsonPropertyDescription("Previous context for reruns.")
    PreviousContext previousContext
) implements AgentRequest {
    // prettyPrint implementation
}

enum ContextManagerRequestType {
    INTROSPECT_AGENT_CONTEXT,
    PROCEED
}
```

#### `ContextManagerRouting` (Response)
The result of the Context Manager agent execution.

```java
@Builder(toBuilder=true)
record ContextManagerResultRouting(
    ContextManagerInterruptRequest interruptRequest,

    // One of these will be populated with the reconstructed context
    OrchestratorRequest orchestratorRequest,
    OrchestratorCollectorRequest orchestratorCollectorRequest,
    DiscoveryOrchestratorRequest discoveryOrchestratorRequest,
    DiscoveryCollectorRequest discoveryCollectorRequest,
    PlanningOrchestratorRequest planningOrchestratorRequest,
    PlanningCollectorRequest planningCollectorRequest,
    TicketOrchestratorRequest ticketOrchestratorRequest,
    TicketCollectorRequest ticketCollectorRequest,
    ReviewRequest reviewRequest,
    MergerRequest mergerRequest,
    PlanningAgentRequest planningAgentRequest,
    PlanningAgentRequests planningAgentRequests,
    PlanningAgentResults planningAgentResults,
    TicketAgentRequest ticketAgentRequest,
    TicketAgentRequests ticketAgentRequests,
    TicketAgentResults ticketAgentResults,
    DiscoveryAgentRequest discoveryAgentRequest,
    DiscoveryAgentRequests discoveryAgentRequests,
    DiscoveryAgentResults discoveryAgentResults,
    ContextOrchestratorRequest contextOrchestratorRequest
) implements SomeOf {}
```

### 2. BlackboardHistory Extensions

#### `HistoryNote`
Annotations attached to history entries.

```java
public record HistoryNote(
    String noteId,
    Instant timestamp,
    String content,
    List<String> tags, // e.g., "exclusion", "diagnostic", "routing"
    String authorAgent
) {}
```

### 3. Blackboard History Tools Data Models

#### `HistoryTraceRequest` & `Response`
```java
public record HistoryTraceRequest(String actionNameFilter, String agentNameFilter) {}
public record HistoryTraceResponse(List<BlackboardHistory.Entry> entries) {}
```

#### `HistoryListingRequest` & `Response`
```java
public record HistoryListingRequest(int offset, int limit, Instant startTime, Instant endTime) {}
public record HistoryListingResponse(List<BlackboardHistory.Entry> entries, boolean hasMore, int nextOffset) {}
```

#### `HistorySearchRequest` & `Response`
```java
public record HistorySearchRequest(String query, int maxResults) {}
public record HistorySearchResponse(List<BlackboardHistory.Entry> entries) {}
```

#### `HistoryItemRequest` & `Response`
```java
public record HistoryItemRequest(int index, String entryId) {}
public record HistoryItemResponse(BlackboardHistory.Entry entry) {}
```

#### `NoteRequest` & `Response`
```java
public record AddNoteRequest(List<Integer> entryIndices, String content, List<String> tags) {}
public record AddNoteResponse(HistoryNote note) {}
```

### 4. Exceptions

#### `DegenerateLoopException`
Thrown by `BlackboardHistory` when a loop is detected.

```java
public class DegenerateLoopException extends RuntimeException {
    private final String actionName;
    private final Class<?> inputType;
    private final int repetitionCount;
    // ... constructors
}
```

## State Transitions

1. **Normal Flow** -> **Loop Detected** (BlackboardHistory throws exception) -> **StuckHandler** (WorkflowAgent) catches -> **ContextManagerRequest** added to `AgentProcess` -> **Context Manager Agent** runs (invoked by Embabel) -> **ContextManagerResultRouting** returns to new agent.

2. **Review/Merge** -> **ContextManagerRequest** (PROCEED) -> **Context Manager Agent** runs -> **ContextManagerResultRouting** returns to downstream agent.
