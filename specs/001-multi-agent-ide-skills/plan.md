# Implementation Plan: Multi-Agent IDE Skill Ergonomics Refactor

**Branch**: `001-multi-agent-ide-skills` | **Date**: 2026-03-13 | **Spec**: [spec.md](./spec.md)

## Summary

Refactor the monolithic `multi_agent_test_supervisor` skill into four focused skills (`deploy_multi_agent_ide`, `multi_agent_ide_api`, `multi_agent_ide_debug`, `multi_agent_ide_controller`), add a clone/pull deploy script, add OpenAPI annotations to all controllers with a filter script, and remove the original skill once migration is complete. Two skills (`deploy`, `api`) include Python scripts; two skills (`debug`, `controller`) are document-only — `debug` is an instructional guide, `controller` is a living executable workflow document. Existing content is migrated, not rewritten.

## Technical Context

**Language/Version**: Java 21 (controller annotations), Python 3.11+ (scripts)
**Primary Dependencies**: springdoc-openapi 2.x (Swagger UI + OpenAPI JSON), Python stdlib (`argparse`, `re`, `json`, `subprocess`, `pathlib`)
**Storage**: N/A — skill files are markdown on disk; scripts are standalone CLI tools
**Testing**: JUnit 5 (controller annotation smoke tests), manual script validation
**Target Platform**: macOS / Linux (scripts), Spring Boot 3.x app (controller annotations)
**Project Type**: Mixed — Java submodule (controller changes) + root skills directory (skill files) + scripts directory (Python scripts)
**Performance Goals**: Swagger filter script completes in <5s; deploy/health-check completes in <3 minutes
**Constraints**: Scripts must run without Docker or Gradle; skill files must be self-contained markdown loadable by AI agents; no new persistent storage introduced
**Scale/Scope**: 4 skill files (2 with scripts, 2 instructional), 2 new Python scripts (clone/pull deploy, OpenAPI progressive disclosure), ~10 controller classes receiving annotations

## Constitution Check

*GATE: Must pass before Phase 0 research.*

This feature touches **infrastructure tooling and controller documentation only** — it does not introduce new node types, state machine transitions, artifact propagation mechanisms, agent tools, worktrees, or review gates. All eight constitution principles are unaffected:

| Principle | Impact | Status |
|-----------|--------|--------|
| I. Computation Graph / State Machine | No new node types or transitions | PASS |
| II. Three-Phase Workflow | No workflow changes | PASS |
| III. Orchestrator/Work/Collector Pattern | No new node categories | PASS |
| IV. Event-Driven Node Lifecycle | No new events or lifecycle hooks | PASS |
| V. Artifact-Driven Context | No new artifact files or propagation | PASS |
| VI. Worktree Isolation | No worktree changes | PASS |
| VII. Tool Integration | No new agent tools | PASS |
| VIII. Human-in-the-Loop Gates | No new review gates | PASS |

No complexity violations. Complexity Tracking table not required.

## Project Structure

### Documentation (this feature)

```text
specs/001-multi-agent-ide-skills/
├── plan.md          ← this file
├── research.md      ← Phase 0 output
├── data-model.md    ← Phase 1 output
├── quickstart.md    ← Phase 1 output
├── contracts/
│   └── openapi-consolidated.yaml   ← Phase 1 output
└── tasks.md         ← Phase 2 output (/speckit.tasks)
```

### Source Code

```text
multi_agent_ide_parent/                          # repo root
├── skills/
│   ├── multi_agent_test_supervisor/             # DELETED at end of this feature
│   │   └── SKILL.md
│   ├── deploy_multi_agent_ide/                  # NEW
│   │   ├── SKILL.md
│   │   └── scripts/
│   │       ├── clone_or_pull.py                 # NEW: clone/pull to /tmp
│   │       └── deploy_restart.py               # MIGRATED from multi_agent_test_supervisor
│   ├── multi_agent_ide_api/                     # NEW
│   │   ├── SKILL.md
│   │   └── scripts/
│   │       ├── api_schema.py                   # NEW: progressive disclosure of OpenAPI schema
│   │       └── quick_action.py                 # MIGRATED
│   ├── multi_agent_ide_debug/                   # NEW (instructional — no scripts)
│   │   └── SKILL.md
│   └── multi_agent_ide_controller/              # NEW (executable workflow — no scripts)
│       └── SKILL.md

multi_agent_ide_java_parent/multi_agent_ide/
└── src/main/java/com/hayden/multiagentide/
    ├── controller/
    │   ├── LlmDebugUiController.java            # ADD OpenAPI annotations
    │   ├── LlmDebugRunsController.java          # ADD OpenAPI annotations
    │   ├── AgentControlController.java          # ADD OpenAPI annotations
    │   ├── OrchestrationController.java         # ADD OpenAPI annotations
    │   ├── EventStreamController.java           # ADD OpenAPI annotations
    │   ├── InterruptController.java             # ADD OpenAPI annotations
    │   ├── PermissionController.java            # ADD OpenAPI annotations
    │   ├── LlmDebugPromptsController.java       # ADD OpenAPI annotations
    │   └── UiController.java                   # ADD OpenAPI annotations
    ├── filter/controller/
    │   └── FilterPolicyController.java          # ADD OpenAPI annotations
    ├── propagation/controller/
    │   ├── PropagatorController.java            # ADD OpenAPI annotations
    │   ├── PropagationController.java           # ADD OpenAPI annotations
    │   └── PropagationRecordController.java     # ADD OpenAPI annotations
    └── transformation/controller/
        ├── TransformerController.java           # ADD OpenAPI annotations
        └── TransformationRecordController.java  # ADD OpenAPI annotations
```
