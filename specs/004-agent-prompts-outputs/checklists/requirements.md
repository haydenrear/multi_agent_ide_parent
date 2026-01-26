# Specification Quality Checklist: Agent Prompts, Structured Outputs, Tool Prompt Contributors, and Structured Interrupt Requests

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-01-19
**Updated**: 2026-01-21
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Coverage Summary

### Design Philosophy
- [x] Model tuning focus documented
- [x] Versioned schemas for all outputs
- [x] Training data extraction as explicit goal
- [x] Typed context flow with traceability documented
- [x] Context ID structure defined

### Typed Context Flow
- [x] Context ID structure (workflow ID + agent type + sequence + timestamp)
- [x] Upstream context typing for all agents with dependencies
- [x] Previous context slot for rerun support
- [x] Full context chain reconstruction capability

### Context Dependencies by Agent

| Agent | Receives Typed Context From |
|-------|----------------------------|
| Discovery Orchestrator | Orchestrator |
| Discovery Agent | Discovery Orchestrator |
| Discovery Collector | Discovery Agent(s) |
| Planning Orchestrator | Discovery Collector |
| Planning Agent | Discovery Collector (via Planning Orchestrator) |
| Planning Collector | Planning Agent(s), Discovery Collector |
| Ticket Orchestrator | Discovery Collector, Planning Collector |
| Ticket Agent | Discovery Collector, Planning Collector, Planning Ticket |
| Ticket Collector | Ticket Agent(s), full upstream chain |
| Orchestrator Collector | All downstream collectors |
| Review Agent | Content being reviewed (typed) |
| Merger Agent | Content being merged (typed) |

### Orchestrators (Standardized Delegation Template)
- [x] Main Orchestrator
- [x] Discovery Orchestrator (passes subdomain context)
- [x] Planning Orchestrator (receives + passes discovery context)
- [x] Ticket Orchestrator (receives + passes discovery + planning context)

### Collectors (Standardized Consolidation Template)
- [x] Orchestrator Collector (full context chain)
- [x] Discovery Collector (recommendations + query-specific findings)
- [x] Planning Collector (preserves discovery links)
- [x] Ticket Collector (full upstream chain)

### Agents (Standardized Report Templates with Links/References)
- [x] Discovery Agent (reports with file references, cross-links, tags, queries, diagrams)
- [x] Planning Agent (tickets with IDs, tasks, dependencies, discovery links)
- [x] Ticket Agent (implementation outputs with full traceability)
- [x] Review Agent (evaluation outputs with content links)
- [x] Merger Agent (validation outputs with conflict links)

### Infrastructure
- [x] Prompt Contributor Framework
- [x] Episodic Memory Prompt Contributor (retain/reflect/recall)
- [x] Context ID generation and tracking
- [x] Previous context mapping for reruns

### Excluded (per requirements)
- [x] Context Orchestrator - excluded
- [x] Context Agent - excluded
- [x] Context Collector - excluded

## Model Tuning Alignment

| Output Type | Training Target | Key Data |
|-------------|-----------------|----------|
| Orchestrator Delegation | Controller models | Rationale, agent selection, decomposition, context selection |
| Collector Consolidation | Merging/aggregation models | Merge strategy, conflict resolution, decisions, input traceability |
| Discovery Reports | Retrieval models | References, queries, tags, links |
| Discovery Collector | Context selection models | Recommendations, query-specific findings |
| Planning Tickets | Requirement-to-code models | Tasks, dependencies, discovery links |
| Ticket Outputs | Implementation models | Code changes, test results, full context chain |

## Traceability Features

| Feature | Purpose |
|---------|---------|
| Context IDs | Enable reconstruction of full decision context |
| Upstream context typing | Explicit dependencies between agents |
| Previous context slot | Support reruns with historical context |
| Result IDs | Link outputs back to specific agent executions |
| Cross-links | Connect related findings within and across reports |

## Structured Interrupt Request Coverage

