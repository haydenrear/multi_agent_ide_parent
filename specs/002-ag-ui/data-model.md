# Data Model: Agent Graph UI

## Entities

### GraphEvent
**Fields**:
- id (string, required)
- type (string, required)
- timestamp (ISO-8601 string, required)
- nodeId (string, optional)
- payload (object, optional)
- sequence (number, optional)

**Validation rules**:
- id and type must be present
- timestamp must be parseable
- if nodeId present, it must match an existing GraphNode or create a placeholder

### AgUiEvent
**Fields**:
- id (string, required)
- type (string, required)
- timestamp (ISO-8601 string, required)
- nodeId (string, optional)
- payload (object, optional)

**Validation rules**:
- type must be a known ag-ui event type or a registered custom event type
- timestamp must be parseable

### EventMappingRule
**Fields**:
- sourceEventType (string, required)
- targetEventType (string, required)
- mappingVersion (string, optional)
- transform (string, optional description of transformation)

**Validation rules**:
- sourceEventType must map to a GraphEvent type
- targetEventType must map to an AgUiEvent type

### ViewerPlugin
**Fields**:
- id (string, required)
- name (string, required)
- supportedNodeTypes (string[], optional)
- supportedEventTypes (string[], optional)
- supportedToolCalls (string[], optional)
- priority (number, optional)

**Validation rules**:
- at least one of supportedNodeTypes, supportedEventTypes, supportedToolCalls must be provided

### ViewerPluginRegistry
**Fields**:
- plugins (ViewerPlugin[], required)
- defaultPluginId (string, required)

**Validation rules**:
- defaultPluginId must reference a plugin in the registry

### CustomEventDefinition
**Fields**:
- name (string, required)
- description (string, optional)
- payloadSchema (object, optional)

**Validation rules**:
- name must not collide with existing ag-ui event types

### GraphNode
**Fields**:
- id (string, required)
- title (string, optional)
- nodeType (string, required)
- status (string, required)
- parentId (string, optional)
- worktreeIds (string[], optional)
- lastUpdatedAt (ISO-8601 string, required)

**Validation rules**:
- status must be one of READY, RUNNING, COMPLETED, FAILED, WAITING_INPUT, HUMAN_REVIEW_REQUESTED, PRUNED
- parentId must refer to another GraphNode if provided

**State transitions**:
- READY -> RUNNING -> COMPLETED/FAILED/WAITING_INPUT/PRUNED
- WAITING_INPUT -> RUNNING or COMPLETED

### Worktree
**Fields**:
- id (string, required)
- nodeId (string, required)
- path (string, required)
- type (string, required; main/submodule)

**Validation rules**:
- nodeId must reference a GraphNode

### AgentControlAction
**Fields**:
- actionType (string, required; PAUSE, INTERRUPT, REVIEW_REQUEST)
- nodeId (string, required)
- message (string, optional)
- timestamp (ISO-8601 string, required)

**Validation rules**:
- actionType must match supported control actions
- nodeId must reference a GraphNode

## Relationships

- GraphEvent references GraphNode via nodeId.
- GraphEvent is mapped to AgUiEvent via EventMappingRule.
- GraphNode references parent GraphNode via parentId.
- Worktree references GraphNode via nodeId.
- AgentControlAction references GraphNode via nodeId and results in GraphEvent emission.
- ViewerPluginRegistry selects a ViewerPlugin based on node/event/tool call metadata.
