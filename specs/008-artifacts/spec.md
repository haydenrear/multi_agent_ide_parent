# Feature Specification: Execution Artifacts, Templates, and Semantics

**Feature Branch**: `008-artifacts`  
**Created**: 2026-01-24  
**Status**: Implemented  
**Last Updated**: 2026-01-31  
**Input**: User description: "Define canonical execution artifacts, templates, and semantic representations for agent runs"

## Intent and Vision

This feature defines a canonical, tree-structured execution record ("artifact tree") for every agent run.

The intent is to improve workflows over time by saving everything needed to reconstruct what happened and to enable future post-hoc analysis. This creates a durable corpus of executions plus semantic representations that future models (and humans) can sift through to identify regressions, discover improvements, and iteratively refine prompt templates.

Key ideas:

- The artifact tree is the authoritative source of truth for *what happened* in a run.
- The model is event-driven: artifacts are emitted incrementally as the workflow runs, enabling persistence and analysis without coupling runtime execution to storage or post-hoc semantics.
- Prompts decompose into:
  - static prompt templates (stored as artifacts and versioned by the hash of the static text)
  - dynamic inputs/arguments (stored separately as canonical data)
  - the fully rendered prompt text (stored for exact replay/audit)
- Two hierarchical naming systems enable "directory-like" navigation:
  - `ArtifactKey`: a hierarchical, time-sortable path within a single execution tree (structural provenance: where/when)
  - Template static IDs: hierarchical, referential identifiers that group template families across executions (semantic provenance: what kind of template)
- Hashes complement keys: hierarchical keys capture *where/when*; content hashes capture *what*, enabling deduplication, semantic caching, and measuring template evolution over time.
- Semantic representations attach after execution by reference and never mutate source artifacts, enabling evolving interpretations (summaries, embeddings, difficulty/success scoring, indexing) without re-instrumenting runtime.

ArtifactKey intent:

- An `ArtifactKey` carries both structure and time: it is a path-like identifier whose lexicographic ordering corresponds to creation time.
- Within an execution, sorting execution-scoped artifacts by `ArtifactKey` yields a deterministic timeline view of runtime artifact creation; together with containment and explicit references (including references to shared artifacts), this is sufficient to reconstruct what happened.
- PromptTemplate versions are shared artifacts: the same PromptTemplate `ArtifactKey` may be referenced by many executions.
- For static PromptTemplate versions, the `ArtifactKey` time component represents the template version's update time (the moment that template text last changed), enabling time-series analysis of template evolution.

Conceptual examples:

```text
Artifact keys (within a single execution):
ak:<execution-root>
 └── <rendered-prompt>
     ├── <prompt-template>
     └── <prompt-args>

Template static IDs (across executions):
tpl.agent.discovery.prompt
tpl.agent.ticket.loop_builder.system
tpl.agent.ticket.loop_builder.system.constraints
```

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Reconstruct Execution (Priority: P1)

Platform engineers and auditors need every execution to emit a complete artifact tree so any run can be reconstructed without relying on external logs or hidden state.

**Why this priority**: Reconstructability is the foundation for debugging, compliance, and reliable system improvement.

**Independent Test**: Load a single execution record and reconstruct rendered prompts, tool I/O, and outcomes using only the artifact tree.

**Cucumber Test Tag**: `@artifacts-reconstruct`

**Acceptance Scenarios**:

1. **Given** a completed execution, **When** the Execution artifact is loaded, **Then** it contains the required child groups `ExecutionConfig`, `InputArtifacts`, `AgentExecutionArtifacts`, and `OutcomeEvidenceArtifacts`.
2. **Given** a rendered prompt artifact, **When** its children are inspected, **Then** the template version (as an embedded artifact or a reference) and the prompt arguments used to render it are present.
3. **Given** an artifact and its child artifact in the same execution, **When** their keys are compared, **Then** the child key starts with the parent key and sorts after the parent key.
4. **Given** a completed execution, **When** the `ExecutionConfig` artifact is inspected, **Then** it contains all control state needed to reproduce behavior, including model references, tool availability/limits, routing/loop parameters, and a repository snapshot identifier.
5. **Given** a run that emits artifacts, **When** a specific artifact needs to be referenced or located later, **Then** it can be identified using its `ArtifactKey` without relying on the legacy context identifier format.
6. **Given** a rendered prompt was assembled using multiple prompt contributors, **When** the execution is reconstructed, **Then** each contributor's output is available as an artifact so the assembled prompt can be reproduced.
7. **Given** workflow graph events are emitted during an execution, **When** the execution is reconstructed, **Then** the event stream is available as source artifacts in the execution tree in a deterministic order.
8. **Given** a workflow execution dispatches sub-agents, **When** the execution is reconstructed, **Then** artifacts for each sub-agent are contained under a distinct nested subtree so attribution is unambiguous.

