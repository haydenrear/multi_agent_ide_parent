# Data Model: Execution Artifacts, Templates, and Semantics

**Feature**: `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/008-artifacts/spec.md`  
**Date**: 2026-01-24

## Overview

The canonical record is an **execution artifact tree** (execution-scoped artifacts) plus **shared template artifacts** (PromptTemplate versions) and **post-hoc semantic representations** that reference source artifacts.

This document describes conceptual entities and relationships; storage technology and schema details are implementation choices.

## Core Entities

### Execution

Represents a single workflow run and is the root of an execution artifact tree.

**Fields**

- `executionKey`: ArtifactKey (root)
- `workflowRunId`: String
- `startedAt`: Timestamp
- `finishedAt`: Timestamp (optional)
- `status`: Enum (running/completed/failed)

**Relationships**

- 1 Execution → N execution-scoped Artifacts (all keys under the execution root prefix)

### ArtifactKey

Hierarchical, time-sortable identifier for artifacts.

**Format**: `ak:<ulid-segment>/<ulid-segment>/...`

**Structure**:
- Prefix: `ak:`
- Segments: ULID values (26 chars, Crockford Base32)
- Separator: `/`

**ULID Properties**:
- First 10 chars: millisecond timestamp (48 bits)
- Last 16 chars: randomness (80 bits)
- Lexicographically sortable by creation time

**Examples**:
```text
ak:01HQXK5V8RPZM4J3NHWGC7SDFG                                           # root
ak:01HQXK5V8RPZM4J3NHWGC7SDFG/01HQXK5V9KPZM4J3NHWGC7SDXY                # child
ak:01HQXK5V8RPZM4J3NHWGC7SDFG/01HQXK5V9KPZM4J3NHWGC7SDXY/01HQXK5VAKXYZ  # grandchild
```

### ArtifactKey Parent Derivation (Implementation Pattern)

**Critical Rule**: Child artifact keys MUST be derived from the parent agent's `ArtifactKey`, not from the workflow run ID.

In a multi-agent workflow, the artifact tree mirrors the agent tree. Each agent request carries a `contextId` (its `ArtifactKey`), and all artifacts emitted during that agent's execution are children of that key.

**Parent Lookup by Request Type**:

The parent for a given request/artifact type is determined by the execution context (BlackboardHistory):

| Request/Artifact Type | Parent Type | Rationale |
|-----------------------|-------------|-----------|
| `OrchestratorRequest` | (root) | Top-level orchestrator |
| `DiscoveryOrchestratorRequest` | `OrchestratorRequest` | Phase orchestrator |
| `DiscoveryAgentRequests` | `DiscoveryOrchestratorRequest` | Dispatch container |
| `DiscoveryAgentRequest` | `DiscoveryAgentRequests` | Individual agent under dispatch |
| `DiscoveryAgentResults` | `DiscoveryOrchestratorRequest` | Results container |
| `DiscoveryCollectorRequest` | `DiscoveryOrchestratorRequest` | Collector under phase |
| `PlanningOrchestratorRequest` | `OrchestratorRequest` | Phase orchestrator |
| `PlanningAgentRequests` | `PlanningOrchestratorRequest` | Dispatch container |
| `PlanningAgentRequest` | `PlanningOrchestratorRequest` | Individual agent |
| `TicketOrchestratorRequest` | `OrchestratorRequest` | Phase orchestrator |
| `TicketAgentRequests` | `TicketOrchestratorRequest` | Dispatch container |
| `TicketAgentRequest` | `TicketOrchestratorRequest` | Individual agent |
| `ReviewRequest` | `AgentRequest` or `AgentResult` | Review of prior work |
| `MergerRequest` | `AgentRequest` or `AgentResult` | Merge of prior work |
| `RenderedPrompt` | Current `AgentRequest` | Prompt for this agent |
| `PromptArgs` | `RenderedPrompt` | Args for this prompt |
| `ToolCallArtifact` | Current `AgentRequest` | Tool call by this agent |
| `InterruptRequest` | Parent request being interrupted | Interrupt inherits parent |

**Implementation**: See `RequestEnrichment.findParentForInput()` in `multi_agent_ide_lib`.

