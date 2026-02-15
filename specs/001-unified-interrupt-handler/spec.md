# Feature Specification: Unified Interrupt Handler

**Feature Branch**: `001-unified-interrupt-handler`  
**Created**: 2026-02-15  
**Status**: Draft  
**Input**: User description: "Replace the various interrupt handlers for each of the agents with a single unified interrupt handler in WorkflowAgent. Use SkipPropertyFilter added dynamically in FilterPropertiesDecorator based on which agent was running before the interrupt. Add an InterruptRequestEvent to Events which the supervisor or the human can send, causing a re-routing to the given Action with logic depending on which agent is running. Start with skipping the dispatched agents. There will be a single InterruptRequest type and an InterruptRouting, with all of the actions that can route to it filtered except for the actions possible to be routed back to."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Single Interrupt Handler Replaces Per-Agent Handlers (Priority: P1)

As a workflow system, the interrupt handling logic currently duplicated across many agent-specific interrupt handler methods (handleOrchestratorInterrupt, handleDiscoveryInterrupt, handlePlanningInterrupt, handleTicketInterrupt, handleReviewInterrupt, handleMergerInterrupt, handleContextManagerInterrupt, and the dispatched-agent transitionToInterruptState methods) is consolidated into a single unified interrupt handler method in WorkflowAgent. When any agent is interrupted, the same handler is invoked. The handler determines which agent was previously running by inspecting the blackboard history, and routes the interrupt resolution back to the appropriate request type.

**Why this priority**: This is the core structural change. Without the single handler, nothing else works. Every other story depends on this consolidation existing.

**Independent Test**: Can be fully tested by triggering an interrupt from any agent phase and verifying that the unified handler processes it and routes back to the correct request type for that agent.

**Cucumber Test Tag**: `@unified-interrupt-handler`

**Acceptance Scenarios**:

1. **Given** the orchestrator agent is running and produces an interrupt, **When** the interrupt is routed to the unified handler, **Then** the handler resolves the interrupt and produces an OrchestratorRequest as the continuation.
2. **Given** the discovery orchestrator agent is running and produces an interrupt, **When** the interrupt is routed to the unified handler, **Then** the handler resolves the interrupt and produces a DiscoveryOrchestratorRequest as the continuation.
3. **Given** the planning orchestrator agent is running and produces an interrupt, **When** the interrupt is routed to the unified handler, **Then** the handler resolves the interrupt and produces a PlanningOrchestratorRequest as the continuation.
4. **Given** the ticket orchestrator agent is running and produces an interrupt, **When** the interrupt is routed to the unified handler, **Then** the handler resolves the interrupt and produces a TicketOrchestratorRequest as the continuation.
5. **Given** the review agent is running and produces an interrupt, **When** the interrupt is routed to the unified handler, **Then** the handler resolves the interrupt and produces a ReviewRouting as the continuation.
6. **Given** the merger agent is running and produces an interrupt, **When** the interrupt is routed to the unified handler, **Then** the handler resolves the interrupt and produces a MergerRouting as the continuation.

---

### User Story 2 - Dynamic Schema Filtering via SkipPropertyFilter (Priority: P1)

When the unified interrupt handler is invoked, the system dynamically determines which output properties are relevant based on the agent that was running before the interrupt. Properties on the InterruptRouting output schema that do not correspond to valid return routes for the interrupted agent are filtered out (skipped) using the SkipPropertyFilter annotation mechanism and the FilterPropertiesDecorator. This ensures the response schema presented to the decision-maker (human or supervisor) only contains the valid routing options for the current context.

**Why this priority**: Without dynamic filtering, the unified handler would present all possible routing options regardless of context, leading to invalid routing decisions. This is co-requisite with Story 1.

**Independent Test**: Can be tested by triggering an interrupt from a specific agent (e.g., orchestrator) and verifying that only the valid return route fields (e.g., OrchestratorRequest) are present in the schema sent to the decision-maker, while unrelated fields (e.g., TicketOrchestratorRequest) are filtered out.

**Cucumber Test Tag**: `@interrupt-schema-filtering`

**Acceptance Scenarios**:

