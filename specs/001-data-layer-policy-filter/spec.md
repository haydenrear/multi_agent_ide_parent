# Feature Specification: Layered Data Policy Filtering

**Feature Branch**: `[001-data-layer-policy-filter]`  
**Created**: 2026-02-26  
**Status**: Draft  
**Input**: User description: "Introduce layered policy-based filtration at controller and agent abstraction levels with persistent, polyglot executable filters that can drop/replace/summarize text and events, support dynamic registration and deregistration per agent, and maintain recent filter history."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Discover Active Layer Policies (Priority: P1)

As an agent operating at a specific layer, I need to see which data filtering policies are currently active, including a human-readable description and source path for each policy.

**Why this priority**: Agents cannot make reliable filtering decisions without visibility into active policy behavior and provenance.

**Independent Test**: Can be fully tested by activating policies at both controller and agent layers and verifying discovery output lists only active policies in the selected layer with description and source path fields.

**Cucumber Test Tag**: `@policy-discovery`

**Acceptance Scenarios**:

1. **Given** multiple policies are registered across layers, **When** an agent queries active policies for its current layer, **Then** only active policies for that layer are returned with description and source path.
2. **Given** a policy is deactivated, **When** active policy discovery is queried, **Then** the deactivated policy is not included in the active list.

---

### User Story 2 - Register and Deactivate Layer Policies (Priority: P1)

As an agent operating at a layer, I need to register new data filtering policies and deactivate existing policies through a registrar endpoint that validates policy schema before saving.

**Why this priority**: Dynamic policy control is required to adapt filtering behavior during real workflows without redeploying.

**Independent Test**: Can be fully tested by submitting valid and invalid policy registrations for supported executor types (binary, Java function, python, AI) across object and path filters, verifying validation results, then deactivating a policy and verifying it no longer applies.

**Cucumber Test Tag**: `@policy-registration`

**Acceptance Scenarios**:

1. **Given** an agent submits a valid policy definition with a supported filter kind and executor type, **When** the registrar endpoint validates and stores the policy, **Then** the policy becomes discoverable as active for the targeted layer scope.
2. **Given** an agent submits an invalid policy definition, **When** validation fails, **Then** the policy is rejected with clear validation feedback and is not activated.
3. **Given** an active policy exists, **When** the agent deactivates it through the registrar endpoint, **Then** the policy status changes to inactive and it is no longer applied in filtering execution.

---

### User Story 3 - Inspect Filtered Data by Active Policy (Priority: P2)

As an agent operating at a layer, I need to inspect filtered output tied to a particular active policy so I can verify what data that policy removed, replaced, or transformed.

**Why this priority**: Policy transparency is required to debug filtering behavior and avoid hidden data-loss or distortion.

**Independent Test**: Can be fully tested by running content through at least two active policies, selecting one policy in a filtered-output view, and verifying returned records include only data decisions caused by that policy.

**Cucumber Test Tag**: `@policy-output-inspection`

**Acceptance Scenarios**:

1. **Given** active policies are filtering live data, **When** an agent requests filtered data for one active policy, **Then** the response includes only outputs and decisions associated with that policy.
2. **Given** a policy is inactive, **When** an agent requests filtered data for that policy, **Then** the system returns no active filtered output and indicates the policy is inactive.

---

### Edge Cases

