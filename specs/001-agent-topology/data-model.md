# Data Model: Inter-Agent Communication Topology

## Entities

### CommunicationTopologyConfig

Runtime-configurable topology matrix defining allowed agent-to-agent communication.

| Field | Type | Description |
|-------|------|-------------|
| allowedCommunications | Map<AgentType, Set<AgentType>> | Permitted caller -> target mappings |
| maxCallChainDepth | int | Maximum hops in a single communication flow (default: 5) |
| messageBudget | int | Max messages per justification conversation before user escalation (default: 3) |

**Source**: `application.yml` via `@ConfigurationProperties`
**Validation**: Non-null map, positive integers for depth/budget.

**Agent types participating in topology** (from `AgentType` enum):
- Workflow agents: `ORCHESTRATOR`, `ORCHESTRATOR_COLLECTOR`, `DISCOVERY_ORCHESTRATOR`, `DISCOVERY_COLLECTOR`, `DISCOVERY_AGENT`, `PLANNING_ORCHESTRATOR`, `PLANNING_COLLECTOR`, `PLANNING_AGENT`, `TICKET_ORCHESTRATOR`, `TICKET_COLLECTOR`, `TICKET_AGENT`, `CONTEXT_MANAGER`
- Worktree service agents: `COMMIT_AGENT` (WorktreeAutoCommitService), `MERGE_CONFLICT_AGENT` (WorktreeMergeConflictService) — run LLM calls with own PromptContext, chatId = contextId.parent() (calling agent's key)
- AI pipeline tool agents: `AI_FILTER` (AiFilterTool), `AI_PROPAGATOR` (AiPropagatorTool), `AI_TRANSFORMER` (AiTransformerTool) — run inline LLM calls, session identity depends on `SessionMode` via `AiFilterSessionResolver`
- Dispatch wrappers: `DISCOVERY_AGENT_DISPATCH`, `PLANNING_AGENT_DISPATCH`, `TICKET_AGENT_DISPATCH` — not directly addressable via topology (internal dispatch mechanism)
- Interrupt service agents: `REVIEW_AGENT` (InterruptService.runInterruptAgentReview), `REVIEW_RESOLUTION_AGENT` (InterruptService.handleInterrupt) — run LLM calls on behalf of interrupted agents, can call back to any workflow agent
- Removed/being removed: `MERGER_AGENT` — part of interrupt simplification cleanup (FR-022/FR-023)

### AgentAvailabilityEntry

Represents a reachable agent for list_agents results.

**Identity note**: `agentKey` is the single ArtifactKey that serves as both nodeId and sessionId — these are all the same thing.

| Field | Type | Description |
|-------|------|-------------|
| agentKey | ArtifactKey | The agent's ArtifactKey (= nodeId = sessionId) |
| agentType | AgentType | Role (ORCHESTRATOR, DISCOVERY_AGENT, etc.) |
| busy | boolean | True if currently processing a tool call |
| callableByCurrentAgent | boolean | True if topology permits the caller to contact this agent |

**Source**: Computed at runtime from AcpSessionManager + AgentProcess + topology config.
**Not persisted** — ephemeral query result.

### CallChain

Tracks the path of a communication request through agents for loop detection.

| Field | Type | Description |
|-------|------|-------------|
| entries | List<CallChainEntry> | Ordered sequence of agent identifiers |
| initiatorKey | ArtifactKey | Agent that started the chain |

### CallChainEntry

| Field | Type | Description |
|-------|------|-------------|
| agentKey | ArtifactKey | The agent's ArtifactKey (= nodeId = sessionId) |
| agentType | AgentType | Agent role |
| timestamp | Instant | When this hop occurred |

**Source**: Threaded through tool call parameters (not persisted in BlackboardHistory).
**Validation**: Loop detected when target agentKey already appears in entries.

### AgentCallEvent (extends Events.GraphEvent)

Structured event for all inter-agent and controller↔agent communication actions. Used for both `call_agent` and `call_controller` flows.

| Field | Type | Description |
|-------|------|-------------|
| eventId | String | Unique event identifier |
| timestamp | Instant | Event time |
| eventType | AgentCallEventType | INITIATED, INTERMEDIARY, RETURNED, ERROR |
| callerKey | ArtifactKey | Calling agent's or controller's ArtifactKey |
| callerAgentType | AgentType | Calling agent's role (null for controller-initiated) |
| targetKey | ArtifactKey | Target agent's ArtifactKey |
| targetAgentType | AgentType | Target agent's role |
| callChain | List<CallChainEntry> | Full chain at time of event |
| availableAgents | List<AgentAvailabilityEntry> | Available agents at time of event |
| message | String | Communication content (for INITIATED) |
| response | String | Response content (for RETURNED) |
| errorDetail | String | Error details (for ERROR only) |
| checklistAction | String | Checklist action identifier (e.g. `VERIFY_REQUIREMENTS_MAPPING`) when the call is part of a structured checklist review. Null for non-checklist calls. Stored as a plain string for queryability. |

### AgentCallEventType (enum)

| Value | Description |
|-------|-------------|
| INITIATED | Caller sent message to target |
| INTERMEDIARY | Target forwarded to another agent |
| RETURNED | Target responded to caller |
| ERROR | Call failed (target unavailable, topology violation, loop detected) |

## ArtifactKey Identity Model

**nodeId = sessionId = ArtifactKey** — these are all the same thing. When this document says "target agent" or "source agent," the identifier is always a single ArtifactKey.

### Key hierarchy for communication requests

- `sourceAgentKey` (ArtifactKey) — the agent sending the message
- `targetAgentKey` (ArtifactKey) — the agent receiving the message
- `key` (ArtifactKey) — the request's own key, a **child** of `sourceAgentKey` (like all other AgentRequests)

### Controller identity & conversation key lifecycle

The controller (Claude Code) is not a regular agent — it doesn't have a persistent ArtifactKey in the graph. The system manages a **per-target conversation key** for each (controller, target agent) pair. This key is created once and reused for all subsequent conversations with that target.

**Resolution algorithm** (used by both `call_controller` and `/api/agent-conversations/respond`):

1. **If the controller provides a `controllerConversationKey`**:
   a. Look it up in GraphRepository (`ControllerToAgentConversationNode` by nodeId).
   b. If found, **validate** that the node's `targetAgentKey` field matches the `targetAgentKey` in the current request. If it doesn't match, log a warning and fall through to step 2 (treat as if no key was provided).
   c. If not found (stale/invalid key), log a warning and fall through to step 2.
   d. If found and valid, use it.

2. **If no key was provided, or the provided key was invalid**:
   a. Look up an existing `ControllerToAgentConversationNode` in GraphRepository by querying `targetAgentKey = <targetAgentKey>` under the root node's children.
   b. If found, reuse its key.
   c. If not found, create a new ArtifactKey via `root.createChild()`, persist a new `ControllerToAgentConversationNode` in GraphRepository with `targetAgentKey` set.

3. **Always return the resolved key** in the response payload — both in the `call_controller` tool response (so it's in the agent's context for observability) and in the `/api/agent-conversations/respond` response (so the controller can include it in future calls). The controller is asked to send it back, but the system never relies on it — the lookup-by-target fallback (step 2) guarantees correctness even if the controller ignores the key entirely.

**When is the key created?** The key is created on whichever call happens first for a given target agent:
- If an agent calls `call_controller` first, the system creates the key during step 6 of the `call_controller` pipeline (before publishing the interrupt).
- If the controller responds via `/api/agent-conversations/respond` first (unlikely but possible in edge cases), the system creates the key during step 1 of the respond pipeline.

### PromptContext.chatId() for communication requests

`chatId()` must return the **target** agent's ArtifactKey (the session we're sending into):
- `AgentToAgentRequest` → `targetAgentKey`
- `AgentToControllerRequest` → the controller's per-target key (child of root, from GraphRepository lookup)
- `ControllerToAgentRequest` → `targetAgentKey`

