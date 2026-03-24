# Implementation Plan: Inter-Agent Communication Topology & Conversational Structured Channels

**Branch**: `001-agent-topology` | **Date**: 2026-03-21 | **Spec**: [spec.md](spec.md)

**Input**: Feature specification from `/specs/001-agent-topology/spec.md`

## Summary

This feature has three major workstreams executed in dependency order:

1. **Interrupt Simplification (P0 pre-requisite)**: Remove `performReview`, `performMerge`, `ReviewRequest`, `MergerRequest` and all agent-initiated interrupt prompt contributors (including `RouteBackInterruptPromptContributorFactory`). Simplify interrupts to controller-initiated rerouting only via `SkipPropertyFilter` + victools schema generation + required `AgentType rerouteToAgentType`. Simplify route-back: remove `CollectorDecision` from all `*CollectorResult` types (collectors always route forward). Route-back is handled by the conversational topology controller conference (Story 7): when a collector confers with the controller, the controller can set `routeBack=true` to inject a route-back schema via AddMessage. This clears the path for structured conversations.

2. **Inter-Agent Communication Tools**: Add `list_agents`, `call_agent`, `call_controller` tools with configurable topology matrix, loop detection, event emission, and prompt contributor injection. `call_controller` uses the existing permission gate infrastructure.

3. **Conversational Topology**: Add prompt contributor factories that match blackboard history to inject justification conversation structure. Add living instructional documents in the controller skill's `conversational-topology/` directory with history tracking.

4. **Controller Conversation Polling**: Add a lightweight `/api/ui/activity-check` endpoint that returns only pending permissions, interrupts, and conversations. Update `poll.py` with a `--subscribe` mode that calls this lightweight endpoint every tick (0.5-1s) and only runs the full heavy `poll_once()` when activity is detected or the timer expires. Extend `poll_once()` to include conversation data. Add a new `conversations.py` script for ergonomic conversation management (list, view history, retrieve last messages, respond).

## Technical Context

**Language/Version**: Java 21 (primary), Kotlin (ACP/permission modules), Python (controller skill scripts)
**Primary Dependencies**: Spring Boot 3.x, LangChain4j-Agentic, Embabel Agent Framework (@Agent, @Action, PromptRunner), Lombok, Jackson, victools JSON schema generator
**Storage**: In-memory BlackboardHistory (per session), existing GraphRepository (persistent), existing WorktreeRepository
**Testing**: JUnit 5, Spring Boot integration tests, Mockito, AssertJ. test_graph integration tests deferred per spec.
**Target Platform**: JVM server (Spring Boot embedded Tomcat)
**Project Type**: Multi-module Gradle project with submodules
**Performance Goals**: list_agents < 1s, call_agent single-hop < 5s, failed calls < 3s error return (SC-001, SC-002, SC-007)
**Constraints**: No new persistent storage. Topology reconfigurable without restart (SC-008). Message budget default k=3.
**Scale/Scope**: Single workflow instance; multiple concurrent agent sessions per workflow.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Computation Graph with State Machine | PASS | New tools operate within existing session/process lifecycle. No new node types. Events follow existing emission pattern. |
| II. Three-Phase Workflow Pattern | PASS | No change to discovery → planning → implementation flow. Conversational topology overlays the existing phases. |
| III. Orchestrator, Work, Collector Node Pattern | PASS | Tools operate within existing agent sessions. No new node types or orchestration patterns. |
| IV. Event-Driven Node Lifecycle | PASS | New communication events (AgentCallEvent, AgentCallErrorEvent) follow existing event patterns. Published via existing EventBus. |
| V. Artifact-Driven Context Propagation | PASS | Conversational topology documents are skill-level markdown artifacts. Justification conversations propagate via BlackboardHistory + prompt contributors. |
| VI. Worktree Isolation | PASS | No changes to worktree management. WorktreeMergeConflictService and WorktreeAutoCommitService retained. |
| VII. Tool Integration | PASS | New tools (list_agents, call_agent, call_controller) registered via existing AcpTooling/@Tool pattern with @SetFromHeader(MCP_SESSION_HEADER). |
| VIII. Human-in-the-Loop Review Gates | JUSTIFIED DEVIATION | Removing ReviewAgent-initiated review (performReview action) and agent-initiated route-back review protocol (RouteBackInterruptPromptContributorFactory). Review gates are replaced by structured justification conversations via call_controller. Route-back decisions are handled by controller conference + AddMessage schema injection. The human can still initiate interrupts. The intent of review gates (quality verification before routing decisions) is preserved through conversational topology. |
| API Design: No Path Variables | PASS | No new REST endpoints with path variables. call_controller uses tool calls, not REST. |

## Project Structure

### Documentation (this feature)

```text
specs/001-agent-topology/
├── spec.md                                     # Feature specification
├── plan.md                                     # This file
├── research.md                                 # Phase 0 output
├── data-model.md                               # Phase 1 output
├── quickstart.md                               # Phase 1 output
├── contracts/                                  # Phase 1 output
└── checklists/
    └── requirements.md                         # Spec quality checklist
```

### Source Code (repository root)

