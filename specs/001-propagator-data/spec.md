# Feature Specification: Controller Data Propagators and Transformers

**Feature Branch**: `001-propagator-data`  
**Created**: 2026-03-11  
**Status**: Draft  
**Input**: User description: "Split propagators and transformers into separate capabilities. Propagators attach to action requests and responses, with action responses as the primary propagation trigger. Transformers attach to controller endpoints and can replace JSON-shaped endpoint outputs with string responses. Both capabilities reuse the existing layer model and executable-tool model, emit audit events, and support controller acknowledgement through the permission gate when propagation is informational but still must be acknowledged."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Propagate important action responses to the controller (Priority: P1)

As a controller operator, I need important action responses to be propagated to the controller as soon as they are produced, so the controller can see, acknowledge, approve, or escalate important workflow information before dependent work continues.

**Why this priority**: The main failure mode described for the current workflow is that important agent output is missed, cut off, or not surfaced clearly enough to the controller.

**Independent Test**: Can be fully tested by registering a propagator on an action response, triggering the action, and verifying that the controller receives a propagation item or propagation acknowledgement request with the correct source context and resolution state.

**Cucumber Test Tag**: `@propagator_action_response`

**Acceptance Scenarios**:

1. **Given** an action response matches a registered propagator, **When** the action completes, **Then** the system emits a propagation event and sends the propagated information to the controller.
2. **Given** a propagated action response is informational but must still be acknowledged, **When** the controller acknowledges it through the permission gate flow, **Then** the system records the acknowledgement and emits a `PROPAGATION_ACKNOWLEDGED` outcome.
3. **Given** a propagated action response requires approval or escalation, **When** downstream work attempts to continue before resolution, **Then** the system pauses the dependent work until the item is resolved.

---

### User Story 2 - Attach propagators to action requests and responses (Priority: P1)

As a platform operator, I need propagators to be attachable to both action requests and action responses, so I can surface important inputs before work starts and important outputs after work completes.

**Why this priority**: Action responses are the primary propagation mechanism, but request-time propagation is still needed for pre-execution checks and controller visibility.

**Independent Test**: Can be fully tested by registering one propagator on an action request and one on an action response, then verifying that each is invoked only at its intended stage.

**Cucumber Test Tag**: `@propagator_action_binding`

**Acceptance Scenarios**:

1. **Given** a propagator is registered for an action request, **When** the action request is issued, **Then** the propagator runs before the action executes and records a propagation event.
2. **Given** a propagator is registered for an action response, **When** the action result is produced, **Then** the propagator runs after the response is created and records a propagation event.
3. **Given** no propagator is registered for the current action stage, **When** the action request or response is processed, **Then** the system skips propagation for that stage without affecting normal action execution.

---

### User Story 3 - Transform controller endpoint outputs into strings (Priority: P1)

As a controller consumer, I need selected controller endpoints to return transformed string outputs instead of the original JSON object shape, so downstream controller-facing workflows can consume a purpose-built textual representation.

**Why this priority**: This is the primary transformation mechanism the feature is meant to introduce for controller endpoints.

**Independent Test**: Can be fully tested by attaching a transformer to a controller endpoint, invoking the endpoint, and verifying that the endpoint returns the transformed string while the original and transformed values are both captured for audit.

**Cucumber Test Tag**: `@transformer_controller_endpoint`

**Acceptance Scenarios**:

1. **Given** a controller endpoint has a registered transformer, **When** the endpoint returns its normal result, **Then** the transformer converts that result into the configured string output returned by the endpoint.
2. **Given** a controller endpoint response is transformed, **When** the request completes, **Then** the system emits a transformation event containing both the original payload and the transformed string.
3. **Given** a transformer fails while handling a controller endpoint response, **When** the endpoint request completes, **Then** the controller flow still succeeds and the system records the failure for review.

---

### User Story 4 - Compare propagation and transformation outcomes over time (Priority: P2)

As a platform owner, I need propagation and transformation runs captured as separate but comparable audit records, so I can measure which propagation or transformation strategies improve controller effectiveness.

**Why this priority**: The team wants to compare how different propagation and transformation strategies affect downstream controller behavior and model performance.

**Independent Test**: Can be fully tested by running the same source through propagators and transformers, then verifying that before/after payloads, decisions, acknowledgements, and outcomes are queryable later.

**Cucumber Test Tag**: `@propagation_transformation_audit`

**Acceptance Scenarios**:

1. **Given** a propagator or transformer runs, **When** processing completes, **Then** the system stores the original payload, transformed payload if any, execution metadata, and final outcome together in an audit record.
2. **Given** repeated runs happen for the same source checkpoint, **When** operators review history, **Then** they can compare outcomes by layer, rule, action stage, and final resolution.

### Edge Cases

