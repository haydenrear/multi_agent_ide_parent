# Implementation Plan: Worktree Sandbox for Agent File Operations

**Branch**: `009-worktree-sandbox` | **Date**: 2026-02-01 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/009-worktree-sandbox/spec.md`

## Summary

Implement worktree sandboxing for agent file operations by propagating worktree context through request decorators, instructing ticket orchestrators to create per-ticket worktrees, and enforcing file read/write/edit boundaries via header-based lookup in AcpTooling and provider-specific sandbox translation in AcpChatModel.

## Technical Context

**Language/Version**: Java 21 (some Kotlin in ACP module)
**Primary Dependencies**: Spring Boot 3.x, LangChain4j-Agentic, Embabel Agent Framework, Lombok, Jackson
**Storage**: GraphRepository + WorktreeRepository (existing)
**Testing**: JUnit 5, test_graph framework
**Target Platform**: JVM server (Linux/macOS)
**Project Type**: Multi-module Gradle (Git submodules)
**Performance Goals**: No measurable latency added to file ops (<5ms overhead)
**Constraints**: Backward compatible; must not break decorator chains
**Scale/Scope**: All agent requests in worktree-enabled workflows

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Computation Graph with State Machine | PASS | Uses existing event-driven architecture and node metadata | 
| II. Three-Phase Workflow Pattern | PASS | Propagates context across existing phases | 
| III. Orchestrator, Work, Collector Pattern | PASS | Decorator + prompt contributor align with node roles | 
| IV. Event-Driven Node Lifecycle | PASS | Worktree context flows via existing request lifecycle | 
| V. Artifact-Driven Context Propagation | PASS | Context stored on nodes/requests; no implicit state | 
| VI. Worktree Isolation & Feature Branching | PASS | Feature directly enforces isolation | 
| VII. Tool Integration & Agent Capabilities | PASS | Adds sandbox enforcement to existing tooling | 
| VIII. Human-in-the-Loop Review Gates | PASS | No impact on review gates | 

**Gate Result**: PASS - All principles satisfied.

**Post-Design Check (Phase 1)**: PASS - No changes introduced that violate constitution principles.

## Project Structure

### Documentation (this feature)

```text
specs/009-worktree-sandbox/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
└── tasks.md

test_graph/src/test/resources/features/worktree-sandbox/   # new or updated feature files
```

### Source Code (repository root)

```text
multi_agent_ide_java_parent/
├── multi_agent_ide/
│   └── src/main/java/com/hayden/multiagentide/
│       ├── agent/decorator/
│       │   ├── WorktreeContextRequestDecorator.java      # NEW
│       │   └── RequestContextRepositoryDecorator.java    # NEW
│       ├── agent/AgentInterfaces.java                    # MODIFY
│       ├── prompt/
│       │   └── TicketOrchestratorWorktreePromptContributor.java  # NEW
│       └── repository/
│           └── InMemoryRequestContextRepository.java     # NEW
│
├── multi_agent_ide_lib/
│   └── src/main/java/com/hayden/multiagentidelib/
│       ├── agent/
│       │   ├── AgentModels.java                          # MODIFY
│       │   └── AcpTooling.java                           # MODIFY
│       ├── model/worktree/
│       │   ├── WorktreeSandbox.java                      # NEW
│       │   └── SandboxValidationResult.java              # NEW
│       ├── repository/
│       │   ├── RequestContext.java                       # NEW
│       │   └── RequestContextRepository.java             # NEW
│       └── sandbox/
│           ├── SandboxTranslationStrategy.java           # NEW
│           ├── SandboxTranslationRegistry.java           # NEW
│           ├── ClaudeCodeSandboxStrategy.java            # NEW
│           ├── CodexSandboxStrategy.java                 # NEW
│           └── GooseSandboxStrategy.java                 # NEW
│
└── acp-cdc-ai/
    └── src/main/kotlin/com/hayden/acp_cdc_ai/acp/
        └── AcpChatModel.kt                               # MODIFY
