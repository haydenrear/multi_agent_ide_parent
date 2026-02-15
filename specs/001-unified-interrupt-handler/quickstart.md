# Quickstart: Unified Interrupt Handler

**Feature**: 001-unified-interrupt-handler  
**Date**: 2026-02-15

## Overview

This feature consolidates 10+ per-agent interrupt handler methods into a single unified handler. The key insight is that all interrupt handlers follow the exact same logic pattern — the only differences are data: which request class to look up, which template to use, and which routing class to return. By extracting these differences into a mapping and using Embabel's `withAnnotationFilter` to dynamically filter the output schema, we can replace all handlers with one.

## How It Works

### 1. Agent self-interrupts (existing flow, unified)

Before: Each Routing type (OrchestratorRouting, DiscoveryOrchestratorRouting, etc.) had a dedicated per-agent InterruptRequest field that routed to a dedicated handler.

After: All per-agent InterruptRequest subtypes are kept, but they all route to the single `handleUnifiedInterrupt(InterruptRequest, OperationContext)` @Action via the sealed interface. Each agent's routing schema stays clean with its own typed interrupt fields.

### 2. Unified handler resolves the interrupt

```
handleUnifiedInterrupt(InterruptRequest request, OperationContext context):
  1. Determine originAgentType via instanceof pattern matching on the concrete subtype
  2. Look up the last request of that agent type from history
  3. Build prompt context with the agent's template
  4. Call interruptService.handleInterrupt() with InterruptRouting.class
  5. FilterPropertiesDecorator filters the output schema to only show the valid return field
  6. Return InterruptRouting with one field populated
  7. BlackboardRoutingPlanner extracts the non-null field from InterruptRouting
  8. Embabel dispatches to the matching @Action
```

### 3. External interrupt triggering (new — message + event-driven re-route)

```
Supervisor/Human publishes InterruptRequestEvent:
  1. EventBus receives InterruptRequestEvent (with sourceAgentType and rerouteToAgentType)
  2. Event handler calls addMessageToAgent() — sends message to running agent
     asking it to produce an InterruptRequest (agent already has interruptRequest
     as a routing option — no schema modification needed)
  3. Event handler stores the event in FilterPropertiesDecorator (keyed by nodeId)
  4. Agent produces InterruptRequest naturally on its next response
  5. InterruptRequest routes to handleUnifiedInterrupt
  6. FilterPropertiesDecorator detects stored event by nodeId
  7. Instead of routing back to originating agent (normal set-difference),
     filters InterruptRouting to show only the event's rerouteToAgentType route
  8. Event is consumed/cleared after use
  9. Workflow continues at the target agent
```

If `rerouteToAgentType` is null, falls back to normal set-difference filtering (routes back to originating agent).

### 4. Schema filtering via annotation set difference

Each InterruptRouting field is marked with a route annotation (`@OrchestratorRoute`, `@DiscoveryRoute`, `@OrchestratorCollectorRoute`, `@DiscoveryDispatchRoute`, etc. — 14 total). FilterPropertiesDecorator intercepts the LLM call in DefaultLlmRunner and applies `withAnnotationFilter(...)` for all route annotations **except** the originating node's. This removes both the properties and their `$defs` from the schema.

```
FilterPropertiesDecorator.decorate(LlmCallContext<T> ctx):
  if ctx is creating InterruptRouting:
    // Check for stored InterruptRequestEvent first (US3 re-route)
    storedEvent = lookupStoredEvent(ctx.nodeId)
    if storedEvent != null && storedEvent.rerouteToAgentType != null:
      targetAnnotation = mapAgentTypeToAnnotation(storedEvent.rerouteToAgentType)
      consumeEvent(ctx.nodeId)  // clear after use
    else:
      // Normal set-difference: route back to originating agent (US2)
      previousRequest = ctx.promptContext().previousRequest()
      targetAnnotation = mapAgentTypeToAnnotation(previousRequest)  // e.g. OrchestratorRoute.class
    allAnnotations = {14 route annotations}
    toFilter = allAnnotations - {targetAnnotation}
    ops = ctx.templateOperations()
    for each annotation in toFilter:
      ops = ops.withAnnotationFilter(annotation)
    ctx = ctx.toBuilder().templateOperations(ops).build()
  return ctx
```

