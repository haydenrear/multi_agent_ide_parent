# Implementation Plan: [FEATURE]

**Branch**: `[###-feature-name]` | **Date**: [DATE] | **Spec**: [link]

**Input**: Feature specification from `/specs/[###-feature-name]/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

[Extract from feature spec: primary requirement + technical approach from research]

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: [e.g., Python 3.11, Swift 5.9, Rust 1.75 or NEEDS CLARIFICATION]  
**Primary Dependencies**: [e.g., FastAPI, UIKit, LLVM or NEEDS CLARIFICATION]  
**Storage**: [if applicable, e.g., PostgreSQL, CoreData, files or N/A]  
**Testing**: [e.g., pytest, XCTest, cargo test or NEEDS CLARIFICATION]  
**Target Platform**: [e.g., Linux server, iOS 15+, WASM or NEEDS CLARIFICATION]
**Project Type**: [single/web/mobile - determines source structure]  
**Performance Goals**: [domain-specific, e.g., 1000 req/s, 10k lines/sec, 60 fps or NEEDS CLARIFICATION]  
**Constraints**: [domain-specific, e.g., <200ms p95, <100MB memory, offline-capable or NEEDS CLARIFICATION]  
**Scale/Scope**: [domain-specific, e.g., 10k users, 1M LOC, 50 screens or NEEDS CLARIFICATION]

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

[Gates determined based on constitution file]

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md                                     # This file (/speckit.plan command output)
├── research.md                                 # Phase 0 output (/speckit.plan command)
├── data-model.md                               # Phase 1 output (/speckit.plan command)
├── quickstart.md                               # Phase 1 output (/speckit.plan command)
├── contracts/                                  # Phase 1 output (/speckit.plan command)
└── tasks.md                                    # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)

test_graph/src/test/resources/features/{name}   # the feature files for all of the scenarios and user stories. See the test_graph/instructions.md and test_graph/instructions-features.md files for instructions on how to write the Gherkin feature files and how we integrate with Gherkin.
```

### Source Code (repository root)
<!--
  ACTION REQUIRED: Replace the placeholder tree below with the concrete layout
  for this feature. Delete unused options and expand the chosen structure with
  real paths (e.g., apps/admin, packages/something). The delivered plan must
  not include Option labels.
-->

```text
# Typical setup - you will be initialized in the [app_name_parent] - it will be the root - and the below parent project will be created.

[app_name_parent]/
├── .git/                                      # parent repo
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradlew
├── gradlew.bat
├── gradle/

├── specs/                                     # above specs directory goes in root of parent 

├── buildSrc/                                  # GIT SUBMODULE
│   ├── .git/
│   ├── build.gradle.kts
│   └── src/main/kotlin/...

├── [app_name]/                                # GIT SUBMODULE: Spring Boot app
│   ├── .git/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── java/
│       │   │   └── com/hayden/[app_name]/
│       │   │       ├── api/
│       │   │       ├── models/
│       │   │       ├── services/
│       │   │       └── App.java
│       │   ├── resources/
│       │   │   ├── application.yml
│       │   │   └── static/
│       │   │       ├── README.md              # indicates optional frontend build output
│       │   │       └── (frontend build output here, e.g. dist/, assets/, index.html)
│       │   └── docker/
│       │       └── Dockerfile
│       └── test/
│           ├── java/com/hayden/[app_name]/...
│           └── resources/...

├── [app_name_lib]/                            # GIT SUBMODULE: shared Java lib (one of many)
│   ├── .git/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/java/com/hayden/[app_name_lib]/...
│       └── test/java/com/hayden/[app_name_lib]/...

├── [java_lib_module_2]/                       # GIT SUBMODULE: Java lib (sibling module)
│   ├── .git/
│   ├── build.gradle.kts
│   └── src/main/java/com/hayden/[java_lib_module_2]/...

├── [java_lib_module_3]/                       # GIT SUBMODULE: Java lib (sibling module)
│   ├── .git/
│   ├── build.gradle.kts
│   └── src/main/java/com/hayden/[java_lib_module_3]/...

├── ...                                        # (0..N) additional Java lib submodules

├── runner_code/                               # GIT SUBMODULE: builds/pushes base Docker images
│   ├── .git/
│   ├── build.gradle.kts                       # NOTE: Code for building/pushing base docker images, adding this to the computation graph - other modules depend on specific tasks.
│   └── src/
│       └── main/docker/...                    # base docker images and registry 

├── test_graph/                                # GIT SUBMODULE: feature/integration tests, test graph framework
│   ├── .git/
│   ├── build.gradle.kts
│   ├── instructions.md                        # NOTE: Instructions on how to add to test graph framework
│   ├── instructions-features.md               # NOTE: Instructions on best practices for writing feature files for test graph framework
│   └── src/
│       ├── main/java/com/hayden/test_graph/...
│       └── test/
│           ├── java/com/hayden/test_graph/...
│           └── resources/features/[feature]/...

├── [python app parent]/                                    # GIT SUBMODULE: Python parent (Gradle-managed too)
│   ├── .git/
│   ├── build.gradle.kts                       # NOTE: present so Gradle can orchestrate
│   ├── pyproject.toml                         # NOTE: build uv multi-module workspace 
│   │                                          # container builds / tasks across python packages
│   └── packages/
│       ├── shared_python_lib_a/               # GIT SUBMODULE: shared python lib (one of many) - example python_util - python shared utils
│       │   ├── .git/
│       │   ├── pyproject.toml
│       │   ├── .env
│       │   ├── resources/application.yml
│       │   ├── src/shared_python_lib_a/...
│       │   └── tests/...
│       │
│       ├── shared_python_lib_b/               # GIT SUBMODULE: shared python lib (one of many) - example python_di - config and DI framework
│       │   ├── .git/
│       │   ├── pyproject.toml
│       │   ├── .env
│       │   ├── resources/application.yml
│       │   ├── src/shared_python_lib_b/...
│       │   └── tests/...
│       │
│       ├── [python_app_name]/                 # GIT SUBMODULE: python app package
│       │   ├── .git/
│       │   ├── build.gradle.kts               # NOTE: present so Gradle can build Docker images
│       │   ├── pyproject.toml
│       │   ├── .env
│       │   ├── resources/application.yml
│       │   ├── resources/application-docker.yml
│       │   ├── src/[python_app_name]/...
│       │   ├── tests/...
│       │   └── docker/               # Docker build context for python app
│       │       └── Dockerfile
│       │
│       └── ...                                # (0..N) additional python lib/app submodules

├── deploy-helm/                               # GIT SUBMODULE: Helm chart + Python CLI
│   ├── .git/
│   ├── Chart.yaml
│   ├── values.yaml
│   ├── templates/
│   ├── pyproject.toml
│   └── src/
│       └── helm_deploy/
│           └── __main__.py                 # script for deploying creating kubernetes cluster in k3d and deploying helm with options
```

**Structure Decision**: [Document the selected structure and reference the real directories captured above]

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient] |
