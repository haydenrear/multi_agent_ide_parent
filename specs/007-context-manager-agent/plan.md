# Implementation Plan: Context Manager Agent

**Branch**: `007-context-manager-agent` | **Date**: 2026-01-22 | **Spec**: [specs/007-context-manager-agent/spec.md](specs/007-context-manager-agent/spec.md)

**Input**: Feature specification from `specs/007-context-manager-agent/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

The Context Manager Agent acts as a central recovery mechanism for multi-agent workflows that lose context or get stuck. It leverages `BlackboardHistory` to reconstruct context by tracing, searching, and snapshotting historical execution data. The feature includes implementing `BlackboardHistory` tools (trace, list, search, snapshot, notes), integrating `StuckHandler` in `WorkflowAgent` to catch degenerate loops and hung states, and defining `ContextManagerRequest` in `AgentModels` for explicit routing.

## Technical Context

**Language/Version**: Java 21
**Primary Dependencies**: `embabel-agent` (for Agent API), `multi_agent_ide_lib` (for shared models), `utilitymodule` (for EventBus)
**Storage**: In-memory `BlackboardHistory` (per session)
**Testing**: JUnit 5 (Unit tests only)
**Target Platform**: JVM
**Project Type**: Backend Service / Library Extension
**Performance Goals**: Context reconstruction < 30s, Loop detection within 3 iterations
**Constraints**: Must use existing `BlackboardHistory` and `AgentModels` patterns.
**Scale/Scope**: Extensions to existing classes, new Agent implementation.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- **Scope**: Focused on context management and recovery.
- **Complexity**: Minimal new dependencies. Leverages existing `EventBus` and `AgentModels`.
- **Standards**: Follows project structure for agents and models.

## Project Structure

### Documentation (this feature)

```text
specs/007-context-manager-agent/
├── plan.md                                     # This file
├── research.md                                 # Research findings
├── data-model.md                               # Data models (ContextManagerRequest, etc.)
├── quickstart.md                               # Usage guide
├── contracts/                                  # API contracts (if applicable)
└── tasks.md                                    # Implementation tasks
```

### Source Code

```text
multi_agent_ide_parent/
├── multi_agent_ide_lib/
│   └── src/
│       └── main/java/com/hayden/multiagentidelib/
│           ├── agent/
│           │   ├── AgentModels.java            # Add ContextManagerRequest
│           │   ├── BlackboardHistory.java      # Add tools (trace, search, etc.) and EventSubscriber
│           │   └── ContextManagerTools.java    # (New) Tool implementations if separated
│           └── events/
│               └── DegenerateLoopException.java # (New) Exception class
├── multi_agent_ide/
│   └── src/
│       ├── main/java/com/hayden/multiagentide/
│       │   └── agent/
│       │       ├── AgentInterfaces.java        # WorkflowAgent implements com.embabel.agent.api.common.StuckHandler
│       │       └── ContextManagerAgent.java    # (New) Agent implementation
│       └── test/java/com/hayden/multiagentide/agent/
│           └── ContextManagerAgentTest.java    # Unit tests
```

**Structure Decision**: We will extend `multi_agent_ide_lib` with the core data structures and tools, and implement the agent logic in `multi_agent_ide`.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| None      | N/A        | N/A                                 |