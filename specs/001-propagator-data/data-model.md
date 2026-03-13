# Data Model: Controller Data Propagators and Transformers

**Feature**: `001-propagator-data`  
**Date**: 2026-03-11

## Type System Overview

- `Layer`: existing sealed shared attachment model reused by both subsystems
- `ExecutableTool<I, O, CTX extends FilterContext>`: existing executor abstraction reused by both subsystems
- `Propagator<I, O, CTX extends FilterContext>`: new sealed propagation model
  - `TextPropagator`
  - `AiTextPropagator`
- `Transformer<I, O, CTX extends FilterContext>`: new sealed transformation model
  - `TextTransformer`
  - `AiTextTransformer`
- `PropagatorLayerBinding`: action-stage attachment definition
- `TransformerLayerBinding`: controller-endpoint attachment definition
- `PropagatorRegistrationEntity`: persisted propagator registration
- `TransformerRegistrationEntity`: persisted transformer registration
- `PropagationContext`: propagation-specific context family
  - `DefaultPropagationContext`
  - `AiPropagatorContext`
- `TransformationContext`: transformation-specific context family
  - `ControllerEndpointTransformationContext`
  - `AiTransformerContext`
- `PropagationOutput`: normalized propagator output
- `TransformationOutput`: normalized transformer output
- `PropagationItem`: acknowledgeable or reviewable unit emitted by a propagator when gating is required
- `PropagationEvent`: audit record for every propagator invocation
- `TransformationEvent`: audit record for every transformer invocation
- `PropagationRecordEntity`: persisted propagation-event storage
- `TransformationRecordEntity`: persisted transformation-event storage
- `AiPropagatorTool`: AI executor for propagators
- `AiTransformerTool`: AI executor for transformers
- `AgentModels.AiPropagatorRequest` / `AgentModels.AiPropagatorResult`
- `AgentModels.AiTransformerRequest` / `AgentModels.AiTransformerResult`

## First-Release Scope Rules

- Propagators attach to action requests and action responses, with action responses as the primary propagation trigger.
- Transformers attach to controller endpoint responses and may replace the outward endpoint payload with a transformed string.
- The first release supports `PythonExecutor` and dedicated AI executors for both propagators and transformers.
- Both subsystems always retain before/after audit data when a payload is changed or acknowledged.

## Core Propagator Models

### 1. Propagator<I, O, CTX> (sealed)

**Purpose**: Shared execution definition for action-stage propagation rules.

| Field | Type | Description | Validation |
|---|---|---|---|
| id | String | Unique propagator identifier | Not blank, unique |
| name | String | Human-readable propagator name | Not blank |
| description | String | Operator-facing behavior description | Not blank |
| sourcePath | String | Source location for saved config or script | Not blank |
| executor | `ExecutableTool<I, O, CTX>` | Bound execution strategy | Not null |
| status | Enum(`ACTIVE`,`INACTIVE`) | Registration state | Not null |
| priority | int | Deterministic ordering within a layer | `>= 0` |
| propagationMode | Enum(`INFORMATIONAL`,`ACKNOWLEDGEMENT_REQUIRED`,`APPROVAL_REQUIRED`,`ESCALATION_REQUIRED`) | Default handling mode for emitted output | Not null |
| createdAt | Instant | Creation timestamp | Not null |
| updatedAt | Instant | Last update timestamp | Not null |

#### 1a. TextPropagator

**Purpose**: String-surface propagator driven by `PythonExecutor`.

**Type binding**: `Propagator<String, PropagationOutput, DefaultPropagationContext>`

#### 1b. AiTextPropagator

**Purpose**: AI-backed propagator that can summarize, escalate, or require acknowledgement/approval.

**Type binding**: `Propagator<AgentModels.AiPropagatorRequest, AgentModels.AiPropagatorResult, AiPropagatorContext>`

### 2. PropagatorLayerBinding

**Purpose**: Saved attachment of a propagator to an existing action-layer surface.