**Example Tree**:
```text
ak:01HQX.../                                      # Execution root
├── ak:01HQX.../01HQX...                          # OrchestratorRequest
│   ├── ak:01HQX.../01HQX.../01HQX...             # DiscoveryOrchestratorRequest
│   │   ├── ak:.../01HQX.../01HQX.../01HQX...     # DiscoveryAgentRequests (dispatch)
│   │   │   ├── ak:.../.../.../.../01HQX...       # DiscoveryAgentRequest[0]
│   │   │   │   └── ak:.../.../.../.../...        # RenderedPrompt for agent[0]
│   │   │   └── ak:.../.../.../.../01HQX...       # DiscoveryAgentRequest[1]
│   │   │       └── ak:.../.../.../.../...        # RenderedPrompt for agent[1]
│   │   └── ak:.../01HQX.../01HQX.../01HQX...     # DiscoveryCollectorRequest
```

### ContentHash

Stable identifier computed from canonical bytes using SHA-256.

**Format**: 64-character lowercase hexadecimal string

**Example**: `a3f2b8c9d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1`

### Artifact (execution-scoped)

Represents a node in the execution tree (prompt renders, args, tool I/O, config, outcomes, captured events).

**Fields**

- `artifactKey`: ArtifactKey (hierarchical, time-sortable; includes creation-time component)
- `artifactType`: String/Enum (ExecutionConfig, RenderedPrompt, PromptArgs, ToolCall, ToolOutput, OutcomeEvidence, EventArtifact, PromptContribution, RefArtifact, etc.)
- `createdAt`: Timestamp (derivable from ArtifactKey time component)
- `contentBytes`: Bytes/Text (optional)
- `contentHash`: ContentHash (optional; required when bytes exist; SHA-256 hex)
- `metadata`: Canonical JSON (optional; small structured fields)
- `children`: List<Artifact> (optional; execution-scoped child artifacts)

**Relationships**

- Artifact is a self-referential tree structure: each Artifact may contain zero or more child Artifacts.
- Tree edges are represented by key prefix (child keys start with parent key) and by explicit `children` references in the in-memory model.
- Non-tree dependencies are represented by `RefArtifact` nodes (see below).
- ArtifactKey locality: keys encode proximity in the tree, so artifacts with a shared prefix are spatially and semantically local in both storage and traversal.
- ArtifactKey indexing: the hierarchical key structure is well-suited to trie/prefix indexing to support fast subtree queries and prefix scans.

### RefArtifact

Explicit reference edge used when the dependency is not represented by containment.

**Fields**

- `artifactKey`: ArtifactKey (execution-scoped)
- `targetArtifactKey`: ArtifactKey (execution-scoped or shared)
- `relationType`: String/Enum (uses-template, depends-on, references, etc.)

### ExecutionConfig

Reconstructability-critical configuration snapshot.

**Fields**

- `artifactKey`: ArtifactKey
- `repositorySnapshotId`: String (e.g., commit SHA)
- `modelRefs`: Canonical JSON (model identifiers + parameters)
- `toolPolicy`: Canonical JSON (tool availability/limits)
- `routingPolicy`: Canonical JSON (routing/planning/loop parameters)

### RenderedPrompt

Stores the fully rendered prompt text plus references to its static template version and dynamic inputs.

**Fields**

- `artifactKey`: ArtifactKey
- `renderedText`: Text
- `renderedTextHash`: ContentHash

**Relationships**

- RenderedPrompt → RefArtifact → PromptTemplateVersion (shared) OR embedded template artifact (allowed by spec)
- RenderedPrompt → PromptArgs (child)
- RenderedPrompt → PromptContribution (0..N children)
- RenderedPrompt is typically a child of a PromptExecution artifact (see hierarchy below)

### PromptArgs

Canonical representation of the dynamic inputs bound into a template.

**Fields**

- `artifactKey`: ArtifactKey
- `argsJson`: Canonical JSON
- `contentHash`: ContentHash (hash of canonical JSON bytes)

### PromptContribution

Represents a single `PromptContributor` output that participated in assembling a prompt.

**Fields**

