# Parallel Execution: Branch Support for Multi-Goal Workflows

## Problem

Currently, the multi_agent_ide workflow always operates on `main`. When worktrees are created for agents, submodule commits get pushed directly to `main`, and subsequent clones/worktrees pull from `main`. This means only one goal can run at a time — if two goals run concurrently, they'll step on each other's submodule pushes to `main`.

## Goal

Support parallel execution of multiple goals by working on feature branches instead of `main`, and merging back to `main` only when a goal completes (like a pull request workflow).

## Current Flow (single-goal, main-only)

1. Clone repo into /tmp on `main`
2. Create worktrees branching from `main`
3. Agents work in worktrees, push submodule commits to `main`
4. Subsequent agent worktrees clone from `main`, getting those pushed commits
5. Final merge goes back to `main` in the source repo

## Desired Flow (parallel-safe, branch-based)

1. Clone repo into a **separate** /tmp location for each goal
2. At goal start, create a feature branch (e.g. `goal/<nodeId>` or user-specified) from `baseBranch`
3. All worktree operations (clone, branch, push submodules) target the **feature branch**, not `main`
4. Subsequent agent worktrees within the same goal clone from the feature branch
5. When a goal completes:
   a. Checkout `main` in the goal's repo
   b. Merge the feature branch into `main` for all submodules (leaves-first)
   c. Push submodules to `main` on remote
   d. Update submodule pointers in parent repo on `main`
   e. Push parent repo to `main`
6. The orchestrating process (the one that started the goals) can then pull `main` to get the merged results

## Key Changes Needed

### 1. Goal-scoped feature branch creation
- When a goal starts with a `baseBranch` (defaults to `main`), create a feature branch from it
- All worktree operations for that goal should use the feature branch as their "trunk"
- The feature branch name should be deterministic from the goal/nodeId (e.g. `goal/<short-nodeId>`)

### 2. Submodule push targeting
- `pushSubmoduleCommitsToRemote()` currently pushes to the derived branch or a `push-<uuid>` fallback
- It needs to push to the **goal's feature branch** instead of `main`
- When child worktrees are created (branchWorktree), the clone source should resolve commits from the feature branch

### 3. Clone-from-branch support
- `cloneRepository()` already accepts `baseBranch` and resolves it — this likely works
- Verify that `branchWorktree()` propagates the feature branch correctly when cloning from a source worktree
- Ensure submodule init/update checks out the feature branch in submodules too (not just `main`)

### 4. Final merge to main (PR-style)
- After the goal's agent work is complete, in `finalMergeToSource` or a new step:
  a. For each submodule (leaves-first): checkout `main`, merge the feature branch, push to `main`
  b. In the parent repo: update submodule pointers, commit, push to `main`
- This is essentially the same merge plan but targeting `main` instead of `baseBranch` → `derivedBranch`

### 5. Goal isolation via separate tmp repos
- Each goal should get its own /tmp clone so they don't interfere
- The process that starts goals should:
  - Clone a fresh copy (or create a new worktree) for each goal
  - Point the goal's `--repo` at that isolated copy
  - After the goal merges to `main`, pull results back into the main working repo

## Files Likely Affected

- `GitWorktreeService.java` — clone, branch, worktree creation, final merge, submodule push
- `GitMergeService.java` — merge operations, auto-commit
- `GoalExecutor.java` — goal initialization, baseBranch handling
- `MainWorktreeContext` / `WorktreeContext` — store the goal's feature branch name
- `OrchestratorCollectorRequestDecorator` — final merge trigger
- `WorktreeMergeResultDecorator` — trunk-to-child merge (needs branch awareness)
- `RepoUtil.java` — clone/branch utility methods

## Testing

- Unit tests: verify feature branch creation, submodule push targeting
- Integration tests: run two goals concurrently against same repo, verify no conflicts
- Verify submodule pointer consistency after parallel merges to main

## Notes

- This is related to existing tickets: `synchronized-merges.md`, `push-to-remote.md`
- The `baseBranch` parameter already exists in the goal start API — we extend its semantics
- Consider conflict resolution when two goals try to merge to `main` simultaneously (sequential merge + retry, or lock)
