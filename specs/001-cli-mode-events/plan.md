# Implementation Plan: CLI mode interactive event rendering

**Branch**: `001-cli-mode-events` | **Date**: 2026-02-07 | **Spec**: /Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-cli-mode-events/spec.md

**Input**: Feature specification from `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-cli-mode-events/spec.md`

**Note**: This plan follows the `/speckit.plan` workflow and produces research/design artifacts under the feature spec directory.

## Summary

Enable a CLI run mode that prompts for a goal, renders a broad set of execution events (including tool calls, interrupts, permissions, worktree, plan updates, UI/render updates), and shows action start/completion with hierarchy context derived from artifact keys, using a CLI event listener that subscribes to the existing event system only when CLI mode is active.

## Technical Context

**Language/Version**: Java 21  
**Primary Dependencies**: Spring Boot 3.x, Embabel agent framework, LangChain4j, Lombok, Jackson  
**Storage**: PostgreSQL (production), H2/Postgres for tests via Spring Data  
**Testing**: JUnit 5, Spring Boot test, Gradle  
**Target Platform**: JVM on developer workstation/server (interactive CLI)  
**Project Type**: Multi-module Java monorepo with Spring Boot application  
**Performance Goals**: CLI goal prompt available within 2 seconds; event rendering within 1 second of emission  
**Constraints**: CLI rendering only active in CLI mode; unknown event types must have fallback rendering; interrupt input must not deadlock the run  
**Scale/Scope**: One active goal per CLI session; single terminal output stream

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- PASS: Event-driven state machine preserved; CLI listener consumes existing events without altering state transitions.
- PASS: Three-phase workflow remains unchanged.
- PASS: Orchestrator/work/collector pattern unaffected.
- PASS: Artifact-driven context propagation unchanged.
- PASS: Worktree isolation and branching unchanged.
- PASS: Tool integration model unchanged; CLI only renders tool call events.
- PASS: Human review gates remain intact; CLI only provides the input surface.
Post-design check: PASS (no constitutional changes introduced by design artifacts).

## Project Structure

### Documentation (this feature)

```text
/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-cli-mode-events/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
└── tasks.md

/Users/hayde/IdeaProjects/multi_agent_ide_parent/test_graph/src/test/resources/features/{name}
```

### Source Code (repository root)

```text
/Users/hayde/IdeaProjects/multi_agent_ide_parent/
├── specs/
├── multi_agent_ide_java_parent/
│   ├── multi_agent_ide/
│   │   └── src/main/java/com/hayden/multiagentide/
│   │       ├── infrastructure/DefaultEventBus.java
│   │       └── (CLI mode entry point + event rendering additions)
│   ├── acp-cdc-ai/
│   │   └── src/main/java/com/hayden/acp_cdc_ai/acp/events/
│   │       ├── Events.java
│   │       ├── EventListener.java
│   │       └── ArtifactKey.java
│   └── multi_agent_ide_lib/
├── test_graph/
└── (other submodules)
```

**Structure Decision**: Changes are confined to the multi-agent IDE Spring Boot app and the shared ACP events module; no new modules are introduced.

## Complexity Tracking

No constitution violations.

## Phase 0: Outline & Research

- Produce research decisions for CLI mode activation, event rendering strategy, and artifact-key hierarchy extraction.
- Confirm no unresolved clarifications remain.

## Phase 1: Design & Contracts

- Define entity relationships for CLI session, goal, events, interrupts, and artifact key hierarchy.
- Document the internal event rendering contract and fallback behavior for unknown event types.
- Provide quickstart steps for running CLI mode once implemented.
- Update agent context after plan artifacts are generated.

## Phase 2: Implementation Planning (high level)

- Add CLI entry/runner that prompts for goals and handles interrupts.
- Implement `CliEventListener` that subscribes to the event bus in CLI mode only.
- Render event categories with clear formatting, including tool calls and action lifecycle with hierarchy context.
- Ensure CLI prompt and event rendering are coordinated (no interleaving corruption).
- Add coverage for CLI mode start/stop and event rendering behavior.

## Phase 3: Interactive TUI & Event-Driven State

- Introduce a Spring Shell TerminalUI view with a scrollable event history and a persistent chat input pane.
- Implement keyboard navigation (Up/Down) and an Enter-triggered detail pane for expanded event inspection.
- Emit `GraphEvent` instances for all TUI interactions (navigation, open/close detail, submit chat).
- Build a `TuiState` reducer that derives state from `GraphEvent` streams.
- Render TUI changes strictly from `TuiState` to keep the UI deterministic and supervisor-friendly.