- `artifactKey`: ArtifactKey
- `contributorName`: String
- `priority`: Integer
- `agentTypes`: List (optional)
- `contributedText`: Text
- `contentHash`: ContentHash (optional; hash of contributed text)
- `orderIndex`: Integer (ordering in final assembly for reproducibility)

### ToolCall / ToolIO

Captures tool invocations, inputs, outputs, and errors.

**Fields**

- `artifactKey`: ArtifactKey
- `toolName`: String
- `inputBytes`: Bytes/Text (optional)
- `inputHash`: ContentHash (optional)
- `outputBytes`: Bytes/Text (optional)
- `outputHash`: ContentHash (optional)
- `error`: Text (optional)

### OutcomeEvidence

Objective evidence for outcomes (tests/build/merge/human acknowledgements).

**Fields**

- `artifactKey`: ArtifactKey
- `evidenceType`: String/Enum (test-result, build-result, merge-result, human-ack, etc.)
- `payload`: Canonical JSON or text blob
- `contentHash`: ContentHash (optional)

### EventArtifact (GraphEvent capture)

Stores workflow GraphEvents as source artifacts for replay/debugging.

**Fields**

- `artifactKey`: ArtifactKey
- `eventId`: String
- `nodeId`: String
- `eventTimestamp`: Timestamp
- `eventType`: String (matches GraphEvent.eventType)
- `payloadJson`: Canonical JSON (event fields)
- `contentHash`: ContentHash (hash of canonical JSON bytes)

**Notes**

- All event variants in `utilitymodule/.../Events.java` map into `EventArtifact`.
- The execution tree may additionally include structural nodes (WorkflowNode scopes) that events reference via `nodeId`.

### MessageStreamArtifact (model output stream)

Captures token/stream deltas, thought deltas, and message fragments emitted during model interaction.

**Fields**

- `artifactKey`: ArtifactKey
- `streamType`: Enum (NodeStreamDeltaEvent | NodeThoughtDeltaEvent | UserMessageChunkEvent | AddMessageEvent)
- `nodeId`: String
- `eventTimestamp`: Timestamp
- `payloadJson`: Canonical JSON
- `contentHash`: ContentHash (optional)

## Shared Template Entities

### TemplateStaticId

Hierarchical, dot-separated identifier grouping related templates semantically.

**Format**: `tpl.<category>.<subcategory>...<name>`

**Rules**:
- Prefix: `tpl.`
- Segments: lowercase alphanumeric with underscores
- Separator: `.`
- Max depth: 8 levels
- Max segment length: 64 chars
- Max total length: 256 chars

**Validation Regex**: `^tpl\.([a-z][a-z0-9_]{0,63}\.){1,7}[a-z][a-z0-9_]{0,63}$`

**Reserved Prefixes**:
- `tpl.agent.*` — Agent-specific prompt templates
- `tpl.system.*` — System-level templates
- `tpl.workflow.*` — Workflow orchestration templates
- `tpl.tool.*` — Tool-specific templates

**Examples**:
```text
tpl.agent.discovery.system_prompt
tpl.agent.ticket.loop_builder.system
tpl.workflow.planning.initial
```

### PromptTemplateVersion (shared)

Represents a versioned static prompt template reused across many executions.

**Fields**

- `templateStaticId`: TemplateStaticId (hierarchical, referential; see format above)
- `templateText`: Text (static; no unresolved placeholders)
- `contentHash`: ContentHash (SHA-256 hex of UTF-8 template text bytes)
- `templateArtifactKey`: ArtifactKey (stable across executions; changes only when template text changes)
- `lastUpdatedAt`: Timestamp (time component reflected in templateArtifactKey)
- `sourceLocation`: String (optional; resource path / contributor origin)

**Relationships**

- Many RenderedPrompt artifacts reference one PromptTemplateVersion via RefArtifact.

### Templated (contract)

Represents the reusable template contract across template types (starting with PromptTemplate).

**Contract Fields**

- `templateStaticId`
- `templateText` (or equivalent static representation)
- `contentHash`
- `templateArtifactKey`
- `render(inputs) -> renderedText`

## Semantic Layer

### SemanticRepresentation

Computed post-hoc and attached by reference. Must not mutate source artifacts.

**Fields**

