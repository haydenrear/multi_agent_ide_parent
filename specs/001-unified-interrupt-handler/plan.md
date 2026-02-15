# Implementation Plan: Unified Interrupt Handler

**Branch**: `001-unified-interrupt-handler` | **Date**: 2026-02-15 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-unified-interrupt-handler/spec.md`

## Summary

Replace 10+ per-agent interrupt handler methods across WorkflowAgent, TicketDispatchSubagent, PlanningDispatchSubagent, and DiscoveryDispatchSubagent with a single unified interrupt handler in WorkflowAgent. The per-agent InterruptRequest subtypes are **kept as-is** — the unified handler accepts the `InterruptRequest` sealed interface and uses `instanceof` pattern matching to determine the originating agent. It dynamically filters the InterruptRouting output schema using Embabel's `withAnnotationFilter` via the FilterPropertiesDecorator, and routes the interrupt resolution back to the correct request type. A new InterruptRequestEvent is added to the event system for external interrupt triggering by supervisors or humans: the event handler sends a message to the running agent via `addMessageToAgent()` asking it to produce an InterruptRequest (no schema modification needed), and stores the event so that FilterPropertiesDecorator can detect it during interrupt resolution and re-route to the event's target destination instead of back to the originating agent.

## Technical Context

**Language/Version**: Java 21  
**Primary Dependencies**: Spring Boot 3.x, LangChain4j-Agentic, Embabel Agent Framework (@Agent, @Action, PromptRunner.Creating, withAnnotationFilter), Lombok, Jackson  
**Storage**: N/A (in-memory BlackboardHistory, existing GraphRepository)  
**Testing**: JUnit 5, Mockito, AssertJ, existing test_graph Cucumber framework  
**Target Platform**: JVM (Spring Boot application)  
**Project Type**: Multi-module Gradle project with Git submodules  
**Performance Goals**: Interrupt handling latency unchanged from current behavior  
**Constraints**: Must preserve existing interrupt semantics; Embabel framework routing must work with unified handler; BlackboardRoutingPlanner must handle InterruptRouting without structural changes  
**Scale/Scope**: 7 orchestrator-level + 4 collector interrupt handlers consolidated to 1; 3 dispatched agent handlers deferred; 14 route annotations total

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Computation Graph with State Machine | PASS | No change to state machine transitions. Interrupts still flow through same state transitions (RUNNING → WAITING_INPUT → RUNNING). |
| II. Three-Phase Workflow Pattern | PASS | No change to Discovery → Planning → Implementation flow. Interrupt handling is orthogonal to phase structure. |
| III. Orchestrator, Work, and Collector Node Pattern | PASS | Orchestrator and collector interrupt handling consolidated but node types unchanged. Dispatched agents deferred. |
| IV. Event-Driven Node Lifecycle & Artifact Propagation | PASS | New InterruptRequestEvent follows existing event patterns. Two-step flow (message + schema decoration) uses existing OutputChannel infrastructure. Events still drive transitions. |
| V. Artifact-Driven Context Propagation | PASS | No change to artifact file formats. Interrupt context still flows through blackboard history. |
| VI. Worktree Isolation & Feature Branching | PASS | No impact on worktree management. |
| VII. Tool Integration & Agent Capabilities | PASS | No new tools added. Existing tool context preserved in interrupt flow. |
| VIII. Human-in-the-Loop Review Gates | PASS | Human review via InterruptRequestEvent enhances human-in-the-loop capability. PermissionGate flow unchanged. |

**Gate Result**: ALL PASS. No violations.

## Project Structure

### Documentation (this feature)

```text
specs/001-unified-interrupt-handler/
├── plan.md                                     # This file
├── spec.md                                     # Feature specification
├── research.md                                 # Phase 0 output (8 research decisions)
├── data-model.md                               # Phase 1 output (6 entities, 14 annotations)
├── quickstart.md                               # Phase 1 output
├── contracts/                                  # Phase 1 output
│   └── interrupt-routing-contract.md           # InterruptRouting schema contract
└── tasks.md                                    # Phase 2 output (/speckit.tasks)

