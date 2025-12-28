# Research: Interrupt Handling & Continuations

## Decision 1: Treat interrupts as first-class graph events
- **Decision**: Use existing graph event emission and node status transitions to represent interrupt requests and resolutions.
- **Rationale**: Aligns with the event-driven state machine and avoids hidden control flow.
- **Alternatives considered**: Direct method calls or in-memory flags without events (rejected due to reduced auditability).

## Decision 2: Route resumes to the originating node
- **Decision**: Persist a pointer from interrupt handling to the originating node so continuation always returns to the source.
- **Rationale**: Ensures deterministic resumption behavior and avoids skipping work stages.
- **Alternatives considered**: Resume from the next phase (rejected because it can bypass required work).

## Decision 3: Stop/prune interrupts terminate downstream work
- **Decision**: Mark affected nodes terminal and prevent downstream continuation when stop/prune is requested.
- **Rationale**: Protects workflow correctness and aligns with pruning semantics in the constitution.
- **Alternatives considered**: Allow downstream continuation with warnings (rejected due to inconsistent outcomes).
