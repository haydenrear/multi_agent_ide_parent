# Multi-Agent IDE Roadmap

Last Updated: 2026-01-21

## Overview

This roadmap organizes all tickets into a phased execution plan with clear dependencies, priorities, and current states. The goal is to build a multi-agent orchestration system that supports full execution reconstructability, structured interrupt requests, and training data extraction for specialized models.

## Technology Stack

| Component | Technology |
|-----------|------------|
| **Backend** | Java 21 + Spring Boot 3.x |
| **Chat Model** | Spring AI ChatModel (NOT langchain4j) |
| **ACP Integration** | AcpChatModel (Kotlin) in utilitymodule - **ALREADY IMPLEMENTED** |
| **Agent Framework** | Embabel Agent Framework (@Agent, @Action, SomeOf) |
| **Tool Protocol** | MCP (Model Context Protocol) via Spring AI MCP |
| **Frontend** | React + Next.js + CopilotKit + ag-UI |
| **Testing** | Cucumber/Gherkin BDD in test_graph |

---

## Current State Summary

| Category | Count | Status |
|----------|-------|--------|
| **Total Tickets** | 27 (multi_agent_ide) + 3 (commit-diff-context) | - |
| **Completed** | 1 | acp.md (AcpChatModel implemented in utilitymodule) |
| **In Progress** | 2 | agent-model-prompt-results.md, goap-embabel.md |
| **Pending** | 24 | Awaiting dependencies or prioritization |
| **Blocked** | 0 | - |

**Current Branch**: `004-agent-prompts-outputs` - Focused on agent prompts, structured outputs, and artifact model.

---

## Dependency Graph

```
                              ┌─────────────────┐
                              │   acp.md        │ (ChatModel for Codex) ✓ COMPLETE
                              └────────┬────────┘
                                       │
              ┌────────────────────────┼────────────────────────┐
              │                        │                        │
              ▼                        ▼                        ▼
    ┌─────────────────┐    ┌─────────────────────┐    ┌─────────────────┐
    │  unit-tests.md  │    │ agent-model-prompt- │    │ orchestrator-   │
    │  (unblocked)    │    │ results.md [ACTIVE] │    │ onboard.md      │
    └────────┬────────┘    └──────────┬──────────┘    └─────────────────┘
             │                        │                        │
             ▼                        │         ┌──────────────┘
    ┌─────────────────┐              │         │
    │ devops-int-     │              │         ▼
    │ tests.md        │              │    ┌─────────────────────┐
    └────────┬────────┘              │    │ commit-diff-context │
             │                        │    │ (episodic-mem.md,   │
             ├────────────────────────┤    │ code-search-lib.md) │
             │                        │    └─────────────────────┘
             ▼                        ▼
    ┌─────────────────┐    ┌─────────────────────┐
    │   tg-mb.md      │    │   context-id.md     │
    └─────────────────┘    └──────────┬──────────┘
                                      │
              ┌───────────────────────┼───────────────────────┐
              │                       │                       │
              ▼                       ▼                       ▼
    ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────────┐
    │  artifact.md    │    │ previous-       │    │ goap-embabel.md     │
    │                 │    │ contexts.md     │    │ [ACTIVE]            │
    └────────┬────────┘    └────────┬────────┘    └──────────┬──────────┘
             │                      │                        │
             ▼                      ▼                        ▼
    ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────────┐
    │ locking-        │    │ context-manager │    │  dispatch-agents.md │
    │ interrupts.md   │    │ -agent.md       │    └──────────┬──────────┘
    └────────┬────────┘    └─────────────────┘               │
             │                                               ▼
             ▼                                    ┌─────────────────────┐
    ┌─────────────────┐                          │   mcp_tools.md      │
    │ finish-         │                          └─────────────────────┘
    │ interrupts.md   │
    └────────┬────────┘
             │
             ▼
    ┌─────────────────┐    ┌─────────────────────┐
    │  permission.md  │    │ agent-interfaces-   │
    └─────────────────┘    │ tools.md            │
                           └──────────┬──────────┘
                                      │
              ┌───────────────────────┼───────────────────────┐
              │                       │                       │
              ▼                       ▼                       ▼
    ┌─────────────────┐    ┌─────────────────┐    ┌─────────────────────┐
    │    mem.md       │    │ agent-interfaces│    │ action-discovery.md │
    │ (Hindsight/Mem0)│    │ -tx.md          │    └─────────────────────┘
    └─────────────────┘    └────────┬────────┘
                                    │
                                    ▼
                           ┌─────────────────────┐
                           │ loop-builder-env.md │
                           └─────────────────────┘

    ┌─────────────────────────────────────────────────────────────────────┐
    │                            UI TRACK                                 │
    └─────────────────────────────────────────────────────────────────────┘
    
    devops-int-tests.md
             │
             ▼
    ┌─────────────────┐
    │  resumable-ui.md│
    └────────┬────────┘
             │
             ▼
    ┌─────────────────┐
    │     ui.md       │
    └────────┬────────┘
             │
             ▼
    ┌─────────────────┐
    │ ui-graph-view.md│
    └─────────────────┘
```

