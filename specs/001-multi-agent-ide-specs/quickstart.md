# Quickstart

## Goal
Validate end-to-end orchestration tests for multi_agent_ide with and without submodules, plus revision and failure flows.

## Run Tests

From the repository root:

```sh
./gradlew :multi_agent_ide:test
```

## Expected Results

- Orchestrator lifecycle tests complete with all nodes transitioning to COMPLETED in the happy path.
- Worktree creation events include main-only or main+submodule, depending on the scenario.
- Review and merge events appear in the event stream for ticket workflows.
