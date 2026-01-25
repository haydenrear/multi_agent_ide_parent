# Quickstart: Execution Artifacts, Templates, and Semantics

**Feature**: `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/008-artifacts/spec.md`  
**Date**: 2026-01-24

## Prerequisites

- Java 21
- Gradle (use `./gradlew`)
- A configured datastore for artifacts/templates (production target: PostgreSQL)
- Docker Compose available for test Postgres

## Run (Local)

1. Start the application:

   ```bash
   ./gradlew :multi_agent_ide:bootRun
   ```

2. On startup, verify built-in prompt templates are loaded and deduplicated:

   - Templates from `multi_agent_ide/src/main/resources/prompts/workflow/` are registered as PromptTemplate versions
   - Re-running the app does not create duplicate template versions for unchanged template text

3. Trigger a workflow execution (UI or existing API entry point) and verify:

   - An Execution root artifact is created
   - GraphEvents emitted during the run are captured as EventArtifact nodes
   - Rendered prompts are reconstructable from:
     - referenced PromptTemplate version (shared)
     - PromptArgs (dynamic inputs)
     - PromptContribution artifacts (dynamic prompt contributors)

4. (Optional) Add a semantic representation after the run and verify:

   - The semantic representation references a target ArtifactKey
   - No source artifact content changes when semantics are written

## Tests

Run the unit/integration tests for the application modules:

```bash
./gradlew :multi_agent_ide:test
./gradlew :multi_agent_ide_lib:test
```

If tests require a Postgres instance, ensure the docker-compose service is up:

```bash
docker compose -f jpa-persistence/src/test/docker/docker-compose.yml up -d
```