## Communication AgentRequest Subtypes

These are new `AgentRequest` implementations that flow through `PromptContext` as `currentRequest`, enabling all decorator infrastructure to match on them.

### AgentToControllerRequest

Agent sends a justification message to the controller. Built when `call_controller` tool is invoked.

| Field | Type | Description |
|-------|------|-------------|
| sourceAgentKey | ArtifactKey | The calling agent's ArtifactKey |
| sourceAgentType | AgentType | Role of the calling agent |
| justificationMessage | String | Structured justification content |
| goal | String | Current goal context |
| key | ArtifactKey | Child of sourceAgentKey |

### AgentToAgentRequest

Agent sends a message to another agent. Built when `call_agent` tool is invoked.

| Field | Type | Description |
|-------|------|-------------|
| sourceAgentKey | ArtifactKey | The calling agent's ArtifactKey |
| sourceAgentType | AgentType | Role of the calling agent |
| targetAgentKey | ArtifactKey | The target agent's ArtifactKey |
| targetAgentType | AgentType | Role of the target agent |
| message | String | Message content |
| callChain | List<CallChainEntry> | Current call chain for loop detection |
| goal | String | Current goal context |
| key | ArtifactKey | Child of sourceAgentKey |

### ControllerToAgentRequest

Controller sends a response to an agent. Built when the controller responds via `POST /api/agent-conversations/respond`.

