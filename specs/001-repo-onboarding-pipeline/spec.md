# Feature Specification: Repository Onboarding Pipeline

**Feature Branch**: `001-repo-onboarding-pipeline`  
**Created**: 2026-02-12  
**Status**: Draft  
**Input**: User description: "Implement repository onboarding across submodules, embeddings, contiguous temporal segmentation, serial episode generation with Hindsight memory, and a pluggable EpisodicMemoryAgent; skip test_graph for now and define Spring Boot style integration tests."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Build Deterministic Onboarding Runs (Priority: P1)

As a platform engineer, I need onboarding to build a disposable ingestion repository, compute commit embeddings, and generate contiguous historical segments so every repository can be processed predictably and repeatably.

**Why this priority**: This is the foundation for all later memory generation. Without deterministic ingest and segmentation, episode quality and replayability are unreliable.

**Independent Test**: Can be tested end-to-end by onboarding a repo with submodules and asserting reproducible ingestion metadata, embedding coverage, and contiguous segment boundaries.

**Cucumber Test Tag**: `@repo-onboarding-core`

**Acceptance Scenarios**:

1. **Given** a repository with at least two submodules, **When** onboarding starts, **Then** a disposable ingestion repository is created with root and submodule histories preserved under stable prefixes.
2. **Given** an ordered commit history in the ingestion repository, **When** embedding processing runs, **Then** each commit receives either a primary embedding or an explicit fallback embedding reason.
3. **Given** embeddings and segmentation constraints, **When** segmentation runs, **Then** output ranges are contiguous, non-overlapping, and cover the full commit sequence.
4. **Given** a configured maximum commits-per-segment limit, **When** segment plans are generated, **Then** no segment exceeds the configured limit.

---

### User Story 2 - Generate Serial Episodic Memory Updates (Priority: P1)

As a memory-augmented analysis workflow, I need each history segment to run through a pluggable episodic agent entrypoint that executes memory-aware analysis for the segment in order.

**Why this priority**: The primary value of onboarding is durable understanding over time. Serial memory conditioning is required for coherent long-range repository knowledge.

**Independent Test**: Can be tested with a stubbed episodic memory agent by validating per-segment invocation order, one-call `runAgent` behavior, and persisted handoff continuity.

**Cucumber Test Tag**: `@repo-onboarding-episodic`

**Acceptance Scenarios**:

1. **Given** a K-segment plan, **When** episodic processing runs, **Then** segments are processed strictly in chronological order.
2. **Given** segment processing begins, **When** the episodic agent is invoked, **Then** onboarding performs exactly one `runAgent` call with the segment context.
3. **Given** the segment completes, **When** memory is committed by the agent runtime, **Then** onboarding records bounded handoff and per-segment outcome before moving to the next segment.
4. **Given** the episodic agent is configured with Hindsight MCP tools, **When** it runs, **Then** memory organization and consolidation happen inside the agent runtime without explicit onboarding-side tool choreography.

---

### User Story 3 - Enable Application-Provided Episodic Agent Implementations (Priority: P2)

As an application integrator, I need the commit-diff module to depend on an EpisodicMemoryAgent interface so each application can provide its own Hindsight-aware implementation.

**Why this priority**: The onboarding core should be reusable across applications while allowing each deployment to customize memory workflows and tools.

**Independent Test**: Can be tested by wiring a test implementation in one application module and verifying the onboarding pass delegates memory actions exclusively through the interface.

**Cucumber Test Tag**: `@repo-onboarding-agent-contract`

**Acceptance Scenarios**:

1. **Given** onboarding runs in a host application, **When** episodic processing executes, **Then** memory operations are routed through the injected EpisodicMemoryAgent contract.
2. **Given** no custom episodic agent bean is provided, **When** onboarding starts, **Then** startup fails fast with a clear configuration error.
3. **Given** the multi-agent application provides an implementation, **When** onboarding runs, **Then** that implementation is invoked for every segment step required by policy.

---

### User Story 4 - Validate Core Logic with Unit Tests (Priority: P2)

As a maintainer, I need focused unit tests over segmentation, retry behavior, and episodic orchestration sequencing so logic regressions are caught quickly before full-context test runs.

**Why this priority**: Unit tests provide fast feedback and isolate failures in core decision logic, especially around contiguous segmentation and ordering guarantees.

**Independent Test**: Can be tested by running module unit test suites that validate deterministic segmentation, fallback behavior, retry policy, and single-call episodic agent invocation ordering with test doubles.

**Cucumber Test Tag**: `@repo-onboarding-unit-tests`

**Acceptance Scenarios**:

1. **Given** fixed commit embeddings and deterministic settings, **When** segment planning unit tests run, **Then** the same contiguous ranges are generated on every run.
2. **Given** AST extraction failures for selected commits, **When** embedding unit tests run, **Then** fallback status is recorded and pipeline control flow continues.
3. **Given** episodic orchestration for one segment, **When** orchestration unit tests run, **Then** exactly one `runAgent` call executes for the segment context.
4. **Given** transient segment failure, **When** retry-policy unit tests run, **Then** attempts stop at configured limits and final state is reported correctly.