1. **Given** an interrupt originates from the orchestrator agent, **When** the InterruptRouting output schema is prepared, **Then** only the OrchestratorRequest field is available and all other routing fields are filtered out.
2. **Given** an interrupt originates from the discovery orchestrator, **When** the InterruptRouting output schema is prepared, **Then** only the DiscoveryOrchestratorRequest field is available and all other routing fields are filtered out.
3. **Given** an interrupt originates from the planning orchestrator, **When** the InterruptRouting output schema is prepared, **Then** only the PlanningOrchestratorRequest field is available.
4. **Given** an interrupt originates from the ticket orchestrator, **When** the InterruptRouting output schema is prepared, **Then** only the TicketOrchestratorRequest field is available.

---

### User Story 3 - InterruptRequestEvent for External Interrupt Triggering (Priority: P2)

A supervisor agent or a human operator can send an InterruptRequestEvent through the event system to cause a running agent to be interrupted and re-routed to a different agent. The event specifies the target re-route destination and the interrupt metadata (reason, choices, confirmation items). The flow works in two steps:

1. **Message to running agent**: The event handler sends a message to the currently running agent via `addMessageToAgent(reason, nodeId)`, injecting a UserMessage that asks the agent to produce an InterruptRequest. The running agent's schema already includes `interruptRequest` as a routing option — no schema modification is needed on the running agent. The message simply tells the agent *why* it should choose that option.

2. **Re-route in the interrupt handler**: The agent naturally produces an InterruptRequest, which routes to `handleUnifiedInterrupt` (US1). When `FilterPropertiesDecorator` processes the InterruptRouting output schema, it checks whether a matching `InterruptRequestEvent` was recently received (by node/session ID). If a matching event exists, instead of filtering InterruptRouting to route back to the originating agent (the normal US1/US2 behavior), the decorator filters the schema to show only the route specified in the event — re-routing to wherever the supervisor/human directed.

The key insight is that the external actor does not modify the running agent's schema or force a specific response. It sends a message asking for an interrupt, and the re-routing decision is made later in `FilterPropertiesDecorator` when the interrupt handler prepares its output schema.

**Why this priority**: Stories 1 and 2 handle interrupts that the agents themselves raise and route back to the originating agent. This story adds the ability for external actors (supervisor, human) to trigger interrupts that re-route to a *different* agent, which is a distinct and separable capability.

