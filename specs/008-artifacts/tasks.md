# Tasks: Execution Artifacts, Templates, and Semantics

**Input**: Design documents from `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/008-artifacts/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/artifact-events.contract.json, quickstart.md

**Tests**: Not requested in the specification. No test tasks included.

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and baseline configuration for artifact persistence.

- [ ] T001 Add docker-compose test DB config in `multi_agent_ide/src/test/resources/application-test.yml`
- [ ] T002 [P] Add artifact storage config placeholders in `multi_agent_ide/src/main/resources/application.yml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core artifact/key/serialization infrastructure and persistence wiring.

- [ ] T003 Create ArtifactKey and generator utility in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/artifact/ArtifactKey.java`
- [ ] T004 [P] Create base artifact models in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/artifact/Artifact.java`
- [ ] T005 [P] Create RefArtifact model in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/artifact/RefArtifact.java`
- [ ] T006 [P] Add content hashing + canonical JSON helpers in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/artifact/ArtifactHashing.java`
- [ ] T007 Add ArtifactEvent to `utilitymodule/src/main/java/com/hayden/utilitymodule/acp/events/Events.java`
- [ ] T008 [P] Add artifact persistence entity in `multi_agent_ide/src/main/java/com/hayden/multiagentide/artifacts/entity/ArtifactEntity.java`
- [ ] T009 [P] Add artifact repository interface in `multi_agent_ide/src/main/java/com/hayden/multiagentide/artifacts/repository/ArtifactRepository.java`
- [ ] T010 [P] Add artifact tree builder in `multi_agent_ide/src/main/java/com/hayden/multiagentide/artifacts/ArtifactTreeBuilder.java`
- [ ] T011 Add ArtifactEvent listener wired to EventBus in `multi_agent_ide/src/main/java/com/hayden/multiagentide/artifacts/ArtifactEventListener.java`
- [ ] T012 Add GraphEvent -> EventArtifact mapper in `multi_agent_ide/src/main/java/com/hayden/multiagentide/artifacts/EventArtifactMapper.java`

**Checkpoint**: Artifact infrastructure ready; user story work can begin.

---

## Phase 3: User Story 1 - Reconstruct Execution (Priority: P1) 🎯 MVP

**Goal**: Emit a complete, reconstructable execution artifact tree (prompts, tool I/O, config, events, outcomes).

**Independent Test**: Load a single execution record and reconstruct prompts, tool I/O, and outcomes using only the artifact tree.

### Implementation for User Story 1

- [ ] T013 [US1] Add execution scope + root artifact creation in `multi_agent_ide/src/main/java/com/hayden/multiagentide/artifacts/ExecutionScopeService.java`
- [ ] T014 [P] [US1] Emit AgentRequestArtifact in `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java`
- [ ] T015 [P] [US1] Emit AgentResult/CollectorDecision/Interrupt artifacts in `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java`
- [ ] T016 [US1] Emit ExecutionConfig and OutcomeEvidence artifacts in `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentLifecycleHandler.java`
- [ ] T017 [US1] Add MessageStreamArtifact model in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/artifact/MessageStreamArtifact.java`
- [ ] T018 [US1] Map stream/message events to MessageStreamArtifact in `multi_agent_ide/src/main/java/com/hayden/multiagentide/artifacts/EventArtifactMapper.java`
- [ ] T019 [US1] Ensure nested sub-agent artifact scopes in `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/WorkflowGraphService.java`

**Checkpoint**: Execution tree can be reconstructed end-to-end for a single run.

---

## Phase 4: User Story 2 - Track Template Evolution (Priority: P2)

**Goal**: Persist static prompt templates as versioned artifacts and deduplicate by content hash.

**Independent Test**: Compare two executions using different template versions; verify version identity and outcome correlation.

### Implementation for User Story 2

- [ ] T020 [P] [US2] Add Templated contract in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/artifact/Templated.java`
- [ ] T021 [P] [US2] Add PromptTemplateVersion model in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/artifact/PromptTemplateVersion.java`
- [ ] T022 [US2] Implement prompt template loader in `multi_agent_ide/src/main/java/com/hayden/multiagentide/prompt/PromptTemplateLoader.java`
- [ ] T023 [US2] Implement template store + dedup logic in `multi_agent_ide/src/main/java/com/hayden/multiagentide/prompt/PromptTemplateStore.java`
- [ ] T024 [US2] Emit PromptContribution artifacts in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/prompt/PromptAssembly.java`
- [ ] T025 [US2] Emit PromptExecution/RenderedPrompt/PromptArgs artifacts in `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java`

**Checkpoint**: Template versions are stable across runs and traceable to outcomes.

---

## Phase 5: User Story 3 - Post-Hoc Semantics (Priority: P3)

**Goal**: Attach semantic representations to artifacts without mutating source artifacts.

**Independent Test**: Create a semantic representation and verify source artifacts are unchanged.

### Implementation for User Story 3

- [ ] T026 [P] [US3] Add SemanticRepresentation model in `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/artifact/SemanticRepresentation.java`
- [ ] T027 [US3] Add semantic persistence entity/repository in `multi_agent_ide/src/main/java/com/hayden/multiagentide/artifacts/semantic/SemanticRepresentationEntity.java`
- [ ] T028 [US3] Add semantic attachment service in `multi_agent_ide/src/main/java/com/hayden/multiagentide/artifacts/semantic/SemanticRepresentationService.java`

**Checkpoint**: Semantic layer is functional without mutating source artifacts.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Documentation and operational checks.

- [ ] T029 [P] Update artifact persistence docs in `multi_agent_ide/README.md`
- [ ] T030 Validate quickstart steps and update `specs/008-artifacts/quickstart.md` if needed

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phases 3-5)**: Depend on Foundational phase completion
- **Polish (Phase 6)**: Depends on desired user stories being complete

### User Story Dependencies

- **US1 (P1)**: Starts after Foundational; no dependency on other stories
- **US2 (P2)**: Starts after Foundational; can run in parallel with US1
- **US3 (P3)**: Starts after Foundational; can run in parallel with US1/US2

### Parallel Opportunities

- T004, T005, T006, T008, T009, T010 can run in parallel in Phase 2.
- US1 emissions (T014/T015) can run in parallel once execution scope is defined.
- US2 model/contract tasks (T020/T021) can run in parallel.
- US3 model task (T026) can run in parallel with US2 work.

---

## Parallel Example: User Story 1

```text
Task: "Emit AgentRequestArtifact in multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java"
Task: "Emit AgentResult/CollectorDecision/Interrupt artifacts in multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AgentInterfaces.java"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1
4. Validate a single execution can be reconstructed from artifacts

### Incremental Delivery

1. Complete Setup + Foundational
2. Deliver US1 (MVP) and validate reconstruction
3. Deliver US2 (template evolution tracking)
4. Deliver US3 (semantic representations)
5. Polish and documentation updates

---

## Notes

- No test_graph tasks included (explicitly skipped for this feature).
- All tasks include file paths and follow the required checklist format.