```

**Structure Decision**: Keep decorators and prompt contributors in `multi_agent_ide`, shared context/sandbox utilities in `multi_agent_ide_lib`, and ACP command translation in `acp-cdc-ai` to align with existing module responsibilities.

## Complexity Tracking

> No constitution violations requiring justification.

---

## Extension: Worktree Merge Flow Testing & Implementation

**Context**: The WorktreeContextRequestDecorator has been implemented and is now setting WorktreeSandboxContext on all requests. This extension addresses:
1. Testing that worktree context flows correctly through the application
2. Verifying submodule worktree handling
3. Implementing merge logic after dispatch agents (Discovery, Planning, Ticket) return results
4. Ensuring merges respect all submodules in addition to the main worktree

### Architecture Overview

The merge flow uses a **two-phase decorator pattern** with two distinct decorator interfaces:

1. **Child Agent Phase** (DispatchedAgentResultDecorator on AgentResult):
   - Interface: `DispatchedAgentResultDecorator extends ResultDecorator`
   - Decorates: `TicketAgentResult`, `PlanningAgentResult`, `DiscoveryAgentResult`
   - After a child agent completes work, decorator attempts to merge FROM trunk TO the agent's worktree
   - Adds MergeDescriptor to the result describing success/conflict
   - Simple: just try to merge, report result

2. **Parent Dispatch Phase** (ResultsRequestDecorator on ResultsRequest):
   - Interface: `ResultsRequestDecorator` (NEW)
   - Model: `ResultsRequest` interface (NEW) - implemented by `TicketAgentResults`, `PlanningAgentResults`, `DiscoveryAgentResults`
   - Decoration point: After collecting child results, before passing to routing LLM
   ```java
   var ticketAgentResults = AgentModels.TicketAgentResults.builder()
           .ticketAgentResults(ticketResults)
           .build();
   // NEW: Decorate here with ResultsRequestDecorator
   ticketAgentResults = AgentInterfaces.decorateResultsRequest(ticketAgentResults, context, resultsRequestDecorators, ...);
   ```
   - Iterates through each child's worktree, attempting to merge FROM child worktree BACK to trunk
   - For submodules: merge all submodules first, then main worktree
   - Builds MergeAggregation with three lists: merged, pending, conflicted
   - Stops on first conflict, passes context to routing LLM
   - The LLM decides how to handle (MergerRequest, MergerInterruptRequest, retry, etc.)

### Merge Flow Requirements

#### Worktree Context Flow Verification
- **MF-001**: System MUST verify that WorktreeSandboxContext flows from WorktreeContextRequestDecorator through all request types
- **MF-002**: System MUST verify that OrchestratorNode worktree information is correctly resolved from GraphRepository
- **MF-003**: System MUST verify that child requests inherit worktree context from parent requests
- **MF-004**: System MUST verify that TicketAgentRequests, DiscoveryAgentRequests, and PlanningAgentRequests receive distinct per-agent worktrees via attachWorktreesTo* methods

#### Submodule Worktree Handling
- **MF-005**: System MUST verify that WorktreeSandboxContext correctly includes both MainWorktreeContext and List<SubmoduleWorktreeContext>
- **MF-006**: System MUST verify that submodule worktrees are created for all submodules in the repository
- **MF-007**: System MUST verify that submodule worktree IDs are tracked in MainWorktreeContext.submoduleWorktreeIds
- **MF-008**: System MUST verify that SubmoduleWorktreeContext.mainWorktreeId correctly references the parent main worktree

#### Child Agent Merge (Trunk → Child Worktree)
- **MF-009**: DispatchedAgentResultDecorator MUST decorate TicketAgentResult, PlanningAgentResult, and DiscoveryAgentResult
- **MF-010**: For each AgentResult, decorator MUST attempt to merge FROM parent/trunk worktree TO the agent's worktree
- **MF-011**: Decorator MUST merge all submodule worktrees first, then main worktree
- **MF-012**: Decorator MUST add MergeDescriptor to the result with merge status and any conflict details
- **MF-013**: MergeDescriptor MUST include: mergeDirection (TRUNK_TO_CHILD), success flag, conflict files list, submodule merge results

#### Parent Dispatch Merge (Child Worktrees → Trunk)
- **MF-014**: System MUST define ResultsRequest interface implemented by TicketAgentResults, PlanningAgentResults, and DiscoveryAgentResults
- **MF-015**: System MUST define ResultsRequestDecorator interface for decorating ResultsRequest types
- **MF-016**: ResultsRequestDecorator MUST be called after child results are collected, before passing to routing LLM
- **MF-017**: WorktreeMergeResultsDecorator MUST iterate through each child agent's worktree context
- **MF-018**: For each child worktree, decorator MUST merge all submodules first, then main worktree
- **MF-019**: Decorator MUST build MergeAggregation with lists: merged (completed), pending (not yet attempted), conflicted (first conflict)
- **MF-020**: Decorator MUST stop processing on first conflict and include conflict details in MergeAggregation
- **MF-021**: MergeAggregation MUST be added to the ResultsRequest and passed to the routing LLM
- **MF-022**: The routing LLM (not the decorator) decides how to handle conflicts (MergerRequest, MergerInterruptRequest, etc.)

#### GitWorktreeService Submodule Support
- **MF-023**: GitWorktreeService MUST support merging submodule worktrees with proper ordering
- **MF-024**: GitWorktreeService MUST update submodule pointers in parent after successful submodule merge
- **MF-025**: GitWorktreeService MUST provide rollback capability for failed multi-submodule merges

### Testing Strategy

#### Unit Tests - WorktreeContextRequestDecorator
- Test decorate() for all request types (OrchestratorRequest, TicketAgentRequest, etc.)
- Test resolveFromOrchestratorNode() with OrchestratorNode and HasWorktree nodes
- Test buildSandboxContext() with and without submodules
- Test worktree context inheritance from parent request

#### Unit Tests - DispatchedAgentResultDecorator (WorktreeMergeResultDecorator)
- Test decorate(TicketAgentResult) performs trunk→child merge and adds MergeDescriptor
- Test decorate(PlanningAgentResult) performs trunk→child merge and adds MergeDescriptor
- Test decorate(DiscoveryAgentResult) performs trunk→child merge and adds MergeDescriptor
- Test submodules merge before main worktree
- Test MergeDescriptor contains all submodule results

#### Unit Tests - ResultsRequestDecorator (WorktreeMergeResultsDecorator)
- Test decorate(TicketAgentResults) iterates child worktrees, builds MergeAggregation
- Test decorate(PlanningAgentResults) iterates child worktrees, builds MergeAggregation
- Test decorate(DiscoveryAgentResults) iterates child worktrees, builds MergeAggregation
- Test merge stops on first conflict and includes pending list
- Test submodules merge before main worktree for each child
- Test MergeAggregation.merged + pending + conflicted equals total children

#### Unit Tests - GitWorktreeService
- Test mergeWorktrees() for main worktrees
- Test mergeWorktrees() for submodule worktrees
- Test mergeSubmodulesFirst() merges all submodules before main
- Test updateSubmodulePointer() after successful submodule merge
- Test rollback capability for failed multi-submodule merges
- Test merge conflict detection and MergeResult population

#### Unit Tests - WorkflowGraphService
- Test integration with DispatchedAgentResultDecorator
- Test merge context flows to routing LLM
- Test node state transitions based on merge results

#### Integration Tests (test_graph)
- **Feature**: worktree-context-flow.feature
  - Scenario: Worktree context propagates from OrchestratorNode to child requests
  - Scenario: Submodule worktrees are included in WorktreeSandboxContext
  - Scenario: TicketAgentRequests receive per-ticket worktrees via attachWorktreesToTicketRequests
  
- **Feature**: worktree-merge-child.feature
  - Scenario: TicketAgentResult decorated with MergeDescriptor after trunk→child merge
  - Scenario: PlanningAgentResult decorated with MergeDescriptor after trunk→child merge
  - Scenario: DiscoveryAgentResult decorated with MergeDescriptor after trunk→child merge
  - Scenario: Child merge includes submodule results before main worktree result

- **Feature**: worktree-merge-parent.feature
  - Scenario: TicketAgentResults contains MergeAggregation after child→trunk merges
  - Scenario: MergeAggregation has merged/pending/conflicted lists
  - Scenario: Merge stops on first conflict, remaining worktrees in pending list
  - Scenario: Submodule worktrees merge before main worktree for each child
  - Scenario: Routing LLM receives MergeAggregation in context

### Implementation Components

#### New Data Models
```text
multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/model/merge/
├── MergeDescriptor.java                                  # NEW - single merge operation result
│   ├── mergeDirection: MergeDirection (TRUNK_TO_CHILD, CHILD_TO_TRUNK)
│   ├── successful: boolean
│   ├── conflictFiles: List<String>
│   ├── submoduleMergeResults: List<SubmoduleMergeResult>
│   └── mainWorktreeMergeResult: MergeResult
│
├── SubmoduleMergeResult.java                             # NEW - per-submodule merge result
│   ├── submoduleName: String
│   ├── mergeResult: MergeResult
│   └── pointerUpdated: boolean
│
├── MergeAggregation.java                                 # NEW - aggregated merge state for routing LLM
│   ├── merged: List<AgentMergeStatus>                    # successfully merged
│   ├── pending: List<AgentMergeStatus>                   # not yet attempted
│   ├── conflicted: AgentMergeStatus                      # first conflict (nullable)
│   └── allSuccessful(): boolean
│
└── AgentMergeStatus.java                                 # NEW - per-agent merge state
    ├── agentResultId: String
    ├── worktreeContext: WorktreeSandboxContext
    └── mergeDescriptor: MergeDescriptor