---

### User Story 5 - Validate Full Process in multi_agent_ide Integration Context (Priority: P2)

As a maintainer, I need full-process integration tests in the `multi_agent_ide` module so we can run onboarding over a real repository and experiment with swappable episodic agent implementations.

**Why this priority**: This validates real wiring boundaries where onboarding core meets application-provided memory behavior and supports iterative experimentation with memory-agent strategies.

**Independent Test**: Can be tested by running `@SpringBootTest` integration suites in `multi_agent_ide` using a real git repository fixture while mocking only embedding provider, Hindsight provider, and episodic agent (with optional agent implementation swaps).

**Cucumber Test Tag**: `@repo-onboarding-spring-integration`

**Acceptance Scenarios**:

1. **Given** a `multi_agent_ide` integration test context and a real repository fixture with submodules, **When** onboarding is triggered, **Then** ingestion, segmentation, orchestration, and cleanup execute end-to-end with persisted run telemetry.
2. **Given** integration tests with dependency overrides, **When** the test context is built, **Then** only embedding provider, Hindsight provider, and episodic agent are mocked while other pipeline components remain real.
3. **Given** multiple episodic agent implementations are available, **When** integration tests are run with different test profiles, **Then** the onboarding flow remains stable and recorded outputs enable comparison across agent strategies.
4. **Given** one segment agent invocation fails transiently, **When** retries are enabled in integration tests, **Then** retries occur up to configured limits and status is recorded.
5. **Given** onboarding completes in normal mode, **When** cleanup runs, **Then** ingestion workspace artifacts are removed while run logs remain queryable.

---

### Edge Cases

- Repository has no submodules; onboarding still creates a valid ingestion repository and manifest.
- Root commit has no parent diff; embedding stage records a defined initialization behavior.
- AST parsing is unavailable for some files; embedding stage falls back without aborting the run.
- Requested `K` is smaller than minimum allowed by max-commit constraint; planner increases `K` deterministically.
- Episodic agent fails after partial internal memory operations; retry policy prevents duplicate segment processing where possible.
- Cleanup fails to remove ingestion workspace; run ends with warning status and explicit cleanup-needed marker.

## Requirements *(mandatory)*

### Functional Requirements

#### Ingestion and Repository Preparation

- **FR-001**: System MUST create a disposable ingestion repository for each onboarding run without mutating the source repository.
- **FR-002**: System MUST include root history and selected submodule histories in the ingestion repository under stable, namespaced prefixes.
- **FR-003**: System MUST produce an ingestion manifest containing repository identity, included submodules, path mappings, and commit counts.
- **FR-004**: System MUST preserve original commit authorship, timestamps, and messages during ingestion import.

#### Embeddings and Change Representation

- **FR-005**: System MUST process commits in deterministic chronological order for segmentation input.
- **FR-006**: System MUST produce one embedding record per eligible commit, or an explicit fallback/skipped status with reason.
- **FR-007**: Embedding writes MUST be idempotent for the same run, commit identifier, and embedding version.
- **FR-008**: Embedding metadata MUST include commit identifier, commit time, changed path scope, and embedding version identity.

#### Contiguous Temporal Segmentation

- **FR-009**: System MUST reduce embedding dimensions before segmentation using a deterministic configuration.
- **FR-010**: System MUST generate `K` contiguous commit ranges that are non-overlapping and collectively exhaustive.
- **FR-011**: Segment generation MUST enforce minimum and maximum commits-per-segment constraints.
- **FR-012**: If configured `K` violates segment constraints, system MUST adjust to the nearest valid deterministic plan and record the adjustment.
- **FR-013**: Segment planning MUST be reproducible for identical inputs and seed/configuration.

#### Serial Episodic Processing

- **FR-014**: System MUST process segments strictly in chronological order and MUST NOT run segment memory passes concurrently.
- **FR-015**: For each segment, system MUST invoke the `EpisodicMemoryAgent` once via a `runAgent`-style call with segment context.
- **FR-016**: Segment execution MUST capture zero to N retained episodes bounded by configured per-segment limits.
- **FR-017**: Memory tool usage (`recall`, `retain`, `reflect`, model refresh) MUST be handled implicitly by the agent runtime configuration rather than explicit onboarding orchestration code.
- **FR-018**: Segment output MUST include a bounded handoff summary for subsequent segment processing.

#### EpisodicMemoryAgent Contract and Hindsight Semantics

- **FR-019**: Commit onboarding memory behavior MUST depend on an injected `EpisodicMemoryAgent` interface rather than a hard-coded implementation.
- **FR-020**: The `EpisodicMemoryAgent` contract MUST expose a single segment execution call (`runAgent`) that receives repo + segment context.
- **FR-021**: Memory retention semantics (including temporal preservation for unchanged memories) MUST be handled within the agent runtime and Hindsight configuration.
- **FR-022**: Episodic processing MUST support bank/tag/model organization signals so mental model refresh is scoped to the onboarding context.
- **FR-023**: Multi-agent application modules MUST be able to provide their own `EpisodicMemoryAgent` implementation without modifying onboarding core code.

