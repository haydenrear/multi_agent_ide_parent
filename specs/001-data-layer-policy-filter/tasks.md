# Tasks: Layered Data Policy Filtering

**Input**: Design documents from `/specs/001-data-layer-policy-filter/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Not explicitly requested in feature specification. Test-graph intentionally deferred per spec notes.

**Organization**: Tasks grouped by user story (3 stories: US1=P1, US2=P1, US3=P2). US1 and US2 are both P1 but US1 (discovery) is a prerequisite for US2 (registration) validation flows.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

## Path Conventions

- **Lib models**: `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/filter/`
- **App services/controllers**: `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/`
- **App persistence**: `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/repository/`
- **App config**: `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/config/`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Create package structure and shared enums/constants for the filter subsystem.

- [ ] T001 Create filter model package structure in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/filter/` with sub-packages: `model/`, `model/executor/`, `model/interpreter/`, `model/layer/`, `model/instruction/`, `model/path/`, `model/decision/`, `model/policy/`
- [ ] T002 Create filter app package structure in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/` with sub-packages: `controller/`, `service/`, `repository/`, `config/`, `validation/`
- [ ] T003 [P] Create shared enums file with `PolicyStatus`, `ExecutorType`, `FilterKind`, `InstructionLanguage`, `InstructionOp`, `FilterAction`, `FilterDomain`, `ResponseMode`, `LayerType`, `InterpreterType`, `PathType` in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/filter/model/FilterEnums.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core domain model types that ALL user stories depend on. Must complete before any story work.

**CRITICAL**: No user story work can begin until this phase is complete.

### Sealed Type Hierarchy (Filter + ExecutableTool + Interpreter)

- [ ] T004 [P] Define sealed `Filter<I, O, CTX>` interface with fields (id, name, description, sourcePath, executor, policyStatus, priority, createdAt, updatedAt) in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/filter/model/Filter.java`
- [ ] T005 [P] Define matcher enums `MatcherKey` (NAME, TEXT), `MatcherType` (REGEX, EQUALS), `MatchOn` (PROMPT_CONTRIBUTOR, GRAPH_EVENT) in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/filter/model/FilterEnums.java` (add to existing enums file from T003)
- [ ] T006 [P] Define `FilterContext` interface extending `LayerCtx` with initial variants `PromptContributorContext` and `ControllerContext` in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/filter/model/FilterContext.java`
- [ ] T008 Define `PathFilter<I, O, CTX>` record implementing `Filter` with interpreter and instructionLanguage fields — all serialization (including instruction deserialization) is Jackson/JSON — in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/filter/model/PathFilter.java` (depends on T004)

### ExecutableTool Hierarchy

- [ ] T009 [P] Define sealed `ExecutableTool<I, O, CTX>` interface with fields (executorType, timeoutMs, configVersion) and execution contract returning `FilterExecutionResult<I, O>` in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/filter/model/executor/ExecutableTool.java`
- [ ] T010 [P] Define `FilterExecutionResult<I, O>` record (output, instructions, metadata) in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/filter/model/executor/FilterExecutionResult.java`
- [ ] T011 [P] Define `BinaryExecutor` record implementing `ExecutableTool` (command, workingDirectory, env, outputParserRef) in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/filter/model/executor/BinaryExecutor.java`
- [ ] T012 [P] Define `JavaFunctionExecutor` record implementing `ExecutableTool` (functionRef, className, methodName) in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/filter/model/executor/JavaFunctionExecutor.java`
- [ ] T013 [P] Define `PythonExecutor` record implementing `ExecutableTool` (scriptPath, entryFunction, runtimeArgsSchema) in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/filter/model/executor/PythonExecutor.java`
- [ ] T014 [P] Define `AiFilterTool` record implementing `ExecutableTool` (modelRef, promptTemplate, maxTokens, responseMode, outputSchema) in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/filter/model/executor/AiFilterTool.java`

### Path + Instruction + Interpreter Types

- [ ] T015 [P] Define sealed `Path` interface with `MarkdownPath` and `JsonPath` record variants in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/filter/model/path/Path.java`
- [ ] T016 [P] Define sealed `Instruction` interface with `Replace`, `Set`, `Remove` record variants (targetPath, value, order) in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/filter/model/instruction/Instruction.java`
- [ ] T017 [P] Define sealed `Interpreter` interface with `RegexInterpreter`, `MarkdownPathInterpreter`, `JsonPathInterpreter` variants in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/filter/model/interpreter/Interpreter.java`

