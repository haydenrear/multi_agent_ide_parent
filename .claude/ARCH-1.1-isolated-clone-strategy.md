# ARCH-1.1: Isolated Clone Strategy for Per-Goal Worktrees

## Problem Statement
Currently, all goals share a single tmp repository at `/private/tmp/multi_agent_ide_parent`. This causes:
- Parallel tickets to interfere with each other (all push/pull through main)
- Non-deterministic merge outcomes
- Blocked execution when main is being pulled/pushed

## Solution: Per-Goal Isolated Clones

### Key Design Decisions

#### 1. Clone Location Strategy
- **Base Path**: Each goal can specify a custom `goalRepoTmpDir` parameter
- **Default**: If not specified, use a system-default tmp location (e.g., `~/.multi-agent-ide/tmp/{goal-id}`)
- **Semantics**: The clone should be independent, with no shared state between parallel goals

#### 2. Clone Initialization Flow
```
StartGoalRequest (with optional goalRepoTmpDir)
  ↓
GoalExecutor
  ↓
AgentLifecycleHandler.initializeOrchestrator()
  ↓
GitWorktreeService.createMainWorktree()
  ↓
Invoke clone_or_pull.py with --target-dir and --branch flags
```

#### 3. clone_or_pull.py Enhancement
The script must support:
- `--target-dir PATH`: Custom clone location (instead of hardcoded `/private/tmp/multi_agent_ide_parent`)
- `--branch BRANCH`: Checkout non-main branch during clone (supports baseBranch parameter)
- Backward compatibility: If neither flag is provided, use default behavior

**Phase 1 (Clone/Sync) Logic**:
- If `--target-dir` is provided, clone to that directory
- If directory exists and is a valid git repo, pull latest from origin
- After clone, checkout the specified `--branch` (default: main)
- Initialize submodules on the specified branch

#### 4. Parameters Flow

```
StartGoalRequest.goalRepoTmpDir (optional)
  ↓
GoalExecutor (passes to AgentLifecycleHandler)
  ↓
AgentLifecycleHandler.initializeOrchestrator(goalRepoTmpDir)
  ↓
GitWorktreeService.createMainWorktree(goalRepoTmpDir)
  ↓
Invoke clone_or_pull.py --target-dir $goalRepoTmpDir --branch $baseBranch
```

#### 5. Isolation Guarantees

**Per-Goal Independence**:
- Each goal gets its own clone directory
- Each goal creates its own derived branch (baseBranch + UUID)
- Each goal's worktree is stored in `~/.multi-agent-ide/worktrees/{worktreeId}`

**No Shared State**:
- No shared tmp repo file (`tmp_repo.txt`)
- No read/write races on main branch
- Each goal can safely commit and push to its derived branch

#### 6. Merge-Back Strategy

After goal completion, merge back to main:
1. Use `GitWorktreeService.finalMergeToSource()` to merge worktree → source clone
2. Add `mergeBaseToMain()` extension to propagate changes from source clone to upstream main
3. Leaves-first merge strategy (submodules before parent) for consistent state
4. Reconcile submodule pointers in each step

#### 7. Error Handling

- If `--target-dir` doesn't exist, create it (with mkdir -p)
- If clone fails, propagate error with clear messaging
- If branch checkout fails during clone, fallback to main and log warning
- Phase 2 verification gate will catch any invalid state

## Implementation Checklist

- [ ] **clone_or_pull.py**: Add `--target-dir` and `--branch` flags
- [ ] **clone_or_pull.py**: Phase 1 logic respects both flags
- [ ] **StartGoalRequest**: Add optional `goalRepoTmpDir` field
- [ ] **GoalExecutor**: Pass `goalRepoTmpDir` through to AgentLifecycleHandler
- [ ] **AgentLifecycleHandler**: Accept and propagate `goalRepoTmpDir`
- [ ] **GitWorktreeService**: Pass `goalRepoTmpDir` to clone_or_pull.py invocation
- [ ] **GitWorktreeService**: Extend with `mergeBaseToMain()` method
- [ ] **EventBus**: Emit events for merge success/failure (event-driven observability)

## Backward Compatibility

- If `goalRepoTmpDir` is null/blank, use system default (no breaking changes)
- clone_or_pull.py gracefully handles missing flags (uses defaults)
- Existing code paths remain functional