**Independent Test**: Can be tested by publishing an InterruptRequestEvent while an agent is running and verifying that (a) a message is sent to the agent asking it to interrupt, (b) the agent produces an InterruptRequest naturally, (c) the interrupt handler's InterruptRouting schema is filtered to show the event's target route (not the originating agent's route), and (d) the workflow continues at the target agent.

**Cucumber Test Tag**: `@interrupt-request-event`

**Acceptance Scenarios**:

1. **Given** the orchestrator agent is running, **When** a supervisor sends an InterruptRequestEvent with a target re-route to the discovery orchestrator, **Then** a UserMessage with the interrupt reason is sent to the orchestrator via `addMessageToAgent`, **And** the orchestrator produces an `OrchestratorInterruptRequest` that routes to `handleUnifiedInterrupt`, **And** the InterruptRouting output schema is filtered to show only `discoveryOrchestratorRequest` (the event's target), **And** the workflow continues at the discovery orchestrator.
2. **Given** the discovery orchestrator is running, **When** a human sends an InterruptRequestEvent with a target re-route to the planning orchestrator, **Then** a UserMessage is sent to the discovery orchestrator, **And** the discovery orchestrator produces a `DiscoveryOrchestratorInterruptRequest` that routes to `handleUnifiedInterrupt`, **And** the InterruptRouting output schema is filtered to show only `planningOrchestratorRequest`.
3. **Given** an InterruptRequestEvent is sent with no target re-route (default behavior), **When** the agent produces an InterruptRequest and it reaches the unified handler, **Then** FilterPropertiesDecorator falls back to normal set-difference filtering (US2), routing back to the originating agent.
4. **Given** an InterruptRequestEvent is sent with HUMAN_REVIEW type and structured choices, **When** the agent produces an InterruptRequest and it reaches the unified handler, **Then** the choices are presented to the human via the interrupt resolution prompt, **And** the selected choice determines which return route field is populated in the InterruptRouting.
5. **Given** an InterruptRequestEvent targets an agent that is not currently running, **When** the event handler receives the event, **Then** no message is sent, **And** an informational event is emitted indicating the target agent is not active.

---

### User Story 4 - Dispatched Agents Skipped in Initial Scope (Priority: P3)

In the initial implementation, the dispatched sub-agents (TicketDispatchSubagent, PlanningDispatchSubagent, DiscoveryDispatchSubagent) do not participate in the unified interrupt handler. Their existing interrupt handling (transitionToInterruptState) remains unchanged or is removed. Interrupts for dispatched agents are handled by interrupting at the orchestrator level that dispatched them instead.

**Why this priority**: This scopes the initial delivery to the orchestrator-level agents and avoids the complexity of sub-agent interrupt unification. The dispatched agents can be unified in a follow-up iteration.

**Independent Test**: Can be tested by verifying that dispatched sub-agents do not route to the unified interrupt handler, and that interrupting a dispatched agent's work is achieved by interrupting its parent orchestrator.

**Cucumber Test Tag**: `@skip-dispatched-interrupts`

**Acceptance Scenarios**:

1. **Given** a ticket dispatch sub-agent is running, **When** an interrupt is needed, **Then** the interrupt is raised at the ticket orchestrator level, not at the dispatched agent level.
2. **Given** the unified interrupt handler is invoked, **When** the previous request type is a dispatched agent request, **Then** the handler delegates to the parent orchestrator's interrupt flow.

---

### Edge Cases

- What happens when the unified handler cannot find a previous request in the blackboard history for the interrupted agent? The system should emit an error event and raise a degenerate loop exception, consistent with current behavior.
- What happens when an InterruptRequestEvent targets an agent that is not currently running? The event should be acknowledged but no interrupt transition should occur; an informational event should be emitted.
- What happens when the FilterPropertiesDecorator encounters an unknown agent type during schema filtering? The system should fall back to presenting all routing options (no filtering) and log a warning.
- What happens when multiple InterruptRequestEvents arrive for the same agent simultaneously? Only the first should be processed; subsequent events should be queued or ignored with an informational event.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a single unified interrupt handler method in WorkflowAgent that replaces all existing per-agent interrupt handler methods (handleOrchestratorInterrupt, handleDiscoveryInterrupt, handlePlanningInterrupt, handleTicketInterrupt, handleReviewInterrupt, handleMergerInterrupt, handleContextManagerInterrupt).
- **FR-002**: The unified interrupt handler MUST determine which agent was running before the interrupt by inspecting the blackboard history for the most recent request type.
- **FR-003**: The unified interrupt handler MUST produce an InterruptRouting output that contains all possible return route fields, with only the valid route for the interrupted agent populated.
- **FR-004**: System MUST dynamically apply property filtering on the InterruptRouting output schema using the SkipPropertyFilter annotation and FilterPropertiesDecorator, so that only the valid return route fields for the interrupted agent are visible to the decision-maker.
- **FR-005**: The FilterPropertiesDecorator MUST determine which fields to filter based on the previous request type found in the blackboard history at the time of the interrupt.
- **FR-006**: The unified interrupt handler MUST accept the sealed `InterruptRequest` interface, allowing all existing per-agent InterruptRequest subtypes to route to it. Per-agent subtypes are kept as-is to preserve structured, agent-specific fields.
- **FR-007**: System MUST introduce an InterruptRequestEvent in the event system that external actors (supervisor agent, human operator) can send to trigger an interrupt on a running agent.
- **FR-008**: When an InterruptRequestEvent is received, the system MUST: (1) send a UserMessage to the running agent via `addMessageToAgent(reason, nodeId)` asking it to produce an InterruptRequest (the agent's schema already includes `interruptRequest` as a routing option — no schema modification needed), and (2) store the event so that when the resulting InterruptRequest reaches `handleUnifiedInterrupt` and `FilterPropertiesDecorator` processes the InterruptRouting output, the decorator can detect the matching event and filter the InterruptRouting schema to show the event's target re-route destination instead of the originating agent's return route.
- **FR-009**: Each per-agent InterruptRequest subtype MUST continue to carry its own agent-specific structured fields (interrupt type, reason, choices, confirmation items, context for decision). The unified handler uses `instanceof` pattern matching on the concrete subtype to determine the originating agent.
- **FR-010**: In the initial implementation, dispatched sub-agents (TicketDispatchSubagent, PlanningDispatchSubagent, DiscoveryDispatchSubagent) MUST NOT participate in the unified interrupt handler. Their interrupts are handled at the parent orchestrator level.
- **FR-011**: The unified handler MUST preserve the existing decorator pipeline (request decorators, result decorators, prompt context decorators) during interrupt processing.
- **FR-012**: System MUST emit appropriate events (InterruptStatusEvent, error events) during interrupt processing, consistent with current behavior.

### Key Entities

- **InterruptRequest (Sealed Interface — Unchanged)**: The existing sealed interface with all per-agent subtypes kept as-is (e.g., `OrchestratorInterruptRequest`, `DiscoveryOrchestratorInterruptRequest`). Each subtype carries agent-specific structured fields. The unified handler accepts this interface and uses `instanceof` to determine origin.
- **InterruptRouting (New)**: A new routing record with 14 nullable fields — one per node type that can route to the interrupt handler (7 orchestrator-level, 4 collector, 3 dispatch). Only the field matching the interrupted agent is populated; the rest are filtered out via annotation set-difference in FilterPropertiesDecorator.
- **InterruptRequestEvent (New)**: A new event type in the event system that external actors can publish to trigger an interrupt and re-route on a running agent. Contains: target node/session identifier, target re-route destination (which agent to route to), interrupt type, reason, choices, confirmation items. The event handler sends a message to the running agent asking it to interrupt; the event is stored so FilterPropertiesDecorator can detect it later during interrupt resolution.
- **FilterPropertiesDecorator (Enhanced)**: The existing decorator enhanced with two behaviors when processing InterruptRouting: (a) **normal interrupt resolution** — applies annotation set-difference filtering so only the originating agent's return route is visible (US2), and (b) **event-driven re-route** — checks for a matching stored `InterruptRequestEvent` by node/session ID, and if found, filters the InterruptRouting schema to show only the event's target re-route destination instead of the originating agent's route (US3).

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: The number of interrupt handler methods across the codebase is reduced from 10+ individual handlers to 1 unified handler, reducing interrupt-related code duplication by at least 80%.
- **SC-002**: All existing interrupt scenarios (orchestrator, discovery, planning, ticket, review, merger, context manager) continue to function correctly with the unified handler, with 100% of existing integration tests passing.
- **SC-003**: When an interrupt is triggered, only the valid return route for the interrupted agent is presented to the decision-maker, with 0 invalid routing options visible.
- **SC-004**: An external actor (supervisor or human) can trigger an interrupt and re-route on any in-scope running agent within 1 event publish. The event causes a message to be sent to the agent (asking it to interrupt), and the event is stored so that the interrupt handler's InterruptRouting output is filtered to the event's target destination instead of the originating agent.
- **SC-005**: Adding interrupt handling for a new agent type requires adding only 1 new field to InterruptRouting and 1 filter rule, rather than creating an entirely new handler method, interrupt request type, and routing type.

## Assumptions

- The blackboard history reliably contains the most recent request for any running agent, and can be inspected to determine which agent was active before the interrupt.
- The SkipPropertyFilter annotation mechanism and FilterPropertiesDecorator can be extended to support dynamic, context-dependent filtering (not just static annotation-based filtering).
- The InterruptService.handleInterrupt method can work with a unified InterruptRequest type and a generic InterruptRouting return type.
- Dispatched sub-agents will be addressed in a follow-up feature iteration; their current interrupt handling remains unchanged or is simplified in this scope.
- The event bus infrastructure supports publishing and subscribing to a new InterruptRequestEvent type without architectural changes.

## Notes on Gherkin Feature Files For Test Graph

The functional requirements, success criteria, edge cases, and measurable outcomes will then be encoded in feature files
using Gherkin and Cucumber in test_graph/src/test/resources/features/unified-interrupt-handler.

The translation to Gherkin feature files should adhere to the instructions test_graph/instructions.md and also
information about the test_graph framework can be found under test_graph/instructions-features.md. When completing
the integration test feature files in the test_graph, check to see if feature files already exist, and if so use the existing
step definitions if at all possible.

When writing the feature files and step definitions for those files, make sure to create as few steps as possible, and make
the setup as generic as possible. It is as simple as provide information to be added to the context, then assert that
messages were received as expected. There should not be a lot of different step definitions. Additionally, Spring
is used so we can decouple initialization logic. This means by adding a bean of a type as a component accepting a
context and depending on other beans in that computation graph, we can then add an assertion over that context without
changing any of the other code. This is what it means to run the tests together but have them independent. They are
sufficiently decoupled to be useful over time and minimize regressions.