- What happens when a policy executable fails or times out while evaluating content?
- How does the system behave when multiple policies target the same content and produce conflicting outcomes?
- What happens when a persisted policy references an executable path that is no longer available?
- How does filtering behave when incoming payloads are malformed, partially structured, or much larger than normal?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST support policy application at two abstraction layers: controller and agent.
- **FR-002**: The system MUST provide active policy discovery for a selected layer.
- **FR-003**: Each discovered active policy MUST include a policy description and source path.
- **FR-004**: Policy registration MUST support binding one policy to many layers (usually one), with layer-level enable/disable control.
- **FR-005**: The system MUST provide a registrar interface that accepts policy registration requests from authorized actors.
- **FR-006**: The registrar MUST validate incoming policy definitions before persistence and activation.
- **FR-007**: `Filter` MUST be modeled as a sealed interface with concrete implementations `ObjectSerializerFilter` and `PathFilter`.
- **FR-008**: The execution mechanism MUST be modeled as an `ExecutableTool` mix-in (sealed interface) with concrete implementations `BinaryExecutor`, `JavaFunctionExecutor`, `PythonExecutor`, and `AiFilterTool`.
- **FR-008a**: Path targets MUST be modeled as a sealed `Path` interface with concrete record implementations `RegexPath`, `MarkdownPath`, and `JsonPath`.
- **FR-008b**: Path execution MUST use a sealed `Interpreter` interface with concrete implementations `RegexInterpreter`, `MarkdownPathInterpreter`, and `JsonPathInterpreter`.
- **FR-008c**: Filter instructions MUST be modeled as sealed instruction records `Replace`, `Set`, `Remove`, `ReplaceIfMatch`, and `RemoveIfMatch`.
- **FR-009**: Invalid policy definitions MUST be rejected with validation feedback that identifies the failed schema constraints.
- **FR-010**: Authorized actors MUST be able to deactivate active policies without deleting policy history.
- **FR-011**: The system MUST persist policy definitions and activation state across sessions and service restarts.
- **FR-012**: The system MUST execute applicable policies in a deterministic order for a given scope.
- **FR-013**: `Layer` MUST be modeled as a sealed interface to represent granular execution boundaries, including agent actions and controller-polled UI event scopes.
- **FR-014**: `Layer` MUST provide a `matches(LayerCtx)` operation where `LayerCtx` is a sealed interface with `AgentLayerCtx`, `ActionLayerCtx`, and `ControllerLayerCtx`.
- **FR-014a**: Layer data MUST be represented as a hierarchical structure so policies can be enabled or disabled at a node or for the full subtree beneath that node.
- **FR-014b**: `Layer` and policy mutation payloads MUST include `isInheritable`, defaulting to `false`.
- **FR-014c**: `Layer` and policy mutation payloads MUST include `isPropagatedToParent`, defaulting to `false`.
- **FR-015**: `Filter` MUST be generic over input, output, and context (`Filter<I,O,CTX>`), where `CTX` extends `FilterContext`.
- **FR-016**: For prompt-contributor and event filtering, `ObjectSerializerFilter` MUST preserve input/output type parity (`PromptContributor -> PromptContributor`, `Event -> Event`) and use serialization context for typed conversion without runtime polymorphism requirements.
- **FR-016b**: Matcher evaluation MUST be driven by a typed source object (`FilterSource`) rather than payload aliases, so bindings can consistently evaluate `matcherKey`, `matcherType`, and `matchOn` against the originating `PromptContributor` or `GraphEvent`.
- **FR-016d**: For `matcherType=REGEX`, `matcherText` MUST be evaluated with full-string regex `matches()` semantics against the resolved source value. Substring-oriented bindings therefore require patterns such as `(?s).*needle.*`.
- **FR-016c**: For `matchOn=GRAPH_EVENT`, `matcherKey=NAME` MUST resolve the event class simple name (for example `AddMessageEvent`), not the serialized payload `eventType` field (for example `ADD_MESSAGE_EVENT`). `matcherKey=TEXT` MUST resolve the string surface actually being filtered by the invoking integration.
- **FR-016a**: `PathFilter` MUST consume instructions produced by an `ExecutableTool` and apply them via dispatching interpreters selected by each instruction `targetPath.pathType`.
- **FR-016e**: Persisted `Filter.filterType` discriminators MUST remain `PATH` for regex/json/markdown path filters and `AI` for AI filters. Endpoint/discovery kinds (`REGEX_PATH`, `MARKDOWN_PATH`, `JSON_PATH`, `AI_PATH`) MUST be tracked separately as registration metadata rather than serialized `Filter.filterType` values.
- **FR-017**: `FilterDecisionRecord` MUST be generic over input/output (`FilterDecisionRecord<I,O>`) and owned by the policy/filter execution result, not by executor type.
- **FR-018**: For each processed input item, filter evaluation MUST return a `FilterDecisionRecord<I,O>` whose optional output determines forwarding behavior (present => use output, empty => drop item).
- **FR-019**: `ExecutableTool` implementations MUST support outcomes that can remove an item entirely, return a transformed item, or return a reduced-item variant.
- **FR-020**: The system MUST record filtering decisions with policy identity, layer scope, timestamp, and action taken.
- **FR-021**: Filtered-output inspection MUST return policy-scoped `FilterDecisionRecord<I,O>` results and exclude records from other policies.
- **FR-021a**: Layer-based policy search MUST return all policies effective for a target layer, including policies bound at ancestor layers with descendant inclusion enabled.
- **FR-021b**: Policy add/remove/enable/disable operations MUST propagate to descendant layers only when `isInheritable=true` for the change context.
- **FR-021d**: Policy add/remove/enable/disable operations MUST propagate to parent layers only when `isPropagatedToParent=true` for the change context.
- **FR-021c**: The system MUST provide an endpoint to discover all child layers for a given layer so agents can decide whether inheritance should be applied.
- **FR-022**: The system MUST handle policy execution errors gracefully by recording failures and applying configured fallback behavior without interrupting unrelated processing.
- **FR-023**: The system MUST support policy implementations delivered as executable artifacts, regardless of implementation language.
- **FR-024**: Dynamically registered prompt-contributor policies MUST propagate to workflow-agent prompt assembly paths, and dynamically registered event policies MUST propagate to controller event-processing paths.
- **FR-024a**: Prompt contributor filtering MUST run in two phases: (1) object-serializer filters over the `PromptContributor` object, then (2) path filters over the result of `contribute(...)`.
- **FR-024b**: Graph event filtering MUST run in two phases: (1) object-serializer filters over the `GraphEvent` object, then (2) path filters over the string surface chosen by the invoking integration.
- **FR-024c**: Controller/UI event list-detail rendering paths MUST apply path filters to formatted/pretty event text and therefore support `MARKDOWN_PATH`, `REGEX`, and AI-emitted instructions over those text path types.
- **FR-024d**: Event stream/SSE/WebSocket serialization paths MUST apply path filters to serialized event JSON and therefore support `JSON_PATH`, `REGEX`, and AI-emitted instructions over those path types.
- **FR-024e**: Controller/UI graph-event text filtering operates on the event's raw `prettyPrint()` output prior to final UI wrapping. Nested markdown embedded inside artifact pretty-print payloads is not normalized before `MARKDOWN_PATH` evaluation.
- **FR-025**: `PathFilter` execution MUST support regex-path, markdown-path, and json-path instruction targets and apply `Replace`, `Set`, `Remove`, `ReplaceIfMatch`, and `RemoveIfMatch` operations.
- **FR-026**: Instruction execution MUST apply operations deterministically, preserving operation order and recording each applied operation in policy-scoped decision history.
- **FR-027**: Policy registration MUST validate instruction schemas, path-language compatibility, and executor compatibility before policy activation.
- **FR-028**: `ExecutableTool` implementations MUST be swappable at runtime so the same filter contract can run as external binary execution or in-process function execution.
- **FR-028a**: When an external executor uses a configured `filter.bins` working directory, that directory MUST exist before subprocess launch; otherwise the executor invocation fails before the external script/binary starts.
- **FR-028b**: In this app, `filter.bins` MUST be resolved from the deployed Spring app module `PROJ_DIR` rather than assumed to be the repo root; tmp-deploy workflows therefore MUST provision `<tmp-repo>/multi_agent_ide_java_parent/multi_agent_ide/bin` (or inspect the processed app config and provision the resolved directory).
- **FR-029**: `AiFilterTool` MUST be supported as an executor that accepts filter context and prompt inputs and returns either transformed output payloads or executable instruction lists.
- **FR-029a**: `AiFilterTool` MUST support configurable chat-session reuse strategies (per invocation, global, per action, and per agent) for ACP chat model calls.
- **FR-029b**: `AiFilterTool` execution MUST be able to build AI-call context from `AgentModels.AgentRequest`/`AgentModels.AgentResult` metadata and apply the same prompt/tool/request/result decorators used by workflow agents when configured.
- **FR-029c**: AI filter registration MUST be exposed via a dedicated endpoint (`POST /api/filters/ai-path-filters/policies`) while policy discovery and inspection continue to use existing shared read endpoints.
- **FR-029d**: `AiFilterTool` execution MUST delegate to `LlmRunner.runWithTemplate()` using an `AiFilterContext` that carries a decorated `PromptContext`, `ToolContext`, `OperationContext`, template model map, and response class. The `AiFilterContext` is built by `FilterExecutionService.runAiFilter()` from either the originating `PromptContributorContext` or a `GraphEventObjectContext` that can be resolved back to an agent process.
- **FR-029e**: `AiFilterTool` MUST require filter context that can resolve both `OperationContext` and `PromptContext` for LLM dispatch: either a `PromptContributorContext` (or a `PathFilterContext` wrapping one), or a `GraphEventObjectContext` whose artifact key can be traced to an agent process. When neither is reachable, AI filter execution MUST be skipped with a warning and PASSTHROUGH action.
- **FR-029f**: `AiFilterTool` MUST apply the full decorator chain (request, prompt-context, tool-context, result decorators) consistent with workflow agent actions, keyed to the agent name `ai-filter`, action `path-filter`, and method `runAiFilter`.
- **FR-029g**: `AiFilterTool` MUST support an optional `registrarPrompt` field on AI executor registration, persist it in executor configuration, and pass it into the AI filter template model so registrar-authored policy intent can guide instruction generation.
- **FR-029h**: `PythonExecutor` / `PathFilter` instruction executors MUST deserialize a bare JSON array of `Instruction` values (`[]` for no-op). A top-level `{"instructions": ...}` envelope MUST NOT be treated as a valid path-filter response contract.