### Layer Model

- [ ] T018 [P] Define sealed `Layer` interface with `matches(LayerCtx)` method and representative variants (`WorkflowAgentLayer`, `WorkflowAgentActionLayer`, `ControllerLayer`, `ControllerUiEventPollLayer`) in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/filter/model/layer/Layer.java`
- [ ] T019 [P] Define sealed `LayerCtx` interface with `AgentLayerCtx`, `ActionLayerCtx`, `ControllerLayerCtx` variants in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/filter/model/layer/LayerCtx.java`
- [ ] T020 [P] Define `LayerEntity` record/class with hierarchy fields (layerId, layerType, layerKey, parentLayerId, childLayerIds, isInheritable, isPropagatedToParent, depth, metadata) in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/filter/model/layer/LayerEntity.java`

### Decision Record + Policy Model

- [ ] T021 Define `FilterDecisionRecord<I, O>` record (decisionId, policyId, layer, action, input, output, appliedInstructions, errorMessage, createdAt) in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/filter/model/decision/FilterDecisionRecord.java` (depends on T016, T018)
- [ ] T022 [P] Define `PolicyLayerBinding` record (layerId, enabled, includeDescendants, isInheritable, isPropagatedToParent, matcherKey, matcherType, matcherText, matchOn, updatedBy, updatedAt) with matcher fields for narrowing which PromptContributor or GraphEvent the policy applies to within a layer in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/filter/model/policy/PolicyLayerBinding.java` (depends on T005 for matcher enums)
- [ ] T023 Define `PolicyRegistration` record (registrationId, registeredBy, status, isInheritable, isPropagatedToParent, filter, layerBindings, activatedAt, deactivatedAt) in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/filter/model/policy/PolicyRegistration.java` (depends on T004, T022)

### Persistence Layer

- [ ] T024 Define JPA entity `PolicyRegistrationEntity` mapped from `PolicyRegistration` with JSON column for filter definition in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/repository/PolicyRegistrationEntity.java` (depends on T023)
- [ ] T025 Define JPA entity `FilterDecisionRecordEntity` mapped from `FilterDecisionRecord` in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/repository/FilterDecisionRecordEntity.java` (depends on T021)
- [ ] T026 [P] Define `PolicyRegistrationRepository` (Spring Data JPA) with query methods for layer-based discovery in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/repository/PolicyRegistrationRepository.java`
- [ ] T027 [P] Define `FilterDecisionRecordRepository` (Spring Data JPA) with policy-scoped query methods in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/repository/FilterDecisionRecordRepository.java`
- [ ] T028 [P] Define `LayerRepository` (Spring Data JPA) for layer hierarchy queries (children, ancestors) in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/repository/LayerRepository.java`

### Jackson Serialization Config

- [ ] T029 Create Jackson `FilterModelModule` with serializers/deserializers for sealed filter type hierarchy (Filter, ExecutableTool, Interpreter, Path, Instruction, Layer, PolicyLayerBinding with matcher fields) in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/config/FilterModelModule.java` (depends on T004–T022)

**Checkpoint**: Foundation ready — all domain types, persistence, and serialization in place. User story implementation can begin.

---

## Phase 3: User Story 1 — Discover Active Layer Policies (Priority: P1) — MVP

**Goal**: Agents at any layer can query active policies and see description + source path for each.

**Independent Test**: Activate policies at controller and agent layers, query discovery endpoint, verify only active policies returned with description and sourcePath fields.

**Cucumber Tag**: `@policy-discovery`

### Implementation for User Story 1