- The same action response triggers the same propagator repeatedly in a short window and risks flooding the controller with duplicate acknowledgements or review items.
- An action request propagator runs successfully, but the matching action response propagator later fails.
- A transformer is registered for a controller endpoint that normally returns a large structured object, and the transformed string becomes too large for its consumer.
- A propagation item waits for acknowledgement or approval after the originating workflow has already entered a terminal state.
- A transformer fails after the controller endpoint has already produced the original response object.
- A propagated action response is informative enough to surface but not important enough to require human approval.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST reuse the existing `Layer` hierarchy for both propagators and transformers and MUST NOT introduce a second attachment hierarchy.
- **FR-002**: The system MUST define `PropagatorLayerBinding` and `TransformerLayerBinding` as separate binding models, each persisted in the same JSON-backed registration style already used for filters.
- **FR-003**: The system MUST persist propagator registrations and transformer registrations separately so they can be activated, deactivated, queried, and audited independently.
- **FR-004**: The system MUST allow propagators to be registered on action requests and action responses for existing action layers.
- **FR-005**: The system MUST treat action responses as the primary propagation trigger for the first release.
- **FR-006**: The system MUST allow transformers to be registered on controller endpoints that return outward-facing controller responses.
- **FR-007**: The system MUST split propagator execution from transformer execution by providing `PropagationExecutionService` and `TransformerExecutionService` as separate runtime services.
- **FR-008**: The system MUST reuse the existing `ExecutableTool` abstraction for both propagators and transformers.
- **FR-009**: The system MUST support `PythonExecutor` and AI-based executor variants for both propagators and transformers.
- **FR-010**: The system MUST provide separate AI executor types for propagators and transformers so request/result contracts remain specific to each capability.
- **FR-011**: The system MUST allow a propagator to classify its outcome as informational, acknowledgement-required, approval-required, or escalation-required.
- **FR-012**: The system MUST route acknowledgement-required propagation through the permission-gate flow and MUST require the controller to acknowledge the item before the propagation is considered resolved.
- **FR-013**: The system MUST emit a propagation event whenever a propagator is invoked and MUST emit a transformation event whenever a transformer is invoked.
- **FR-014**: The system MUST include `PROPAGATION_ACKNOWLEDGED` as a distinct propagation outcome when the controller acknowledges a propagated item.
- **FR-015**: The system MUST create a propagation item only when the propagator outcome requires acknowledgement, approval, or escalation.
- **FR-016**: The system MUST let acknowledgement-required, approval-required, and escalation-required propagation items gate dependent downstream work until resolution.
- **FR-017**: The system MUST allow controller endpoints with registered transformers to return transformed string responses instead of the original JSON object shape.
- **FR-018**: The system MUST capture the original payload and transformed payload together whenever a propagator or transformer changes the outward representation.
- **FR-019**: The system MUST treat propagation and transformation failures as non-fatal to the originating controller or action flow and MUST record the failure reason for later review.
- **FR-020**: The system MUST correlate repeated propagation runs from the same source so duplicate acknowledgement or review noise can be suppressed without losing audit history.
- **FR-021**: The system MUST preserve queryable audit history for propagation events, transformation events, propagation items, acknowledgements, approvals, rejections, dismissals, and feedback.

### Key Entities *(include if feature involves data)*

- **Propagator Rule**: A saved rule that runs on action requests or action responses to surface important information to the controller.
- **Propagator Layer Binding**: The saved attachment that determines which action request or action response surfaces invoke a propagator.
- **Transformer Rule**: A saved rule that transforms controller endpoint outputs into string responses.
- **Transformer Layer Binding**: The saved attachment that determines which controller endpoint responses invoke a transformer.
- **Propagation Item**: A reviewable or acknowledgeable unit created when a propagator requires controller acknowledgement, approval, or escalation.
- **Propagation Event**: The audit record capturing before/after payloads and outcomes for a propagation run.
- **Transformation Event**: The audit record capturing before/after payloads and outcomes for a transformer run.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: At least 95% of action-response propagations that require acknowledgement, approval, or escalation are surfaced to the controller within 30 seconds of the action response being produced.
- **SC-002**: 100% of controller acknowledgements for acknowledgement-required propagations produce a recorded `PROPAGATION_ACKNOWLEDGED` outcome.
- **SC-003**: 100% of controller endpoints with active transformers return a string response surface while also producing a before/after transformation audit record.
- **SC-004**: 100% of propagation-enabled and transformation-enabled runs retain a queryable audit record containing the original payload, resulting payload, and final outcome.
- **SC-005**: In validation runs on critical controller paths, missed or truncated important action-response handoffs are reduced by at least 80% compared with the current controller flow.

## Implementation Constraints

- Propagators and transformers both reuse the existing `Layer` model and existing layer resolution flow.
- Propagators and transformers are separate concepts with separate bindings, separate execution services, and separate event streams.
- Propagators attach to action requests and action responses; action responses are the primary propagation surface for the first release.
- Transformers attach to controller endpoint responses and are the primary mechanism for returning transformed strings instead of JSON objects.
- Both propagators and transformers reuse `ExecutableTool` and support `PythonExecutor` plus dedicated AI executor variants in the first release.
- Propagation acknowledgement flows through the permission gate and must produce a distinct acknowledgement outcome.

## Assumptions

- The existing permission-gate flow can be extended to carry acknowledgement-required propagation items as well as approval-like decisions.
- The existing layer-resolution and registration infrastructure can be reused instead of creating a second hierarchy.
- The first release focuses on action request/response propagation and controller endpoint transformation rather than every possible prompt or event surface.
- Per user request, `test_graph` feature-file generation remains deferred for this feature.

## Notes on Gherkin Feature Files For Test Graph

Per user request, `test_graph` feature files are not being created for this specification. The Cucumber tags above are reserved so automated integration coverage can be added later without changing the user-facing contract described here.