---

### User Story 2 - Track Template Evolution (Priority: P2)

Template owners need to monitor how prompt templates change over time and correlate template versions with outcomes so they can confidently improve or roll back workflow changes.

**Why this priority**: The primary improvement loop is iterating on prompt templates; without clear version identity and provenance, improvements cannot be validated or trusted.

**Independent Test**: Compare two executions that use different versions of the same template family and verify both versions are identifiable and traceable to outcome evidence.

**Cucumber Test Tag**: `@artifacts-templates`

**Acceptance Scenarios**:

1. **Given** a rendered prompt artifact, **When** its template child is inspected, **Then** it includes a hierarchical static template ID and a content hash computed from the static template text.
2. **Given** two executions use the same static template ID but different template content hashes, **When** template usage is reviewed, **Then** both versions are distinguishable and can be correlated to their respective outcome evidence artifacts.
3. **Given** a static template ID prefix, **When** templates are grouped for review, **Then** all templates under that prefix can be listed together as a family.
4. **Given** a template version (static template ID + content hash) already exists in persistent storage, **When** it is emitted again in a later execution, **Then** it is deduplicated as the same version rather than creating a new version record.
5. **Given** a template's static text changes, **When** the template is emitted, **Then** its content hash changes and it is stored as a new template version that can be compared against prior versions in the same static ID family.
6. **Given** a template is updated to a new version, **When** the new PromptTemplate version is stored, **Then** it receives a new PromptTemplate `ArtifactKey` whose time component reflects the template's last-updated time (not the execution time).

---

### User Story 3 - Post-Hoc Semantics (Priority: P3)

Analysts need to attach semantic interpretations after the fact, so they can score difficulty, summarize runs, and build indexes without changing source artifacts.

**Why this priority**: Semantic analysis drives learning and retrieval but must not compromise the authoritative execution record.

**Independent Test**: Add semantic representations to an existing execution and verify the source artifacts remain unchanged.

**Cucumber Test Tag**: `@artifacts-semantics`

**Acceptance Scenarios**:

1. **Given** a source artifact, **When** a semantic representation is created, **Then** it references the target artifact key and includes a derivation recipe ID and version, model reference, timestamp, and quality metadata.
2. **Given** semantic representations are attached, **When** the source artifacts are reloaded, **Then** their content is unchanged.

---

### Edge Cases

