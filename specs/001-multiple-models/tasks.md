# Tasks: Runtime ACP Model Selection

**Input**: Design documents from `/Users/hayde/IdeaProjects/multi_agent_ide_parent/specs/001-multiple-models/`

**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/acp-runtime-selection.openapi.yaml`

**Tests**: Regression and unit/integration tests are included because the plan and quickstart explicitly call out ACP routing regression coverage.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g. `US1`, `US2`, `US3`)
- Include exact file paths in descriptions

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Prepare the ACP runtime-selection implementation surface and test targets.

- [X] T001 Review and align implementation touchpoints in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/service/DefaultLlmRunner.java`, `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/config/MultiAgentEmbabelConfig.java`, and `multi_agent_ide_java_parent/acp-cdc-ai/src/main/kotlin/com/hayden/acp_cdc_ai/acp/AcpChatModel.kt`
- [X] T002 [P] Add placeholder runtime-routing model package under `multi_agent_ide_java_parent/acp-cdc-ai/src/main/java/com/hayden/acp_cdc_ai/acp/config/` for provider catalog and call payload records
- [X] T003 [P] Identify and stage regression test targets in `multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/service/DefaultLlmRunnerTest.java`, `multi_agent_ide_java_parent/acp-cdc-ai/src/test/java/com/hayden/acp_cdc_ai/acp/AcpChatModelArgsParsingTest.java`, and `multi_agent_ide_java_parent/acp-cdc-ai/src/test/kotlin/com/hayden/acp_cdc_ai/acp/AcpChatModelIntegrationTest.kt`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Create the shared runtime-routing data structures and configuration model required by all user stories.

**⚠️ CRITICAL**: No user story work should begin until this phase is complete.

- [X] T004 Create the `AcpChatOptionsString` record in `multi_agent_ide_java_parent/acp-cdc-ai/src/main/java/com/hayden/acp_cdc_ai/acp/config/AcpChatOptionsString.java`
- [X] T005 [P] Create the `AcpProviderDefinition` record in `multi_agent_ide_java_parent/acp-cdc-ai/src/main/java/com/hayden/acp_cdc_ai/acp/config/AcpProviderDefinition.java`
- [X] T006 [P] Create the `AcpResolvedCall` record in `multi_agent_ide_java_parent/acp-cdc-ai/src/main/java/com/hayden/acp_cdc_ai/acp/config/AcpResolvedCall.java`
- [X] T007 [P] Create the `AcpSessionRoutingKey` record in `multi_agent_ide_java_parent/acp-cdc-ai/src/main/java/com/hayden/acp_cdc_ai/acp/config/AcpSessionRoutingKey.java`
- [X] T008 Refactor `multi_agent_ide_java_parent/acp-cdc-ai/src/main/java/com/hayden/acp_cdc_ai/acp/config/AcpModelProperties.java` into a catalog-based configuration root with `defaultProvider` and `providers` map support
- [X] T009 Add provider-catalog binding and validation tests in `multi_agent_ide_java_parent/acp-cdc-ai/src/test/java/com/hayden/acp_cdc_ai/acp/AcpChatModelArgsParsingTest.java`

**Checkpoint**: Shared ACP payload and provider catalog contracts exist and are test-backed.

---

## Phase 3: User Story 1 - Select ACP Runtime Per Call (Priority: P1) 🎯 MVP

**Goal**: Allow each LLM call to carry a structured runtime ACP payload that selects provider and model without changing application profile.

**Independent Test**: Build two LLM calls in one running application flow and verify the serialized ACP payload resolves to different provider/model combinations while preserving the same call path.

### Tests for User Story 1

- [X] T010 [P] [US1] Add payload-construction assertions in `multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/service/DefaultLlmRunnerTest.java`
- [X] T011 [P] [US1] Add options-converter coverage for serialized ACP routing in `multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/service/DefaultLlmRunnerTest.java`

### Implementation for User Story 1

