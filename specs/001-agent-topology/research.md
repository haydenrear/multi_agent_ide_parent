# Research: Inter-Agent Communication Topology & Conversational Structured Channels

## R1: How to register new tools (list_agents, call_agent, call_controller)

**Decision**: Add new `@Tool`-annotated methods to `AcpTooling.java` with `@SetFromHeader(MCP_SESSION_HEADER)` for session identification.

**Rationale**: This follows the established pattern for all existing tools (read, edit, write, bash). The `@Tool` annotation is processed by `SpecialMethodToolCallbackProviderFactory` in `SpringMcpConfig`, and `@SetFromHeader` parameters are automatically excluded from JSON schema via `SkipSetFromSessionHeader` filter. The `McpRequestContext` ThreadLocal provides header access during tool execution.

**Alternatives considered**:
- Separate tool carrier class: Rejected — would require additional Spring bean registration and breaks the single-carrier pattern.
- REST endpoints: Rejected — tools are the established mechanism for agent-to-system interaction. REST is for external (human/controller) interaction.

**Key files**:
- `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/AcpTooling.java` — existing tool registration
- `multi_agent_ide/src/main/java/com/hayden/multiagentide/config/SpringMcpConfig.java` — tool callback factory
- `acp-cdc-ai/src/main/kotlin/com/hayden/acp_cdc_ai/acp/AcpChatModel.kt` — `MCP_SESSION_HEADER` constant

## R2: How to determine agent session state (open/closed/busy/dispatched)

**Decision**: Query `BlackboardHistory` and `AgentProcess` via `AcpSessionManager` to determine session state. An agent is:
- **Open**: Has an active `AcpSessionContext` in `AcpSessionManager.sessionContexts` and its `AgentProcess` status is `RUNNING` or `WAITING`
- **Closed/Dispatched**: Session has been removed from `AcpSessionManager` or `AgentProcess` status is `COMPLETED`/`FAILED`/`KILLED`/`TERMINATED`
- **Busy**: `AgentProcess` status is `RUNNING` and currently processing a tool call (detectable via active `CompletableDeferred` in permission gate)

**Rationale**: `AcpSessionManager` already tracks all active sessions via `ConcurrentHashMap<Any, AcpSessionContext>`. `AgentProcess` (ThreadLocal via `AgentProcess.get()`) provides execution status. `BlackboardHistory` event subscription tracks all agent lifecycle events.

**Alternatives considered**:
- Polling the workflow graph: Rejected — too slow for real-time session discovery, and the graph represents logical workflow state not session state.
- Custom session registry: Rejected — `AcpSessionManager` already has the information; adding another registry creates consistency risk.

**Key files**:
- `acp-cdc-ai/src/main/kotlin/com/hayden/acp_cdc_ai/acp/AcpSessionManager.kt` — session lifecycle
- `embabel-agent-api/src/main/kotlin/com/embabel/agent/core/AgentProcess.kt` — process status codes
- `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/agent/BlackboardHistory.java` — event subscription

## R3: How to enforce communication topology at runtime

**Decision**: Define a topology matrix in `application.yml` configuration as a map of `AgentType -> Set<AgentType>` (allowed targets). At runtime, `AgentCommunicationService` validates each `call_agent` request against the intersection of (configured permissions) AND (open sessions). The default topology allows: worker -> own orchestrator/collector, orchestrator/collector -> peers at same level, orchestrator/collector -> workers under them.

**Rationale**: Configuration-based topology allows runtime reconfiguration without restart (SC-008). The intersection model ensures topology rules AND session availability are both checked. This aligns with the existing pattern of Spring `@ConfigurationProperties` for system behavior.

**Alternatives considered**:
- Hardcoded topology: Rejected — violates SC-008 (reconfigurable without restart).
- Database-stored topology: Rejected — over-engineering for a configuration that changes rarely and doesn't need persistence across restarts.

## R4: How to detect communication loops

**Decision**: Track the call chain as a `List<String>` of agent session IDs, threaded through each `call_agent` invocation. Before delivering a message, check if the target session ID already appears in the chain. If so, reject with a loop-detected error containing the full chain. The chain is stored as a tool parameter (not in BlackboardHistory) since it's scoped to a single communication flow.

**Rationale**: Simple list-based detection catches both direct recursion (A -> B -> A) and indirect loops (A -> B -> C -> A). Threading the chain through tool parameters means it's inherently scoped to the communication flow and doesn't pollute session state.

