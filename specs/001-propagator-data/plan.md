# Implementation Plan: Controller Data Propagators and Transformers

**Branch**: `001-propagator-data` | **Date**: 2026-03-11 | **Spec**: `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-propagator-data/spec.md`

**Input**: Feature specification from `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-propagator-data/spec.md`

## Summary

Add two parallel controller-facing data extensions on top of the existing filter/layer foundation: a propagator subsystem for action-request and action-response propagation to the controller, and a transformer subsystem for converting selected controller endpoint outputs into string responses. The design reuses the existing `Layer` and `ExecutableTool` abstractions, introduces separate binding and execution services for propagators vs transformers, routes acknowledgement-required propagations through the permission gate, and captures before/after payloads in separate propagation and transformation event streams.

## Technical Context

**Language/Version**: Java 21 with existing Kotlin in the app module  
**Primary Dependencies**: Spring Boot 3.x, Jackson, Lombok, LangChain4j-Agentic, Embabel agent framework, existing layer/filter execution infrastructure, existing permission-gate flow  
**Storage**: Existing persistence layer (PostgreSQL in production, H2/Postgres test profiles) for propagator registrations, transformer registrations, layer bindings, propagation items, propagation events, and transformation events  
**Testing**: JUnit 5 + Spring Boot test support + module compile validation (`test_graph` intentionally deferred for this feature)  
**Target Platform**: JVM server application in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide`  
**Project Type**: Multi-module Gradle monorepo with shared library and Spring Boot app modules  
**Performance Goals**: non-AI propagation/transform execution p95 < 150ms per payload; registration/query operations p95 < 1s; acknowledgement-required or approval-required propagations surfaced within 30s of the action response  
**Constraints**: must reuse existing `Layer`, `LayerIdResolver`, JSON-backed registration style, and `ExecutableTool`; must split propagators from transformers into separate bindings, services, and event streams; action responses are the primary propagation surface; controller endpoints with transformers return string outputs; before/after payloads must be captured for every transformed run; propagation and transformation failures must remain non-fatal to the originating flow  
**Scale/Scope**: initial rollout covers propagators on action requests and responses, transformers on controller endpoint responses, Python and AI executors for both, acknowledgement/approval propagation items through the permission gate, and audit/query contracts for both execution streams

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Computation Graph with State Machine Architecture | PASS | Propagators and transformers are additive interception layers and do not replace node lifecycle or state transitions |
| II. Three-Phase Workflow Pattern | PASS | Discovery, planning, and implementation sequencing remains unchanged |
| III. Orchestrator, Work, and Collector Node Pattern | PASS | Propagators observe action inputs/outputs without bypassing orchestrator/collector responsibilities |
| IV. Event-Driven Node Lifecycle & Artifact Propagation | PASS | Propagation and transformation events extend the audit trail while preserving event-driven lifecycle handling |
| V. Artifact-Driven Context Propagation | PASS | The feature uses explicit request/response payloads and persisted events/items instead of hidden state |
| VI. Worktree Isolation & Feature Branching | N/A | No worktree lifecycle changes are required for the first rollout |
| VII. Tool Integration & Agent Capabilities | PASS | Reusing `ExecutableTool` with dedicated AI variants keeps execution explicit and auditable |
| VIII. Human-in-the-Loop Review Gates | PASS | Acknowledgement, approval, and escalation reuse the existing permission/review style rather than introducing a side-channel |

**Gate Status**: PASS - no constitution violations identified before research.

## Project Structure

### Documentation (this feature)

```text
/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-propagator-data/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   ├── controller-data-flow.openapi.yaml
│   ├── propagation-event.schema.json
│   └── transformation-event.schema.json
├── examples/
│   ├── register-propagator.json
│   └── register-transformer.json
└── tasks.md
```

### Source Code (repository root)

```text
/Users/hayde/IdeaProjects/multi_agent_ide_parent/
├── specs/
│   └── 001-propagator-data/
└── multi_agent_ide_java_parent/
    ├── multi_agent_ide_lib/
    │   └── src/main/java/com/hayden/multiagentidelib/
    │       ├── filter/model/layer/                    # existing shared layer model reused by both subsystems
    │       ├── filter/model/executor/                 # existing executor abstraction reused by both subsystems
    │       ├── propagation/                           # new shared propagator models, bindings, contexts, request/result types
    │       └── transformation/                        # new shared transformer models, bindings, contexts, request/result types
    └── multi_agent_ide/
        └── src/main/java/com/hayden/multiagentide/
            ├── controller/                            # existing controller outputs and polling endpoints
            ├── cli/                                   # existing CLI/controller formatting hooks
            ├── gate/                                  # existing permission-gate flow reused for acknowledgement and approval
            ├── propagation/controller/                # propagator registration/query/resolve endpoints
            ├── propagation/service/                   # propagator registration, discovery, execution, query services
            ├── propagation/repository/                # propagator JPA entities and repositories
            ├── propagation/integration/               # action-request/action-response propagation hooks
            ├── transformation/controller/             # transformer registration/query endpoints
            ├── transformation/service/                # transformer registration, discovery, execution, query services
            ├── transformation/repository/             # transformer JPA entities and repositories
            ├── transformation/integration/            # controller-endpoint transformation hooks
            └── artifacts/                             # propagation/transformation artifact capture support
