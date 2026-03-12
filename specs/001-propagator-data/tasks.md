# Tasks: Controller Data Propagators and Transformers

**Input**: Design documents from `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-propagator-data/`

**Prerequisites**: `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-propagator-data/plan.md`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-propagator-data/spec.md`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-propagator-data/research.md`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-propagator-data/data-model.md`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-propagator-data/contracts/controller-data-flow.openapi.yaml`

**Tests**: App-module integration and controller tests are included because the spec defines independent verification criteria. `test_graph` work remains deferred per `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-propagator-data/spec.md`.

**Organization**: Tasks are grouped by user story so each increment remains independently implementable and verifiable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel when dependencies are satisfied and files do not overlap
- **[Story]**: Maps to the user story in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-propagator-data/spec.md`
- Every task includes exact file paths

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Create the shared wiring points that let the new propagator and transformer subsystems plug into the existing model, event, and serialization infrastructure.

- [ ] T001 Create Spring/Jackson wiring for the new subsystems in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/config/PropagationModelModule.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/config/TransformationModelModule.java`, and `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/config/FilterConfig.java`
- [ ] T002 [P] Extend shared enums and graph-event registration for propagation and transformation actions in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/acp-cdc-ai/src/main/java/com/hayden/acp_cdc_ai/acp/filter/FilterEnums.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/acp-cdc-ai/src/main/java/com/hayden/acp_cdc_ai/acp/events/Events.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/adapter/SaveGraphEventToGraphRepositoryListener.java`, and `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/artifacts/ArtifactEventListener.java`
- [ ] T003 [P] Add AI propagator/transformer request and result records plus pretty-print support in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/AgentModels.java`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Build the reusable library, persistence, and execution foundations that every user story depends on.

**⚠️ CRITICAL**: No user story work should start until this phase is complete.

