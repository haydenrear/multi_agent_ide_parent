# Feature Specification: Runtime ACP Model Selection

**Feature Branch**: `001-multiple-models`  
**Created**: 2026-03-06  
**Status**: Draft  
**Input**: User description: "Allow ACP provider and model selection to be chosen at runtime per LLM call by encoding artifact key, model choice, provider choice, and extensible options into a structured ACP chat options payload instead of relying on per-profile ACP arguments."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Select ACP Runtime Per Call (Priority: P1)

Workflow authors need each LLM call to choose the ACP provider and target model at runtime so the same running application can route different calls to different ACP backends without switching application profiles.

**Why this priority**: This is the core business value of the feature. Without per-call runtime selection, teams must restart or reconfigure the application to compare providers, which slows experimentation and agent workflows.

**Independent Test**: Can be fully tested by issuing two otherwise similar LLM calls in one running application session and verifying each call is routed through its requested ACP provider and target model while preserving the correct conversation/session identity.

**Cucumber Test Tag**: `@multiple-models-runtime-selection`

**Acceptance Scenarios**:

1. **Given** a running application with more than one ACP provider configuration available, **When** an LLM call specifies provider A and model X, **Then** the call is executed through provider A using model X.
2. **Given** the same running application session, **When** a later LLM call specifies provider B and model Y, **Then** the call is executed through provider B using model Y without requiring a profile change or restart.
3. **Given** an LLM call omits an explicit provider choice, **When** the call is executed, **Then** the system uses the configured default ACP provider for that call.

---

### User Story 2 - Preserve Session Routing Metadata (Priority: P2)

Platform services need runtime ACP selection to preserve the session and artifact identity already used for chat continuity, event routing, and downstream message handling.

**Why this priority**: Provider switching is only useful if it keeps existing run/session attribution intact. Losing session identity would break history, event correlation, and tool behavior.

**Independent Test**: Can be fully tested by issuing a call with explicit runtime ACP selection and confirming the conversation continues in the correct session lineage while downstream components still resolve the same artifact/session identifier.

**Cucumber Test Tag**: `@multiple-models-session-routing`

**Acceptance Scenarios**:

1. **Given** a conversation already has an existing session identity, **When** an LLM call includes runtime ACP selection details, **Then** the same session identity is preserved for memory, event publishing, and downstream routing.
2. **Given** a call carries both session identity and runtime ACP selection details, **When** the chat request is processed asynchronously, **Then** all information needed to execute the call is available without relying on thread-local state.
3. **Given** downstream consumers inspect the call identity, **When** they receive events or assistant output for that call, **Then** they can still resolve the originating session correctly.

---

### User Story 3 - Reuse Structured ACP Configuration (Priority: P3)

Operators need ACP runtime configuration to come from a reusable catalog of named provider definitions so they can add or update provider-specific connection and execution settings without changing the call contract.

**Why this priority**: Runtime routing becomes maintainable only if provider definitions are centralized and reusable instead of hard-coded into one active profile at a time.

**Independent Test**: Can be fully tested by defining multiple ACP provider entries, selecting one of them at runtime, and confirming that its configured connection and execution settings are the ones applied for the call.

**Cucumber Test Tag**: `@multiple-models-provider-catalog`

**Acceptance Scenarios**:

1. **Given** multiple named ACP provider definitions are configured, **When** a call selects one provider by name, **Then** the system uses that provider definition for the ACP session it creates.
2. **Given** a selected provider definition contains the connection and execution settings required for that backend, **When** the call is executed, **Then** all configured settings for that provider are applied consistently.
3. **Given** a call references an unknown provider name, **When** runtime selection is resolved, **Then** the call fails with a clear error that identifies the missing provider choice.

---

### Edge Cases