| Edge Case | Expected Behavior |
|-----------|-------------------|
| Execution record missing required child group | Validation MUST fail at execution completion. The system MUST NOT mark an execution as `completed` unless all required child groups (`ExecutionConfig`, `InputArtifacts`, `AgentExecutionArtifacts`, `OutcomeEvidenceArtifacts`) are present. Incomplete executions remain in `running` or `failed` status with a validation error recorded. |
| Two artifacts with identical keys in same execution | The system MUST reject the second artifact. ArtifactKey uniqueness within an execution tree is enforced at emission time. The `ArtifactEventListener` MUST log an error and discard the duplicate, preserving the first artifact. |
| Template emitted with non-static text (contains unresolved placeholders) | The system MUST reject the template at emission time. Template validation MUST verify no unresolved template variables remain (e.g., `{{variable}}`). If detected, emit a validation error artifact instead and fail the template registration. |
| Semantic representation references nonexistent artifact key | The system MUST reject the semantic representation at write time. Foreign key validation ensures target artifact exists before persisting. Return an error to the semantic analysis job. |
| Artifact key not lexicographically ordered by creation time | This indicates a bug in key generation. The system SHOULD log a warning but MUST accept the artifact (keys are immutable). Reconstruction relies on the key's embedded ULID timestamp, not relative ordering, so correctness is preserved. |
| Template static ID missing or not hierarchical | Validation MUST fail. Templates without valid static IDs (matching the regex `^tpl\.([a-z][a-z0-9_]{0,63}\.){1,7}[a-z][a-z0-9_]{0,63}$`) are rejected at registration time. |
| Execution references missing PromptTemplate ArtifactKey | The system MUST fail the artifact tree persistence with a referential integrity error. All `RefArtifact` targets must resolve. For templates, this means the template version must be registered before or during execution persistence. |
| Two different template texts produce same content hash | SHA-256 collision (cryptographically negligible, ~2^128 operations). If detected, the system MUST log a critical error and reject the second template. In practice, this will never occur with valid inputs. |

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST produce exactly one Execution artifact as the root for every run.
- **FR-002**: Execution artifacts MUST include the required child groups `ExecutionConfig`, `InputArtifacts`, `AgentExecutionArtifacts`, and `OutcomeEvidenceArtifacts`.
- **FR-003**: Each artifact MUST have a hierarchical, time-sortable `ArtifactKey` that includes a creation time component, can be generated independently by emitters, and whose child keys MUST start with the parent key.
- **FR-004**: Execution-scoped artifacts MUST have unique `ArtifactKey` values within a single execution tree; shared artifacts (such as PromptTemplate versions) MAY be referenced by many execution trees using the same `ArtifactKey`.
- **FR-005**: Any artifact that carries bytes MUST include a content hash computed from its canonical bytes.
- **FR-006**: Template artifacts MUST be static text, MUST carry a hierarchical static template ID that remains stable across versions, and MUST include a content hash.
- **FR-007**: Template family identity MUST be the static template ID; within a family, each template version MUST be identified by both (a) the content hash of its static template text and (b) a stable PromptTemplate `ArtifactKey` that changes only when the template text changes (deduplicated by the tuple (static template ID, content hash)).
- **FR-008**: Rendered prompt artifacts MUST include the fully rendered text and MUST contain children (or references) capturing the template version and the prompt arguments used to render it.
- **FR-009**: Dependencies MUST be represented by containment, a reference artifact pointing to another artifact key, or embedding the dependent content directly.
- **FR-010**: Semantic representations MUST reference a target artifact key and MUST never mutate source artifacts.
- **FR-011**: Semantic representations MUST include derivation recipe ID and version, model reference, timestamp, and one payload variant. Quality metadata is OPTIONAL but RECOMMENDED when available.
- **FR-012**: Semantic payloads MUST support `Embedding`, `Summary`, and `SemanticIndexRef`, and MUST allow future variants without changing source artifacts.
- **FR-013**: Outcome evidence MUST be recorded as source artifacts, while outcome interpretations MUST be recorded as semantic representations.
- **FR-014**: Execution configuration MUST capture all non-textual control state required to reproduce behavior (models, tool availability/limits, routing parameters, loop parameters, repository snapshot identifiers).
- **FR-015**: The system MUST use `ArtifactKey` as the canonical identifier for per-run context and provenance, replacing the legacy context identifier pattern based on (workflow run ID, agent type, sequence number, timestamp) for new runs.
- **FR-016**: Users MUST be able to group templates by hierarchical static template ID prefix (directory-style families) and compare versions within a family by content hash.
- **FR-017**: Source artifacts MUST be immutable once emitted; any later interpretation, scoring, or indexing MUST be captured only as semantic representations that reference source artifacts.
- **FR-018**: Sorting execution-scoped artifacts by `ArtifactKey` MUST provide a deterministic temporal ordering consistent with each artifact's creation time component, sufficient to reconstruct execution chronology and provenance.
- **FR-019**: PromptTemplate artifacts MUST expose the template version's last-updated time, and the `ArtifactKey` time component for that PromptTemplate version MUST reflect that last-updated time.
- **FR-020**: For template artifacts, the content hash MUST be computed from the canonical bytes of the static template text and MUST be used to detect when a new template version exists.
- **FR-021**: The system MUST deduplicate persisted template versions by (static template ID, content hash); when a previously unseen content hash is encountered for the same static template ID, the system MUST create a new stored PromptTemplate version with a new PromptTemplate `ArtifactKey`.
- **FR-022**: Template artifacts MUST support a standard template contract that includes static template ID, static text (or an equivalent representation), content hash, and a render operation that produces rendered text from dynamic inputs (starting with PromptTemplate).
- **FR-023**: Within an execution, repeated uses of the same shared template version MUST be represented via `RefArtifact` nodes rather than duplicating the shared PromptTemplate artifact node.
- **FR-024**: The system MUST emit a PromptContribution artifact for each dynamic prompt contributor used to assemble a rendered prompt, including contributor identity, ordering, and contributed text.
- **FR-025**: On application startup, the system MUST load all built-in prompt templates from configured sources and ensure they exist in persistent template storage, deduplicating versions by content hash and creating new PromptTemplate versions when template text changes.
- **FR-026**: For a given template version (static template ID + content hash), the PromptTemplate `ArtifactKey` MUST be stable and reused across executions, and MUST change only when the template text changes (creating a new content hash).
- **FR-027**: The system MUST capture all workflow graph events emitted during execution as source artifacts within the execution tree, preserving each event's identity, timestamp, type, and payload.
- **FR-028**: The system MUST provide a complete mapping from all currently defined workflow graph event variants to their corresponding source artifact representation so executions can be reconstructed using only persisted artifacts.
- **FR-029**: Artifact emission MUST be event-driven: artifacts MUST be emitted as events and processed by subscribers/listeners that build and persist the artifact tree using `ArtifactKey` semantics (including parent/child prefix structure and key ordering).
- **FR-030**: The execution artifact tree MUST represent sub-agent activity as nested scopes (child artifact subtrees) so that prompts, tool calls, and events can be attributed to the correct workflow agent and sub-agent node.

