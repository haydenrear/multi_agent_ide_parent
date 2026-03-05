# Implementation Plan: Layered Data Policy Filtering

**Branch**: `001-data-layer-policy-filter` | **Date**: 2026-02-27 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-data-layer-policy-filter/spec.md`

## Summary

Introduce a dynamic, layer-aware filtering subsystem that lets agents and controllers discover active policies,
register/deactivate policies at runtime, and inspect policy-specific filtered output. The design centers on a typed
model split between `Filter` (`ObjectSerializerFilter` and `PathFilter`) and `ExecutableTool` (`BinaryExecutor`,
`JavaFunctionExecutor`, `PythonExecutor`, `AiFilterTool`), with swappable `Interpreter` implementations for regex,
markdown-path, and json-path instructions (`Replace`, `Set`, `Remove`).

## Technical Context

**Language/Version**: Java 21  
**Primary Dependencies**: Spring Boot 3.x, Jackson, LangChain4j-Agentic, Embabel agent framework  
**Storage**: Existing persistence layer (PostgreSQL in production, H2/Postgres test profiles) for policy definitions,
activation state, and filter decision history  
**Testing**: JUnit 5 + Spring Boot test support (test_graph intentionally deferred for this feature)  
**Target Platform**: JVM server application (`multi_agent_ide`)  
**Project Type**: Multi-module Gradle monorepo with app/lib separation  
**Performance Goals**: policy discovery p95 < 300ms, registration/deactivation p95 < 1s, filter execution overhead p95 <
100ms per payload for in-process executor paths under normal payload sizes  
**Constraints**: dynamic runtime registration without restart; deterministic policy order; policy output interpreted as
optional typed result; policy propagation split by layer (workflow agent vs controller)  
**Scale/Scope**: initial support for object and path filter families, four executor types, and three interpreter types;
per-layer and per-agent scope; rolling history for recent decisions and instruction traces

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle                                         | Status | Notes                                                                                                   |
|---------------------------------------------------|--------|---------------------------------------------------------------------------------------------------------|
| I. Computation Graph with State Machine           | PASS   | Filtering hooks are layered integrations, not graph lifecycle replacements                              |
| II. Three-Phase Workflow Pattern                  | PASS   | Feature applies inside existing phases; no phase model changes                                          |
| III. Orchestrator, Work, Collector Pattern        | PASS   | No direct orchestrator/work contract violations; only prompt/event filtering extensions                 |
| IV. Event-Driven Lifecycle & Artifact Propagation | PASS   | Object serializer filters integrate with event flow while preserving lifecycle/event contracts          |
| V. Artifact-Driven Context Propagation            | PASS   | PromptContributor filtering works on artifact-derived prompt inputs; no hidden state channel introduced |
| VI. Worktree Isolation & Feature Branching        | N/A    | No change to ticket worktree lifecycle                                                                  |
| VII. Tool Integration & Agent Capabilities        | PASS   | Executor and interpreter integrations are explicit and auditable                                        |
| VIII. Human-in-the-Loop Review Gates              | PASS   | No change to review gate semantics                                                                      |

**Gate Status**: PASS - No constitution violations, proceed to Phase 0.

## Project Structure

### Documentation (this feature)

```text
specs/001-data-layer-policy-filter/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── filter-policy.schema.json                  # Shared $defs (executor configs, layer binding, common fields)
│   ├── json-path-filter-policy.schema.json        # JsonPathFilter policy schema
│   ├── markdown-path-filter-policy.schema.json    # MarkdownPathFilter policy schema
│   ├── regex-path-filter-policy.schema.json       # RegexPathFilter policy schema
│   ├── policy-registration-request.schema.json    # Per-type endpoint request (executor + common fields, no filter block)
│   ├── policy-registration-response.schema.json
│   ├── deactivate-policy-response.schema.json       # Global deactivation response
│   ├── toggle-policy-layer-request.schema.json      # Layer-scoped enable/disable request
│   ├── toggle-policy-layer-response.schema.json     # Layer-scoped enable/disable response
│   ├── filtered-output-response.schema.json
│   ├── put-policy-layer-request.schema.json
│   ├── put-policy-layer-response.schema.json
│   ├── read-policies-by-layer-response.schema.json
│   ├── read-layer-children-response.schema.json
│   └── read-recent-filtered-records-by-policy-layer-response.schema.json
└── tasks.md                                  # Created later by /speckit.tasks
```

### Source Code (repository root)

```text
multi_agent_ide_parent/
├── multi_agent_ide_java_parent/
│   ├── multi_agent_ide_lib/
│   │   └── src/main/java/com/hayden/multiagentidelib/
│   │       ├── prompt/
│   │       │   └── contributor/             # PromptContributor and related models
│   │       ├── agent/                       # Shared agent models and context interfaces
│   │       └── (new filter model package)   # Filter, ObjectSerializerFilter, PathFilter, ExecutableTool, Interpreter
│   └── multi_agent_ide/
│       └── src/main/java/com/hayden/multiagentide/
│           ├── controller/                  # Registration/discovery/inspection endpoints
│           ├── service/                     # Filter registry, validation, persistence, execution
│           ├── agent/                       # Workflow agent propagation integration
│           └── config/                      # Wiring for dynamic executors and interpreters
└── specs/
    └── 001-data-layer-policy-filter/
```

**Structure Decision**: place core filter domain models in `multi_agent_ide_lib` for shared use and integrate runtime
registry/execution/endpoint concerns in `multi_agent_ide`.

## Phase 0 Research Scope

1. Runtime-safe Python/AI/binary execution and sandbox/timeout strategy for dynamic executors.
2. Deterministic policy ordering and conflict handling when multiple policies target the same payload.
3. Validation approach for typed filter definitions (filter kind + executor + interpreter + scope constraints).
4. Propagation hooks for workflow-agent prompt contributors vs controller event streams.

## Phase 1 Design Scope

1. Define filter domain model and lifecycle states.
2. Define registration/discovery/inspection API contracts.
3. Define persistence schema for policies, activation state, and decision history.
4. Define execution contract for optional typed transformations (`Optional<T>` semantics).
5. Define operational quickstart for registering and validating sample executor/interpreter policy combinations.

## Post-Design Constitution Re-Check

| Principle                                         | Status | Notes                                                        |
|---------------------------------------------------|--------|--------------------------------------------------------------|
| I. Computation Graph with State Machine           | PASS   | Design adds filtering extension points only                  |
| II. Three-Phase Workflow Pattern                  | PASS   | No phase change                                              |
| III. Orchestrator, Work, Collector Pattern        | PASS   | Prompt filtering applies within existing work unit execution |
| IV. Event-Driven Lifecycle & Artifact Propagation | PASS   | Object and path filters are additive and audit-friendly      |
| V. Artifact-Driven Context Propagation            | PASS   | No hidden context transfer introduced                        |
| VI. Worktree Isolation & Feature Branching        | N/A    | Unchanged                                                    |
| VII. Tool Integration & Agent Capabilities        | PASS   | ExecutableTool and Interpreter variants are explicit and typed |
| VIII. Human-in-the-Loop Review Gates              | PASS   | Unchanged                                                    |

**Post-Design Gate Status**: PASS.

## Complexity Tracking

No constitution violations requiring justification.
