# Tasks: Repository Onboarding Pipeline

**Input**: Design documents from `/specs/001-repo-onboarding-pipeline/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/, quickstart.md, integration-tests.md

**Tests**: Tests are explicitly required by the specification (User Story 4 and User Story 5).

**Organization**: Tasks are grouped by user story so each story can be implemented and validated independently.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependency on incomplete tasks)
- **[Story]**: User story label (`[US1]`, `[US2]`, `[US3]`, `[US4]`, `[US5]`)
- Every task includes an exact file path

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Prepare code locations and shared configuration needed by all stories.

- [X] T001 Create onboarding package structure under `commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/episodic/`
- [X] T002 Add onboarding pipeline configuration properties in `commit-diff-context/src/main/java/com/hayden/commitdiffcontext/cdc_config/OnboardingPipelineConfigProps.java`
- [X] T003 [P] Add onboarding integration test package structure in `multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/integration/onboarding/`
- [X] T004 [P] Add onboarding integration support package in `multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/integration/onboarding/support/`
- [ ] T005 [P] Align onboarding task references and fixture expectations in `specs/001-repo-onboarding-pipeline/integration-tests.md`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Build shared models/services that block all user-story implementation.

**CRITICAL**: Complete this phase before starting user-story phases.

- [X] T006 Create onboarding run metadata model in `commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/episodic/model/OnboardingRunMetadata.java`
- [X] T007 [P] Create ingestion manifest model in `commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/episodic/model/IngestionManifest.java`
- [X] T008 [P] Create segment plan model in `commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/episodic/model/SegmentPlan.java`
- [X] T009 [P] Create segment execution model in `commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/episodic/model/SegmentExecution.java`
- [X] T010 Create run log store contract in `commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/episodic/service/OnboardingRunLogStore.java`
- [X] T011 Create deterministic segmentation service contract in `commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/episodic/service/ContiguousSegmentationService.java`
- [X] T012 Implement deterministic contiguous segmentation service in `commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/episodic/service/DefaultContiguousSegmentationService.java`
- [X] T013 Create onboarding orchestration facade for parser-chain flow in `commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/episodic/service/OnboardingOrchestrationService.java`

**Checkpoint**: Foundational models/services are ready; user stories can proceed.

---

## Phase 3: User Story 1 - Build Deterministic Onboarding Runs (Priority: P1) 🎯 MVP

**Goal**: Build reproducible ingestion + embedding + contiguous segmentation over full repository history.

**Independent Test**: Run onboarding against a repo with submodules and verify deterministic ingestion manifest, embedding coverage/fallback, and contiguous segment ranges.

### Implementation for User Story 1

- [X] T014 [US1] Add onboarding embedding metadata fields to the existing commit diff entity in `commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/entity/CommitDiff.java`
- [X] T015 [US1] Populate and persist onboarding embedding metadata on `CommitDiff` during embedding flow in `commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/SetEmbedding.java`
- [X] T016 [US1] Evolve rewrite service contract to return onboarding artifacts in `commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/episodic/service/RewriteHistoryService.java`
- [X] T017 [US1] Implement ingestion repo preparation and manifest creation in `commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/episodic/service/DefaultRewriteHistoryService.java`
- [X] T018 [US1] Update rewrite operation to consume evolved rewrite result in `commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/RewriteHistory.java`
- [X] T019 [P] [US1] Persist onboarding run metadata and status transitions in `commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/episodic/service/OnboardingOrchestrationService.java`
- [X] T020 [US1] Wire contiguous segmentation into rewrite flow in `commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/episodic/service/DefaultRewriteHistoryService.java`
- [X] T021 [US1] Enforce min/max segment constraints and deterministic seed behavior in `commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/episodic/service/DefaultContiguousSegmentationService.java`
- [X] T022 [US1] Add dry-run behavior to skip episodic writes while preserving run artifacts in `commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/RewriteHistory.java`
- [X] T023 [US1] Expose onboarding run/segment/log query endpoints in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/controller/OrchestrationController.java`

**Checkpoint**: US1 delivers deterministic onboarding and queryable run outputs.

---

## Phase 4: User Story 2 - Generate Serial Episodic Memory Updates (Priority: P1)

