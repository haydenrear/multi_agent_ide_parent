# Implementation Plan: Runtime ACP Model Selection

**Branch**: `001-multiple-models` | **Date**: 2026-03-06 | **Spec**: `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-multiple-models/spec.md`

**Input**: Feature specification from `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-multiple-models/spec.md`

## Summary

Enable each LLM call to choose its ACP provider and target model at runtime by replacing the current flat ACP configuration and split model/session handoff with a structured, versioned ACP routing payload plus a catalog of named ACP provider definitions. Preserve existing session/artifact identity semantics, keep the async-safe transport path, and isolate ACP session reuse by resolved provider/model selection.

## Technical Context

**Language/Version**: Java 21 and Kotlin on Spring Boot 3.x  
**Primary Dependencies**: Spring Boot configuration properties, Embabel agent runtime, Spring AI/LangChain4j integration, Jackson, Lombok  
**Storage**: Application configuration files for provider catalog; in-memory ACP session manager/cache for active sessions; no new persistent storage required  
**Testing**: Gradle test tasks with JUnit 5 for Java and Kotlin ACP integration/unit tests  
**Target Platform**: JVM server application running the multi-agent IDE backend  
**Project Type**: Multi-module backend application (Spring Boot app plus Java/Kotlin library modules)  
**Performance Goals**: Runtime provider resolution should add only constant-time parsing/lookup overhead to each call and must not require application restart for provider changes  
**Constraints**: Preserve session/artifact routing across async boundaries; maintain backward compatibility for legacy model/session callers; avoid thread-local routing state; prevent cross-provider session leakage; never log secrets from provider definitions  
**Scale/Scope**: Focused changes in `multi_agent_ide`, `acp-cdc-ai`, and `multi_agent_ide_lib`, plus targeted tests and configuration updates across existing ACP profiles

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### Pre-Phase 0 Gate Review

- **Principles I, IV, V**: PASS — the feature preserves the event-driven/session-driven architecture and moves additional routing context through explicit payload artifacts rather than implicit state.
- **Principles II, III**: PASS — no workflow-phase or orchestrator/collector behavior changes are introduced.
- **Principle VI**: PASS — no worktree or branching model changes are required.
- **Principle VII**: PASS — the design extends existing ACP/tool integration boundaries instead of introducing ad hoc external access.
- **Principle VIII**: PASS — no human review gate behavior changes are required.

### Post-Phase 1 Design Review

- **Architecture alignment**: PASS — design artifacts keep runtime routing inside the existing LLM/ACP integration boundary and preserve explicit artifact/session identity propagation.
- **Artifact-driven context**: PASS — the structured ACP call payload becomes the explicit carrier for runtime routing across async execution.
- **Testing and reviewability**: PASS — plan targets focused regression tests in the existing ACP/unit test suites without expanding feature scope into unrelated subsystems.

## Project Structure

### Documentation (this feature)

```text
specs/001-multiple-models/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── acp-runtime-selection.openapi.yaml
└── tasks.md
```

### Source Code (repository root)

```text
/Users/hayde/IdeaProjects/multi_agent_ide_parent/
├── specs/
│   └── 001-multiple-models/
└── multi_agent_ide_java_parent/
    ├── acp-cdc-ai/
    │   ├── build.gradle.kts
    │   └── src/
    │       ├── main/java/com/hayden/acp_cdc_ai/acp/config/
    │       ├── main/kotlin/com/hayden/acp_cdc_ai/acp/
    │       └── test/
    ├── multi_agent_ide/
    │   ├── build.gradle.kts
    │   └── src/
    │       ├── main/java/com/hayden/multiagentide/config/
    │       ├── main/java/com/hayden/multiagentide/service/
    │       ├── main/java/com/hayden/multiagentide/filter/service/
    │       ├── main/resources/
    │       └── test/
    ├── multi_agent_ide_lib/
    │   ├── build.gradle.kts
    │   └── src/main/java/com/hayden/multiagentidelib/
    └── utilitymodule/
        ├── build.gradle.kts
        └── src/
```

**Structure Decision**: Implement the feature entirely inside the existing Java/Kotlin backend modules. `multi_agent_ide` owns prompt/LLM option construction, `acp-cdc-ai` owns provider catalog binding and ACP session resolution, and `multi_agent_ide_lib` remains the shared contract surface for prompt/session context inputs.

## Complexity Tracking

No constitution violations or exceptional complexity exemptions are required for this design.