**Alternatives considered**:
- BlackboardHistory-based detection: Rejected — BlackboardHistory is per-session, but the call chain spans multiple sessions.
- Graph-based detection: Rejected — over-engineering; a simple list check is O(n) where n is chain length (bounded by max depth).

## R5: How to implement call_controller using the permission gate

**Decision**: `call_controller` publishes an interrupt-like request via `PermissionGate.publishInterrupt()` with type `HUMAN_REVIEW`. The controller (Claude Code) sees this as a pending interrupt in `/api/interrupts/pending` and resolves it via `/api/interrupts/resolve` with `resolutionNotes` containing the controller's response. The resolution is fed back to the agent via a prompt contributor that reads the interrupt resolution from the graph.

**Rationale**: This reuses the existing permission gate infrastructure without modification. The controller already monitors interrupts as part of its standard workflow (Step 9 in standard_workflow.md). The resolution notes field is flexible enough to carry structured justification responses.

**Alternatives considered**:
- New REST endpoint for controller communication: Rejected — would require the agent to make HTTP calls, breaking the tool-based interaction model.
- WebSocket-based communication: Rejected — agents don't have WebSocket connections; they interact via tools.

**Key files**:
- `multi_agent_ide/src/main/kotlin/com/hayden/multiagentide/gate/PermissionGate.kt` — interrupt publishing/resolution
- `multi_agent_ide/src/main/java/com/hayden/multiagentide/controller/InterruptController.java` — interrupt REST API
- `multi_agent_ide/src/main/java/com/hayden/multiagentide/service/InterruptService.java` — interrupt handling

## R6: How to generate interrupt-only JSON schema with victools

**Decision**: Use the existing `SpecialJsonSchemaGenerator` (which wraps victools) with a custom `SchemaFilter` that filters out all routing object fields except the `interruptRequest` field. This is separate from the `FilterPropertiesDecorator` which operates at the LLM call level. The schema generator produces the JSON schema the agent sees; the `FilterPropertiesDecorator` ensures the LLM response parsing ignores filtered fields.

**Rationale**: `SpecialJsonSchemaGenerator` already supports `SchemaFilter` list for filtering parameters (e.g., `SkipSetFromSessionHeader`). Adding another filter for interrupt-only schema follows the same extension pattern. The victools generator is already a dependency.

**Alternatives considered**:
- Manual schema construction: Rejected — fragile, doesn't benefit from victools' Jackson/Swagger2 module integration.
- Modifying `SkipPropertyFilter` annotation: Rejected — `SkipPropertyFilter` operates at the `FilterPropertiesDecorator` level (LLM call time), not at schema generation time. Need a separate mechanism for schema generation.

**Key files**:
- `utilitymodule/src/main/java/com/hayden/utilitymodule/schema/SpecialJsonSchemaGenerator.java` — victools wrapper

## R7: Scope of Review/Merger removal

**Decision**: Remove the following across the codebase:

**Models (AgentModels.java)**:
- `ReviewRequest` record and all references
- `MergerRequest` record and all references
- `ReviewRouting` record
- `MergerRouting` record
- `ReviewRequest`/`MergerRequest` fields from `InterruptRouting`, `OrchestratorCollectorRouting`, `DiscoveryCollectorRouting`, `PlanningCollectorRouting`, `TicketCollectorRouting`
- Remove from `Routing` sealed interface permits list

**Annotations**:
- `ReviewRoute.java` — delete
- `MergerRoute.java` — delete

**Agent actions (AgentInterfaces.java)**:
- `performReview()` method (~L1946-2021)
- `performMerge()` method (~L1869-1944)
- `mapReview()` helper method (~L920-934)
- `mapMerge()` helper method (~L936-951)

**Prompt contributors**:
- `InterruptPromptContributorFactory.java` — delete entirely
- `OrchestratorRouteBackInterruptPromptContributorFactory.java` — delete entirely
- `InterruptLoopBreakerPromptContributorFactory.java` — remove `ReviewRequest`/`MergerRequest` cases from switch

**Filter decorator**:
- `FilterPropertiesDecorator.java` — remove `ReviewRoute.class`/`MergerRoute.class` from `ALL_INTERRUPT_ROUTE_ANNOTATIONS`, remove `ReviewRequest`/`MergerRequest` handling from `decorate()` and `resolveReviewMergerTargetRoute()`, remove `routeFromCollectorRequest()` helper

**Other references**: Grep for `ReviewRequest`, `MergerRequest`, `ReviewRouting`, `MergerRouting`, `ReviewRoute`, `MergerRoute`, `performReview`, `performMerge` across all Java files and remove/update.

