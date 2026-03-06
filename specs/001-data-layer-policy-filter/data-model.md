# Data Model: Layered Data Policy Filtering

**Feature**: 001-data-layer-policy-filter  
**Date**: 2026-03-01

## Type System Overview

- `Filter<I, O, CTX>`: sealed interface
  - `PathFilter<I, O, CTX extends FilterContext>`
  - `AiPathFilter` (wraps `AiFilterTool` executor + dispatching interpreter)
- `ExecutableTool<I, O, CTX>`: sealed interface
  - `BinaryExecutor`
  - `JavaFunctionExecutor`
  - `PythonExecutor`
  - `AiFilterTool`
- `Interpreter`: sealed interface
  - `RegexInterpreter`
  - `MarkdownPathInterpreter`
  - `JsonPathInterpreter`
- `Path`: sealed interface
  - `RegexPath`
  - `MarkdownPath`
  - `JsonPath`
- `Instruction`: sealed interface
  - `Replace`
  - `Set`
  - `Remove`
- `Layer`: sealed interface
- `LayerCtx`: sealed interface
- `FilterContext`: extends `LayerCtx`
- `FilterSource`: sealed marker interface
  - `PromptContributorSource`
  - `GraphEventSource`
- `FilterDecisionRecord<I, O>`: generic policy-owned execution result

## Core Filter Model

### 1. Filter<I, O, CTX> (sealed)

**Purpose**: Typed policy target and execution entrypoint.

| Field | Type | Description | Validation |
|---|---|---|---|
| id | String | Unique filter identifier | Not blank, unique |
| name | String | Human-readable filter name | Not blank |
| description | String | Operator-facing behavior description | Not blank |
| sourcePath | String | Source path for policy definition/executor source | Not blank |
| executor | ExecutableTool<I, O, CTX> | Bound executor implementation | Not null |
| status | Enum(`ACTIVE`,`INACTIVE`) | Runtime status | Not null |
| priority | int | Deterministic ordering | >= 0 |
| createdAt | Instant | Creation timestamp | Not null |
| updatedAt | Instant | Last update timestamp | Not null |

### 2. ObjectSerializerFilter<I, O, CTX> (sealed, permits Filter)

**Purpose**: Full-object filtering via serialize/transform/deserialize lifecycle. Sealed interface with domain-specific permitted subclasses that fix the type parameters. All serialization is Jackson/JSON — no separate serialization context needed.

Permitted subclasses:

#### 2a. EventObjectSerializerFilter implements ObjectSerializerFilter<GraphEvent, GraphEvent, GraphEventObjectContext>

**Purpose**: Filters controller-streamed events. Deserializes `GraphEvent` objects as JSON via Jackson and produces `GraphEvent` output.

#### 2b. PromptContributorObjectSerializerFilter implements ObjectSerializerFilter<PromptContributor, PromptContributor, PromptContributorContext>

**Purpose**: Filters prompt contributor content during agent prompt assembly. Deserializes `PromptContributor` objects as JSON via Jackson and produces `PromptContributor` output.

### 3. PathFilter<I, O, CTX> (permits Filter)

**Purpose**: Path-instruction filtering over partial document/object structure.

Runtime notes:
- `PathFilter`/`AiPathFilter` do not persist an interpreter field.
- Instructions are applied through a dispatching interpreter that routes each ordered instruction batch by `targetPath.pathType` (`REGEX`, `MARKDOWN_PATH`, `JSON_PATH`).
- Policy registration kind, persisted `filterType`, and instruction `targetPath.pathType` are distinct. Persisted `filterType` is `PATH` for regex/json/markdown path filters and `AI` for AI filters; registration/discovery metadata tracks concrete kinds such as `AI_PATH`, `REGEX_PATH`, `MARKDOWN_PATH`, and `JSON_PATH`; returned instructions still target `REGEX`, `MARKDOWN_PATH`, or `JSON_PATH`.
- Prompt contributor filtering and controller/UI event list-detail filtering operate on string text surfaces; stream/SSE/WebSocket event filtering operates on serialized JSON string surfaces.

All serialization (including instruction deserialization) is Jackson/JSON — no separate serialization context needed.

Typical type bindings:
- `MarkdownDocument -> MarkdownDocument`
- `JsonDocument -> JsonDocument`

## Execution Model

### 4. ExecutableTool<I, O, CTX> (sealed)

**Purpose**: Produces either transformed payload output, instruction list, or both.

Operational note for external executors:
- `PythonExecutor` and `BinaryExecutor` use the configured `filter.bins` directory as subprocess working directory when present.
- In this repo's default app config that resolves to `<repo>/multi_agent_ide_java_parent/multi_agent_ide/bin` because `PROJ_DIR` is the Spring app module project dir, so deployments/tests that exercise external executors must provision that directory.