### Key Entities *(include if feature involves data)*

- **Execution**: Root record for a run containing the artifact tree and required child groups.
- **Artifact**: A node in the execution tree containing content, metadata, and an `ArtifactKey`.
- **ArtifactKey**: Hierarchical, time-sortable identifier that encodes containment and ordering, including an embedded creation time component used for lexicographic sorting.
- **ExecutionConfig**: Configuration state required to reproduce behavior.
- **RenderedPrompt**: Artifact containing full rendered prompt text plus children for template and arguments.
- **PromptTemplate**: Static template text with a hierarchical static template ID, content hash, and a stable PromptTemplate `ArtifactKey` that represents the template version (shared across executions and updated only when the template text changes).
- **TemplateStaticId**: Hierarchical, referential identifier that groups a family of related templates across executions.
- **TemplateVersion**: A specific version of a template family, identified by (static template ID, content hash), associated with a last-updated time, and referenced by a stable PromptTemplate `ArtifactKey`.
- **ContentHash**: Stable identifier computed from canonical bytes, used for deduplication and version identity (especially for static templates).
- **Templated**: A reusable template contract shared by template types (starting with PromptTemplate) that supports rendering from dynamic inputs while retaining stable version identity.
- **PromptContribution**: Artifact representing a single dynamic prompt contributor output that participates in assembling a rendered prompt.
- **GraphEvent**: A workflow graph event record emitted during execution that captures runtime activity (event ID, node ID, timestamp, event type, and payload).
- **GraphEvent Types**: The supported workflow graph event variants to be captured as source artifacts: `NodeAddedEvent`, `ActionStartedEvent`, `ActionCompletedEvent`, `StopAgentEvent`, `PauseEvent`, `ResumeEvent`, `ResolveInterruptEvent`, `AddMessageEvent`, `NodeStatusChangedEvent`, `NodeErrorEvent`, `NodeBranchedEvent`, `NodePrunedEvent`, `NodeReviewRequestedEvent`, `InterruptStatusEvent`, `GoalCompletedEvent`, `WorktreeCreatedEvent`, `WorktreeBranchedEvent`, `WorktreeMergedEvent`, `WorktreeDiscardedEvent`, `NodeUpdatedEvent`, `NodeDeletedEvent`, `NodeStreamDeltaEvent`, `NodeThoughtDeltaEvent`, `ToolCallEvent`, `GuiRenderEvent`, `UiDiffAppliedEvent`, `UiDiffRejectedEvent`, `UiDiffRevertedEvent`, `UiFeedbackEvent`, `NodeBranchRequestedEvent`, `PlanUpdateEvent`, `UserMessageChunkEvent`, `CurrentModeUpdateEvent`, `AvailableCommandsUpdateEvent`, `PermissionRequestedEvent`, `PermissionResolvedEvent`, `UiStateSnapshot`.
- **EventArtifact**: A source artifact that records a captured GraphEvent as part of an execution's authoritative event trail.
- **ArtifactEvent**: An artifact emission event that carries an artifact (or artifact reference) so artifacts can be persisted and assembled into a tree by subscribers/listeners.
- **WorkflowNode**: A workflow graph node representing a unit of work (workflow agent step or sub-agent dispatch) used to scope nested artifact subtrees and attribute events/prompts/tool calls.
- **PromptArgs**: Canonical representation of the arguments bound into the template.
- **ToolIO**: Input and output artifacts for tool interactions.
- **OutcomeEvidence**: Objective artifacts proving test, build, merge, or human acknowledgment outcomes.
- **SemanticRepresentation**: Post-hoc analysis record that targets a source artifact without mutation.
- **SemanticPayload**: Sealed payload variant such as `Embedding`, `Summary`, or `SemanticIndexRef`.
- **RefArtifact**: Explicit reference to another artifact key for dependency modeling.
- **AgentContext**: Root interface for all agent-related artifacts, extends `Artifact.AgentModel`, requires `ArtifactKey artifactKey()` and serialization methods for different contexts (Standard, Interrupt, GoalResolution, MergeSummary, Results).
- **ArtifactNode**: Trie-like in-memory tree structure for artifact management during execution, uses concurrent hash maps and content-hash-based sibling deduplication.
- **DiscoveryReport**: Comprehensive discovery analysis artifact containing file references, cross-links, semantic tags, generated queries, and diagram representations.
- **PlanningTicket**: Work breakdown structure artifact with tasks, dependencies, acceptance criteria, and discovery links for traceability.