- `semanticKey`: String/UUID
- `targetArtifactKey`: ArtifactKey
- `derivationRecipeId`: String
- `derivationRecipeVersion`: String
- `modelRef`: String
- `createdAt`: Timestamp
- `qualityMetadata`: Canonical JSON
- `payloadType`: Enum (Embedding, Summary, SemanticIndexRef, ...)
- `payload`: Bytes/JSON

## Key Constraints (From Spec)

- **Prefix rule**: if B is a child of A, then `B.artifactKey` starts with `A.artifactKey`.
- **Uniqueness**: execution-scoped ArtifactKeys are unique within an execution tree.
- **Shared templates**: PromptTemplateVersion ArtifactKeys are stable and reused across executions; new keys only when template text changes.
- **Hashing**: any artifact with bytes has a contentHash computed from canonical bytes.
- **Immutability**: source artifacts never mutate; later interpretations are semantic representations.

## Execution Tree Hierarchy (Conceptual)

The execution artifact tree is a self-referential structure of Artifacts with hierarchical keys. The prompt portion of the tree decomposes into static template artifacts and dynamic prompt execution artifacts.

```text
Execution (root)
├── ExecutionConfig
├── InputArtifacts
│   ├── AgentRequestArtifact
│   └── PromptExecution
│       ├── RenderedPrompt
│       │   ├── RefArtifact -> PromptTemplateVersion (shared)
│       │   ├── PromptArgs
│       │   └── PromptContribution (0..N)
│       └── MessageStreamArtifact (0..N)   # stream/thought deltas, user chunks
├── AgentExecutionArtifacts
│   ├── ToolCall / ToolIO
│   ├── EventArtifact (GraphEvent capture)
│   └── AgentResultArtifact
└── OutcomeEvidenceArtifacts
    └── OutcomeEvidence
```

Notes:

- PromptTemplateVersion is a shared artifact (stable ArtifactKey across executions) and is typically referenced via RefArtifact.
- PromptExecution is an execution-scoped artifact that groups a rendered prompt with its dynamic inputs and related stream/message artifacts.
- Child relationships can be represented both by key prefix and by explicit `children` references in the artifact model.

## Event Variant Mapping (GraphEvent -> EventArtifact)

The following GraphEvent variants MUST be captured as EventArtifact nodes. These cover tool calls, UI updates, stream deltas, and messages from the model/user:

- `NodeAddedEvent`
- `ActionStartedEvent`
- `ActionCompletedEvent`
- `StopAgentEvent`
- `PauseEvent`
- `ResumeEvent`
- `ResolveInterruptEvent`
- `AddMessageEvent` (message emitted into the workflow stream)
- `NodeStatusChangedEvent`
- `NodeErrorEvent`
- `NodeBranchedEvent`
- `NodePrunedEvent`
- `NodeReviewRequestedEvent`
- `InterruptStatusEvent`
- `GoalCompletedEvent`
- `WorktreeCreatedEvent`
- `WorktreeBranchedEvent`
- `WorktreeMergedEvent`
- `WorktreeDiscardedEvent`
- `NodeUpdatedEvent`
- `NodeDeletedEvent`
- `NodeStreamDeltaEvent` (streaming output delta)
- `NodeThoughtDeltaEvent` (streaming thoughts delta)
- `ToolCallEvent`
- `GuiRenderEvent`
- `UiDiffAppliedEvent`
- `UiDiffRejectedEvent`
- `UiDiffRevertedEvent`
- `UiFeedbackEvent`
- `NodeBranchRequestedEvent`
- `PlanUpdateEvent`
- `UserMessageChunkEvent` (user message fragments)
- `CurrentModeUpdateEvent`
- `AvailableCommandsUpdateEvent`
- `PermissionRequestedEvent`
- `PermissionResolvedEvent`
- `UiStateSnapshot`

## Agent Model Artifacts

The AgentModels request/response records in
`multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AgentModels.java`
are emitted as artifacts so that each agent interaction is reconstructable.

**Artifact types**

