# Data Model: Agent Prompts, Structured Outputs, and Tool Prompt Contributors

**Feature**: 004-agent-prompts-outputs
**Date**: 2026-01-19

## Entity Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           CONTEXT FLOW ENTITIES                              │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌──────────┐    ┌─────────────────┐    ┌─────────────────┐                 │
│  │ContextId │───▶│ UpstreamContext │───▶│ PreviousContext │                 │
│  └──────────┘    └─────────────────┘    └─────────────────┘                 │
│       │                  │                                                   │
│       ▼                  ▼                                                   │
│  ┌─────────────────────────────────────────────────────────────┐            │
│  │                    AgentModels.*Request                      │            │
│  │  (All request types now include contextId, upstreamContext   │            │
│  │   (curated), previousContext per agent)                      │            │
│  └─────────────────────────────────────────────────────────────┘            │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                          PROMPT CONTRIBUTOR ENTITIES                         │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌───────────────────┐    ┌────────────────────────┐                        │
│  │ PromptContributor │───▶│ PromptContributorReg.  │                        │
│  └───────────────────┘    └────────────────────────┘                        │
│           │                          │                                       │
│           ▼                          ▼                                       │
│  ┌───────────────────┐    ┌────────────────────────┐                        │
│  │ EpisodicMemory    │    │    PromptAssembly      │                        │
│  │ PromptContributor │    └────────────────────────┘                        │
│  └───────────────────┘              │                                        │
│                                     ▼                                        │
│                          ┌────────────────────────┐                         │
│                          │    PromptContext       │                         │
│                          └────────────────────────┘                         │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                          TEMPLATE ENTITIES                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌───────────────────┐    ┌────────────────────────┐                        │
│  │DelegationTemplate │    │ConsolidationTemplate   │                        │
│  │  (Orchestrators)  │    │    (Collectors)        │                        │
│  └───────────────────┘    └────────────────────────┘                        │
│           │                          │                                       │
│           ▼                          ▼                                       │
│  ┌───────────────────┐    ┌────────────────────────┐                        │
│  │ AgentAssignment   │    │   InputReference       │                        │
│  │ ContextSelection  │    │   ConflictResolution   │                        │
│  └───────────────────┘    └────────────────────────┘                        │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                        DISCOVERY OUTPUT ENTITIES                             │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌───────────────────┐                                                      │
│  │  DiscoveryReport  │                                                      │
│  └───────────────────┘                                                      │
│           │                                                                  │
│           ├──▶ FileReference                                                │
│           ├──▶ CrossLink                                                    │
│           ├──▶ SemanticTag                                                  │
│           ├──▶ GeneratedQuery                                               │
│           └──▶ DiagramRepresentation                                        │
│                                                                              │
│  ┌───────────────────────────┐                                              │
│  │DiscoveryCollectorResult   │                                              │
│  └───────────────────────────┘                                              │
│           │                                                                  │
│           ├──▶ CodeMap                                                      │
│           ├──▶ Recommendation                                               │
│           └──▶ QueryFindings                                                │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────────────┐
│                          CURATED CONTEXT ENTITIES                            │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                              │
│  ┌───────────────────────────┐                                              │
│  │DiscoveryCollectorContext  │                                              │
│  └───────────────────────────┘                                              │
│           │                                                                  │
│           └──▶ DiscoveryCuration                                             │
│                                                                              │
│  ┌───────────────────────────┐                                              │
│  │PlanningCollectorContext   │                                              │
│  └───────────────────────────┘                                              │
│           │                                                                  │
│           └──▶ PlanningCuration                                              │
│                                                                              │
│  ┌───────────────────────────┐                                              │
│  │TicketCollectorContext     │                                              │
│  └───────────────────────────┘                                              │
│           │                                                                  │
│           └──▶ TicketCuration                                                │
│                                                                              │
└─────────────────────────────────────────────────────────────────────────────┘
```

## Core Entities

### 1. ContextId

**Purpose**: Unique identifier for any context/result in the workflow, enabling full traceability.

| Field          | Type      | Description                                  | Validation                     |
|----------------|-----------|----------------------------------------------|--------------------------------|
| workflowRunId  | String    | ID of the workflow execution                 | Not blank, format: `wf-{uuid}` |
| agentType      | AgentType | Type of agent that produced this             | Not null                       |
| sequenceNumber | int       | Sequence within workflow for this agent type | >= 1                           |
| timestamp      | Instant   | When this context was created                | Not null                       |

**Computed Properties**:

- `toStringId()`: `{workflowRunId}/{agentType}/{sequenceNumber}/{timestamp}`

**State Transitions**: None (immutable value object)

---

### 2. UpstreamContext (Sealed Interface)

**Purpose**: Type-safe container for context received from upstream agents.

**Permits**:

- `OrchestratorUpstreamContext`
- `DiscoveryOrchestratorUpstreamContext`
- `DiscoveryAgentUpstreamContext`
- `DiscoveryCollectorUpstreamContext`
- `PlanningOrchestratorUpstreamContext`
- `PlanningAgentUpstreamContext`
- `PlanningCollectorContext`
- `TicketOrchestratorUpstreamContext`
- `TicketAgentUpstreamContext`
- `TicketCollectorContext`
- `ReviewUpstreamContext`
- `MergerUpstreamContext`

**Common Fields** (all implementations):
| Field | Type | Description |
|-------|------|-------------|
| contextId | ContextId | ID of this upstream context |

**Curated Collector Context Types** (implement `Curation`):

- **DiscoveryCollectorContext**: `contextId`, `sourceResultId`, `curation` (`DiscoveryCuration`), `selectionRationale`
- **PlanningCollectorContext**: `contextId`, `sourceResultId`, `curation` (`PlanningCuration`), `selectionRationale`
- **TicketCollectorContext**: `contextId`, `sourceResultId`, `curation` (`TicketCuration`), `selectionRationale`

Each curated context references the full upstream result by `sourceResultId` but only carries the curated subset needed
for downstream prompts.

---

### 3. PreviousContext (Mixin Interface)

**Purpose**: Shared shape for previous attempt context when an agent is rerun. Implementations are per-agent and include discrete previous contexts instead of polymorphic curation lists.

| Field             | Type      | Description                      | Validation |
|-------------------|-----------|----------------------------------|------------|
| previousContextId | ContextId | ID of the previous attempt       | Not null   |
| serializedOutput  | String    | JSON-serialized previous output  | Not blank  |
| errorMessage      | String    | Error message if previous failed | Nullable   |
| errorStackTrace   | String    | Stack trace if previous failed   | Nullable   |
| attemptNumber     | int       | Which attempt this is (1-based)  | >= 1       |
| previousAttemptAt | Instant   | When previous attempt occurred   | Not null   |

#### PreviousContext Implementations

**Orchestrators**
- **OrchestratorPreviousContext**: previousDiscoveryCuration, previousPlanningCuration, previousTicketCuration
- **DiscoveryOrchestratorPreviousContext**: previousDiscoveryCuration, previousPlanningCuration (nullable), previousTicketCuration (nullable)
- **PlanningOrchestratorPreviousContext**: previousDiscoveryCuration, previousPlanningCuration
- **TicketOrchestratorPreviousContext**: previousDiscoveryCuration, previousPlanningCuration, previousTicketCuration

**Agents**
- **DiscoveryAgentPreviousContext**: previousDiscoveryResult (DiscoveryReport)
- **PlanningAgentPreviousContext**: previousPlanningResult (PlanningAgentResult)
- **TicketAgentPreviousContext**: previousTicketResult (TicketAgentResult)

**Collectors**
- **DiscoveryCollectorPreviousContext**: previousDiscoveryResult (DiscoveryCollectorResult), previousDiscoveryCuration
- **PlanningCollectorPreviousContext**: previousPlanningResult (PlanningCollectorResult), previousPlanningCuration
- **TicketCollectorPreviousContext**: previousTicketResult (TicketCollectorResult), previousTicketCuration
- **OrchestratorCollectorPreviousContext**: previousDiscoveryCuration, previousPlanningCuration, previousTicketCuration

**Review/Merger**
- **ReviewPreviousContext**: previousReviewEvaluation (ReviewEvaluation)
- **MergerPreviousContext**: previousMergerValidation (MergerValidation)

Previous curation fields are nullable and only populated when a workflow reroutes back with prior collector context.

**State Transitions**: None (immutable)

---

### 4. PromptContributor (Interface)

**Purpose**: Interface for tools to contribute prompting guidance to agents.

| Method                    | Return Type    | Description                         |
|---------------------------|----------------|-------------------------------------|
| name()                    | String         | Unique name for this contributor    |
| applicableAgents()        | Set<AgentType> | Which agent types this applies to   |
| contribute(PromptContext) | String         | Generate prompt contribution        |
| priority()                | int            | Ordering priority (lower = earlier) |

**Validation**:

- `name()` must be unique across all contributors
- `priority()` should be 0-1000

---

### 5. PromptContext

**Purpose**: Context provided to prompt contributors for generating contributions.

| Field            | Type                | Description                         |
|------------------|---------------------|-------------------------------------|
| agentType        | AgentType           | Type of agent being prompted        |
| currentContextId | ContextId           | ID for current execution            |
| upstreamContext  | UpstreamContext     | Typed upstream context (nullable)   |
| previousContext  | PreviousContext     | Previous attempt context (per-agent, nullable) |
| blackboardHistory | BlackboardHistory   | Blackboard history for prior runs (nullable) |
| metadata         | Map<String, Object> | Additional metadata                 |

---

### 6. PromptAssembly

**Purpose**: Service that assembles final prompts from base prompt + all applicable contributors.

| Method                          | Return Type             | Description                        |
|---------------------------------|-------------------------|------------------------------------|
| assemble(String, PromptContext) | String                  | Assemble final prompt              |
| getContributors(PromptContext)  | List<PromptContributor> | Get sorted applicable contributors |

---

### 7. DelegationTemplate

**Purpose**: Standardized output structure for orchestrator agents.

| Field               | Type                   | Description              | Validation       |
|---------------------|------------------------|--------------------------|------------------|
| schemaVersion       | String                 | Version of this schema   | Semantic version |
| resultId            | ContextId              | ID of this result        | Not null         |
| upstreamContextId   | ContextId              | ID of upstream context   | Not null         |
| goal                | String                 | Goal being delegated     | Not blank        |
| delegationRationale | String                 | Why this decomposition   | Not blank        |
| assignments         | List<AgentAssignment>  | Work assignments         | Not empty        |
| contextSelections   | List<ContextSelection> | Selected context to pass | Can be empty     |
| metadata            | Map<String, String>    | Training metadata        | Not null         |

---

### 8. AgentAssignment

**Purpose**: Individual work assignment within a delegation.

| Field          | Type                | Description               | Validation |
|----------------|---------------------|---------------------------|------------|
| agentId        | String              | ID for the assigned agent | Not blank  |
| agentType      | AgentType           | Type of agent             | Not null   |
| assignedGoal   | String              | Goal for this agent       | Not blank  |
| subdomainFocus | String              | Subdomain to focus on     | Nullable   |
| contextToPass  | Map<String, String> | Context to pass to agent  | Not null   |

---

### 9. ContextSelection

**Purpose**: Documents what context an orchestrator selected to pass downstream.

| Field              | Type      | Description           | Validation |
|--------------------|-----------|-----------------------|------------|
| selectionId        | String    | ID for this selection | Not blank  |
| sourceContextId    | ContextId | Where this came from  | Not null   |
| selectedContent    | String    | The selected content  | Not blank  |
| selectionRationale | String    | Why this was selected | Not blank  |

---

### 10. ConsolidationTemplate (Mixin Interface)

**Purpose**: Shared consolidation fields implemented by every collector result. This is a mix-in interface (not a serialized payload) that standardizes consolidation metadata and provides a uniform way to access curated contexts.

| Field                | Type                     | Description                                           | Validation       |
|----------------------|--------------------------|-------------------------------------------------------|------------------|
| schemaVersion        | String                   | Version of this schema                                | Semantic version |
| resultId             | ContextId                | ID of this result                                     | Not null         |
| inputs               | List<InputReference>     | References to inputs                                  | Not empty        |
| mergeStrategy        | String                   | How inputs were merged                                | Not blank        |
| conflictResolutions  | List<ConflictResolution> | How conflicts were resolved                           | Can be empty     |
| aggregatedMetrics    | Map<String, Double>      | Aggregated metrics                                    | Not null         |
| consolidatedOutput   | String                   | Consolidation summary/output                          | Not blank        |
| decision             | CollectorDecision        | Phase advancement decision                            | Not null         |
| upstreamContextChain | List<ContextId>          | Full context chain                                    | Not null         |
| metadata             | Map<String, String>      | Training metadata                                     | Not null         |
| curations            | List<Curation>           | Derived list of curated contexts produced by collector | Derived         |

Concrete collector results expose discrete curation fields (for example, `discoveryCuration`, `planningCuration`, `ticketCuration`). The `curations` list is derived from these fields (for example, `List.of(planningCuration, discoveryCuration)` with nulls omitted).

#### Curation (Mixin Interface)

**Purpose**: Shared shape for curated contexts produced by collectors.

| Field              | Type      | Description                         | Validation |
|--------------------|-----------|-------------------------------------|------------|
| contextId          | ContextId | ID of this curated context          | Not null   |
| sourceResultId     | ContextId | Collector result ID this came from  | Not null   |
| selectionRationale | String    | Why these items were selected       | Not blank  |

**Implementations**: `DiscoveryCollectorContext`, `PlanningCollectorContext`, `TicketCollectorContext`

---

### 11. InputReference

**Purpose**: Reference to an input consumed by a collector.

| Field          | Type      | Description              | Validation |
|----------------|-----------|--------------------------|------------|
| inputContextId | ContextId | ID of the input          | Not null   |
| inputType      | String    | Type of the input        | Not blank  |
| inputSummary   | String    | Summary of input content | Not blank  |

---

### 12. ConflictResolution

**Purpose**: Documents how a conflict was resolved during consolidation.

| Field               | Type            | Description             | Validation |
|---------------------|-----------------|-------------------------|------------|
| conflictDescription | String          | What conflicted         | Not blank  |
| resolutionApproach  | String          | How it was resolved     | Not blank  |
| conflictingInputs   | List<ContextId> | Which inputs conflicted | Not empty  |

---

## Discovery Entities

### 13. DiscoveryReport

**Purpose**: Enhanced structured output from discovery agents.

| Field                | Type                        | Description                    | Validation       |
|----------------------|-----------------------------|--------------------------------|------------------|
| schemaVersion        | String                      | Version of this schema         | Semantic version |
| resultId             | ContextId                   | ID of this result              | Not null         |
| upstreamContextId    | ContextId                   | ID of upstream context         | Not null         |
| fileReferences       | List<FileReference>         | Code file references           | Can be empty     |
| crossLinks           | List<CrossLink>             | Links between findings         | Can be empty     |
| semanticTags         | List<SemanticTag>           | Categorization tags            | >= 5 per module  |
| generatedQueries     | List<GeneratedQuery>        | Queries for retrieval training | >= 3 per module  |
| diagrams             | List<DiagramRepresentation> | Architectural diagrams         | Can be empty     |
| architectureOverview | String                      | High-level overview            | Not blank        |
| keyPatterns          | List<String>                | Key patterns found             | Can be empty     |
| integrationPoints    | List<String>                | Integration points             | Can be empty     |
| relevanceScores      | Map<String, Double>         | File → relevance mapping       | Not null         |

---

### 14. FileReference

**Purpose**: Reference to a specific location in the codebase.

| Field          | Type         | Description             | Validation      |
|----------------|--------------|-------------------------|-----------------|
| filePath       | String       | Path to file            | Not blank       |
| startLine      | int          | Starting line number    | >= 1            |
| endLine        | int          | Ending line number      | >= startLine    |
| relevanceScore | double       | Relevance 0.0-1.0       | 0.0 <= x <= 1.0 |
| snippet        | String       | Code snippet            | Not blank       |
| tags           | List<String> | Tags for this reference | Can be empty    |

---

### 15. CrossLink

**Purpose**: Connection between related findings.

| Field             | Type     | Description                 | Validation |
|-------------------|----------|-----------------------------|------------|
| linkId            | String   | Unique ID for this link     | Not blank  |
| sourceReferenceId | String   | ID of source finding        | Not blank  |
| targetReferenceId | String   | ID of target finding        | Not blank  |
| linkType          | LinkType | Type of relationship        | Not null   |
| description       | String   | Description of relationship | Not blank  |

**LinkType Enum**: `CALLS`, `DEPENDS_ON`, `IMPLEMENTS`, `EXTENDS`, `RELATED_TO`, `DATA_FLOW`

---

### 16. SemanticTag

**Purpose**: Categorization label for discoveries.

| Field       | Type         | Description                   | Validation      |
|-------------|--------------|-------------------------------|-----------------|
| tagId       | String       | Unique ID for this tag        | Not blank       |
| tagName     | String       | The tag name                  | Not blank       |
| tagCategory | TagCategory  | Category of tag               | Not null        |
| confidence  | double       | Confidence 0.0-1.0            | 0.0 <= x <= 1.0 |
| appliedTo   | List<String> | Reference IDs this applies to | Not empty       |

**TagCategory Enum**: `ARCHITECTURE`, `PATTERN`, `DATA_FLOW`, `ENTRY_POINT`, `TEST`, `INTEGRATION`, `SECURITY`,
`PERFORMANCE`

---

### 17. GeneratedQuery

**Purpose**: Search query for retrieval model training.

| Field                | Type                | Description                    | Validation |
|----------------------|---------------------|--------------------------------|------------|
| queryId              | String              | Unique ID for this query       | Not blank  |
| queryText            | String              | The query text                 | Not blank  |
| expectedResultPaths  | List<String>        | Expected file paths in results | Not empty  |
| queryType            | QueryType           | Type of query                  | Not null   |
| groundTruthRelevance | Map<String, Double> | Path → relevance ground truth  | Not null   |

**QueryType Enum**: `CODE_SEARCH`, `PATTERN_FIND`, `DEPENDENCY_TRACE`, `API_LOOKUP`, `TEST_FIND`

---

### 18. DiagramRepresentation

**Purpose**: Structured diagram data.

| Field         | Type              | Description       | Validation   |
|---------------|-------------------|-------------------|--------------|
| diagramId     | String            | Unique ID         | Not blank    |
| diagramType   | DiagramType       | Type of diagram   | Not null     |
| title         | String            | Diagram title     | Not blank    |
| nodes         | List<DiagramNode> | Nodes in diagram  | Not empty    |
| edges         | List<DiagramEdge> | Edges in diagram  | Can be empty |
| mermaidSource | String            | Mermaid.js source | Nullable     |

**DiagramType Enum**: `CLASS`, `SEQUENCE`, `COMPONENT`, `DATA_FLOW`, `DEPENDENCY`

---

## Discovery Collector Entities

### 19. DiscoveryCollectorResult (Enhanced)

**Purpose**: Consolidated discovery output with recommendations and query findings. Implements `ConsolidationTemplate`.

| Field                | Type                       | Description                           | Validation       |
|----------------------|----------------------------|---------------------------------------|------------------|
| schemaVersion        | String                     | Version of this schema                | Semantic version |
| resultId             | ContextId                  | ID of this result                     | Not null         |
| inputs               | List<InputReference>       | References to inputs                  | Not empty        |
| mergeStrategy        | String                     | How inputs were merged                | Not blank        |
| conflictResolutions  | List<ConflictResolution>   | How conflicts were resolved           | Can be empty     |
| aggregatedMetrics    | Map<String, Double>        | Aggregated metrics                    | Not null         |
| consolidatedOutput   | String                     | Consolidation summary/output          | Not blank        |
| decision             | CollectorDecision          | Phase advancement decision            | Not null         |
| upstreamContextChain | List<ContextId>            | Full context chain                    | Not null         |
| metadata             | Map<String, String>        | Training metadata                     | Not null         |
| unifiedCodeMap       | CodeMap                    | Merged code understanding             | Not null         |
| recommendations      | List<Recommendation>       | Actionable recommendations            | Can be empty     |
| querySpecificFindings| Map<String, QueryFindings> | Query → findings mapping              | Not null         |
| discoveryCuration    | DiscoveryCollectorContext  | Curated discovery context for downstream | Not null      |

---

### 20. CodeMap

**Purpose**: Unified view of codebase structure.

| Field                  | Type                  | Description       | Validation |
|------------------------|-----------------------|-------------------|------------|
| modules                | List<ModuleOverview>  | Module summaries  | Not empty  |
| deduplicatedReferences | List<FileReference>   | Merged references | Not empty  |
| unifiedDiagram         | DiagramRepresentation | Composite diagram | Not null   |
| mergedTags             | List<SemanticTag>     | All tags merged   | Not empty  |

---

### 21. Recommendation

**Purpose**: Actionable recommendation for planning phase.

| Field                  | Type                   | Description              | Validation   |
|------------------------|------------------------|--------------------------|--------------|
| recommendationId       | String                 | Unique ID                | Not blank    |
| title                  | String                 | Short title              | Not blank    |
| description            | String                 | Full description         | Not blank    |
| priority               | RecommendationPriority | Priority level           | Not null     |
| supportingDiscoveryIds | List<ContextId>        | Supporting discovery IDs | Not empty    |
| relatedFilePaths       | List<String>           | Related files            | Can be empty |
| estimatedImpact        | String                 | Impact description       | Nullable     |

**RecommendationPriority Enum**: `CRITICAL`, `HIGH`, `MEDIUM`, `LOW`

---

### 22. QueryFindings

**Purpose**: Results for a specific generated query.

| Field           | Type                | Description         | Validation      |
|-----------------|---------------------|---------------------|-----------------|
| originalQuery   | String              | The query text      | Not blank       |
| results         | List<FileReference> | Found references    | Can be empty    |
| confidenceScore | double              | Confidence 0.0-1.0  | 0.0 <= x <= 1.0 |
| summary         | String              | Summary of findings | Not blank       |

---

## Planning Entities

### 23. PlanningTicket (Enhanced)

**Purpose**: Structured work item from planning agents.

| Field              | Type                | Description                 | Validation       |
|--------------------|---------------------|-----------------------------|------------------|
| schemaVersion      | String              | Version of this schema      | Semantic version |
| resultId           | ContextId           | ID of this result           | Not null         |
| ticketId           | String              | Unique ticket identifier    | Not blank        |
| title              | String              | Ticket title                | Not blank        |
| description        | String              | Full description            | Not blank        |
| tasks              | List<TicketTask>    | Implementation tasks        | Not empty        |
| dependencies       | List<String>        | Dependent ticket IDs        | Can be empty     |
| acceptanceCriteria | List<String>        | Acceptance criteria         | Not empty        |
| effortEstimate     | String              | Estimated effort            | Nullable         |
| discoveryLinks     | List<DiscoveryLink> | Links to discovery findings | Can be empty     |
| priority           | int                 | Priority 1-5                | 1 <= x <= 5      |

---

### 24. TicketTask

**Purpose**: Individual task within a ticket.

| Field          | Type         | Description      | Validation     |
|----------------|--------------|------------------|----------------|
| taskId         | String       | Unique task ID   | Not blank      |
| description    | String       | Task description | Not blank      |
| estimatedHours | Double       | Estimated hours  | Nullable, >= 0 |
| relatedFiles   | List<String> | Files to modify  | Can be empty   |

---

### 25. DiscoveryLink

**Purpose**: Link from planning ticket back to discovery finding.

| Field             | Type      | Description                        | Validation |
|-------------------|-----------|------------------------------------|------------|
| discoveryResultId | ContextId | Discovery collector result ID      | Not null   |
| referenceId       | String    | Specific reference ID in discovery | Not blank  |
| linkRationale     | String    | Why this is linked                 | Not blank  |

---

### 26. PlanningAgentResult

**Purpose**: Structured output from planning agents.

| Field             | Type                 | Description                   | Validation       |
|-------------------|----------------------|-------------------------------|------------------|
| schemaVersion     | String               | Version of this schema        | Semantic version |
| resultId          | ContextId            | ID of this result             | Not null         |
| upstreamContextId | ContextId            | ID of upstream context        | Not null         |
| discoveryResultId | ContextId            | Discovery collector result ID | Not null         |
| tickets           | List<PlanningTicket> | Proposed tickets              | Not empty        |

---

### 27. PlanningCollectorResult

**Purpose**: Consolidated planning output with dependency graph. Implements `ConsolidationTemplate`.

| Field                | Type                     | Description                           | Validation       |
|----------------------|--------------------------|---------------------------------------|------------------|
| schemaVersion        | String                   | Version of this schema                | Semantic version |
| resultId             | ContextId                | ID of this result                     | Not null         |
| inputs               | List<InputReference>     | References to inputs                  | Not empty        |
| mergeStrategy        | String                   | How inputs were merged                | Not blank        |
| conflictResolutions  | List<ConflictResolution> | How conflicts were resolved           | Can be empty     |
| aggregatedMetrics    | Map<String, Double>      | Aggregated metrics                    | Not null         |
| consolidatedOutput   | String                   | Consolidation summary/output          | Not blank        |
| decision             | CollectorDecision        | Phase advancement decision            | Not null         |
| upstreamContextChain | List<ContextId>          | Full context chain                    | Not null         |
| metadata             | Map<String, String>      | Training metadata                     | Not null         |
| finalizedTickets     | List<PlanningTicket>     | Finalized tickets                     | Not empty        |
| dependencyGraph      | List<TicketDependency>   | Ticket dependency edges               | Can be empty     |
| discoveryResultId    | ContextId                | Discovery collector result ID         | Not null         |
| planningCuration     | PlanningCollectorContext | Curated planning context for downstream | Not null       |

---

### 28. TicketDependency

**Purpose**: Edge in a ticket dependency graph.

| Field          | Type   | Description         | Validation |
|----------------|--------|---------------------|------------|
| fromTicketId   | String | Source ticket ID    | Not blank  |
| toTicketId     | String | Dependent ticket ID | Not blank  |
| dependencyType | String | Relationship type   | Not blank  |

---

## Ticket Entities

### 29. TicketAgentResult

**Purpose**: Structured output from ticket agents.

| Field                 | Type            | Description                   | Validation       |
|-----------------------|-----------------|-------------------------------|------------------|
| schemaVersion         | String          | Version of this schema        | Semantic version |
| resultId              | ContextId       | ID of this result             | Not null         |
| upstreamContextId     | ContextId       | ID of upstream context        | Not null         |
| ticketId              | String          | Ticket identifier             | Not blank        |
| discoveryResultId     | ContextId       | Discovery collector result ID | Not null         |
| implementationSummary | String          | Summary of implementation     | Not blank        |
| filesModified         | List<String>    | Files changed                 | Can be empty     |
| testResults           | List<String>    | Test outcomes                 | Can be empty     |
| commits               | List<String>    | Commit references             | Can be empty     |
| verificationStatus    | String          | Verification status           | Not blank        |
| upstreamContextChain  | List<ContextId> | Full upstream context chain   | Not empty        |

---

### 30. TicketCollectorResult

**Purpose**: Consolidated ticket execution output. Implements `ConsolidationTemplate`.

| Field                | Type                     | Description                           | Validation       |
|----------------------|--------------------------|---------------------------------------|------------------|
| schemaVersion        | String                   | Version of this schema                | Semantic version |
| resultId             | ContextId                | ID of this result                     | Not null         |
| inputs               | List<InputReference>     | References to inputs                  | Not empty        |
| mergeStrategy        | String                   | How inputs were merged                | Not blank        |
| conflictResolutions  | List<ConflictResolution> | How conflicts were resolved           | Can be empty     |
| aggregatedMetrics    | Map<String, Double>      | Aggregated metrics                    | Not null         |
| consolidatedOutput   | String                   | Consolidation summary/output          | Not blank        |
| decision             | CollectorDecision        | Phase advancement decision            | Not null         |
| upstreamContextChain | List<ContextId>          | Full context chain                    | Not null         |
| metadata             | Map<String, String>      | Training metadata                     | Not null         |
| completionStatus     | String                   | Completion status                     | Not blank        |
| followUps            | List<String>             | Follow-up items                       | Can be empty     |
| ticketCuration       | TicketCollectorContext   | Curated ticket context for downstream | Not null         |

---

## Curated Context Entities

### 31. ConsolidationSummary

**Purpose**: Curated view of consolidation metadata for downstream prompts.

| Field                | Type                     | Description                           | Validation   |
|----------------------|--------------------------|---------------------------------------|--------------|
| inputs               | List<InputReference>     | Curated input references              | Can be empty |
| mergeStrategy        | String                   | How inputs were merged                | Not blank    |
| conflictResolutions  | List<ConflictResolution> | How conflicts were resolved           | Can be empty |
| aggregatedMetrics    | Map<String, Double>      | Aggregated metrics                    | Not null     |
| consolidatedOutput   | String                   | Consolidation summary/output          | Not blank    |
| decision             | CollectorDecision        | Phase advancement decision            | Not null     |
| upstreamContextChain | List<ContextId>          | Full context chain                    | Not empty    |
| metadata             | Map<String, String>      | Training metadata                     | Not null     |

---

### 32. DiscoveryCuration

**Purpose**: Curated subset of discovery results for downstream agents.

| Field                 | Type                       | Description                     | Validation   |
|-----------------------|----------------------------|---------------------------------|--------------|
| discoveryReports      | List<DiscoveryReport>      | Curated discovery agent reports | Can be empty |
| unifiedCodeMap        | CodeMap                    | Curated unified code map        | Not null     |
| recommendations       | List<Recommendation>       | Curated recommendations         | Can be empty |
| querySpecificFindings | Map<String, QueryFindings> | Curated query findings          | Can be empty |
| consolidationSummary  | ConsolidationSummary       | Curated consolidation metadata  | Not null     |

---

### 33. DiscoveryCollectorContext

**Purpose**: Typed curated context derived from DiscoveryCollectorResult. Implements `Curation`.

| Field              | Type              | Description                   | Validation |
|--------------------|-------------------|-------------------------------|------------|
| contextId          | ContextId         | ID of this curated context    | Not null   |
| sourceResultId     | ContextId         | DiscoveryCollectorResult ID   | Not null   |
| curation           | DiscoveryCuration | Curated discovery content     | Not null   |
| selectionRationale | String            | Why these items were selected | Not blank  |

---

### 34. PlanningCuration

**Purpose**: Curated subset of planning results for downstream agents.

| Field                | Type                      | Description                    | Validation   |
|----------------------|---------------------------|--------------------------------|--------------|
| planningAgentResults | List<PlanningAgentResult> | Curated planning agent outputs | Can be empty |
| finalizedTickets     | List<PlanningTicket>      | Curated finalized tickets      | Can be empty |
| dependencyGraph      | List<TicketDependency>    | Curated dependency graph       | Can be empty |
| discoveryResultId    | ContextId                 | Discovery collector result ID  | Not null     |
| consolidationSummary | ConsolidationSummary      | Curated consolidation metadata | Not null     |

---

### 35. PlanningCollectorContext

**Purpose**: Typed curated context derived from PlanningCollectorResult. Implements `Curation`.

| Field              | Type             | Description                   | Validation |
|--------------------|------------------|-------------------------------|------------|
| contextId          | ContextId        | ID of this curated context    | Not null   |
| sourceResultId     | ContextId        | PlanningCollectorResult ID    | Not null   |
| curation           | PlanningCuration | Curated planning content      | Not null   |
| selectionRationale | String           | Why these items were selected | Not blank  |

---

### 36. TicketCuration

**Purpose**: Curated subset of ticket results for downstream agents.

| Field                | Type                    | Description                    | Validation   |
|----------------------|-------------------------|--------------------------------|--------------|
| ticketAgentResults   | List<TicketAgentResult> | Curated ticket agent outputs   | Can be empty |
| completionStatus     | String                  | Completion status              | Not blank    |
| followUps            | List<String>            | Follow-up items                | Can be empty |
| consolidationSummary | ConsolidationSummary    | Curated consolidation metadata | Not null     |

---

### 37. TicketCollectorContext

**Purpose**: Typed curated context derived from TicketCollectorResult. Implements `Curation`.

| Field              | Type           | Description                    | Validation |
|--------------------|----------------|--------------------------------|------------|
| contextId          | ContextId      | ID of this curated context     | Not null   |
| sourceResultId     | ContextId      | TicketCollectorResult ID       | Not null   |
| curation           | TicketCuration | Curated implementation content | Not null   |
| selectionRationale | String         | Why these items were selected  | Not blank  |

---

## Entity Relationships

```
ContextId ─────────────────────────────────────────────────────────────────┐
    │                                                                       │
    ├──▶ UpstreamContext.contextId                                         │
    ├──▶ PreviousContext.previousContextId                                 │
    ├──▶ DelegationTemplate.resultId, upstreamContextId                    │
    ├──▶ ConsolidationTemplate.resultId, upstreamContextChain[]            │
    ├──▶ DiscoveryReport.resultId, upstreamContextId                       │
    ├──▶ PlanningTicket.resultId                                           │
    ├──▶ PlanningAgentResult.resultId                                      │
    ├──▶ TicketAgentResult.resultId                                        │
    ├──▶ DiscoveryCollectorContext.contextId                               │
    ├──▶ PlanningCollectorContext.contextId                                │
    ├──▶ TicketCollectorContext.contextId                                  │
    └──▶ InputReference.inputContextId                                     │
                                                                            │
