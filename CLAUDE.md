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
- 001-multi-agent-test-supervisor: Added Java 21 (app), Kotlin (existing modules), Python 3.x (skill scripts), Markdown/YAML (skill docs + contracts) + Spring Boot 3.x, LangChain4j-Agentic, Embabel framework, Jackson/Lombok, existing ACP events and TUI reducers
- 001-multi-agent-test-supervisor: Added Java 21 (primary app), Kotlin (existing modules), Markdown/YAML (skill package and contracts) + Spring Boot 3.x, LangChain4j-Agentic, Embabel agent framework, Jackson, Lombok, existing ACP event model (`Events.*`)
- 001-repo-onboarding-pipeline: Added Java 21 (with existing Kotlin interoperability in the Java parent modules) + Spring Boot 3.x, LangChain4j-Agentic, Embabel agent framework, Lombok, Jackson, existing parser operations (`SetEmbedding`, `AddEpisodicMemory`, `RewriteHistory`, `GitParser`), existing embedding client (`ModelServerEmbeddingClient`)


<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->
