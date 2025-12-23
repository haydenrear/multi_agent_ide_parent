# Research: Agent Graph UI

## Decision: Event transport uses text/event-stream with reconnect + cursor
**Rationale**: CopilotKit consumer supports text/event-stream; cursor-based resume prevents duplication after reconnects while keeping low latency.
**Alternatives considered**: Polling REST endpoint (higher latency, more load), WebSocket (unsupported by current consumer).

## Decision: UI event schema uses ag-ui event types mapped from backend Events
**Rationale**: ag-ui events are the target frontend schema and align with CopilotKit; mapping from backend Events preserves lifecycle semantics while enabling UI-specific shaping via a normalized envelope.
**Alternatives considered**: Frontend-only event model (risk of divergence), direct raw event rendering without normalization (harder UI logic, more edge cases).

## Decision: CopilotKit consumes event stream and exposes UI actions as control events
**Rationale**: CopilotKit already provides UI/agent interaction patterns and can act as the event consumer layer; control actions map directly to existing Pause/Interrupt/Review events.
**Alternatives considered**: Custom event bus in UI (more maintenance), direct backend-to-React event wiring without CopilotKit (misses intended integration).

## Decision: Support custom event mappings when no ag-ui type exists
**Rationale**: Some backend graph events may not have a direct ag-ui equivalent; custom mapping preserves event visibility without blocking the UI.
**Alternatives considered**: Dropping unsupported events (loss of visibility), extending ag-ui core types for every backend event (higher maintenance).

## Decision: Control actions use non-stream endpoints compatible with the frontend consumer
**Rationale**: The event stream is one-way; control actions will use separate endpoints for reliable backend state updates.
**Alternatives considered**: WebSocket command channel (unsupported by current consumer), client-side-only state changes (inconsistent backend state).

## Decision: Viewer plugin registry for specialized node/event rendering
**Rationale**: Different node types and tool call outputs need specialized views; a plugin registry enables drop-in viewers without modifying core UI.
**Alternatives considered**: Single generic viewer (poor UX), hard-coded per-node view switching (harder to extend).
## Decision: Out-of-order handling via per-node last-seen timestamp
**Rationale**: Events may arrive out of order; using timestamp-based reconciliation per node preserves latest state while keeping history.
**Alternatives considered**: Strict sequence ordering (requires server sequence IDs), ignoring out-of-order events (risk stale UI state).
