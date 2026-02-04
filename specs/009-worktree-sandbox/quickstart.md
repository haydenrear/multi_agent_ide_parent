# Quickstart: Worktree Sandbox for Agent File Operations

## Prerequisites
- Java 21
- Gradle wrapper available (`./gradlew`)

## Run unit tests
```shell
./gradlew :multi_agent_ide:test
```

## Run integration tests
```shell
./gradlew :multi_agent_ide:test -Pprofile=integration
```

## Manual verification checklist

### Sandbox Enforcement
1. Create an orchestrator with a worktree and trigger a ticket agent.
2. Confirm the request includes worktree context (main + submodules).
3. Invoke a file read inside the worktree (should succeed).
4. Invoke a file read/write/edit outside the worktree (should return sandbox error JSON).

### Worktree Context Flow
1. Create an orchestrator with a repository containing submodules.
2. Trigger a ticket dispatch with multiple ticket agents.
3. Verify each TicketAgentRequest has its own worktree context (via attachWorktreesToTicketRequests).
4. Confirm submodule worktrees are included in WorktreeSandboxContext.

### Merge Flow - Phase 1 (Trunk → Child)
1. Create a ticket agent with its own worktree.
2. Make changes in the trunk worktree (simulate upstream changes).
3. Complete the ticket agent work.
4. Verify TicketAgentResult contains MergeDescriptor with direction=TRUNK_TO_CHILD.
5. If conflicts exist, verify conflictFiles list is populated.

### Merge Flow - Phase 2 (Child → Trunk)
1. Dispatch multiple ticket agents (e.g., 3 agents).
2. Have each agent complete work in their worktrees.
3. After all agents complete, verify TicketAgentResults contains MergeAggregation.
4. Verify MergeAggregation.merged contains successfully merged agents.
5. If a conflict occurs, verify:
   - MergeAggregation.conflicted contains the first conflicted agent
   - MergeAggregation.pending contains remaining unprocessed agents
   - Merge stops at first conflict (no further merges attempted)

### Submodule Merge Ordering
1. Create a repository with at least 2 submodules.
2. Trigger a ticket agent that modifies files in both submodules and main repo.
3. Complete the agent work.
4. Verify merge order in MergeDescriptor.submoduleMergeResults:
   - All submodules merged before main worktree
   - Submodule pointers updated after successful submodule merges

### Routing LLM Integration
1. Complete a dispatch with merge conflicts.
2. Verify routing LLM receives MergeAggregation in template variables.
3. Verify LLM can emit MergerRequest or MergerInterruptRequest based on conflict status.

## Key Test Files
- `WorktreeContextRequestDecoratorTest.java` - context propagation
- `WorktreeMergeResultDecoratorTest.java` - Phase 1 trunk→child merge
- `WorktreeMergeResultsDecoratorTest.java` - Phase 2 child→trunk merge
- `GitWorktreeServiceMergeTest.java` - submodule merge ordering

## Integration Test Features
- `worktree-context-flow.feature` - context propagation scenarios
- `worktree-merge-child.feature` - Phase 1 merge scenarios
- `worktree-merge-parent.feature` - Phase 2 merge scenarios