| Field | Type | Description |
|-------|------|-------------|
| sourceKey | ArtifactKey | Controller's per-target key (child of root) |
| targetAgentKey | ArtifactKey | The target agent's ArtifactKey |
| targetAgentType | AgentType | Role of the target agent |
| message | String | Controller's response content |
| checklistAction | ChecklistAction | Optional — present when responding as part of a structured checklist review |
| goal | String | Current goal context |
| key | ArtifactKey | Child of sourceKey |

**All three**: Implement `AgentRequest` sealed interface. Run through full decorator pipeline (PromptContextDecorator, RequestDecorator, ResultDecorator, PromptContributorFactory). Structured response type is `String.class` (not a routing record).

### ChecklistAction

Represents a completed step in a conversational topology checklist. Referenced in `ControllerToAgentRequest` and the REST payload.

| Field | Type | Description |
|-------|------|-------------|
| actionType | String | ACTION identifier from checklist row (e.g. `VERIFY_REQUIREMENTS_MAPPING`, `CHALLENGE_ASSUMPTIONS`) |
| completedStep | String | Reference to the checklist step (e.g. `checklist-discovery-agent.md#step-3`) |
| stepDescription | String | Human-readable description of the completed step |

### Artifact.ControllerChecklistTurn

Emitted when a `ControllerToAgentRequest` contains a `checklistAction`. Extracted and persisted as an artifact but **NOT** passed to the model. Provides an audit trail of checklist progress through the justification conversation.

| Field | Type | Description |
|-------|------|-------------|
| targetAgentKey | ArtifactKey | The agent this checklist turn is directed at |
| targetAgentType | AgentType | Role of the target agent |
| checklistAction | ChecklistAction | The action type and completed step reference |
| controllerMessage | String | The controller's message for this turn |
| conversationKey | ArtifactKey | Controller conversation key |
| timestamp | Instant | When this turn occurred |

**Not passed to model** — persisted for audit/observability only. Queryable via artifact repository.

## Graph Nodes for Communication

Three new `GraphNode` implementations added to `GraphNode.java` sealed interface permits list. These persist communication relationships in `GraphRepository` for searchability.

### AgentToAgentConversationNode

| Field | Type | Description |
|-------|------|-------------|
| nodeId | String | The request's ArtifactKey |
| title | String | Descriptive title |
| goal | String | Current goal context |
| status | Events.NodeStatus | Node status |
| parentNodeId | String | sourceAgentKey |
| childNodeIds | List<String> | Child nodes |
| sourceAgentKey | String | The calling agent's ArtifactKey |
| sourceAgentType | AgentType | Role of the calling agent |
| targetAgentKey | String | The target agent's ArtifactKey |
| targetAgentType | AgentType | Role of the target agent |
| createdAt | Instant | Creation time |
| lastUpdatedAt | Instant | Last update time |
| nodeType | Events.NodeType | `AGENT_TO_AGENT_CONVERSATION` |

### AgentToControllerConversationNode

