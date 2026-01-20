# Implementation Plan: Agent Prompts, Structured Outputs, and Tool Prompt Contributors

**Branch**: `004-agent-prompts-outputs` | **Date**: 2026-01-19 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-agent-prompts-outputs/spec.md`

## Summary

This feature enhances the multi-agent IDE workflow system with:
1. **Typed context flow with IDs** - Every agent input/output gets a unique context ID enabling full traceability
2. **Previous context slots for reruns** - Agents can be rerun with previous attempt context mapped into prompts
3. **Prompt contributor framework** - Tools can dynamically contribute prompting guidance to agents
4. **Episodic memory prompt contributor** - First implementation with retain/reflect/recall guidance
5. **Standardized templates** - Delegation template for orchestrators, consolidation template for collectors
6. **Enhanced discovery collector** - Code map + recommendations + query-specific findings
7. **Typed upstream dependencies** - Planning receives discovery context, tickets receive discovery + planning context

## Technical Context

**Language/Version**: Java 21 (existing project)
**Primary Dependencies**: Spring Boot 3.x, Embabel Agent Framework (@Agent), LangChain4j
**Storage**: GraphRepository (existing), artifact markdown files
**Testing**: JUnit 5, test_graph Cucumber integration tests
**Target Platform**: JVM server (Spring Boot application)
**Project Type**: Multi-module Gradle with Git submodules

**Performance Goals**: 
- Prompt assembly < 100ms
- Context ID generation O(1)
- No significant overhead from typed context wrapping

**Constraints**:
- Must integrate with existing AgentModels.java records
- Must work with existing BlackboardHistory prompt provider pattern
- Must maintain backward compatibility with existing routing classes

**Scale/Scope**:
- 14 agent types to enhance (excluding context agents)
- 4 orchestrators, 4 collectors, 4 work agents, review agent, merger agent
- ~80 functional requirements

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Computation Graph with State Machine | PASS | Context IDs integrate with existing node lifecycle |
| II. Three-Phase Workflow Pattern | PASS | Enhancement respects Discovery → Planning → Implementation |
| III. Orchestrator, Work, Collector Pattern | PASS | Standardized templates align with existing pattern |
| IV. Event-Driven Lifecycle & Artifact Propagation | PASS | Context IDs become part of event payloads |
| V. Artifact-Driven Context Propagation | PASS | Typed context enhances artifact propagation |
| VI. Worktree Isolation | N/A | No changes to worktree management |
| VII. Tool Integration | PASS | Prompt contributor framework IS a tool integration pattern |
| VIII. Human-in-the-Loop Review Gates | PASS | Review agent gets enhanced prompts like other agents |

**Gate Status**: PASS - No violations, proceed to Phase 0

## Project Structure

### Documentation (this feature)

```text
specs/004-agent-prompts-outputs/
├── plan.md                                     # This file
├── research.md                                 # Phase 0 output
├── data-model.md                               # Phase 1 output
├── quickstart.md                               # Phase 1 output
├── contracts/                                  # Phase 1 output
│   ├── context-id.schema.json
│   ├── delegation-template.schema.json
│   ├── consolidation-template.schema.json
│   ├── discovery-report.schema.json
│   └── prompt-contributor.schema.json
└── tasks.md                                    # Phase 2 output (speckit.tasks)
```

### Source Code (repository root)

```text
multi_agent_ide_parent/
├── multi_agent_ide_lib/                        # GIT SUBMODULE: shared models
│   └── src/main/java/com/hayden/multiagentidelib/
│       ├── agent/
│       │   ├── AgentModels.java               # MODIFY: Add context ID fields to all models
│       │   ├── BlackboardHistory.java         # MODIFY: Enhance prompt provider pattern
│       │   ├── ContextId.java                 # NEW: Context ID value object
│       │   ├── UpstreamContext.java           # NEW: Typed upstream context container
│       │   └── PreviousContext.java           # NEW: Previous attempt context mixin + per-agent implementations
│       ├── prompt/                            # NEW PACKAGE: Prompt contributor framework
│       │   ├── PromptContributor.java         # NEW: Contributor interface
│       │   ├── PromptAssembly.java            # NEW: Assembles prompts from contributors
│       │   ├── PromptContributorRegistry.java # NEW: Registry for contributors
│       │   └── EpisodicMemoryPromptContributor.java  # NEW: First implementation
│       └── template/                          # NEW PACKAGE: Standardized templates
│           ├── DelegationTemplate.java        # NEW: Orchestrator output structure
│           ├── ConsolidationTemplate.java     # NEW: Collector output structure
│           ├── DiscoveryReport.java           # NEW: Enhanced discovery output
│           └── PlanningTicket.java            # NEW: Enhanced planning output

