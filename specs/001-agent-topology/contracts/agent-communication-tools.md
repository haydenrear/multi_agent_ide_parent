# Agent Communication Tool Contracts

## ArtifactKey Identity Model

**nodeId = sessionId = ArtifactKey** — these are all the same thing. When this document says "target agent" or "source agent," the identifier is always a single ArtifactKey.

### Key hierarchy for communication requests

- `sourceAgentKey` (ArtifactKey) — the agent sending the message
- `targetAgentKey` (ArtifactKey) — the agent receiving the message
- `key` (ArtifactKey) — the request's own key, a **child** of `sourceAgentKey` (like all other AgentRequests)

### Controller identity & conversation key lifecycle

The controller (Claude Code) is not a regular agent — it doesn't have a persistent ArtifactKey in the graph. The root key belongs to the original orchestrator node. The system manages a **per-target conversation key** for each (controller, target agent) pair via `ControllerToAgentConversationNode` in GraphRepository.

**Resolution algorithm** (see data-model.md for full detail):
1. If controller provides `controllerConversationKey`: validate it exists in GraphRepository AND its `targetAgentKey` field matches. If invalid/mismatched, log warning and fall through.
2. If no key or invalid: look up existing `ControllerToAgentConversationNode` by `targetAgentKey` field under root's children.
3. If not found: create via `root.createChild()`, persist new node with `targetAgentKey` field set.
4. **Always return the resolved key** in the response payload. The controller is asked to reuse it, but the system never relies on it — the lookup-by-target fallback guarantees correctness if the controller drops or misuses the key.

### PromptContext.chatId() for communication requests

`chatId()` must return the **target** agent's ArtifactKey (the session we're sending into):
- `AgentToAgentRequest` → `targetAgentKey`
- `AgentToControllerRequest` → the controller's per-target key (child of root, from GraphRepository lookup)
- `ControllerToAgentRequest` → `targetAgentKey`

`currentContextId` on PromptContext follows the same logic — it's the target's key.

---

## list_agents

**Tool name**: `list_agents`
**Registered in**: `AcpTooling.java`
**Parameters**:
- `sessionId` (String, @SetFromHeader — injected, not user-visible): Calling agent's ArtifactKey

**Returns**: JSON array of `AgentAvailabilityEntry`:
```json
[
  {
    "agentKey": "ak:01KJ.../01KK...",
    "agentType": "ORCHESTRATOR",
    "busy": false,
    "callableByCurrentAgent": true
  }
]
```

**Error cases**:
- No open sessions: Returns empty array
- Session not found: Returns error string

## call_agent

**Tool name**: `call_agent`
**Registered in**: `AcpTooling.java`
**Parameters**:
- `sessionId` (String, @SetFromHeader — injected): Calling agent's ArtifactKey
- `targetAgentKey` (String): Target agent's ArtifactKey (from list_agents)
- `message` (String): Message content to deliver
- `callChain` (String, JSON array): Serialized call chain for loop detection (empty on first call)

**Returns**: String — target agent's response (run through decorator/LlmRunner pipeline), or error message.

**Processing pipeline**:
1. Validate topology and loop detection
2. Build `AgentToAgentRequest` with `sourceAgentKey` (from header), `targetAgentKey`, message. Request `key` = child of `sourceAgentKey`.
3. Persist an `AgentToAgentConversationNode` in GraphRepository (searchable by source/target)
4. Build `PromptContext` with the `AgentToAgentRequest` as `currentRequest`, `currentContextId` = `targetAgentKey`
5. Run through `PromptContextDecorator`s (prompt contributors match on AgentToAgentRequest type)
6. Run through `DecorateRequestResults` (request decorators, result decorators)
7. Run through `DefaultLlmRunner` — structured response type is `String`
8. Return the string response to the calling agent

**Error cases**:
- Topology violation: `"ERROR: Communication not permitted by topology rules. [caller_type] cannot call [target_type]."`
- Target busy: `"ERROR: Agent [targetAgentKey] is currently busy. Try an alternative route."`
- Target unavailable: `"ERROR: Agent [targetAgentKey] is no longer available. Try an alternative route."`
- Loop detected: `"ERROR: Loop detected in call chain: [A -> B -> C -> A]. Call rejected."`
- Max depth exceeded: `"ERROR: Call chain depth [N] exceeds maximum [M]. Call rejected."`

