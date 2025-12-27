# Research: Collector Orchestrator Routing

## Decision 1: Collector Routing Decision Model

- **Decision**: Represent collector routing as an explicit decision field on collector results (e.g., ROUTE_BACK, ADVANCE_PHASE, STOP).
- **Rationale**: Enables deterministic orchestration decisions and supports human review overrides.
- **Alternatives considered**: Infer routing from free-form output text (rejected due to ambiguity and testability gaps).

## Decision 2: Collector State Snapshot

- **Decision**: Store a list of collected child node statuses on each collector node.
- **Rationale**: Keeps collector nodes self-contained and auditable without re-walking the graph to reconstruct state.
- **Alternatives considered**: Rely on transient graph traversal only (rejected due to loss of historical snapshot).

## Decision 3: Ticket Collector Node

- **Decision**: Introduce a ticket collector node to finalize ticket execution summaries and phase routing.
- **Rationale**: Aligns implementation phase with the orchestrator/work/collector pattern and supports routing decisions.
- **Alternatives considered**: Treat ticket orchestrator as its own collector (rejected for role mixing).

## Decision 4: Human Review Gate Placement

- **Decision**: Add optional review gate hooks after collector completion for all collector types.
- **Rationale**: Provides consistent operator control across phases without changing work-node execution semantics.
- **Alternatives considered**: Review gates only at final review stage (rejected because it does not cover early-phase routing decisions).