**Retained**:
- `InterruptService.java` — still handles human interrupt resolution
- `WorktreeMergeConflictService.java` — still handles merge conflicts during worktree operations
- `WorktreeAutoCommitService.java` — still handles auto-commits
- `RouteBackInterruptPromptContributorFactory.java` — serves controller callback pattern
- `InterruptLoopBreakerPromptContributorFactory.java` — prevents degenerate human-interrupt loops (with Review/Merger cases removed)

## R8: Message interception, decorator pipeline, and ArtifactKey identity for agent communication

**Decision**: All three communication paths (agent→controller, agent→agent, controller→agent) run through the full decorator pipeline. Three new `AgentRequest` subtypes (`AgentToControllerRequest`, `AgentToAgentRequest`, `ControllerToAgentRequest`) are added to `AgentModels.java` and flow through `PromptContext` as `currentRequest`. This enables all existing decorator infrastructure to match on them in switch cases.

**ArtifactKey identity model**: nodeId = sessionId = ArtifactKey — these are all the same thing. Each communication request uses `sourceAgentKey` and `targetAgentKey` (both ArtifactKeys) to identify agents. The request's own `key` is always a child of the source agent's key. `PromptContext.chatId()` returns the **target** agent's ArtifactKey for all communication request types. For `AgentToControllerRequest`, the controller's per-target key (child of root, looked up from GraphRepository) is used as the chatId.

**Controller identity & conversation key lifecycle**: The controller is not a regular agent — it doesn't have a persistent ArtifactKey. The system manages a per-target conversation key for each (controller, target agent) pair. The key is created on whichever call happens first (either `call_controller` from the agent side or `/api/agent-conversations/respond` from the controller side) via `root.createChild()`, persisted as a `ControllerToAgentConversationNode` in GraphRepository with its `targetAgentKey` field set. On subsequent calls, the system looks up the existing node by the `targetAgentKey` field (a proper field on the node, not a metadata map entry). If the controller provides a `controllerConversationKey`, the system validates it (must exist AND `targetAgentKey` field must match); if invalid or mismatched, it logs a warning and falls back to lookup-by-target. The resolved key is always returned in every response so the controller can reuse it, but the system never relies on the controller sending it back.

**GraphNode persistence**: Three new `GraphNode` implementations (`AgentToAgentConversationNode`, `AgentToControllerConversationNode`, `ControllerToAgentConversationNode`) are added to the `GraphNode` sealed interface permits list. Each stores source/target keys as proper fields (not metadata map entries) for lookup via `GraphRepository.findByType()` and `findByParentId()`.

For agent→controller and agent→agent: the tool call handler in `AcpTooling` builds the appropriate request, persists the corresponding conversation GraphNode, constructs a `PromptContext`, runs through `PromptContextDecorator`s and `DecorateRequestResults`, then runs through `DefaultLlmRunner` with `String.class` as the structured response type.

For controller→agent: a **dedicated** `AgentConversationController` (separate from `InterruptController`) receives the controller's response via `POST /api/agent-conversations/respond`. The separation ensures we know definitively the message is an agent conversation response. The controller resolves the `controllerConversationKey` (using it if provided, otherwise looking up or creating from GraphRepository), builds a `ControllerToAgentRequest`, persists a `ControllerToAgentConversationNode`, runs through the same decorator pipeline, and delivers the enriched message.

**Rationale**: Using the existing decorator pipeline means all prompt contributors, request decorators, and result decorators can intercept and enrich communication messages. The `AgentRequest` subtype in the `PromptContext` tells each decorator/contributor what kind of communication is happening, so it can contribute appropriately (e.g., justification templates for agent→controller, topology context for agent→agent). GraphNode persistence enables conversation history lookup and cross-session searchability.

A separate REST controller for controller→agent (rather than routing through `InterruptController`) is necessary because:
1. We need to intercept and add prompt contributors specific to agent conversations
2. The prompt contributor switch cases need to cleanly distinguish controller-to-agent messages from regular interrupt resolutions
3. The InterruptController handles interrupt lifecycle (publish/resolve/status/detail) — agent conversations are a different concern

**Decorator audit required**: All decorators that switch on `AgentRequest` subtypes must add cases for the three new types. Key locations:
- `FilterPropertiesDecorator.mapRequestToRoute()` — return null (no routing filter)
- `InterruptLoopBreakerPromptContributorFactory.resolveMapping()` — return null
- `WeAreHerePromptContributor` — "you are in a conversation with [agent/controller]"
- `CurationHistoryContextContributorFactory` — include conversation history
- `PromptContext.chatId()` — return targetAgentKey (or controller per-target key for AgentToControllerRequest)
- All `RequestDecorator` / `ResultDecorator` implementations — handle or pass through