- What happens when a call references an ACP provider name that is not configured? The call must fail before session startup with a clear validation error and must not silently fall back to an unintended provider.
- What happens when a call supplies malformed runtime ACP selection data? The call must be rejected as invalid input and must not create a partial or ambiguous ACP session.
- What happens when a call supplies a provider but no model? The system must use the requested provider and apply that provider's default model behavior if one is defined; otherwise it must fail with a clear error.
- What happens when a default ACP provider is not configured and a call omits provider selection? The call must fail with a clear error that a default provider is required.
- What happens when two calls in the same application process choose different providers concurrently? Each call must use its own runtime ACP selection without leaking provider or model settings across sessions.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow each LLM call to carry a structured ACP call payload that includes the conversation or artifact identity required for routing that call.
- **FR-002**: The structured ACP call payload MUST support an explicit ACP provider choice for the call.
- **FR-003**: The structured ACP call payload MUST support an explicit target model choice for the call.
- **FR-004**: The structured ACP call payload MUST support additional extensible call-level options without requiring a new call contract for each new option.
- **FR-005**: The system MUST use the structured ACP call payload, rather than active application profile selection, to decide which ACP provider configuration to use for a given call.
- **FR-006**: The system MUST preserve the current session or artifact identity semantics while introducing runtime ACP selection.
- **FR-007**: The system MUST make the complete call-routing information available across asynchronous execution boundaries without depending on thread-local storage.
- **FR-008**: The system MUST support a configured default ACP provider that is used when a call does not explicitly name a provider.
- **FR-009**: The system MUST fail fast with a clear error when neither an explicit provider nor a default provider can be resolved.
- **FR-010**: The system MUST resolve ACP provider settings from a named catalog of provider definitions instead of a single flat ACP configuration.
- **FR-011**: Each ACP provider definition MUST be able to supply the connection, execution, authentication, and environment settings required to use that backend.
- **FR-012**: The system MUST apply the full selected ACP provider definition when creating or reusing an ACP session for a call.
- **FR-013**: The system MUST keep provider definitions immutable for the duration of a call once routing has been resolved.
- **FR-014**: The system MUST surface a clear validation error when a call references an unknown ACP provider definition.
- **FR-015**: The system MUST surface a clear validation error when the structured ACP call payload cannot be parsed or is missing required routing fields.
- **FR-016**: The system MUST preserve compatibility for callers that only need to supply session identity and model choice by mapping those inputs into the new structured ACP call payload.
- **FR-017**: The system MUST allow downstream chat, memory, and event-routing components to recover the session or artifact identity from the structured ACP call payload.
- **FR-018**: The system MUST ensure concurrent calls can select different ACP providers and models without cross-call contamination.
- **FR-019**: The system MUST make provider selection observable in logs or diagnostics so operators can verify which ACP provider and model were used for each call.
- **FR-020**: The system MUST allow a provider definition to omit fields that are not applicable to that provider while still enforcing required fields for successful execution.

### Key Entities *(include if feature involves data)*

- **ACP Call Payload**: The structured per-call routing object that carries session identity, selected ACP provider, selected model, and extensible call options.
- **Session Identity**: The conversation or artifact identifier used to preserve memory, routing, and event correlation across a call.
- **ACP Provider Definition**: A named reusable configuration entry describing how to connect to one ACP backend for a call.
- **Default ACP Provider**: The configured provider name used when a call does not specify one explicitly.
- **Provider Catalog**: The collection of all named ACP provider definitions available to the application.
- **Call-Level Option**: Additional per-call metadata that may influence ACP execution without changing the provider catalog schema.

### Assumptions

- The existing session or artifact identity remains the canonical key for memory and event routing.
- Existing provider-specific profiles are transitional and may continue to exist temporarily while callers migrate to runtime selection.
- Operators are able to supply provider-specific secrets through existing secure configuration mechanisms.

### Dependencies

- The ACP runtime can create sessions from a selected provider definition at call time.
- Callers can pass structured routing data through the current LLM invocation path.
- Existing logging and diagnostics surfaces can expose selected provider and model details for troubleshooting.

### Out of Scope

- Redesigning non-ACP model providers.
- Defining UI workflows for choosing providers.
- Adding `test_graph` artifacts in this spec iteration.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In a single running application instance, operators can successfully execute calls against at least two different ACP providers without restarting or changing the active profile.
- **SC-002**: 100% of calls that specify a valid ACP provider and valid model route to the requested provider/model combination in verification testing.
- **SC-003**: 100% of calls that omit provider selection but rely on the configured default provider execute successfully when that default exists.
- **SC-004**: 100% of calls with an unknown provider or invalid routing payload fail with a clear, actionable validation error before provider execution begins.
- **SC-005**: Concurrent verification runs show no provider-selection leakage between calls that choose different ACP providers in the same application process.

## Notes on Gherkin Feature Files For Test Graph

Per user instruction for this feature, `test_graph` feature files are intentionally not added as part of this specification cycle. The acceptance scenarios and cucumber tags are still defined so they can be translated into integration tests later if needed.
