# Internal Contracts: Repository Onboarding Pipeline

## Overview

This feature adds onboarding behavior by extending existing parser operations and introducing a pluggable episodic memory contract.

The operational chain remains:

`SetEmbedding` -> `RewriteHistory` (segmentation) -> `AddEpisodicMemory` (agent delegation) -> `GitParser.aggregateAllErrors()`

## Contract 1: Reuse of Existing Embedding Operation (`SetEmbedding`)

**Applies to**: commit embedding stage before segmentation

**Contract**:
1. Onboarding embedding stage reuses `SetEmbedding` as the entrypoint for embedding commits.
2. `SetEmbedding.embedIfNotEmbedded(...)` remains the component responsible for calling embedding client behavior.
3. Embedding failures are represented as node descriptor errors and remain visible through parser aggregation.
4. Any new segmentation metadata is added to the existing `CommitDiff` object and must not bypass existing idempotent commit embedding keys.

## Contract 2: Existing Parser Traversal and Error Aggregation (`GitParser`)

**Applies to**: traversal and failure reporting across onboarding operations

**Contract**:
1. Repository traversal order and node progression continue to be driven by parser operation execution.
2. Aggregated parser errors remain discoverable through existing aggregate error behavior.
3. New onboarding operations must emit descriptors/errors in the same parser-compatible shape so aggregate reporting remains complete.

## Contract 3: RewriteHistory Evolution for Contiguous Segmentation

**Applies to**: `RewriteHistory` operation and associated service

**Contract**:
1. `RewriteHistory` is evolved to orchestrate contiguous segment planning from existing embedded commit history.
2. Segment output is deterministic for fixed inputs/configuration.
3. Segment plan enforces min/max commit constraints.
4. `RewriteHistory.parse(...)` preserves parser result flow and does not replace parser return semantics with a detached execution path.

## Contract 4: AddEpisodicMemory Evolution via EpisodicMemoryAgent

**Applies to**: `AddEpisodicMemory` operation

**Contract**:
1. `AddEpisodicMemory` delegates episodic behavior through injected `EpisodicMemoryAgent`.
2. Delegation preserves parser option integration and existing provenance capture.
3. Segment processing uses one `runAgent` call per segment context.
4. Memory tool sequencing (`recall/retain/reflect`) is runtime-managed by agent configuration, not onboarding code.

## Contract 5: EpisodicMemoryAgent Interface

**Applies to**: app-provided implementation in `multi_agent_ide`

**Required methods (logical contract)**:
- `runAgent(segmentContext)`

**Behavioral requirements**:
1. Implementations are swappable without changing onboarding core code.
2. Interface supports provenance fields (`repoPath`, `branch`, `commit`, `depth`, `segmentIndex`, `runId`).
3. Hard memory integrity failures are surfaced as terminal errors unless run mode is analysis-only.

## Contract 6: Integration Test Boundary

**Applies to**: full-process tests in `multi_agent_ide`

**Contract**:
1. Full-process integration tests run in `multi_agent_ide` with `@SpringBootTest`.
2. Tests use a real repository fixture (including submodule-aware behavior).
3. Only embedding provider, Hindsight provider, and `EpisodicMemoryAgent` are mocked by default.
4. Test harness supports swapping episodic agent implementations via profile/bean override for experimentation.
