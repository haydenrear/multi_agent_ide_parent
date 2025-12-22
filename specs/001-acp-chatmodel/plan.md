# Implementation Plan: ACP Model Type Support

**Branch**: `001-acp-chatmodel` | **Date**: 2025-12-22 | **Spec**: /Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-acp-chatmodel/spec.md

**Input**: Feature specification from `/specs/001-acp-chatmodel/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

Add ACP as a selectable chat model provider for multi_agent_ide runs so orchestration behavior remains unchanged while model calls are routed through ACP when configured.

## Technical Context

**Language/Version**: Java 21  
**Primary Dependencies**: Spring Boot 3.x, LangChain4j agentic services, agentclientprotocol Kotlin SDK (JVM)  
**Storage**: Filesystem/Git artifacts (no new persistent storage)  
**Testing**: JUnit 5, test_graph integration features  
**Target Platform**: JVM service (Spring Boot)  
**Project Type**: Server application  
**Performance Goals**: Standard prompt completion within 3 seconds for ACP-backed chat requests  
**Constraints**: No user-visible workflow changes; event lifecycle and artifacts remain unchanged  
**Scale/Scope**: Single service integration for multi_agent_ide model selection

## Constitution Check

- Computation graph state machine preserved (no changes to node lifecycle) — PASS
- Three-phase workflow remains intact (discovery → planning → implementation) — PASS
- Orchestrator/work/collector separation preserved — PASS
- Artifact-driven propagation unchanged — PASS
- Tool integration remains via LangChain4j tools — PASS
- Human review gate behavior unchanged — PASS

## Constitution Check (Post-Design)

- No violations introduced by design artifacts — PASS

## Project Structure

### Documentation (this feature)

```text
specs/001-acp-chatmodel/
├── plan.md                                     # This file (/speckit.plan command output)
├── research.md                                 # Phase 0 output (/speckit.plan command)
├── data-model.md                               # Phase 1 output (/speckit.plan command)
├── quickstart.md                               # Phase 1 output (/speckit.plan command)
├── contracts/                                  # Phase 1 output (/speckit.plan command)
└── tasks.md                                    # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)

/Users/hayde/IdeaProjects/multi_agent_ide_parent/test_graph/src/test/resources/features/multi_agent_ide
```

### Source Code (repository root)

```text
/Users/hayde/IdeaProjects/multi_agent_ide_parent/
├── multi_agent_ide/
│   └── src/main/java/com/hayden/multiagentide/config/
│       └── LangChain4jConfiguration.java
├── test_graph/
│   └── src/test/java/com/hayden/test_graph/multi_agent_ide/step_def/
│       └── MultiAgentIdeStepDefs.java
└── test_graph/src/test/resources/features/multi_agent_ide/multi_agent_ide_baseline.feature
```

**Structure Decision**: Implement ACP model selection in `multi_agent_ide` configuration and drive selection in test_graph via configuration step data.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| N/A | N/A | N/A |