---

## Phase 1: Foundation (P1 - Critical Path)

These tickets are blocking everything else and must be completed first.

### 1.1 ACP ChatModel Implementation - COMPLETE
**Ticket**: `acp.md`  
**Status**: **COMPLETE**  
**Dependencies**: None  
**Priority**: CRITICAL

**Description**: Spring AI ChatModel implementation using the agentclientprotocol Kotlin SDK for Codex support.

**Location**: `utilitymodule/src/main/kotlin/com/hayden/utilitymodule/acp/AcpChatModel.kt`

**Completed Deliverables**:
- [x] AcpChatModel implementing `org.springframework.ai.chat.model.ChatModel` and `StreamingChatModel`
- [x] Integration with Kotlin SDK from agentclientprotocol/kotlin-sdk
- [x] Session management via AcpSessionManager
- [x] MCP server integration (agent-tools, gateway)
- [x] Permission gate integration for tool calls
- [x] Terminal operations (create, output, kill, wait)
- [x] File system operations (read, write)

**Remaining Work** (for spec 001-acp-chatmodel):
- [ ] End-to-end integration tests with/without submodules
- [ ] Revision cycle and failure handling tests
- [ ] Cucumber feature files in test_graph

---

### 1.2 Agent Model Prompts & Results (IN PROGRESS)
**Ticket**: `agent-model-prompt-results.md`  
**Status**: **IN PROGRESS** (active on branch 004-agent-prompts-outputs)  
**Dependencies**: None  
**Priority**: CRITICAL

**Description**: Standardize prompts and structured outputs for all agents. Enable training data extraction for smaller specialized models.

**Deliverables**:
- [ ] Standardized system prompts for all agents (Orchestrator, Discovery, Planning, Ticket, Review, Merge)
- [ ] Structured output schemas for each agent type
- [ ] Discovery dataset extraction pipeline (reports, references, queries)
- [ ] Planning result templates (tickets/reports)
- [ ] Onboarding flow for repository memory/episodes

**Sub-components**:
1. **Orchestrator Onboarding**: Check if repository exists in memory; if not, call commit-diff-context for episodic memory generation
2. **Discovery Results**: Code-map-like results with references for retrieval dataset
3. **Planning Results**: Structured ticket definitions

---

### 1.3 Context ID Implementation
**Ticket**: `context-id.md`  
**Status**: Pending  
**Dependencies**: `agent-model-prompt-results.md`  
**Priority**: CRITICAL

**Description**: Replace context ID with artifact ID per artifact.md spec. Use Embabel's property filtering to exclude fields from LLM schema.

**Deliverables**:
- [ ] Migrate from ContextId to ArtifactId
- [ ] Property filtering in PromptRunner API (withoutProperties)
- [ ] Artifact logic in utility module
- [ ] All models emit as artifact trees

---

