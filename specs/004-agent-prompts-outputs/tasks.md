---

description: "Task list for 004-agent-prompts-outputs implementation"
---

# Tasks: 004-agent-prompts-outputs

**Input**: Design documents from `/specs/004-agent-prompts-outputs/`

**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, contracts/, quickstart.md

**Tests**: Not requested in the spec; skip new test tasks for now (existing test_graph coverage assumed).

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [X] T001 Create package directories for new prompt/template/context classes under `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/{prompt,template,agent}/`
- [X] T002 Create package directories for config/service classes under `multi_agent_ide/src/main/java/com/hayden/multiagentide/{config,service}/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

- [X] T003 Implement context ID value object in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/ContextId.java`
- [X] T004 Implement upstream context sealed interface + curated context types in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/UpstreamContext.java`
- [X] T005 Implement previous context mixin + per-agent implementations in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/PreviousContext.java`
- [X] T006 Implement prompt context record (with blackboard history) in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/prompt/PromptContext.java`
- [X] T007 Implement consolidation template mixin + curation mixin + consolidation summary in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/template/ConsolidationTemplate.java`
- [X] T008 Implement delegation template + agent assignment + context selection in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/template/DelegationTemplate.java`

**Checkpoint**: Foundation ready - user story implementation can now begin in sequence

---

## Phase 3: User Story 1 - Prompt Contributor Framework (Priority: P1) 🎯 MVP

**Goal**: Modular prompt contributor framework with ordered, agent-specific contributions

**Independent Test**: Register a mock contributor and confirm it appears in assembled prompts

### Implementation for User Story 1

- [X] T009 [US1] Create prompt contributor interface in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/prompt/PromptContributor.java`
- [X] T010 [US1] Implement contributor registry in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/prompt/PromptContributorRegistry.java`
- [X] T011 [US1] Implement prompt assembly with getContributors(PromptContext) in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/prompt/PromptAssembly.java`
- [ ] T012 [US1] Integrate prompt assembly into blackboard history flow in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/BlackboardHistory.java`
- [X] T013 [US1] Wire prompt assembly into agent prompts in `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java`
- [X] T014 [US1] Add Spring contributor config in `multi_agent_ide/src/main/java/com/hayden/multiagentide/config/PromptContributorConfig.java`

---

## Phase 4: User Story 2 - Episodic Memory Tool Prompt Contributor (Priority: P1)

**Goal**: Episodic memory prompt contributor with retain/reflect/recall guidance

**Independent Test**: Assembled prompts include episodic memory guidance sections

### Implementation for User Story 2

- [X] T015 [US2] Implement episodic memory prompt contributor in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/prompt/EpisodicMemoryPromptContributor.java`
- [X] T016 [US2] Register episodic memory contributor in `multi_agent_ide/src/main/java/com/hayden/multiagentide/config/PromptContributorConfig.java`

---

## Phase 5: User Story 3 - Typed, Curated Upstream Context with IDs (Priority: P1)

**Goal**: Typed curated upstream context with traceable IDs

**Independent Test**: Requests include curated upstream context and IDs end-to-end

### Implementation for User Story 3

- [X] T017 [US3] Add context ID service in `multi_agent_ide/src/main/java/com/hayden/multiagentide/service/ContextIdService.java`
- [X] T018 [US3] Add contextId + upstreamContext to request models in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AgentModels.java`
- [ ] T019 [US3] Update agent interface wiring to pass curated contexts in `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java`

---

## Phase 6: User Story 4 - Previous Context for Reruns (Priority: P1)

**Goal**: Per-agent previous context support for reruns and reroutes

**Independent Test**: Rerun prompts include previous context fields and prior curations

### Implementation for User Story 4

- [X] T020 [US4] Add previousContext to request models in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AgentModels.java`
- [ ] T021 [US4] Populate previous context on reroutes in `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java`

---

## Phase 7: User Story 5 - Orchestrator Standardized Delegation Template (Priority: P1)

**Goal**: Orchestrator outputs use the delegation template with context selection

**Independent Test**: Orchestrator outputs conform to the delegation template schema

### Implementation for User Story 5

- [X] T022 [US5] Add delegation result types to `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AgentModels.java`
- [ ] T023 [US5] Update orchestrator outputs to use DelegationTemplate in `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java`

---

## Phase 8: User Story 6 - Collector Standardized Consolidation Template (Priority: P1)

**Goal**: Collector outputs implement consolidation template mixin with discrete curations

**Independent Test**: Collector outputs expose consolidation metadata + derived curations list

### Implementation for User Story 6

- [X] T024 [US6] Update collector result models to implement ConsolidationTemplate in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AgentModels.java`
- [ ] T025 [US6] Update collector outputs to populate consolidation fields in `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java`

---

## Phase 9: User Story 7 - Discovery Agent Reports with References and Links (Priority: P1)

**Goal**: Discovery agent outputs structured reports with references, tags, and diagrams

**Independent Test**: Discovery reports contain references, links, tags, queries, and diagrams

### Implementation for User Story 7

- [X] T026 [US7] Implement discovery report entities in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/template/DiscoveryReport.java`
- [X] T027 [US7] Update discovery agent result models in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AgentModels.java`
- [ ] T028 [US7] Update discovery agent output wiring in `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java`

---

## Phase 10: User Story 8 - Discovery Collector with Recommendations and Query Findings (Priority: P1)

**Goal**: Discovery collector merges reports into code map + recommendations + query findings

**Independent Test**: Discovery collector output includes recommendations and query findings

### Implementation for User Story 8

- [X] T029 [US8] Implement discovery collector models (CodeMap, Recommendation, QueryFindings, DiscoveryCuration) in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AgentModels.java`
- [ ] T030 [US8] Update discovery collector output to populate curated context in `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java`