**Events emitted**: `AgentCallEvent` (INITIATED on send, RETURNED on success, ERROR on failure)

## call_controller

**Tool name**: `call_controller`
**Registered in**: `AcpTooling.java`
**Parameters**:
- `sessionId` (String, @SetFromHeader — injected): Calling agent's ArtifactKey
- `justificationMessage` (String): Structured justification message for the controller

**Returns**: String — controller's response (from interrupt resolution notes, run through decorator pipeline).

**Processing pipeline (agent → controller)**:
1. Build `AgentToControllerRequest` with `sourceAgentKey` (from header), message. Request `key` = child of `sourceAgentKey`.
2. Persist an `AgentToControllerConversationNode` in GraphRepository.
3. **Resolve controller conversation key**: Run the resolution algorithm (look up `ControllerToAgentConversationNode` by `targetAgentKey` field = calling agent's key under root's children; create via `root.createChild()` if not found). This ensures the key exists before the interrupt is published.
4. Build `PromptContext` with `AgentToControllerRequest` as `currentRequest`, `currentContextId` = resolved controller conversation key.
5. Run through `PromptContextDecorator`s (prompt contributors match on AgentToControllerRequest type).
6. Run through `DecorateRequestResults` and `DefaultLlmRunner` — structured response type is `String`.
7. Publish the enriched message as a `HUMAN_REVIEW` interrupt via `PermissionGate.publishInterrupt()`. Include the resolved controller conversation key in the interrupt payload so the controller can see it.
8. Block until the controller resolves (via `/api/agent-conversations/respond`).
9. Return the resolution notes as the tool response, including the controller conversation key.

**Error cases**:
- Timeout (configurable): `"ERROR: Controller did not respond within timeout. Proceeding with best judgment."`
- Message budget exceeded: `"ESCALATED: Message budget (k=[N]) exceeded. Escalating to user."`

## Controller → Agent Response (REST Controller)

**Separate REST controller**: `AgentConversationController.java`

Dedicated controller (not InterruptController) so the system definitively knows the message is an agent conversation response.

### Endpoints

**`POST /api/agent-conversations/list`** — List conversations by ArtifactKey hierarchy walk. Given a nodeId, returns all conversations on that agent and any agent whose ArtifactKey descends from it via `createChild`. Uses `GraphRepository.findByParentId()` recursively to walk the tree, filtering for conversation node types (`AGENT_TO_CONTROLLER_CONVERSATION`, `CONTROLLER_TO_AGENT_CONVERSATION`, `AGENT_TO_AGENT_CONVERSATION`).

Request body: `{ "nodeId": "ak:01KJ..." }`

Response: array of conversation summaries (target agent key, agent type, message count, last message preview, pending status).

**`POST /api/agent-conversations/respond`**

**Request body**:
```json
{
  "targetAgentKey": "ak:01KJ.../01KK...",
  "message": "Your interpretation is correct for R1 and R2, but R3 is unmapped...",
  "controllerConversationKey": "ak:ROOT/CTRL_01...",
  "checklistAction": {
    "actionType": "VERIFY_REQUIREMENTS_MAPPING",
    "completedStep": "checklist-discovery-agent.md#step-3",
    "stepDescription": "Verify all goal requirements are mapped to discovered artifacts"
  }
}
```

`controllerConversationKey` is optional — resolved via the controller conversation key resolution algorithm (see data-model.md). If provided, validated against GraphRepository (must exist AND node's `targetAgentKey` field must match request's `targetAgentKey`). If null, missing, not found, or mismatched, the system falls back to lookup-by-target, creating if needed. The controller is never trusted blindly.

`checklistAction` is optional — present when the controller is responding as part of a structured checklist review. Contains the ACTION type from the checklist row and a reference to the completed step.

