# Implementation Plan: Multi-Agent IDE Baseline Tests

**Branch**: `001-multi-agent-ide-specs` | **Date**: 2025-12-20 | **Spec**: `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-multi-agent-ide-specs/spec.md`

**Input**: Feature specification from `/specs/001-multi-agent-ide-specs/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

Complete end-to-end orchestration tests for multi-agent IDE workflows, covering runs with and without submodules plus revision/failure flows, using existing Spring Boot test patterns and event listeners.

## Technical Context

**Language/Version**: Java 21  
**Primary Dependencies**: Spring Boot 3.x, LangChain4j-Agentic, Mockito, AssertJ  
**Storage**: In-memory repositories (GraphRepository, WorktreeRepository)  
**Testing**: JUnit 5, Spring Boot Test  
**Target Platform**: JVM (local test execution)  
**Project Type**: single (Gradle multi-module, Spring Boot app submodule)  
**Performance Goals**: N/A for tests (focused on correctness and event ordering)  
**Constraints**: Tests must rely on event-driven lifecycle and mocked agent/worktree services  
**Scale/Scope**: Multi-agent orchestration phases and worktree flows within multi_agent_ide module

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- Complies with computation graph state machine and event-driven lifecycle principles.
- Preserves three-phase workflow pattern and orchestrator/work/collector relationships.
- Uses artifact-driven context as provided by existing test harness (no new state channels).
- Maintains worktree isolation via mocked WorktreeService interactions.
- Keeps human review gate behavior observable via events in tests.

**Post-Phase 1 Re-check**: PASS. No conflicts with constitution requirements identified in design artifacts.

## Project Structure

### Documentation (this feature)

```text
specs/001-multi-agent-ide-specs/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
└── tasks.md

test_graph/src/test/resources/features/multi_agent_ide/
```

### Source Code (repository root)

```text
multi_agent_ide_parent/
├── specs/
├── multi_agent_ide/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/java/com/hayden/multiagentide/...
│       └── test/java/com/hayden/multiagentide/workflow/OrchestratorEndToEndTest.java
├── test_graph/
│   └── src/test/resources/features/multi_agent_ide/
└── ...
```

**Structure Decision**: Tests are implemented in `multi_agent_ide` module under `src/test/java`, following existing Spring Boot test patterns and using shared test support classes.

## Complexity Tracking

No constitution violations; no additional complexity justification required.

## Phase Outputs

- Phase 0: `research.md`
- Phase 1: `data-model.md`, `contracts/README.md`, `quickstart.md`
