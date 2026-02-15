# Contract: Unified Interrupt Routing

**Feature**: 001-unified-interrupt-handler  
**Date**: 2026-02-15

## Unified Interrupt Handler Action

### Input: InterruptRequest (Sealed Interface)

The unified `@Action` method accepts the `InterruptRequest` sealed interface. All per-agent subtypes (e.g., `OrchestratorInterruptRequest`, `DiscoveryOrchestratorInterruptRequest`) route to this single handler. The handler uses `instanceof` pattern matching on the concrete subtype to determine the originating agent.

```
@Action(canRerun = true, cost = 1)
handleUnifiedInterrupt(InterruptRequest request, OperationContext context)
  → InterruptRouting
```

### Output: InterruptRouting

A `Routing` record with 14 nullable fields — one per node type that can route to the interrupt handler. Exactly one field is non-null after resolution.

**Schema (before filtering — full 14-field schema)**:
```json
{
  "type": "object",
  "properties": {
    "orchestratorRequest": { "$ref": "#/$defs/OrchestratorRequest" },
    "discoveryOrchestratorRequest": { "$ref": "#/$defs/DiscoveryOrchestratorRequest" },
    "planningOrchestratorRequest": { "$ref": "#/$defs/PlanningOrchestratorRequest" },
    "ticketOrchestratorRequest": { "$ref": "#/$defs/TicketOrchestratorRequest" },
    "reviewRequest": { "$ref": "#/$defs/ReviewRequest" },
    "mergerRequest": { "$ref": "#/$defs/MergerRequest" },
    "contextManagerRequest": { "$ref": "#/$defs/ContextManagerRoutingRequest" },
    "orchestratorCollectorRequest": { "$ref": "#/$defs/OrchestratorCollectorRequest" },
    "discoveryCollectorRequest": { "$ref": "#/$defs/DiscoveryCollectorRequest" },
    "planningCollectorRequest": { "$ref": "#/$defs/PlanningCollectorRequest" },
    "ticketCollectorRequest": { "$ref": "#/$defs/TicketCollectorRequest" },
    "discoveryAgentRequests": { "$ref": "#/$defs/DiscoveryAgentRequests" },
    "planningAgentRequests": { "$ref": "#/$defs/PlanningAgentRequests" },
    "ticketAgentRequests": { "$ref": "#/$defs/TicketAgentRequests" }
  }
}
```

**Schema (after filtering for Orchestrator origin — 13 annotations applied, 1 remains)**:
```json
{
  "type": "object",
  "properties": {
    "orchestratorRequest": { "$ref": "#/$defs/OrchestratorRequest" }
  },
  "$defs": {
    "OrchestratorRequest": { ... }
  }
}
```

## FilterPropertiesDecorator Contract

### Input: LlmCallContext<T>

The decorator receives the full LLM call context before the LLM is invoked.

### Behavior — Interrupt Resolution (InterruptRouting):

1. Check if `T` is `InterruptRouting.class`
2. If yes, inspect `promptContext.previousRequest()` to determine originating agent/node type
3. Determine which route annotation corresponds to the originating node (e.g., Orchestrator → `OrchestratorRoute.class`)
4. Collect all 14 route annotations into a set
5. Remove the originating node's annotation from the set (set difference)
6. For each remaining annotation, chain `withAnnotationFilter(X.class)` on `templateOperations`
7. Return the modified `LlmCallContext`

This removes both the properties and their `$defs` for all non-matching routes.

### Behavior — Event-Driven Re-Route (InterruptRequestEvent):

When processing `InterruptRouting`, **before** applying the normal set-difference logic:

1. Check if a matching `InterruptRequestEvent` exists for this node/session ID (stored by the event handler when the event was received)
2. If a matching event exists, read the event's `targetRouteAnnotation` (the route annotation for the destination agent)
3. Instead of the normal set-difference (which routes back to the originating agent), apply `withAnnotationFilter(...)` for all 14 route annotations **except** the event's target annotation
4. This filters the InterruptRouting schema to show only the event's target route — re-routing the workflow to wherever the supervisor/human directed
5. Clear/consume the stored event after application

If no matching event exists, fall through to the normal set-difference behavior (Behavior — Interrupt Resolution above).

**Note**: The running agent's schema is never modified. The event handler only sends a message via `addMessageToAgent()` asking the agent to produce an InterruptRequest. The re-routing decision is made entirely within FilterPropertiesDecorator when the interrupt handler's InterruptRouting output is being prepared.

### Example (Orchestrator origin, interrupt resolution):

```java
// All 14 route annotations
Set<Class<? extends Annotation>> allRoutes = Set.of(
    OrchestratorRoute.class, DiscoveryRoute.class, PlanningRoute.class,
    TicketRoute.class, ReviewRoute.class, MergerRoute.class, ContextManagerRoute.class,
    OrchestratorCollectorRoute.class, DiscoveryCollectorRoute.class,
    PlanningCollectorRoute.class, TicketCollectorRoute.class,
    DiscoveryDispatchRoute.class, PlanningDispatchRoute.class, TicketDispatchRoute.class
);

// Remove the originating node's annotation
Set<Class<? extends Annotation>> toFilter = allRoutes - Set.of(OrchestratorRoute.class);

// Apply filters — removes all other route fields and their $defs
var ops = templateOperations;
for (var annotation : toFilter) {
    ops = ops.withAnnotationFilter(annotation);
}
```

### Output: LlmCallContext<T> with filtered templateOperations

## InterruptRequestEvent Contract

### Event Structure:

