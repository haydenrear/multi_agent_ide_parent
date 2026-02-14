# Research: LLM Debug UI Skill and Shared UI Abstraction

## Context

This research resolves implementation choices for debugging-stage scope:

- Shared UI abstraction for TUI + LLM parity
- Script-first AI interface in `skills`
- Deploy/restart + start-goal + poll/inspect/action debug loop
- Built-in workflow/routing context references in skill docs

Out of scope by decision:

- Explicit `EvaluationProfile` model
- Explicit `PromptVersion` model
- Prompt-evolution proposal/approval optimization workflows
- In-app self-restart endpoint (`/api/llm-debug/build-rerun`)
- Prompt management script workflows for this ticket

## Decisions

### 1) AI interface mode

- **Decision**: Use Python scripts in the skill as the operational AI interface; scripts call app endpoints and return machine-readable outputs.
- **Rationale**: Gives the LLM stable, discoverable commands and minimizes repeated endpoint/protocol reasoning in prompts.
- **Alternatives considered**:
  - Direct endpoint calls in prompt instructions only: rejected due to inconsistency and high prompt complexity.
  - Non-script shell snippets only: rejected due to lower reuse and weaker argument/validation handling.

### 2) Endpoint mapping strategy

- **Decision**: Expose UI-first endpoint operations through scripts:
  - `deploy_restart.py` for process control + health readiness,
  - `quick_action.py` for start-goal/send-message/poll-events/event-detail,
  - `ui_state.py` and `ui_action.py` for shared UI abstraction interaction.
- **Rationale**: Mirrors the real operator loop and keeps AI operations deterministic.
- **Alternatives considered**:
  - Single monolithic script: rejected because it is harder to discover and maintain.
  - In-app endpoint that rebuilds/restarts itself: rejected because process control belongs outside the app runtime.

### 3) UI parity contract

- **Decision**: Define interface-neutral `UiStateSnapshot` and `UiActionRequest` semantics equivalent to shared `UiState` + interaction events.
- **Rationale**: Prevents interface drift and preserves existing UX behavior as the source of truth.
- **Alternatives considered**:
  - LLM-only bespoke state model: rejected due to parity regressions.
  - Exposing raw internal records directly as public contract: rejected due to coupling risk.

### 4) Shared abstraction boundary

- **Decision**: New interaction capabilities must be implemented in shared UI abstraction before exposure through any interface.
- **Rationale**: Enforces FR-014 and keeps behavior changes centralized.
- **Alternatives considered**:
  - Duplicated logic in TUI and LLM adapters: rejected due to maintenance/regression risk.

### 5) Node identity and scope semantics

- **Decision**: Treat root `nodeId` as the run identity (`runId` alias) for skill workflows, and scope event/state filtering to node + descendants.
- **Rationale**: Aligns identifiers across execution id, artifact key root, and agent process id; avoids ambiguous session/run id mental models.
- **Alternatives considered**:
  - Separate `sessionId` concept for skill routing: rejected due to ambiguity and mismatch with execution graph semantics.

### 6) Run data scope and tenancy

- **Decision**: Assume single-user context with node-scoped debugging loops.
- **Rationale**: Simplifies operational flow and aligns with current usage.
- **Alternatives considered**:
  - Full multi-tenant profile modeling now: rejected as unnecessary for current phase.

### 7) Event inspection shape

- **Decision**: Provide two read paths:
  - paged truncated summaries for polling,
  - explicit detail expansion using `CliEventFormatter`.
- **Rationale**: Keeps polling efficient while preserving deep inspection on demand.
- **Alternatives considered**:
  - Always expanded payloads in poll stream: rejected due to verbosity and lower operator efficiency.

### 8) Transport choices

- **Decision**: Continue REST for command/query and SSE for event stream observation.
- **Rationale**: Reuses existing app patterns and avoids introducing a new transport contract.
- **Alternatives considered**:
  - WebSocket-only command + observation channel: rejected due to higher client complexity for scripts.

### 9) Program/routing context strategy

- **Decision**: Capture workflow routing behavior in skill references (`program_model.md`, `we_are_here_prompt.md`) sourced from `AgentModels`, `AgentInterfaces`, `BlackboardRoutingPlanner`, and prompt contributors.
- **Rationale**: Reduces repeated prompt-context bootstrapping and makes next-action routing decisions explainable from references during debugging.
- **Alternatives considered**:
  - Keep this context implicit in source only: rejected because LLM sessions repeatedly need the same interpretation context.

### 10) Subgraph mental-model strategy

- **Decision**: Document the workflow as nested subgraphs: top-level orchestrator/collector control graph and phase subgraphs (Discovery, Planning, Ticket) with orchestrator -> dispatch -> agent(s) -> collector pattern.
- **Rationale**: Improves triage speed for stalls by helping operators classify whether failures are parent control-flow issues or phase-local routing issues.
- **Alternatives considered**:
  - Flat linear workflow description only: rejected because it obscures fan-out/fan-in dispatch behavior and collector branch intent.

### 11) Context-manager memory strategy

- **Decision**: Document context-manager recovery behavior and tool surface using `ContextManagerTools` over shared `BlackboardHistory` (trace/list/search/message events/item/note).
- **Rationale**: Loop/stall debugging depends on quickly reconstructing missing execution context from shared run history.
- **Alternatives considered**:
  - Omit memory-tool semantics from skill docs: rejected because operators then need source deep-dives in every run.

### 12) Planner routing-resolution strategy

- **Decision**: Document action-resolution semantics from `BlackboardRoutingPlanner`: unwrap first non-null route field from routing records, then match resolved type to next action input bindings, with explicit `OrchestratorCollectorResult` branch handling.
- **Rationale**: Makes action selection deterministic and auditable for event-detail interpretation.
- **Alternatives considered**:
  - Generic “routing picks next action” guidance: rejected as too vague for debugging incorrect branch transitions.

### 13) Testing strategy

- **Decision**: Use module + integration parity tests in `multi_agent_ide`; keep `test_graph` deferred in this ticket.
- **Rationale**: Preserves focus on debugging interfaces and abstraction first.
- **Alternatives considered**:
  - Add test_graph artifacts now: rejected by stakeholder direction.

### 14) Prompt evolution readiness documentation

- **Decision**: Document prompt template locations and prompt assembly extension points in skill references, while keeping prompt-management scripts/endpoints out of scope.
- **Rationale**: Loop debugging often requires prompt edits; operators need file paths and architecture guidance (`PromptContext`, contributor factories, decorators) without enabling a wider prompt-management surface yet.
- **Alternatives considered**:
  - Keep prompt architecture implicit in source only: rejected due to repeated operator bootstrapping cost.
  - Re-enable prompt management endpoints/scripts now: rejected as premature scope expansion.
