# Test Plan: Unit First, Then Full Integration

## Scope

This file defines Story 4 and Story 5 testing strategy for the repository onboarding pipeline.

- Story 4: unit tests for core logic
- Story 5: full-process integration tests in `multi_agent_ide`
- Out of scope for this ticket: `test_graph` feature files

## Story 4: Unit Test Plan

### Module placement

- Primary unit tests: `commit-diff-context`
- Application contract unit tests: `multi_agent_ide_java_parent/multi_agent_ide`

### Unit test targets

- Segmentation determinism for fixed embeddings and seed/config.
- Contiguous coverage and min/max commit constraints.
- Embedding fallback behavior when AST extraction fails.
- Segment orchestration call order: single `runAgent` invocation per segment.
- Retry-policy behavior for transient and terminal failures.
- Reuse of existing embedding/parser operation flow (`SetEmbedding`, `GitParser`) instead of parallel duplicate flow.
- Evolution of existing operations (`AddEpisodicMemory`, `RewriteHistory`) with preserved parser-operation contracts.

### Suggested unit test classes

- `ContiguousSegmentationServiceTest`
- `EmbeddingFallbackServiceTest`
- `SegmentEpisodicOrchestratorTest`
- `SegmentRetryPolicyTest`
- `EpisodicMemoryAgentContractTest`
- `SetEmbeddingReusePathTest`
- `RewriteHistoryEvolutionTest`
- `AddEpisodicMemoryAgentDelegationTest`

## Story 5: Full Integration Test Plan (`multi_agent_ide`)

### Required placement

- All full-process Spring Boot integration tests for this feature live in:
  - `multi_agent_ide_java_parent/multi_agent_ide`

### Integration harness expectations

- Use `@SpringBootTest` in `multi_agent_ide`.
- Use a real git repository fixture (with submodule-aware behavior) as onboarding input.
- Mock only these dependencies:
  - embedding provider
  - Hindsight provider
  - `EpisodicMemoryAgent`
- Keep ingestion, segmentation, orchestration, run logging, and cleanup components real.
- Assert onboarding still executes through existing parser operation chain and does not bypass it with a new parallel flow.

### Agent experimentation support

- Provide profile-based or bean-override test configuration so agent strategy can be swapped without changing onboarding core code.
- Keep a default deterministic mock agent for reproducible CI runs.
- Add optional experimental integration runs that inject alternate agent implementations for comparison.

### Suggested integration test classes

- `RepoOnboardingHappyPathIT`
- `RepoOnboardingRetryIT`
- `RepoOnboardingMemoryFailureIT`
- `RepoOnboardingCleanupIT`
- `RepoOnboardingAgentSwapIT`

### Core integration scenarios

1. End-to-end onboarding succeeds with real repository fixture and writes run metadata per segment.
2. Planner emits contiguous exhaustive segments under configured max-commit constraints.
3. Transient episodic failure retries up to limit and records retry telemetry.
4. Hard memory-backend integrity failure halts run with failed status.
5. Cleanup removes disposable ingestion workspace unless keep-for-debug is enabled.
6. Agent implementation swap runs through identical pipeline steps and records comparable run outputs.

## Minimal Assertions Per Test

- Segment ranges are contiguous and exhaustive.
- Per-segment memory invocation remains one-call and ordered by segment index.
- Run log includes per-segment status and retry count.
- Cleanup behavior matches configuration.
- Mock usage boundary is enforced (only embedding/Hindsight/agent are mocked).
- Existing operation-chain reuse is verified (`SetEmbedding`, `GitParser`, evolved `AddEpisodicMemory`, evolved `RewriteHistory`).