- [ ] T004 Create the shared propagator model hierarchy in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/propagation/model/Propagator.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/propagation/model/TextPropagator.java`, and `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/propagation/model/AiTextPropagator.java`
- [ ] T005 [P] Create the shared transformer model hierarchy in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/transformation/model/Transformer.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/transformation/model/TextTransformer.java`, and `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/transformation/model/AiTextTransformer.java`
- [ ] T006 [P] Create separate binding and execution-context types in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/propagation/model/PropagatorLayerBinding.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/propagation/model/layer/DefaultPropagationContext.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/propagation/model/layer/AiPropagatorContext.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/transformation/model/TransformerLayerBinding.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/transformation/model/layer/ControllerEndpointTransformationContext.java`, and `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/transformation/model/layer/AiTransformerContext.java`
- [ ] T007 Create normalized propagator and transformer payload models in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/propagation/model/PropagationOutput.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/propagation/model/PropagationItem.java`, and `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/transformation/model/TransformationOutput.java`
- [ ] T008 Create dedicated AI executor implementations and hydration helpers in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/propagation/model/executor/AiPropagatorTool.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/service/AiPropagatorToolHydration.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/transformation/model/executor/AiTransformerTool.java`, and `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/service/AiTransformerToolHydration.java`
- [ ] T009 [P] Create JPA entities and repositories in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/repository/PropagatorRegistrationEntity.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/repository/PropagationItemEntity.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/repository/PropagationRecordEntity.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/repository/PropagatorRegistrationRepository.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/repository/PropagationItemRepository.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/repository/PropagationRecordRepository.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/repository/TransformerRegistrationEntity.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/repository/TransformationRecordEntity.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/repository/TransformerRegistrationRepository.java`, and `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/repository/TransformationRecordRepository.java`
- [ ] T010 [P] Create registration, discovery, and validator services in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/service/PropagatorRegistrationService.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/service/PropagatorDiscoveryService.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/validation/PropagatorSemanticValidator.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/validation/PropagatorExecutorValidator.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/service/TransformerRegistrationService.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/service/TransformerDiscoveryService.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/validation/TransformerSemanticValidator.java`, and `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/validation/TransformerExecutorValidator.java`
- [ ] T011 Create attachable-target and layer-resolution support in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/service/LayerIdResolver.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/service/PropagatorAttachableCatalogService.java`, and `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/service/TransformerAttachableCatalogService.java`
- [ ] T012 Create baseline execution and query infrastructure in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/service/PropagationExecutionService.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/service/PropagationRecordQueryService.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/service/AiPropagatorSessionResolver.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/service/TransformerExecutionService.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/service/TransformationRecordQueryService.java`, and `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/service/AiTransformerSessionResolver.java`

**Checkpoint**: Shared models, persistence, and execution services exist so user stories can layer integrations on top.

---

## Phase 3: User Story 1 - Propagate important action responses to the controller (Priority: P1) 🎯 MVP

**Goal**: Surface important action responses to the controller, create acknowledgement or approval items when needed, and block downstream work until required controller resolution occurs.

**Independent Test**: Register a response-stage propagator, trigger the action, verify the controller receives a propagation item or acknowledgement request, resolve it, and confirm `PROPAGATION_ACKNOWLEDGED` or the required final state is recorded.

### Tests for User Story 1

- [ ] T013 [P] [US1] Add response-stage propagation integration coverage in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/integration/propagation/ActionResponsePropagationIT.java`
- [ ] T014 [P] [US1] Add acknowledgement-resolution coverage in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/integration/propagation/PropagationAcknowledgementIT.java`

### Implementation for User Story 1

- [ ] T015 [US1] Implement the response-stage propagation hook in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/integration/ActionResponsePropagationIntegration.java` and wire it from `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/decorator/result/EmitActionCompletedResultDecorator.java`
- [ ] T016 [US1] Implement propagation-item creation, gating, and duplicate suppression in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/service/PropagationItemService.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/service/PropagationDeduplicationService.java`, and `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/repository/PropagationItemEntity.java`
- [ ] T017 [US1] Route acknowledgement and approval resolution through the permission gate in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/kotlin/com/hayden/multiagentide/gate/PermissionGate.kt` and `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/service/PropagationResolutionService.java`
- [ ] T018 [US1] Expose propagation-item list and resolve endpoints in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/controller/PropagationController.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/controller/dto/ReadPropagationItemsResponse.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/controller/dto/ResolvePropagationItemRequest.java`, and `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/controller/dto/ResolvePropagationItemResponse.java`
- [ ] T019 [US1] Surface propagation pending items and event summaries in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/controller/LlmDebugUiController.java` and `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/cli/CliEventFormatter.java`
- [ ] T020 [US1] Add controller-operator CLI support for listing and acknowledging propagation items in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/skills/multi_agent_test_supervisor/scripts/quick_action.py` and `/Users/hayde/IdeaProjects/multi_agent_ide_parent/skills/multi_agent_test_supervisor/scripts/_client.py`

**Checkpoint**: Response-stage propagations create controller-visible items, resolve through the permission gate, and emit auditable acknowledgement outcomes.

---

## Phase 4: User Story 2 - Attach propagators to action requests and responses (Priority: P1)

**Goal**: Let operators bind propagators to either action requests or action responses while keeping response-stage propagation as the primary path.

**Independent Test**: Register one request-stage propagator and one response-stage propagator, exercise both stages, and verify only the matching stage fires for each binding.

### Tests for User Story 2

- [ ] T021 [P] [US2] Add stage-specific propagation binding coverage in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/integration/propagation/PropagationStageBindingIT.java`
- [ ] T022 [P] [US2] Add propagator controller contract coverage in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/integration/propagation/PropagatorControllerIT.java`

### Implementation for User Story 2

- [ ] T023 [US2] Implement the request-stage propagation hook and shared stage routing in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/integration/ActionRequestPropagationIntegration.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/decorator/request/PropagateActionRequestDecorator.java`, and `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/service/PropagationExecutionService.java`
- [ ] T024 [US2] Implement propagator registration, query, deactivate, and layer-update endpoints in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/controller/PropagatorController.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/controller/dto/PropagatorRegistrationRequest.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/controller/dto/PropagatorRegistrationResponse.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/controller/dto/PutPropagatorLayerRequest.java`, and `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/controller/dto/PutPropagatorLayerResponse.java`
- [ ] T025 [US2] Implement attachable-target and layer-read responses for propagators in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/service/PropagatorAttachableCatalogService.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/controller/dto/ReadPropagatorAttachableTargetsResponse.java`, and `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/controller/dto/ReadPropagatorsByLayerResponse.java`
- [ ] T026 [US2] Finalize request-vs-response matching and non-fatal passthrough behavior in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/propagation/model/PropagatorLayerBinding.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/service/PropagationExecutionService.java`, and `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/service/PropagationRecordQueryService.java`

**Checkpoint**: Propagators can be attached to either action stage and only run where their bindings say they should.

---

## Phase 5: User Story 3 - Transform controller endpoint outputs into strings (Priority: P1)

**Goal**: Allow controller endpoints to return transformed strings while preserving the original payload for audit and failure fallback.

**Independent Test**: Attach a transformer to a controller endpoint, invoke the endpoint, verify the outward response becomes a string, and confirm before/after values are recorded even when transformation fails.

### Tests for User Story 3

- [ ] T027 [P] [US3] Add controller-endpoint transformation coverage in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/controller/LlmDebugUiControllerWorkflowGraphTest.java`
- [ ] T028 [P] [US3] Add transformer controller contract coverage in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/integration/transformation/TransformerControllerIT.java`

### Implementation for User Story 3

- [ ] T029 [US3] Implement controller-response transformation advice and endpoint hook selection in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/integration/ControllerResponseTransformationAdvice.java` and `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/integration/ControllerEndpointTransformationIntegration.java`
- [ ] T030 [US3] Implement transformer registration, query, deactivate, and layer-update endpoints in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/controller/TransformerController.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/controller/dto/TransformerRegistrationRequest.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/controller/dto/TransformerRegistrationResponse.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/controller/dto/PutTransformerLayerRequest.java`, and `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/controller/dto/PutTransformerLayerResponse.java`
- [ ] T031 [US3] Implement attachable-target and execution wiring for controller-endpoint transformers in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/service/TransformerAttachableCatalogService.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/service/TransformerExecutionService.java`, and `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/service/LayerIdResolver.java`
- [ ] T032 [US3] Update controller and client request handling for transformed string responses in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/controller/LlmDebugUiController.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/skills/multi_agent_test_supervisor/scripts/quick_action.py`, and `/Users/hayde/IdeaProjects/multi_agent_ide_parent/skills/multi_agent_test_supervisor/scripts/_client.py`
- [ ] T033 [US3] Emit transformation events and audit descriptors for transformed controller responses in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/acp-cdc-ai/src/main/java/com/hayden/acp_cdc_ai/acp/events/Events.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/cli/CliEventFormatter.java`, and `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/service/TransformerExecutionService.java`