| Field | Type | Description |
|-------|------|-------------|
| nodeId | String | The request's ArtifactKey |
| title | String | Descriptive title |
| goal | String | Current goal context |
| status | Events.NodeStatus | Node status |
| parentNodeId | String | sourceAgentKey |
| childNodeIds | List<String> | Child nodes |
| sourceAgentKey | String | The calling agent's ArtifactKey |
| sourceAgentType | AgentType | Role of the calling agent |
| createdAt | Instant | Creation time |
| lastUpdatedAt | Instant | Last update time |
| nodeType | Events.NodeType | `AGENT_TO_CONTROLLER_CONVERSATION` |

### ControllerToAgentConversationNode

| Field | Type | Description |
|-------|------|-------------|
| nodeId | String | The request's ArtifactKey |
| title | String | Descriptive title |
| goal | String | Current goal context |
| status | Events.NodeStatus | Node status |
| parentNodeId | String | Controller's per-target key (child of root) |
| childNodeIds | List<String> | Child nodes |
| targetAgentKey | String | The target agent's ArtifactKey — used for lookup in the resolution algorithm |
| targetAgentType | AgentType | Role of the target agent |
| createdAt | Instant | Creation time |
| lastUpdatedAt | Instant | Last update time |
| nodeType | Events.NodeType | `CONTROLLER_TO_AGENT_CONVERSATION` |

Searchable via existing `GraphRepository.findByType()` and `findByParentId()`. Source/target keys are proper fields, not metadata map entries — they are functional fields used by the resolution algorithm, not descriptors.

## AgentConversationController (new REST controller)

Dedicated controller for controller → agent responses. Separate from InterruptController so the system can definitively identify agent conversation messages and intercept them with the correct prompt contributors.

| Endpoint | Method | Request | Purpose |
|----------|--------|---------|---------|
| /api/agent-conversations/respond | POST | `{targetAgentKey, message, controllerConversationKey, checklistAction}` | Controller sends response to an agent, intercepted for prompt contributor enrichment. `controllerConversationKey` is optional — if null, looked up or created from GraphRepository. `checklistAction` is optional — when present, triggers `Artifact.ControllerChecklistTurn` emission. |

## Removed Entities

The following are removed from `AgentModels.java` as part of the interrupt simplification:

| Entity | Reason for Removal |
|--------|--------------------|
| ReviewRequest | Replaced by call_controller justification conversations |
| MergerRequest | Replaced by call_controller justification conversations |
| ReviewRouting | No longer needed — review action removed |
| MergerRouting | No longer needed — merger action removed |
| ReviewRoute (annotation) | No routing target for review |
| MergerRoute (annotation) | No routing target for merger |
| ReviewInterruptRequest | No agent-initiated review interrupts |
| MergerInterruptRequest | No agent-initiated merger interrupts |
| CollectorDecision (enum: ADVANCE_PHASE, ROUTE_BACK, STOP) | Removed from all *CollectorResult types — collectors always route forward; route-back handled by controller conference + AddMessage schema injection (FR-032g) |
| RouteBackInterruptPromptContributorFactory | Replaced by controller conference mechanism — controller decides route-back and injects route-back schema via AddMessage (FR-026) |

## Modified Entities

### InterruptController.InterruptRequest (REST payload, modified)

Add `rerouteToAgentType` field. This is the primary mechanism by which the human/controller specifies where the interrupted agent should route to.

| Field | Type | Description |
|-------|------|-------------|
| type | Events.InterruptType | Interrupt type (HUMAN_REVIEW, PAUSE, STOP, PRUNE) |
| originNodeId | String | ArtifactKey of the target agent node to interrupt |
| reason | String | Human-readable reason for the interrupt |
| **rerouteToAgentType** | **AgentType** | **Required. Target agent type to reroute the interrupted agent to. Must be a workflow agent type — cannot route to ALL, COMMIT_AGENT, MERGE_CONFLICT_AGENT, AI_FILTER, AI_PROPAGATOR, AI_TRANSFORMER, REVIEW_AGENT, REVIEW_RESOLUTION_AGENT, MERGER_AGENT, CONTEXT_MANAGER. Flows through to `InterruptRequestEvent.rerouteToAgentType` → `FilterPropertiesDecorator`. Interrupt is now exclusively for controller-initiated rerouting.** |

### InterruptRequestEvent (modified)