- AgentRequestArtifact (serialized AgentModels.AgentRequest)
- AgentResultArtifact (serialized AgentModels.AgentResult)
- InterruptRequestArtifact (serialized AgentModels.InterruptRequest)
- InterruptResolutionArtifact (serialized AgentModels.InterruptResolution)
- CollectorDecisionArtifact (serialized AgentModels.CollectorDecision)

Each artifact stores:

- `artifactKey` (execution-scoped)
- `agentType` / `nodeId` / `interactionType` metadata
- canonical JSON payload of the record
- optional contentHash (canonical JSON bytes)

These artifacts are emitted by the workflow agents in
`multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java`
and nested under the correct workflow/sub-agent scope.

## Workflow Prompt Templates

Prompt templates under `multi_agent_ide/src/main/resources/prompts/workflow/` are
loaded at startup and persisted as PromptTemplateVersion artifacts. Each template
version is keyed by static template ID + content hash + stable template ArtifactKey.

## Request Enrichment Infrastructure

The request enrichment infrastructure ensures every agent request carries a properly derived `ArtifactKey` (contextId) before execution, enabling correct artifact tree construction.

### Components

**ContextIdService** (`multi_agent_ide_lib/.../prompt/ContextIdService.java`)

Generates child `ArtifactKey` values from parent keys:

```java
public ArtifactKey generate(String workflowRunId, AgentType agentType, Artifact.AgentModel parent) {
    // Priority 1: If workflowRunId is itself a valid ArtifactKey, create child from it
    if (workflowRunId != null && ArtifactKey.isValid(workflowRunId)) {
        return new ArtifactKey(workflowRunId).createChild();
    }
    // Priority 2: If parent model exists, create child from parent's key
    if (parent != null) {
        return parent.key().createChild();
    }
    // Priority 3: Only create root if no parent context exists
    return ArtifactKey.createRoot();
}
```

**RequestEnrichment** (`multi_agent_ide_lib/.../service/RequestEnrichment.java`)

Central service that enriches agent requests/results with:
- `ArtifactKey` (contextId) derived from the correct parent
- `PreviousContext` built from `BlackboardHistory` for retry/loop support

Key responsibilities:
1. **Parent Discovery**: Uses pattern matching to find the correct parent from `BlackboardHistory`
2. **Key Generation**: Delegates to `ContextIdService` to create child keys
3. **Recursive Enrichment**: Enriches nested children (e.g., `DiscoveryAgentRequests` contains multiple `DiscoveryAgentRequest`)
4. **PreviousContext Building**: Uses `PreviousContextFactory` to build typed previous context for each agent type

**ArtifactEnrichmentDecorator** (`multi_agent_ide/.../agent/ArtifactEnrichmentDecorator.java`)

Decorator implementing `ResultDecorator` that intercepts all agent routing results and applies `RequestEnrichment` to outgoing requests. This ensures every code path that produces an agent request goes through enrichment.

Pattern:
```java
public <T extends AgentModels.Routing> T decorate(T routing, OperationContext context) {
    // For each routing type, enrich all outgoing requests
    case AgentModels.DiscoveryOrchestratorRouting routing ->
        routing.toBuilder()
            .interruptRequest(enrichRequest(routing.interruptRequest(), context))
            .agentRequests(enrichRequest(routing.agentRequests(), context))
            .collectorRequest(enrichRequest(routing.collectorRequest(), context))
            // ... all other request fields
            .build();
}
```

### Data Flow

```text
Agent completes execution
    ↓
Returns Routing object with outgoing requests (contextId may be null)
    ↓
ArtifactEnrichmentDecorator.decorate()
    ↓
RequestEnrichment.enrich() for each request
    ↓
findParentForInput() → looks up correct parent from BlackboardHistory
    ↓
ContextIdService.generate() → creates child key from parent
    ↓
Request now has contextId = properly derived ArtifactKey
    ↓
Next agent executes with request.contextId() as parent for its artifacts
```

### Why This Pattern

1. **Separation of Concerns**: Agents don't need to know about artifact key derivation
2. **Consistency**: All requests go through the same enrichment path
3. **Correctness**: Parent lookup logic is centralized and testable
4. **Flexibility**: `BlackboardHistory` provides execution context without explicit parameter passing
5. **Tree Integrity**: Ensures artifact tree structure matches agent execution tree