```

#### New Interfaces
```text
multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AgentModels.java
├── ResultsRequest                                        # NEW interface
│   ├── worktreeContext(): WorktreeSandboxContext
│   ├── mergeAggregation(): MergeAggregation
│   └── withMergeAggregation(MergeAggregation): ResultsRequest
│
├── TicketAgentResults   implements ResultsRequest        # MODIFY
├── PlanningAgentResults implements ResultsRequest        # MODIFY
└── DiscoveryAgentResults implements ResultsRequest       # MODIFY
```

#### Modified Agent Results (add MergeDescriptor field)
```text
multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AgentModels.java
├── TicketAgentResult    → add mergeDescriptor: MergeDescriptor field
├── PlanningAgentResult  → add mergeDescriptor: MergeDescriptor field
└── DiscoveryAgentResult → add mergeDescriptor: MergeDescriptor field
```

#### New Decorator Interfaces and Implementations
```text
multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/decorator/
├── DispatchedAgentResultDecorator.java                   # EXISTS - interface for AgentResult
├── WorktreeMergeResultDecorator.java                     # NEW - implements DispatchedAgentResultDecorator
│   ├── decorate(TicketAgentResult) → merge trunk→child, add MergeDescriptor
│   ├── decorate(PlanningAgentResult) → merge trunk→child, add MergeDescriptor
│   └── decorate(DiscoveryAgentResult) → merge trunk→child, add MergeDescriptor
│
├── ResultsRequestDecorator.java                          # NEW - interface for ResultsRequest
│   └── <T extends ResultsRequest> T decorate(T request, DecoratorContext context)
│
└── WorktreeMergeResultsDecorator.java                    # NEW - implements ResultsRequestDecorator
    ├── decorate(TicketAgentResults) → iterate children, merge child→trunk, build MergeAggregation
    ├── decorate(PlanningAgentResults) → iterate children, merge child→trunk, build MergeAggregation
    └── decorate(DiscoveryAgentResults) → iterate children, merge child→trunk, build MergeAggregation
