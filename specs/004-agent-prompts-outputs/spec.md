# Feature Specification: Agent Prompts, Structured Outputs, and Tool Prompt Contributors

**Feature Branch**: `004-agent-prompts-outputs`  
**Created**: 2026-01-19  
**Status**: Draft  
**Input**: Building structured outputs and prompts for all agents in AgentInterfaces.java, adding a prompt contributor framework for tools, and implementing episodic memory tool prompts as the first contributor

## Design Philosophy: Standardization for Model Tuning

This feature is designed with a key goal in mind: **all structured outputs and prompts should be standardized to enable training smaller, specialized models** that excel at specific agent roles. The resulting data from agent executions will be used to:

1. **Train controller models** from orchestrator delegation patterns
2. **Train consolidation models** from collector merging and decision patterns  
3. **Train specialized task models** from agent outputs (discovery, planning, implementation)
4. **Build retrieval datasets** from references, queries, and links in reports

Every structured output includes versioning to track schema evolution and enable dataset filtering by version.

## Design Philosophy: Curated Typed Context Flow with Traceability

All agents receive **typed, curated upstream context** from their dependencies. The full upstream result is stored, but downstream prompts receive a curated, role-specific representation that references the source result ID. This enables:

1. **Explicit traceability** - Every context has an ID that traces back through the workflow
2. **Rerun support** - Previous context can be mapped into prompts when agents are rerun
3. **Training data enrichment** - We know exactly what information each agent considered helpful
4. **Dependency clarity** - Typed context makes upstream dependencies explicit and queryable
5. **Curation discipline** - Agents are nudged to pass only what is relevant, not entire results

### Context Flow Diagram

```
Orchestrator
    ↓ (OrchestratorContext)
Discovery Orchestrator
    ↓ (DiscoveryOrchestratorContext)
Discovery Agent(s)
    ↓ (DiscoveryAgentResult[])
Discovery Collector → DiscoveryCollectorResult (with ID)
    ↓ (DiscoveryCollectorContext: DiscoveryCuration)
Planning Orchestrator (receives: DiscoveryCollectorContext)
    ↓ (PlanningOrchestratorContext + DiscoveryCollectorContext)
Planning Agent(s)
    ↓ (PlanningAgentResult[])
Planning Collector → PlanningCollectorResult (with ID, links to DiscoveryCollectorResult)
    ↓ (PlanningCollectorContext: PlanningCuration)
Ticket Orchestrator (receives: DiscoveryCollectorContext, PlanningCollectorContext)
    ↓ (TicketOrchestratorContext + DiscoveryCollectorContext + PlanningCollectorContext)
Ticket Agent(s)
    ↓ (TicketAgentResult[])
Ticket Collector → TicketCollectorResult (with ID, links to Discovery + Planning)
    ↓ (TicketCollectorContext: TicketCuration)
Orchestrator Collector (receives: collector results + curated contexts)
```

### Context ID Structure

Each context/result has a unique ID that includes:
- Workflow run ID (ties all contexts in a single execution)
- Agent type identifier
- Sequence number (for multiple agents of same type)
- Timestamp

Example: `wf-abc123/discovery-collector/001/2026-01-19T10:30:00Z`

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Prompt Contributor Framework (Priority: P1)

As a developer, I want a modular prompt contributor framework so that tools can dynamically add their specific prompting guidance to any agent's prompts without modifying agent code.

**Why this priority**: This is the foundational infrastructure that enables all tool-specific prompting. Without this framework, tool prompts cannot be systematically integrated across agents.

**Independent Test**: Can be tested by registering a mock prompt contributor and validating that agent prompts include the contributed sections when assembled.

**Cucumber Test Tag**: `@prompt-contributor-framework`

**Acceptance Scenarios**:

1. **Given** a tool registers a prompt contributor, **When** any agent prompt is assembled, **Then** the tool's prompt contribution is included in the final prompt
2. **Given** multiple tools register prompt contributors, **When** prompts are assembled, **Then** all contributions are included in a consistent, ordered format
3. **Given** a prompt contributor specifies which agent types it applies to, **When** prompts are assembled for different agents, **Then** only relevant contributions are included
4. **Given** blackboard history exists for an agent, **When** prompts are assembled, **Then** the prompt contributor context includes that history

