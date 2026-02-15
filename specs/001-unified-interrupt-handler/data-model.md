# Data Model: Unified Interrupt Handler

**Feature**: 001-unified-interrupt-handler  
**Date**: 2026-02-15

## Entities

### 1. InterruptRequest (Sealed Interface) — UNCHANGED

The existing `InterruptRequest` sealed interface and all its per-agent subtypes are **kept as-is**. No new unified record is created. Each subtype retains its agent-specific structured fields.

The unified handler accepts the sealed interface `InterruptRequest` directly:
```java
@Action(canRerun = true, cost = 1)
handleUnifiedInterrupt(InterruptRequest request, OperationContext context) → InterruptRouting
```

All per-agent subtypes (e.g., `OrchestratorInterruptRequest`, `DiscoveryOrchestratorInterruptRequest`) route to this single handler. The handler uses `instanceof` pattern matching on the concrete subtype to determine the originating agent.

**No changes to InterruptRequest subtypes or the sealed interface permits list.**

**Key benefit**: Each agent's routing schema stays clean — the LLM sees only the interrupt fields relevant to that agent (e.g., `OrchestratorRouting.interruptRequest` remains typed as `OrchestratorInterruptRequest` with `phase`, `goal`, `workflowContext`). No polymorphic schema issues.

### 2. InterruptRouting — NEW

A routing record that Embabel uses to determine the next @Action after interrupt resolution. Contains nullable fields for **all node types that can route to the interrupt handler** — orchestrator-level, collectors, and dispatch nodes. Each field is annotated with a unique route marker annotation. FilterPropertiesDecorator applies `withAnnotationFilter(...)` for all annotations **except** the one matching the originating node (set difference), so only the valid return route and its `$defs` remain in the schema.

#### 2a. Orchestrator-level route fields

| Field | Type | Marker Annotation | Description |
|-------|------|-------------------|-------------|
| orchestratorRequest | OrchestratorRequest | @OrchestratorRoute | Resume at orchestrator |
| discoveryOrchestratorRequest | DiscoveryOrchestratorRequest | @DiscoveryRoute | Resume at discovery orchestrator |
| planningOrchestratorRequest | PlanningOrchestratorRequest | @PlanningRoute | Resume at planning orchestrator |
| ticketOrchestratorRequest | TicketOrchestratorRequest | @TicketRoute | Resume at ticket orchestrator |
| reviewRequest | ReviewRequest | @ReviewRoute | Resume at review |
| mergerRequest | MergerRequest | @MergerRoute | Resume at merger |
| contextManagerRequest | ContextManagerRoutingRequest | @ContextManagerRoute | Resume at context manager |

#### 2b. Collector route fields

| Field | Type | Marker Annotation | Description |
|-------|------|-------------------|-------------|
| orchestratorCollectorRequest | OrchestratorCollectorRequest | @OrchestratorCollectorRoute | Resume at orchestrator collector |
| discoveryCollectorRequest | DiscoveryCollectorRequest | @DiscoveryCollectorRoute | Resume at discovery collector |
| planningCollectorRequest | PlanningCollectorRequest | @PlanningCollectorRoute | Resume at planning collector |
| ticketCollectorRequest | TicketCollectorRequest | @TicketCollectorRoute | Resume at ticket collector |

#### 2c. Dispatch node route fields

These are the *dispatch nodes* (orchestrator-level nodes that produce agent request batches), not the dispatched agents themselves. Dispatched agents are skipped for interrupt handling in this scope.

| Field | Type | Marker Annotation | Description |
|-------|------|-------------------|-------------|
| discoveryAgentRequests | DiscoveryAgentRequests | @DiscoveryDispatchRoute | Resume at discovery dispatch node |
| planningAgentRequests | PlanningAgentRequests | @PlanningDispatchRoute | Resume at planning dispatch node |
| ticketAgentRequests | TicketAgentRequests | @TicketDispatchRoute | Resume at ticket dispatch node |

**Total fields**: 14

**Implements**: `Routing` (sealed interface extending `SomeOf`)

**Constraints**:
- Exactly one field must be non-null after interrupt resolution
- FilterPropertiesDecorator applies `withAnnotationFilter(X.class)` for each route annotation X that does **not** match the originating node (set difference). This removes both the property and its `$defs` from the schema.
- Must be added to the `Routing` sealed interface permits list

### 3. Route Marker Annotations — NEW (14 annotations)

One `@Retention(RUNTIME) @Target({FIELD, RECORD_COMPONENT})` annotation per routing field, following the same pattern as `@SkipPropertyFilter`. The annotation marks what the field *is*; `withAnnotationFilter(X.class)` removes fields marked with `X`.

#### 3a. Orchestrator-level annotations

| Annotation | Applied to Field | Filtered out when origin is NOT |
|-----------|-----------------|-------------------------------|
| @OrchestratorRoute | orchestratorRequest | Orchestrator |
| @DiscoveryRoute | discoveryOrchestratorRequest | DiscoveryOrchestrator |
| @PlanningRoute | planningOrchestratorRequest | PlanningOrchestrator |
| @TicketRoute | ticketOrchestratorRequest | TicketOrchestrator |
| @ReviewRoute | reviewRequest | Review |
| @MergerRoute | mergerRequest | Merger |
| @ContextManagerRoute | contextManagerRequest | ContextManager |