---

## Phase 11: User Story 9 - Discovery Orchestrator Delegation (Priority: P1)

**Goal**: Discovery orchestrator uses delegation template for subdomain routing

**Independent Test**: Discovery orchestrator outputs delegation template with subdomain assignments

### Implementation for User Story 9

- [ ] T031 [US9] Update discovery orchestrator delegation output in `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java`

---

## Phase 12: User Story 16 - Orchestrator Collector with Full Context Chain (Priority: P1)

**Goal**: Orchestrator collector consolidates downstream results with full context chain

**Independent Test**: Orchestrator collector output contains full context chain and decision rationale

### Implementation for User Story 16

- [ ] T032 [US16] Implement orchestrator collector result model in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AgentModels.java`
- [ ] T033 [US16] Update orchestrator collector consolidation logic in `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java`

---

## Phase 13: User Story 10 - Planning Orchestrator with Curated Discovery Context (Priority: P2)

**Goal**: Planning orchestrator receives and passes curated discovery context

**Independent Test**: Planning orchestrator includes curated discovery context IDs in delegations

### Implementation for User Story 10

- [ ] T034 [US10] Update planning orchestrator context models in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AgentModels.java`
- [ ] T035 [US10] Update planning orchestrator delegation wiring in `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java`

---

## Phase 14: User Story 11 - Planning Agent with Curated Discovery Context (Priority: P2)

**Goal**: Planning agents use curated discovery context to generate tickets with links

**Independent Test**: Planning tickets link back to discovery collector result IDs

### Implementation for User Story 11

- [ ] T036 [US11] Update planning agent models (PlanningTicket, DiscoveryLink) in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/template/PlanningTicket.java`
- [ ] T037 [US11] Update planning agent request/result models in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AgentModels.java`
- [ ] T038 [US11] Update planning agent output wiring in `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java`

---

## Phase 15: User Story 12 - Planning Collector with Typed Inputs (Priority: P2)

**Goal**: Planning collector consolidates tickets with dependency graph and curated planning context

**Independent Test**: Planning collector output includes dependency graph and curated planning context

### Implementation for User Story 12

- [ ] T039 [US12] Implement planning collector models (PlanningCollectorResult, TicketDependency, PlanningCuration) in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AgentModels.java`
- [ ] T040 [US12] Update planning collector output wiring in `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java`

---

## Phase 16: User Story 13 - Ticket Orchestrator with Curated Discovery and Planning Context (Priority: P2)

**Goal**: Ticket orchestrator receives curated discovery + planning contexts for delegation

**Independent Test**: Ticket orchestrator delegations include both curated context IDs

### Implementation for User Story 13

- [ ] T041 [US13] Update ticket orchestrator context models in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AgentModels.java`
- [ ] T042 [US13] Update ticket orchestrator delegation wiring in `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java`

---

## Phase 17: User Story 14 - Ticket Agent with Curated Upstream Context (Priority: P2)

**Goal**: Ticket agents implement work with full curated context references

**Independent Test**: Ticket agent outputs include implementation summary and context links

### Implementation for User Story 14

- [ ] T043 [US14] Implement ticket agent result models in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AgentModels.java`
- [ ] T044 [US14] Update ticket agent request/result wiring in `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java`

---

## Phase 18: User Story 15 - Ticket Collector with Typed Inputs (Priority: P2)

**Goal**: Ticket collector consolidates implementations with curated ticket context

**Independent Test**: Ticket collector output includes completion status and follow-ups

### Implementation for User Story 15

- [ ] T045 [US15] Implement ticket collector models (TicketCollectorResult, TicketCuration) in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AgentModels.java`
- [ ] T046 [US15] Update ticket collector output wiring in `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java`

---

## Phase 19: User Story 17 - Review Agent with Context References (Priority: P2)

**Goal**: Review agent produces evaluation outputs with context references

**Independent Test**: Review outputs link to reviewed context IDs

### Implementation for User Story 17

- [ ] T047 [US17] Implement review evaluation model in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AgentModels.java`
- [ ] T048 [US17] Update review agent wiring in `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java`

---

## Phase 20: User Story 18 - Merger Agent with Context References (Priority: P3)

**Goal**: Merger agent produces merge validation outputs with context references

**Independent Test**: Merger outputs include conflict details and resolution guidance

### Implementation for User Story 18

- [ ] T049 [US18] Implement merger validation model in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AgentModels.java`
- [ ] T050 [US18] Update merger agent wiring in `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java`

---

## Phase 21: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T051 Verify no new test_graph work is needed (existing features cover this spec) in `test_graph/src/test/resources/features/`
- [ ] T052 Update quickstart examples if any API signatures shifted during implementation in `specs/004-agent-prompts-outputs/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **US1-US9, US16 (P1)**: Must complete before P2/P3 stories
- **US10-US15, US17 (P2)**: Depends on P1 completion
- **US18 (P3)**: Depends on P1/P2 completion

### Parallel Opportunities

- Within a phase, tasks touching different files may be parallelized if there are no dependencies
- Most AgentModels.java and AgentInterfaces.java tasks should remain sequential to avoid merge conflicts

---

## Implementation Strategy

- **MVP**: Complete US1 (prompt contributor framework) first
- **Incremental Delivery**: Implement P1 stories in order, then P2, then P3
- **Integration Check**: After each collector/orchestrator update, verify curated contexts still flow correctly