├── multi_agent_ide/                           # GIT SUBMODULE: Spring Boot app
│   └── src/main/java/com/hayden/multiagentide/
│       ├── agent/
│       │   └── AgentInterfaces.java           # MODIFY: Use new templates, prompt assembly
│       ├── config/
│       │   └── PromptContributorConfig.java   # NEW: Spring config for contributors
│       └── service/
│           └── ContextIdService.java          # NEW: Context ID generation service

└── test_graph/                                # GIT SUBMODULE: Integration tests
    └── src/test/resources/features/
        └── (existing features cover this)
```

**Structure Decision**: Enhancements split between `multi_agent_ide_lib` (models, interfaces) and `multi_agent_ide` (Spring integration, services). This follows existing pattern where models are in lib and Spring beans are in app.

## Complexity Tracking

No constitution violations requiring justification.

## Key Design Decisions

### 1. Context ID Format
```
{workflowRunId}/{agentType}/{sequenceNumber}/{timestamp}
```
Example: `wf-abc123/discovery-collector/001/2026-01-19T10:30:00Z`

### 2. Upstream Context Typing
Each agent type has explicitly typed upstream context:
- `DiscoveryAgentContext` - receives `DiscoveryOrchestratorContext`
- `PlanningAgentContext` - receives `DiscoveryCollectorContext`
- `TicketAgentContext` - receives `DiscoveryCollectorContext` + `PlanningCollectorContext`

### 3. Prompt Contributor Pattern
```java
interface PromptContributor {
    Set<AgentType> applicableAgents();
    String contribute(AgentContext context);
    int priority(); // ordering
}
```

### 4. Previous Context Mapping
Previous context is nullable and contains:
- Previous output (serialized)
- Error/failure information if any
- Retry count
- Discrete prior contexts per agent (for reroutes)

## Files to Modify/Create Summary

### New Files (14)
1. `ContextId.java` - Value object for context IDs
2. `UpstreamContext.java` - Generic typed upstream context
3. `PreviousContext.java` - Previous attempt context mixin + per-agent implementations
4. `PromptContributor.java` - Contributor interface
5. `PromptAssembly.java` - Prompt assembly service
6. `PromptContributorRegistry.java` - Contributor registry
7. `EpisodicMemoryPromptContributor.java` - First contributor implementation
8. `DelegationTemplate.java` - Orchestrator output template
9. `ConsolidationTemplate.java` - Collector output template
10. `DiscoveryReport.java` - Enhanced discovery output
11. `PlanningTicket.java` - Enhanced planning output
12. `PromptContributorConfig.java` - Spring configuration
13. `ContextIdService.java` - Context ID generation

### Files to Modify (3)
1. `AgentModels.java` - Add context ID fields, upstream context types
2. `BlackboardHistory.java` - Integrate with prompt assembly
3. `AgentInterfaces.java` - Use new templates and prompt assembly

## Dependencies Between Components

```
ContextId ←── UpstreamContext ←── AgentModels (all request/result types)
                    ↑
              PreviousContext

PromptContributor ←── PromptContributorRegistry ←── PromptAssembly
        ↑
EpisodicMemoryPromptContributor

DelegationTemplate ←── AgentInterfaces (orchestrators)
ConsolidationTemplate ←── AgentInterfaces (collectors)
DiscoveryReport ←── AgentModels.DiscoveryAgentResult
PlanningTicket ←── AgentModels.PlanningAgentResult
```