```

**Structure Decision**: keep reusable propagator and transformer abstractions in `multi_agent_ide_lib` beside the existing layer/executor model, and place persistence, execution, controller, and integration wiring in `multi_agent_ide` so both capabilities parallel the current filter subsystem without creating a second attachment hierarchy.

## Phase 0 Research Scope

1. Reuse strategy for `Layer`, `LayerIdResolver`, and JSON-backed registration/binding storage across both propagators and transformers.
2. Minimal split domain model for propagators vs transformers while still reusing `ExecutableTool`.
3. Action request vs action response attachment behavior, with action responses as the primary propagation trigger.
4. Permission-gate acknowledgement model, including `PROPAGATION_ACKNOWLEDGED` and acknowledgement-required propagation items.
5. Controller endpoint transformation behavior, including string-return responses and before/after audit capture.
6. Event and audit model split for propagation events vs transformation events.
7. Duplicate suppression strategy for repeated propagations without losing history.

## Phase 1 Design Scope

1. Define shared propagator types, transformer types, separate layer bindings, and separate AI request/result contracts.
2. Define persistence schema for propagator registrations, transformer registrations, propagation items, propagation events, and transformation events.
3. Define API contracts for propagator registration/query/resolve flows and transformer registration/query flows.
4. Define `PropagationExecutionService` and `TransformerExecutionService` plus their integration points.
5. Define quickstart validation flow for compile, targeted tests, and sample contract usage for both subsystems.

## Post-Design Constitution Re-Check

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Computation Graph with State Machine Architecture | PASS | Design remains additive and keeps node-state transitions authoritative |
| II. Three-Phase Workflow Pattern | PASS | No phase-model changes introduced |
| III. Orchestrator, Work, and Collector Node Pattern | PASS | Propagators observe action requests/responses and transformers operate at controller endpoints only |
| IV. Event-Driven Node Lifecycle & Artifact Propagation | PASS | Propagation and transformation events extend lifecycle observability without replacing graph events |
| V. Artifact-Driven Context Propagation | PASS | Propagation items and transformation events remain explicit persisted outputs |
| VI. Worktree Isolation & Feature Branching | N/A | Unchanged in this design phase |
| VII. Tool Integration & Agent Capabilities | PASS | Execution continues through explicit tool abstractions and service wiring |
| VIII. Human-in-the-Loop Review Gates | PASS | Acknowledgement-required and approval-required items align with the existing gating style |

**Post-Design Gate Status**: PASS.

## Complexity Tracking

No constitution violations requiring justification.