#### Operational Controls and Cleanup

- **FR-024**: System MUST persist minimal onboarding run metadata including run identity, status, configuration snapshot, segment ranges, and per-segment outcomes.
- **FR-025**: System MUST support configurable retry behavior for segment-level episodic failures.
- **FR-026**: System MUST stop processing on hard memory-backend integrity failures unless explicitly configured for analysis-only mode.
- **FR-027**: System MUST remove disposable ingestion repositories at run completion unless keep-for-debug is enabled.
- **FR-028**: Dry-run mode MUST execute ingestion, embeddings, and segmentation without committing episodic memory updates.

#### Test Strategy and Placement

- **FR-029**: The feature MUST include unit tests for segmentation determinism, embedding fallback behavior, episodic sequencing, and retry-policy logic.
- **FR-030**: Full-process Spring Boot integration tests for this feature MUST be implemented in `multi_agent_ide_java_parent/multi_agent_ide`, not in `commit-diff-context`.
- **FR-031**: Integration tests MUST execute onboarding against a real git repository fixture, including submodule-aware behavior.
- **FR-032**: Integration tests MUST mock only the embedding provider, Hindsight provider, and `EpisodicMemoryAgent` dependencies by default.
- **FR-033**: Integration test harness MUST allow swapping episodic agent implementations/profiles to support experimentation without changing onboarding core code.

#### Reuse and Evolution of Existing Parser Operations

- **FR-034**: Embedding behavior in onboarding MUST reuse the existing `SetEmbedding` operation path as the primary embedding entry point and extend it for segmentation needs rather than replacing it with a parallel embedding pipeline.
- **FR-035**: Repository traversal, node progression, and aggregate error handling in onboarding MUST continue to use existing `GitParser` behavior and aggregation semantics.
- **FR-036**: Episodic memory behavior MUST evolve the existing `AddEpisodicMemory` operation to delegate through `EpisodicMemoryAgent` while preserving current parse-option based integration with parser traversal.
- **FR-037**: History rewrite behavior MUST evolve the existing `RewriteHistory` operation to execute contiguous segmentation and serial episodic orchestration while preserving its parser-oriented contract and result flow.

### Key Entities *(include if feature involves data)*

- **Onboarding Run**: The lifecycle record for one repository onboarding execution, including configuration and status.
- **Ingestion Manifest**: The reproducibility record mapping source repositories/submodules to ingestion prefixes and identity snapshots.
- **Commit Embedding Record**: The stored vector + metadata representation of one commit-level change unit.
- **K Segment Plan**: Ordered contiguous commit ranges that partition the full commit history.
- **Segment Episode Batch**: The set of episodes generated for a segment plus handoff summary and outcome metadata.
- **EpisodicMemoryAgent**: Application-provided contract that executes a single segment run entrypoint, while memory tooling remains internal to the configured runtime.
- **Run Log Entry**: Minimal operational record for retries, failures, and completion telemetry.

### Assumptions

- Onboarding repositories can be cloned and read in the execution environment.
- Hindsight memory operations are externally managed and remain the source of truth for episode and mental model content.
- Segment-level evidence and memory payloads can be represented without introducing a new custom evidence schema.

### Dependencies

- Repository tooling for submodule-aware ingestion and history-preserving import.
- Embedding capability for change representations with fallback behavior.
- A memory backend and application implementation conforming to the `EpisodicMemoryAgent` contract.
- Application wiring in the multi-agent module to provide the production episodic agent implementation.

### Out of Scope

- Human-friendly rewritten git history as a persistent artifact.
- Perfect AST coverage for every language and file type.
- New bespoke data models for memory content beyond minimal run metadata.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of onboarding runs produce an ingestion manifest with source/submodule mapping and run identity metadata.
- **SC-002**: At least 95% of eligible commits receive primary embeddings; remaining commits are explicitly marked fallback or skipped with reason.
- **SC-003**: 100% of generated segment plans are contiguous, non-overlapping, and cover the full commit sequence while respecting configured segment size bounds.
- **SC-004**: 100% of segments are processed in chronological order with one recorded agent invocation per segment.
- **SC-005**: Unit test suite covers segmentation determinism, fallback behavior, orchestration ordering, and retry logic for onboarding core behavior.
- **SC-006**: Integration test suite in `multi_agent_ide` covers happy path, retry path, and memory-backend failure path using a real repository fixture.
- **SC-007**: By default, ingestion workspace cleanup succeeds for at least 99% of successful runs; cleanup failures are explicitly recorded.
- **SC-008**: Unit and integration tests verify that onboarding executes through the existing parser operation chain without introducing a disconnected duplicate flow.

## Notes on Gherkin Feature Files For Test Graph

Per current direction for this ticket, test_graph feature-file generation is intentionally deferred.

This specification still uses Gherkin-style acceptance scenarios as executable behavior definitions, but implementation validation for this ticket will be delivered through application integration tests in module test suites.

When test_graph work is resumed, these acceptance scenarios and tags should be mapped into feature files with minimal generic step definitions.