---

### User Story 2 - Episodic Memory Tool Prompt Contributor (Priority: P1)

As a developer, I want the episodic memory tool to have a dedicated prompt contributor that guides agents to use retain, reflect, and recall operations effectively, with emphasis on reflection to build knowledge graph connections.

**Why this priority**: The episodic memory tool is the first and most critical prompt contributor implementation. The reflect operation is essential for building lasting connections - simply retaining isn't enough.

**Independent Test**: Can be tested by validating that agent prompts contain specific episodic memory guidance including retain/reflect/recall instructions.

**Cucumber Test Tag**: `@episodic-memory-prompts`

**Acceptance Scenarios**:

1. **Given** the episodic memory tool is registered, **When** agent prompts are assembled, **Then** guidance for retain/reflect/recall operations is included
2. **Given** an agent is prompted to use episodic memory, **When** the agent processes the prompt, **Then** explicit instructions guide the agent to reflect after significant actions to solidify memory connections
3. **Given** an agent begins a new task, **When** the prompt is assembled, **Then** guidance includes recalling relevant prior episodes before proceeding

---

### User Story 3 - Typed, Curated Upstream Context with IDs (Priority: P1)

As a developer, I want all agents to receive typed, curated upstream context with unique IDs so that I can trace information flow through the workflow, see what was intentionally passed, and avoid shipping full results downstream.

**Why this priority**: Typed context with IDs is essential for traceability, debugging, and creating high-quality training data that captures the full decision context.

**Independent Test**: Can be tested by running a workflow and validating each agent's output includes typed references to upstream context IDs.

**Cucumber Test Tag**: `@typed-context-flow`

**Acceptance Scenarios**:

1. **Given** an agent has upstream dependencies, **When** receiving input, **Then** the input includes typed curated context from each upstream agent with its unique context ID and source result ID
2. **Given** an agent produces output, **When** the output is examined, **Then** it includes references to all upstream context IDs and source result IDs that were considered
3. **Given** a workflow completes, **When** tracing a decision, **Then** the full context chain can be reconstructed from context IDs and source result IDs

---

### User Story 4 - Previous Context for Reruns (Priority: P1)

As a developer, I want all agents to have a slot for previous context so that when agents are rerun, the previous attempt's context can be mapped into the prompt.

**Why this priority**: Agents can be rerun (canRerun=true). The previous context helps the agent understand what was tried before and potentially improve.

**Independent Test**: Can be tested by rerunning an agent and validating the prompt includes the previous context.

**Cucumber Test Tag**: `@previous-context-rerun`

**Acceptance Scenarios**:

1. **Given** an agent is being rerun, **When** the prompt is assembled, **Then** the previous context is included in a designated section
2. **Given** previous context exists, **When** mapped into the prompt, **Then** the agent can see what was attempted before, the outcome, and any prior curated contexts
3. **Given** no previous context exists (first run), **When** the prompt is assembled, **Then** the previous context section is omitted or marked as first attempt
4. **Given** a collector reroutes back to an orchestrator, **When** the orchestrator is prompted, **Then** previous curated contexts from the prior run are included for refinement

---

### User Story 5 - Orchestrator Standardized Delegation Template (Priority: P1)

As a developer, I want all orchestrators to use a standardized delegation template with versioning and upstream context so that the resulting delegation patterns can be used to train controller models.

**Why this priority**: Orchestrators are the controllers of the system. Standardizing their delegation logic creates clean training data for smaller controller models that can make routing decisions.

**Independent Test**: Can be tested by triggering any orchestrator and validating the output conforms to the versioned delegation template with context references.

**Cucumber Test Tag**: `@orchestrator-delegation-template`

**Acceptance Scenarios**:

1. **Given** any orchestrator receives a request, **When** it produces a delegation decision, **Then** the output follows the standardized delegation template with version number and context IDs
2. **Given** the orchestrator delegates to sub-agents, **When** producing output, **Then** it includes delegation rationale, selected agents, task decomposition, upstream context references, and context to pass
3. **Given** orchestrator outputs are collected over time, **When** used for training, **Then** the standardized format enables direct extraction of controller training examples with full context