| Field | Type | Description | Validation |
|---|---|---|---|
| executorType | Enum(`BINARY`,`JAVA_FUNCTION`,`PYTHON`,`AI`) | execution strategy | Not null |
| timeoutMs | int | max execution duration | > 0 |
| configVersion | String | config version marker | Semantic version |

Execution contract:
- Input: `I` + `CTX`
- Output: `FilterExecutionResult<I, O>` containing optional `output` and optional `instructions`

### 5. BinaryExecutor (permits ExecutableTool)

| Field | Type | Description |
|---|---|---|
| command | List<String> | executable + args |
| workingDirectory | String or null | optional working dir |
| env | Map<String,String> | environment overrides |
| outputParserRef | String | parser for executor output |

### 6. JavaFunctionExecutor (permits ExecutableTool)

| Field | Type | Description |
|---|---|---|
| functionRef | String | stable function identifier |
| className | String | containing class |
| methodName | String | callable method |

### 7. PythonExecutor (permits ExecutableTool)

| Field | Type | Description |
|---|---|---|
| scriptPath | String | script path/reference |
| entryFunction | String | callable function |
| runtimeArgsSchema | Object or null | runtime arg schema |

### 8. AiFilterTool (permits ExecutableTool)

| Field | Type | Description |
|---|---|---|
| modelRef | String | AI model identifier |
| promptTemplate | String | prompt template |
| registrarPrompt | String or null | optional registrar-authored guidance prompt for policy-specific intent |
| maxTokens | int | response budget |
| sessionMode | Enum(`PER_INVOCATION`,`SAME_SESSION_FOR_ALL`,`SAME_SESSION_FOR_ACTION`,`SAME_SESSION_FOR_AGENT`) | chat-session reuse strategy |
| sessionKeyOverride | String or null | optional explicit ACP session key |
| requestModelType | String or null | optional `AgentModels.AgentRequest` type hint |
| resultModelType | String or null | optional `AgentModels.AgentResult` type hint |
| includeAgentDecorators | boolean | when true, apply workflow decorator chains |
| controllerModelRef | String or null | optional controller model for arbitration |
| controllerPromptTemplate | String or null | optional controller prompt template |
| outputSchema | Object or null | expected structured output schema |
| configVersion | String or null | version tag for tracking config changes |
| timeoutMs | int | execution timeout in milliseconds |

**Execution Flow**: `AiFilterTool.apply()` delegates to `LlmRunner.runWithTemplate()` using the `AiFilterContext` which carries `PromptContext`, `ToolContext`, `OperationContext`, template model map, and `AgentModels.AiFilterResult.class` as the response type. The `FilterExecutionService.runAiFilter()` method builds the `AiFilterContext` from either the originating `PromptContributorContext` or a `GraphEventObjectContext` that can be resolved back to an agent process, then applies the full decorator chain (request, prompt-context, tool-context, result) keyed to agent name `ai-filter`, action `path-filter`, method `runAiFilter`.

**Prerequisite**: Requires filter context that can resolve both `PromptContext` and `OperationContext`: either a `PromptContributorContext` (or `PathFilterContext` wrapping one), or a `GraphEventObjectContext` whose artifact key can be traced to an agent process. When neither path is available, execution is skipped with PASSTHROUGH.

### 8a. AiFilterContext (record, implements PathFilterContext)

| Field | Type | Description |
|---|---|---|
| filterContext | FilterContext | Delegated base filter context |
| templateName | String | Jinja template name for LLM call |
| promptContext | PromptContext | Decorated prompt context with model/template/request |
| model | Map<String, Object> | Template data model (payload, policyId, etc.) |
| toolContext | ToolContext | Decorated tool context for tool abstractions |
| responseClass | Class<AiFilterResult> | Expected LLM response type |
| context | OperationContext | Embabel operation context for agent process |

### 8b. AiFilterRequest (record, implements AgentRequest)

| Field | Type | Description |
|---|---|---|
| contextId | ArtifactKey | Unique context id for this request |
| worktreeContext | WorktreeSandboxContext | Worktree sandbox context |
| goal | String | Goal context for AI filter execution |
| input | String | Serialized input content to be filtered |

### 8c. AiFilterResult (record, implements AgentResult)

| Field | Type | Description |
|---|---|---|
| contextId | ArtifactKey | Unique context id for this result |
| successful | boolean | Whether the AI filter executed successfully |
| output | List<Instruction> | Instructions produced by the AI filter |
| errorMessage | String | Error message if execution failed |
| worktreeContext | WorktreeSandboxContext | Worktree sandbox context |

## Interpreter + Instruction Model

### 9. Interpreter (sealed)

| Field | Type | Description |
|---|---|---|
| interpreterType | Enum(`REGEX`,`MARKDOWN_PATH`,`JSON_PATH`) | interpreter family |