### Key Entities *(include if feature involves data)*

- **Policy Definition**: Declares filter identity, scope (controller/agent/global), execution target type, and intended action behavior.
- **Filter**: Sealed generic filter interface `Filter<I,O,CTX>` bound to a layer target.
- **ObjectSerializerFilter**: Filter specialization that serializes/deserializes full objects and can replace/drop full payloads.
- **PathFilter**: Filter specialization that executes reusable path instructions over payload segments instead of replacing the full object.
- **ExecutableTool**: Sealed mix-in for instruction/output producers; concrete implementations are BinaryExecutor, JavaFunctionExecutor, PythonExecutor, and AiFilterTool.
- **BinaryExecutor**: Runs external binaries (for example via ProcessBuilder) and returns normalized filter outputs/instructions.
- **JavaFunctionExecutor**: Runs in-process Java/Kotlin function callbacks and returns normalized outputs/instructions.
- **PythonExecutor**: Runs python-backed logic and returns normalized outputs/instructions.
- **AiFilterTool**: AI-backed executor that uses filter context to produce transformed output or structured instructions.
- **Interpreter**: Sealed interface that applies instructions to payloads.
- **RegexInterpreter**: Applies instructions against regex-oriented targets.
- **MarkdownPathInterpreter**: Applies instructions against markdown-path targets.
- **JsonPathInterpreter**: Applies instructions against json-path targets.
- **Path**: Sealed path model with concrete records `RegexPath`, `MarkdownPath`, and `JsonPath`.
- **Instruction**: Sealed instruction model with concrete records `Replace`, `Set`, `Remove`, `ReplaceIfMatch`, and `RemoveIfMatch`.
- **Layer**: Sealed interface representing filter execution scope at multiple granularities.
- **LayerCtx**: Sealed context interface used by `Layer.matches(LayerCtx)` to evaluate policy applicability at runtime.
- **Policy Registration**: Associates a policy definition to one or more hierarchical layer bindings with active/inactive status.
- **Policy Layer Binding**: Layer-level enable/disable state for a policy, including `isInheritable` and `isPropagatedToParent` propagation controls.
- **Filter Source**: Typed runtime source (`PromptContributor` or `GraphEvent`) used for policy binding matcher evaluation.
- **Layer Hierarchy Node**: Self-referential layer node with parent/child references and propagation capability metadata.
- **Filter Decision Record**: Generic immutable record `FilterDecisionRecord<I,O>` containing typed input/output, policy identifier, action, status, and timestamp.
- **Layer Scope**: Defines where a policy applies, including controller-level data streams and agent prompt assembly paths.
- **Execution Artifact Reference**: Reference to the executable or callable unit that evaluates input and returns a filtering decision.