---

### User Story 6 - Collector Standardized Consolidation Template (Priority: P1)

As a developer, I want all collectors to use a standardized consolidation template with versioning and typed input references so that the resulting consolidation patterns can be used to train merging/aggregation models.

**Why this priority**: Collectors perform critical consolidation logic. Standardizing their outputs creates training data for models that excel at merging, deduplicating, and making advancement decisions.

**Independent Test**: Can be tested by triggering any collector and validating the output conforms to the versioned consolidation template with input references.

**Cucumber Test Tag**: `@collector-consolidation-template`

**Acceptance Scenarios**:

1. **Given** any collector receives inputs to consolidate, **When** it produces output, **Then** the output implements the standardized consolidation template with version number, unique result ID, and a derived list of curations
2. **Given** the collector merges multiple inputs, **When** producing output, **Then** it includes typed references to each input with their IDs, merge strategy, conflict resolutions, decision rationale, and discrete curation fields for its collector type
3. **Given** collector outputs are collected over time, **When** used for training, **Then** the standardized format enables direct extraction of consolidation training examples

---

### User Story 7 - Discovery Agent Reports with References and Links (Priority: P1)

As a developer, I want discovery agents to produce structured reports with file references, cross-links, diagrams, semantic tags, and generated queries so that the findings can be used for retrieval model training and dataset extraction.

**Why this priority**: Discovery agent outputs are the primary source for training retrieval models and building code understanding datasets. Rich linking and references maximize training value.

**Independent Test**: Can be tested by triggering a discovery agent and validating the structured report contains all required linked data.

**Cucumber Test Tag**: `@discovery-agent-reports`

**Acceptance Scenarios**:

1. **Given** a discovery agent analyzes a subdomain, **When** producing output, **Then** the report includes versioned schema, unique ID, file references with paths/lines/relevance, and cross-links between related findings
2. **Given** the discovery report is produced, **When** examined, **Then** it contains semantic tags, generated queries (for retrieval training), and diagram representations as structured data
3. **Given** discovery reports are collected over time, **When** used for training, **Then** the references and queries can be extracted as retrieval training pairs

---

### User Story 8 - Discovery Collector with Recommendations and Query Findings (Priority: P1)

As a developer, I want the discovery collector to produce not just a unified code map but also recommendations and query-specific discovery findings so that downstream agents get actionable guidance.

**Why this priority**: The discovery collector output feeds directly into planning. Recommendations and query-specific findings help the planning orchestrator understand what to prioritize.

**Independent Test**: Can be tested by providing multiple discovery reports and validating the collector output includes recommendations and query-specific sections.

**Cucumber Test Tag**: `@discovery-collector-recommendations`

**Acceptance Scenarios**:

1. **Given** the discovery collector receives multiple reports, **When** merging, **Then** the output includes a unified code map with deduplicated references and aggregated relevance scores
2. **Given** discovery agents found notable patterns or issues, **When** the collector produces output, **Then** it includes a recommendations section with prioritized action items
3. **Given** discovery was driven by specific queries, **When** the collector produces output, **Then** it includes query-specific findings that map queries to their results
4. **Given** the collector produces output, **When** examined, **Then** it has a unique ID and includes references to all input discovery agent report IDs

---

### User Story 9 - Discovery Orchestrator Delegation (Priority: P1)

As a developer, I want the discovery orchestrator to use the standardized delegation template to divide work across subdomain-focused agents.

**Why this priority**: Discovery orchestration delegation patterns are valuable for training controllers that understand code partitioning.

**Independent Test**: Can be tested by triggering discovery orchestration and validating delegation template conformance.

**Cucumber Test Tag**: `@discovery-orchestrator-delegation`

**Acceptance Scenarios**:

1. **Given** a discovery orchestrator receives a goal, **When** it divides work, **Then** the output follows the standardized delegation template with subdomain assignments and upstream orchestrator context ID
2. **Given** the orchestrator identifies subdomains, **When** producing delegation, **Then** it includes rationale for partitioning and expected coverage per agent