**Key files**:
- `multi_agent_ide/src/main/java/com/hayden/multiagentide/agent/decorator/request/DecorateRequestResults.java` — decorator orchestration
- `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/llm/LlmRunner.java` / `DefaultLlmRunner` — LLM execution pipeline
- `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/model/nodes/GraphNode.java` — sealed interface, needs three new conversation node types in permits list

## R9: Prompt contributor factory pattern for justification conversations

**Decision**: Create `JustificationPromptContributorFactory` that matches on the `currentRequest` type in `PromptContext` and blackboard history to detect when an agent is at a justification point. The factory inspects:
1. The current request type (determines agent role)
2. Whether the blackboard history already contains a controller approval for this agent's output (skip if already approved)
3. The workflow phase (discovery, planning, ticket)

When matched, it injects a role-specific justification template instructing the agent to call the `call_controller` tool with its interpretation and findings mapping.

**Rationale**: This follows the established `PromptContributorFactory` pattern used by `InterruptLoopBreakerPromptContributorFactory`, `RouteBackInterruptPromptContributorFactory`, etc. The factory's `create(PromptContext)` method receives the full context needed for history matching.

**Key files**:
- `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/prompt/PromptContributorFactory.java` — factory interface
- `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/prompt/PromptContextFactory.java` — context assembly

## R10: SessionKeyResolutionService — centralizing session identity logic

**Decision**: Introduce `SessionKeyResolutionService` to centralize three pieces of scattered session logic:

1. **Session key resolution** (from `AiFilterSessionResolver`): The `resolveSessionKey()` method with its `SessionMode`-based scoping (`PER_INVOCATION`, `SAME_SESSION_FOR_ACTION`, `SAME_SESSION_FOR_ALL`, `SAME_SESSION_FOR_AGENT`), the `SessionScopeKey` cache, and lifecycle eviction on `GoalCompletedEvent`/`ActionCompletedEvent`.

2. **Message-to-session routing** (from `MultiAgentEmbabelConfig.llmOutputChannel()` lines 208-260): The inline lambda currently queries `graphRepository.getAllMatching(ChatSessionCreatedEvent.class, n -> matchesThisSession(evt, n))` to find the closest session key for a `MessageOutputChannelEvent`, then sets `EventBus.Process` and calls `chatModel.call()`. This session resolution logic moves to the service; the config bean becomes a thin delegate.

3. **Self-call filtering**: New `filterSelfCalls(callingKey, candidateKeys)` method that checks ArtifactKey ancestry (equality, ancestor-of, descendant-of) to prevent agents from calling themselves when AI pipeline tools or worktree services share session identity with the calling agent.

**Rationale**: The current `AiFilterSessionResolver` already handles most of the session key resolution, but it's scoped only to AI filter/propagator/transformer tools and doesn't participate in topology enforcement. The `MultiAgentEmbabelConfig` message routing is a critical piece of session-to-message plumbing buried in a config bean lambda — it's hard to test, hard to extend (e.g., adding provenance markers), and disconnected from the session key resolution that creates the keys it's trying to match. Centralizing both into one service creates a single source of truth for "given X, what session does it belong to?"

**Alternatives considered**:
- Keep `AiFilterSessionResolver` separate and just add `filterSelfCalls` to `AgentCommunicationService`: Rejected — the message routing in `MultiAgentEmbabelConfig` still needs the same resolution logic, and having two services that both resolve session keys creates divergence risk.
- Add the filter to `AiFilterSessionResolver` directly: Rejected — `AiFilterSessionResolver` is in the `filter.service` package and shouldn't know about topology enforcement or agent communication.

**Key files**:
- `multi_agent_ide/src/main/java/com/hayden/multiagentide/filter/service/AiFilterSessionResolver.java` — current session resolution (to be absorbed)
- `multi_agent_ide/src/main/java/com/hayden/multiagentide/config/MultiAgentEmbabelConfig.java` L208-260 — current message routing (to be absorbed)
- `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/prompt/PromptContext.java` — `chatId()` resolution for CommitAgentRequest/MergeConflictRequest
- `multi_agent_ide_lib/src/main/java/com/hayden/multiagentidelib/filter/model/executor/AiFilterTool.java` — `SessionMode` enum definition
