# Phase 0 Research: Repository Onboarding Pipeline

## Decision: Evolve existing parser operation chain instead of introducing a parallel onboarding pipeline
**Rationale**: The current parser flow already provides traversal ordering, descriptor capture, and aggregate error behavior. Reusing this chain preserves compatibility and limits behavioral drift while adding segmentation and episodic orchestration capabilities.
**Alternatives considered**: Build a standalone onboarding pipeline outside parser operations. Rejected because it duplicates traversal/embedding logic and risks divergence from existing commit-processing behavior.

## Decision: Keep `SetEmbedding` as the primary embedding entry point
**Rationale**: `SetEmbedding` already composes `AddCodeBranch` + embedding operations and maps embedding failures into parser descriptors. Extending this path for segmentation prerequisites keeps idempotency and error handling consistent.
**Alternatives considered**: Create a new embedding-only service path and bypass parser options. Rejected because it fragments embedding semantics and weakens parser-chain traceability.

## Decision: Preserve `GitParser` traversal and aggregate error semantics as the onboarding backbone
**Rationale**: `GitParser.aggregateAllErrors()` provides a single aggregation point across node descriptors. Onboarding needs this to surface partial failures (fallback embeddings, episodic retries) without opaque side channels.
**Alternatives considered**: Replace aggregate errors with per-stage custom exception structures. Rejected because it breaks existing parser consumers and reduces compatibility with current operations.

## Decision: Default segmentation strategy is deterministic contiguous drift-based boundaries
**Rationale**: Drift scoring over ordered embeddings is cheap, deterministic, and naturally respects contiguous ranges required by the feature. It scales better than exhaustive optimization for large histories.
**Alternatives considered**: Unconstrained k-means only (rejected: non-contiguous clusters) and DP-only segmentation (rejected as expensive default for large repositories).

## Decision: Evolve `RewriteHistory` into segmentation + serial episodic orchestrator entrypoint
**Rationale**: `RewriteHistory` already owns history rewrite intent. Extending it to produce contiguous segments and drive serial segment execution keeps behavior discoverable in one operation boundary.
**Alternatives considered**: Add a separate orchestrator operation and deprecate `RewriteHistory`. Rejected due to extra migration overhead and duplicate lifecycle entrypoints.

## Decision: Evolve `AddEpisodicMemory` to delegate through `EpisodicMemoryAgent`
**Rationale**: `AddEpisodicMemory` already has parser-option integration and provenance construction. Delegation through an interface preserves extension points while keeping parse integration intact.
**Alternatives considered**: Remove `AddEpisodicMemory` and move all behavior into app-specific services. Rejected because parser-linked provenance and sequencing logic would be duplicated.

## Decision: `EpisodicMemoryAgent` contract uses a single `runAgent` segment call
**Rationale**: The onboarding layer should stay minimal and delegate memory behavior to the agent runtime, where Hindsight MCP tool wiring already manages recall/retain/reflect/model refresh behavior.
**Alternatives considered**: Expose explicit recall/retain/reflect methods in onboarding. Rejected because it over-models internal tool choreography and duplicates runtime concerns.

## Decision: Keep memory content in Hindsight, persist only minimal run metadata locally
**Rationale**: Feature direction explicitly avoids bespoke internal memory schemas. Minimal run metadata is enough for reproducibility, retries, and debugging while Hindsight remains source of truth.
**Alternatives considered**: Introduce internal evidence packs for episodes/mental models. Rejected by scope and by requirement to avoid custom memory data models.

## Decision: Full-process integration tests live in `multi_agent_ide` with real repository fixtures
**Rationale**: This is the integration boundary where application-provided episodic agent implementations are wired and swapped for experimentation. Real repositories validate ingestion/submodule behavior more reliably than synthetic mocks.
**Alternatives considered**: Keep full integration tests in `commit-diff-context`. Rejected because it cannot validate application-level `EpisodicMemoryAgent` wiring and swap strategies.

## Decision: Integration tests mock only embedding provider, Hindsight provider, and episodic agent by default
**Rationale**: Narrow mocking preserves high-fidelity verification of ingestion, traversal, segmentation, orchestration, and cleanup flows while keeping external dependencies controlled.
**Alternatives considered**: Mock repository traversal and segmentation services as well. Rejected because it would no longer validate full-process behavior and would miss integration regressions.