**Processing pipeline (controller → agent)**:
1. **Resolve `controllerConversationKey`** via the resolution algorithm: validate if provided (exists + target matches), fall back to lookup-by-target, create via `root.createChild()` if not found. Log warnings on invalid/mismatched keys.
2. Build `ControllerToAgentRequest` with `sourceKey` = controller conversation key, `targetAgentKey`, message, `checklistAction`. Request `key` = child of `sourceKey`.
3. Persist a `ControllerToAgentConversationNode` in GraphRepository
4. **Emit `Artifact.ControllerChecklistTurn`** — extracted and persisted as an artifact but **NOT** passed to the model. Contains the action type, completed step reference, controller message, and target agent key.
5. Build `PromptContext` with `ControllerToAgentRequest` as `currentRequest`, `currentContextId` = `targetAgentKey`
6. Run through `PromptContextDecorator`s and `DecorateRequestResults` and `DefaultLlmRunner`
7. Deliver the enriched message to the target agent's session
8. Resolve the pending interrupt so the agent's `call_controller` tool unblocks with the response

**Response**: Returns the resolved controller conversation key (for the controller to include in future calls to this same agent) and standard interrupt resolution status. The key is always returned regardless of whether the controller provided one — this is how the controller learns the key on first use and recovers from dropped keys.

## AgentRouting & AgentResult Subtypes for Communication

Each communication path flows through the decorator pipeline (`DecorateRequestResults` → `PromptContext` → `LlmRunner` → result decoration) and needs its own routing and result types permitted by the `AgentRouting` and `AgentResult` sealed interfaces.

### AgentCallRouting (agent-to-agent)

```java
@Builder(toBuilder = true)
@JsonClassDescription("Routing result from an inter-agent call. Contains the target agent's response.")
record AgentCallRouting(
    @JsonPropertyDescription("The target agent's response text.")
    String response
) implements AgentRouting {}
```

### AgentCallResult (agent-to-agent)

```java
@Builder(toBuilder = true) @With
@JsonClassDescription("Result of an inter-agent call.")
record AgentCallResult(
    @SkipPropertyFilter ArtifactKey contextId,
    String response,
    @SkipPropertyFilter WorktreeSandboxContext worktreeContext
) implements AgentResult {}
```

### ControllerCallRouting (agent-to-controller)

```java
@Builder(toBuilder = true)
@JsonClassDescription("Routing result from an agent-to-controller call. Contains the controller's response.")
record ControllerCallRouting(
    @JsonPropertyDescription("The controller's response text.")
    String response,
    @JsonPropertyDescription("The resolved controller conversation key for future calls.")
    String controllerConversationKey
) implements AgentRouting {}
```

### ControllerCallResult (agent-to-controller)

```java
@Builder(toBuilder = true) @With
@JsonClassDescription("Result of an agent-to-controller call.")
record ControllerCallResult(
    @SkipPropertyFilter ArtifactKey contextId,
    String response,
    String controllerConversationKey,
    @SkipPropertyFilter WorktreeSandboxContext worktreeContext
) implements AgentResult {}
```

### ControllerResponseRouting (controller-to-agent)

```java
@Builder(toBuilder = true)
@JsonClassDescription("Routing result from a controller-to-agent response. Contains the enriched response after decorator pipeline.")
record ControllerResponseRouting(
    @JsonPropertyDescription("The enriched response text.")
    String response,
    @JsonPropertyDescription("The controller conversation key used.")
    String controllerConversationKey
) implements AgentRouting {}
```

### ControllerResponseResult (controller-to-agent)

```java
@Builder(toBuilder = true) @With
@JsonClassDescription("Result of a controller-to-agent response.")
record ControllerResponseResult(
    @SkipPropertyFilter ArtifactKey contextId,
    String response,
    String controllerConversationKey,
    @SkipPropertyFilter WorktreeSandboxContext worktreeContext
) implements AgentResult {}
```

### AgentInterfaces Constants

Each communication path registers constants in `AgentInterfaces`:

