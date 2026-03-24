# Interrupt Routing Flow (Story 9)

## End-to-End: Human/Controller Interrupts an Agent

This chart covers the complete interrupt lifecycle per FR-022 through FR-032e.

```mermaid
sequenceDiagram
    participant Human as Human / Controller
    participant REST as InterruptController<br/>(REST API)
    participant EventBus as EventBus
    participant Decorators as Interrupt Decorators
    participant ACP as Running ACP Session<br/>(agent already in-flight)
    participant FPD as FilterPropertiesDecorator
    participant UIH as Unified Interrupt Handler<br/>(handleUnifiedInterrupt<br/>on ContextManager)

    Note over Human,UIH: Step 1 — Human sends interrupt with routing target (FR-028a)

    Human->>REST: POST /api/interrupts/request<br/>{type: HUMAN_REVIEW,<br/>originNodeId: "ak:ORCH/DISC_COLL",<br/>rerouteToAgentType: "PLANNING_ORCHESTRATOR",<br/>reason: "discovery missed key area, reroute to planning"}

    REST->>EventBus: Emit InterruptRequestEvent<br/>nodeId = "ak:ORCH/DISC_COLL"<br/>sourceAgentType = "DISCOVERY_COLLECTOR"<br/>rerouteToAgentType = "PLANNING_ORCHESTRATOR"<br/>interruptType = HUMAN_REVIEW

    EventBus->>FPD: storeEvent(event)<br/>key = nodeId

    Note over Human,UIH: Step 2 — Inject tokens into RUNNING session (FR-028c, FR-028d)
    Note over ACP: Session is already running —<br/>agent is mid-generation

    REST->>Decorators: Compose AddMessage payload
    Decorators->>Decorators: Generate override schema:<br/>SomeOf(InterruptRequest) via victools
    Decorators->>Decorators: Compose interrupt text:<br/>reason + context + instructions +<br/>"your structured response type has changed"<br/>+ override schema

    Decorators->>ACP: AddMessage — inject tokens<br/>into running session<br/>(NOT a new LLM call)

    Note over ACP: Agent was generating Routing/SomeOf<br/>Injected tokens steer it to return<br/>InterruptRequest instead

    Note over Human,UIH: Step 3 — Session returns InterruptRequest (FR-028c)

    ACP->>FPD: Session completes with InterruptRequest

    FPD->>FPD: resolveTargetRoute():<br/>1. Look up stored event by nodeId<br/>2. event.rerouteToAgentType = "PLANNING_ORCHESTRATOR"<br/>3. mapAgentTypeToRoute("PLANNING_ORCHESTRATOR")<br/>   → PlanningRoute.class

    FPD->>UIH: Route to unified interrupt handler<br/>with PlanningRoute as target

    Note over Human,UIH: Step 4 — Unified handler resolves (FR-032a)

    UIH->>UIH: handleUnifiedInterrupt()<br/>Routes to planning orchestrator
```

## Normal Operation: Agent Never Sees Interrupt (FR-028, FR-032)

```mermaid
sequenceDiagram
    participant FPD as FilterPropertiesDecorator
    participant LLM as Agent LLM

    Note over FPD,LLM: Normal (non-interrupt) LLM call

    FPD->>FPD: currentRequest instanceof InterruptRequest? NO
    FPD->>FPD: Apply withAnnotationFilter(SkipPropertyFilter)
    FPD->>LLM: Schema with @SkipPropertyFilter fields hidden<br/>→ interruptRequest NOT visible<br/>→ Agent cannot self-initiate interrupt

    Note over LLM: Agent sees routing fields for its normal<br/>workflow (e.g., collectorRequest, orchestratorRequest)<br/>but NEVER sees interruptRequest
```

## Subagent Interrupt Bubble-Up (FR-032e)