---

### User Story 10 - Planning Orchestrator with Curated Discovery Context (Priority: P2)

As a developer, I want the planning orchestrator to receive typed discovery collector context (curated) so that it can pass relevant discovery context to its planning agents and we can trace what discovery information influenced planning.

**Why this priority**: Explicit discovery context in planning shows what information the orchestrator considered helpful, creating valuable training data for context selection.

**Independent Test**: Can be tested by triggering planning orchestration and validating it receives and passes discovery collector context.

**Cucumber Test Tag**: `@planning-orchestrator-discovery-context`

**Acceptance Scenarios**:

1. **Given** a planning orchestrator receives a goal, **When** receiving input, **Then** it includes typed DiscoveryCollectorContext with its unique context ID and source result ID
2. **Given** the orchestrator delegates to planning agents, **When** producing output, **Then** it includes selected portions of curated discovery context to pass to each agent with references to the discovery collector context ID and source result ID
3. **Given** planning delegation is examined, **When** tracing context, **Then** we can see exactly what discovery findings the orchestrator considered relevant

---

### User Story 11 - Planning Agent with Curated Discovery Context (Priority: P2)

As a developer, I want planning agents to receive typed discovery collector context (curated) from the planning orchestrator so that tickets can link back to discovery findings.

**Why this priority**: Planning agents need discovery context to create tickets that reference the right code. Typed context ensures traceability.

**Independent Test**: Can be tested by triggering a planning agent and validating it receives discovery context and produces tickets with discovery links.

**Cucumber Test Tag**: `@planning-agent-discovery-context`

**Acceptance Scenarios**:

1. **Given** a planning agent receives a goal, **When** receiving input, **Then** it includes typed DiscoveryCollectorContext with ID from the planning orchestrator and source result ID
2. **Given** the planning agent produces tickets, **When** tickets reference code, **Then** each ticket includes links to specific curated discovery items and the discovery collector result ID
3. **Given** a ticket is generated, **When** examined, **Then** it includes acceptance criteria, effort estimate, and traceability links to discovery

---

### User Story 12 - Planning Collector with Typed Inputs (Priority: P2)

As a developer, I want the planning collector to receive typed planning agent results and preserve discovery links in consolidated tickets.

**Why this priority**: Planning collector outputs become the input to ticket orchestration. Typed references ensure full traceability.

**Independent Test**: Can be tested by providing planning outputs and validating consolidated ticket structure with preserved links.

**Cucumber Test Tag**: `@planning-collector-typed-inputs`

**Acceptance Scenarios**:

1. **Given** the planning collector receives planning results, **When** consolidating, **Then** output follows the standardized consolidation template with unique ID and typed references to each planning agent result ID
2. **Given** planning agent tickets have discovery links, **When** consolidated, **Then** discovery links are preserved with original discovery collector result ID
3. **Given** the collector produces output, **When** examined, **Then** it includes finalized tickets with dependency graph, upstream context chain, and a PlanningCollectorContext with curated PlanningCuration for downstream agents

---

### User Story 13 - Ticket Orchestrator with Curated Discovery and Planning Context (Priority: P2)

As a developer, I want the ticket orchestrator to receive typed discovery and planning collector contexts (curated) so that ticket agents have full upstream context without passing entire results.

**Why this priority**: Ticket agents need both discovery (code understanding) and planning (work items) context. Typed inputs ensure nothing is lost.

**Independent Test**: Can be tested by triggering ticket orchestration and validating it receives both collector contexts.

**Cucumber Test Tag**: `@ticket-orchestrator-full-context`

**Acceptance Scenarios**:

1. **Given** a ticket orchestrator receives tickets, **When** receiving input, **Then** it includes typed DiscoveryCollectorContext and PlanningCollectorContext with their unique context IDs and source result IDs
2. **Given** the orchestrator delegates to ticket agents, **When** producing output, **Then** it includes selected curated context from both collectors to pass to each agent with ID references
3. **Given** ticket delegation is examined, **When** tracing context, **Then** we can see the full context chain from discovery through planning

---

### User Story 14 - Ticket Agent with Curated Upstream Context (Priority: P2)

