# Research: Unified Interrupt Handler

**Feature**: 001-unified-interrupt-handler  
**Date**: 2026-02-15

## R1: How do current per-agent interrupt handlers work?

**Decision**: All 10+ interrupt handlers follow an identical pattern that can be factored into a single method.

**Rationale**: Every interrupt handler in AgentInterfaces.java follows this exact sequence:
1. Look up the last request of the interrupted agent's type from BlackboardHistory
2. If not found, throw DegenerateLoopException
3. Decorate the interrupt request via `decorateRequest()`
4. Build a template model map with agent-specific fields (goal, phase, ticketDetails, etc.)
5. Build PromptContext via `buildPromptContext()` with the agent's template name
6. Call `interruptService.handleInterrupt()` with the agent's routing class
7. Decorate the result via `decorateRouting()` or `decorateRequestResult()`

The only differences between handlers are:
- The `Class<T>` of the last request to look up (e.g., `OrchestratorRequest.class` vs `DiscoveryOrchestratorRequest.class`)
- The template name (e.g., `TEMPLATE_WORKFLOW_ORCHESTRATOR` vs `TEMPLATE_WORKFLOW_DISCOVERY_ORCHESTRATOR`)
- The template model variables (e.g., `goal + phase` vs `goal + subdomainFocus`)
- The routing/result class (e.g., `OrchestratorRequest.class` vs `DiscoveryOrchestratorRequest.class`)
- The action/method name constants for decorator context

**Alternatives considered**:
- Keep per-agent handlers but extract shared logic into a helper: Rejected because it doesn't solve the InterruptRequest type proliferation problem and doesn't enable the InterruptRouting with dynamic filtering.
- Use a strategy pattern with per-agent strategy classes: Over-engineering; the differences between handlers are data (class references, template names), not logic.

## R2: How does Embabel's property filtering work for structured output?

**Decision**: Use `ObjectCreator.withAnnotationFilter(Class<? extends Annotation>)` via the `LlmCallContext.templateOperations` in FilterPropertiesDecorator. This is a custom extension that filters properties by annotation and also removes `$defs` from the schema.

