# Implementation Plan: LLM Debug UI Skill and Shared UI Abstraction

**Branch**: `001-multi-agent-test-supervisor` | **Date**: 2026-02-12 | **Spec**: `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-multi-agent-test-supervisor/spec.md`

**Input**: Feature specification from `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-multi-agent-test-supervisor/spec.md`

## Summary

Implement a debugging-stage workflow with three coordinated deliverables:
1. shared UI abstraction in `multi_agent_ide` that preserves TUI semantics while exposing LLM-friendly state/actions,
2. UI-oriented convenience endpoints for goal start and event observation/expansion,
3. script-first skill interface for deploy/restart + start/poll/inspect/action loops plus embedded routing/context references grounded in source-of-truth workflow classes.

Scope excludes prompt-management workflows, explicit EvaluationProfile/PromptVersion modeling, and in-app self-restart endpoints.

## Technical Context

**Language/Version**: Java 21 (app), Kotlin (existing modules), Python 3.x (skill scripts), Markdown/YAML (skill docs + contracts)  
**Primary Dependencies**: Spring Boot 3.x, LangChain4j-Agentic, Embabel framework, Jackson/Lombok, existing ACP events and TUI reducers  
**Storage**: Existing event stream + state store; no new multi-tenant profile/version stores required  
**Testing**: JUnit 5 + integration tests in `multi_agent_ide`; parity tests for TUI vs LLM action outcomes; Python script contract tests  
**Target Platform**: Local/dev backend runtime on Linux/macOS with Codex/CLI skill execution  
**Project Type**: Multi-module monorepo with Git submodules (`skills`, Java app modules)  
**Performance Goals**: Health-ready restart within normal local startup bounds; stable paged event polling for active node scopes  
**Constraints**: Single-user assumption; script-first AI interface; preserve event-driven lifecycle/review gates; optimization workflows out of scope  
**Scale/Scope**: Debugging loop over one node-scoped execution tree (deploy -> start -> poll -> inspect -> act -> repeat)

**Routing/Context Source Anchors**:
- `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java`
- `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AgentModels.java`
- `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/config/BlackboardRoutingPlanner.java`
- `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/ContextManagerTools.java`
- `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/BlackboardHistory.java`

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-Phase 0 Gate Review

- **Gate 1 - Event-driven lifecycle preserved (Principles I, IV)**: PASS  
  New endpoints/scripts observe and drive existing graph events; no alternate lifecycle introduced.
- **Gate 2 - Three-phase workflow preserved (Principle II)**: PASS  
  Debug interfaces do not alter Discovery -> Planning -> Implementation workflow semantics.
- **Gate 3 - Orchestrator/work/collector boundaries preserved (Principle III)**: PASS  
  Shared UI abstraction and scripts act as interaction layers, not orchestration logic.
- **Gate 4 - Artifact/context propagation preserved (Principle V)**: PASS  
  Event/state evidence remains explicit and auditable.
- **Gate 5 - Worktree/review safety preserved (Principles VI, VIII)**: PASS  
  Scripted actions map to existing node/event workflows and existing controls.
- **Gate 6 - Tooling discipline preserved (Principle VII)**: PASS  
  Script wrappers formalize AI operations and reduce ad hoc manual invocation.

### Post-Phase 1 Gate Review

- **Gate 1 - Event-driven lifecycle preserved**: PASS (contracts map to existing event/UI action surfaces)  
- **Gate 2 - Three-phase workflow preserved**: PASS (no design changes phase ordering)  
- **Gate 3 - Orchestrator/work/collector boundaries preserved**: PASS (shared abstraction remains interface-level)  
- **Gate 4 - Artifact/context propagation preserved**: PASS (event/state evidence and routing context references are explicit)  
- **Gate 5 - Worktree/review safety preserved**: PASS (action contract keeps existing control semantics)  
- **Gate 6 - Tooling discipline preserved**: PASS (script catalog + schema docs + program context references)

## Project Structure

### Documentation (this feature)

```text
specs/001-multi-agent-test-supervisor/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── llm-debug-ui-openapi.yaml
└── tasks.md                                     # Generated later by /speckit.tasks
```

### Source Code (repository root)

```text
/Users/hayde/IdeaProjects/multi_agent_ide_parent/
├── skills/
│   ├── skills_template/
│   └── multi_agent_test_supervisor/             # New skill package
│       ├── SKILL.md
│       ├── references/
│       ├── scripts/                             # Python endpoint + deploy/quick-action wrappers
│       └── assets/
├── multi_agent_ide_java_parent/
│   └── multi_agent_ide/
│       ├── src/main/java/com/hayden/multiagentide/ui/state/
│       │   ├── UiState.java
│       │   ├── UiSessionState.java
│       │   └── UiStateReducer.java
│       ├── src/main/java/com/hayden/multiagentide/controller/
│       └── src/main/java/com/hayden/multiagentide/   # Shared UI abstraction + LLM adapters
└── specs/
    └── 001-multi-agent-test-supervisor/
```

**Structure Decision**: Keep implementation localized to existing `skills` and `multi_agent_ide` modules; no new top-level modules or optimization subsystems are introduced.

## Implementation Epics

### Epic A: Shared UI Abstraction + LLM Debug Interfaces (`multi_agent_ide`)

1. Extract interface-neutral state/action contracts from current TUI semantics.
2. Route TUI and LLM interaction handlers through shared UI abstraction.
3. Add LLM-friendly UI/debug endpoints for node state, UI actions, goal start, quick actions, event poll, and event detail.
4. Add parity validation tests for critical TUI-vs-LLM behavior paths.

### Epic B: Script-First AI Interface Skill (`skills`)

1. Create `skills/multi_agent_test_supervisor` from `skills_template` conventions.
2. Add Python scripts for deploy/restart, quick actions (start-goal/send-message/poll-events/event-detail), and UI operations.
3. Document script discovery, invocation, arguments, and expected output in `SKILL.md` and per-script schema docs.

### Epic C: Program Context for Debugging (`skills/references`)

1. Add concise program/routing reference based on `AgentModels`, `AgentInterfaces`, and `BlackboardRoutingPlanner` action-selection semantics.
2. Add embedded workflow-position/branch guidance with parent/child subgraph intuition for Discovery, Planning, and Ticket phases.
3. Add context-manager recovery and shared-memory reference grounded in `ContextManagerTools` and `BlackboardHistory`.
4. Add prompt architecture reference with prompt file locations and extension points (`PromptContributorFactory`, `PromptContributor`, `PromptContextDecorator`).
5. Ensure references are linked from `SKILL.md` and used before active debugging loops.

## Complexity Tracking

No constitution violations or exceptions are required by this design.