**Goal**: Process segments serially and perform a single `runAgent` call per segment.

**Independent Test**: Execute segmented onboarding with stubbed memory backend and verify ordered segment execution plus one `runAgent` invocation per segment.

### Implementation for User Story 2

- [X] T024 [US2] Create episodic memory agent contract in `commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/episodic/EpisodicMemoryAgent.java`
- [X] T025 [P] [US2] Create segment episodic request model in `commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/episodic/model/SegmentEpisodicRequest.java`
- [X] T026 [P] [US2] Create segment episodic result model in `commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/episodic/model/SegmentEpisodicResult.java`
- [X] T027 [US2] Implement serial segment episodic executor service in `commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/episodic/service/SerialSegmentEpisodicService.java`
- [X] T028 [US2] Delegate AddEpisodicMemory flow through EpisodicMemoryAgent in `commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/AddEpisodicMemory.java`
- [X] T029 [US2] Delegate temporal-memory handling to agent runtime configuration in `commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/AddEpisodicMemory.java`
- [X] T030 [P] [US2] Capture per-segment handoff summary and single-call agent telemetry in `commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/episodic/service/SerialSegmentEpisodicService.java`
- [X] T031 [US2] Add configurable segment retry behavior in `commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/episodic/service/SerialSegmentEpisodicService.java`
- [X] T032 [US2] Invoke serial episodic executor from rewrite pipeline in `commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/RewriteHistory.java`
- [X] T033 [US2] Ensure episodic failures surface through parser descriptors in `commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/AddEpisodicMemory.java`

**Checkpoint**: US2 delivers serial segment memory updates with retry + telemetry.

---

## Phase 5: User Story 3 - Enable Application-Provided Episodic Agent Implementations (Priority: P2)

**Goal**: Make memory behavior pluggable and application-owned in `multi_agent_ide`.

**Independent Test**: Provide alternate app beans and verify onboarding delegates through injected implementations only.

### Implementation for User Story 3

- [X] T034 [US3] Add multi-agent IDE episodic implementation in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/episodic/MultiAgentIdeEpisodicMemoryAgent.java`
- [X] T035 [P] [US3] Add Spring default bean wiring for `MultiAgentIdeEpisodicMemoryAgent` in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/config/MultiAgentIdeConfig.java`
- [X] T036 [US3] Add production dependency wiring for `MultiAgentIdeEpisodicMemoryAgent` collaborators in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/config/MultiAgentIdeConfig.java`
- [X] T037 [US3] Add fail-fast validation when no EpisodicMemoryAgent bean is available in `commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/AddEpisodicMemory.java`
- [ ] T038 [US3] Connect onboarding endpoint execution to parser-chain orchestration service in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/controller/OrchestrationController.java`
- [X] T039 [P] [US3] Add `@MockitoBean`-based episodic agent swapping support for tests in `multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/integration/onboarding/support/OnboardingIntegrationTestConfig.java`
- [ ] T040 [US3] Update implementation contract notes for app-provided agent wiring in `specs/001-repo-onboarding-pipeline/contracts/internal.md`

**Checkpoint**: US3 delivers swappable, app-provided episodic agent implementations.

---

## Phase 6: User Story 4 - Validate Core Logic with Unit Tests (Priority: P2)

**Goal**: Add fast unit coverage for deterministic segmentation, fallback, sequencing, retries, and parser-chain reuse.

**Independent Test**: Run module unit suites and confirm deterministic, isolated behavior for core onboarding logic.

### Tests for User Story 4