**Rationale**: The project has a custom `withAnnotationFilter(AnnotationClass.class)` method on `ObjectCreator` (extending Embabel's `PromptRunner.Creating`). This method:
- Filters out fields annotated with the given annotation from the JSON schema sent to the LLM
- Also removes `$defs` entries for the filtered types, keeping the schema clean
- Can be chained: `creating(Foo.class).withAnnotationFilter(SkipA.class).withAnnotationFilter(SkipB.class)`

The approach for InterruptRouting uses **per-field marker annotations** and **set difference**:
1. Each field on InterruptRouting is marked with a descriptive annotation naming the route (e.g., `@OrchestratorRoute`, `@DiscoveryRoute`, etc.)
2. `withAnnotationFilter(SomeRoute.class)` filters out fields annotated with that annotation (removing the field and its `$defs`)
3. FilterPropertiesDecorator determines the originating agent from PromptContext
4. It applies `withAnnotationFilter(...)` for **all route annotations except** the one corresponding to the originating agent
5. Result: only the originating agent's field remains visible in the schema (including its `$defs`)

Example: If the orchestrator was interrupted, the decorator applies:
```java
templateOperations
    .withAnnotationFilter(DiscoveryRoute.class)
    .withAnnotationFilter(PlanningRoute.class)
    .withAnnotationFilter(TicketRoute.class)
    .withAnnotationFilter(ReviewRoute.class)
    .withAnnotationFilter(MergerRoute.class)
    .withAnnotationFilter(ContextManagerRoute.class)
    .withAnnotationFilter(OrchestratorCollectorRoute.class)
    .withAnnotationFilter(DiscoveryCollectorRoute.class)
    .withAnnotationFilter(PlanningCollectorRoute.class)
    .withAnnotationFilter(TicketCollectorRoute.class)
    .withAnnotationFilter(DiscoveryDispatchRoute.class)
    .withAnnotationFilter(PlanningDispatchRoute.class)
    .withAnnotationFilter(TicketDispatchRoute.class)
// OrchestratorRoute is NOT applied → orchestratorRequest stays visible
```

The existing `@SkipPropertyFilter` annotation demonstrates the filtering mechanism (used on fields like `contextId`, `worktreeContext` across many request/result records).

**Alternatives considered**:
- `withPropertyFilter(Predicate<String>)`: Works for filtering by property name but does NOT remove `$defs` from the schema. The annotation-based approach is superior because it cleans up the full schema.
- Custom Jackson serialization filter: Would work but doesn't integrate with Embabel's schema generation.
- Multiple InterruptRouting classes (one per agent): Defeats the purpose of unification.
- Naming annotations `@SkipXRoute`: Confusing — the annotation marks what the field *is*, not what to skip. `withAnnotationFilter` does the skipping.

## R3: How should InterruptRequest types be handled?

**Decision**: Keep the existing per-agent InterruptRequest subtypes as-is. The unified handler accepts the sealed `InterruptRequest` interface directly, so any subtype routes to the same handler.

**Rationale**: Each per-agent InterruptRequest subtype (e.g., `OrchestratorInterruptRequest`, `DiscoveryOrchestratorInterruptRequest`) has agent-specific structured fields that are meaningful for that agent's context. Collapsing them into a single record would either:
- Lose structured typing (replacing with a generic `Map<String, String>`)
- Create a polymorphic schema problem where the LLM sees fields for all agents

By keeping the per-agent subtypes:
1. **Each agent's routing schema stays clean** — the LLM sees only the interrupt fields relevant to that agent (e.g., `OrchestratorRouting.interruptRequest` is typed as `OrchestratorInterruptRequest` with `phase`, `goal`, `workflowContext`)
2. **The unified handler accepts `InterruptRequest` (sealed interface)** — all subtypes route to `handleUnifiedInterrupt(InterruptRequest request, ...)` via Embabel's type system
3. **No schema changes to existing Routing records** — `OrchestratorRouting` still has `OrchestratorInterruptRequest interruptRequest`, not a generic type
4. **Agent-specific context is preserved** — each subtype carries structured fields meaningful to that agent, which the interrupt resolution can use

The unified handler determines the originating agent from the concrete subtype class of the `InterruptRequest` it receives (e.g., `request instanceof OrchestratorInterruptRequest` → Orchestrator origin).

**Alternatives considered**:
- Single unified InterruptRequest record with `Map<String, String> agentSpecificContext`: Loses structured typing, forces the LLM to produce a polymorphic schema, and makes agent-specific fields opaque.
- Single unified InterruptRequest with all agent fields as top-level @SkipPropertyFilter fields: Too many fields; the InterruptRequest would become unwieldy.
- Use inheritance with abstract base class: Records don't support inheritance. The sealed interface approach (already in place) is the correct Java pattern.

## R4: How should InterruptRouting be structured?

**Decision**: Create an `InterruptRouting` record implementing `Routing` (the sealed `SomeOf` interface) with nullable fields for **every node type that can route to it** — not just orchestrator-level agents, but also dispatch nodes and collector nodes.

**Rationale**: The user clarified that while dispatched agent *handlers* are skipped (TicketDispatchSubagent, PlanningDispatchSubagent, DiscoveryDispatchSubagent won't have interrupt handling logic), the dispatch *nodes* (PlanningAgentRequests, DiscoveryAgentRequests, TicketAgentRequests) and *collector* nodes (PlanningCollectorRequest, DiscoveryCollectorRequest, TicketCollectorRequest, OrchestratorCollectorRequest) still need to be able to route to the unified interrupt handler. This means:

1. **InterruptRouting must have return-route fields for all node types** that can trigger an interrupt
2. Each existing Routing record that currently has a per-agent InterruptRequest field must be updated to reference the unified InterruptRequest instead
3. The InterruptRouting fields correspond to all the request types that can resume after interrupt resolution

Full field list:
```
InterruptRouting(
    // Orchestrator-level routes
    OrchestratorRequest orchestratorRequest,                         @OrchestratorRoute
    DiscoveryOrchestratorRequest discoveryOrchestratorRequest,       @DiscoveryRoute
    PlanningOrchestratorRequest planningOrchestratorRequest,         @PlanningRoute
    TicketOrchestratorRequest ticketOrchestratorRequest,             @TicketRoute
    ReviewRequest reviewRequest,                                     @ReviewRoute
    MergerRequest mergerRequest,                                     @MergerRoute
    ContextManagerRoutingRequest contextManagerRequest,              @ContextManagerRoute
    // Collector routes
    OrchestratorCollectorRequest orchestratorCollectorRequest,       @OrchestratorCollectorRoute
    DiscoveryCollectorRequest discoveryCollectorRequest,             @DiscoveryCollectorRoute
    PlanningCollectorRequest planningCollectorRequest,               @PlanningCollectorRoute
    TicketCollectorRequest ticketCollectorRequest,                   @TicketCollectorRoute
    // Dispatch node routes (the nodes, not the dispatch agents themselves)
    DiscoveryAgentRequests discoveryAgentRequests,                   @DiscoveryDispatchRoute
    PlanningAgentRequests planningAgentRequests,                     @PlanningDispatchRoute
    TicketAgentRequests ticketAgentRequests,                         @TicketDispatchRoute
)
```

FilterPropertiesDecorator applies `withAnnotationFilter(...)` for all annotations **except** the one matching the originating node, so only the valid return route remains in the schema.

**Alternatives considered**:
- Return a generic `Object` and cast: Loses type safety; Embabel can't route on Object.
- Return the original agent's routing type: Would require per-agent handler methods, which defeats unification.
- Separate InterruptRouting for orchestrators vs collectors vs dispatch: Over-fragmentation; the set-difference filtering pattern handles all cases uniformly.

## R5: How should InterruptRequestEvent work?

**Decision**: Add an `InterruptRequestEvent` record to `Events.java` implementing `GraphEvent`. The event carries the source agent type, a target re-route destination, and interrupt metadata. The flow is: (1) send a message to the running agent asking it to produce an InterruptRequest naturally, then (2) store the event so that `FilterPropertiesDecorator` can detect it during interrupt resolution and re-route to the event's target destination instead of back to the originating agent.

**Rationale**: The existing infrastructure provides:
- `AgentRunner.addMessageToAgent(String input, String nodeId)` sends messages via `llmOutputChannel.send(new MessageOutputChannelEvent(nodeId, new UserMessage(input)))`
- `MultiAgentEmbabelConfig.llmOutputChannel` bean handles `MessageOutputChannelEvent` — messages with content are sent to the chat model via `chatModel.call(new Prompt(new AssistantMessage(content), ...))`
- `EventBus.publish(Events.GraphEvent event)` for publishing
- `EventListener.onEvent(Events.GraphEvent event)` for consumption

The interrupt request flow is:
1. **Supervisor/Human publishes** `InterruptRequestEvent` via EventBus with `sourceAgentType` (who's running) and `rerouteToAgentType` (where to go)
2. **Event handler receives** the event and sends a message to the running agent via `addMessageToAgent()` asking it to produce an InterruptRequest. The agent's routing schema already includes `interruptRequest` as an option — **no schema modification is needed on the running agent**.
3. **Event handler stores** the event keyed by node/session ID for later lookup by `FilterPropertiesDecorator`
4. **Agent produces an InterruptRequest naturally** — the message tells it why it should interrupt, and it chooses to do so
5. **The InterruptRequest routes** to the unified `handleUnifiedInterrupt` @Action
6. **FilterPropertiesDecorator detects the stored event** when processing InterruptRouting output — instead of applying normal set-difference filtering (which routes back to the originating agent), the decorator filters to show only the event's `rerouteToAgentType` route
7. **Event is consumed/cleared** after use
8. **Workflow continues** at the target agent

The key insight is that the running agent's schema is never modified. The agent is simply asked (via message) to produce an InterruptRequest, which it already has as a routing option. The re-routing decision is made entirely within FilterPropertiesDecorator during the interrupt handler's output preparation.

If `rerouteToAgentType` is null/absent, the decorator falls back to normal set-difference filtering (routes back to the originating agent), making the event function as a simple "please interrupt" request without re-routing.

**Alternatives considered**:
- Schema priming on the running agent: Would require modifying the running agent's schema mid-call, which is invasive and unnecessary since `interruptRequest` is already a routing option.
- Direct blackboard injection: Would bypass the agent's current LLM call. The message approach lets the agent produce the interrupt naturally.
- Use Spring ApplicationEvents: The project uses a custom EventBus, not Spring's. Mixing would create inconsistency.
- Immediate interrupt without message: The agent needs context about why it's being interrupted to produce a meaningful interrupt request.

## R6: How does the unified handler determine the originating agent?

**Decision**: Use `instanceof` pattern matching on the concrete `InterruptRequest` subtype to determine the originating agent directly, with blackboard history as a fallback.

**Rationale**: Since we keep per-agent InterruptRequest subtypes, the unified handler receives a concrete subtype (e.g., `OrchestratorInterruptRequest`). The handler can directly pattern-match on the subtype class to determine the originating agent:

```java
AgentType origin = switch (request) {
    case OrchestratorInterruptRequest _ -> AgentType.ORCHESTRATOR;
    case DiscoveryOrchestratorInterruptRequest _ -> AgentType.DISCOVERY_ORCHESTRATOR;
    // ... etc
};
```

This is more reliable than history inspection since the type is known at compile time. The blackboard history approach (`findLastNonContextRequest()`) remains available as a fallback and is still used for retrieving the last request's data (template model fields like goal, phase, etc.). The concrete class of the last request determines agent-specific context:
- `OrchestratorRequest.class` → Orchestrator
- `DiscoveryOrchestratorRequest.class` → Discovery Orchestrator
- `PlanningOrchestratorRequest.class` → Planning Orchestrator
- `TicketOrchestratorRequest.class` → Ticket Orchestrator
- `ReviewRequest.class` → Review
- `MergerRequest.class` → Merger
- `ContextManagerRequest.class` → Context Manager
- `OrchestratorCollectorRequest.class` → Orchestrator Collector
- `DiscoveryCollectorRequest.class` → Discovery Collector
- `PlanningCollectorRequest.class` → Planning Collector
- `TicketCollectorRequest.class` → Ticket Collector
- `DiscoveryAgentRequests.class` → Discovery Dispatch Node
- `PlanningAgentRequests.class` → Planning Dispatch Node
- `TicketAgentRequests.class` → Ticket Dispatch Node

This mapping drives both:
1. Which template to use for the interrupt resolution prompt
2. Which InterruptRouting fields to keep (property filter)

**Alternatives considered**:
- Rely solely on blackboard history: Works but less direct than `instanceof` matching on the concrete subtype class. The subtype class is already available and unambiguous.
- Pass the originating agent type explicitly as a field on InterruptRequest: Redundant since the concrete subtype already encodes this. For InterruptRequestEvent (external trigger), the event carries the target agent type directly.

## R7: How should BlackboardRoutingPlanner handle InterruptRouting?

**Decision**: BlackboardRoutingPlanner already handles all `Routing` records generically via `extractFirstNonNullComponent()`. InterruptRouting implements `Routing`, so it will be automatically handled — the planner extracts the single non-null field and matches it against action input types.

**Rationale**: The planner at `BlackboardRoutingPlanner.java` works as follows:
1. Gets the last result from the blackboard
2. If it's a `Routing` instance, calls `extractFirstNonNullComponent()` which iterates all record components and returns the first non-null value
3. Matches the resolved type against action input bindings via `satisfiesType()`

Since InterruptRouting implements `Routing` and will have exactly one non-null field after FilterPropertiesDecorator constrains the schema, the planner will:
1. Detect InterruptRouting as a Routing instance
2. Extract the single non-null field (e.g., `OrchestratorRequest`)
3. Match it against the `@Action` that accepts `OrchestratorRequest`

**No structural changes needed** to BlackboardRoutingPlanner — it already handles the generic case. However, there are two special cases in the planner for `OrchestratorCollectorResult` (routing to `finalCollectorResult` or `handleOrchestratorCollectorBranch`) that occur *before* the Routing check. These are unaffected because they handle `OrchestratorCollectorResult`, not a Routing type.

**One consideration**: If the blackboard ever has an InterruptRouting with *zero* non-null fields (error case), the planner logs a warning and returns null. This is acceptable behavior — the interrupt resolution should always produce exactly one non-null field.

## R8: How should WeAreHerePromptContributor be updated?

**Decision**: Update WeAreHerePromptContributor to reflect the unified interrupt handler in its workflow graph visualization, routing options, and guidance templates.

**Rationale**: WeAreHerePromptContributor.java (~591 lines) provides the LLM with:

1. **Static TEMPLATE** (lines ~65-170): ASCII graph showing all workflow nodes and routing options. Currently shows per-agent interrupt handlers. Must be updated to show a single `handleUnifiedInterrupt` node that all agents can route to, with a note that return routing is dynamically filtered.

2. **GUIDANCE_TEMPLATES map** (lines ~172-280): Per-request-type guidance strings. Currently has entries for each orchestrator/agent request type. The guidance for each node type should be updated to reference the unified InterruptRequest instead of per-agent types.

3. **INTERRUPT_GUIDANCE** string (line ~282): Generic interrupt guidance. Should be updated to describe the unified interrupt flow and the set-difference filtering mechanism.

4. **`buildRoutingOptions()` method** (lines ~390-480): Dynamically builds routing info from `WorkflowAgentGraphNode.buildBranchesFromRouting()`. Since the Routing records are being updated to reference unified InterruptRequest, this method will automatically reflect the change. However, we should verify that `buildBranchesFromRouting()` correctly reports the unified InterruptRequest field.

5. **`>>> YOU ARE HERE <<<` markers**: These are positioned based on the current node. The unified interrupt handler node needs to be represented in the graph so the marker can be placed there when the interrupt handler is active.

**Changes needed**:
- Update TEMPLATE to show single `handleUnifiedInterrupt` node
- Update routing arrows to show unified InterruptRequest from all agent nodes
- Add guidance for the unified interrupt handler
- Update INTERRUPT_GUIDANCE to describe set-difference filtering
- Verify `buildRoutingOptions()` works with updated Routing records

## R9: What prompt infrastructure references per-agent interrupt types?

**Decision**: Six files across three directories need updates to remove per-agent InterruptRequest references and align with the unified type.

**Rationale**: A full audit of the prompt directories revealed the following files with interrupt-related content:

### Java Prompt Contributors/Factories (hard references to per-agent types)

1. **`InterruptLoopBreakerPromptContributorFactory.java`** (`multi_agent_ide_lib/prompt/contributor/`)
   - **Critical**: Has a 17-case `resolveMapping()` switch that maps each request type (e.g., `OrchestratorRequest`) to its per-agent InterruptRequest subtype (e.g., `OrchestratorInterruptRequest.class`) and provides loop-breaking guidance.
   - **Change needed**: Update the switch to reference the unified `InterruptRequest` type. The `InterruptLoopMapping` record still needs the `interruptType` field to count occurrences in history, but it should reference the unified type. The `correctNextStep` and `correctRoutingField` guidance remain per-node-type (not per-interrupt-type), so those are unaffected.
   - **Note**: The dispatched agent cases (DiscoveryAgentRequest, PlanningAgentRequest, TicketAgentRequest) and dispatch cases (DiscoveryAgentRequests, PlanningAgentRequests, TicketAgentRequests) are out of scope for unified interrupt, so their mappings stay as-is until those are migrated.

2. **`WorkflowAgentGraphNode.java`** (`multi_agent_ide_lib/prompt/`)
   - **`getRequestToRoutingMap()`** (lines 153-159): Has 7 explicit entries mapping per-agent InterruptRequest subtypes to their parent Routing types (e.g., `OrchestratorInterruptRequest.class → OrchestratorRouting.class`). These need to be replaced with a single entry: `InterruptRequest.class → InterruptRouting.class`.
   - **`describeBranch()`**: Uses heuristic detection (`fieldName.contains("interrupt")` and `fieldType.getSimpleName().contains("InterruptRequest")`). This will work with the unified type since the field is still named `interruptRequest` and the type still contains "InterruptRequest" in its name.
   - **`buildBranchesFromRouting()`**: Reflection-based, automatically picks up changes to Routing record fields. No changes needed.

3. **`NodeMappings.java`** (`multi_agent_ide_lib/prompt/contributor/`)
   - **Critical**: `initAllMappings()` has 18 per-agent InterruptRequest subtype entries providing display names (e.g., `OrchestratorInterruptRequest.class → "Orchestrator Interrupt"`). These should be reduced to a single unified entry: `InterruptRequest.class → "Interrupt Request"`. The dispatched agent interrupt entries (out of scope) remain.
   - Display names are used by `InterruptLoopBreakerPromptContributor` and `WeAreHerePromptContributor` for human-readable output.

4. **`InterruptPromptContributorFactory.java`** (`multi_agent_ide/prompt/contributor/`)
   - Uses `AgentModels.InterruptRequest` (the sealed interface) via `instanceof` check. Since the unified type will be a concrete record implementing this interface, the check still works.
   - The `InterruptResolutionContributor` template says "route back to the agent that requested the review" and references `previousRequest` — this is generic and works with the unified handler.
   - **Change needed**: Minor — verify the `InterruptResolutionContributor` template text doesn't need to reference the unified InterruptRouting explicitly. Currently it references `{{last_request}}` which is populated from `context.previousRequest()`, which is the request that *preceded* the interrupt (e.g., `OrchestratorRequest`), not the interrupt itself. This is correct for the unified handler.

5. **`ContextManagerReturnRoutes.java`** (`multi_agent_ide_lib/prompt/contributor/`)
   - `findLastNonContextManagerRequest()` filters out `instanceof AgentModels.InterruptRequest` generically. This works with the unified type.
   - **No changes needed**.

### Jinja Templates

6. **`_interrupt_guidance.jinja`** (`resources/prompts/workflow/`)
   - Current text: "Include the agent-specific context fields for your interrupt type so the controller can act."
   - **Change needed**: Update to reference unified InterruptRequest fields. Remove "your interrupt type" phrasing since there's now one type. The `agentSpecificContext` map replaces per-agent fields.

7. **`orchestrator.jinja`** — Lists `interruptRequest` as a routing option with description "Pause for human review". The field name stays the same (still `interruptRequest` on OrchestratorRouting). The description may reference the unified type.

8. **`_context_manager_body.jinja`** — Lists `interruptRequest` as routing option #21. Same consideration as orchestrator.jinja.

9. **`context_manager_interrupt.jinja`** — Template for resuming after interrupt. Generic enough to work as-is.

10. **All other `*.jinja` templates** — Include `_interrupt_guidance.jinja` via `{% include %}`. No direct changes needed beyond the included partial.

### Files NOT needing changes
- `PromptTemplateStore.java` — Generic template storage, no interrupt references
- `PromptTemplateLoader.java` — Generic template loading
- `ContextManagerReturnRoutePromptContributorFactory.java` — Uses `ContextManagerReturnRoutes` generically, filters out interrupts via `instanceof`
- `ContextManagerRoutingPromptContributor.java` / `ContextManagerRoutingPromptContributorFactory.java` — Context manager routing, no interrupt-specific logic

**Alternatives considered**:
- Update all files simultaneously: Higher risk. Better to update in dependency order: NodeMappings → InterruptLoopBreaker → WorkflowAgentGraphNode → InterruptPromptContributor → jinja templates.
- Keep per-agent entries in NodeMappings for backward compatibility: Unnecessary complexity. The per-agent types are being removed from the sealed interface.