| Field | Type | Description | Validation |
|---|---|---|---|
| layerId | String | Existing shared action-layer identifier | Must resolve to a known layer |
| enabled | boolean | Whether this binding is active | Not null |
| includeDescendants | boolean | Whether descendant layers inherit applicability | Not null |
| isInheritable | boolean | Whether child contexts may inherit the binding | Not null |
| isPropagatedToParent | boolean | Whether the binding can be copied upward when propagated | Not null |
| matcherKey | Enum(`NAME`,`TEXT`) | Which source field to match | Not null |
| matcherType | Enum(`EQUALS`,`REGEX`,`CONTAINS`) | Matching strategy | Not null |
| matcherText | String | Match expression | Optional but required when matcherType is not implicit |
| matchOn | Enum(`ACTION_REQUEST`,`ACTION_RESPONSE`) | Action stage this binding evaluates | Not null |
| updatedBy | String | Actor that last changed the binding | Not blank |
| updatedAt | Instant | Last binding update time | Not null |

### 3. PropagatorRegistrationEntity

**Purpose**: Persisted application-layer registration for a propagator and its bindings.

| Field | Type | Description |
|---|---|---|
| registrationId | String | Stable propagator registration id |
| registeredBy | String | Actor or subsystem that created the propagator |
| status | String | `ACTIVE` or `INACTIVE` |
| propagatorKind | String | `TEXT` or `AI_TEXT` |
| isInheritable | boolean | Default inheritance flag |
| isPropagatedToParent | boolean | Default upward-propagation flag |
| propagatorJson | String (TEXT) | Serialized propagator definition |
| layerBindingsJson | String (TEXT) | Serialized binding list |
| activatedAt | Instant | Activation timestamp |
| deactivatedAt | Instant | Deactivation timestamp |

## Core Transformer Models

### 4. Transformer<I, O, CTX> (sealed)

**Purpose**: Shared execution definition for controller-endpoint transformation rules.

| Field | Type | Description | Validation |
|---|---|---|---|
| id | String | Unique transformer identifier | Not blank, unique |
| name | String | Human-readable transformer name | Not blank |
| description | String | Operator-facing behavior description | Not blank |
| sourcePath | String | Source location for saved config or script | Not blank |
| executor | `ExecutableTool<I, O, CTX>` | Bound execution strategy | Not null |
| status | Enum(`ACTIVE`,`INACTIVE`) | Registration state | Not null |
| priority | int | Deterministic ordering within a layer | `>= 0` |
| replaceEndpointResponse | boolean | Whether the transformed string replaces the endpoint's outward response | Not null |
| createdAt | Instant | Creation timestamp | Not null |
| updatedAt | Instant | Last update timestamp | Not null |

#### 4a. TextTransformer

**Purpose**: String transformer driven by `PythonExecutor`.

**Type binding**: `Transformer<String, TransformationOutput, ControllerEndpointTransformationContext>`

#### 4b. AiTextTransformer

**Purpose**: AI-backed transformer that produces the final controller-endpoint string response.

**Type binding**: `Transformer<AgentModels.AiTransformerRequest, AgentModels.AiTransformerResult, AiTransformerContext>`

### 5. TransformerLayerBinding

**Purpose**: Saved attachment of a transformer to a controller-endpoint response surface.

| Field | Type | Description | Validation |
|---|---|---|---|
| layerId | String | Existing controller layer identifier | Must resolve to a known layer |
| enabled | boolean | Whether this binding is active | Not null |
| includeDescendants | boolean | Whether descendant controller layers inherit applicability | Not null |
| isInheritable | boolean | Whether child contexts may inherit the binding | Not null |
| isPropagatedToParent | boolean | Whether the binding can be copied upward when propagated | Not null |
| matcherKey | Enum(`NAME`,`TEXT`) | Which source field to match | Not null |
| matcherType | Enum(`EQUALS`,`REGEX`,`CONTAINS`) | Matching strategy | Not null |
| matcherText | String | Match expression | Optional but required when matcherType is not implicit |
| matchOn | Enum(`CONTROLLER_ENDPOINT_RESPONSE`) | Endpoint response surface this binding evaluates | Not null |
| updatedBy | String | Actor that last changed the binding | Not blank |
| updatedAt | Instant | Last binding update time | Not null |

### 6. TransformerRegistrationEntity

