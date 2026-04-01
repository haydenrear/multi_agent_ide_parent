# Parallel Execution: Multi-Goal Branch Workflow

## Problem

Currently we run one goal at a time on main. The single-worktree model gives us `GoalCompletedEvent.worktreePath` after each goal, but there's no automated workflow for:
1. Running multiple goals in parallel on separate feature branches
2. Merging worktree results back into feature branches after goal completion
3. Merging feature branches back into main after all goals complete

## What Already Exists

- `StartGoalRequest.baseBranch` — goal submission already accepts a branch (defaults to "main")
- `clone_or_pull.py --branch <name>` — clones/syncs tmp repo to a specific branch
- `GoalCompletedEvent.worktreePath` — controller receives the worktree path at goal completion
- `standard_workflow.md` "Parallel Execution with Feature Branches" section — manual instructions exist but no automation
- `GitWorktreeService.createMainWorktree()` clones from `repositoryUrl` on `baseBranch`, creates a derived branch for agent work

## What Needs to Be Built

### 1. `merge_from_worktree.py` script (controller executable)

After `GoalCompletedEvent`, the controller needs a script that:
- Takes `worktreePath` from the event (the shared worktree with all agent commits)
- Fetches the worktree's derived branch into the tmp repo (the feature branch clone)
- Merges the derived branch into the feature branch in the tmp repo
- For each submodule: fetches from worktree submodule, merges into feature branch submodule
- Pushes submodules first (innermost → outermost), then pushes root
- Reports success/conflict

This replaces what `finalMergeToSource` used to do internally — now the controller does it externally via script.

```
Usage:
  python merge_from_worktree.py <worktree-path> [--dry-run]

  # worktree-path: from GoalCompletedEvent.worktreePath
  # Reads tmp repo from tmp_repo.txt
  # Merges worktree derived branch → tmp repo's current branch
  # Pushes submodules first, then root
```

### 2. `merge_feature_to_main.py` script (controller executable)

After all goals on a feature branch complete and merge_from_worktree succeeds:
- Switches tmp repo to main, pulls latest
- Merges feature branch into main for each submodule (innermost → outermost)
- Pushes everything
- Reports success/conflict

```
Usage:
  python merge_feature_to_main.py <feature-branch> [--dry-run]
```

### 3. Parallel goal workflow documentation

Update `standard_workflow.md` or create a new workflow variant documenting:

**Per-goal setup:**
1. Create feature branch in source repo + all submodules
2. `clone_or_pull.py --branch feature/<ticket>` — creates/syncs tmp repo on feature branch
3. Deploy and submit goal with `"baseBranch": "feature/<ticket>"`
4. Poll with subscribe until `GOAL_COMPLETED`
5. `merge_from_worktree.py <worktreePath>` — merges agent work into feature branch
6. Push feature branch

**After all goals complete:**
7. `merge_feature_to_main.py feature/<ticket>` for each feature branch
8. Pull main back into working tmp repo

**Key constraint:** Each goal needs its own tmp repo clone OR sequential execution sharing the same clone. The `tmp_repo.txt` path is a singleton — for true parallelism, we need either multiple tmp repo paths or a naming convention (e.g., `/private/tmp/multi_agent_ide_parent/<branch>/tmp_repo.txt`).

### 4. `poll.py` integration

When `GOAL_COMPLETED` is detected in subscribe mode, `poll.py` should print the `worktreePath` from the event so the controller can immediately invoke `merge_from_worktree.py`.

## What Does NOT Need to Change

- **No Java backend changes** — the single-worktree model and GoalCompletedEvent.worktreePath already provide everything needed
- **No changes to agent behavior** — agents work in the shared worktree regardless of what branch it was cloned from
- **No changes to deploy_restart.py** — deployment is branch-agnostic
- `clone_or_pull.py` already handles `--branch` correctly

## Sequencing

1. `merge_from_worktree.py` — needed first, this is the critical path
2. `poll.py` worktreePath print — small enhancement, do alongside #1
3. `merge_feature_to_main.py` — builds on #1
4. Workflow documentation — after scripts are tested

## Notes

- For now, sequential execution (one goal at a time per branch) is fine. True parallelism (multiple concurrent goals) requires solving the singleton `tmp_repo.txt` issue but that's a later optimization.
- The worktree path format from GoalCompletedEvent is a raw filesystem path (e.g., `/var/folders/.../UUID/`), not a `file:///` URI. Scripts should handle both formats defensively.
- Submodule merge order is always innermost → outermost to avoid stale pointers.