- [ ] T030 [US1] Implement `LayerService` with `getChildLayers(layerId, recursive)` and `getEffectiveLayers(layerId)` (ancestor walk with inheritance) in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/service/LayerService.java`
- [ ] T031 [US1] Implement `PolicyDiscoveryService` with `getActivePoliciesByLayer(layerId)` that resolves effective layers (including inherited ancestor policies) and returns only ACTIVE policies with description and sourcePath in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/service/PolicyDiscoveryService.java` (depends on T030)
- [ ] T032 [US1] Define request/response DTOs: `ReadPoliciesByLayerResponse` (matching `read-policies-by-layer-response.schema.json`) and `ReadLayerChildrenResponse` (matching `read-layer-children-response.schema.json`) in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/controller/dto/` (depends on T023)
- [ ] T033 [US1] Implement `FilterPolicyController` with `GET /api/filters/layers/{layerId}/policies?status=ACTIVE` endpoint returning `ReadPoliciesByLayerResponse` in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/controller/FilterPolicyController.java` (depends on T031, T032)
- [ ] T034 [US1] Implement `GET /api/filters/layers/{layerId}/children?recursive=true` endpoint on `FilterPolicyController` returning `ReadLayerChildrenResponse` in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/controller/FilterPolicyController.java` (depends on T030, T032)
- [ ] T035 [US1] Verify deactivated policies are excluded from active discovery query in `PolicyDiscoveryService` (acceptance scenario 2) — add filtering logic and confirm inactive policies are not returned in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/service/PolicyDiscoveryService.java`

**Checkpoint**: US1 complete — `GET /api/filters/layers/{layerId}/policies?status=ACTIVE` and `GET /api/filters/layers/{layerId}/children` are functional.

---

## Phase 4: User Story 2 — Register and Deactivate Layer Policies (Priority: P1)

**Goal**: Agents can register new policies (with validation) and deactivate existing ones at runtime without restart.

**Independent Test**: Submit valid/invalid registrations for all executor types (binary, Java function, python, AI) across object and path filters, verify validation, then deactivate and confirm policy no longer applies.

**Cucumber Tag**: `@policy-registration`

### Implementation for User Story 2