As a developer, I want ticket agents to receive typed discovery and planning collector contexts (curated) so that implementation is fully traceable to requirements and code understanding.

**Why this priority**: Ticket agent outputs with full traceability create training data for implementation models that understand context-to-code mapping.

**Independent Test**: Can be tested by triggering a ticket agent and validating the structured output with full upstream context links.

**Cucumber Test Tag**: `@ticket-agent-full-context`

**Acceptance Scenarios**:

1. **Given** a ticket agent implements a ticket, **When** receiving input, **Then** it includes typed DiscoveryCollectorContext, PlanningCollectorContext, and specific PlanningTicket with IDs
2. **Given** the ticket agent produces output, **When** examined, **Then** it includes implementation summary, files modified, test results, and links back to originating ticket ID and discovery collector ID
3. **Given** the implementation is complete, **When** examined, **Then** it includes commit information and the full upstream context chain

---

### User Story 15 - Ticket Collector with Typed Inputs (Priority: P2)

As a developer, I want the ticket collector to consolidate implementation results with typed references to all upstream context.

**Why this priority**: Ticket collector outputs show implementation completion patterns with full traceability.

**Independent Test**: Can be tested by providing ticket results and validating consolidated output structure with context chain.

**Cucumber Test Tag**: `@ticket-collector-typed-inputs`

**Acceptance Scenarios**:

1. **Given** the ticket collector receives ticket results, **When** consolidating, **Then** output follows the standardized consolidation template with unique ID and typed references to each ticket agent result ID
2. **Given** the collector produces output, **When** examined, **Then** it includes completed/failed status, follow-ups, full upstream context chain (discovery → planning → tickets), and a TicketCollectorContext with curated TicketCuration for downstream agents

---

### User Story 16 - Orchestrator Collector with Full Context Chain (Priority: P1)

As a developer, I want the orchestrator collector to receive and consolidate results from all downstream collectors along with their curated contexts so we keep full context chain visibility without passing entire results in prompts.

**Why this priority**: Orchestrator collector decisions are the highest-level control flow, critical for training controller models with complete context.

**Independent Test**: Can be tested by providing workflow outputs and validating the consolidation template with full context chain.

**Cucumber Test Tag**: `@orchestrator-collector-full-context`

**Acceptance Scenarios**:

1. **Given** the orchestrator collector receives workflow outputs, **When** consolidating, **Then** output follows the standardized consolidation template with phase decision and full context chain
2. **Given** the collector decides on next phase, **When** producing output, **Then** it includes decision type, rationale, and typed references to all collector results and curated context IDs in the context chain

---

### User Story 17 - Review Agent with Context References (Priority: P2)

As a developer, I want the review agent to receive typed, curated context and produce structured evaluation outputs with references to reviewed content.

**Why this priority**: Review outputs create training data for quality evaluation models.

**Independent Test**: Can be tested by triggering a review and validating the structured evaluation output with context references.

**Cucumber Test Tag**: `@review-agent-context`

**Acceptance Scenarios**:

1. **Given** a review agent evaluates content, **When** receiving input, **Then** it includes typed curated context with IDs for the content being reviewed
2. **Given** the review agent produces output, **When** examined, **Then** it includes overall assessment, specific feedback, suggestions, and links to the exact content sections reviewed by ID

---

### User Story 18 - Merger Agent with Context References (Priority: P3)

As a developer, I want the merger agent to receive typed, curated context and produce structured merge validation outputs with references.

**Why this priority**: Merger outputs are less frequent but create valuable training data for merge conflict models.

**Independent Test**: Can be tested by triggering a merge validation and validating the output with context references.

**Cucumber Test Tag**: `@merger-agent-context`

**Acceptance Scenarios**:

1. **Given** a merger agent validates a merge, **When** receiving input, **Then** it includes typed curated context with IDs for the branches/content being merged
2. **Given** the merger agent produces output, **When** examined, **Then** it includes acceptability status, conflict details, and resolution guidance with links to conflicting sections by ID

---

### Edge Cases

