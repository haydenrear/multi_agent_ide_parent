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

- Java 21 + Spring Boot 3.x, LangChain4j-Agentic, Lombok (001-collector-orchestrator-routing)

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
- 001-unified-interrupt-handler: Added Java 21 + Spring Boot 3.x, LangChain4j-Agentic, Embabel Agent Framework (@Agent, @Action, PromptRunner.Creating, withAnnotationFilter), Lombok, Jackson
- 001-unified-interrupt-handler: Added Java 21 + Spring Boot 3.x, LangChain4j-Agentic, Embabel Agent Framework (@Agent, @Action, PromptRunner.Creating, withAnnotationFilter), Lombok, Jackson
- 001-unified-interrupt-handler: Added Java 21 + Spring Boot 3.x, LangChain4j-Agentic, Embabel Agent Framework (@Agent, @Action, PromptRunner.Creating, withAnnotationFilter), Lombok, Jackson


<!-- MANUAL ADDITIONS START -->
- Prompt contributor naming/versioning rule:
  - `PromptContributor.name()` must be deterministic and static for the template variant.
  - `PromptContributor.template()` must be static text for that variant and map 1:1 with the name.
  - Do not use sequence/counter-based names for contributors whose template text is versioned.
<!-- MANUAL ADDITIONS END -->


## Testing

### Timing and Timeouts

| Test suite | Approximate duration | Bash timeout |
|---|---|---|
| Unit tests (`./gradlew multi_agent_ide:test`) | ~3 minutes | 180000ms |
| Integration tests (`./gradlew multi_agent_ide:test -Pprofile=integration`) | ~10 minutes | 600000ms |
| Full pipeline (`tests.sh`) | ~15 minutes | 900000ms |

### Running Tests

Unit tests (default profile):
```shell
./gradlew multi_agent_ide:test
```

Integration tests (does NOT include unit tests):
```shell
./gradlew multi_agent_ide:test -Pprofile=integration
```

In practice you'll want to run both of these because the integration profile does not include non-integration tests.

Full test pipeline (unit + shell-integration + integration):
```shell
cd multi_agent_ide_java_parent/multi_agent_ide && ./tests.sh
```

This runs all test profiles sequentially: unit tests, shell-integration tests, and integration tests.
