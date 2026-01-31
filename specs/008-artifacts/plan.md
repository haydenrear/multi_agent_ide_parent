# Implementation Plan: Execution Artifacts, Templates, and Semantics

**Branch**: `008-artifacts` | **Date**: 2026-01-24 | **Spec**: `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/008-artifacts/spec.md`

**Input**: Feature specification from `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/008-artifacts/spec.md`

## Summary

Define a canonical artifact tree per workflow execution and persist it in an event-driven manner so we can:

- Fully reconstruct runs (prompts, tool I/O, config, outcomes, GraphEvents)
- Track prompt template evolution via stable static IDs + content hashes + stable template ArtifactKeys
- Attach semantic representations post-hoc without mutating source artifacts

## Technical Context

**Language/Version**: Java 21 (and some Kotlin in the app)  
**Primary Dependencies**: Spring Boot 3.x, Embabel agent framework, LangChain4j, Lombok, Jackson (JSON), Spring events/listener infrastructure (custom)  
**Storage**: PostgreSQL via Spring Data (production target); Postgres via Docker Compose for tests  
**Testing**: JUnit 5, AssertJ, Mockito, Spring Boot Docker Compose integration  
**Target Platform**: JVM service (local dev + Linux server deployments)  
**Project Type**: Multi-module Gradle monorepo (multiple git submodules)  
**Performance Goals**: Persist >= 10k artifact nodes/event artifacts per execution without noticeable workflow slowdown (<5% overhead on a representative run)  
**Constraints**: Event-driven emission (no blocking the agent loop); deterministic reconstructability; template version dedup by hash; avoid hidden context propagation (artifacts only); docker-compose-backed test DBs; contracts are internal JSON payload definitions (event bus), not external APIs  
**Scale/Scope**: Dozens of artifact types; thousands of artifacts per run; long-term corpus across many runs and template versions

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- Pass: Preserves the computation-graph + event-driven lifecycle model (Principles I, IV)
- Pass: Strengthens artifact-driven context propagation (Principle V)
- Pass: Does not alter the mandatory three-phase workflow pattern (Principle II)
- Pass: Does not bypass worktree isolation/merge gates (Principle VI)
- Pass: Adds instrumentation/events without introducing hidden side-channel state

**Post-Design Re-check (2026-01-24)**: Pass

## Project Structure

### Documentation (this feature)

```text
specs/008-artifacts/
├── spec.md
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── artifact-events.contract.json
└── checklists/
    └── requirements.md
```

### Source Code (repository root)

```text
multi_agent_ide_parent/
├── multi_agent_ide_java_parent/                       # Java modules parent
│   ├── acp-cdc-ai/                                    # Core artifact infrastructure
│   │   └── src/main/java/com/hayden/acp_cdc_ai/acp/events/
│   │       ├── ArtifactKey.java                       # Hierarchical ULID-based identifier
│   │       ├── Artifact.java                          # Base artifact sealed interface
│   │       ├── ArtifactHashing.java                   # Content hashing + canonical JSON
│   │       └── EventBus.java                          # Event bus for artifact emission
│   │
│   ├── multi_agent_ide/                               # Spring Boot app
│   │   ├── src/main/java/com/hayden/multiagentide/
│   │   │   ├── agent/
│   │   │   │   ├── AgentInterfaces.java
│   │   │   │   └── decorator/
│   │   │   │       └── ArtifactEnrichmentDecorator.java
│   │   │   ├── artifacts/                             # Artifact persistence layer
│   │   │   │   ├── ArtifactService.java               # Persistence + serialization
│   │   │   │   ├── ArtifactTreeBuilder.java           # In-memory trie + persistence
│   │   │   │   ├── ArtifactNode.java                  # Trie node structure
│   │   │   │   ├── ArtifactEventListener.java         # Event subscriber
│   │   │   │   ├── ArtifactEmissionService.java       # Artifact emission
│   │   │   │   ├── EventArtifactMapper.java           # GraphEvent mapping
│   │   │   │   ├── ExecutionScopeService.java         # Execution lifecycle
│   │   │   │   ├── entity/
│   │   │   │   │   └── ArtifactEntity.java            # JPA entity
│   │   │   │   ├── repository/
│   │   │   │   │   └── ArtifactRepository.java        # JPA repository
│   │   │   │   └── semantic/
│   │   │   │       ├── SemanticRepresentationEntity.java
│   │   │   │       ├── SemanticRepresentationRepository.java
│   │   │   │       └── SemanticRepresentationService.java
│   │   │   └── config/
│   │   │       └── ArtifactConfig.java
│   │   └── src/main/resources/prompts/workflow/...
│   │
│   ├── multi_agent_ide_lib/                           # Shared workflow/prompt models
│   │   └── src/main/java/com/hayden/multiagentidelib/
│   │       ├── agent/
│   │       │   ├── AgentModels.java                   # Agent request/result types
│   │       │   └── AgentContext.java                  # Root artifact interface
│   │       ├── artifact/
│   │       │   ├── PromptTemplateVersion.java         # Versioned templates
│   │       │   └── SemanticRepresentation.java        # Semantic representations
│   │       ├── service/
│   │       │   └── RequestEnrichment.java             # ArtifactKey enrichment
│   │       └── prompt/
│   │           ├── ContextIdService.java              # Child key generation
│   │           └── PromptContributor.java
│   │
│   └── utilitymodule/                                 # General utilities
│       └── src/main/java/com/hayden/utilitymodule/
│           └── result/                                # Result/error handling
│
└── specs/008-artifacts/...
```

**Structure Decision**: Core artifact infrastructure (`ArtifactKey`, `Artifact`, `ArtifactHashing`, `EventBus`) lives in `acp-cdc-ai`. Shared artifact models (`PromptTemplateVersion`, `SemanticRepresentation`, `AgentModels`) live in `multi_agent_ide_lib`. Persistence services and event listeners are implemented in `multi_agent_ide`.