### 10. Path (sealed)

| Field | Type | Description |
|---|---|---|
| pathType | Enum(`REGEX`,`MARKDOWN_PATH`,`JSON_PATH`) | path family |
| expression | String | path expression |

Records:
- `RegexPath(expression)`
- `MarkdownPath(expression)`
- `JsonPath(expression)`

MarkdownPath semantics:
- Expressions are heading-scoped (`#`, `## ...`, `### ...`) only.
- `#` is the root scope; a `REMOVE` targeting `#` drops the entire rendered template/contributor document.

### 11. Instruction (sealed)

| Field | Type | Description |
|---|---|---|
| op | Enum(`REPLACE`,`SET`,`REMOVE`,`REPLACE_IF_MATCH`,`REMOVE_IF_MATCH`) | operation type |
| targetPath | Path | instruction target |
| order | int | deterministic apply order |

Records:
- `Replace(targetPath, value, order)`
- `Set(targetPath, value, order)`
- `Remove(targetPath, order)`
- `ReplaceIfMatch(targetPath, matcher, value, order)`
- `RemoveIfMatch(targetPath, matcher, order)`

Rules:
- Operations are applied in ascending `order`.
- `Replace` and `Set` require a value payload.
- `Remove` does not carry a value.
- `ReplaceIfMatch` and `RemoveIfMatch` require matcher `{ matcherType: REGEX|EQUALS, value: string }`.

## Layer Model

### 12. Layer (sealed)

Behavior contract:
- `boolean matches(LayerCtx ctx)`

Representative variants:
- `WorkflowAgentLayer`
- `WorkflowAgentActionLayer`
- `ControllerLayer`
- `ControllerUiEventPollLayer`

### 12a. Layer (entity)

| Field | Type | Description |
|---|---|---|
| layerId | String | unique layer id |
| layerType | String | layer variant |
| layerKey | String | hierarchical key |
| parentLayerId | String or null | parent layer reference |
| childLayerIds | List<String> | direct child layers |
| isInheritable | boolean | allow propagation to descendants |
| isPropagatedToParent | boolean | allow propagation to ancestors |
| depth | int | hierarchy depth |
| metadata | Map<String, String> | optional attributes |

### 13. LayerCtx (sealed)

Variants:
- `AgentLayerCtx`
- `ActionLayerCtx`
- `ControllerLayerCtx`

### 14. FilterContext (extends LayerCtx)

Purpose:
- Supplies runtime context to filter execution (shared base for prompt contributor, controller/event, and future contexts).

Initial variants:
- `PromptContributorContext`
- `GraphEventObjectContext`

### 14a. FilterSource (sealed)

**Purpose**: Carries the originating domain object used for policy binding matcher evaluation.

Contract:
- `matchOn(): MatchOn`
- `GraphEventSource.NAME` resolves `event.getClass().getSimpleName()`
- `GraphEventSource.TEXT` resolves the string surface being filtered by the integration path
- `PromptContributorSource.NAME` resolves `PromptContributor.name()`
- `PromptContributorSource.TEXT` resolves `PromptContributor.template()`
- `matcherValue(MatcherKey key): String`

Variants:
- `PromptContributorSource` wraps `PromptContributor`
- `GraphEventSource` wraps `GraphEvent`

## Decision Model

All serialization/deserialization is Jackson/JSON. No separate SerializationContext type is needed — each filter type knows how to deserialize its own domain objects (ObjectSerializerFilter subclasses deserialize GraphEvent or PromptContributor, PathFilter deserializes instructions).

### 15. FilterExecutionResult<I, O>

| Field | Type | Description |
|---|---|---|
| output | O or null | transformed full output |
| instructions | List<Instruction> | path instructions (optional) |
| metadata | Map<String,String> | optional execution metadata |

### 16. FilterDecisionRecord<I, O>

| Field | Type | Description |
|---|---|---|
| decisionId | String | unique decision id |
| policyId | String | owning policy |
| layer | Layer | resolved call layer |
| action | Enum(`DROPPED`,`TRANSFORMED`,`PASSTHROUGH`,`ERROR`) | outcome class |
| input | I | typed input payload |
| output | O or null | typed output payload |
| appliedInstructions | List<Instruction> | instructions applied by interpreter |
| errorMessage | String or null | error detail |
| createdAt | Instant | timestamp |

Behavior rules:
- If `output` is present, downstream uses `output`.
- If `output` is absent and instructions are present, interpreter output is used.
- If neither output nor instructions yield content, downstream drops the item.

## Policy Model

### 17. PolicyRegistration