- [X] T041 [P] [US4] Add deterministic segmentation repeatability tests in `commit-diff-context/src/test/java/com/hayden/commitdiffcontext/episodic/ContiguousSegmentationServiceTest.java`
- [X] T042 [P] [US4] Add segment min/max constraint tests in `commit-diff-context/src/test/java/com/hayden/commitdiffcontext/episodic/ContiguousSegmentationServiceTest.java`
- [X] T043 [P] [US4] Add embedding fallback continuation tests in `commit-diff-context/src/test/java/com/hayden/commitdiffcontext/episodic/EmbeddingFallbackServiceTest.java`
- [X] T044 [US4] Add SetEmbedding reuse-path tests in `commit-diff-context/src/test/java/com/hayden/commitdiffcontext/episodic/SetEmbeddingReusePathTest.java`
- [X] T045 [US4] Add RewriteHistory evolution contract tests in `commit-diff-context/src/test/java/com/hayden/commitdiffcontext/episodic/RewriteHistoryEvolutionTest.java`
- [X] T046 [US4] Add AddEpisodicMemory delegation-order tests in `commit-diff-context/src/test/java/com/hayden/commitdiffcontext/episodic/AddEpisodicMemoryAgentDelegationTest.java`
- [X] T047 [P] [US4] Add segment retry policy tests in `commit-diff-context/src/test/java/com/hayden/commitdiffcontext/episodic/SegmentRetryPolicyTest.java`
- [X] T048 [P] [US4] Add episodic agent contract behavior tests in `multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/agent/episodic/EpisodicMemoryAgentContractTest.java`
- [X] T049 [US4] Update rewrite wiring assertions to verify parser-chain reuse in `commit-diff-context/src/test/java/com/hayden/commitdiffcontext/episodic/RewriteOperationWiringTest.java`

**Checkpoint**: US4 provides focused, deterministic unit validation for core logic.

---

## Phase 7: User Story 5 - Validate Full Process in multi_agent_ide Integration Context (Priority: P2)

**Goal**: Execute full onboarding flow in `multi_agent_ide` with real repository fixtures and constrained mocking.

**Independent Test**: Run integration profile tests in `multi_agent_ide` that exercise end-to-end onboarding and agent-swapping behavior.

### Tests for User Story 5

- [X] T050 [US5] Add real repository fixture factory with submodule-aware setup in `multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/integration/onboarding/support/RepositoryFixtureFactory.java`
- [X] T051 [P] [US5] Add Spring integration test config using `@MockitoBean` to mock only allowed dependencies in `multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/integration/onboarding/support/OnboardingIntegrationTestConfig.java`
- [X] T052 [US5] Add happy-path full-process integration test in `multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/integration/onboarding/RepoOnboardingHappyPathIT.java`
- [X] T053 [US5] Add transient retry-path integration test in `multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/integration/onboarding/RepoOnboardingRetryIT.java`
- [X] T054 [US5] Add hard-stop memory failure integration test in `multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/integration/onboarding/RepoOnboardingMemoryFailureIT.java`
- [X] T055 [US5] Add cleanup behavior integration test in `multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/integration/onboarding/RepoOnboardingCleanupIT.java`
- [X] T056 [US5] Add profile-driven agent swap integration test in `multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/integration/onboarding/RepoOnboardingAgentSwapIT.java`
- [X] T057 [US5] Add parser-chain execution assertions helper for integration tests in `multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/integration/onboarding/support/OnboardingTraceAssertions.java`
- [ ] T058 [US5] Configure integration profile discovery for onboarding tests in `multi_agent_ide_java_parent/multi_agent_ide/build.gradle.kts`

**Checkpoint**: US5 validates end-to-end behavior in app context with real repository fixtures.

---

## Phase 8: Polish & Cross-Cutting Concerns

**Purpose**: Final alignment, docs, and execution validation across all stories.