- What happens when no prompt contributors are registered for an agent?
- How does the system handle prompt contributor failures during assembly?
- What happens when a tool's prompt contribution exceeds context limits?
- How does the system handle conflicting guidance from multiple prompt contributors?
- What happens when structured output validation fails against the versioned schema?
- How does the system handle episodic memory operations when the knowledge graph is unavailable?
- What happens when links reference context IDs that no longer exist or are invalid?
- What happens when upstream context is too large to fit in the prompt?
- How does the system handle rerun when previous context had errors?
- What happens when curated context omits necessary detail from the full result?

## Requirements *(mandatory)*

### Functional Requirements

#### Prompt Contributor Framework
- **FR-001**: System MUST support registration of prompt contributors by tools
- **FR-002**: Prompt contributors MUST specify which agent types they apply to
- **FR-003**: Agent prompts MUST be assembled by combining base prompts with all applicable tool prompt contributions
- **FR-004**: Prompt contributions MUST be included in a consistent, ordered format
- **FR-005**: Prompt assembly MUST handle missing or failed contributors gracefully

#### Episodic Memory Prompt Contributor
- **FR-006**: Episodic memory tool MUST register a prompt contributor
- **FR-007**: Episodic memory prompt contributor MUST include guidance for retain operations with contextual metadata
- **FR-008**: Episodic memory prompt contributor MUST include explicit guidance for reflect operations to build knowledge graph connections
- **FR-009**: Episodic memory prompt contributor MUST include guidance for recall operations before starting new tasks
- **FR-010**: Reflect guidance MUST emphasize that reflection solidifies connections between memories
- **FR-011**: Prompt MUST guide agents to reflect after significant actions, not just retain

#### Typed Context Flow
- **FR-012**: All agent inputs MUST include typed, curated upstream context from dependencies (not full results)
- **FR-013**: All context objects MUST have a unique ID with workflow run ID, agent type, sequence, and timestamp
- **FR-014**: All agent outputs MUST include references to upstream context IDs and source result IDs that were considered
- **FR-015**: Context IDs MUST enable reconstruction of the full context chain for any decision

#### Previous Context for Reruns
- **FR-016**: All agent inputs MUST include a slot for previous context (nullable for first run) with per-agent implementations and discrete prior contexts
- **FR-017**: When agents are rerun, previous context MUST be mapped into a designated prompt section
- **FR-018**: Previous context MUST include the previous output, any error/failure information, and prior curated contexts when rerouted

#### Standardized Output Templates
- **FR-019**: All structured outputs MUST include a schema version number
- **FR-020**: All structured outputs MUST include a unique result ID
- **FR-021**: Orchestrator outputs MUST follow a standardized delegation template
- **FR-022**: Collector outputs MUST follow a standardized consolidation template
- **FR-023**: Agent outputs MUST follow standardized report templates specific to their role

#### Orchestrator Delegation Template
- **FR-024**: Delegation template MUST include: version, result ID, goal, upstream context IDs, delegation rationale, selected agents with assignments, task decomposition, and context to pass
- **FR-025**: Delegation template MUST support extraction of controller training examples with full context chain
- **FR-026**: All orchestrators (main, discovery, planning, ticket) MUST use the delegation template

#### Collector Consolidation Template
- **FR-027**: Consolidation template MUST include: version, result ID, typed input references with IDs, merge strategy, conflict resolutions, aggregated metrics, decision type, decision rationale, upstream context chain, and derived curations list
- **FR-028**: Consolidation template MUST support extraction of consolidation training examples
- **FR-029**: All collectors (orchestrator, discovery, planning, ticket) MUST implement the consolidation template mix-in and expose discrete curation fields per collector type

#### Discovery Agent Reports
- **FR-030**: Discovery reports MUST include versioned schema and unique result ID
- **FR-031**: Discovery reports MUST include file references with paths, line numbers, and relevance scores
- **FR-032**: Discovery reports MUST include cross-links between related findings
- **FR-033**: Discovery reports MUST include semantic tags for categorization
- **FR-034**: Discovery reports MUST include generated queries for retrieval training
- **FR-035**: Discovery reports MUST include diagram representations as structured data
- **FR-036**: Discovery reports MUST include upstream context ID from discovery orchestrator