### Design Philosophy
- [x] Controller extraction dataset goal documented
- [x] Emergent uncertainty routing via prompting (agents self-identify uncertainty)
- [x] Progressive disclosure via multiple-choice structured choices with write-in
- [x] Cerebral inhibition for controller model documented
- [x] "Missing middle ground" between approve-every-file and let-it-run-forever

### Interrupt Flow
- [x] Interrupt request typed per agent (20 types - one per agent)
- [x] Base fields defined (type, reason with natural language uncertainty explanation)
- [x] StructuredChoice format specified (choiceId, question, context, options A/B/C/CUSTOM, recommended)
- [x] ConfirmationItem format specified (confirmationId, statement, context, defaultValue, impactIfNo)
- [x] InterruptResolution structure defined (selectedChoices, customInputs, confirmations)
- [x] LLM translation step documented

### Typed Interrupt Requests (20 Total - One Per Agent)

| Interrupt Type                         | Agent-Specific Fields |
|----------------------------------------|----------------------|
| OrchestratorInterruptRequest           | phase, goal, workflow context |
| OrchestratorCollectorInterruptRequest  | phase decision, collector results |
| DiscoveryOrchestratorInterruptRequest  | subdomain partitioning, scope |
| DiscoveryAgentInterruptRequest         | code findings, boundary decisions |
| DiscoveryCollectorInterruptRequest     | codeReferences, subdomainBoundaries, recommendations |
| DiscoveryAgentDispatchInterruptRequest | dispatch routing decisions |
| PlanningOrchestratorInterruptRequest   | ticket decomposition, discovery context |
| PlanningAgentInterruptRequest          | proposedTickets, architectureDecisions, discoveryReferences |
| PlanningCollectorInterruptRequest      | consolidatedTickets, dependencyConflicts, mergeDecisions |
| PlanningAgentDispatchInterruptRequest  | dispatch routing decisions |
| TicketOrchestratorInterruptRequest     | implementation scope, planning context |
| TicketAgentInterruptRequest            | filesToModify, testStrategies, implementationApproaches |
| TicketCollectorInterruptRequest        | completion status, follow-ups |
| TicketAgentDispatchInterruptRequest    | dispatch routing decisions |
| ReviewInterruptRequest                 | review criteria, assessment |
| MergerInterruptRequest                 | conflict resolution, merge strategy |
| ContextManagerInterruptRequest         | context gathering scope |

### Prompt-Based Uncertainty Recognition
- [x] Prompts guide agents to notice implicit implementation decisions
- [x] Agents self-assess and route for review when uncertain
- [x] Uncertainty emerges naturally from LLM reasoning (not computed scores)
- [x] Natural language uncertainty explanations become training data

### User Stories - Core Interrupt Infrastructure (19-23)
- [x] User Story 19: Agent-Specific Typed Interrupt Requests - all 20 agent types (P1)
- [x] User Story 20: Prompt-Based Uncertainty Recognition (P1)
- [x] User Story 21: Structured Choices with Multiple-Choice and Write-In (P1)
- [x] User Story 22: Confirmation Items in Interrupts (P1)
- [x] User Story 23: Interrupt Resolution Translation via LLM (P1)