**Purpose**: Persisted application-layer registration for a transformer and its bindings.

| Field | Type | Description |
|---|---|---|
| registrationId | String | Stable transformer registration id |
| registeredBy | String | Actor or subsystem that created the transformer |
| status | String | `ACTIVE` or `INACTIVE` |
| transformerKind | String | `TEXT` or `AI_TEXT` |
| isInheritable | boolean | Default inheritance flag |
| isPropagatedToParent | boolean | Default upward-propagation flag |
| transformerJson | String (TEXT) | Serialized transformer definition |
| layerBindingsJson | String (TEXT) | Serialized binding list |
| activatedAt | Instant | Activation timestamp |
| deactivatedAt | Instant | Deactivation timestamp |

## Execution Context Models

### 7. DefaultPropagationContext

**Purpose**: Execution context for action request/response propagation.

| Field | Type | Description |
|---|---|---|
| layerId | String | Resolved action layer id |
| key | ArtifactKey | Workflow/action scope key |
| actionStage | Enum(`ACTION_REQUEST`,`ACTION_RESPONSE`) | Action stage currently being processed |
| sourceName | String | Human-readable action name |
| sourceNodeId | String | Optional node id tied to the action |
| originalPayload | Object | Original structured payload prior to serialization |
| serializedPayload | String | Normalized string payload sent to the executor |
| objectMapper | ObjectMapper | Shared serializer |
| filterConfigProperties | FilterConfigProperties | Existing runtime config access |

### 8. AiPropagatorContext

**Purpose**: AI execution context parallel to `AiFilterContext`.

| Field | Type | Description |
|---|---|---|
| propagationContext | DefaultPropagationContext | Delegated base context |
| templateName | String | AI template name |
| promptContext | PromptContext | Decorated prompt context |
| model | Map<String, Object> | Template model data |
| toolContext | ToolContext | Decorated tool context |
| responseClass | Class<AiPropagatorResult> | Expected AI result type |
| context | OperationContext | Workflow operation context |

### 9. ControllerEndpointTransformationContext

**Purpose**: Execution context for controller-endpoint response transformation.

| Field | Type | Description |
|---|---|---|
| layerId | String | Resolved controller layer id |
| key | ArtifactKey | Controller/session scope key if available |
| controllerId | String | Controller identifier |
| endpointId | String | Endpoint/action identifier |
| originalPayload | Object | Pre-serialization controller payload |
| serializedPayload | String | Baseline serialized value passed to the transformer |
| objectMapper | ObjectMapper | Shared serializer |
| filterConfigProperties | FilterConfigProperties | Existing runtime config access |

### 10. AiTransformerContext

**Purpose**: AI execution context for controller-endpoint transformation.

| Field | Type | Description |
|---|---|---|
| transformationContext | ControllerEndpointTransformationContext | Delegated base context |
| templateName | String | AI template name |
| promptContext | PromptContext | Decorated prompt context |
| model | Map<String, Object> | Template model data |
| toolContext | ToolContext | Decorated tool context |
| responseClass | Class<AiTransformerResult> | Expected AI result type |
| context | OperationContext | Workflow or controller operation context |

## Output, Item, and Event Models

### 11. PropagationOutput

**Purpose**: Normalized output from a propagator.

NEEDS UPDATE

| Field | Type | Description |
|---|---|---|
| propagatedText | String | Primary propagated text |
| summaryText | String | Short controller-facing summary |
| propagationModeOverride | Enum(`INFORMATIONAL`,`ACKNOWLEDGEMENT_REQUIRED`,`APPROVAL_REQUIRED`,`ESCALATION_REQUIRED`) | Optional mode override |
| createItem | boolean | Whether a `PropagationItem` should be created |
| blockDownstream | boolean | Whether dependent work must pause |
| metadata | Map<String,String> | Execution metadata |
| errorMessage | String | Partial/degraded execution detail |

### 12. TransformationOutput

**Purpose**: Normalized output from a transformer.

| Field | Type | Description |
|---|---|---|
| transformedText | String | Final controller-endpoint string response |
| contentType | String | Optional outward content-type hint |
| metadata | Map<String,String> | Execution metadata |
| errorMessage | String | Partial/degraded execution detail |