### 1.4 Artifact Model Specification
**Ticket**: `artifact.md`  
**Status**: Draft (spec complete)  
**Dependencies**: `context-id.md`  
**Priority**: CRITICAL

**Description**: Canonical data model for execution capture with full reconstructability.

**Key Principles**:
- Hierarchical, time-sortable ArtifactKey
- Content identity via naive hashing
- Template static IDs (hierarchical: `tpl.agent.discovery.prompt.v1`)
- Semantic representations computed post-hoc

**Deliverables**:
- [ ] Execution artifact contract implementation
- [ ] ExecutionConfig artifact (model IDs, tool availability, loop parameters)
- [ ] Template artifact + capability pattern
- [ ] SemanticRepresentation attachment layer

---

## Phase 2: Agent Framework Modernization (P2)

### 2.1 GOAP with Embabel (IN PROGRESS)
**Ticket**: `goap-embabel.md`  
**Status**: **IN PROGRESS**  
**Dependencies**: `agent-model-prompt-results.md`  
**Priority**: HIGH

**Description**: Simplify routing using Embabel's GOAP pattern. Merge Orchestrator/Agent/Collector into single @Agent with multiple @Actions using SomeOf for conditional routing.

**Deliverables**:
- [ ] Convert each Orchestrator/Agent/Collector to @Agent with @Actions
- [ ] SomeOf interface for routing decisions (including InterruptType)
- [ ] Event emission on routing changes
- [ ] Update PromptProvider for all context types
- [ ] Remove routing from AgentEventListener (let Embabel handle it)

---

### 2.2 Agent Dispatch
**Ticket**: `dispatch-agents.md`  
**Status**: Pending  
**Dependencies**: `goap-embabel.md`  
**Priority**: HIGH

**Description**: Dispatch AgentType in dispatch actions properly.

**Deliverables**:
- [ ] AgentType dispatching pattern
- [ ] Type-safe dispatch actions

---

### 2.3 MCP Tools for Embabel
**Ticket**: `mcp_tools.md`  
**Status**: Pending  
**Dependencies**: `goap-embabel.md`  
**Priority**: HIGH

**Description**: Add MCP tool support for Embabel agents. Note: The project uses Spring AI MCP, not langchain4j.

**Deliverables**:
- [ ] MCP tool integration for Embabel agents
- [ ] Tool discovery and registration via Spring AI MCP

---

### 2.4 Agent Interface Tools
**Ticket**: `agent-interfaces-tools.md`  
**Status**: Pending  
**Dependencies**: `agent-model-prompt-results.md`  
**Priority**: HIGH

**Description**: Define specific tools for each agent type.

| Agent Type | Tools |
|------------|-------|
| Discovery | deep-wiki, codeweaver, ast-grep, memory tool, libs downloader (CtxFs), all ACP tools |
| Planning | ticketing search (CtxFs), all Discovery tools, spec-driven development skill |
| Ticket | (TBD based on implementation) |
| Orchestrator Collector | ticketing (CtxFs) for completion, all above |

---

### 2.5 Agent Interface Testing
**Ticket**: `agent-interfaces-tx.md`  
**Status**: Pending  
**Dependencies**: `agent-interfaces-tools.md`  
**Priority**: HIGH

**Description**: Fix routing issues and test interrupt flow.

**Deliverables**:
- [ ] Fix CollectorBranches routing to OrchestratorCollector
- [ ] Test interrupts in WorkflowAgentIntegrationTest
- [ ] Single agent workflow validation with expected responses

---

## Phase 3: Context & Interrupt System (P3)

### 3.1 Previous Context Management
**Ticket**: `previous-contexts.md`  
**Status**: Pending  
**Dependencies**: `context-id.md`  
**Priority**: MEDIUM

**Description**: Proper context flow across agent invocations.

**Deliverables**:
- [ ] toBuilder pattern in BlackboardHistory.registerAndHideInput
- [ ] RequestEnrichment class for context setup
- [ ] PreviousContextFactory for building typed previous contexts

---