`rerouteToAgentType` already exists on this event. The change is that it is now **populated from the REST payload** rather than being set by agent-initiated logic (which is removed). The `FilterPropertiesDecorator.resolveTargetRoute()` reads this field, maps it via `mapAgentTypeToRoute()`, and filters all route annotations except the target.

### InterruptRouting (modified)

Remove `reviewRequest` and `mergerRequest` fields. Remaining fields unchanged.

### All Routing records — @SkipPropertyFilter annotations (modified)

All `interruptRequest` fields on all routing objects MUST be annotated with `@SkipPropertyFilter` (FR-028). The LLM must not see interrupt fields during normal operation — they are only revealed when the controller injects the interrupt schema via AddMessage.

### All Collector Routing records (modified)

Remove `reviewRequest` and `mergerRequest` fields from:
- OrchestratorCollectorRouting
- DiscoveryCollectorRouting
- PlanningCollectorRouting
- TicketCollectorRouting

Add `@SkipPropertyFilter` to all route-back request fields (FR-032f):
- `PlanningOrchestratorRequest planningRequest` on `PlanningCollectorRouting`
- `DiscoveryOrchestratorRequest discoveryRequest` on `DiscoveryCollectorRouting`
- Equivalent fields on `TicketCollectorRouting` and `OrchestratorCollectorRouting`

The LLM must not see route-back fields during normal operation — collectors always route forward. Route-back schemas are only injected by the controller when it approves route-back during a conversational topology conference (FR-032j).

### All *CollectorResult types (modified)

Remove `CollectorDecision collectorDecision` field and `CollectorDecision` enum from:
- PlanningCollectorResult
- DiscoveryCollectorResult
- TicketCollectorResult
- OrchestratorCollectorResult

Keep collector action fields — only the decision enum is removed. Route-back is now handled exclusively by controller conference (FR-032g).

### FilterPropertiesDecorator (modified)

Remove `ReviewRoute.class` and `MergerRoute.class` from:
- `ALL_INTERRUPT_ROUTE_ANNOTATIONS`
- `REVIEW_MERGER_RETURN_ROUTE_ANNOTATIONS` (remove entire set)
- `resolveReviewMergerTargetRoute()` (remove entire method)
- `mapRequestToRoute()` switch cases for ReviewRequest/MergerRequest
- `mapAgentTypeToRoute()` switch cases for REVIEW_AGENT/MERGER_AGENT

## Conversational Topology Documents (skill-level, not code entities)

These are markdown files in the controller skill, not Java entities:

| Document | Location | Purpose |
|----------|----------|---------|
| checklist.md | conversational-topology/ | General phase-gate review instructions |
| checklist-discovery-agent.md | conversational-topology/ | Discovery agent review criteria |
| checklist-planning-agent.md | conversational-topology/ | Planning agent review criteria |
| checklist-ticket-agent.md | conversational-topology/ | Ticket agent review criteria |
| reference.md | conversational-topology/ | Index of current documents |
| reference.md | conversational-topology-history/ | Chronological change log |

### Checklist ACTION Row Structure

Each step in an agent-specific checklist has an **ACTION** column that identifies the type of review action. The controller references this ACTION type in the `checklistAction` payload when responding to an agent, creating a traceable link between checklist steps and conversation turns.

Example checklist row format:

```markdown
| Step | ACTION | Description | Gate |
|------|--------|-------------|------|
| 1 | VERIFY_GOAL_UNDERSTANDING | Confirm the agent correctly interpreted the goal requirements | PASS/FAIL |
| 2 | VERIFY_REQUIREMENTS_MAPPING | Verify all goal requirements are mapped to discovered artifacts | PASS/FAIL |
| 3 | CHALLENGE_ASSUMPTIONS | Challenge unstated assumptions in the agent's interpretation | PASS/FAIL/DEFER |
| 4 | VERIFY_COMPLETENESS | Check for missing coverage areas not addressed by the agent | PASS/FAIL |
```

The `completedStep` field in `ChecklistAction` references the step using `{checklist-filename}#step-{N}` format (e.g. `checklist-discovery-agent.md#step-2`). Each `Artifact.ControllerChecklistTurn` emitted ties the controller's message to a specific ACTION and step, creating an audit trail without passing checklist metadata to the model.
