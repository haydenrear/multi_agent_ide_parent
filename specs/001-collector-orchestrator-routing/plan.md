# Implementation Plan: Collector Orchestrator Routing

**Branch**: `001-collector-orchestrator-routing` | **Date**: 2025-12-26 | **Spec**: `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-collector-orchestrator-routing/spec.md`

**Input**: Feature specification from `/specs/001-collector-orchestrator-routing/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

Enable collector nodes to aggregate child node state, produce a routing decision, and pass consolidated context back to their orchestrator (loop or advance), with optional human review gating on completion. Add a collector mixin and a ticket collector node to align all phases with the orchestrator/work/collector pattern.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: Spring Boot 3.x, LangChain4j-Agentic, Lombok
**Storage**: N/A (in-memory graph repository; existing persistence if configured)
**Testing**: JUnit 5, test_graph (Cucumber)
**Target Platform**: JVM server (local and containerized)
**Project Type**: server (monorepo with submodules)
**Performance Goals**: Collector routing decision visible within 10 seconds of completion
**Constraints**: Event-driven state machine; artifact-driven context; nodes created only via orchestrators
**Scale/Scope**: Dozens to hundreds of nodes per workflow, multiple concurrent workflows

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- Pass: Workflow remains computation-graph/state-machine driven; no direct node injection.
- Pass: Three-phase workflow preserved with explicit orchestrator/work/collector nodes per phase.
- Pass: Collector nodes aggregate only after all child work nodes complete.
- Pass: Event-driven lifecycle preserved; artifacts remain the context mechanism.
- Pass: Human review gate remains explicit and optional.
- Pass: Worktree isolation unaffected; no changes to merge flow.
- Pass: Tool integration unchanged; no new tools required.
- Post-design check: Pass.

## Project Structure

### Documentation (this feature)

```text
specs/001-collector-orchestrator-routing/
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
├── multi_agent_ide/
│   ├── src/main/java/com/hayden/multiagentide/
│   │   ├── agent/
│   │   ├── infrastructure/
│   │   ├── model/
│   │   │   └── nodes/
│   │   └── orchestration/
│   └── src/test/java/...
├── test_graph/
│   └── src/test/resources/features/multi_agent_ide/
├── specs/
└── buildSrc/
```

**Structure Decision**: Implement model and routing changes in `multi_agent_ide/src/main/java`, and update integration feature files under `test_graph/src/test/resources/features/multi_agent_ide/`.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| None | N/A | N/A |