#### Discovery Collector
- **FR-037**: Discovery collector MUST produce unique result ID
- **FR-038**: Discovery collector MUST include typed references to all input discovery agent report IDs
- **FR-039**: Discovery collector MUST deduplicate references with source attribution
- **FR-040**: Discovery collector MUST aggregate relevance scores for duplicates
- **FR-041**: Discovery collector MUST preserve and enhance cross-links
- **FR-042**: Discovery collector MUST produce unified architecture diagram
- **FR-043**: Discovery collector MUST include recommendations section with prioritized action items
- **FR-044**: Discovery collector MUST include query-specific findings mapping queries to results
- **FR-045**: Discovery collector MUST include merge metadata for training extraction

#### Planning Orchestrator
- **FR-046**: Planning orchestrator MUST receive typed DiscoveryCollectorContext with context ID and source result ID
- **FR-047**: Planning orchestrator MUST include discovery collector context ID and source result ID in delegation output
- **FR-048**: Planning orchestrator MUST select and pass relevant discovery context to planning agents

#### Planning Agent
- **FR-049**: Planning agent MUST receive typed DiscoveryCollectorContext with context ID and source result ID from orchestrator
- **FR-050**: Planning tickets MUST include versioned schema and unique result ID
- **FR-051**: Planning tickets MUST include unique ticket IDs, titles, tasks, and dependencies
- **FR-052**: Planning tickets MUST include links to discovery collector result ID and curated discovery items
- **FR-053**: Planning tickets MUST include acceptance criteria and effort estimates

#### Planning Collector
- **FR-054**: Planning collector MUST produce unique result ID
- **FR-055**: Planning collector MUST include typed references to all planning agent result IDs
- **FR-056**: Planning collector MUST include reference to discovery collector result ID and curated discovery context ID
- **FR-057**: Planning collector MUST resolve dependency graph and determine execution order
- **FR-058**: Planning collector MUST preserve discovery links in finalized tickets

#### Ticket Orchestrator
- **FR-059**: Ticket orchestrator MUST receive typed DiscoveryCollectorContext and PlanningCollectorContext with context IDs and source result IDs
- **FR-060**: Ticket orchestrator MUST include both collector context IDs and source result IDs in delegation output
- **FR-061**: Ticket orchestrator MUST select and pass relevant context to ticket agents

#### Ticket Agent
- **FR-062**: Ticket agent MUST receive typed DiscoveryCollectorContext, PlanningCollectorContext, and specific PlanningTicket with IDs
- **FR-063**: Ticket agent outputs MUST include versioned schema and unique result ID
- **FR-064**: Ticket agent outputs MUST include implementation summary, files modified, and test results
- **FR-065**: Ticket agent outputs MUST include links back to originating ticket ID and discovery collector ID
- **FR-066**: Ticket agent outputs MUST include commit information and verification status

#### Ticket Collector
- **FR-067**: Ticket collector MUST produce unique result ID
- **FR-068**: Ticket collector MUST include typed references to all ticket agent result IDs
- **FR-069**: Ticket collector MUST include full upstream context chain (discovery → planning → tickets)
- **FR-070**: Ticket collector MUST consolidate with completed/failed status and follow-ups
- **FR-071**: Ticket collector MUST preserve traceability links

#### Orchestrator Collector
- **FR-072**: Orchestrator collector MUST receive typed results from downstream collectors
- **FR-073**: Orchestrator collector MUST include full context chain in consolidation output
- **FR-074**: Orchestrator collector MUST include phase decision with rationale

#### Review Agent
- **FR-075**: Review agent MUST receive typed curated context with IDs for content being reviewed
- **FR-076**: Review outputs MUST include versioned schema, result ID, assessment status, feedback, and suggestions
- **FR-077**: Review outputs MUST include links to specific reviewed sections by ID

#### Merger Agent
- **FR-078**: Merger agent MUST receive typed curated context with IDs for content being merged
- **FR-079**: Merger outputs MUST include versioned schema, result ID, acceptability, and conflict details
- **FR-080**: Merger outputs MUST include resolution guidance with links when conflicts exist