### 3.2 Graph Repository Locking
**Ticket**: `locking-interrupts.md`  
**Status**: Pending  
**Dependencies**: `artifact.md`  
**Priority**: MEDIUM

**Description**: Add locking mechanism for graph repository to support interrupts safely.

---

### 3.3 Finish Interrupts
**Ticket**: `finish-interrupts.md`  
**Status**: Pending  
**Dependencies**: `locking-interrupts.md`  
**Priority**: MEDIUM

**Description**: Complete interrupt handling pipeline with structured responses.

**Key Innovation**: Typed interrupt requests with multiple-choice options (A, B, C, or write-in) for controller extraction dataset.

**Deliverables**:
- [ ] Translate interrupt results to output
- [ ] LLM call for routing after each interrupt
- [ ] Structured "human review" with implementation detail validation
- [ ] Multiple-choice response options

---

### 3.4 Permission & Timeout Management
**Ticket**: `permission.md`  
**Status**: Pending  
**Dependencies**: `finish-interrupts.md`  
**Priority**: MEDIUM

**Description**: Permission request/response infrastructure.

**Deliverables**:
- [ ] Permission request/response in SSE emitter/REST
- [ ] Cancellation timeout with background thread
- [ ] Interrupt events emit to parent node only (not separate nodes)
- [ ] Filter interrupt nodes from main graph view

---

### 3.5 Context Manager Agent
**Ticket**: `context-manager-agent.md`  
**Status**: Design  
**Dependencies**: `previous-contexts.md`  
**Priority**: MEDIUM

**Description**: Agent for context curation and reflection with memory tools.

**Use Cases**:
- Route when SomeOf has additional context
- Context summarization for long-running tasks
- Interactive context curation (expand, minimize, save to memory, recall)
- "Clear and rebuild" context with specific intention

---

## Phase 4: Repository Onboarding (P4)

This phase integrates the `commit-diff-context` module for repository onboarding, episodic memory generation, and code search capabilities.

### 4.1 Orchestrator Onboarding
**Ticket**: `orchestrator-onboard.md`  
**Status**: Pending  
**Dependencies**: `agent-model-prompt-results.md`  
**Priority**: MEDIUM

**Description**: Repository onboarding flow for episodic memory generation.

**Onboarding Flow** (from agent-model-prompt-results.md):
1. Orchestrator checks if repository exists in memory (episodes present)
2. If not, interrupt to ask user if onboarding should proceed
3. Check if embedding/code search tools are onboarded
4. If not, interrupt with list of available onboarding tools
5. Call commit-diff-context for rewrite and episodic memory generation

**Deliverables**:
- [ ] Memory existence check (episodes for repository)
- [ ] Embedding/code search tools onboarding check
- [ ] Interrupt flow for onboarding approval
- [ ] Integration with commit-diff-context tools

**Note**: Once onboarded, these tools never need refresh. Episodic memory is generated once from rewritten history; subsequent commits add memories as they're processed.

---

### 4.2 Episodic Memory (commit-diff-context)
**Ticket**: `commit-diff-context/tickets/episodic-mem.md`  
**Status**: Draft  
**Dependencies**: None (separate module)  
**Priority**: MEDIUM
**Location**: `/commit-diff-context/tickets/episodic-mem.md`

**Description**: MCP server for AST search on commits, blame tree retrieval.

**Three-Pass Pipeline**:
1. **Embed Phase**: Add repository/commits and embed them (commit diff hunks)
2. **Rewrite Phase**: Post-processing - rewrite git history, create context packs
   - For each commit diff, create ContextPack using research-level agents with tools
   - Save context pack to CommitDiffCluster for training
   - Agent can search using all embedded commits (separate pass)
3. **Re-embed Phase**: Embed new commits with AST context as separate repo

**History Rewriting Strategy**:
- Squash useless commits together
- Merge like commits to minimum number using embeddings
- Iterate through rewritten history and write episodes to memory

**Key Insight**: The rewritten history is not saved - only the episodes generated from it are persisted.

---