### 5. BlackboardRoutingPlanner compatibility

BlackboardRoutingPlanner already handles InterruptRouting generically:
- It detects `InterruptRouting` as a `Routing` instance (via `lastResult instanceof AgentModels.Routing`)
- Calls `extractFirstNonNullComponent()` to get the single non-null field
- Matches the extracted type against `@Action` input bindings
- No changes needed to the planner

### 6. WeAreHerePromptContributor updates

The prompt contributor that shows the LLM its position in the workflow graph is updated to:
- Show a single `handleUnifiedInterrupt` node instead of 10+ per-agent nodes
- Update routing arrows to show all agents routing to the unified interrupt handler
- Update guidance templates to reference the unified InterruptRequest
- Describe the set-difference filtering in interrupt guidance

## Files Changed

| File | Change | Description |
|------|--------|-------------|
| `AgentModels.java` | ADD | `InterruptRouting` record (14 fields) |
| `AgentModels.java` | UNCHANGED | All existing Routing records and per-agent InterruptRequest subtypes kept as-is |
| `AgentInterfaces.java` | MODIFY | Add `handleUnifiedInterrupt`, remove 7+ per-agent handlers |
| `FilterPropertiesDecorator.java` | MODIFY | Implement annotation set-difference filtering for InterruptRouting; event-driven re-route via stored InterruptRequestEvent lookup |
| `Events.java` | ADD | `InterruptRequestEvent` record |
| `multi_agent_ide_lib/agent/` | ADD | 14 route marker annotations |
| `BlackboardRoutingPlanner.java` | NONE | Already handles InterruptRouting generically |
| `WeAreHerePromptContributor.java` | MODIFY | Update TEMPLATE, GUIDANCE_TEMPLATES, INTERRUPT_GUIDANCE for unified handler |
| `InterruptLoopBreakerPromptContributorFactory.java` | MODIFY | Update 17-case switch to use unified InterruptRequest |
| `WorkflowAgentGraphNode.java` | MODIFY | Replace 7 per-agent interrupt entries in `getRequestToRoutingMap()` with unified |
| `NodeMappings.java` | MODIFY | Replace 18 per-agent interrupt entries with single unified mapping |
| `InterruptPromptContributorFactory.java` | MINOR | Verify `instanceof` check works with unified type |
| `_interrupt_guidance.jinja` | MODIFY | Update guidance text for unified interrupt type |
| `orchestrator.jinja` | MINOR | Update `interruptRequest` routing option description |
| `_context_manager_body.jinja` | MINOR | Update `interruptRequest` routing option description |

## Migration Path

1. Add 14 route marker annotations to `multi_agent_ide_lib`
2. Add `InterruptRouting` to `Routing` sealed permits
3. Implement `handleUnifiedInterrupt(InterruptRequest, OperationContext)` in WorkflowAgent (accepts sealed interface)
4. Implement `FilterPropertiesDecorator` — annotation set-difference for InterruptRouting, with event-lookup for re-route
5. Remove per-agent interrupt handler methods (7 orchestrator-level + collector handlers)
6. Add `InterruptRequestEvent` and event handler (sends message via `addMessageToAgent`, stores event in FilterPropertiesDecorator for re-route lookup)
7. Update `WorkflowAgentGraphNode.java` — add `InterruptRequest.class → InterruptRouting.class` entry in `getRequestToRoutingMap()`
8. Update `InterruptLoopBreakerPromptContributorFactory.java` — update `resolveMapping()` switch (handler references, not type references)
9. Update `WeAreHerePromptContributor` TEMPLATE, guidance, and interrupt guidance
10. Update `_interrupt_guidance.jinja` and other jinja templates for unified interrupt handler
11. Verify `InterruptPromptContributorFactory` and `NodeMappings` work with existing subtypes
12. Update tests