### Data Model Relationships

- A **Filter<I,O,CTX>** has one **ExecutableTool** and relies on bound **Layer** entities that execute `matches(LayerCtx)`.
- A **Policy Registration** has many **Policy Layer Bindings**.
- An **ExecutableTool** evaluates one typed item with filter context and returns a `FilterDecisionRecord<I,O>` that may include instructions.
- **ObjectSerializerFilter** is used for prompt-contributor and event full-object filtering paths.
- **PathFilter** uses dispatching interpreters to apply ordered **Instruction** records over **Path** targets.
- **BinaryExecutor**, **JavaFunctionExecutor**, **PythonExecutor**, and **AiFilterTool** are attachable to filters through the same executor contract.
- A call executes all active filters bound to layers where `Layer.matches(LayerCtx)` is true for the invocation context.
- For a call scope, policy inclusion is true when any matched layer binding is enabled.
- Policy propagation to descendant layers is applied only when `isInheritable=true`.
- Policy propagation to parent layers is applied only when `isPropagatedToParent=true`.

### Scope Boundaries

- In scope: layer-aware policy definition, registration lifecycle, persistence, deterministic execution, and auditable decision history.
- In scope: support for text and structured payload filtering behaviors, including full replacement and transformation actions.
- Out of scope: redesigning the full workflow orchestration model, replacing existing event transport, or introducing unrelated prompt authoring features.