```

#### AgentInterfaces Helper Method
```text
multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java
└── decorateResultsRequest(                               # NEW static method
        T resultsRequest,
        ActionContext context,
        List<ResultsRequestDecorator> decorators,
        String agentName,
        String action,
        String method,
        AgentRequest lastRequest
    ): T
```

#### Prompt Contributor Factory
```text
multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/prompt/contributor/
└── MergeAggregationPromptContributorFactory.java         # NEW - provides merge status to routing LLM
    ├── create(PromptContext) → List<PromptContributor>
    ├── isDispatchAgentType(AgentType) → only for TICKET/PLANNING/DISCOVERY_AGENT_DISPATCH
    └── extractMergeAggregation(PromptContext) → from ResultsRequest or metadata

└── MergeAggregationPromptContributor                     # NEW - inner record
    ├── name() → "MergeAggregationPromptContributor"
    ├── include(PromptContext) → true if aggregation exists
    ├── template() → markdown-formatted merge status with action guidance
    └── priority() → 500 (after core context, before decision prompts)
```

#### Modified Services
```text
multi_agent_ide/src/main/java/com/hayden/multiagentide/service/
└── GitWorktreeService.java                               # MODIFY
    ├── mergeSubmodulesFirst(childContext, parentContext) # NEW - merges all submodules then main
    ├── mergeWithSubmoduleOrdering(sourceId, targetId)    # NEW - handles submodule order
    └── rollbackMerges(List<MergeResult>)                 # NEW - rollback on failure

multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/
└── WorkflowGraphService.java                             # MODIFY - ensure decorator chain runs
```

#### Test Files
```text
test_graph/src/test/resources/features/worktree-sandbox/
├── worktree-context-flow.feature                         # NEW
├── worktree-merge-child.feature                          # NEW
└── worktree-merge-parent.feature                         # NEW

multi_agent_ide/src/test/java/com/hayden/multiagentide/
├── decorator/WorktreeContextRequestDecoratorTest.java    # NEW
├── decorator/WorktreeMergeResultDecoratorTest.java       # NEW - DispatchedAgentResultDecorator tests
├── decorator/WorktreeMergeResultsDecoratorTest.java      # NEW - ResultsRequestDecorator tests
└── service/GitWorktreeServiceMergeTest.java              # NEW
```

### Merge Flow Sequence

#### Phase 1: Child Agent Completes Work (Trunk → Child Merge)

```text
1. TicketAgent completes work in its worktree
2. ranTicketAgentResult() receives TicketAgentResult
3. DispatchedAgentResultDecorator.decorate(TicketAgentResult) called
4. WorktreeMergeResultDecorator:
   a. Get child's WorktreeSandboxContext from result/request
   b. Get parent's WorktreeSandboxContext (trunk)
   c. For each submodule in child context:
      - GitWorktreeService.mergeWorktrees(trunk.submodule.id, child.submodule.id)
      - Collect SubmoduleMergeResult
   d. GitWorktreeService.mergeWorktrees(trunk.main.id, child.main.id)
   e. Build MergeDescriptor with direction=TRUNK_TO_CHILD
5. Return TicketAgentResult.toBuilder().mergeDescriptor(descriptor).build()
6. Result returns to dispatchTicketAgentRequests() with merge info
```

#### Phase 2: Parent Collects Results (Child → Trunk Merge)

```text
1. dispatchTicketAgentRequests() collects all TicketAgentResult into TicketAgentResults:
   
   var ticketAgentResults = AgentModels.TicketAgentResults.builder()
           .ticketAgentResults(ticketResults)
           .build();

2. Decoration point - call AgentInterfaces.decorateResultsRequest():
   
   ticketAgentResults = AgentInterfaces.decorateResultsRequest(
           ticketAgentResults,
           context,
           resultsRequestDecorators,  // injected List<ResultsRequestDecorator>
           multiAgentAgentName(),
           ACTION_TICKET_DISPATCH,
           METHOD_DISPATCH_TICKET_AGENT_REQUESTS,
           lastRequest
   );

3. ResultsRequestDecorator chain runs, including WorktreeMergeResultsDecorator