| Path | AgentType | Agent Name | Action | Method | Template |
|------|-----------|------------|--------|--------|----------|
| Agent→Agent | `AGENT_CALL` | `agent-call` | `agent-call` | `callAgent` | `communication/agent_call` |
| Agent→Controller | `CONTROLLER_CALL` | `controller-call` | `controller-call` | `callController` | `communication/controller_call` |
| Controller→Agent | `CONTROLLER_RESPONSE` | `controller-response` | `controller-response` | `respondToAgent` | `communication/controller_response` |

### Exhaustive Switch Requirements

All switches on `AgentRouting` and `AgentResult` in the following locations MUST handle the new types:
- `WorkflowGraphResultDecorator` — complete the conversation node and emit `AgentCallCompletedEvent`
- `StartWorkflowRequestDecorator` — create the conversation node and emit `AgentCallStartedEvent`
- `CliEventFormatter` — format the routing/result for CLI display
- `FilterPropertiesDecorator` — return null (no routing filter needed for communication)
- All other `RequestDecorator` and `ResultDecorator` implementations — handle or pass through

---

## AgentRequest Subtypes for Communication

Three new `AgentRequest` implementations added to `AgentModels.java`. These flow through `PromptContext` as `currentRequest`, enabling all existing decorator infrastructure to match on them.

### AgentToControllerRequest

```java
record AgentToControllerRequest(
    ArtifactKey sourceAgentKey,    // agent sending the message
    AgentType sourceAgentType,
    String justificationMessage,
    String goal,
    ArtifactKey key                // child of sourceAgentKey
) implements AgentRequest {}
```

### AgentToAgentRequest

```java
record AgentToAgentRequest(
    ArtifactKey sourceAgentKey,    // agent sending the message
    AgentType sourceAgentType,
    ArtifactKey targetAgentKey,    // agent receiving the message
    AgentType targetAgentType,
    String message,
    List<CallChainEntry> callChain,
    String goal,
    ArtifactKey key                // child of sourceAgentKey
) implements AgentRequest {}
```

### ControllerToAgentRequest

```java
record ControllerToAgentRequest(
    ArtifactKey sourceKey,         // controller's per-target key (child of root)
    ArtifactKey targetAgentKey,    // agent receiving the message
    AgentType targetAgentType,
    String message,
    ChecklistAction checklistAction, // optional — present when responding as part of checklist review
    String goal,
    ArtifactKey key                // child of sourceKey
) implements AgentRequest {}
```

### ChecklistAction

```java
record ChecklistAction(
    String actionType,             // ACTION from checklist row (e.g. "VERIFY_REQUIREMENTS_MAPPING")
    String completedStep,          // reference to the checklist step (e.g. "checklist-discovery-agent.md#step-3")
    String stepDescription         // human-readable description of the completed step
) {}
```

### Artifact.ControllerChecklistTurn

Emitted when a `ControllerToAgentRequest` contains a `checklistAction`. Extracted and persisted but **NOT** passed to the model.

```java
record ControllerChecklistTurn(
    ArtifactKey targetAgentKey,
    AgentType targetAgentType,
    ChecklistAction checklistAction,
    String controllerMessage,
    ArtifactKey conversationKey,   // controller conversation key
    Instant timestamp
) implements Artifact {}
```

## Graph Nodes for Communication

Three new `GraphNode` implementations for persistence and searchability in `GraphRepository`.

### AgentToAgentConversationNode

```java
record AgentToAgentConversationNode(
    String nodeId,                 // the request's ArtifactKey
    String title,
    String goal,
    Events.NodeStatus status,
    String parentNodeId,           // sourceAgentKey
    List<String> childNodeIds,
    String sourceAgentKey,         // calling agent's ArtifactKey
    AgentType sourceAgentType,     // role of the calling agent
    String targetAgentKey,         // target agent's ArtifactKey
    AgentType targetAgentType,     // role of the target agent
    Instant createdAt,
    Instant lastUpdatedAt,
    Events.NodeType nodeType       // AGENT_TO_AGENT_CONVERSATION
) implements GraphNode {}
```

### AgentToControllerConversationNode