### 13. PropagationItem

**Purpose**: Reviewable or acknowledgeable item created when a propagator gates downstream work.

| Field | Type | Description |
|---|---|---|
| itemId | String | Stable item id |
| registrationId | String | Source propagator registration |
| layerId | String | Resolved layer id |
| sourceNodeId | String | Node/workflow origin if available |
| sourceType | Enum(`ACTION_REQUEST`,`ACTION_RESPONSE`) | Action stage that produced the item |
| status | Enum(`CREATED`,`WAITING_REVIEW`,`RESOLVED`,`FAILED`,`CANCELLED`) | Item lifecycle status |
| resolutionType | Enum(`ACKNOWLEDGED`,`APPROVED`,`REJECTED`,`DISMISSED`,`FEEDBACK`) | Controller/reviewer outcome |
| propagationMode | Enum(`INFORMATIONAL`,`ACKNOWLEDGEMENT_REQUIRED`,`APPROVAL_REQUIRED`,`ESCALATION_REQUIRED`) | Effective handling mode |
| summaryText | String | Controller-facing summary |
| beforePayloadRef | String | Reference to the original payload |
| afterPayloadRef | String | Reference to the propagated payload |
| correlationKey | String | Duplicate-suppression key |
| createdAt | Instant | Creation timestamp |
| resolvedAt | Instant | Resolution timestamp |
| resolutionNotes | String | Controller or reviewer notes |

**State transitions**:
- `CREATED -> WAITING_REVIEW` when the item is emitted to the controller.
- `WAITING_REVIEW -> RESOLVED` with `ACKNOWLEDGED`, `APPROVED`, `REJECTED`, `DISMISSED`, or `FEEDBACK`.
- `CREATED|WAITING_REVIEW -> FAILED` when the item cannot be stored or routed.
- `CREATED|WAITING_REVIEW -> CANCELLED` when the originating workflow becomes terminal and the item is no longer actionable.

### 14. PropagationEvent

**Purpose**: Auditable record for every propagator invocation.

| Field | Type | Description |
|---|---|---|
| eventId | String | Stable propagation event id |
| registrationId | String | Source propagator registration |
| layerId | String | Resolved layer id |
| sourceNodeId | String | Workflow/action origin node |
| sourceType | Enum(`ACTION_REQUEST`,`ACTION_RESPONSE`) | Action stage |
| action | Enum(`PASSTHROUGH`,`TRANSFORMED`,`ITEM_CREATED`,`PROPAGATION_ACKNOWLEDGED`,`DUPLICATE_ITEM_SUPPRESSED`,`FAILED`) | Result classification |
| beforePayload | String | Serialized original payload |
| afterPayload | String | Serialized propagated payload |
| summaryText | String | Summary emitted for controller/reviewers |
| itemId | String | Related `PropagationItem`, if any |
| correlationKey | String | Duplicate-correlation key |
| descriptorJson | String | Serialized executor/propagator descriptor |
| error | String | Execution or routing failure detail |
| createdAt | Instant | Event timestamp |

### 15. TransformationEvent

**Purpose**: Auditable record for every transformer invocation.

| Field | Type | Description |
|---|---|---|
| eventId | String | Stable transformation event id |
| registrationId | String | Source transformer registration |
| layerId | String | Resolved layer id |
| controllerId | String | Controller identifier |
| endpointId | String | Endpoint/action identifier |
| action | Enum(`PASSTHROUGH`,`TRANSFORMED`,`FAILED`) | Result classification |
| beforePayload | String | Serialized original payload |
| afterPayload | String | Final transformed string |
| descriptorJson | String | Serialized executor/transformer descriptor |
| error | String | Execution or mapping failure detail |
| createdAt | Instant | Event timestamp |

### 16. PropagationRecordEntity

**Purpose**: Query-optimized persisted form of `PropagationEvent`.

### 17. TransformationRecordEntity

**Purpose**: Query-optimized persisted form of `TransformationEvent`.

## AI Contracts

### 18. AiPropagatorTool

**Purpose**: AI executor specialized for propagation outcomes.