```mermaid
sequenceDiagram
    participant Dispatch as Dispatch Method<br/>(e.g., dispatchDiscoveryAgents)
    participant Subagent as Subagent LLM<br/>(e.g., DiscoveryDispatchSubagent)
    participant DR as DispatchRouting<br/>(e.g., DiscoveryAgentDispatchRouting)

    Note over Dispatch,DR: If controller previously interrupted<br/>the subagent, its schema was filtered<br/>to show interruptRequest

    Dispatch->>Subagent: runSubProcess()<br/>returns DiscoveryAgentRouting

    Subagent-->>Dispatch: DiscoveryAgentRouting {<br/>  interruptRequest: non-null,<br/>  agentResult: null<br/>}

    Dispatch->>Dispatch: Check: response.interruptRequest() != null

    Dispatch->>DR: Return DiscoveryAgentDispatchRouting {<br/>  @SkipPropertyFilter<br/>  agentInterruptRequest: response.interruptRequest(),<br/>  collectorRequest: null<br/>}

    Note over DR: agentInterruptRequest is @SkipPropertyFilter<br/>→ LLM never sees it<br/>→ purely internal routing propagation<br/>→ handled upstream by unified interrupt handler
```

## What Agents See: Schema Comparison

```mermaid
flowchart TD
    subgraph "Normal Operation — Single Schema"
        N0["Structured Response: Routing/SomeOf"]
        N1["✅ orchestratorRequest"]
        N2["✅ collectorRequest"]
        N3["✅ contextManagerRequest"]
        N4["❌ interruptRequest<br/>(@SkipPropertyFilter — hidden)"]
        N0 --- N1
        N0 --- N2
        N0 --- N3
        N0 --- N4
    end

    subgraph "During Interrupt — AddMessage Injection into Running Session"
        direction TB
        P0["Agent is mid-generation in ACP session<br/>Original schema: Routing/SomeOf with normal fields"]
        P1["✅ orchestratorRequest"]
        P2["✅ collectorRequest"]
        P3["❌ interruptRequest (hidden)"]
        P0 --- P1
        P0 --- P2
        P0 --- P3

        O0["AddMessage injects tokens:<br/>override schema + reason + instructions<br/>NOT a new LLM call"]
        O1["Override: SomeOf&lpar;InterruptRequest&rpar; ONLY"]
        O2["Agent steered to return InterruptRequest"]
        O0 --- O1 --- O2
    end

    subgraph "After Session Returns InterruptRequest"
        S0["FilterPropertiesDecorator reads<br/>rerouteToAgentType from stored event"]
        S1["mapAgentTypeToRoute&lpar;PLANNING_ORCHESTRATOR&rpar;<br/>→ PlanningRoute.class"]
        S2["Routes to planning orchestrator"]
        S0 --> S1 --> S2
    end

    style N4 fill:#f66,color:#fff
    style P4 fill:#f66,color:#fff
    style O0 fill:#4a4,color:#fff
    style O1 fill:#4a4,color:#fff
    style S2 fill:#36c,color:#fff
```

## Validation Edge Cases

```mermaid
flowchart TD
    REQ[POST /api/interrupts/request] --> CHECK_TYPE{type =<br/>HUMAN_REVIEW?}

    CHECK_TYPE -->|No| OTHER[PAUSE/STOP/PRUNE:<br/>rerouteToAgentType optional]
    CHECK_TYPE -->|Yes| CHECK_REROUTE{rerouteToAgentType<br/>provided?}

    CHECK_REROUTE -->|No / null| REJECT_400["400 Bad Request:<br/>HUMAN_REVIEW requires<br/>rerouteToAgentType"]

    CHECK_REROUTE -->|Yes| MAP[mapAgentTypeToRoute<br/>(rerouteToAgentType)]
    MAP --> CHECK_MAP{Returns<br/>route annotation?}

    CHECK_MAP -->|null| REJECT_INVALID["400 Bad Request:<br/>Agent type has no route<br/>(e.g., DISCOVERY_AGENT,<br/>TICKET_AGENT are leaf types<br/>— not routing targets)"]

    CHECK_MAP -->|Valid| EMIT[Emit InterruptRequestEvent<br/>with rerouteToAgentType]

    style REJECT_400 fill:#f66,color:#fff
    style REJECT_INVALID fill:#f66,color:#fff
    style EMIT fill:#4a4,color:#fff
```