### Assumptions

- Retention, access control, and privacy policies for stored artifacts are defined elsewhere.
- Semantic analysis jobs run after execution completion and do not block runtime execution.
- Artifact storage can persist full rendered prompt text and tool I/O without truncation.

### Dependencies

- The execution runtime can emit and store the execution artifact tree.
- A post-hoc analysis pipeline can read source artifacts and write semantic representations.
- Existing components that currently rely on the legacy context identifier format can be updated to use `ArtifactKey` for new runs.

### Out of Scope

- Specific storage formats, transport protocols, or API designs.
- UI or visualization tooling for browsing artifacts or templates.
- Backfilling or migrating historical executions into the new model (new runs use the new identifiers).

---

## Technical Specifications *(mandatory)*

### ArtifactKey Format

An `ArtifactKey` is a hierarchical, time-sortable string identifier with the following structure:

```
ak:<segment>/<segment>/.../<segment>
```

**Segment Format**: Each segment is a ULID (Universally Unique Lexicographically Sortable Identifier):
- 26 characters, Crockford Base32 encoded
- First 10 characters encode millisecond timestamp (48 bits)
- Last 16 characters encode randomness (80 bits)
- Lexicographically sortable by creation time

**Examples**:
```text
# Execution root
ak:01HQXK5V8RPZM4J3NHWGC7SDFG

# Child artifact (rendered prompt under execution)
ak:01HQXK5V8RPZM4J3NHWGC7SDFG/01HQXK5V9KPZM4J3NHWGC7SDXY

# Grandchild artifact (template under rendered prompt)
ak:01HQXK5V8RPZM4J3NHWGC7SDFG/01HQXK5V9KPZM4J3NHWGC7SDXY/01HQXK5VAKPZM4J3NHWGC7SDYZ
```

**Generation Rules**:
1. Root artifacts generate a single ULID segment prefixed with `ak:`
2. Child artifacts append `/<new-ulid>` to parent key
3. ULIDs are generated at artifact creation time
4. Emitters generate keys independently (no coordination required)

**Invariants**:
- Child key MUST start with parent key followed by `/`
- Sorting keys lexicographically yields creation-time order within a tree level
- Keys are immutable once assigned

**Shared Artifact Keys** (e.g., PromptTemplateVersion):
- Use the same format but are stored independently
- The ULID timestamp reflects template version creation time (when that hash was first seen)
- Example: `ak:01HQXK3M2NPZM4J3NHWGC7SABC` (standalone, not nested)

---

### TemplateStaticId Format

A `TemplateStaticId` is a hierarchical, dot-separated identifier that groups related templates semantically:

```
tpl.<category>.<subcategory>...<name>
```

**Format Rules**:
1. MUST start with `tpl.` prefix
2. Segments are lowercase alphanumeric with underscores allowed
3. Segments are separated by `.`
4. Maximum depth: 8 levels (including `tpl.` prefix)
5. Maximum segment length: 64 characters
6. Total maximum length: 256 characters

**Reserved Prefixes**:
- `tpl.agent.*` — Agent-specific prompt templates
- `tpl.system.*` — System-level templates (config, error messages)
- `tpl.workflow.*` — Workflow orchestration templates
- `tpl.tool.*` — Tool-specific templates

**Examples**:
```text
tpl.agent.discovery.system_prompt
tpl.agent.discovery.user_prompt
tpl.agent.ticket.loop_builder.system
tpl.agent.ticket.loop_builder.constraints
tpl.workflow.planning.initial
tpl.system.error.validation
```

**Validation Regex**:
```
^tpl\.([a-z][a-z0-9_]{0,63}\.){1,7}[a-z][a-z0-9_]{0,63}$
```

---

### Content Hashing Specification

**Algorithm**: SHA-256

**Encoding**: Lowercase hexadecimal (64 characters)

**Computation Rules**:

1. **PromptTemplate**: Hash of UTF-8 encoded static template text bytes
   ```
   contentHash = sha256(templateText.getBytes(UTF_8)).toHex()
   ```

2. **PromptArgs / JSON payloads**: Hash of canonical JSON bytes (see Canonical JSON below)
   ```
   contentHash = sha256(canonicalJson.getBytes(UTF_8)).toHex()
   ```

3. **Binary content**: Hash of raw bytes
   ```
   contentHash = sha256(rawBytes).toHex()
   ```

**Example**:
```text
Template text: "You are a helpful assistant."
Content hash:  a3f2b8c9d4e5f6a7b8c9d0e1f2a3b4c5d6e7f8a9b0c1d2e3f4a5b6c7d8e9f0a1
```