### 4.3 Code Search Library (commit-diff-context)
**Ticket**: `commit-diff-context/tickets/code-search-lib.md`  
**Status**: Draft  
**Dependencies**: None  
**Priority**: MEDIUM
**Location**: `/commit-diff-context/tickets/code-search-lib.md`

**Description**: Code search entities as MCP server.

**Capabilities**:
- Library retrieval from build files (requirements.txt, build.gradle)
- Source code retrieval by class/reference name

**Note**: Needs to be spun off into its own repository.

---

### 4.4 RAG History (commit-diff-context)
**Ticket**: `commit-diff-context/tickets/toolish-rag-history.md`  
**Status**: Draft  
**Dependencies**: `episodic-mem.md`  
**Priority**: LOW
**Location**: `/commit-diff-context/tickets/toolish-rag-history.md`

**Description**: Tool-based RAG with result expander over episodes.

---

## Phase 5: Execution Loops (P5)

### 5.1 Loop Builder Environment
**Ticket**: `loop-builder-env.md`  
**Status**: Pending  
**Dependencies**: `agent-interfaces-tx.md`  
**Priority**: MEDIUM

**Description**: Build loops as a2-ui component integrated with TicketDispatchSubagent.

**Loop Types**:
- **Outer loops** (expensive): debugging, integration testing, web testing
- **Inner loops** (cheap): unit testing, narrowing issues

**Deliverables**:
- [ ] Loop creation and templating
- [ ] Loop versioning and adjustments
- [ ] Loop execution tracking with metadata
- [ ] Thread-local ACP session extension with trace ID + versioned loop ID

---

## Phase 6: Testing Infrastructure (P6)

### 6.1 Unit Test Expansion
**Ticket**: `unit-tests.md`  
**Status**: **UNBLOCKED** (ready to start)  
**Dependencies**: `acp.md` ✓ (complete)  
**Priority**: MEDIUM

**Gaps to Address**:
- [ ] Git repo creation tests
- [ ] Git repo merging tests
- [ ] Submodule case tests
- [ ] Add message tests
- [ ] Interrupt tests
- [ ] Prune tests
- [ ] Add branch tests

---

### 6.2 DevOps Integration Tests
**Ticket**: `devops-int-tests.md`  
**Status**: Pending  
**Dependencies**: `unit-tests.md`  
**Priority**: MEDIUM

**Deliverables**:
- [ ] Docker support in multi_agent_ide build.gradle.kts
- [ ] Import Docker config into test_graph build.gradle.kts
- [ ] Step definitions implementation
- [ ] Computation graph node assertions
- [ ] Imposter files for test loads

---

### 6.3 Test Graph Mocking
**Ticket**: `tg-mb.md`  
**Status**: Pending  
**Dependencies**: `devops-int-tests.md`  
**Priority**: LOW

**Description**: Use Mountebank proxy to intercept OpenAI traffic and record as imposters.

---

## Phase 7: UI Implementation (P7)

### 7.1 Resumable UI Framework
**Ticket**: `resumable-ui.md`  
**Status**: Pending  
**Dependencies**: `devops-int-tests.md`  
**Priority**: MEDIUM

**Description**: ag-UI framework integration for agent event visualization.

---

### 7.2 Full UI Implementation
**Ticket**: `ui.md`  
**Status**: Pending  
**Dependencies**: `resumable-ui.md`  
**Priority**: MEDIUM

**Backend**:
- [ ] WebSocketEventAdapter mapping to ag-ui events
- [ ] AgUiSerdes serializer

**Frontend** (multi_agent_ide/fe):
- [ ] WebSocket connector and reader
- [ ] Computation graph in-memory building
- [ ] View plugins for different node types
- [ ] Interrupt/add message controls
- [ ] Node.js Gradle plugin for building to src/main/resources/static

---

### 7.3 Graph Visualization
**Ticket**: `ui-graph-view.md`  
**Status**: Pending  
**Dependencies**: `ui.md`  
**Priority**: LOW

**Description**: Graph view showing entire agent graph with clickable nodes linking to node tabs.