```json
{
  "eventId": "uuid",
  "timestamp": "2026-02-15T12:00:00Z",
  "nodeId": "target-node-id",
  "sourceAgentType": "ORCHESTRATOR",
  "rerouteToAgentType": "DISCOVERY_ORCHESTRATOR",
  "interruptType": "HUMAN_REVIEW",
  "reason": "Need clarification on discovery scope",
  "choices": [
    { "label": "Expand scope", "value": "expand", "description": "Include all modules" },
    { "label": "Keep narrow", "value": "narrow", "description": "Only core modules" }
  ],
  "confirmationItems": [],
  "contextForDecision": "Discovery found 15 modules but only 3 were specified in the goal."
}
```

- `sourceAgentType`: The agent currently running that will be asked to produce an InterruptRequest
- `rerouteToAgentType`: The agent to re-route to after interrupt resolution. If null/absent, falls back to normal set-difference filtering (routes back to source agent)

### Flow:

1. **Message to running agent**: Event handler calls `AgentRunner.addMessageToAgent(reason, nodeId)` which sends via `llmOutputChannel.send(new MessageOutputChannelEvent(nodeId, new UserMessage(reason)))`. This asks the running agent to produce an InterruptRequest. The agent's routing schema already includes `interruptRequest` as an option — no schema modification is needed on the running agent.

2. **Store event for later lookup**: Event handler stores the `InterruptRequestEvent` keyed by node/session ID so that `FilterPropertiesDecorator` can find it later.

3. **Agent produces InterruptRequest naturally**: The agent, having received the message, chooses to produce an InterruptRequest on its next response. This routes to `handleUnifiedInterrupt`.

4. **Re-route in FilterPropertiesDecorator**: When the interrupt handler prepares its `InterruptRouting` output, `FilterPropertiesDecorator` detects the stored event by matching node/session ID. Instead of filtering the schema to route back to the originating agent (normal behavior), the decorator filters to show only the event's target re-route destination. The event is consumed/cleared after use.

### Publishing:
- Via `EventBus.publish(new Events.InterruptRequestEvent(...))`
- Via REST endpoint (facade that publishes the event)

### Consumption:
- Event handler receives `InterruptRequestEvent`
- Calls `addMessageToAgent()` to ask the running agent to interrupt
- Stores the event for `FilterPropertiesDecorator` lookup
- Agent produces `InterruptRequest` naturally (no schema modification)
- `InterruptRequest` routes to `handleUnifiedInterrupt`
- `FilterPropertiesDecorator` detects stored event → filters InterruptRouting to event's target route
- Workflow continues at the target agent

## Routing Field to Agent/Node Type Mapping

### Orchestrator-level

| Routing Field Name | AgentType Enum | Last Request Class | Route Annotation |
|-------------------|----------------|-------------------|-----------------|
| orchestratorRequest | ORCHESTRATOR | OrchestratorRequest | @OrchestratorRoute |
| discoveryOrchestratorRequest | DISCOVERY_ORCHESTRATOR | DiscoveryOrchestratorRequest | @DiscoveryRoute |
| planningOrchestratorRequest | PLANNING_ORCHESTRATOR | PlanningOrchestratorRequest | @PlanningRoute |
| ticketOrchestratorRequest | TICKET_ORCHESTRATOR | TicketOrchestratorRequest | @TicketRoute |
| reviewRequest | REVIEW_AGENT | ReviewRequest | @ReviewRoute |
| mergerRequest | MERGER_AGENT | MergerRequest | @MergerRoute |
| contextManagerRequest | CONTEXT_MANAGER | ContextManagerRequest | @ContextManagerRoute |

### Collectors

| Routing Field Name | AgentType Enum | Last Request Class | Route Annotation |
|-------------------|----------------|-------------------|-----------------|
| orchestratorCollectorRequest | ORCHESTRATOR_COLLECTOR | OrchestratorCollectorRequest | @OrchestratorCollectorRoute |
| discoveryCollectorRequest | DISCOVERY_COLLECTOR | DiscoveryCollectorRequest | @DiscoveryCollectorRoute |
| planningCollectorRequest | PLANNING_COLLECTOR | PlanningCollectorRequest | @PlanningCollectorRoute |
| ticketCollectorRequest | TICKET_COLLECTOR | TicketCollectorRequest | @TicketCollectorRoute |

### Dispatch Nodes

| Routing Field Name | AgentType Enum | Last Request Class | Route Annotation |
|-------------------|----------------|-------------------|-----------------|
| discoveryAgentRequests | DISCOVERY_DISPATCH | DiscoveryAgentRequests | @DiscoveryDispatchRoute |
| planningAgentRequests | PLANNING_DISPATCH | PlanningAgentRequests | @PlanningDispatchRoute |
| ticketAgentRequests | TICKET_DISPATCH | TicketAgentRequests | @TicketDispatchRoute |

## Existing Routing Records — UNCHANGED

Per-agent InterruptRequest subtypes are kept as-is on all existing Routing records. No field type changes are needed. Each Routing record continues to reference its own typed InterruptRequest subtype (e.g., `OrchestratorRouting` still has `OrchestratorInterruptRequest interruptRequest`).

The change is that all these per-agent subtypes now route to the **same unified handler** via Embabel's sealed interface resolution, instead of per-agent handler methods.

## BlackboardRoutingPlanner Compatibility

BlackboardRoutingPlanner handles InterruptRouting automatically:
1. InterruptRouting implements `Routing` → planner detects it in `lastResult instanceof AgentModels.Routing`
2. `extractFirstNonNullComponent()` returns the single non-null field (e.g., `OrchestratorRequest`)
3. Planner matches it against `@Action` input bindings via `satisfiesType()`
4. No structural changes needed — the existing generic routing logic handles InterruptRouting