4. WorktreeMergeResultsDecorator.decorate(TicketAgentResults):
   a. Initialize: merged=[], pending=[all child results], conflicted=null
   b. For each child result in order:
      i.   Get child's WorktreeSandboxContext from child result's worktreeContext
      ii.  Get parent's WorktreeSandboxContext from lastRequest.worktreeContext() (dispatch agent's worktree)
      iii. For each submodule:
           - GitWorktreeService.mergeWorktrees(child.submodule.id, parent.submodule.id)
           - If conflict: set conflicted, break
           - GitWorktreeService.updateSubmodulePointer(parent.main.id, submoduleName)
      iv.  If no submodule conflicts:
           - GitWorktreeService.mergeWorktrees(child.main.id, parent.main.id)
           - If conflict: set conflicted, break
      v.   If success: move from pending to merged
      vi.  If conflict: stop iteration, remaining stay in pending
   c. Build MergeAggregation(merged, pending, conflicted)
   
5. Return ticketAgentResults.withMergeAggregation(aggregation)

6. Decorated ticketAgentResults passed to routing LLM via llmRunner.runWithTemplate()

7. PromptContext built with ticketAgentResults as currentRequest

8. MergeAggregationPromptContributorFactory detects ResultsRequest, extracts MergeAggregation

9. MergeAggregationPromptContributor adds merge status to prompt with action guidance

10. Routing LLM receives merge status via prompt contributor (not template variables)

11. LLM decides: if all merged → proceed; if conflict → MergerRequest/MergerInterruptRequest
```

### Edge Cases & Error Handling

- **EC-MF-001**: What happens if submodule merge succeeds but main worktree merge fails?
  - **Solution**: In Phase 1 (trunk→child), include partial success in MergeDescriptor. In Phase 2 (child→trunk), stop iteration, set as conflicted, include in MergeAggregation for LLM decision
  
- **EC-MF-002**: What happens if submodule pointer update fails after submodule merge?
  - **Solution**: Include pointer update failure in SubmoduleMergeResult.pointerUpdated=false, let routing LLM decide how to proceed
  
- **EC-MF-003**: What happens if child worktree has no changes?
  - **Solution**: Return MergeDescriptor with successful=true, no conflict files, empty commit hash (fast-forward or no-op)
  
- **EC-MF-004**: What happens if parent worktree was deleted?
  - **Solution**: MergeDescriptor.successful=false with appropriate error message, routing LLM can create interrupt to notify user
  
- **EC-MF-005**: What happens if submodule exists in child but not in parent?
  - **Solution**: Include in SubmoduleMergeResult with special status, let routing LLM decide (may require initialization)
  
- **EC-MF-006**: What happens if worktree context is null on result?
  - **Solution**: Skip merge decoration, return result unchanged, log debug message
  
- **EC-MF-007**: What happens if child agent result already has merge conflicts from Phase 1?
  - **Solution**: In Phase 2, still attempt child→trunk merge; include both Phase 1 and Phase 2 status in MergeAggregation

- **EC-MF-008**: How does the routing LLM access MergeAggregation?
  - **Solution**: MergeAggregationPromptContributorFactory creates MergeAggregationPromptContributor which adds merge status to the prompt via the standard prompt contributor chain; no template variables needed

### Success Metrics

- **SM-MF-001**: 100% of TicketAgentResult, PlanningAgentResult, DiscoveryAgentResult have MergeDescriptor after decoration
- **SM-MF-002**: 100% of TicketAgentResults, PlanningAgentResults, DiscoveryAgentResults have MergeAggregation after decoration
- **SM-MF-003**: 100% of submodule merges complete before main worktree merge in both phases
- **SM-MF-004**: MergeAggregation.merged + pending + conflicted equals total child results
- **SM-MF-005**: MergeAggregationPromptContributor is included in prompt for dispatch agent types with MergeAggregation
- **SM-MF-006**: Integration tests pass for all merge flow scenarios
- **SM-MF-007**: Unit tests cover all edge cases EC-MF-001 through EC-MF-008