#### 3b. Collector annotations

| Annotation | Applied to Field | Filtered out when origin is NOT |
|-----------|-----------------|-------------------------------|
| @OrchestratorCollectorRoute | orchestratorCollectorRequest | OrchestratorCollector |
| @DiscoveryCollectorRoute | discoveryCollectorRequest | DiscoveryCollector |
| @PlanningCollectorRoute | planningCollectorRequest | PlanningCollector |
| @TicketCollectorRoute | ticketCollectorRequest | TicketCollector |

#### 3c. Dispatch node annotations

| Annotation | Applied to Field | Filtered out when origin is NOT |
|-----------|-----------------|-------------------------------|
| @DiscoveryDispatchRoute | discoveryAgentRequests | DiscoveryDispatch |
| @PlanningDispatchRoute | planningAgentRequests | PlanningDispatch |
| @TicketDispatchRoute | ticketAgentRequests | TicketDispatch |

**Location**: `multi_agent_ide_lib` alongside `SkipPropertyFilter.java`

### 4. InterruptRequestEvent — NEW

Event published to EventBus by external actors (supervisor, human) to trigger an interrupt and optionally re-route the workflow. The event handler sends a message to the running agent asking it to produce an InterruptRequest (the agent's schema already includes `interruptRequest` as a routing option — no schema modification needed). The event is then stored so `FilterPropertiesDecorator` can detect it during interrupt resolution and re-route to the event's target destination instead of back to the originating agent.

| Field | Type | Description |
|-------|------|-------------|
| eventId | String | Unique event identifier |
| timestamp | Instant | When the event was created |
| nodeId | String | Target node ID in the computation graph |
| sourceAgentType | AgentType | The agent currently running that will be asked to interrupt |
| rerouteToAgentType | AgentType | Where to re-route after interrupt resolution (nullable — if absent, falls back to normal set-difference filtering, routing back to source agent) |
| interruptType | Events.InterruptType | Type of interrupt |
| reason | String | Natural language reason for the interrupt |
| choices | List\<StructuredChoice\> | Optional structured choices |
| confirmationItems | List\<ConfirmationItem\> | Optional confirmation items |
| contextForDecision | String | Optional context for the decision |

**Implements**: `Events.GraphEvent`, `Events.AgentEvent`

**Relationships**:
- Published via `EventBus.publish()`
- Consumed by event handler which calls `AgentRunner.addMessageToAgent()` to send message to running agent (asking it to produce an InterruptRequest)
- Event is stored in `FilterPropertiesDecorator` (keyed by nodeId/sessionId) for later lookup
- Agent produces its per-agent InterruptRequest subtype naturally, which routes to unified handler
- `FilterPropertiesDecorator` detects the stored event when processing InterruptRouting output, and filters to show the event's `rerouteToAgentType` route instead of the originating agent's route

### 5. AgentType to Routing Field Mapping — CONFIGURATION

Maps each agent/node type to its corresponding routing field name, template name, history lookup class, and route annotation.

| AgentType | Routing Field | Template | History Lookup Class | Route Annotation |
|-----------|--------------|----------|---------------------|-----------------|
| ORCHESTRATOR | orchestratorRequest | workflow/orchestrator | OrchestratorRequest.class | @OrchestratorRoute |
| DISCOVERY_ORCHESTRATOR | discoveryOrchestratorRequest | workflow/discovery_orchestrator | DiscoveryOrchestratorRequest.class | @DiscoveryRoute |
| PLANNING_ORCHESTRATOR | planningOrchestratorRequest | workflow/planning_orchestrator | PlanningOrchestratorRequest.class | @PlanningRoute |
| TICKET_ORCHESTRATOR | ticketOrchestratorRequest | workflow/ticket_orchestrator | TicketOrchestratorRequest.class | @TicketRoute |
| REVIEW_AGENT | reviewRequest | workflow/review | ReviewRequest.class | @ReviewRoute |
| MERGER_AGENT | mergerRequest | workflow/merger | MergerRequest.class | @MergerRoute |
| CONTEXT_MANAGER | contextManagerRequest | workflow/context_manager | ContextManagerRequest.class | @ContextManagerRoute |
| ORCHESTRATOR_COLLECTOR | orchestratorCollectorRequest | workflow/orchestrator_collector | OrchestratorCollectorRequest.class | @OrchestratorCollectorRoute |
| DISCOVERY_COLLECTOR | discoveryCollectorRequest | workflow/discovery_collector | DiscoveryCollectorRequest.class | @DiscoveryCollectorRoute |
| PLANNING_COLLECTOR | planningCollectorRequest | workflow/planning_collector | PlanningCollectorRequest.class | @PlanningCollectorRoute |
| TICKET_COLLECTOR | ticketCollectorRequest | workflow/ticket_collector | TicketCollectorRequest.class | @TicketCollectorRoute |
| DISCOVERY_DISPATCH | discoveryAgentRequests | workflow/discovery_dispatch | DiscoveryAgentRequests.class | @DiscoveryDispatchRoute |
| PLANNING_DISPATCH | planningAgentRequests | workflow/planning_dispatch | PlanningAgentRequests.class | @PlanningDispatchRoute |
| TICKET_DISPATCH | ticketAgentRequests | workflow/ticket_dispatch | TicketAgentRequests.class | @TicketDispatchRoute |

This mapping can be implemented as:
- An enum with fields
- A static Map in the unified handler
- A registry bean

### 6. Changes to Existing Routing Records — NONE

Since per-agent InterruptRequest subtypes are kept as-is, **no changes are needed to existing Routing records**. Each Routing record continues to reference its own typed InterruptRequest subtype:

| Routing Record | InterruptRequest Field | Status |
|---------------|----------------------|--------|
| OrchestratorRouting | OrchestratorInterruptRequest | UNCHANGED |
| OrchestratorCollectorRouting | OrchestratorCollectorInterruptRequest | UNCHANGED |
| DiscoveryOrchestratorRouting | DiscoveryOrchestratorInterruptRequest | UNCHANGED |
| DiscoveryCollectorRouting | DiscoveryCollectorInterruptRequest | UNCHANGED |
| PlanningOrchestratorRouting | PlanningOrchestratorInterruptRequest | UNCHANGED |
| PlanningCollectorRouting | PlanningCollectorInterruptRequest | UNCHANGED |
| TicketOrchestratorRouting | TicketOrchestratorInterruptRequest | UNCHANGED |
| TicketCollectorRouting | TicketCollectorInterruptRequest | UNCHANGED |
| ReviewRouting | ReviewInterruptRequest | UNCHANGED |
| MergerRouting | MergerInterruptRequest | UNCHANGED |
| ContextManagerResultRouting | ContextManagerInterruptRequest | UNCHANGED |
| DiscoveryAgentDispatchRouting | DiscoveryAgentDispatchInterruptRequest | UNCHANGED (out of scope) |
| PlanningAgentDispatchRouting | PlanningAgentDispatchInterruptRequest | UNCHANGED (out of scope) |
| TicketAgentDispatchRouting | TicketAgentDispatchInterruptRequest | UNCHANGED (out of scope) |
| DiscoveryAgentRouting | DiscoveryAgentInterruptRequest | UNCHANGED (out of scope) |
| PlanningAgentRouting | PlanningAgentInterruptRequest | UNCHANGED (out of scope) |
| TicketAgentRouting | TicketAgentInterruptRequest | UNCHANGED (out of scope) |

The key change is that all these per-agent InterruptRequest subtypes now route to the **same unified handler** (`handleUnifiedInterrupt(InterruptRequest, OperationContext)`) instead of per-agent handler methods. Embabel's type system resolves the sealed interface to the @Action.

## State Transitions

```
Agent Running
    │
    ├──[Agent self-interrupts via Routing]──→ Per-agent InterruptRequest subtype on blackboard
    │                                              │
    │                                              ▼
    │                                      Unified Interrupt Handler
    │                                              │
    │                                              ├──[FilterPropertiesDecorator filters schema]
    │                                              │
    │                                              ├──[InterruptService resolves]
    │                                              │      ├── HUMAN_REVIEW → PermissionGate blocks → human feedback
    │                                              │      ├── AGENT_REVIEW → ReviewAgent generates feedback
    │                                              │      └── PAUSE/STOP/BRANCH/PRUNE → handled per type
    │                                              │
    │                                              ▼
    │                                      InterruptRouting (one field populated)
    │                                              │
    │                                              ▼
    │                                      BlackboardRoutingPlanner extracts non-null field
    │                                              │
    │                                              ▼
    │                                      Embabel dispatches to matching @Action
    │
    ├──[External InterruptRequestEvent]──→ InterruptRequestEvent published via EventBus
                                                │
                                                ▼
                                         Event handler:
                                           1. Calls addMessageToAgent() — asks agent to produce InterruptRequest
                                           2. Stores event in FilterPropertiesDecorator (keyed by nodeId)
                                                │
                                                ▼
                                         Agent produces per-agent InterruptRequest naturally
                                           (interruptRequest is already a routing option — no schema change)
                                                │
                                                ▼
                                         Unified Interrupt Handler
                                                │
                                                ├──[FilterPropertiesDecorator detects stored event]
                                                │   Filters InterruptRouting to event's rerouteToAgentType
                                                │   (instead of routing back to originating agent)
                                                │   Consumes/clears stored event
                                                │
                                                ▼
                                         InterruptRouting (target route field populated)
                                                │
                                                ▼
                                         Workflow continues at target agent
```

## Entities Removed

**No InterruptRequest subtypes are removed.** All per-agent InterruptRequest subtypes are kept as-is. The only removal is the per-agent **handler methods** in `AgentInterfaces.java` (e.g., `handleOrchestratorInterrupt`, `handleDiscoveryInterrupt`, etc.) — these are replaced by the single `handleUnifiedInterrupt` method.