### User Stories - Per-Agent Interrupt Context (24-43)
- [x] User Story 24: OrchestratorInterruptRequest - workflow context (P2)
- [x] User Story 25: OrchestratorCollectorInterruptRequest - phase decision context (P2)
- [x] User Story 26: DiscoveryOrchestratorInterruptRequest - scope context (P2)
- [x] User Story 27: DiscoveryAgentInterruptRequest - code findings context (P2)
- [x] User Story 28: DiscoveryCollectorInterruptRequest - consolidation context (P2)
- [x] User Story 29: DiscoveryAgentDispatchInterruptRequest - routing context (P2)
- [x] User Story 30: PlanningOrchestratorInterruptRequest - decomposition context (P2)
- [x] User Story 31: PlanningAgentInterruptRequest - ticket design context (P2)
- [x] User Story 32: PlanningCollectorInterruptRequest - consolidation context (P2)
- [x] User Story 33: PlanningAgentDispatchInterruptRequest - routing context (P2)
- [x] User Story 34: TicketOrchestratorInterruptRequest - implementation scope context (P2)
- [x] User Story 35: TicketAgentInterruptRequest - implementation context (P2)
- [x] User Story 36: TicketCollectorInterruptRequest - completion context (P2)
- [x] User Story 37: TicketAgentDispatchInterruptRequest - routing context (P2)
- [x] User Story 38: ReviewInterruptRequest - assessment context (P2)
- [x] User Story 39: MergerInterruptRequest - conflict context (P2)
- [x] User Story 40: ContextManagerInterruptRequest - gathering scope context (P2)

### User Stories - Prompt Guidance (44)
- [x] User Story 44: Prompt Guidance for Structured Interrupt Generation (P1)

### Functional Requirements (FR-081 to FR-112)
- [x] Base interrupt requirements (FR-081 to FR-085)
- [x] Structured choices requirements (FR-086 to FR-091)
- [x] Confirmation items requirements (FR-092 to FR-094)
- [x] Prompt-based uncertainty recognition (FR-095 to FR-098)
- [x] Interrupt resolution requirements (FR-099 to FR-102)
- [x] Agent-specific interrupt fields - all 20 types (FR-103 to FR-108)
- [x] Interrupt prompt guidance (FR-109 to FR-112)

### Success Criteria (SC-018 to SC-027)
- [x] 100% typed interrupts per agent - all 20 types (SC-018)
- [x] Natural language uncertainty explanations (SC-019)
- [x] Uncertainty recognition guidance in prompts (SC-020)
- [x] Structured choices coverage (SC-021)
- [x] LLM translation timing (SC-022)
- [x] Prompt guidance presence (SC-023)
- [x] Controller training extraction (SC-024)
- [x] Recommended option presence (SC-025)
- [x] Controller response rate (SC-026)
- [x] CUSTOM write-in option in all choices (SC-027)

### Edge Cases
- [x] Empty structured choices
- [x] Invalid option selection
- [x] Partial controller responses
- [x] Ambiguous LLM translation
- [x] Stale/expired interrupts
- [x] Concurrent interrupts
- [x] Controller timeout
- [x] Large context payloads
- [x] Interrupt during rerun
- [x] Custom option selected without providing custom input
- [x] Vague or unhelpful uncertainty explanations

### Gherkin Feature Coverage
- Existing interrupt_handling.feature scenarios cover base interrupt functionality
- No new test scenarios added (existing coverage sufficient)

## Notes

- All checklist items pass validation
- Spec is ready for `/speckit.clarify` or `/speckit.plan`
- Existing test_graph features cover integration testing
- 44 user stories covering all agent types, context flow, templates, and structured interrupts
- 112 functional requirements defined
- 27 measurable success criteria established
- Key additions in this revision (2026-01-21):
  - Structured interrupt requests with agent-specific typing (all 20 agent types)
  - Individual user stories for each of the 20 interrupt request types (US-24 through US-43)
  - Prompt-based uncertainty recognition (agents self-identify when uncertain)
  - StructuredChoice format: multiple-choice (A, B, C) with CUSTOM write-in option
  - Confirmation items for yes/no decisions
  - Interrupt resolution translation via LLM
  - Agent-specific interrupt fields documented per agent type
  - Prompt guidance for interrupt generation
  - "Missing middle ground" design philosophy (between approve-every-file and let-it-run-forever)
- Previous additions (2026-01-19):
  - Typed upstream context with IDs for all agents
  - Previous context slot for rerun support
  - Discovery collector expanded with recommendations and query-specific findings
  - Planning receives explicit discovery context
  - Full context chain traceability through workflow