**Checkpoint**: Controller endpoints can return transformed strings without losing the original payload or breaking the controller flow when a transformer fails.

---

## Phase 6: User Story 4 - Compare propagation and transformation outcomes over time (Priority: P2)

**Goal**: Persist and query propagation and transformation outcomes as comparable audit streams for later operator analysis.

**Independent Test**: Run both a propagator and a transformer against the same logical flow, then query propagation and transformation history and verify before/after payloads, resolutions, and outcomes remain comparable.

### Tests for User Story 4

- [ ] T034 [P] [US4] Add audit comparison coverage in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/integration/audit/PropagationTransformationAuditIT.java`

### Implementation for User Story 4

- [ ] T035 [US4] Implement propagation-record query endpoints in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/controller/PropagationRecordController.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/controller/dto/ReadPropagationRecordsResponse.java`, and `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/service/PropagationRecordQueryService.java`
- [ ] T036 [US4] Implement transformation-record query endpoints in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/controller/TransformationRecordController.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/controller/dto/ReadTransformationRecordsResponse.java`, and `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/service/TransformationRecordQueryService.java`
- [ ] T037 [US4] Persist comparable before/after metadata and final resolutions in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/service/PropagationExecutionService.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/service/TransformerExecutionService.java`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/repository/PropagationRecordEntity.java`, and `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/repository/TransformationRecordEntity.java`
- [ ] T038 [US4] Add quick-action commands for propagation and transformation record review in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/skills/multi_agent_test_supervisor/scripts/quick_action.py`