test_graph/src/test/resources/features/unified-interrupt-handler/
```

### Source Code (repository root)

```text
multi_agent_ide_parent/
├── multi_agent_ide_java_parent/                           # GIT SUBMODULE
│   ├── multi_agent_ide/                                   # Spring Boot app
│   │   └── src/main/java/com/hayden/multiagentide/
│   │       ├── agent/
│   │       │   ├── AgentInterfaces.java                   # MODIFY: Add unified handleInterrupt, remove per-agent handlers
│   │       │   └── decorator/prompt/
│   │       │       └── FilterPropertiesDecorator.java     # MODIFY: Implement annotation set-difference filtering + event-driven re-route via stored InterruptRequestEvent lookup
│   │       ├── config/
│   │       │   └── BlackboardRoutingPlanner.java          # NO CHANGE: Already handles InterruptRouting generically
│   │       ├── infrastructure/
│   │       │   └── AgentRunner.java                       # REFERENCE: addMessageToAgent() used by InterruptRequestEvent handler
│   │       ├── prompt/contributor/
│   │       │   └── InterruptPromptContributorFactory.java  # MINOR: Verify instanceof check works with unified type
│   │       └── service/
│   │           └── InterruptService.java                  # MINOR: No structural changes needed
│   │   └── src/main/resources/prompts/workflow/
│   │       ├── _interrupt_guidance.jinja                   # MODIFY: Update guidance text for unified interrupt type
│   │       ├── orchestrator.jinja                          # MINOR: Update interruptRequest routing option description
│   │       ├── _context_manager_body.jinja                 # MINOR: Update interruptRequest routing option description
│   │       └── context_manager_interrupt.jinja             # MINOR: Verify template works with unified type
│   │
│   ├── multi_agent_ide_lib/                               # Shared lib
│   │   └── src/main/java/com/hayden/multiagentidelib/
│   │       ├── agent/
│   │       │   ├── AgentModels.java                       # MODIFY: Add InterruptRouting (14 fields). Per-agent InterruptRequest subtypes and Routing records UNCHANGED
│   │       │   └── SkipPropertyFilter.java                # NO CHANGE (pattern reference for new annotations)
│   │       └── prompt/
│   │           ├── WorkflowAgentGraphNode.java             # MODIFY: Update getRequestToRoutingMap() — replace 7 per-agent interrupt entries with unified
│   │           └── contributor/
│   │               ├── WeAreHerePromptContributor.java     # MODIFY: Update TEMPLATE, GUIDANCE_TEMPLATES, INTERRUPT_GUIDANCE
│   │               ├── InterruptLoopBreakerPromptContributorFactory.java  # MODIFY: Update resolveMapping() switch — replace per-agent InterruptRequest refs
│   │               └── NodeMappings.java                   # MODIFY: Replace 18 per-agent interrupt entries with single unified entry
│   │
│   └── acp-cdc-ai/                                        # Events module
│       └── src/main/java/com/hayden/acp_cdc_ai/acp/
│           └── events/
│               └── Events.java                            # MODIFY: Add InterruptRequestEvent
│
│   └── (NEW FILES in multi_agent_ide_lib/agent/)
│       ├── OrchestratorRoute.java                         # NEW: Route marker annotation
│       ├── DiscoveryRoute.java                            # NEW: Route marker annotation
│       ├── PlanningRoute.java                             # NEW: Route marker annotation
│       ├── TicketRoute.java                               # NEW: Route marker annotation
│       ├── ReviewRoute.java                               # NEW: Route marker annotation
│       ├── MergerRoute.java                               # NEW: Route marker annotation
│       ├── ContextManagerRoute.java                       # NEW: Route marker annotation
│       ├── OrchestratorCollectorRoute.java                # NEW: Route marker annotation
│       ├── DiscoveryCollectorRoute.java                   # NEW: Route marker annotation
│       ├── PlanningCollectorRoute.java                    # NEW: Route marker annotation
│       ├── TicketCollectorRoute.java                      # NEW: Route marker annotation
│       ├── DiscoveryDispatchRoute.java                    # NEW: Route marker annotation
│       ├── PlanningDispatchRoute.java                     # NEW: Route marker annotation
│       └── TicketDispatchRoute.java                       # NEW: Route marker annotation
```

**Structure Decision**: Changes span 9 existing Java files + 4 jinja templates across 3 modules, plus 14 new annotation files. No new modules, no new directories (annotations go alongside existing `SkipPropertyFilter.java`). This is a refactoring of existing interrupt handling patterns with expanded scope to cover collectors, dispatch nodes, and the full prompt infrastructure.

## Key Design Decisions

### InterruptRouting has 14 fields (not 7)

The user clarified that while dispatched agent *handlers* are skipped, the dispatch *nodes* (PlanningAgentRequests, DiscoveryAgentRequests, TicketAgentRequests) and *collector* nodes (PlanningCollectorRequest, DiscoveryCollectorRequest, TicketCollectorRequest, OrchestratorCollectorRequest) need to be able to route to the unified interrupt handler. This means InterruptRouting must have return-route fields for all 14 node types.

### InterruptRequestEvent uses message + event-driven re-route

External interrupt requests work via: (1) send a message to the running agent via `addMessageToAgent()` asking it to produce an InterruptRequest (the agent's schema already includes `interruptRequest` as a routing option — no schema modification needed), and (2) store the event in `FilterPropertiesDecorator` so that when the resulting interrupt reaches the handler, the decorator can detect it and filter the InterruptRouting schema to show the event's `rerouteToAgentType` destination instead of routing back to the originating agent. The running agent's schema is never modified.

### BlackboardRoutingPlanner needs no changes

BlackboardRoutingPlanner already handles any `Routing` implementation generically via `extractFirstNonNullComponent()`. Since InterruptRouting implements `Routing` and will always have exactly one non-null field, the planner handles it automatically.

### WeAreHerePromptContributor needs updates

The prompt contributor shows the LLM its workflow position. It needs updates to its static TEMPLATE (ASCII graph), GUIDANCE_TEMPLATES map, and INTERRUPT_GUIDANCE string to reflect the unified interrupt handler instead of per-agent handlers.

### Per-agent InterruptRequest subtypes are kept — no Routing record changes

All per-agent InterruptRequest subtypes are kept as-is with their agent-specific structured fields. The unified handler accepts the `InterruptRequest` sealed interface, so all subtypes route to one handler via Embabel's type system. This means **no changes to any existing Routing records** — `OrchestratorRouting` still has `OrchestratorInterruptRequest interruptRequest`, etc. No polymorphic schema issues, no generic `Map<String, String>` for agent-specific context.

### Prompt infrastructure has 6 files with per-agent interrupt references

A full audit of the prompt directories (`resources/prompts/workflow/`, `multi_agent_ide_lib/.../prompt/`, `multi_agent_ide/.../prompt/`) revealed:

- **`InterruptLoopBreakerPromptContributorFactory.java`**: 17-case switch mapping request types to per-agent InterruptRequest subtypes. Must be updated to reference unified type (in-scope cases only).
- **`WorkflowAgentGraphNode.java`**: `getRequestToRoutingMap()` has 7 per-agent interrupt entries. Replace with single `InterruptRequest.class → InterruptRouting.class`. Heuristic detection in `describeBranch()` works without changes.
- **`NodeMappings.java`**: 18 per-agent interrupt entries in `initAllMappings()`. Reduce to single unified entry.
- **`InterruptPromptContributorFactory.java`**: Uses `instanceof AgentModels.InterruptRequest` (sealed interface). Works with unified type. Minor verification needed.
- **`_interrupt_guidance.jinja`**: References "your interrupt type" — update for unified type.
- **`orchestrator.jinja`** and **`_context_manager_body.jinja`**: List `interruptRequest` as routing option — field name unchanged, descriptions may need minor update.

Files explicitly NOT needing changes: `PromptTemplateStore.java`, `PromptTemplateLoader.java`, `ContextManagerReturnRoutePromptContributorFactory.java`, `ContextManagerReturnRoutes.java`, `ContextManagerRoutingPromptContributor.java`.