---

### Canonical JSON Specification

Canonical JSON ensures identical content produces identical hashes regardless of serialization order.

**Rules**:
1. **Key ordering**: Object keys sorted lexicographically (Unicode code point order)
2. **Whitespace**: No insignificant whitespace (compact form)
3. **Numbers**: No trailing zeros; no leading zeros except for `0.x`; no positive sign; use exponential notation only when required
4. **Strings**: UTF-8 encoded; escape only required characters (`"`, `\`, control chars)
5. **Null handling**: Explicit `null` values are included; missing keys are omitted
6. **Array ordering**: Preserved as-is (arrays are ordered)

**Example**:
```json
// Input (non-canonical)
{
  "zebra": 1,
  "apple": 2,
  "nested": { "b": true, "a": false }
}

// Canonical output
{"apple":2,"nested":{"a":false,"b":true},"zebra":1}
```

**Implementation**: Use Jackson with `SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS` and `JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS` disabled.

---

### ArtifactKey Parent Derivation (Critical Implementation Pattern)

**Problem**: In a multi-agent workflow tree, artifacts must be nested under the correct parent to preserve attribution. A common mistake is deriving child artifact keys from the workflow run ID alone, which flattens the tree and loses the hierarchical relationship between agents and their artifacts.

**Correct Pattern**: Child artifact keys MUST be derived from the parent agent's `ArtifactKey`, not from the workflow run ID.

**Why This Matters**:
- The workflow is a tree of agents: Orchestrator → DiscoveryOrchestrator → DiscoveryAgent(s) → etc.
- Each agent's request carries an `ArtifactKey` (the agent's contextId)
- Artifacts emitted during that agent's execution (prompts, tool calls, results) MUST be children of that agent's key
- Using workflow run ID instead creates a flat list, losing the tree structure needed for reconstruction

**Example - Wrong**:
```text
# Flat structure - cannot attribute prompts to specific agents
ak:01HQXK5V8R.../01HQXK5V9K...  (RenderedPrompt under execution root)
ak:01HQXK5V8R.../01HQXK5VAK...  (RenderedPrompt under execution root)
ak:01HQXK5V8R.../01HQXK5VBK...  (RenderedPrompt under execution root)
```

**Example - Correct**:
```text
# Hierarchical structure - prompts attributed to their agent
ak:01HQXK5V8R.../01HQXK5V9K...                          (DiscoveryOrchestratorRequest)
ak:01HQXK5V8R.../01HQXK5V9K.../01HQXK5VAK...            (RenderedPrompt for DiscoveryOrchestrator)
ak:01HQXK5V8R.../01HQXK5V9K.../01HQXK5VBK...            (DiscoveryAgentRequest child)
ak:01HQXK5V8R.../01HQXK5V9K.../01HQXK5VBK.../01HQX...   (RenderedPrompt for DiscoveryAgent)
```

**Implementation Rule**: When emitting an artifact during agent execution:
1. Obtain the current agent request's `contextId` (its `ArtifactKey`)
2. Create child keys by calling `parentKey.createChild()`
3. NEVER derive keys directly from workflow run ID except for the execution root

**Key Generation Priority** (from `ContextIdService`):
```java
public ArtifactKey generate(String workflowRunId, AgentType agentType, Artifact.AgentModel parent) {
    // 1. If workflowRunId is itself a valid ArtifactKey, create child from it
    if (workflowRunId != null && ArtifactKey.isValid(workflowRunId)) {
        return new ArtifactKey(workflowRunId).createChild();
    }
    // 2. If parent model exists, create child from parent's key
    if (parent != null) {
        return parent.key().createChild();
    }
    // 3. Only create root if no parent context exists
    return ArtifactKey.createRoot();
}
```

**Parent Discovery**: When enriching a request, the system must find the appropriate parent from the execution history (BlackboardHistory). The parent type depends on the request type:
- `DiscoveryAgentRequest` → parent is `DiscoveryAgentRequests` (the dispatch container)
- `RenderedPrompt` → parent is the current `AgentRequest` being processed
- `ToolCallArtifact` → parent is the current `AgentRequest` being processed

---

### Implementation Scope (Initial Rollout)

This feature is implemented across multiple modules in the multi-agent IDE system:

**Core Artifact Infrastructure** (in `acp-cdc-ai` module):
- `acp-cdc-ai/src/main/java/com/hayden/acp_cdc_ai/acp/events/ArtifactKey.java` — Hierarchical, time-sortable ULID-based identifier
- `acp-cdc-ai/src/main/java/com/hayden/acp_cdc_ai/acp/events/Artifact.java` — Base artifact interface and sealed type hierarchy
- `acp-cdc-ai/src/main/java/com/hayden/acp_cdc_ai/acp/events/ArtifactHashing.java` — Content hashing + canonical JSON helpers
- `acp-cdc-ai/src/main/java/com/hayden/acp_cdc_ai/acp/events/EventBus.java` — Event bus for artifact emission

**Shared Artifact Models** (in `multi_agent_ide_lib` module):
- `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/artifact/PromptTemplateVersion.java` — Versioned prompt templates with static ID + content hash deduplication
- `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/artifact/SemanticRepresentation.java` — Post-hoc semantic interpretations (embeddings, summaries, indexes)
- `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AgentModels.java` — Agent request/result types implementing `AgentContext` with `ArtifactKey contextId()`
- `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AgentContext.java` — Root interface for all agent-related artifacts

**Artifact Persistence Services** (in `multi_agent_ide` module):
- `multi_agent_ide/src/main/java/com/hayden/multiagentide/artifacts/ArtifactService.java` — Manages persistence and serialization with batch deduplication
- `multi_agent_ide/src/main/java/com/hayden/multiagentide/artifacts/ArtifactTreeBuilder.java` — In-memory trie structure management and tree persistence/loading
- `multi_agent_ide/src/main/java/com/hayden/multiagentide/artifacts/ArtifactNode.java` — Trie-like tree structure for in-memory artifact management
- `multi_agent_ide/src/main/java/com/hayden/multiagentide/artifacts/ArtifactEventListener.java` — Event-driven persistence subscriber for ArtifactEvents and stream events
- `multi_agent_ide/src/main/java/com/hayden/multiagentide/artifacts/EventArtifactMapper.java` — Maps GraphEvents to EventArtifact/MessageStreamArtifact nodes
- `multi_agent_ide/src/main/java/com/hayden/multiagentide/artifacts/ExecutionScopeService.java` — Execution lifecycle and root artifact management
- `multi_agent_ide/src/main/java/com/hayden/multiagentide/artifacts/ArtifactEmissionService.java` — Emits agent models, execution config, tool calls, and events

**Artifact Persistence Layer** (in `multi_agent_ide` module):
- `multi_agent_ide/src/main/java/com/hayden/multiagentide/artifacts/entity/ArtifactEntity.java` — JPA entity with indexes on key, parent, execution, type, hash
- `multi_agent_ide/src/main/java/com/hayden/multiagentide/artifacts/repository/ArtifactRepository.java` — JPA repository with specialized queries

**Semantic Representation Layer** (in `multi_agent_ide` module):
- `multi_agent_ide/src/main/java/com/hayden/multiagentide/artifacts/semantic/SemanticRepresentationEntity.java` — JPA entity for post-hoc semantic attachments
- `multi_agent_ide/src/main/java/com/hayden/multiagentide/artifacts/semantic/SemanticRepresentationService.java` — Attaches semantics without mutating source artifacts
- `multi_agent_ide/src/main/java/com/hayden/multiagentide/artifacts/semantic/SemanticRepresentationRepository.java` — Queries for semantic representations

**Core Agent Models and Interfaces**:
- `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AgentModels.java` — Sealed interface hierarchy for all agent request/result types
- `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java` — Agent execution interfaces

**Prompt Infrastructure**:
- Built-in prompt templates under `multi_agent_ide/src/main/resources/prompts/workflow/`
- Prompt contributor infrastructure in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/prompt/PromptContributor.java`

**Request Enrichment and ArtifactKey Assignment** (Critical for Hierarchical Keys):
- `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/service/RequestEnrichment.java` — Central service for enriching agent requests/results with `ArtifactKey` (contextId) and `PreviousContext`. Implements the parent derivation logic described in "ArtifactKey Parent Derivation" above.
- `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/prompt/ContextIdService.java` — Generates child `ArtifactKey` values from parent keys, ensuring hierarchical structure is preserved.
- `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/decorator/ArtifactEnrichmentDecorator.java` — Decorator that intercepts agent routing results and applies `RequestEnrichment` to all outgoing requests, ensuring every request has a properly derived `ArtifactKey`.

**Request Enrichment Pattern**:

The `RequestEnrichment` service is invoked via the `ArtifactEnrichmentDecorator` which implements `ResultDecorator`. When an agent completes and returns a routing decision, the decorator enriches all outgoing requests with:

1. **ArtifactKey (contextId)**: Derived from the parent agent's key using `ContextIdService.generate()`
2. **PreviousContext**: Built from `BlackboardHistory` to provide retry/loop context

The enrichment uses pattern matching to find the correct parent for each request type:

```java
// Example parent derivation from RequestEnrichment.findParentForInput()
case AgentModels.DiscoveryAgentRequest ignored ->
    findLastFromHistory(history, AgentModels.DiscoveryAgentRequests.class);
case AgentModels.PlanningAgentRequest ignored ->
    findLastFromHistory(history, AgentModels.PlanningOrchestratorRequest.class);
case AgentModels.RenderedPrompt ignored ->
    findLastFromHistory(history, AgentModels.AgentRequest.class);
```

**Why This Pattern Matters**:
- Ensures every agent request carries a hierarchical `ArtifactKey` before execution
- Artifacts emitted during execution (prompts, tool calls) can use the request's key as their parent
- The decorator pattern intercepts at the routing boundary, guaranteeing enrichment happens for all code paths
- `BlackboardHistory` provides execution context to find the correct parent without explicit parameter passing

**Integration Points for Artifact Emission**:

The `ArtifactKey` assigned during enrichment becomes the parent key for all artifacts emitted during that agent's execution:

```text
ArtifactEnrichmentDecorator.decorate()
    ↓ (enriches outgoing requests with ArtifactKey)
RequestEnrichment.enrich()
    ↓ (finds parent from BlackboardHistory, generates child key)
ContextIdService.generate()
    ↓ (creates child key from parent)
Agent executes with request.contextId() = ArtifactKey
    ↓ (all emissions use this as parent)
ArtifactEmissionService.emit*(request.contextId(), ...)
```

**In-Memory Artifact Tree Architecture**:

The artifact system uses a trie-like in-memory structure (`ArtifactNode`) during execution before persisting:

```text
ArtifactEvent emitted
    ↓
ArtifactEventListener.onEvent()
    ↓
ArtifactTreeBuilder.addArtifact()
    ↓ (uses ArtifactKey prefix for tree navigation)
ArtifactNode trie (concurrent hash maps)
    ↓ (on execution completion)
ArtifactTreeBuilder.persistExecutionTree()
    ↓
ArtifactService.doPersist() (batch with deduplication)
    ↓
ArtifactRepository.saveAll()
```

Key implementation details:
- `ArtifactNode` uses concurrent hash maps for thread-safe operations
- Hash-based deduplication at the sibling level (same parent, same content hash → reuse)
- Duplicate artifacts become `DbRef` type references to the original
- Append-only structure (nodes are never removed during execution)
- Orphan artifacts (no parent found) are handled gracefully with warning logs

**Operationally**:

- On application start, built-in prompt templates are loaded and checked against persistent template storage; new PromptTemplate versions are created only when the template content hash is new.
- Prompt rendering uses the existing templating integration in the runtime (Jinja-based rendering in embabel); regardless of renderer, the system emits artifacts that separate static templates from dynamic inputs and records contributor outputs.
- A shared `Templated` contract is introduced for versioned templates (static template ID, static text, content hash, render-to-string), and persisted template lookup/dedup is performed per template type (starting with PromptTemplate).
- GraphEvent-to-artifact capture is enabled by continuing to emit existing GraphEvents and mapping them into EventArtifact nodes within the execution tree.
- A new `ArtifactEvent` is introduced in `Events.java` (carrying the artifact interface only), and an `EventListener` implementation subscribes to ArtifactEvents and incrementally builds/persists the artifact tree using `ArtifactKey` semantics.
- Sub-agent execution (dispatch subagents and other workflow nodes) emits nested artifact subtrees to preserve attribution and chronology across the workflow graph.
- **Request enrichment via `ArtifactEnrichmentDecorator` ensures every agent request has a hierarchical `ArtifactKey` before execution begins, enabling proper artifact tree construction.**

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of sampled executions contain exactly one Execution root and all required child groups.
- **SC-002**: For a representative sample of executions, reviewers can reconstruct rendered prompts and tool I/O using only the artifact tree with 100% success.
- **SC-003**: 100% of template artifacts in sampled runs are grouped by static template ID and differentiated by content hash.
- **SC-004**: Post-hoc semantic additions result in 0 observed mutations of source artifacts across sampled executions.
- **SC-005**: For a representative sample of template versions, reviewers can determine which executions used each version and retrieve the associated outcome evidence with 100% success.
- **SC-006**: Across sampled executions, identical template static text yields the same content hash and does not create duplicate stored template versions; changes to template text yield a new content hash and a new stored version 100% of the time.
- **SC-007**: For a representative sample of executions, reviewers can reconstruct assembled prompts by combining the referenced template version, dynamic inputs, and prompt contributor artifacts with 100% success.
- **SC-008**: For a representative sample of template versions, the same (static template ID, content hash) maps to a single stable PromptTemplate `ArtifactKey` reused across executions; template updates produce a new `ArtifactKey` 100% of the time.