PromptContributor ──▶ PromptContributorRegistry ──▶ PromptAssembly         │
                                                          │                 │
                                                          ▼                 │
                                                    PromptContext ◀─────────┘
                                                          │
                                                          ├── currentContextId
                                                          ├── upstreamContext
                                                          ├── previousContext
                                                          └── blackboardHistory

DelegationTemplate ──┬──▶ AgentAssignment[]
                     └──▶ ContextSelection[]

ConsolidationTemplate (mixin) ──┬──▶ InputReference[]
                               ├──▶ ConflictResolution[]
                               ├──▶ CollectorDecision
                               └──▶ Curation[]

DiscoveryReport ──┬──▶ FileReference[]
                  ├──▶ CrossLink[]
                  ├──▶ SemanticTag[]
                  ├──▶ GeneratedQuery[]
                  └──▶ DiagramRepresentation[]

DiscoveryCollectorResult (implements ConsolidationTemplate) ──┬──▶ CodeMap
                                                             ├──▶ Recommendation[]
                                                             └──▶ QueryFindings[]

PlanningAgentResult ──▶ PlanningTicket[]
PlanningCollectorResult (implements ConsolidationTemplate) ──┬──▶ PlanningTicket[]
                                                             └──▶ TicketDependency[]

TicketAgentResult ──▶ (implementation summary, files, tests, commits)
TicketCollectorResult (implements ConsolidationTemplate)

