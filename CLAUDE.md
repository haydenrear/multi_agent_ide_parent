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
- 009-worktree-sandbox: Added Java 21 (some Kotlin in ACP module) + Spring Boot 3.x, LangChain4j-Agentic, Embabel Agent Framework, Lombok, Jackson
- 009-worktree-sandbox: Added Java 21 + Spring Boot 3.x, LangChain4j-Agentic, Embabel Agent Framework
- 007-context-manager-agent: Added Java 21 + `embabel-agent` (for Agent API), `multi_agent_ide_lib` (for shared models), `utilitymodule` (for EventBus)


<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->
