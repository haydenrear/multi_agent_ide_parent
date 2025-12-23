# Implementation Plan: Agent Graph UI

**Branch**: `002-ag-ui` | **Date**: 2025-12-22 | **Spec**: /Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/002-ag-ui/spec.md

**Input**: Feature specification from `/specs/002-ag-ui/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

Deliver a React-based Agent Graph UI that consumes graph events (via CopilotKit as the event consumer), renders live node/worktree updates, exposes per-node details, and supports user control actions (pause/interrupt/review request). The plan includes mapping backend graph events to ag-ui event types (with custom mappings where needed), a text/event-stream delivery channel, and a pluggable viewer system to render specialized node/event content (merge reviews, streaming changes, tool call outputs). It emphasizes event-stream ingestion, UI state reconciliation for out-of-order events, and API contracts for control actions and event delivery.

## Technical Context

**Language/Version**: Java 21 (backend), TypeScript/JavaScript (frontend)  
**Primary Dependencies**: Spring Boot 3.x, CopilotKit, React, ag-ui Java SDK  
**Storage**: N/A (event-driven UI; backend persistence already exists)  
**Testing**: JUnit 5 for backend, test_graph (Cucumber) for integration, frontend unit tests as needed  
**Target Platform**: Web (browser), backend on Linux server  
**Project Type**: Web application with Spring Boot backend and React frontend  
**Performance Goals**: Event updates visible in UI within 2 seconds (p95)  
**Constraints**: Maintain event-driven state machine semantics; tolerate out-of-order events  
**Scale/Scope**: Single UI for multi-agent graph monitoring and control

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- Event-driven node lifecycle preserved; UI only consumes emitted events. PASS
- Artifact-driven context unchanged; no new implicit context paths. PASS
- Worktree isolation untouched; UI is read/control surface only. PASS
- Human-in-the-loop review gates surfaced via events and control actions. PASS
- Text/event-stream real-time delivery is the primary channel. PASS

## Phase 0: Research Summary

Key decisions captured in `research.md`:
- Text/event-stream delivery with reconnect cursor
- Normalized event envelope mirroring backend event types with ag-ui mapping
- CopilotKit as the event consumer and UI control surface for ag-ui events
- Timestamp-based reconciliation for out-of-order events
 - Viewer plugin registry for specialized node/event rendering

## Phase 1: Design Summary

Artifacts generated in `data-model.md`, `contracts/openapi.yaml`, and `quickstart.md`:
- Event, node, and ag-ui mapping entities, relationships, and state transitions
- Viewer plugin registry and matching rules for specialized content rendering
- Control action API contracts and event stream endpoint definition
- Quickstart workflow for running backend and frontend locally

## Constitution Check (Post-Design)

- Event-driven lifecycle usage preserved in data model and contracts. PASS
- Text/event-stream delivery retained in contracts and research. PASS
- Human review gates preserved via control action endpoints. PASS

## Project Structure

### Documentation (this feature)

```text
specs/002-ag-ui/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
└── tasks.md

test_graph/src/test/resources/features/multi_agent_ide/ag_ui.feature
```

### Source Code (repository root)

```text
multi_agent_ide_parent/
├── specs/
├── multi_agent_ide/                         # Spring Boot app (submodule)
│   ├── src/main/java/com/hayden/multiagentide/
│   ├── src/main/resources/
│   └── fe/                                  # React app root
│       └── src/app/
├── test_graph/                              # Integration tests (submodule)
│   └── src/test/resources/features/multi_agent_ide/
├── libs/                                    # CopilotKit clone
└── ... (other submodules)
```

**Structure Decision**: The UI will live under `multi_agent_ide/fe` and consume backend events from `multi_agent_ide` via text/event-stream; test_graph holds feature-level integration scenarios for event consumption.

## Complexity Tracking

No constitution violations requiring justification.
