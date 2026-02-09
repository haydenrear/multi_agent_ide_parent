# Research: CLI mode interactive event rendering

## Decision 1: Activate CLI mode via Spring profile `cli`

**Decision**: Use a dedicated Spring profile (`cli`) to activate CLI entry/runner and `CliEventListener` beans.  
**Rationale**: Profiles cleanly isolate CLI-only components and ensure event rendering is off in non-CLI runs. This aligns with the requirement that CLI rendering is active only in CLI mode.  
**Alternatives considered**: 
- Environment variable gate only (less explicit and harder to audit)
- Always-on listener with runtime no-op (risk of accidental enablement)

## Decision 2: Render events using the existing EventListener interface

**Decision**: Implement `CliEventListener` using the existing `EventListener` interface and subscribe it to the `DefaultEventBus` when CLI mode is active.  
**Rationale**: Reuses the current event system and avoids creating new event channels. Keeps the CLI output in sync with the rest of the system.  
**Alternatives considered**: 
- Separate event stream exclusively for CLI (adds duplication and divergence risk)
- Polling an event repository (higher latency and complexity)

## Decision 3: Action hierarchy context derived from artifact key semantics

**Decision**: Treat action lifecycle events’ `nodeId` as an artifact-key string and derive hierarchy context (root, depth, parent chain) from the artifact key format.  
**Rationale**: Artifact keys already encode hierarchy; deriving context avoids new data structures and keeps display consistent with the graph.  
**Alternatives considered**: 
- Extend action events with explicit parent fields (requires broader event schema changes)
- Maintain an in-memory graph index for the CLI only (adds state and complexity)

## Decision 4: Fallback rendering for unknown event types

**Decision**: Render unknown event types using a generic fallback that includes `eventType`, `nodeId`, and any available metadata.  
**Rationale**: Ensures forward compatibility as new events are added without breaking CLI output.  
**Alternatives considered**: 
- Drop unknown events (loses visibility)
- Fail hard on unknown events (breaks CLI sessions)

## Decision 5: Resolve pending permission/interrupts from chat submit path

**Decision**: On chat submit, check the permission/interrupt gate for pending requests and resolve those first, before forwarding input as a regular user message.  
**Rationale**: The event stream is useful for user context ("last event"), but gate state is the authoritative source for whether a request is still unresolved. This avoids stale-event races and ensures deterministic routing of user input.  
**Alternatives considered**:
- Infer pending state only from the most recent rendered event (can be stale or out of order)
- Introduce a separate prompt mode that blocks normal chat until cleared (higher UX complexity)

## Decision 6: Deterministic parsing and precedence for chat-based resolution

**Decision**: Use deterministic precedence and parsing for pending requests:
- Permission resolution has priority when both pending types exist.
- Permission input supports command-like forms (selection index, option identifier, cancel).
- Interrupt input maps to resolution type + notes (approve/default feedback forms).  
**Rationale**: A simple parser with clear precedence keeps TUI behavior predictable and mirrors existing API semantics.  
**Alternatives considered**:
- Natural-language classification for every input (less predictable and harder to test)
- Require slash commands only (more rigid and slower for users)
