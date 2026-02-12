# Implementation Plan: Repository Onboarding Pipeline

**Branch**: `001-repo-onboarding-pipeline` | **Date**: 2026-02-12 | **Spec**: `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-repo-onboarding-pipeline/spec.md`

**Input**: Feature specification from `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-repo-onboarding-pipeline/spec.md`

## Summary

Implement repository onboarding as an extension of the current parser operation chain in `commit-diff-context`, not as a parallel pipeline. The design reuses `SetEmbedding` and `GitParser` traversal semantics, evolves `RewriteHistory` for deterministic contiguous segmentation, and evolves `AddEpisodicMemory` to delegate memory behavior through a pluggable `EpisodicMemoryAgent`. Application-specific memory implementation and full-process Spring Boot integration tests live in `multi_agent_ide` with a real repository fixture and only embedding/Hindsight/agent dependencies mocked.

## Technical Context

**Language/Version**: Java 21 (with existing Kotlin interoperability in the Java parent modules)  
**Primary Dependencies**: Spring Boot 3.x, LangChain4j-Agentic, Embabel agent framework, Lombok, Jackson, existing parser operations (`SetEmbedding`, `AddEpisodicMemory`, `RewriteHistory`, `GitParser`), existing embedding client (`ModelServerEmbeddingClient`)  
**Storage**: Existing commit-diff persistence repositories for commit artifacts, minimal onboarding run metadata store, Hindsight as external memory system of record  
**Testing**: JUnit 5, AssertJ, Mockito, Spring Boot Test, Gradle test profile `integration` for integration suites  
**Target Platform**: JVM services on Linux/macOS development environments  
**Project Type**: Multi-module Gradle monorepo with git submodules  
**Performance Goals**: Onboard a medium repository (10k commits) within 20 minutes in default mode; segment planning remains deterministic and bounded by configured limits  
**Constraints**: Preserve existing parser operation contracts; serial segment episodic execution only; integration tests in `multi_agent_ide` must use a real repository fixture; only embedding provider, Hindsight provider, and episodic agent are mocked; `test_graph` work deferred  
**Scale/Scope**: One onboarding run processes full repository history and submodule imports, supports configurable `K` segmentation and per-segment episodic generation across dozens to thousands of commits per segment

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. Computation Graph with State Machine Architecture | PASS | Feature adds onboarding internals and does not alter node state machine semantics. |
| II. Three-Phase Workflow Pattern | PASS | Onboarding is supporting infrastructure for discovery/planning/implementation and does not replace phase sequencing. |
| III. Orchestrator, Work, and Collector Node Pattern | PASS | No direct bypass of orchestrator/work/collector flow is introduced. |
| IV. Event-Driven Node Lifecycle & Artifact Propagation | PASS | Run metadata and episodic orchestration remain explicit and traceable; no hidden lifecycle state added. |
| V. Artifact-Driven Context Propagation | PASS | Context remains explicit through run metadata and memory operations; no implicit parameter-only context channel added. |
| VI. Worktree Isolation & Feature Branching | PASS | No changes to ticket worktree merge model; onboarding pipeline is read/process oriented. |
| VII. Tool Integration & Agent Capabilities | PASS | Episodic behavior is exposed via explicit `EpisodicMemoryAgent` single-call contract, while Hindsight MCP tools stay runtime-configured inside the agent. |
| VIII. Human-in-the-Loop Review Gates | PASS | No changes to review gate behavior. |

**Gate Result**: PASS

**Post-Design Re-check (2026-02-12)**: PASS - Phase 1 artifacts preserve parser-chain reuse, explicit contracts, and existing workflow governance.

## Project Structure

### Documentation (this feature)

```text
specs/001-repo-onboarding-pipeline/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── integration-tests.md
├── contracts/
│   ├── onboarding-api.openapi.yaml
│   └── internal.md
└── checklists/
    └── requirements.md
```

### Source Code (repository root)

```text
/Users/hayde/IdeaProjects/multi_agent_ide_parent/
├── commit-diff-context/
│   └── src/main/java/com/hayden/commitdiffcontext/git/parser/
│       ├── GitParser.java                                            # existing traversal and aggregate error model (reused)
│       └── support/
│           ├── SetEmbedding.java                                     # existing embedding operation (extended/reused)
│           ├── AddEpisodicMemory.java                                # existing episodic operation (evolved to delegate agent)
│           ├── RewriteHistory.java                                   # existing rewrite operation (evolved for segmentation+serial episodes)
│           └── episodic/
│               ├── EpisodicMemoryAgent.java                          # NEW contract
│               ├── model/                                            # NEW segment/run request models
│               └── service/                                          # NEW/updated segmentation and orchestration services
│
├── commit-diff-context/
│   └── src/test/java/com/hayden/commitdiffcontext/...
│       ├── ...Segmentation...Test.java                               # NEW unit tests
│       ├── ...SetEmbeddingReusePathTest.java                         # NEW unit tests
│       ├── ...RewriteHistoryEvolutionTest.java                       # NEW unit tests
│       └── ...AddEpisodicMemoryAgentDelegationTest.java             # NEW unit tests
│
└── multi_agent_ide_java_parent/
    └── multi_agent_ide/
        ├── src/main/java/com/hayden/multiagentide/...
        │   └── .../hindsight/...                                     # NEW EpisodicMemoryAgent implementation wiring
        └── src/test/java/com/hayden/multiagentide/...
            ├── RepoOnboardingHappyPathIT.java                        # NEW integration test
            ├── RepoOnboardingRetryIT.java                            # NEW integration test
            ├── RepoOnboardingMemoryFailureIT.java                    # NEW integration test
            ├── RepoOnboardingCleanupIT.java                          # NEW integration test
            └── RepoOnboardingAgentSwapIT.java                        # NEW integration test
```

**Structure Decision**: Core onboarding logic remains centered in `commit-diff-context` by evolving existing parser operations. Application-specific memory integration and full Spring Boot integration experimentation are owned by `multi_agent_ide`.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| None | N/A | N/A |