```java
record AgentToControllerConversationNode(
    String nodeId,
    String title,
    String goal,
    Events.NodeStatus status,
    String parentNodeId,           // sourceAgentKey
    List<String> childNodeIds,
    String sourceAgentKey,         // calling agent's ArtifactKey
    AgentType sourceAgentType,     // role of the calling agent
    Instant createdAt,
    Instant lastUpdatedAt,
    Events.NodeType nodeType       // AGENT_TO_CONTROLLER_CONVERSATION
) implements GraphNode {}
```

### ControllerToAgentConversationNode

```java
record ControllerToAgentConversationNode(
    String nodeId,
    String title,
    String goal,
    Events.NodeStatus status,
    String parentNodeId,           // controller's per-target key (child of root)
    List<String> childNodeIds,
    String targetAgentKey,         // target agent's ArtifactKey — used by resolution algorithm
    AgentType targetAgentType,     // role of the target agent
    Instant createdAt,
    Instant lastUpdatedAt,
    Events.NodeType nodeType       // CONTROLLER_TO_AGENT_CONVERSATION
) implements GraphNode {}
```

Searchable via existing `GraphRepository.findByType()` and `findByParentId()`. Source/target keys are proper fields, not metadata map entries — they are functional fields used by the resolution algorithm.

## Activity Check Endpoint (Lightweight Polling)

**Controller**: `ActivityCheckController.java`
**Endpoint**: `POST /api/ui/activity-check`

Lightweight endpoint designed to be called at high frequency (every 0.5-1s) by the controller's `poll.py --subscribe` mode. Returns only actionable items — no graph traversal, no propagation queries.

**Request body**:
```json
{
  "nodeId": "ak:01KJ..."
}
```

**Response**:
```json
{
  "pendingPermissions": 2,
  "pendingInterrupts": 1,
  "pendingConversations": 1,
  "hasActivity": true
}
```

`hasActivity` is `true` if any of the counts are > 0. The subscribe loop in `poll.py` uses this single flag to decide whether to call the full `poll_once()`.

## conversations.py (Controller Script)

**Location**: `skills/multi_agent_ide_skills/multi_agent_ide_controller/executables/conversations.py`

Ergonomic CLI for the controller LLM to manage agent↔controller conversations.

**Commands**:
- `python conversations.py <nodeId>` — list all active conversations on this agent and any agent whose ArtifactKey is a descendant (via `createChild`). Shows summary per conversation: agent type, message count, last message preview. This is the tree-walk query — pass the orchestrator's nodeId to see all conversations across the workflow.
- `python conversations.py <nodeId> --last <N>` — retrieve last N messages for this specific agent's conversations only
- `python conversations.py <nodeId> --respond --message "..."` — respond to a pending `call_controller` request on this specific agent
- `python conversations.py <nodeId> --pending` — show only conversations with pending (unresponded) `call_controller` requests on this specific agent

All commands hit `AgentConversationController` endpoints. The nodeId identifies the agent (nodeId = sessionId = ArtifactKey). The list command is the only one that walks the ArtifactKey child hierarchy — all other commands operate on the exact nodeId provided.

## Decorator Pipeline Audit

All existing decorators that switch on `AgentRequest` subtypes must add cases for the three new types:

| Decorator / Factory | File | What to add |
|---------------------|------|-------------|
| `FilterPropertiesDecorator.mapRequestToRoute()` | `FilterPropertiesDecorator.java` | Cases for all three → return null (no routing filter needed) |
| `InterruptLoopBreakerPromptContributorFactory.resolveMapping()` | `InterruptLoopBreakerPromptContributorFactory.java` | Cases for all three → return null (no interrupt loop mapping) |
| `WeAreHerePromptContributor` | `WeAreHerePromptContributor.java` | Cases for all three → appropriate "you are in a conversation with [agent/controller]" context |
| `CurationHistoryContextContributorFactory` | `CurationHistoryContextContributorFactory.java` | Cases for all three → include conversation history |
| `PromptContext.chatId()` | `PromptContext.java` | Cases for all three → return targetAgentKey (or controller per-target key for AgentToControllerRequest) |
| All `RequestDecorator` implementations | Various | Handle or pass through the new types |
| All `ResultDecorator` implementations | Various | Handle or pass through (result is String, not a routing record) |
