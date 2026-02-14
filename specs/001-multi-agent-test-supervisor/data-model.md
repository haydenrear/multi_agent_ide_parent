# Data Model: LLM Debug UI Skill and Shared UI Abstraction

## Overview

This ticket models debugging-stage entities only:

- Shared UI state/actions
- Materialized node-scoped UI state store/query
- Node-scoped event observation (summary + detail)
- Script-first operational commands (deploy/restart + quick actions)
- Routing interpretation context for workflow debugging

Intentionally excluded:

- Prompt discovery/update workflows
- In-app build-rerun endpoint workflows
- Evaluation profile lifecycle
- Explicit prompt version entity/lifecycle
- Prompt evolution proposals
- Multi-user tenancy partitioning

## Entities

### 1) UiStateSnapshot

**Purpose**: Interface-neutral state equivalent to shared UI semantics (`UiState` + `UiSessionState`), materialized incrementally from incoming events.

**Fields**:
- `nodeId` (string, required)
- `activeNodeId` (string, required)
- `nodeOrder` (array<string>, required)
- `nodes` (map<string, UiSessionSnapshot>, required)
- `focus` (enum: `CHAT_INPUT`, `EVENT_STREAM`, `SESSION_LIST`, `CHAT_SEARCH`, required)
- `chatScrollOffset` (int, required)
- `repo` (string/path, required)
- `revision` (string, required)
- `capturedAt` (timestamp, required)

**Validation rules**:
- `activeNodeId` must exist in `nodes`.
- `nodeOrder` entries must be consistent with `nodes` keys.

---

### 2) UiSessionSnapshot

**Purpose**: Per-node UI state equivalent to shared reducer semantics.

**Fields**:
- `selectedIndex` (int, required)
- `scrollOffset` (int, required)
- `autoFollow` (boolean, required)
- `detailOpen` (boolean, required)
- `detailEventId` (string, optional)
- `chatInput` (string, required)
- `chatSearch` (object: `active`, `query`, `resultIndices`, `selectedResultIndex`, required)
- `repo` (string/path, optional)
- `eventCount` (int, required)

---

### 3) UiActionRequest

**Purpose**: LLM-invokable command routed through shared UI action mapping.

**Fields**:
- `nodeId` (string, required)
- `actionType` (enum, required)
- `payload` (object, optional)

---

### 4) QuickActionRequest

**Purpose**: Agent-level convenience action contract exposed at `/api/llm-debug/ui/quick-actions`.

**Fields**:
- `actionType` (enum: `START_GOAL`, `SEND_MESSAGE`, required)
- `nodeId` (string, required for `SEND_MESSAGE`)
- `message` (string, required for `SEND_MESSAGE`)
- `goal` (string, required for `START_GOAL`)
- `repositoryUrl` (string, required for `START_GOAL`)
- `baseBranch` (string, optional)
- `title` (string, optional)

---

### 5) QuickActionResponse

**Purpose**: Script-consumable result of quick action execution.

**Fields**:
- `actionId` (string, required for accepted actions)
- `status` (enum: `started`, `queued`, `rejected`, required)
- `nodeId` (string, optional)
- `error` (string, optional)

---

### 6) UiEventSummary

**Purpose**: Paged summary item for event polling.

**Fields**:
- `eventId` (string, required)
- `nodeId` (string, required)
- `eventType` (string, required)
- `timestamp` (timestamp, required)
- `summary` (string, required; truncated formatter output)

---

### 7) UiEventPage

**Purpose**: Cursor-paged response for `/nodes/{nodeId}/events`.

**Fields**:
- `items` (array<UiEventSummary>, required)
- `page` (`limit`, `cursor`, `nextCursor`, `hasNext`, required)
- `total` (int, required)

**Validation rules**:
- `limit` bounded server-side.
- `nextCursor` null when `hasNext=false`.

---

### 8) UiEventDetail

**Purpose**: Expanded event inspection output using `CliEventFormatter`.

**Fields**:
- `eventId` (string, required)
- `nodeId` (string, required)
- `eventType` (string, required)
- `timestamp` (timestamp, required)
- `summary` (string, required)
- `formatted` (string, required; expanded formatter output)

---

### 9) DeployRestartResult (script-level)

**Purpose**: Result of external deploy/restart command from skill script.

**Fields**:
- `mode` (enum: `bootrun`, `jar`, required)
- `pid` (int, required)
- `pidFile` (string/path, required)
- `logFile` (string/path, required)
- `steps` (array<string>, required)
- `portCleanup` (object: `initialPids`, `terminated`, `killed`, `remaining`, required)
- `health` (object with `httpStatus`, `status`, `body`, required)

---

### 10) RoutingResolutionContext

**Purpose**: Explainable model for how one routing result determines the next action.

**Fields**:
- `lastResultType` (string, required)
- `routingRecordType` (string, optional)
- `selectedRouteField` (string, optional)
- `resolvedRequestType` (string, required)
- `selectedActionName` (string, required)
- `collectorDecisionType` (enum: `ADVANCE_PHASE`, `ROUTE_BACK`, `STOP`, optional)
- `ambiguousRoute` (boolean, required)

**Validation rules**:
- `ambiguousRoute=true` when more than one non-null route field is present.
- `collectorDecisionType` is required when `resolvedRequestType` is collector-result based.

---

### 11) ContextMemoryQueryResult

**Purpose**: Represents memory/tool query output used during context-manager recovery.

**Fields**:
- `nodeId` (string, required)
- `toolName` (enum: `traceHistory`, `listHistory`, `searchHistory`, `listMessageEvents`, `getHistoryItem`, `addHistoryNote`, required)
- `query` (object, required)
- `items` (array<object>, required)
- `total` (int, optional)
- `cursor` (string, optional)
- `nextCursor` (string, optional)
- `summary` (string, required)

---

### 12) LoopSafeguardSignal

**Purpose**: Indicates loop-risk and stuck-handler state used in recovery triage.

**Fields**:
- `nodeId` (string, required)
- `requestType` (string, required)
- `repeatCount` (int, required)
- `threshold` (int, required)
- `stuckHandlerInvocations` (int, required)
- `degenerateLoop` (boolean, required)
- `reason` (string, optional)

## Relationships

- `UiStateSnapshot (1) -> (N) UiSessionSnapshot`
- `UiEventPage (1) -> (N) UiEventSummary`
- `QuickActionRequest (1) -> (0..1) QuickActionResponse`
- `RoutingResolutionContext (1) -> (0..1) LoopSafeguardSignal`
- `ContextMemoryQueryResult (N) -> (1) nodeId scope`

## State Transitions

### QuickActionResponse.status

- `START_GOAL`: `started` or `rejected`
- `SEND_MESSAGE`: `queued` or `rejected`

## Invariants

- TUI and LLM interfaces must emit equivalent semantic state changes for equivalent UI actions.
- New UI actions must be implemented in shared abstraction before interface exposure.
- `nodeId` is the run scope key in skill operations; `runId` aliases root `nodeId` when returned.
- Event queries must be scoped to exact node plus descendants.
- State query responses must come from materialized UI state for the scope node (no request-time replay pass).
- Script outputs must be machine-readable and reflect endpoint/script errors explicitly.
- Routing records should resolve to a single non-null next route field for deterministic planning.
- Context-manager recovery should return one dominant route target after memory reconstruction.