### Assumptions

- Policy registration and deregistration are restricted to authorized actors already supported by existing access controls.
- Existing workflows already provide identifiable controller events and agent prompt-contributor artifacts for policy evaluation.
- Decision history retention uses current operational retention standards unless changed in a future feature.

### Dependencies

- Existing controller event pipeline and agent prompt assembly pipeline remain available as integration points.
- Existing session/run identity model remains available to bind filter decisions to operational context.
- Existing operational observability channels remain available for surfacing recent filter decisions.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of active policies returned by layer-based discovery include both description and source path metadata.
- **SC-002**: At least 95% of valid policy registration requests are accepted and activated within 10 seconds of submission.
- **SC-003**: 100% of invalid policy registrations are rejected with explicit schema-validation feedback.
- **SC-004**: 100% of deactivated policies stop affecting newly processed data within 10 seconds of deactivation.
- **SC-005**: After service restart, 100% of policy activation states are restored correctly for all persisted policies.
- **SC-006**: For policy-specific output inspection requests, 100% of returned records map to the selected policy and exclude other policies' outputs.

## Notes

- Test-graph feature authoring is intentionally deferred for this feature iteration per explicit user direction.
- Cucumber tags are still provided in user stories to preserve traceability for future integration-test implementation.

## Dynamic Runtime Use Case

- An agent identifies low-value repeated prompt-contributor content and registers an `ObjectSerializerFilter` using `AiFilterTool` to summarize or drop noisy content at runtime.
- The registrar validates the submitted filter kind, executor type, path-target compatibility (if path-based), scope, and required metadata, then activates it without requiring a service restart.
- A second policy registers a `PathFilter` with a swappable executor (`BinaryExecutor` or `JavaFunctionExecutor`) that returns ordered `Replace/Set/Remove` instructions.
- The `PathFilter` routes instructions to `RegexInterpreter`, `MarkdownPathInterpreter`, or `JsonPathInterpreter` based on each instruction `targetPath.pathType` and applies deterministic operations.
- A controller operator can similarly register an event-targeting filter and validate it against the correct runtime surface: controller/UI formatted text for list/detail paths, serialized JSON for stream/SSE/WebSocket paths.
- Agents and operators can inspect filtered output by policy to verify that dynamic runtime changes are behaving as intended.

## Delivery Approach

- Define a sealed generic `Filter<I,O,CTX>` model with concrete variant 'PathFilter`.
- Define a sealed `ExecutableTool` mix-in with concrete variants: `BinaryExecutor`, `JavaFunctionExecutor`, `PythonExecutor`, and `AiFilterTool`.
- Define sealed `Path` records (`RegexPath`, `MarkdownPath`, `JsonPath`) and sealed `Instruction` records (`Replace`, `Set`, `Remove`).
- Define sealed interpreter variants (`RegexInterpreter`, `MarkdownPathInterpreter`, `JsonPathInterpreter`) for path-instruction execution.
- Define sealed `Layer` and sealed `LayerCtx` models so layers can match granular invocation scopes through `Layer.matches(LayerCtx)`.
- All serialization is Jackson/JSON — no separate SerializationContext type. Each filter type owns its deserialization semantics.
- Provide per-filter-type registration endpoints (one per concrete filter type) instead of a single generic endpoint. The filter type and I/O types are implicit from the endpoint — request bodies contain only executor config and common policy fields.
- Enrich `PolicyLayerBinding` with matcher fields (`matcherKey`, `matcherType`, `matcherText`, `matchOn`) so policies can narrow which specific `PromptContributor` or `GraphEvent` they apply to within a bound layer.
- Use a typed `FilterSource` marker model in execution so `PolicyLayerBinding` matcher evaluation is based on source object fields (name/text/match domain) instead of payload-name aliases.
- Execute filters in ordered phases per domain: object serializer first, then path filtering on post-object output (`contribute(...)` text for prompt contributors; formatted text for controller/UI graph-event paths; serialized JSON for event-stream paths).
- Apply filtering as `FilterDecisionRecord<I,O>` output where present output is forwarded and absent output is dropped.
- Route filter activation by scope so object filters affect workflow-agent/controller data and path filters apply deterministic instruction execution through interpreters.