```text
multi_agent_ide_java_parent/
├── multi_agent_ide/src/main/java/com/hayden/multiagentide/
│   ├── agent/
│   │   ├── AgentInterfaces.java                # MODIFY: remove performReview, performMerge, mapReview, mapMerge
│   │   ├── AcpTooling.java                     # MODIFY: add list_agents, call_agent, call_controller tools
│   │   └── WorkflowGraphService.java           # MODIFY: remove review/merger routing references
│   ├── agent/decorator/prompt/
│   │   └── FilterPropertiesDecorator.java      # MODIFY: remove ReviewRoute/MergerRoute, update interrupt schema logic
│   ├── config/
│   │   └── AgentModelMixin.java                # MODIFY: remove review/merger mixins if present
│   ├── controller/
│   │   ├── InterruptController.java            # MODIFY: inject prompt contributors for interrupt schema
│   │   ├── AgentConversationController.java    # NEW: dedicated controller for controller→agent responses + conversation queries
│   │   └── ActivityCheckController.java        # NEW: lightweight POST /api/ui/activity-check — returns only pending permissions, interrupts, and conversations for subscribe-mode polling
│   ├── prompt/contributor/
│   │   ├── InterruptPromptContributorFactory.java           # DELETE
│   │   ├── RouteBackInterruptPromptContributorFactory.java  # DELETE (replaced by controller conference + AddMessage route-back schema injection)
│   │   ├── AgentTopologyPromptContributorFactory.java       # NEW: inject communication targets + topology rules
│   │   └── JustificationPromptContributorFactory.java       # NEW: inject justification templates per agent type
│   ├── service/
│   │   ├── InterruptService.java               # KEEP (human interrupt resolution)
│   │   ├── WorktreeAutoCommitService.java      # KEEP (COMMIT_AGENT — LLM calls with own PromptContext)
│   │   ├── WorktreeMergeConflictService.java   # KEEP (MERGE_CONFLICT_AGENT — LLM calls with own PromptContext)
│   │   ├── SessionKeyResolutionService.java    # NEW: centralizes session key resolution (from AiFilterSessionResolver), message-to-session routing (from MultiAgentEmbabelConfig L208-260), self-call filtering, event enrichment with provenance markers
│   │   └── AgentCommunicationService.java      # NEW: topology enforcement, loop detection, session queries; delegates to SessionKeyResolutionService for self-call filtering
│   └── topology/
│       ├── CommunicationTopologyConfig.java    # NEW: configurable topology matrix
│       └── CallChainTracker.java               # NEW: loop detection and chain tracking
│
├── multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/
│   ├── agent/
│   │   ├── AgentModels.java                    # MODIFY: remove ReviewRequest, MergerRequest, ReviewRouting, MergerRouting; ADD AgentToControllerRequest, AgentToAgentRequest, ControllerToAgentRequest
│   │   ├── SkipPropertyFilter.java             # KEEP
│   │   ├── ReviewRoute.java                    # DELETE
│   │   └── MergerRoute.java                    # DELETE
│   ├── model/nodes/
│   │   └── GraphNode.java                      # MODIFY: add AgentToAgentConversationNode, AgentToControllerConversationNode, ControllerToAgentConversationNode to sealed permits list
│   ├── prompt/
│   │   └── PromptContext.java                  # MODIFY: add chatId() cases for three new AgentRequest subtypes (return targetAgentKey)
│   └── prompt/contributor/
│       ├── InterruptLoopBreakerPromptContributorFactory.java         # KEEP (remove ReviewRequest/MergerRequest cases)
│       └── OrchestratorRouteBackInterruptPromptContributorFactory.java # DELETE

skills/multi_agent_ide_skills/
├── SKILL_PARENT.md                             # MODIFY: add conversational topology brief reference
└── multi_agent_ide_controller/
    ├── SKILL.md                                # MODIFY: add full conversational topology instructions
    ├── executables/
    │   ├── poll.py                             # MODIFY: add --subscribe/--tick mode (lightweight activity-check loop, full poll on activity); extend poll_once with conversations section
    │   └── conversations.py                    # NEW: ergonomic conversation interface (list active, view history, retrieve last N messages, respond to pending call_controller requests)
    ├── conversational-topology/
    │   ├── reference.md                        # NEW: index of topology documents
    │   ├── checklist.md                        # NEW: general phase-gate review instructions
    │   ├── checklist-discovery-agent.md         # NEW: discovery agent review criteria
    │   ├── checklist-planning-agent.md          # NEW: planning agent review criteria
    │   └── checklist-ticket-agent.md            # NEW: ticket agent review criteria
    └── conversational-topology-history/
        └── reference.md                        # NEW: chronological change log
```

**Structure Decision**: All new Java code goes into existing modules (`multi_agent_ide` for app-level, `multi_agent_ide_lib` for shared models). New topology/communication classes go into a new `topology` package under the app module. Skill-level documents go into the existing controller skill directory structure.

**Controller Script Polling Design**: The subscribe-style polling in `poll.py` works as follows:
1. Controller LLM calls `python poll.py <nodeId> --subscribe 30 --tick 0.5`
2. Script enters a tight loop calling the lightweight `POST /api/ui/activity-check` endpoint every 0.5s (tick interval). This endpoint returns only pending permissions, interrupts, and conversation updates — no graph traversal, no propagation queries — so it's cheap to call at high frequency.
3. If the activity-check returns any pending items, the script calls the full `poll_once()` (which now includes a `═══ CONVERSATIONS ═══` section) and returns immediately.
4. If the max duration (30s) expires with no activity detected, the script calls the full `poll_once()` and returns anyway — the controller LLM is never blocked indefinitely.
5. This is specific to `poll.py`. The new `conversations.py` script provides a separate ergonomic interface for managing conversations directly (list active, view history, retrieve last N messages, respond to pending requests).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Removing ReviewAgent-initiated review (Constitution VIII) | Review gates are replaced by structured justification conversations that provide the same quality assurance with better traceability | Keeping both review gates and justification conversations would create redundant quality checks and confuse routing |