| Field | Type | Description |
|---|---|---|
| registrationId | String | unique registration id |
| registeredBy | String | actor identity |
| status | Enum(`ACTIVE`,`INACTIVE`) | lifecycle state |
| isInheritable | boolean | default descendant propagation |
| isPropagatedToParent | boolean | default parent propagation |
| filter | Filter<I, O, CTX> | contained policy filter |
| layerBindings | List<PolicyLayerBinding> | layer-level controls |
| activatedAt | Instant or null | activation time |
| deactivatedAt | Instant or null | deactivation time |

### 17a. PolicyLayerBinding

| Field | Type | Description | Validation |
|---|---|---|---|
| layerId | String | bound layer node | Not blank |
| enabled | boolean | policy enabled state | Not null |
| includeDescendants | boolean | subtree include flag | Not null |
| isInheritable | boolean | propagate to descendants | Not null |
| isPropagatedToParent | boolean | propagate to ancestors | Not null |
| matcherKey | Enum(`NAME`, `TEXT`) | what field to match on | Not null |
| matcherType | Enum(`REGEX`, `EQUALS`) | how to match | Not null |
| matcherText | String | regex expression or exact text to match | Not blank |
| matchOn | Enum(`PROMPT_CONTRIBUTOR`, `GRAPH_EVENT`) | which domain object to match against | Not null |
| updatedBy | String | actor identity | Not blank |
| updatedAt | Instant | update timestamp | Not null |

**Matcher semantics (source-driven)**:
- `matcherKey=NAME` + `matchOn=PROMPT_CONTRIBUTOR`: matches against `PromptContributor.name()` (the static name)
- `matcherKey=NAME` + `matchOn=GRAPH_EVENT`: matches against the `GraphEvent` class name (e.g. `NodeStatusChanged`)
- `matcherKey=TEXT` + `matchOn=PROMPT_CONTRIBUTOR`: matches against prompt contributor source text metadata (template/static text) and path filters then run over `contribute(...)` output
- `matcherKey=TEXT` + `matchOn=GRAPH_EVENT`: matches against graph event source text metadata (pretty/event text) and path filters then run over serialized payload text
- `matcherType=REGEX`: `matcherText` is a regex pattern applied with full-string `matches()` semantics; use `(?s).*...*` for substring matching
- `matcherType=EQUALS`: `matcherText` must exactly equal the resolved value

**matchOn constraints by filter type**:
- `EventObjectSerializerFilter` endpoints: `matchOn` must be `GRAPH_EVENT`
- `PromptContributorObjectSerializerFilter` endpoints: `matchOn` must be `PROMPT_CONTRIBUTOR`
- `PathFilter` endpoints (json-path, markdown-path, regex-path): `matchOn` can be either `PROMPT_CONTRIBUTOR` or `GRAPH_EVENT` (the path filter operates on whichever domain object is matched)

**Binding behavior**: A policy binds to a layer (agent, action, or controller level) and then within that layer, the matcher narrows which specific `PromptContributor` or `GraphEvent` instances the filter applies to via `FilterSource`.

**Propagation rules**:
- Propagation is **one-shot**: when a policy is registered or a binding is mutated, the system propagates once to applicable ancestors/descendants. Propagated bindings do NOT re-propagate further (no cascading/infinite loops).
- When both `isInheritable=true` and `isPropagatedToParent=true`: the binding propagates down to descendants AND up to the parent, but each propagated binding is created with `isInheritable=false` and `isPropagatedToParent=false` so it does not cascade further.
- Propagated bindings inherit the `enabled`, `matcherKey`, `matcherType`, `matcherText`, and `matchOn` values from the source binding.

## Relationship Summary

- `Layer` is self-referential (`parent -> children`).
- `PolicyRegistration` binds to many `Layer` nodes via `PolicyLayerBinding`.
- `PolicyRegistration` contains one `Filter<I, O, CTX>`.
- `Filter<I, O, CTX>` has one `ExecutableTool<I, O, CTX>`.
- `PathFilter` uses a dispatching interpreter selected by each instruction `targetPath.pathType`.
- `ExecutableTool` may return instruction lists interpreted by `PathFilter`/`AiPathFilter`.
- For `PathFilter`, `PythonExecutor` response payloads are deserialized directly as `List<Instruction>`; the on-wire shape is a bare JSON array, not `{"instructions": [...]}`.
- `FilterDecisionRecord<I, O>` stores final output and applied instructions.

## Validation Rules

- `Filter`, `ExecutableTool`, `Interpreter`, `Path`, `Instruction`, `Layer`, and `LayerCtx` must satisfy sealed-interface constraints.
- Prompt contributor/event object filters must preserve type parity (`I == O`).
- Path filters must use valid instruction targets (`targetPath.pathType` + `expression`) for runtime dispatch.
- Executor output must validate against instruction/output schemas before application.
- `AiFilterTool` output must validate against declared response mode/schema before execution.
- Layer hierarchy must support subtree and parent propagation rules.
- Matched layer bindings use OR evaluation (`enabled` in any matched layer => include policy).