**Checkpoint**: Operators can query and compare propagation and transformation outcomes without losing stage, resolution, or payload history.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Close the loop with documentation, validation, and cross-story regression checks.

- [ ] T039 [P] Update operator docs and example payloads in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-propagator-data/quickstart.md`, `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-propagator-data/examples/register-propagator.json`, and `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-propagator-data/examples/register-transformer.json`
- [ ] T040 Run the compile validation commands documented in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-propagator-data/quickstart.md`
- [ ] T041 Run the targeted app tests documented in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-propagator-data/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: Starts immediately.
- **Foundational (Phase 2)**: Depends on Phase 1 and blocks every user story.
- **User Story 1 (Phase 3)**: Depends on Phase 2 and is the MVP slice.
- **User Story 2 (Phase 4)**: Depends on Phase 2; implement after US1 if you want to minimize conflicts in propagation files.
- **User Story 3 (Phase 5)**: Depends on Phase 2 and can proceed in parallel with US1 if a separate implementer owns the transformation files.
- **User Story 4 (Phase 6)**: Depends on US1 and US3 because it compares both audit streams; include US2 first if request-stage propagation must also be part of the comparison set.
- **Polish (Phase 7)**: Depends on the user stories you plan to ship.

### User Story Dependencies

- **US1**: No dependency on other stories after Phase 2.
- **US2**: Reuses the propagation runtime from Phase 2 and shares many files with US1; sequence it after US1 for lower merge risk.
- **US3**: Independent of US1 and US2 after Phase 2 because it lives in the transformation/controller path.
- **US4**: Requires the propagation audit path from US1 and the transformation audit path from US3.

### Within Each User Story

- Add the story tests first.
- Complete the runtime hook before controller or CLI exposure.
- Finish persistence and endpoint work before quick-action command wiring.
- Validate the story’s independent test before moving on.

### Parallel Opportunities

- T002 and T003 can run in parallel after T001 starts the wiring shape.
- T005, T006, T009, and T010 can run in parallel after T004 defines the shared propagation baseline.
- US1 and US3 can proceed in parallel after Phase 2 because they mostly touch different packages.
- The explicit `[P]` tasks inside each story are safe parallel starting points.

---

## Parallel Example: User Story 1

```bash
Task: "T013 [US1] Add response-stage propagation integration coverage in /Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/integration/propagation/ActionResponsePropagationIT.java"
Task: "T014 [US1] Add acknowledgement-resolution coverage in /Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/integration/propagation/PropagationAcknowledgementIT.java"
```

## Parallel Example: User Story 2

```bash
Task: "T021 [US2] Add stage-specific propagation binding coverage in /Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/integration/propagation/PropagationStageBindingIT.java"
Task: "T022 [US2] Add propagator controller contract coverage in /Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/integration/propagation/PropagatorControllerIT.java"
```

## Parallel Example: User Story 3

```bash
Task: "T027 [US3] Add controller-endpoint transformation coverage in /Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/controller/LlmDebugUiControllerWorkflowGraphTest.java"
Task: "T028 [US3] Add transformer controller contract coverage in /Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/integration/transformation/TransformerControllerIT.java"
```

## Parallel Example: User Story 4

```bash
Task: "T035 [US4] Implement propagation-record query endpoints in /Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/propagation/controller/PropagationRecordController.java"
Task: "T036 [US4] Implement transformation-record query endpoints in /Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/transformation/controller/TransformationRecordController.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Finish Phase 1 and Phase 2.
2. Deliver Phase 3 completely.
3. Run the compile and targeted test commands from `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-propagator-data/quickstart.md`.
4. Validate the US1 independent test before broadening to request-stage propagation or controller endpoint transformation.

### Incremental Delivery

1. Ship US1 for response-stage controller propagation and acknowledgement handling.
2. Add US3 next if transformed controller responses are the next highest-value controller improvement; otherwise add US2 first for request-stage attachment coverage.
3. Finish the remaining P1 story.
4. Land US4 after both audit streams are live.
5. Keep `test_graph` deferred until the server-side contracts stabilize, as documented in `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-propagator-data/spec.md`.
