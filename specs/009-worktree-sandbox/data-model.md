# Data Model: Worktree Sandbox for Agent File Operations

## Entities

### RequestContext
- **Fields**:
  - `sessionId` (String) — MCP session header value (ArtifactKey.value)
  - `worktree` (WorktreeSandboxContext) — main worktree + submodule worktrees
  - `orchestratorNodeId` (String, optional)
  - `ticketNodeId` (String, optional)
  - `metadata` (Map<String, String>, optional)
- **Validation**:
  - `sessionId` required
  - `worktree.mainPath` required for sandbox enforcement
- **Notes**:
  - Stored in `RequestContextRepository` keyed by `sessionId`
  - Updated immutably via `@With`

### WorktreeSandboxContext
- **Fields**:
  - `mainWorktreeId` (String)
  - `mainWorktreePath` (Path/String)
  - `submoduleWorktrees` (List<SubmoduleWorktreeContext>)
- **Validation**:
  - `mainWorktreePath` must be absolute or resolvable to absolute
  - Submodule paths must be within `mainWorktreePath`

### SubmoduleWorktreeContext
- **Fields**:
  - `submoduleName` (String)
  - `worktreeId` (String)
  - `worktreePath` (Path/String)
  - `parentWorktreeId` (String)
- **Validation**:
  - `worktreePath` must be within `mainWorktreePath`

### SandboxValidationResult
- **Fields**:
  - `allowed` (boolean)
  - `reason` (String)
  - `normalizedPath` (String)
- **Validation**:
  - `reason` required when `allowed` is false

### SandboxTranslationStrategy
- **Fields**:
  - `provider` (enum/string)
- **Behavior**:
  - `translate(RequestContext)` → env vars + CLI args

## Relationships
- `RequestContext` 1→1 `WorktreeSandboxContext`
- `WorktreeSandboxContext` 1→N `SubmoduleWorktreeContext`
- `SandboxTranslationRegistry` 1→N `SandboxTranslationStrategy`

## State Transitions (Context Lifecycle)
- **Initialized**: Request created → `RequestContext` stored by decorator
- **Enriched**: Additional fields (node IDs, metadata) attached as request flows
- **Consumed**: AcpChatModel + AcpTooling lookup via `sessionId`
- **Expired**: Context evicted when request completes (in-memory cleanup policy, if implemented)

---

## Merge Flow Entities

### MergeDescriptor
Attached to individual agent results after trunk→child merge attempt.
- **Fields**:
  - `mergeDirection` (MergeDirection enum) — TRUNK_TO_CHILD or CHILD_TO_TRUNK
  - `successful` (boolean) — overall merge success
  - `conflictFiles` (List<String>) — files with merge conflicts
  - `submoduleMergeResults` (List<SubmoduleMergeResult>) — per-submodule results
  - `mainWorktreeMergeResult` (MergeResult) — main worktree merge result
  - `errorMessage` (String, optional) — error details if merge failed
- **Validation**:
  - `mergeDirection` required
  - If `successful=false`, at least `conflictFiles` or `errorMessage` should be populated

### MergeDirection (Enum)
- `TRUNK_TO_CHILD` — merging from parent/trunk into child worktree (pull latest)
- `CHILD_TO_TRUNK` — merging from child worktree back to parent/trunk (push changes)

### SubmoduleMergeResult
Per-submodule merge outcome.
- **Fields**:
  - `submoduleName` (String) — name of the submodule
  - `mergeResult` (MergeResult) — the existing MergeResult record
  - `pointerUpdated` (boolean) — whether parent's submodule pointer was updated
- **Validation**:
  - `submoduleName` required

### MergeAggregation
Aggregated merge state for routing LLM, attached to ResultsRequest types.
- **Fields**:
  - `merged` (List<AgentMergeStatus>) — successfully merged child agents
  - `pending` (List<AgentMergeStatus>) — not yet attempted (after first conflict)
  - `conflicted` (AgentMergeStatus, nullable) — first agent that had conflict
- **Validation**:
  - `merged` + `pending` + (conflicted ? 1 : 0) should equal total child results
- **Methods**:
  - `allSuccessful()` → boolean — returns true if conflicted is null and pending is empty
  - `prettyPrint()` → String — human-readable format for LLM context

### AgentMergeStatus
Per-agent merge state wrapper for MergeAggregation.
- **Fields**:
  - `agentResultId` (String) — identifier for the agent result (contextId or similar)
  - `worktreeContext` (WorktreeSandboxContext) — the agent's worktree
  - `mergeDescriptor` (MergeDescriptor) — merge result from child→trunk attempt
- **Validation**:
  - `agentResultId` required

### ResultsRequest (Interface)
Common interface for dispatch result aggregation types.
- **Implemented by**:
  - `TicketAgentResults`
  - `PlanningAgentResults`
  - `DiscoveryAgentResults`
- **Methods**:
  - `worktreeContext()` → WorktreeSandboxContext
  - `mergeAggregation()` → MergeAggregation
  - `withMergeAggregation(MergeAggregation)` → ResultsRequest

## Merge Flow Relationships
- `AgentResult` 1→0..1 `MergeDescriptor` (individual result merge status)
- `MergeDescriptor` 1→N `SubmoduleMergeResult`
- `MergeDescriptor` 1→0..1 `MergeResult` (main worktree)
- `ResultsRequest` 1→0..1 `MergeAggregation`
- `MergeAggregation` 1→N `AgentMergeStatus` (merged list)
- `MergeAggregation` 1→N `AgentMergeStatus` (pending list)
- `MergeAggregation` 1→0..1 `AgentMergeStatus` (conflicted)

## Merge State Transitions

### Phase 1: Child Agent Merge (Trunk → Child)
```
Child Agent Completes Work
    │
    ▼
DispatchedAgentResultDecorator.decorate(AgentResult)
    │
    ├── Merge all submodules (trunk.submodule → child.submodule)
    ├── Collect SubmoduleMergeResult for each
    ├── Merge main worktree (trunk.main → child.main)
    ├── Build MergeDescriptor(direction=TRUNK_TO_CHILD)
    │
    ▼
AgentResult.toBuilder().mergeDescriptor(descriptor).build()
```

### Phase 2: Parent Dispatch Merge (Child → Trunk)
```
Dispatch Collects All Child Results
    │
    ▼
ResultsRequestDecorator.decorate(ResultsRequest)
    │
    ├── Initialize: merged=[], pending=[all], conflicted=null
    ├── For each child in order:
    │   ├── Merge submodules (child.submodule → trunk.submodule)
    │   ├── If conflict: set conflicted, break
    │   ├── Update submodule pointers
    │   ├── Merge main (child.main → trunk.main)
    │   ├── If conflict: set conflicted, break
    │   └── Move from pending to merged
    ├── Build MergeAggregation(merged, pending, conflicted)
    │
    ▼
ResultsRequest.withMergeAggregation(aggregation)
    │
    ▼
Routing LLM receives MergeAggregation in context
```
