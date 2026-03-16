# multi_agent_ide_parent Development Guidelines

Auto-generated from all feature plans. Last updated: 2025-12-20

## Active Technologies
- Java 21 + Spring Boot 3.x, LangChain4j agentic services, agentclientprotocol Kotlin SDK (JVM) (001-acp-chatmodel)
- Filesystem/Git artifacts (no new persistent storage) (001-acp-chatmodel)
- Java 21 (backend), TypeScript/JavaScript (frontend) + Spring Boot 3.x, CopilotKit, Reac (002-ag-ui)
- N/A (event-driven UI; backend persistence already exists) (002-ag-ui)
- Java 21 + Spring Boot 3.x, LangChain4j-Agentic, Lombok (001-interrupt)
- GraphRepository persistence (existing), artifact markdown files (001-interrupt)
- Java 21 (existing project) + Spring Boot 3.x, Embabel Agent Framework (@Agent), LangChain4j (004-agent-prompts-outputs)
- GraphRepository (existing), artifact markdown files (004-agent-prompts-outputs)
- Java 21 + `embabel-agent` (for Agent API), `multi_agent_ide_lib` (for shared models), `utilitymodule` (for EventBus) (007-context-manager-agent)
- In-memory `BlackboardHistory` (per session) (007-context-manager-agent)
- Java 21 + Spring Boot 3.x, LangChain4j-Agentic, Embabel Agent Framework (009-worktree-sandbox)
- GraphRepository (existing), WorktreeRepository (existing) (009-worktree-sandbox)
- Java 21 (some Kotlin in ACP module) + Spring Boot 3.x, LangChain4j-Agentic, Embabel Agent Framework, Lombok, Jackson (009-worktree-sandbox)
- GraphRepository + WorktreeRepository (existing) (009-worktree-sandbox)
- Java 21 (with existing Kotlin interoperability in the Java parent modules) + Spring Boot 3.x, LangChain4j-Agentic, Embabel agent framework, Lombok, Jackson, existing parser operations (`SetEmbedding`, `AddEpisodicMemory`, `RewriteHistory`, `GitParser`), existing embedding client (`ModelServerEmbeddingClient`) (001-repo-onboarding-pipeline)
- Existing commit-diff persistence repositories for commit artifacts, minimal onboarding run metadata store, Hindsight as external memory system of record (001-repo-onboarding-pipeline)
- Java 21 (primary app), Kotlin (existing modules), Markdown/YAML (skill package and contracts) + Spring Boot 3.x, LangChain4j-Agentic, Embabel agent framework, Jackson, Lombok, existing ACP event model (`Events.*`) (001-multi-agent-test-supervisor)
- PostgreSQL-backed persistence for execution artifacts and run metadata; existing event repository for event stream access (001-multi-agent-test-supervisor)
- Java 21 (app), Kotlin (existing modules), Python 3.x (skill scripts), Markdown/YAML (skill docs + contracts) + Spring Boot 3.x, LangChain4j-Agentic, Embabel framework, Jackson/Lombok, existing ACP events and TUI reducers (001-multi-agent-test-supervisor)
- Existing run/event persistence mechanisms; no new multi-tenant profile/version stores required in this ticke (001-multi-agent-test-supervisor)
- Java 21 + Spring Boot 3.x, LangChain4j-Agentic, Embabel Agent Framework (@Agent, @Action, PromptRunner.Creating, withPropertyFilter), Lombok, Jackson (001-unified-interrupt-handler)
- N/A (in-memory BlackboardHistory, existing GraphRepository) (001-unified-interrupt-handler)
- Java 21 + Spring Boot 3.x, LangChain4j-Agentic, Embabel Agent Framework (@Agent, @Action, PromptRunner.Creating, withAnnotationFilter), Lombok, Jackson (001-unified-interrupt-handler)
- Java 21 + Spring Boot 3.x, Jackson, LangChain4j-Agentic, Embabel agent framework (001-data-layer-policy-filter)
- Existing persistence layer (PostgreSQL in production, H2/Postgres test profiles) for policy definitions, activation state, and filter decision history (001-data-layer-policy-filter)

- Java 21 + Spring Boot 3.x, LangChain4j-Agentic, Mockito, AssertJ (001-multi-agent-ide-specs)

## Project Structure

```text
src/
tests/
```

## Commands

# Add commands for Java 21

## Code Style

Java 21: Follow standard conventions

## Recent Changes
- 001-data-layer-policy-filter: Added Java 21 + Spring Boot 3.x, Jackson, LangChain4j-Agentic, Embabel agent framework
- 001-unified-interrupt-handler: Added Java 21 + Spring Boot 3.x, LangChain4j-Agentic, Embabel Agent Framework (@Agent, @Action, PromptRunner.Creating, withAnnotationFilter), Lombok, Jackson
- 001-unified-interrupt-handler: Added Java 21 + Spring Boot 3.x, LangChain4j-Agentic, Embabel Agent Framework (@Agent, @Action, PromptRunner.Creating, withAnnotationFilter), Lombok, Jackson


<!-- MANUAL ADDITIONS START -->
## Agent Usage

Do not use parallel sub-agents. Do not spawn parallel sub-agents. They are too expensive to run.

## API Design Rule: No Path Variables for layerId

**NEVER** use `@PathVariable` to capture a `layerId` in Spring MVC controllers. Layer IDs may contain slashes (e.g. `workflow-agent/coordinateWorkflow`), which Tomcat rejects in URL paths even when percent-encoded.

**Always** use `@RequestBody` with a JSON payload containing `layerId` instead.

Bad:
```java
@GetMapping("/layers/{layerId}/registrations")
public ResponseEntity<?> byLayer(@PathVariable String layerId) { ... }
```

Good:
```java
@PostMapping("/registrations/by-layer")
public ResponseEntity<?> byLayer(@RequestBody LayerIdRequest request) { ... }
```

This rule applies to all controllers in this codebase. The same applies to any other identifier that may contain slashes or special characters.
## Schema Changes: Always Update the Liquibase Changelog

**NEVER** add, remove, or modify a JPA entity field that maps to a database column without also adding a new changeSet to `db.changelog-master.yaml`.

Rules:
- Every column addition → `addColumn` changeSet
- Every column removal → `dropColumn` changeSet
- Every type or constraint change → `modifyDataType` or `dropNotNullConstraint` / `addNotNullConstraint` changeSet
- Every new index → `createIndex` changeSet inside the relevant changeSet
- **Increment the changeSet id** sequentially (e.g. `016-...`, `017-...`)
- Changelog file: `multi_agent_ide_java_parent/multi_agent_ide/src/main/resources/db/changelog/db.changelog-master.yaml`

Do not rely on `spring.jpa.hibernate.ddl-auto=update` — the changelog is the source of truth for schema state.
<!-- MANUAL ADDITIONS END -->