---

## Phase 8: Discovery & Actions (P8)

### 8.1 Action Discovery
**Ticket**: `action-discovery.md`  
**Status**: Pending  
**Dependencies**: `agent-interfaces-tools.md`  
**Priority**: LOW

**Description**: Dynamic action discovery for a2-ui buttons.

**Deliverables**:
- [ ] Action model for discrete actions
- [ ] Discovery endpoints at MCP startup
- [ ] Action schema mapping
- [ ] Replace a2uiRegistry with discovery-based registration
- [ ] ActionRequest with name, context, IDs

---

### 8.2 Memory Module
**Ticket**: `mem.md`  
**Status**: Pending  
**Dependencies**: `agent-interfaces-tools.md`  
**Priority**: LOW

**Options**: Hindsight or Mem0

---

## Utilities (Independent)

### Has Worktree Interface
**Ticket**: `has-worktree.md`  
**Status**: Pending  
**Dependencies**: None  
**Priority**: LOW

**Description**: All agents should use HasWorktree interface.

---

## Recommended Execution Order

### Immediate Focus (This Sprint)
1. **Complete** `agent-model-prompt-results.md` (in progress)
2. **Complete** `goap-embabel.md` (in progress)
3. **Start** `context-id.md` (unblocks artifact model)
4. **Start** `unit-tests.md` (unblocked by acp.md completion)

### Next Sprint
5. `artifact.md` - Implement execution artifact model
6. `dispatch-agents.md` - Agent dispatching
7. `mcp_tools.md` - MCP tool integration (Spring AI MCP)
8. `agent-interfaces-tools.md` - Define agent tools
9. `devops-int-tests.md` - Integration testing infrastructure

### Following Sprints
10. `previous-contexts.md` → `locking-interrupts.md` → `finish-interrupts.md` → `permission.md`
11. `orchestrator-onboard.md` + commit-diff-context tickets (episodic-mem.md, code-search-lib.md)
12. UI track: `resumable-ui.md` → `ui.md` → `ui-graph-view.md`

---

## Ticket Refinement Notes

### Tickets Needing More Detail
| Ticket | Missing Information |
|--------|---------------------|
| `resumable-ui.md` | Empty - needs acceptance criteria |
| `has-worktree.md` | Needs specific interface definition |
| `dispatch-agents.md` | Needs concrete examples |
| `mem.md` | Needs evaluation criteria for Hindsight vs Mem0 |
| `code-search-lib.md` | Needs to be spun off into separate repository |

### Tickets That Could Be Split
| Ticket | Suggested Split |
|--------|-----------------|
| `agent-model-prompt-results.md` | Separate into per-agent tickets |
| `ui.md` | Separate backend and frontend tickets |
| `artifact.md` | Separate spec from implementation |

### Tickets With Unclear Dependencies
| Ticket | Clarification Needed |
|--------|---------------------|
| `loop-builder-env.md` | Relationship to interrupt system |
| `context-manager-agent.md` | Trigger conditions for routing |

### Tickets to Update
| Ticket | Update Needed |
|--------|---------------|
| `acp.md` | Mark as complete - implementation exists at `utilitymodule/src/main/kotlin/com/hayden/utilitymodule/acp/AcpChatModel.kt` |
| `mcp_tools.md` | Update description - uses Spring AI MCP, not langchain4j |

---

## Metrics & Success Criteria

### Phase 1 Complete When:
- [x] Codex integration works via AcpChatModel (DONE - utilitymodule)
- [ ] All agents have standardized prompts
- [ ] Artifact IDs flow through system
- [ ] Execution artifacts fully reconstruct runs

### Phase 2-3 Complete When:
- [ ] Agents use Embabel GOAP routing
- [ ] Interrupts return structured multiple-choice options
- [ ] Context flows correctly across agent invocations

### Full System Complete When:
- [ ] Repository onboarding generates episodic memory
- [ ] Loops track execution metadata
- [ ] UI visualizes computation graph in real-time
- [ ] Training datasets extractable from executions
