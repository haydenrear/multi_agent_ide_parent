# multi_agent_ide_parent Development Guidelines

Auto-generated from all feature plans. Last updated: 2025-12-26

## Active Technologies
- Java 21 (and some Kotlin in the app) + Spring Boot 3.x, Embabel agent framework, LangChain4j, Lombok, Jackson (JSON), Spring events/listener infrastructure (custom) (008-artifacts)
- PostgreSQL via Spring Data (production target); H2 for tests (where applicable) (008-artifacts)
- PostgreSQL via Spring Data (production target); Postgres via Docker Compose for tests (008-artifacts)
- Java 21 + Spring Boot 3.x, Embabel agent framework, LangChain4j, Lombok, Jackson (001-cli-mode-events)
- PostgreSQL (production), H2/Postgres for tests via Spring Data (001-cli-mode-events)
- Java 21 + Spring Boot 3.x, LangChain4j-Agentic, Embabel Agent Framework (@Agent, @Action, PromptRunner.Creating, withAnnotationFilter), Lombok, Jackson (001-unified-interrupt-handler)
- N/A (in-memory BlackboardHistory, existing GraphRepository) (001-unified-interrupt-handler)
- Java 21 and Kotlin on Spring Boot 3.x + Spring Boot configuration properties, Embabel agent runtime, Spring AI/LangChain4j integration, Jackson, Lombok (001-multiple-models)
- Application configuration files for provider catalog; in-memory ACP session manager/cache for active sessions; no new persistent storage required (001-multiple-models)

- Java 21 + Spring Boot 3.x, LangChain4j-Agentic, Lombok (001-collector-orchestrator-routing)

## Project Structure

```text
src/
tests/
```

## Commands

From the repo root, compile the main Java modules with fully qualified Gradle paths:

```shell
./multi_agent_ide_java_parent/gradlew :multi_agent_ide_java_parent:acp-cdc-ai:compileJava :multi_agent_ide_java_parent:multi_agent_ide_lib:compileJava :multi_agent_ide_java_parent:multi_agent_ide:compileJava
```

From `multi_agent_ide_java_parent`, run the same compile targets with fully qualified Gradle paths:

```shell
./gradlew :multi_agent_ide_java_parent:acp-cdc-ai:compileJava :multi_agent_ide_java_parent:multi_agent_ide_lib:compileJava :multi_agent_ide_java_parent:multi_agent_ide:compileJava
```

## Code Style

Java 21: Follow standard conventions

## Recent Changes
- 001-multiple-models: Added Java 21 and Kotlin on Spring Boot 3.x + Spring Boot configuration properties, Embabel agent runtime, Spring AI/LangChain4j integration, Jackson, Lombok
- 001-unified-interrupt-handler: Added Java 21 + Spring Boot 3.x, LangChain4j-Agentic, Embabel Agent Framework (@Agent, @Action, PromptRunner.Creating, withAnnotationFilter), Lombok, Jackson
- 001-unified-interrupt-handler: Added Java 21 + Spring Boot 3.x, LangChain4j-Agentic, Embabel Agent Framework (@Agent, @Action, PromptRunner.Creating, withAnnotationFilter), Lombok, Jackson


<!-- MANUAL ADDITIONS START -->
  - `PromptContributor.name()` must be deterministic and static for the template variant.
  - `PromptContributor.template()` must be static text for that variant and map 1:1 with the name.
  - Do not use sequence/counter-based names for contributors whose template text is versioned.
<!-- MANUAL ADDITIONS END -->


## Testing

### Timing and Timeouts

| Test suite                                                                                                    | Approximate duration | Bash timeout |
|---------------------------------------------------------------------------------------------------------------|----------------------|--------------|
| Unit tests (`./gradlew :multi_agent_ide_java_parent:multi_agent_ide:test`)                                    | ~3 minutes           | 180000ms     |
| Full pipeline (`multi_agent_ide_java_parent/tests.sh`)                                                        | ~25-30 minutes       | 900000ms     |
| ACP integration test (`./gradlew :multi_agent_ide_java_parent:multi_agent_ide:test -Pprofile=acp-integration`) | ~60 minutes          | 3600000ms    |
| ACP chat model test (`./gradlew :multi_agent_ide_java_parent:acp-cdc-ai:test -Pprofile=acp-integration`)      | ~3 minutes           | 3600000ms    |

### Running Tests

Run the normal project pipeline from the repo root:

```shell
multi_agent_ide_java_parent/tests.sh
```

This runs the standard Gradle test flow across the Java/Kotlin submodules.

For LLM end-to-end coverage, run the ACP integration test separately:

```shell
./gradlew :multi_agent_ide_java_parent:multi_agent_ide:test -Pprofile=acp-integration
```

Run it from `multi_agent_ide_java_parent`. It takes about 60 minutes and exercises a full ACP-backed workflow with automatic permission resolution.

Common failure modes:

1. out of Ollama/OpenRouter/model credits
2. transient LLM/provider failures

Poll the log periodically during long runs so you can catch stalls or provider failures early.

After ACP integration completes, verify that this file exists:

```shell
/tmp/multi_agent_ide_parent/multi_agent_ide_java_parent/README.md
```

That validates the submodule-targeted file merge path.