- [ ] T059 [P] Refresh quickstart validation commands and expected outcomes in `specs/001-repo-onboarding-pipeline/quickstart.md`
- [ ] T060 [P] Record test_graph deferral and future tag mapping plan in `specs/001-repo-onboarding-pipeline/integration-tests.md`
- [ ] T061 Run onboarding-focused unit test suite and record outcomes in `commit-diff-context/src/test/java/com/hayden/commitdiffcontext/episodic/`
- [ ] T062 Run onboarding integration profile tests and record outcomes in `multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/integration/onboarding/`
- [ ] T063 Validate implemented endpoint payloads against API contract in `specs/001-repo-onboarding-pipeline/contracts/onboarding-api.openapi.yaml`
- [ ] T064 Capture final implementation notes, deviations, and follow-ups in `specs/001-repo-onboarding-pipeline/plan.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: Starts immediately.
- **Phase 2 (Foundational)**: Depends on Phase 1 and blocks all user stories.
- **Phase 3 (US1)**: Starts after Phase 2.
- **Phase 4 (US2)**: Depends on US1 segment outputs and starts after Phase 3 baseline.
- **Phase 5 (US3)**: Depends on US2 contract availability and can overlap late US2 stabilization.
- **Phase 6 (US4)**: Depends on US1-US3 implementation surfaces.
- **Phase 7 (US5)**: Depends on US3 wiring and US4 baseline test assets.
- **Phase 8 (Polish)**: Runs after all stories required for release are complete.

### User Story Dependencies

- **US1 (P1)**: Foundation story for ingestion + segmentation.
- **US2 (P1)**: Depends on US1 segment plan outputs.
- **US3 (P2)**: Depends on US2 memory delegation contract.
- **US4 (P2)**: Validates US1-US3 core logic.
- **US5 (P2)**: Validates full process in `multi_agent_ide` and depends on US3 + US4 readiness.

### Story Completion Order

1. US1
2. US2
3. US3
4. US4
5. US5

---

## Parallel Opportunities

- **Setup**: T003, T004, T005 can run together.
- **Foundational**: T007, T008, T009 can run together after T006; T011 and T010 can run in parallel.
- **US1**: T019 can run in parallel with late US1 tasks after T014-T017 baseline.
- **US2**: T025 and T026 can run in parallel after T024; T030 and T031 can run in parallel after T027.
- **US3**: T035 and T039 can run in parallel after T034.
- **US4**: T041, T042, T043, T047, T048 can run in parallel after test scaffolding exists.
- **US5**: T051 can run in parallel with T050 finishing; T052-T056 can be parallelized once T050/T051/T058 are in place.
- **Polish**: T059 and T060 can run in parallel.

---

## Parallel Example: User Story 1

```bash
Task: "T015 [US1] Populate CommitDiff embedding metadata in commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/SetEmbedding.java"
Task: "T019 [US1] Persist run metadata in commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/episodic/service/OnboardingOrchestrationService.java"
```

## Parallel Example: User Story 2

```bash
Task: "T025 [US2] Create segment request model in commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/episodic/model/SegmentEpisodicRequest.java"
Task: "T026 [US2] Create segment result model in commit-diff-context/src/main/java/com/hayden/commitdiffcontext/git/parser/support/episodic/model/SegmentEpisodicResult.java"
```

## Parallel Example: User Story 3

```bash
Task: "T035 [US3] Add default bean wiring in multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/config/MultiAgentIdeConfig.java"
Task: "T039 [US3] Add MockitoBean swap support in multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/integration/onboarding/support/OnboardingIntegrationTestConfig.java"
```

## Parallel Example: User Story 4

```bash
Task: "T041 [US4] Add deterministic segmentation tests in commit-diff-context/src/test/java/com/hayden/commitdiffcontext/episodic/ContiguousSegmentationServiceTest.java"
Task: "T043 [US4] Add fallback tests in commit-diff-context/src/test/java/com/hayden/commitdiffcontext/episodic/EmbeddingFallbackServiceTest.java"
Task: "T048 [US4] Add contract tests in multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/agent/episodic/EpisodicMemoryAgentContractTest.java"
```

## Parallel Example: User Story 5

```bash
Task: "T052 [US5] Add happy-path IT in multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/integration/onboarding/RepoOnboardingHappyPathIT.java"
Task: "T055 [US5] Add cleanup IT in multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/integration/onboarding/RepoOnboardingCleanupIT.java"
Task: "T056 [US5] Add agent-swap IT in multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/integration/onboarding/RepoOnboardingAgentSwapIT.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1 and Phase 2.
2. Complete Phase 3 (US1).
3. Validate deterministic onboarding behavior and segment outputs.
4. Pause for review before enabling episodic memory flow.

### Incremental Delivery

1. Deliver US1 deterministic onboarding.
2. Add US2 serial episodic flow.
3. Add US3 app-level pluggable agent wiring.
4. Add US4 unit validation suite.
5. Add US5 full integration suite in `multi_agent_ide`.
6. Finish with Phase 8 cross-cutting validation/documentation.

### Notes

- No matching onboarding tags were found in existing `test_graph/src/test/resources/features/` feature files; `test_graph` work remains deferred for this feature.
- Preserve existing parser-chain architecture (`SetEmbedding`, `GitParser`, evolved `RewriteHistory`, evolved `AddEpisodicMemory`) throughout implementation.
