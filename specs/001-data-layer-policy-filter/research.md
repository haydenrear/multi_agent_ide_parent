# Research: Layered Data Policy Filtering

**Feature**: 001-data-layer-policy-filter  
**Date**: 2026-02-27

## 1. Dynamic Python FilterTool Execution

**Decision**: Execute Python filter tools via a managed subprocess boundary with strict timeout, bounded output, and explicit serialization contract.

**Rationale**:
- Keeps the Java process stable while supporting polyglot runtime extensibility.
- Supports binary-like dynamic behavior while preserving deterministic request/response shape.
- Allows failure isolation and clear fallback behavior when script execution fails.

**Alternatives considered**:
- In-process JVM Python embedding (rejected: larger runtime coupling and harder operational isolation).
- Remote script service (rejected: adds network dependency and latency for first release).
- Regex-only release (rejected: does not satisfy dynamic script requirement).

## 2. Policy Ordering and Conflict Resolution

**Decision**: Use deterministic ordered policy chains per scope with explicit integer priority and stable tie-breaker (policy ID).

**Rationale**:
- Deterministic output is required for reproducibility and troubleshooting.
- Priority + ID tie-breaker is simple to reason about and easy to persist.
- Enables predictable composition of multiple filters targeting the same payload.

**Alternatives considered**:
- Registration-time order only (rejected: brittle after updates/migrations).
- Best-effort parallel evaluation (rejected: non-deterministic merge behavior).
- Tool-type precedence (rejected: too implicit and harder for operators to control).

## 3. Validation Model for Filter + Tool Pairs

**Decision**: Validate registration in two stages: structural schema validation, then semantic validation against layer/type/tool compatibility rules.

**Rationale**:
- Structural validation catches malformed payloads quickly.
- Semantic validation enforces compatibility (for example, prompt-contributor filter with valid target scope and tool config).
- Produces precise feedback for dynamic agent-driven registration workflows.

**Alternatives considered**:
- Single ad-hoc validator (rejected: less maintainable, poor error clarity).
- Semantic-only validation (rejected: weak schema guarantees).
- Defer validation to execution (rejected: late failures and poor UX).

## 4. Layer Propagation Strategy

**Decision**: Maintain separate propagation pipelines by filter type:
- `PromptContributorFilter` applied during prompt-contributor assembly in workflow agents.
- `EventFilter` applied in controller event-processing before user-facing emission.

**Rationale**:
- Matches feature requirement for layer transparency and targeted control.
- Prevents cross-layer side effects and preserves clear ownership boundaries.
- Simplifies observability and policy-scoped output inspection.

**Alternatives considered**:
- Single shared global pipeline (rejected: weak layer boundaries, high blast radius).
- Agent-only propagation first (rejected: does not satisfy controller filtering goals).
- Controller-only propagation first (rejected: does not satisfy prompt contributor filtering goals).

## 5. Optional Typed Transformation Contract

**Decision**: Standardize filter execution on `Optional<T>` semantics where `T` is the target domain type (`PromptContributor` or `Event`).

**Rationale**:
- Encodes drop vs transform in a single consistent contract.
- Works uniformly for regex and python tools.
- Supports full-item replace, summarization, and line/path-level reductions as transformed values.

**Alternatives considered**:
- Action enum + separate payload object (rejected: more complex runtime contract).
- Boolean allow/deny only (rejected: cannot express summarization/rewrite).
- Unstructured string output (rejected: loses typed safety for domain payloads).
