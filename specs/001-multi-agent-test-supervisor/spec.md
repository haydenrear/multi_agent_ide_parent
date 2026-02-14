# Feature Specification: LLM Debug UI Skill and Shared UI Abstraction

**Feature Branch**: `001-multi-agent-test-supervisor`  
**Created**: 2026-02-12  
**Last Updated**: 2026-02-13  
**Status**: Updated  
**Input**: Add a reusable skill and LLM-friendly interfaces for debugging multi-agent execution without manual restart-run-watch loops.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Shared UI Abstraction Parity (Priority: P1)

As a platform engineer, I can drive workflow behavior through both TUI and LLM-facing UI endpoints using one shared state/action model, so interface behavior does not drift.

**Why this priority**: This is the architectural foundation for reliable scripted debugging.

**Independent Test**: Execute equivalent interactions from TUI and `/api/llm-debug/ui/...` endpoints and confirm equivalent resulting UI state semantics.

**Cucumber Test Tag**: `@ui_abstraction_parity`

**Acceptance Scenarios**:

1. **Given** an active node-scoped UI context, **When** interactions are sent through TUI and through LLM UI endpoints, **Then** state transitions are semantically equivalent.
2. **Given** a newly added UI interaction capability, **When** it is added via the shared abstraction first, **Then** both TUI and LLM paths can invoke it without interface-specific divergence.
3. **Given** UI state has accumulated from events, **When** `/api/llm-debug/ui/nodes/{nodeId}/state` is requested, **Then** the response comes from materialized shared `UiState` for that scope (not request-time replay).

---

### User Story 2 - Script-First Debug Loop (Priority: P1)

As an LLM supervisor, I can run a deterministic script loop to deploy, start a goal, poll/expand events, inspect UI state, and send actions, so I can diagnose behavior quickly.

**Why this priority**: This is the practical operational loop used to debug the application today.

**Independent Test**: Run each script in the loop with valid args and verify machine-readable outputs plus endpoint/process behavior.

**Cucumber Test Tag**: `@script_interface`

**Acceptance Scenarios**:

1. **Given** the application is not ready, **When** `deploy_restart.py` runs, **Then** it stops any process on port `8080`, restarts via configured mode, and waits for `actuator/health = UP`.
2. **Given** a debug goal, **When** `quick_action.py start-goal` runs, **Then** the response returns root `nodeId` and uses that same value as `runId`.
3. **Given** an active node execution, **When** `quick_action.py poll-events` runs, **Then** it returns paged, truncated event summaries scoped to the node and descendants.
4. **Given** a selected event id, **When** `quick_action.py event-detail` runs, **Then** it returns expanded event content formatted for human/LLM inspection.
5. **Given** a node and message, **When** `quick_action.py send-message` runs, **Then** the message action is queued for that node.
6. **Given** a node and UI action, **When** `ui_action.py` runs, **Then** the shared UI abstraction applies the interaction and subsequent `ui_state.py` reflects the updated state.

---

### User Story 3 - Built-In Program and Routing Context (Priority: P1)

As an LLM operator, I can load workflow/routing context from the skill references, so I do not need to re-derive core agent-routing semantics every session.

**Why this priority**: Debug effectiveness depends on correctly interpreting routing records, collector branch decisions, and prompt-position guidance.

**Independent Test**: Load referenced docs from `SKILL.md` and verify they contain actionable routing, branching, subgraph structure, context-manager memory behavior, and prompt architecture guidance used in event interpretation and prompt updates.

**Cucumber Test Tag**: `@workflow_context_docs`

**Acceptance Scenarios**:

1. **Given** the skill is loaded, **When** an LLM reads the program-context references, **Then** it can explain request/routing flow and collector branching behavior.
2. **Given** an event shows routing ambiguity, **When** an LLM reads the we-are-here contributor reference, **Then** it can map current request type to likely next routing field choices.
3. **Given** prompt updates are needed, **When** an LLM reads prompt architecture references, **Then** it can identify prompt template directories and the correct extension point (template edit vs contributor factory vs decorator) without additional explanation.
4. **Given** an event appears stuck in one phase, **When** an LLM reads subgraph guidance, **Then** it can identify whether behavior is in top-level orchestration or a nested phase subgraph (Discovery, Planning, Ticket).
5. **Given** repeated route-back or repeated request types are observed, **When** an LLM reads context-manager and blackboard-memory references, **Then** it can explain how context reconstruction and loop safeguards should influence next actions.

### Edge Cases

- A script is invoked with missing required args.
- Deploy script clears port `8080` but health never reaches `UP` within timeout.
- Event polling cursor is invalid or beyond available events.
- Event detail is requested for an event outside the node scope.
- UI action payload omits optional fields and relies on defaults.
- Repeated route-back decisions indicate potential loop/stall behavior.
- A routing result contains multiple non-null candidate next-route fields.
- A context-manager recovery result does not provide a clear non-null return route.

### Assumptions

- Single active user/operator context for this debug workflow.
- `nodeId` is the root execution identifier for loop operations; descendants are part of the same scope.
- `runId` is compatibility naming only and must always equal root `nodeId` when returned.
- Existing prompt versioning remains implicit in application behavior.
- Prompt-evolution optimization workflows remain out of scope for this ticket.
- In-application “build-rerun endpoint” behavior is out of scope; restart is controlled externally by skill scripts.
- `test_graph` feature-file generation remains deferred for this feature.

### Delivery Scope