- [X] T012 [US1] Build the versioned ACP routing payload in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/service/DefaultLlmRunner.java`
- [X] T013 [US1] Update `withFirstAvailableLlmOf(...)` usage in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/service/DefaultLlmRunner.java` to pass the serialized ACP payload as the only runtime selection argument
- [X] T014 [US1] Refactor `EmbabelAcpChatOptions` and the options converter in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/config/MultiAgentEmbabelConfig.java` to emit `sessionIdentity___jsonPayload`
- [X] T015 [US1] Preserve legacy caller compatibility by mapping existing model/session inputs into the new payload in `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/config/MultiAgentEmbabelConfig.java`

**Checkpoint**: User Story 1 is complete when LLM calls can express runtime provider/model selection through the new payload path.

---

## Phase 4: User Story 2 - Preserve Session Routing Metadata (Priority: P2)

**Goal**: Keep session identity, async-safe routing, and session reuse behavior correct when provider/model can change per call.

**Independent Test**: Resolve and execute calls with the same session identity but different provider/model selections and verify memory/event routing remains correct while ACP session reuse keys stay isolated.

### Tests for User Story 2

- [X] T016 [P] [US2] Add payload-parsing and validation tests in `multi_agent_ide_java_parent/acp-cdc-ai/src/test/java/com/hayden/acp_cdc_ai/acp/AcpChatModelArgsParsingTest.java`
- [X] T017 [P] [US2] Add composite-session-key coverage in `multi_agent_ide_java_parent/acp-cdc-ai/src/test/kotlin/com/hayden/acp_cdc_ai/acp/AcpChatModelIntegrationTest.kt`

### Implementation for User Story 2

- [X] T018 [US2] Parse `sessionIdentity___jsonPayload` and resolve `AcpResolvedCall` in `multi_agent_ide_java_parent/acp-cdc-ai/src/main/kotlin/com/hayden/acp_cdc_ai/acp/AcpChatModel.kt`
- [X] T019 [US2] Replace memory-id-only session caching with `AcpSessionRoutingKey` in `multi_agent_ide_java_parent/acp-cdc-ai/src/main/kotlin/com/hayden/acp_cdc_ai/acp/AcpChatModel.kt`
- [X] T020 [US2] Update sandbox translation, provider lookup, and effective-model resolution to use the resolved call payload in `multi_agent_ide_java_parent/acp-cdc-ai/src/main/kotlin/com/hayden/acp_cdc_ai/acp/AcpChatModel.kt`
- [X] T021 [US2] Add safe routing diagnostics for session identity, provider, and effective model in `multi_agent_ide_java_parent/acp-cdc-ai/src/main/kotlin/com/hayden/acp_cdc_ai/acp/AcpChatModel.kt`
- [X] T022 [US2] Update legacy ACP request parameter bridging in `multi_agent_ide_java_parent/multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/model/acp/AcpChatRequestParameters.java` so existing callers can still reach the new runtime-routing path

**Checkpoint**: User Story 2 is complete when async execution still resolves the correct session identity and does not leak ACP sessions across provider/model boundaries.

---

## Phase 5: User Story 3 - Reuse Structured ACP Configuration (Priority: P3)

**Goal**: Support a reusable catalog of named ACP provider definitions plus a default provider across existing application profiles.

**Independent Test**: Configure at least two ACP provider definitions, resolve one by explicit name and one by default, and verify unknown providers fail before ACP session startup.

### Tests for User Story 3

- [X] T023 [P] [US3] Add provider-catalog selection and default-provider tests in `multi_agent_ide_java_parent/acp-cdc-ai/src/test/java/com/hayden/acp_cdc_ai/acp/AcpChatModelArgsParsingTest.java`
- [X] T024 [P] [US3] Add end-to-end provider-selection regression coverage in `multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/acp_tests/AcpChatModelCodexIntegrationTest.java`

### Implementation for User Story 3

- [X] T025 [US3] Migrate the base ACP profile structure to the provider catalog in `multi_agent_ide_java_parent/multi_agent_ide/src/main/resources/application-acp.yml`
- [X] T026 [P] [US3] Migrate Codex provider configuration to the provider catalog in `multi_agent_ide_java_parent/multi_agent_ide/src/main/resources/application-codex.yml`
- [X] T027 [P] [US3] Migrate Claude profile configuration to the provider catalog in `multi_agent_ide_java_parent/multi_agent_ide/src/main/resources/application-claude.yml`
- [X] T028 [P] [US3] Migrate Claude Ollama profile configuration to the provider catalog in `multi_agent_ide_java_parent/multi_agent_ide/src/main/resources/application-claudellama.yml`
- [X] T029 [P] [US3] Migrate Claude OpenRouter profile configuration to the provider catalog in `multi_agent_ide_java_parent/multi_agent_ide/src/main/resources/application-claudeopenrouter.yml`
- [X] T030 [US3] Enforce unknown-provider and missing-default validation in `multi_agent_ide_java_parent/acp-cdc-ai/src/main/kotlin/com/hayden/acp_cdc_ai/acp/AcpChatModel.kt`

**Checkpoint**: User Story 3 is complete when named provider definitions and a default provider can drive runtime ACP selection across existing profiles.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final verification, documentation alignment, and regression execution across all stories.

- [X] T031 [P] Update runtime-selection developer notes in `specs/001-multiple-models/quickstart.md`
- [X] T032 Run ACP regression tests via `multi_agent_ide_java_parent/gradlew` for `multi_agent_ide` and `acp-cdc-ai` targets from `/Users/hayde/IdeaProjects/multi_agent_ide_parent/multi_agent_ide_java_parent`
- [X] T033 [P] Review and tighten log-safety/error-message wording in `multi_agent_ide_java_parent/acp-cdc-ai/src/main/kotlin/com/hayden/acp_cdc_ai/acp/AcpChatModel.kt` and `multi_agent_ide_java_parent/multi_agent_ide/src/main/java/com/hayden/multiagentide/config/MultiAgentEmbabelConfig.java`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies; can start immediately.
- **Foundational (Phase 2)**: Depends on Setup completion; blocks all user-story work.
- **User Story 1 (Phase 3)**: Depends on Foundational completion; defines the MVP runtime payload path.
- **User Story 2 (Phase 4)**: Depends on User Story 1 because session parsing and cache-key isolation require the new payload format.
- **User Story 3 (Phase 5)**: Depends on Foundational completion and should follow User Story 2 so provider catalog behavior is validated against the final routing path.
- **Polish (Phase 6)**: Depends on all desired user stories being complete.

### User Story Dependencies

- **US1 (P1)**: First deliverable; no dependency on later stories.
- **US2 (P2)**: Builds on US1 payload transport and makes runtime routing safe for async/session reuse.
- **US3 (P3)**: Uses the routing and validation behavior from US1/US2 to activate the provider catalog in configuration.

### Parallel Opportunities

- T002 and T003 can run in parallel after T001.
- T005, T006, and T007 can run in parallel after T004 starts the shared contract set.
- T010 and T011 can run in parallel before US1 implementation.
- T016 and T017 can run in parallel before US2 implementation.
- T023 and T024 can run in parallel before US3 implementation.
- T026, T027, T028, and T029 can run in parallel after T025 defines the catalog pattern.
- T031 and T033 can run in parallel after implementation is complete.

---

## Parallel Example: User Story 1

```bash
Task: "Add payload-construction assertions in multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/service/DefaultLlmRunnerTest.java"
Task: "Add options-converter coverage for serialized ACP routing in multi_agent_ide_java_parent/multi_agent_ide/src/test/java/com/hayden/multiagentide/service/DefaultLlmRunnerTest.java"
```

---

## Parallel Example: User Story 3

```bash
Task: "Migrate Codex provider configuration to the provider catalog in multi_agent_ide_java_parent/multi_agent_ide/src/main/resources/application-codex.yml"
Task: "Migrate Claude profile configuration to the provider catalog in multi_agent_ide_java_parent/multi_agent_ide/src/main/resources/application-claude.yml"
Task: "Migrate Claude Ollama profile configuration to the provider catalog in multi_agent_ide_java_parent/multi_agent_ide/src/main/resources/application-claudellama.yml"
Task: "Migrate Claude OpenRouter profile configuration to the provider catalog in multi_agent_ide_java_parent/multi_agent_ide/src/main/resources/application-claudeopenrouter.yml"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup.
2. Complete Phase 2: Foundational contracts and provider catalog scaffolding.
3. Complete Phase 3: User Story 1.
4. Validate payload generation and converter behavior through the targeted tests in `DefaultLlmRunnerTest`.
5. Demo runtime provider/model payload generation before moving to session/cache isolation.

### Incremental Delivery

1. Deliver US1 to establish the new runtime payload path.
2. Deliver US2 to make runtime selection safe for async execution and session reuse.
3. Deliver US3 to switch existing profile configuration over to the provider catalog.
4. Finish with regression runs and log-safety cleanup.

### Parallel Team Strategy

1. One developer handles the foundational records/config refactor in `acp-cdc-ai`.
2. One developer handles US1 changes in `multi_agent_ide` and shared LLM option flow.
3. One developer handles profile-configuration migration once the provider catalog contract stabilizes.
4. Merge all work before final ACP regression runs from `multi_agent_ide_java_parent/gradlew`.

---

## Notes

- All tasks follow the required checklist format with IDs, optional `[P]`, and `[US#]` labels where applicable.
- `test_graph` tasks are intentionally omitted for this feature because the specification explicitly deferred `test_graph` work.
- The suggested MVP scope is **User Story 1**.
