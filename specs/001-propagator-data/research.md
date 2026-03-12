# Research: Controller Data Propagators and Transformers

**Feature**: `001-propagator-data`  
**Date**: 2026-03-11

## 1. Shared Layer Reuse with Separate Binding Models

**Decision**: Reuse the existing shared `Layer` hierarchy and `LayerIdResolver`, but define separate `PropagatorLayerBinding` and `TransformerLayerBinding` models so each capability can describe its own attachment semantics without creating a second layer hierarchy.

**Rationale**:
- The existing layer model already covers the action and controller attachment points needed for this feature.
- Separate bindings make the split between action propagation and controller-endpoint transformation explicit.
- JSON-backed binding persistence matches the existing registration pattern used by filters.

**Alternatives considered**:
- Create a second hierarchy just for propagators and transformers (rejected: duplicates resolution semantics and increases drift).
- Use one generic binding type for both capabilities (rejected: hides meaningful differences between action-stage and controller-endpoint attachment).
- Hard-code attachment points directly in controllers and action handlers (rejected: defeats runtime registration and querying).

## 2. Separate Propagator and Transformer Subsystems

**Decision**: Split propagators and transformers into separate domain models, registration flows, execution services, repositories, and event streams.

**Rationale**:
- Propagators surface information to the controller and may require acknowledgement, approval, or escalation.
- Transformers reshape controller endpoint outputs and replace the outward response surface with strings.
- Keeping them separate makes each behavior easier to reason about and avoids overloading one service with two different responsibilities.

**Alternatives considered**:
- Keep a single combined subsystem with a mode flag (rejected: too much branching logic and weaker audit clarity).
- Build transformers first and defer propagators (rejected: misses the primary controller-visibility goal).
- Build propagators first and fake transformations inside the same service (rejected: obscures controller-response behavior).

## 3. Executor Strategy for Both Capabilities

**Decision**: Reuse the existing `ExecutableTool` abstraction, support `PythonExecutor` for both capabilities, and introduce separate AI executors: `AiPropagatorTool` and `AiTransformerTool`.

**Rationale**:
- `ExecutableTool` already provides the serialization, timeout, descriptor, and execution pattern needed here.
- A shared `PythonExecutor` is sufficient for non-AI dynamic behavior.
- Separate AI executors keep propagator-specific request/result contracts distinct from transformer-specific request/result contracts.

**Alternatives considered**:
- Build a second executor abstraction just for this feature (rejected: duplicates existing execution infrastructure).
- Reuse `AiFilterTool` for both capabilities unchanged (rejected: filter-style output semantics do not fit either acknowledgement-driven propagation or controller-response transformation).
- Limit the first release to AI only (rejected: the feature explicitly requires Python support too).

## 4. Action Responses as the Primary Propagation Surface

**Decision**: Support propagators on both action requests and action responses, but make action responses the primary propagation trigger in the first release.

**Rationale**:
- The clearest controller value comes from surfacing the structured response after the action completes.
- Action-request propagation is still useful for pre-execution visibility and domain checks, but it is not the main handoff problem being solved.
- A first-release focus on action responses keeps the initial integration smaller while still allowing request hooks where needed.

**Alternatives considered**:
- Request-only propagation (rejected: misses the important structured results the controller needs most).
- Response-only propagation with no request support (rejected: too restrictive for pre-execution controller visibility).
- Propagate every prompt/event surface first (rejected: broadens scope too much for the initial split design).

## 5. Controller Endpoint Transformation Contract

**Decision**: Apply transformers at controller endpoint response boundaries and let those endpoints return transformed strings when a transformer is active, while always recording both the original structured payload and the transformed string.

**Rationale**:
- This directly matches the desired endpoint behavior change.
- Recording the before/after values preserves debuggability and benchmarking.
- It keeps the transformation contract explicit rather than burying it inside downstream consumers.

**Alternatives considered**:
- Keep endpoints returning JSON and publish strings only as side artifacts (rejected: does not satisfy the desired outward-facing behavior).
- Replace the original payload and discard it after transformation (rejected: removes the comparison record needed for analysis).
- Make transformation fatal when it fails (rejected: violates the non-fatal execution requirement).

## 6. Acknowledgement via Permission Gate

**Decision**: Introduce acknowledgement-required propagation as a first-class outcome that routes through the existing permission-gate flow, with `ACKNOWLEDGED` as an item-resolution outcome and `PROPAGATION_ACKNOWLEDGED` as a distinct propagation-event action.

**Rationale**:
- Some propagated information is meant only to make the controller aware of important context, not to request a full approval.
- The existing permission-gate flow already models gated acknowledgement-like interactions and can be extended more safely than inventing a second controller acknowledgement channel.
- A distinct action/outcome is needed for audit clarity and benchmarking.

**Alternatives considered**:
- Treat acknowledgement as ordinary approval (rejected: semantically different and less precise in audit history).
- Treat acknowledgement as informational only with no controller action (rejected: does not satisfy the explicit acknowledgement requirement).
- Add a brand-new acknowledgement subsystem (rejected: unnecessary duplication beside the permission gate).

## 7. Separate Event Streams and Duplicate Suppression

**Decision**: Emit `PropagationEvent` for every propagator invocation and `TransformationEvent` for every transformer invocation. Suppress duplicate controller-facing propagation items using a correlation key, but never suppress the underlying audit event.

**Rationale**:
- The user wants both propagator and transformer invocations to be observable.
- Separate event types make analysis easier because transformation and propagation solve different problems.
- Duplicate suppression should reduce controller noise without hiding how often the system actually runs.

**Alternatives considered**:
- Use a single shared event type for both (rejected: weaker observability and harder analytics).
- Suppress both items and events for duplicates (rejected: hides execution frequency and reduces benchmarking value).
- No suppression at all (rejected: repeated action responses could flood the controller).