### Key Entities

- **ContextId**: Unique identifier with workflow run ID, agent type, sequence number, and timestamp
- **UpstreamContext**: Typed container for curated context from dependency agents with their context IDs and source result IDs
- **PreviousContext (Mixin)**: Per-agent previous attempt context with discrete fields for prior results and curated contexts
- **PromptContributor**: A component that provides tool-specific prompting guidance
- **DelegationTemplate**: Standardized orchestrator output with version, result ID, upstream context IDs, rationale, assignments, and context to pass
- **ConsolidationTemplate (Mixin)**: Shared consolidation fields for collector results, including derived curations list
- **Curation (Mixin)**: Shared interface for curated contexts produced by collectors
- **DiscoveryAgentResult**: Versioned output with ID, file references, cross-links, semantic tags, queries, diagrams, and upstream context ID
- **DiscoveryCollectorResult**: Versioned full output implementing ConsolidationTemplate with unified code map, recommendations, query-specific findings, and discovery curation
- **DiscoveryCollectorContext**: Curated context derived from DiscoveryCollectorResult, carrying DiscoveryCuration
- **DiscoveryCuration**: Curated subset of discovery inputs and collector outputs (DiscoveryReport, CodeMap, Recommendations, QueryFindings, consolidation metadata)
- **PlanningAgentResult**: Versioned output with ID, tickets, discovery context reference
- **PlanningCollectorResult**: Versioned full output implementing ConsolidationTemplate with finalized tickets, dependency graph, discovery reference, and planning curation
- **PlanningCollectorContext**: Curated context derived from PlanningCollectorResult, carrying PlanningCuration
- **PlanningCuration**: Curated subset of planning inputs and collector outputs (PlanningAgentResult, PlanningTicket, dependency graph, discovery references, consolidation metadata)
- **TicketAgentResult**: Versioned output with ID, implementation summary, files, tests, commits, and full upstream context chain
- **TicketCollectorResult**: Versioned full output implementing ConsolidationTemplate with completion status, follow-ups, and ticket curation
- **TicketCollectorContext**: Curated context derived from TicketCollectorResult, carrying TicketCuration
- **TicketCuration**: Curated subset of implementation inputs and collector outputs (TicketAgentResult, completion status, follow-ups, consolidation metadata)
- **ReviewEvaluation**: Versioned output with ID, assessment status, feedback, suggestions, and content links
- **MergerValidation**: Versioned output with ID, acceptability, conflicts, and resolution guidance

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of agent prompts include applicable tool prompt contributions when contributors are registered
- **SC-002**: Prompt assembly completes within 100ms for all agents
- **SC-003**: 100% of agent inputs include typed curated upstream context with valid IDs
- **SC-004**: 100% of agent outputs include unique result IDs and upstream context references
- **SC-005**: 100% of orchestrator outputs conform to the versioned delegation template schema
- **SC-006**: 100% of collector outputs conform to the versioned consolidation template schema
- **SC-007**: 100% of rerun attempts include previous context mapped into the prompt
- **SC-008**: Full context chain can be reconstructed for 100% of workflow executions
- **SC-009**: Episodic memory prompt contributor guidance is present in all applicable agent prompts
- **SC-010**: 90% of agent workflows invoke at least one reflect operation when episodic memory is available
- **SC-011**: Discovery reports contain at least 5 semantic tags, 3 generated queries, and 10 file references per analyzed module
- **SC-012**: Discovery collector output includes recommendations and query-specific findings for 100% of consolidations
- **SC-013**: Planning tickets include discovery collector ID links for 100% of tickets
- **SC-014**: Ticket agent outputs include full upstream context chain (discovery + planning IDs) for 100% of implementations
- **SC-015**: Generated queries from discovery reports achieve 70% relevance for retrieval (measured against ground truth)
- **SC-016**: Orchestrator delegation outputs can be directly extracted as controller training examples with full context chain
- **SC-017**: Collector consolidation outputs can be directly extracted as consolidation training examples with input traceability

## Notes on Gherkin Feature Files For Test Graph

Existing test_graph feature files cover integration testing for this feature. No new feature files are required.