DiscoveryCollectorContext ──▶ DiscoveryCuration ──┬──▶ DiscoveryReport[]
                                                 ├──▶ CodeMap
                                                 ├──▶ Recommendation[]
                                                 ├──▶ QueryFindings[]
                                                 └──▶ ConsolidationSummary
PlanningCollectorContext ──▶ PlanningCuration ───▶ PlanningAgentResult[]
                                                 ├──▶ PlanningTicket[]
                                                 ├──▶ TicketDependency[]
                                                 └──▶ ConsolidationSummary
TicketCollectorContext ──▶ TicketCuration ───────▶ TicketAgentResult[]
                                                 ├──▶ (completion status, follow-ups)
                                                 └──▶ ConsolidationSummary

PlanningTicket ──┬──▶ TicketTask[]
                 └──▶ DiscoveryLink[] ──▶ ContextId (discovery result)
```

## Validation Summary

| Entity                | Key Validations                            |
|-----------------------|--------------------------------------------|
| ContextId             | workflowRunId format, sequenceNumber >= 1  |
| PromptContributor     | unique name, priority 0-1000               |
| DelegationTemplate    | non-empty assignments, valid version       |
| ConsolidationTemplate | non-empty inputs, valid decision           |
| DiscoveryReport       | >= 5 tags, >= 3 queries per module         |
| FileReference         | relevance 0.0-1.0, startLine <= endLine    |
| PlanningTicket        | non-empty tasks and criteria, priority 1-5 |