- [ ] T036 [US2] Implement `PolicyExecutorValidator` for structural validation of executor config (timeoutMs > 0, required fields per executor type) in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/validation/PolicyExecutorValidator.java`
- [ ] T037 [US2] Implement `PolicySemanticValidator` for semantic validation rules: executor config completeness, layer binding validity, priority uniqueness warnings, and `matchOn` constraint enforcement (PROMPT_CONTRIBUTOR only for prompt-contributor endpoints, GRAPH_EVENT only for event endpoints, either for path filter endpoints) in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/validation/PolicySemanticValidator.java` (depends on T036)
- [ ] T038 [US2] Define request/response DTOs: `PolicyRegistrationRequest` (matching `policy-registration-request.schema.json` — no filter block, no inputType/outputType), `PolicyRegistrationResponse` (matching `policy-registration-response.schema.json`), `PutPolicyLayerRequest`/`Response` in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/controller/dto/` (depends on T023)
- [ ] T039 [US2] Implement `PolicyRegistrationService` with `registerPolicy(filterType, request)` that validates executor, persists, and activates policy — filterType is determined by the endpoint, not the request body in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/service/PolicyRegistrationService.java` (depends on T036, T037, T026)
- [ ] T040 [US2] Implement `deactivatePolicy(policyId)` in `PolicyRegistrationService` that sets status to INACTIVE and records deactivation timestamp without deleting history in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/service/PolicyRegistrationService.java`
- [ ] T041 [US2] Implement `updatePolicyLayerBinding(policyId, request)` in `PolicyRegistrationService` for upsert of layer bindings with inheritance/propagation controls in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/service/PolicyRegistrationService.java`
- [ ] T042 [P] [US2] Add per-filter-type registration endpoints to `FilterPolicyController`: `POST /api/filters/json-path-filters/policies`, `POST /api/filters/markdown-path-filters/policies`, `POST /api/filters/regex-path-filters/policies`, `POST /api/filters/ai-path-filters/policies` — each accepts `PolicyRegistrationRequest` (executor + common fields only, filter type implicit from endpoint) in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/controller/FilterPolicyController.java` (depends on T039, T038)
- [ ] T043 [US2] Add `POST /api/filters/policies/{policyId}/deactivate` endpoint (global deactivation) to `FilterPolicyController` returning `DeactivatePolicyResponse` in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/controller/FilterPolicyController.java` (depends on T040)
- [ ] T043a [P] [US2] Add `POST /api/filters/policies/{policyId}/layers/{layerId}/disable` and `POST /api/filters/policies/{policyId}/layers/{layerId}/enable` convenience endpoints to `FilterPolicyController` — sets `enabled=false`/`true` on a single layer binding with optional `includeDescendants`, returning `TogglePolicyLayerResponse` in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/controller/FilterPolicyController.java` (depends on T041)
- [ ] T044 [US2] Add `PUT /api/filters/policies/{policyId}/layers` endpoint to `FilterPolicyController` for full layer binding upsert (matcher, propagation, enabled state) in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/controller/FilterPolicyController.java` (depends on T041, T038)
- [ ] T045 [US2] Implement layer propagation logic: descendant propagation when `isInheritable=true`, parent propagation when `isPropagatedToParent=true` in `PolicyRegistrationService` in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/service/PolicyRegistrationService.java` (depends on T030, T041)

**Checkpoint**: US2 complete — policies can be registered via per-filter-type endpoints, validated, activated, deactivated, and layer bindings mutated with propagation.

---

## Phase 5: User Story 3 — Inspect Filtered Data by Active Policy (Priority: P2)

**Goal**: Agents can inspect filtered output tied to a specific active policy to verify what data was removed, replaced, or transformed.

**Independent Test**: Run content through multiple active policies, select one policy in filtered-output view, verify returned records include only that policy's decisions.

**Cucumber Tag**: `@policy-output-inspection`

### Implementation for User Story 3

- [ ] T046 [US3] Implement `FilterExecutionService` that executes applicable active filters for a given layer context, calls the bound `ExecutableTool`, and records `FilterDecisionRecord` entries in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/service/FilterExecutionService.java` (depends on T031, T021, T027)
- [ ] T047 [US3] Implement executor dispatch: `BinaryExecutorRunner` (ProcessBuilder with timeout/env), `JavaFunctionExecutorRunner` (reflection-based invoke), `PythonExecutorRunner` (subprocess with timeout), `AiFilterToolRunner` (model call) in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/service/executor/` (depends on T009–T014)
- [ ] T048 [US3] Implement interpreter dispatch: `RegexInterpreterRunner`, `MarkdownPathInterpreterRunner`, `JsonPathInterpreterRunner` that apply instruction lists to payloads in deterministic order in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/service/interpreter/` (depends on T015–T017)
- [ ] T049 [US3] Implement `FilterDecisionQueryService` with `getRecentRecordsByPolicyAndLayer(policyId, layerId, limit, cursor)` returning policy-scoped records only in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/service/FilterDecisionQueryService.java` (depends on T027)
- [ ] T050 [US3] Define response DTOs: `FilteredOutputResponse` (matching `filtered-output-response.schema.json`), `ReadRecentFilteredRecordsByPolicyLayerResponse` (matching `read-recent-filtered-records-by-policy-layer-response.schema.json`) in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/controller/dto/`
- [ ] T051 [US3] Add `GET /api/filters/policies/{policyId}/layers/{layerId}/records/recent?limit=50&cursor=` endpoint to `FilterPolicyController` returning `ReadRecentFilteredRecordsByPolicyLayerResponse` in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/controller/FilterPolicyController.java` (depends on T049, T050)
- [ ] T052 [US3] Add graceful error handling in `FilterExecutionService`: catch executor failures, record ERROR action in `FilterDecisionRecord`, apply configured fallback behavior without interrupting other filter processing in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/service/FilterExecutionService.java`
- [ ] T053 [US3] Verify inactive policy inspection returns no active filtered output and indicates inactive status (acceptance scenario 2) in `FilterDecisionQueryService` in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/service/FilterDecisionQueryService.java`

**Checkpoint**: US3 complete — agents can inspect policy-scoped filtered records, executor/interpreter pipelines are functional.

---

## Phase 6: Integration & Propagation Wiring

**Purpose**: Wire filter execution into existing prompt contributor and controller event pipelines.

- [ ] T054 Implement `PromptContributorFilterIntegration` that hooks `ObjectSerializerFilter` execution into `PromptContributorService` prompt assembly path for workflow agents in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/integration/PromptContributorFilterIntegration.java` (depends on T046)
- [ ] T055 Implement `ControllerEventFilterIntegration` that hooks `ObjectSerializerFilter` execution into controller event-processing path before user-facing emission in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/integration/ControllerEventFilterIntegration.java` (depends on T046)
- [ ] T056 Implement `PathFilterIntegration` that hooks `PathFilter` execution with interpreter dispatch into applicable data paths in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/integration/PathFilterIntegration.java` (depends on T046, T048)
- [ ] T057 Wire `FilterModelModule` Jackson module into existing `SerdesConfiguration` in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/config/FilterConfig.java` (depends on T029)

**Checkpoint**: Filter subsystem is wired into live prompt and event pipelines.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Deterministic ordering, persistence validation, and operational readiness.

- [ ] T058 Implement deterministic policy ordering (priority + policy ID tie-breaker) in `FilterExecutionService` per research decision #2 in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/service/FilterExecutionService.java`
- [ ] T059 Validate persistence: confirm policy activation states survive service restart by verifying `PolicyRegistrationRepository` JPA entity lifecycle in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/repository/PolicyRegistrationEntity.java`
- [ ] T060 Run quickstart.md validation: execute all 8 quickstart examples against live endpoints and confirm expected behavior
- [ ] T061 Run `./gradlew multi_agent_ide_java_parent:multi_agent_ide:test` and `./gradlew multi_agent_ide_java_parent:multi_agent_ide_lib:test` to verify no regressions

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — can start immediately
- **Foundational (Phase 2)**: Depends on Phase 1 — BLOCKS all user stories
- **US1 (Phase 3)**: Depends on Phase 2 completion
- **US2 (Phase 4)**: Depends on Phase 2 completion; can run in parallel with US1 but US1 discovery service is reused
- **US3 (Phase 5)**: Depends on Phase 2 completion; benefits from US1+US2 but independently testable with seed data
- **Integration (Phase 6)**: Depends on US3 (Phase 5) for execution service
- **Polish (Phase 7)**: Depends on all prior phases

### User Story Dependencies

- **US1 (P1)**: Can start after Phase 2 — No dependencies on other stories
- **US2 (P1)**: Can start after Phase 2 — Reuses `LayerService` from US1 but can stub/implement independently
- **US3 (P2)**: Can start after Phase 2 — Requires executor/interpreter runners; benefits from US2 for real policies

### Within Each User Story

- Models before services
- Services before controllers
- Validation before registration (US2)
- Core implementation before integration

### Parallel Opportunities

- T003 can run parallel with T001/T002
- T004–T006, T009–T020 are all parallelizable (independent sealed type files)
- T024–T028 are parallelizable after model types exist
- T030 and T036–T037 can start in parallel (different services)
- T047 executor runners are parallelizable (separate files)
- T048 interpreter runners are parallelizable (separate files)
- T054–T056 integration hooks are parallelizable (different pipelines)

---

## Parallel Example: Phase 2 Foundation

```text
# All sealed type definitions in parallel:
Task T004: Filter<I,O,CTX> sealed interface
Task T005: Matcher enums (MatcherKey, MatcherType, MatchOn)
Task T006: FilterContext interface
Task T009: ExecutableTool sealed interface
Task T010: FilterExecutionResult record
Task T011: BinaryExecutor record
Task T012: JavaFunctionExecutor record
Task T013: PythonExecutor record
Task T014: AiFilterTool record
Task T015: Path sealed interface
Task T016: Instruction sealed interface
Task T017: Interpreter sealed interface
Task T018: Layer sealed interface
Task T019: LayerCtx sealed interface
Task T020: LayerEntity record
Task T022: PolicyLayerBinding record
```

## Parallel Example: US3 Executors

```text
# All executor runners in parallel:
Task T047: BinaryExecutorRunner, JavaFunctionExecutorRunner, PythonExecutorRunner, AiFilterToolRunner
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational (CRITICAL — blocks all stories)
3. Complete Phase 3: User Story 1
4. **STOP and VALIDATE**: Test discovery endpoint independently
5. Deploy/demo if ready

### Incremental Delivery

1. Setup + Foundational → Foundation ready
2. Add US1 → Test discovery independently → Deploy/Demo (MVP!)
3. Add US2 → Test registration/deactivation independently → Deploy/Demo
4. Add US3 → Test inspection independently → Deploy/Demo
5. Add Integration (Phase 6) → Wire into live pipelines
6. Polish (Phase 7) → Operational readiness

### Parallel Team Strategy

With multiple developers:
1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: US1 (discovery)
   - Developer B: US2 (registration/validation)
   - Developer C: US3 (inspection/execution)
3. Stories complete and integrate, then run tests and fix.

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story is independently completable and testable
- test_graph integration is intentionally deferred per spec notes
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
