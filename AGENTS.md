# multi_agent_ide_parent Development Guidelines

Auto-generated from all feature plans. Last updated: 2025-12-26

## Active Technologies
- Java 21 (and some Kotlin in the app) + Spring Boot 3.x, Embabel agent framework, LangChain4j, Lombok, Jackson (JSON), Spring events/listener infrastructure (custom) (008-artifacts)
- PostgreSQL via Spring Data (production target); H2 for tests (where applicable) (008-artifacts)
- PostgreSQL via Spring Data (production target); Postgres via Docker Compose for tests (008-artifacts)
- Java 21 + Spring Boot 3.x, Embabel agent framework, LangChain4j, Lombok, Jackson (001-cli-mode-events)
- PostgreSQL (production), H2/Postgres for tests via Spring Data (001-cli-mode-events)

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
- 001-cli-mode-events: Added Java 21 + Spring Boot 3.x, Embabel agent framework, LangChain4j, Lombok, Jackson
- 008-artifacts: Added Java 21 (and some Kotlin in the app) + Spring Boot 3.x, Embabel agent framework, LangChain4j, Lombok, Jackson (JSON), Spring events/listener infrastructure (custom)
- 008-artifacts: Added Java 21 (and some Kotlin in the app) + Spring Boot 3.x, Embabel agent framework, LangChain4j, Lombok, Jackson (JSON), Spring events/listener infrastructure (custom)


<!-- MANUAL ADDITIONS START -->
<!-- MANUAL ADDITIONS END -->


## Profiles

Run tests with gradle profile "integration" i.e.

```shell
./gradlew :multi_agent_ide:test -Pprofile=integration
```

to run the integration tests. You should do this as the final step when implementing a ticket.

```shell
./gradlew :multi_agent_ide:test
```

In practice you'll want to run both of these because integration profile does not include non-integration tests.