| Field | Type | Description |
|---|---|---|
| modelRef | String | Primary model reference |
| promptTemplate | String | Propagation template |
| registrarPrompt | String | Optional registration-time guidance |
| maxTokens | int | Response budget |
| outputSchema | Object | Expected result schema |
| sessionMode | Enum(`PER_INVOCATION`,`SAME_SESSION_FOR_ALL`,`SAME_SESSION_FOR_ACTION`,`SAME_SESSION_FOR_AGENT`) | Session reuse strategy |
| sessionKeyOverride | String | Optional explicit session key |
| requestModelType | String | Request model hint |
| resultModelType | String | Result model hint |
| includeAgentDecorators | boolean | Whether workflow decorators should be applied |
| controllerModelRef | String | Optional controller/arbitration model |
| controllerPromptTemplate | String | Optional controller prompt template |
| responseMode | String | Output interpretation mode |
| timeoutMs | int | Timeout in milliseconds |
| configVersion | String | Stored config version |

### 19. AiTransformerTool

**Purpose**: AI executor specialized for controller-endpoint transformation outcomes.

| Field | Type | Description |
|---|---|---|
| modelRef | String | Primary model reference |
| promptTemplate | String | Transformation template |
| registrarPrompt | String | Optional registration-time guidance |
| maxTokens | int | Response budget |
| outputSchema | Object | Expected result schema |
| sessionMode | Enum(`PER_INVOCATION`,`SAME_SESSION_FOR_ALL`,`SAME_SESSION_FOR_ACTION`,`SAME_SESSION_FOR_AGENT`) | Session reuse strategy |
| sessionKeyOverride | String | Optional explicit session key |
| requestModelType | String | Request model hint |
| resultModelType | String | Result model hint |
| includeAgentDecorators | boolean | Whether workflow decorators should be applied |
| controllerModelRef | String | Optional controller/arbitration model |
| controllerPromptTemplate | String | Optional controller prompt template |
| responseMode | String | Output interpretation mode |
| timeoutMs | int | Timeout in milliseconds |
| configVersion | String | Stored config version |

### 20. AgentModels.AiPropagatorRequest / Result

**Request fields**: `contextId`, `worktreeContext`, `input`, `sourceType`, `propagationMode`, `originalPayload`, `desiredControllerAudience`  
**Result fields**: `contextId`, `successful`, `propagatedOutput`, `summaryText`, `createItem`, `blockDownstream`, `propagationModeOverride`, `metadata`, `errorMessage`, `worktreeContext`

### 21. AgentModels.AiTransformerRequest / Result

**Request fields**: `contextId`, `worktreeContext`, `input`, `controllerId`, `endpointId`, `originalPayload`, `desiredOutputFormat`  
**Result fields**: `contextId`, `successful`, `transformedOutput`, `contentType`, `metadata`, `errorMessage`, `worktreeContext`

## Relationships Summary

- `Layer` 1..* `<-` `PropagatorLayerBinding`
- `Layer` 1..* `<-` `TransformerLayerBinding`
- `Propagator` 1..* `->` `PropagatorLayerBinding`
- `Transformer` 1..* `->` `TransformerLayerBinding`
- `PropagatorRegistrationEntity` 1..* `->` `PropagationEvent`
- `TransformerRegistrationEntity` 1..* `->` `TransformationEvent`
- `PropagationEvent` 0..1 `->` `PropagationItem`

## Validation Rules

- A propagator registration must bind only to valid action-layer ids and only to `ACTION_REQUEST` or `ACTION_RESPONSE` surfaces.
- A transformer registration must bind only to valid controller-endpoint layer ids and only to `CONTROLLER_ENDPOINT_RESPONSE` surfaces.
- The first release supports `PythonExecutor` and the dedicated AI executor for each subsystem; other executor variants remain out of scope.
- Acknowledgement-required, approval-required, and escalation-required propagation outcomes must create a `PropagationItem`.
- `PROPAGATION_ACKNOWLEDGED` may only be emitted after the controller resolves an acknowledgement-required item through the permission-gate flow.
- A transformed controller endpoint response must always have both before and after audit payloads recorded.