- Provide shared UI abstraction and LLM-facing UI endpoints under `multi_agent_ide`.
- Provide shared state model naming (`UiState`, `UiSessionState`, `UiStateReducer`) and keep TUI rendering code interface-specific.
- Provide materialized node-scoped state storage/query so state endpoints do not replay event history per request.
- Provide convenience endpoints for goal start and event inspection (`poll` and `detail`) under `/api/llm-debug/ui`.
- Provide script-first interface in `skills/multi_agent_test_supervisor`:
  - `deploy_restart.py`
  - `quick_action.py`
  - `ui_state.py`
  - `ui_action.py`
- Provide reference documentation in the skill for workflow/routing context, embedded workflow-branch guidance, subgraph structure, prompt file locations, and prompt-contributor/decorator architecture.
- Include source-of-truth references for routing and memory behavior in skill docs:
  - `AgentInterfaces`
  - `AgentModels`
  - `BlackboardRoutingPlanner`
  - `ContextManagerTools`
  - `BlackboardHistory`

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST provide a shared UI interaction abstraction used by both TUI and LLM-facing interfaces.
- **FR-002**: The system MUST expose node-scoped UI state snapshots through an LLM-friendly endpoint.
- **FR-003**: The system MUST expose LLM-invokable UI actions through a shared action contract.
- **FR-004**: The system MUST expose a convenience endpoint to start a goal and return root execution `nodeId`.
- **FR-005**: The system MUST expose paged event summaries for a node scope with truncation controls.
- **FR-006**: The system MUST expose expanded event-detail retrieval for a specific event id using CLI-style formatting.
- **FR-007**: The system MUST expose a quick-action endpoint for agent-level convenience actions, including `SEND_MESSAGE`.
- **FR-008**: The skill MUST provide script wrappers for deploy/restart, goal start, event polling, event expansion, UI state retrieval, and UI actions.
- **FR-009**: Skill scripts MUST output machine-readable JSON and non-zero exit code on failure.
- **FR-010**: The deploy/restart script MUST stop existing process(es) on configured port and wait for health readiness before success.
- **FR-011**: The skill MUST document per-script schema files for args, payloads, and output contracts.
- **FR-012**: The skill MUST include workflow/routing context references that capture branch behavior expected by workflow-position guidance, so route interpretation is discoverable in-session.
- **FR-013**: The system MUST preserve existing event-driven workflow and review-gate behavior while adding LLM-facing interfaces.
- **FR-014**: New UI interaction capabilities MUST be added through the shared abstraction before interface-specific exposure.
- **FR-015**: Returned `runId` values in this workflow MUST equal root `nodeId`; skill docs and scripts MUST treat `nodeId` as canonical.
- **FR-016**: UI state query endpoints MUST read from materialized shared UI state for the node scope rather than request-time replay reconstruction.
- **FR-017**: The skill MUST document prompt template locations and prompt assembly extension architecture (`PromptContext`, `PromptContributorFactory`, `PromptContributor`, and prompt context decorators) so prompt updates are actionable during loop debugging.
- **FR-018**: The skill MUST document Embabel routing selection semantics: routing result records resolve through the first non-null route field, and the resolved request type determines the next action input match.
- **FR-019**: The skill MUST document orchestrator collector decision handling semantics (`ADVANCE_PHASE`, `ROUTE_BACK`, `STOP`) and their effect on next action selection.
- **FR-020**: The skill MUST document workflow as a parent orchestrator/collector control graph with nested Discovery, Planning, and Ticket subgraphs that each follow orchestrator -> dispatch -> agent(s) -> collector shape.
- **FR-021**: The skill MUST document context-manager recovery behavior, including shared `BlackboardHistory` usage through `ContextManagerTools` and loop/degenerate safeguards.

### Key Entities *(include if feature involves data)*

- **Node-Scoped UI State Snapshot**: Interface-neutral state view representing shared UI semantics.
- **UI Action Command**: Action payload applied through the shared UI abstraction.
- **Quick Action Request/Response**: Agent-level convenience command contract (`START_GOAL`, `SEND_MESSAGE`).
- **Event Summary Item**: Paged, truncated event representation for polling.
- **Event Detail Item**: Expanded, formatter-based event representation for focused inspection.
- **Deploy/Restart Result**: Script-level process/health status result for app restart loop.
- **Routing Resolution Context**: Explanation model for determining which action runs next from routing records and collector decisions.
- **Context Memory Query Result**: Retrieved history/message context used by context-manager flows to recover missing execution context.
- **Loop Safeguard Signal**: Observable indicator for repeated request-type patterns and stuck-handler escalation.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of critical UI interaction intents used in debugging are invokable through the LLM-facing UI contract.
- **SC-002**: At least 95% of parity validation scenarios show equivalent outcomes between TUI and LLM UI paths.
- **SC-003**: 100% of documented skill operations in the debug loop have script wrappers with schema docs.
- **SC-004**: Operators can complete a standard debug loop (deploy -> start goal -> poll events -> inspect detail/state -> act) in under 10 minutes.
- **SC-005**: Deploy/restart script reports health-ready success for standard local startup in under 60 seconds in normal conditions.
- **SC-006**: Event polling returns stable cursor-based pagination with no missing required fields in 100% of sampled responses.
- **SC-007**: From skill references alone, an operator can identify the correct prompt file or prompt extension point for a routing issue in under 5 minutes.
- **SC-008**: From skill references alone, an operator can explain next-action routing selection for sampled routing events with 100% correctness.
- **SC-009**: From skill references alone, an operator can identify which context-manager memory operation to use for a loop/stall triage case in under 3 minutes.

## Notes on Gherkin Feature Files For Test Graph

Gherkin and `test_graph` artifacts are intentionally not created in this feature iteration per stakeholder direction.  
Acceptance scenarios in this specification remain the contractual test input for a later testing-focused feature.
