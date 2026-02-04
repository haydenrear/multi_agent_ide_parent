# Phase 0 Research: Worktree Sandbox for Agent File Operations

## Decision: Propagate worktree context via RequestDecorator chain
**Rationale**: Existing request decorators already enrich AgentRequest objects and are the least invasive way to add worktree context without breaking agent interfaces. This keeps propagation consistent for orchestrator and downstream work/collector requests.
**Alternatives considered**: Directly modifying agent constructors or passing additional parameters through AgentRunner; rejected due to broader API churn and higher risk of breaking existing flows.

## Decision: Use in-memory RequestContextRepository keyed by MCP session ID
**Rationale**: MCP tools already rely on `MCP_SESSION_HEADER` and `@SetFromHeader` for correlation. Storing RequestContext by session ID aligns with current tooling patterns and avoids introducing persistent storage for transient request data.
**Alternatives considered**: Persisting request context in GraphRepository; rejected because sandbox lookups are request-scoped and do not need long-term persistence.

## Decision: Enforce sandbox boundaries in AcpTooling using normalized paths
**Rationale**: Centralizing enforcement in AcpTooling ensures all file operations (read/write/edit) are consistently validated. Path normalization (absolute + normalized) prevents simple traversal attacks; sandbox check returns a structured error response rather than throwing.
**Alternatives considered**: Enforcing in each individual tool method or at the filesystem layer; rejected due to duplication and increased maintenance overhead.

## Decision: Provider-specific sandbox translation strategies
**Rationale**: ACP providers (Claude Code, Codex, Goose) require different env vars and CLI args. A strategy registry isolates provider differences and keeps AcpChatModel clean, while enabling future extensions.
**Alternatives considered**: Hard-coding provider logic in AcpChatModel; rejected to avoid conditional sprawl and reduce coupling.

## Decision: Ticket orchestrator prompt contributor for worktree creation
**Rationale**: The ticket orchestrator is responsible for creating per-ticket worktrees; explicit prompt guidance reduces ambiguity and ensures consistent worktree creation behavior.
**Alternatives considered**: Relying solely on implicit behavior or system prompts; rejected due to lower determinism and weaker testing.

---

## Merge Flow Decisions

## Decision: Two-phase decorator pattern for worktree merging
**Rationale**: Separating child agent merges (trunk→child) from parent dispatch merges (child→trunk) provides clear boundaries. Child agents receive merge status via `DispatchedAgentResultDecorator` on their individual results, while parent dispatch agents aggregate all child merges via `ResultsRequestDecorator` before passing to routing LLM.
**Alternatives considered**: Single decorator handling both phases; rejected because the merge direction and aggregation logic differ substantially between phases.

## Decision: Introduce ResultsRequest interface for dispatch result aggregation
**Rationale**: `TicketAgentResults`, `PlanningAgentResults`, and `DiscoveryAgentResults` all need merge aggregation capabilities. A common interface (`ResultsRequest`) enables a single `ResultsRequestDecorator` to handle all three types uniformly, following the existing decorator pattern.
**Alternatives considered**: Adding merge logic directly in `dispatchTicketAgentRequests()` and similar methods; rejected because it would duplicate merge logic across three dispatch methods and violate decorator pattern.

## Decision: Merge submodules before main worktree
**Rationale**: Git submodule pointers in the parent repo must be updated after submodule merges. Merging main first would leave stale submodule pointers. Submodule-first ordering ensures consistent repository state.
**Alternatives considered**: Parallel merging of submodules and main; rejected due to pointer update dependency.

## Decision: Stop on first conflict, build MergeAggregation with merged/pending/conflicted
**Rationale**: Continuing to merge after a conflict could compound issues and make rollback more complex. Stopping on first conflict and reporting pending items gives the routing LLM clear context to decide next steps.
**Alternatives considered**: Merge all and collect all conflicts; rejected because partial merges on multiple branches create complex rollback scenarios.

## Decision: Let routing LLM decide conflict handling (not decorator)
**Rationale**: Conflict resolution is a high-level decision (retry, interrupt user, skip agent, etc.). Decorators should report merge state, not make policy decisions. The routing LLM receives `MergeAggregation` in context and can choose to emit `MergerRequest`, `MergerInterruptRequest`, or proceed differently.
**Alternatives considered**: Decorator automatically creating `MergerInterruptRequest` on conflict; rejected because it removes LLM agency and doesn't allow for context-aware decisions.

## Decision: Add MergeDescriptor to individual AgentResult types
**Rationale**: Child agents need visibility into their trunk→child merge status. Adding `mergeDescriptor` field to `TicketAgentResult`, `PlanningAgentResult`, `DiscoveryAgentResult` allows the result to carry merge context through the system.
**Alternatives considered**: Storing merge status only in MergeAggregation; rejected because individual agent results need to know their own merge state for logging and debugging.

## Decision: Use GitWorktreeService for all merge operations
**Rationale**: `GitWorktreeService` already has `mergeWorktrees()` and `updateSubmodulePointer()` methods. Extending it with submodule ordering logic keeps git operations centralized.
**Alternatives considered**: Creating a separate MergeCoordinator service; rejected because it would duplicate git knowledge and add unnecessary abstraction.
