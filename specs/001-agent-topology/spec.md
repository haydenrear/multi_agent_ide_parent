# Feature Specification: Inter-Agent Communication Topology & Conversational Structured Channels

**Feature Branch**: `001-agent-topology`
**Created**: 2026-03-20
**Updated**: 2026-03-21
**Status**: Draft
**Input**: User description: "Add inter-agent communication tools (call_agent, list_agents, call_controller) with configurable topology, loop detection, error handling, and event emission. Add structured conversational topology for controller-agent and agent-agent justification conversations. Simplify interrupt model to human-only routing with schema-based filtering."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - List Available Agents (Priority: P1)

An agent running within a workflow session needs to discover which other agents are currently available for communication. The system dynamically detects which agent sessions are open (excluding dispatched agents whose sessions are closed) and returns only those that can accept messages. The agent receives a list of reachable peers along with metadata about their role (orchestrator, collector, worker) and the communication rules governing who it can contact.

**Why this priority**: Without agent discovery, no inter-agent communication is possible. This is the foundational capability that all other stories depend on.

**Independent Test**: Can be fully tested by opening multiple agent sessions within a workflow, invoking the list_agents tool from one session, and verifying only open, non-dispatched sessions appear in the result with correct role metadata.

**Cucumber Test Tag**: `@list_agents`

**Acceptance Scenarios**:

1. **Given** a workflow with three agent sessions open (one orchestrator, two workers), **When** a worker agent calls list_agents, **Then** the system returns the orchestrator and the other worker with their roles and reachability status based on topology rules.
2. **Given** a workflow where one agent has been dispatched and its session is closed, **When** any agent calls list_agents, **Then** the dispatched agent does not appear in the results.
3. **Given** a workflow with topology configuration restricting worker-to-worker communication, **When** a worker agent calls list_agents, **Then** only agents permitted by the topology matrix are listed as callable.

---

### User Story 2 - Call Another Agent (Priority: P1)

An agent within the workflow needs to send a message to another agent and receive a response. The calling agent specifies the target agent and the message content. The system validates the communication is allowed by the configured topology, checks the target session is open and not currently processing another request, delivers the message, and returns the response. If the target agent is unavailable or the call fails, the caller receives a clear error indicating the agent is down with a suggestion to try an alternative route.

**Why this priority**: This is the core inter-agent communication capability. Combined with list_agents, it enables the primary workflow pattern of agents delegating to orchestrators/collectors and vice versa.

**Independent Test**: Can be fully tested by opening two agent sessions, calling from one to the other, and verifying the message is delivered and response returned. Error cases tested by calling an unavailable agent.

**Cucumber Test Tag**: `@call_agent`

**Acceptance Scenarios**:

1. **Given** two open agent sessions where communication is allowed by topology, **When** agent A calls agent B with a message, **Then** agent B receives the message and agent A receives agent B's response.
2. **Given** a target agent whose session is currently processing another request, **When** the caller attempts to call that agent, **Then** the caller receives an error indicating the agent is busy and should try another route.
3. **Given** a topology configuration that forbids direct worker-to-worker calls, **When** a worker attempts to call another worker, **Then** the system rejects the call with an error explaining the communication is not permitted by topology rules.
4. **Given** an agent call that fails due to a network or session error, **When** the failure occurs, **Then** the caller receives an error message indicating the agent is down, an AgentCallErrorEvent is emitted containing the route and call chain, and the caller is advised to try an alternative route.

---

### User Story 3 - Configurable Communication Topology (Priority: P1)

The system administrator or workflow designer configures which agents can communicate with which other agents. The default topology allows workers to call their orchestrator or collector (same level), and orchestrators/collectors to call any agent at their level or below. This topology matrix is defined in configuration and enforced at runtime. A prompt contributor injects the available communication lines into each agent's context so agents understand their communication options.

**Why this priority**: Without topology enforcement, agents could create unbounded communication patterns leading to infinite loops or resource exhaustion. This is essential for safe operation alongside call_agent.

**Independent Test**: Can be tested by defining different topology configurations and verifying that call_agent enforces the rules correctly, rejecting disallowed calls and permitting allowed ones.

**Cucumber Test Tag**: `@agent_topology_config`

**Acceptance Scenarios**:

1. **Given** a topology configuration that permits worker-to-orchestrator communication, **When** a worker calls its orchestrator, **Then** the call succeeds.
2. **Given** a topology configuration that forbids orchestrator-to-orchestrator communication, **When** one orchestrator attempts to call another, **Then** the call is rejected with a topology violation error.
3. **Given** a topology configuration is updated, **When** an agent calls list_agents, **Then** the returned list reflects the updated topology rules (intersection of open sessions and allowed communications).

---

### User Story 4 - Loop Detection in Communication Chains (Priority: P2)

The system tracks the chain of inter-agent calls within a single communication flow. If an agent receives a call that would create a cycle (agent A called B, B called C, C attempts to call A), the system detects this and rejects the call with an error. The call chain is included in all communication events for observability and self-improvement analysis.

**Implementation mechanism**: `AgentCallStartedEvent` and `AgentCallCompletedEvent` are emitted by `call_agent` (US2) and `call_controller` (US7) tools. `SessionKeyResolutionService` listens for these events and maintains an `activeCallChain` map tracking which nodes have active calls to which targets. `filterSelfCalls()` uses `isInActiveCallChain()` (BFS) to detect cycles before allowing a call.

**Shared-session agent filtering**: Multiple agents may resolve to the **same** ACP chat session. Communication requests carry two distinct identifiers: `sourceSessionId` (the calling agent's ACP session header value — identifies who is calling) and `chatId` (the target ACP session where the LLM call will be routed). `PromptContext.chatId()` reads the `chatId` field directly from the request for `AgentToAgentRequest` and `ControllerToAgentRequest`; for `AgentToControllerRequest` it logs an error and falls back to `sourceAgentKey` (controller calls route through the permission gate, not ACP). For other request types (`CommitAgentRequest`, `MergeConflictRequest`), `chatId()` resolves to `contextId.parent()` (the calling agent's key). When multiple agents share a session (e.g., via parent/scope resolution rules in `AiFilterSessionResolver` → `SessionKeyResolutionService.resolveSessionKey()`), calling into that session while it is already processing would interrupt the active thread — injecting a new request before the first response completes, corrupting the structured response or causing the context to be inherited incorrectly. `filterSelfCalls()` must therefore filter out any candidate whose resolved chatId matches an **actively operating** session of the caller. `resolveOwningNodeId()` maps session keys via `ChatSessionCreatedEvent` records to detect this overlap: if the caller's session and a candidate's session resolve to the same active chatId, the candidate is excluded to prevent session contention.

**Why this priority**: Degenerate looping can exhaust resources and block workflow progress. Loop detection is a safety mechanism that must be in place before complex communication patterns are attempted.

**Independent Test**: Can be tested by setting up a topology that allows circular communication and attempting a chain that would loop, verifying the loop is detected and broken.

**Cucumber Test Tag**: `@agent_loop_detection`

**Acceptance Scenarios**:

1. **Given** agents A, B, and C where A has called B and B has called C, **When** C attempts to call A, **Then** the system detects the loop and rejects the call with a loop-detected error containing the full chain (A -> B -> C -> A).
2. **Given** a chain of calls A -> B -> A (direct recursion), **When** B attempts to call A, **Then** the system detects the loop and rejects it.
3. **Given** a successful non-looping chain A -> B -> C, **When** each call completes, **Then** the call chain is recorded in the emitted events for observability.

---

### User Story 5 - Communication Event Emission (Priority: P2)

The system emits structured events for every inter-agent communication action. Events include: call initiated, intermediary call (forwarded), call returned (response received), and call error. Each event contains the full call chain, the agents involved, and the list of agents that were available at the time (from list_agents). These events feed into the self-improving workflow system for topology optimization.

**Why this priority**: Event emission is critical for workflow observability and self-improvement. The topology can only be optimized if communication patterns are captured.

**Independent Test**: Can be tested by performing inter-agent calls and verifying the correct events are emitted with the expected payloads including call chains and available agent lists.

**Cucumber Test Tag**: `@agent_comm_events`

**Acceptance Scenarios**:

1. **Given** agent A calls agent B successfully, **When** the call completes, **Then** events are emitted for: call initiated (A -> B), call returned (B -> A), each containing the call chain and available agents list.
2. **Given** an agent call fails, **When** the error occurs, **Then** an AgentCallErrorEvent is emitted containing the route, call chain, error details, and available agents at time of failure.
3. **Given** a multi-hop call chain A -> B -> C, **When** each hop completes, **Then** intermediary call events are emitted at each hop with the cumulative chain.

---

### User Story 6 - Agent Communication Prompt Contributor (Priority: P2)

Each agent's session context is enriched with information about which other agents it can communicate with and the communication topology rules. This prompt contributor dynamically updates as sessions open and close during the workflow. Agents use this context to make informed decisions about when and whom to call.

**Why this priority**: Agents need to know their communication options to make intelligent decisions. Without this context, agents would need to call list_agents before every communication attempt.

**Independent Test**: Can be tested by opening agent sessions and verifying the prompt context contains the correct list of callable agents and topology rules.

**Cucumber Test Tag**: `@agent_prompt_contributor`

**Acceptance Scenarios**:

1. **Given** a workflow with multiple open agent sessions, **When** an agent's session is initialized, **Then** its prompt context includes the list of callable agents and the topology rules.
2. **Given** a new agent session opens during workflow execution, **When** existing agents receive their next prompt, **Then** the newly available agent appears in their communication context.

---

### User Story 7 - Call Controller with Structured Justification (Priority: P1)

An agent can call the workflow controller to justify its outputs, request guidance on ambiguous situations, or escalate decisions. This works similarly to the existing human-review interrupt mechanism: the agent sends a structured message to the controller, the controller evaluates it (potentially consulting the user), and returns a response. The controller is a node in the conversation graph, not just a checkpoint — it has conversations with agents (structured, per justification templates) AND with the user (ad-hoc, driven by uncertainty).

The call_controller mechanism uses the existing permission gate infrastructure. When an agent calls the controller, the system publishes an interrupt-like request that the controller (or human, if the controller defers) resolves. The response is fed back to the agent as a prompt contribution on its next invocation.

**Why this priority**: This is the foundation of the structured conversational topology. Without controller communication, agents cannot justify their outputs before they are accepted, which was the root cause of the misinterpretation failures described in the structured-convos design.

**Independent Test**: Can be tested by having an agent invoke call_controller with a justification message and verifying the controller receives it, can respond, and the agent receives the response in its next prompt context.

**Cucumber Test Tag**: `@call_controller`

**Acceptance Scenarios**:

1. **Given** an agent has completed its work, **When** it calls the controller with a justification message containing its interpretation and findings, **Then** the controller receives the message and can respond with approval, rejection, or a follow-up question.
2. **Given** the controller receives a justification that has unmapped goal requirements, **When** the controller evaluates the justification, **Then** it escalates to the user with a clear summary of what is covered and what is not.
3. **Given** a message budget of k messages for a conversation, **When** the agent and controller exchange k messages without reaching alignment, **Then** the system escalates to the user automatically.
4. **Given** the controller identifies ambiguity during an agent conversation, **When** the controller decides to ask the user, **Then** the user receives the question immediately (not batched to the next gate) and the controller relays the answer back to the agent.

---

### User Story 8 - Structured Conversational Topology via Prompt Contributors (Priority: P1)

The conversational topology between agents (agent-to-agent and agent-to-controller) is not modeled as a static graph. Instead, prompt contributor factories match over blackboard history and inject conversation-structuring prompts dynamically. When the blackboard history shows that an agent is at a justification point (e.g., discovery agent about to submit findings), the appropriate prompt contributor fires and instructs the agent on how to structure its justification conversation — what to justify, what template to follow, and what the message budget is.

This replaces the current pattern where agents independently decide whether to request review. Instead, the conversational structure is injected by the system based on workflow state.

**Why this priority**: This is the mechanism by which all structured conversations are activated. Without it, agents have no guidance on when or how to justify their outputs.

**Independent Test**: Can be tested by advancing a workflow to a justification point and verifying that the correct prompt contributor fires, injecting the right justification template into the agent's context.

**Cucumber Test Tag**: `@conversational_topology`

**Acceptance Scenarios**:

1. **Given** a discovery agent is about to submit findings to the collector, **When** the prompt context is built, **Then** a justification prompt contributor fires that instructs the agent to call the controller with its interpretation of the goal and a mapping of findings to goal requirements.
2. **Given** a planning agent is about to submit ticket decomposition, **When** the prompt context is built, **Then** a justification prompt contributor fires that instructs the agent to justify each ticket with a traceable link to a goal requirement.
3. **Given** a ticket agent has completed its work, **When** the prompt context is built, **Then** a justification prompt contributor fires that instructs the agent to justify what it changed, what it verified, and what it did not change.
4. **Given** the blackboard history shows the agent has already completed its justification conversation (controller approved), **When** the prompt context is built, **Then** the justification prompt contributor does not fire again (no re-justification after approval).

---

### User Story 9 - Simplified Interrupt Model: Human-Only Routing (Priority: P0)

**Pre-requisite for agent channels.** The interrupt mechanism is simplified so that interrupts are only used for human routing. Agents no longer route to interrupts themselves — the old agent-initiated review and merge actions are removed. When the human (or controller) sends an interrupt request, the system injects a JSON schema into the agent's context that contains only the interrupt request field from the routing object (all other fields are filtered out via the existing property filter mechanism). The agent returns a result with only the interrupt request populated, and the existing interrupt action routes back correctly based on what the human said.

This removes `performReview`, `performMerge`, `ReviewRequest`, `MergerRequest`, `ReviewRouting`, `MergerRouting`, `ReviewRoute`, `MergerRoute`, and all prompt contributors that instruct agents to initiate interrupts (InterruptPromptContributorFactory, OrchestratorRouteBackInterruptPromptContributorFactory, RouteBackInterruptPromptContributorFactory). The `InterruptLoopBreakerPromptContributorFactory` is kept because it prevents degenerate loops when the human repeatedly routes to interrupt.

Additionally, the route-back mechanism is simplified: `CollectorDecision` is removed from all `*CollectorResult` types — collectors always route forward during normal operation. Route-back decisions are handled by the controller conference: when a collector calls the controller to discuss route-back, and the controller approves, the system injects a route-back schema (e.g., `Routing(PlanningOrchestratorRequest)`) into the collector's running ACP session via AddMessage, following the same pattern as interrupt schema injection. All route-back request fields on collector routing objects are annotated with `@SkipPropertyFilter` so they are hidden during normal operation and only revealed when the controller injects the route-back schema.

The `FilterPropertiesDecorator` is updated: when an interrupt request is injected for an agent, `SkipPropertyFilter` is applied to all routing object fields except the interrupt request field. A separate schema generator (victools-based, not aware of `SkipPropertyFilter`) is used to generate the JSON schema that the agent sees — this schema respects the routing object structure but filters out everything except the interrupt request field.

**Why this priority**: P0 because this simplification is a pre-requisite for the agent channels work. The old review/merge actions and agent-initiated interrupts are being replaced by structured conversations and tool calls. Both must be done in one swoop so integration testing covers the combined change.

**Independent Test**: Can be tested by sending an interrupt request from the human, verifying the agent receives a schema with only the interrupt request field, the agent returns a result with only interrupt request populated, and the system routes back correctly.

**Cucumber Test Tag**: `@simplified_interrupts`

**Acceptance Scenarios**:

1. **Given** the human sends an interrupt request to an agent with a `rerouteToAgentType`, **When** the agent's prompt context is built, **Then** a second JSON schema (`SomeOf(InterruptRequest)`) is injected that overrides the structured response type — the agent sees only the interrupt request to fill, while the original routing schema remains as context.
2. **Given** an agent receives the interrupt-only override schema, **When** the agent returns its result, **Then** only the interrupt request field is populated and the system uses `rerouteToAgentType` from the `InterruptRequestEvent` to route to the correct agent.
3. **Given** the `performReview` and `performMerge` actions have been removed, **When** any routing object is serialized, **Then** no `ReviewRequest`, `MergerRequest`, `ReviewRouting`, or `MergerRouting` fields are present.
4. **Given** the old interrupt prompt contributors (InterruptPromptContributorFactory, OrchestratorRouteBackInterruptPromptContributorFactory) have been removed, **When** any agent's prompt context is built, **Then** no prompt instructs the agent to initiate an interrupt request on its own.
5. **Given** the InterruptController receives an interrupt request from the human, **When** it processes the request, **Then** it injects the appropriate prompt contributors (including the interrupt schema) and uses the existing mechanism for filtering possible result types on the interrupt action.
6. **Given** a collector agent is conferring with the controller via the conversational topology endpoint, and the controller decides to route back, **When** the controller responds with `routeBack=true`, **Then** the system injects a route-back schema (e.g., `Routing(PlanningOrchestratorRequest)`) into the collector's running session via AddMessage, the collector fills the route-back request, and the system routes to the target orchestrator.
7. **Given** the controller responds with `routeBack=true` during a conference with an agent that is not a collector (e.g., an orchestrator), **When** the endpoint processes the response, **Then** it returns an error indicating route-back is not supported for that agent type.
8. **Given** the `CollectorDecision` has been removed from all `*CollectorResult` types, **When** a collector agent returns its normal result, **Then** only forward-routing fields are populated and the system advances the workflow.

---

### User Story 10 - Conversational Topology Documents & Controller Review Guidance (Priority: P2)

The controller skill maintains a set of living instructional documents in `skills/multi_agent_ide_skills/multi_agent_ide_controller/conversational-topology/`. These define how the controller should review each agent type's output at phase gates and how it should structure justification conversations. They are not per-goal templates — they are evolving documents that improve over sessions as new failure modes are observed.

The directory contains:
- `checklist.md` — general phase-gate instructions (extract requirements, map to outputs, flag gaps, escalate to user)
- Agent-specific review documents (e.g., `checklist-discovery-agent.md`, `checklist-planning-agent.md`, `checklist-ticket-agent.md`) — review criteria tuned to each agent type's common failure modes and expected output structure
- `reference.md` — index of all conversational topology documents, tracking when they were added or changed and why

This is the mechanism for evolving the conversational topology: when the controller observes a failure mode (e.g., discovery agents pattern-matching instead of understanding architecture), it adds or updates a document with new review criteria, records the change in `reference.md`, and future sessions benefit from the improved guidance.

A companion `conversational-topology-history/` directory preserves previous versions of topology documents whenever they are changed. Each time a document in `conversational-topology/` is updated, the old version is copied to `conversational-topology-history/` with a date-stamped filename (e.g., `checklist-discovery-agent_2026-03-21.md`). The history directory has its own `reference.md` that records each change: what was modified, which goal prompted the change, and the reasoning. This makes it easy to see how the conversational topology has evolved over time and to revert changes that didn't work.

The `multi_agent_ide_controller/SKILL.md` provides full instructions for how to use both the conversational topology documents and the history directory during a controller session. The parent `SKILL-PARENT.md` briefly references their existence and purpose.

**Why this priority**: This is the enforcement mechanism for the structured conversations. Without these documents, the controller has no systematic way to verify that agent outputs cover the goal requirements. The living-document approach means the review guidance improves over time.

**Independent Test**: Can be tested by verifying the controller skill loads the correct document at each phase gate and follows its instructions.

**Cucumber Test Tag**: `@conversational_topology_docs`

**Acceptance Scenarios**:

1. **Given** the controller skill is loaded, **When** the controller reads `conversational-topology/reference.md`, **Then** it finds an index of all available documents with descriptions and change history.
2. **Given** the workflow reaches the discovery -> planning gate, **When** the controller consults `conversational-topology/checklist-discovery-agent.md`, **Then** it follows the review criteria to map each goal requirement to a discovery finding and flags unmapped ones.
3. **Given** the workflow reaches the planning -> tickets gate, **When** the controller consults `conversational-topology/checklist-planning-agent.md`, **Then** it follows the review criteria to verify ticket-to-requirement traceability and flags scope creep.
4. **Given** the controller observes a new failure mode during a session, **When** it updates a document to address the failure, **Then** it records the change in `conversational-topology/reference.md` with the reason for the update.
5. **Given** the controller adds a new agent-specific document, **When** it creates the file, **Then** it adds an entry to `conversational-topology/reference.md` explaining what it covers and why it was added.
6. **Given** the controller updates an existing topology document, **When** it saves the new version, **Then** the previous version is copied to `conversational-topology-history/` with a date-stamped filename and `conversational-topology-history/reference.md` is updated with the goal that prompted the change and the reasoning.
7. **Given** the controller wants to understand how the topology has evolved, **When** it reads `conversational-topology-history/reference.md`, **Then** it finds a chronological log of all changes with goals, reasons, and pointers to the old versions.
8. **Given** `SKILL-PARENT.md` is loaded by any skill, **When** the reader looks for conversational topology guidance, **Then** it finds a brief reference pointing to the controller skill's `conversational-topology/` and `conversational-topology-history/` directories for full instructions.

---

### Edge Cases

- What happens when an agent calls itself? The system should reject self-calls as a degenerate loop via `SessionKeyResolutionService.filterSelfCalls()` (FR-013c). This includes implicit self-calls where AI pipeline tools (`AiFilterTool`, `AiPropagatorTool`, `AiTransformerTool`) share the same session identity as the calling agent due to `SAME_SESSION_FOR_AGENT` session mode, and where `WorktreeAutoCommitService`/`WorktreeMergeConflictService` sessions resolve `chatId()` to `contextId.parent()` (the calling agent's key).
- What happens when a commit agent or merge conflict agent wants to call back to the agent that created the commits/conflicts? The topology permits this (COMMIT_AGENT and MERGE_CONFLICT_AGENT can call any workflow agent), but the self-call filter still applies since `PromptContext.chatId()` for `CommitAgentRequest`/`MergeConflictRequest` resolves to `contextId.parent()` (the calling agent's key).
- What happens when a target agent's session closes between list_agents and call_agent? The system should return a graceful error indicating the agent is no longer available.
- What happens when the topology configuration is empty or missing? The system should fall back to a safe default (no inter-agent communication allowed) and log a warning.
- What happens when multiple agents try to call the same target simultaneously? Only one can be processed; others receive a "busy" error.
- What happens when the call chain exceeds a configurable maximum depth? The system should reject the call to prevent unbounded chains.
- What happens when a justification conversation exceeds the message budget? The system escalates to the user.
- What happens when the controller and agent disagree but the user is unavailable? The system should use the controller's judgment and log the decision for later review.
- What happens when an interrupt is sent to an agent that has already completed? The system should return an error indicating the agent session is closed.
- What happens when `rerouteToAgentType` is null or missing on a HUMAN_REVIEW interrupt? The system should reject the request with a 400 error — HUMAN_REVIEW interrupts require a routing target.
- What happens when `rerouteToAgentType` maps to an agent type that has no route annotation (e.g., DISCOVERY_AGENT, TICKET_AGENT)? `mapAgentTypeToRoute()` returns null for these leaf agent types. The system should reject the request — you can only reroute to agents that are routing targets in the Routing/SomeOf object.
- What happens when the victools schema generator encounters an unknown annotation? It should ignore it and produce the schema without that field's filtering — the `FilterPropertiesDecorator` handles the actual filtering at runtime.
- What happens when a subagent needs context reconstruction that was previously handled by `contextManagerRequest` routing? The agent uses the `call_agent` tool to call the context manager agent instead of routing via the blackboard.
- What happens when a collector wants to route back but the controller is unavailable? The collector continues forward — route-back requires explicit controller approval. The system logs the missed conference opportunity for observability.
- What happens when `routeBack=true` is set on the conversational topology response while an interrupt is also pending? Route-back and interrupt are on separate endpoints — route-back is a controller conference decision (conversational topology), interrupt is a separate controller action (interrupt endpoint). They cannot conflict on the same request.

## Requirements *(mandatory)*

### General API Constraints

- **GC-001**: All REST endpoints introduced by this feature MUST accept parameters exclusively via `@RequestBody` JSON payloads. Path variables (`@PathVariable`) and query parameters (`@RequestParam`) MUST NOT be used. ArtifactKeys and other identifiers contain slashes (e.g., `ak:01KJ.../01KK...`) which are rejected or mangled by Tomcat when percent-encoded in URL paths. This applies to all new endpoints: `AgentConversationController`, `ActivityCheckController`, and any future endpoints added as part of this feature.

### Functional Requirements

#### Inter-Agent Communication (Stories 1-6)

- **FR-001**: System MUST expose a `list_agents` tool that returns only agents with currently open, non-dispatched sessions within the caller's workflow.
- **FR-002**: System MUST expose a `call_agent` tool that sends a message to a target agent and returns the response, validating topology rules before delivery.
- **FR-003**: System MUST enforce a configurable communication topology matrix that defines which agent roles can communicate with which other roles.
- **FR-004**: System MUST detect and reject communication loops by tracking the call chain through the full request lifecycle.
- **FR-005**: System MUST reject calls to agents whose sessions are currently processing another request (busy).
- **FR-006**: System MUST emit structured events for all communication actions: call initiated, intermediary call, call returned, intermediary call returned, and call error.
- **FR-007**: Each communication event MUST include the full call chain, the calling and target agents, and the list of available agents at the time of the event.
- **FR-008**: System MUST include an AgentCallErrorEvent that contains the route (call chain) and error details when a call fails.
- **FR-009**: System MUST provide a prompt contributor that injects available communication targets and topology rules into each agent's context.
- **FR-010**: The communication topology MUST be configurable via application configuration, defining an allowable-communications matrix (set intersection of configured permissions and open sessions).
- **FR-011**: The default topology MUST allow: worker -> own orchestrator/collector, orchestrator/collector -> other orchestrators/collectors at same level, orchestrator/collector -> workers under them. Additionally, COMMIT_AGENT and MERGE_CONFLICT_AGENT can call back to any workflow agent (they need to communicate with the agent that created the commits/conflicts, on both sides for merge). AI_FILTER, AI_PROPAGATOR, and AI_TRANSFORMER can call any workflow agent (they run inline LLM calls within the decorator pipeline and may need to communicate with other agents for context). REVIEW_AGENT and REVIEW_RESOLUTION_AGENT (spawned by `InterruptService`) can call any workflow agent — they operate on behalf of whichever agent received the interrupt and may need to communicate with other agents for context during review or resolution.
- **FR-012**: System MUST gracefully handle call failures by catching exceptions and returning an error response advising the caller to try an alternative route.
- **FR-013**: System MUST introduce a `SessionKeyResolutionService` that centralizes all session key resolution, self-call filtering, and session-to-message routing logic. Specifically:
  - **FR-013a**: `SessionKeyResolutionService` MUST absorb the session key resolution logic currently in `AiFilterSessionResolver` (scope-based resolution for `PER_INVOCATION`, `SAME_SESSION_FOR_ACTION`, `SAME_SESSION_FOR_ALL`, `SAME_SESSION_FOR_AGENT` modes, cache management, lifecycle eviction).
  - **FR-013b**: `SessionKeyResolutionService` MUST absorb the `ChatSessionCreatedEvent`-based message routing logic currently inline in `MultiAgentEmbabelConfig.llmOutputChannel()` (lines 208-260). The current implementation queries `graphRepository.getAllMatching(ChatSessionCreatedEvent.class, ...)` to find the closest matching session for a `MessageOutputChannelEvent` — this belongs in the centralized service, not in a config bean lambda.
  - **FR-013c**: `SessionKeyResolutionService` MUST provide a `filterSelfCalls(callingKey, candidateKeys)` method that checks ArtifactKey ancestry (not just equality) to exclude the calling agent from `list_agents` results and reject self-targeting in `call_agent`. This covers: AI pipeline tools resolving to the calling agent's key via `SAME_SESSION_FOR_AGENT`, and worktree service agents resolving to `contextId.parent()` (the calling agent's key).
  - **FR-013d**: `SessionKeyResolutionService` MUST enrich session events with contextual information including the source node, agent type, session mode, and a `FromMessageChannelEvent` provenance marker so downstream consumers can trace where a session key originated.
- **FR-014**: System MUST enforce a configurable maximum call chain depth to prevent unbounded communication chains.
- **FR-014a**: Each communication path MUST define its own `AgentRouting` and `AgentResult` subtypes that flow through the decorator pipeline. Specifically:
  - `AgentCallRouting` / `AgentCallResult` for agent-to-agent calls (`call_agent`). `AgentCallRouting` contains the target agent's response text. `AgentCallResult` contains the contextId, response text, and worktreeContext.
  - `ControllerCallRouting` / `ControllerCallResult` for agent-to-controller calls (`call_controller`). `ControllerCallRouting` contains the controller's response text and the resolved controller conversation key. `ControllerCallResult` contains the contextId, response text, conversation key, and worktreeContext.
  - `ControllerResponseRouting` / `ControllerResponseResult` for controller-to-agent responses (`/api/agent-conversations/respond`). `ControllerResponseRouting` contains the enriched response text after decorator pipeline processing. `ControllerResponseResult` contains the contextId, response text, conversation key, and worktreeContext.
- **FR-014b**: All communication routing types (`AgentCallRouting`, `ControllerCallRouting`, `ControllerResponseRouting`) MUST be permitted by the `AgentRouting` sealed interface. All communication result types (`AgentCallResult`, `ControllerCallResult`, `ControllerResponseResult`) MUST be permitted by the `AgentResult` sealed interface. All exhaustive switches on `AgentRouting` and `AgentResult` (in decorators, event formatters, graph services, etc.) MUST handle these types.
- **FR-014c**: Each communication routing/result type MUST have a corresponding `AgentType` entry, agent name constant, action constant, method constant, and prompt template path registered in `AgentInterfaces`. These are: `AGENT_CALL` / `agent-call` / `callAgent` / `communication/agent_call` for agent-to-agent; `CONTROLLER_CALL` / `controller-call` / `callController` / `communication/controller_call` for agent-to-controller; `CONTROLLER_RESPONSE` / `controller-response` / `respondToAgent` / `communication/controller_response` for controller-to-agent.
- **FR-014d**: `CallChainEntry` MUST capture both the source and target of each hop. Fields: `agentKey` (source), `agentType` (source), `targetAgentKey`, `targetAgentType`, `timestamp`. `buildCallChainFromGraph()` MUST populate both source and target from `AgentToAgentConversationNode` fields when constructing entries. Loop detection and event emission MUST use the enriched entries.
- **FR-014e**: The full call chain (`List<CallChainEntry>`) MUST be persisted on `AgentToAgentConversationNode` at creation time and kept up to date. This is the authoritative record of the communication path for each agent-to-agent conversation — `buildCallChainFromGraph()` reads it back when reconstructing chains. The call chain MUST be saved by `StartWorkflowRequestDecorator` (or equivalent) when the node is first created, capturing the chain as it exists at call initiation time.

#### Call Controller & Structured Conversations (Stories 7-8)

- **FR-015**: System MUST expose a `call_controller` tool that sends a structured justification message from an agent to the controller and returns the controller's response.
- **FR-016**: The call_controller mechanism MUST use the existing permission gate infrastructure, publishing an interrupt-like request that the controller (or human) resolves.
- **FR-017**: System MUST support a configurable message budget (k messages) per justification conversation. If alignment is not reached within k messages, the system MUST escalate to the user.
- **FR-018**: Prompt contributor factories MUST match over blackboard history to detect justification points and inject conversation-structuring prompts dynamically.
- **FR-019**: Each agent type MUST have a justification template specific to its role (discovery: interpretation + findings mapping; planning: ticket-to-requirement traceability; ticket: change justification + verification summary).
- **FR-020**: The controller MUST be able to escalate to the user at any point during a justification conversation, not only at phase gates.
- **FR-021**: The controller MUST NOT batch questions for phase gates — questions arising during agent conversations MUST be asked immediately.

#### Interrupt Simplification (Story 9) — Pre-requisite

- **FR-022**: System MUST remove `performReview` and `performMerge` actions from the workflow agent.
- **FR-023**: System MUST remove `ReviewRequest`, `MergerRequest`, `ReviewRouting`, `MergerRouting`, `ReviewRoute`, and `MergerRoute` from all models, routing objects, and filter mappings.
- **FR-024**: System MUST remove `InterruptPromptContributorFactory` (interrupt-review-guidance and interrupt-review-resolution contributors).
- **FR-025**: System MUST remove `OrchestratorRouteBackInterruptPromptContributorFactory` (orchestrator-route-back-interrupt-guardrail contributor).
- **FR-026**: System MUST remove `RouteBackInterruptPromptContributorFactory` (route-back review protocol and route-back-review-received contributors). The agent-initiated route-back review protocol is replaced by the controller conference mechanism: collectors call the controller to justify route-back decisions, and if the controller approves, the system injects a route-back schema via AddMessage (see FR-032f–FR-032k).
- **FR-027**: System MUST retain `InterruptLoopBreakerPromptContributorFactory` — this prevents degenerate loops when the human repeatedly routes to interrupt.
- **FR-028**: All `interruptRequest` fields on all agent routing objects MUST be annotated with `@SkipPropertyFilter`. Agents MUST NOT see interrupt request in their JSON schema during normal operation. The interrupt schema is only injected by the controller when it decides to route to another agent — the controller's decorator manually adds the routing schema with only the interrupt request field unfiltered.
- **FR-028a**: The `InterruptController.InterruptRequest` REST payload MUST include a `rerouteToAgentType` field (String, maps to `AgentType`) specifying which agent type the human/controller wants the interrupted agent to route to. This is the new primary purpose of interrupt: the controller says "route to agent X" and the system filters the routing schema so the LLM has only one choice.
- **FR-028b**: The `rerouteToAgentType` from the REST payload MUST flow through to `InterruptRequestEvent.rerouteToAgentType`, which the `FilterPropertiesDecorator` already reads to determine the target route annotation. The full flow is: REST `InterruptRequest.rerouteToAgentType` → `InterruptRequestEvent.rerouteToAgentType` → `FilterPropertiesDecorator.resolveTargetRoute()` → `mapAgentTypeToRoute()` → filters all route annotations except the target → LLM sees only one routing option in the Routing/SomeOf object.
- **FR-028c**: The interrupt does NOT create a new LLM call. The agent's ACP session is already running. The system injects tokens into the running session via the existing AddMessage infrastructure, using special decorators that add the override schema (`SomeOf(InterruptRequest)`) and the interrupt prompt text (reason, context, instructions to fill only the interrupt request). This changes the structured response mid-flight — the agent was generating a normal Routing/SomeOf, but the injected tokens steer it to return an `InterruptRequest` instead. The `rerouteToAgentType` is used by the **system** (via `FilterPropertiesDecorator` → `mapAgentTypeToRoute()`) to determine where to route after the LLM returns — the LLM itself only sees and fills the `InterruptRequest`.
- **FR-028d**: The interrupt injection MUST use the existing AddMessage infrastructure with dedicated endpoints and decorators. The decorators MUST compose the injected message to include: the override schema (`SomeOf(InterruptRequest)`), the human's reason, relevant context for the decision, and verbiage instructing the agent that its structured response type has changed. The system MUST NOT start a new LLM call or create a new session — it operates within the existing running ACP session.
- **FR-029**: The override schema (`SomeOf(InterruptRequest)`) injected via AddMessage MUST be generated by a victools-based schema generator. This schema is composed into the injected token payload by the interrupt decorators — it is not part of the original prompt pipeline.
- **FR-030**: The InterruptController MUST use dedicated decorators to compose the AddMessage payload. These decorators MUST include: the override schema, prompt text explaining the interrupt (reason, context, instructions), and any structured choices or confirmation items from the `InterruptRequest`. The `FilterPropertiesDecorator` continues to handle the response — when the running session returns, it uses `rerouteToAgentType` from the stored `InterruptRequestEvent` to determine the routing target.
- **FR-031**: System MUST retain `WorktreeMergeConflictService`, `WorktreeAutoCommitService`, and `InterruptService` — these services are still used even though the review and merge actions are removed.
- **FR-032**: System MUST remove all prompt contributors and prompts that instruct agents to self-initiate interrupt requests. Agents MUST NOT be able to route to interrupt on their own; only the controller can initiate interrupts by injecting the interrupt schema into an agent's context.
- **FR-032a**: Per-subagent `transitionToInterruptState` handlers MUST be removed from all dispatch subagents (discovery, planning, ticket). The unified interrupt handler (`handleUnifiedInterrupt` on the context manager) is the single entry point for all controller-initiated interrupts.
- **FR-032b**: Subagent routing types (`DiscoveryAgentRouting`, `PlanningAgentRouting`, `TicketAgentRouting`) MUST be separated from the `Routing`/`SomeOf` sealed hierarchy into a `DispatchedAgentRouting` interface. Subagents are pure leaf nodes — they return a result or an interrupt bubble-up, but do not participate in blackboard routing.
- **FR-032c**: `contextManagerRequest` fields MUST be removed from all subagent routing types. Context manager interactions are replaced by `call_agent` tool calls to the context manager agent via the inter-agent communication topology (Stories 1-2).
- **FR-032d**: The `ranXxxAgent` passthrough result actions (`ranTicketAgentResult`, `ranPlanningAgent`, `ranDiscoveryAgent`) MUST be removed from dispatch subagents. These are no longer needed with the simplified subagent routing.
- **FR-032e**: When a subagent's LLM response includes an interrupt request (because the controller previously injected the interrupt schema), the dispatch-level method MUST propagate it upward via a `@SkipPropertyFilter agentInterruptRequest` field on the dispatch routing object. This is internal routing only — the LLM never sees this field.

#### Route-Back Simplification via Controller Conference (Story 9 — continued)

- **FR-032f**: All route-back request fields on collector routing objects (e.g., `PlanningOrchestratorRequest planningRequest` on `PlanningCollectorRouting`, `DiscoveryOrchestratorRequest discoveryRequest` on `DiscoveryCollectorRouting`) MUST be annotated with `@SkipPropertyFilter`. During normal operation, the LLM MUST NOT see route-back fields in the JSON schema — collectors always route forward. Route-back schemas are only injected by the controller when it decides to route back (see FR-032h).
- **FR-032g**: `CollectorDecision` (and its enum values `ADVANCE_PHASE`, `ROUTE_BACK`, `STOP`) MUST be removed from all `*CollectorResult` types (`PlanningCollectorResult`, `DiscoveryCollectorResult`, `TicketCollectorResult`, `OrchestratorCollectorResult`). Since collectors always route forward during normal operation — route-back is now handled exclusively by the controller conference mechanism — the decision field is no longer needed. Collector actions (e.g., action fields on `*CollectorResult`) MUST be retained.
- **FR-032h**: When a collector agent calls the controller (via `call_controller`) to discuss whether to route back, and the controller decides that routing back is appropriate, the controller MUST call a dedicated endpoint that injects a route-back schema into the collector's running ACP session via AddMessage. The injected schema contains ONLY the route-back request field (e.g., `Routing(PlanningOrchestratorRequest)` for `PlanningCollectorRouting`), following the same pattern as interrupt schema injection (FR-028c, FR-028d). The collector then fills the route-back request and the system routes accordingly.
- **FR-032i**: The route-back schema injected via AddMessage MUST be generated by the same victools-based schema generator used for interrupt schemas (FR-029). The generator MUST support producing schemas for individual route-back request fields (e.g., `Routing(PlanningOrchestratorRequest)`) in addition to `SomeOf(InterruptRequest)`.
- **FR-032j**: The conversational topology controller endpoint (agent-to-controller conference, Story 7) MUST support a `routeBack` boolean field on the controller's response payload. When the controller sets `routeBack=true` during a conference with a collector agent, the system injects the route-back schema for the collector's routing object instead of proceeding normally. This is NOT on the interrupt endpoint — route-back is a controller conference decision, not an interrupt action.
- **FR-032k**: If `routeBack=true` is received for an agent that is NOT a collector (i.e., its routing object has no route-back request fields), the endpoint MUST return an error response indicating that route-back is not supported for that agent type.

#### Controller Conversation Polling (Story 7-8 support)

- **FR-041**: The system MUST expose a lightweight API endpoint (`POST /api/ui/activity-check`) that returns only pending permissions, pending interrupts, and pending conversation updates for a given nodeId. This endpoint MUST be fast (no graph traversal, no propagation queries) so it can be called at high frequency without performance impact.
- **FR-042**: The controller skill's `poll.py` MUST support a subscription-style `--subscribe` mode that accepts a maximum wait duration (e.g., `--subscribe 30`) and a tick interval (e.g., `--tick 0.5`). The subscribe loop calls the lightweight activity-check endpoint every tick interval. When the activity-check returns any pending items (permissions, interrupts, or conversations), the script calls the full `poll_once()` — which now also includes conversation data — and returns immediately.
- **FR-043**: The subscribe mode MUST fall back to calling the full `poll_once()` and returning its output after the maximum wait duration expires, even if the activity-check never detected new items — ensuring the controller LLM is never blocked indefinitely.
- **FR-044**: The full `poll_once()` output in `poll.py` MUST be extended to include a `═══ CONVERSATIONS ═══` section showing pending agent-to-controller conversation requests and recent controller-to-agent responses, alongside the existing graph, propagation, permission, and interrupt sections.
- **FR-045**: The controller skill MUST include a new `conversations.py` script that provides an ergonomic interface for viewing conversation data: listing all active conversations, retrieving the last N messages in a conversation, viewing full conversation history for a specific agent, and responding to pending `call_controller` requests. This script is the primary tool the controller uses to manage structured justification conversations.

#### Conversational Topology Documents (Story 10)

- **FR-033**: The controller skill MUST maintain a `conversational-topology/` directory containing living instructional documents that define how the controller reviews agent outputs at phase gates.
- **FR-034**: The directory MUST include a general `checklist.md` with phase-gate instructions (extract requirements, map to outputs, flag gaps, escalate to user) and agent-specific documents (e.g., `checklist-discovery-agent.md`, `checklist-planning-agent.md`, `checklist-ticket-agent.md`) with review criteria tuned to each agent type.
- **FR-035**: The directory MUST include a `reference.md` index that tracks all documents, when they were added or changed, and why — serving as the change log for conversational topology evolution.
- **FR-036**: The `multi_agent_ide_controller/SKILL.md` MUST include full instructions for how to use the conversational topology documents during a controller session (when to consult them, how to update them, how to register changes).
- **FR-037**: The parent `SKILL-PARENT.md` MUST briefly reference both the `conversational-topology/` and `conversational-topology-history/` directories and their purpose.
- **FR-038**: The controller MUST update or add documents when it observes new failure modes, recording the change rationale in `reference.md` so future sessions benefit.
- **FR-039**: The controller skill MUST maintain a `conversational-topology-history/` directory that preserves previous versions of topology documents whenever they are changed, using date-stamped filenames.
- **FR-040**: The history directory MUST include a `reference.md` that records each change: what was modified, which goal prompted the change, and the reasoning — providing a chronological log of how the conversational topology evolved.

### Key Entities

- **Communication Topology**: The configured matrix of allowed agent-role-to-agent-role communications. Defines which roles can initiate calls to which other roles.
- **Call Chain**: An ordered sequence of agent identifiers tracking the path of a communication request through the workflow. Used for loop detection and event observability.
- **Agent Communication Event**: A structured record of a communication action (initiate, intermediary, return, error) containing the call chain, involved agents, and available agents at time of emission.
- **AgentCallErrorEvent**: A specialized event recording a failed communication attempt, including the route (call chain), error details, and the target agent that was unreachable.
- **Agent Availability Entry**: A record representing a reachable agent, including its session identifier, role (orchestrator, collector, worker), and whether it is currently busy.
- **Justification Conversation**: A structured, budgeted exchange between an agent and the controller where the agent justifies its outputs against goal requirements before they are accepted. Managed by prompt contributors matching over blackboard history.
- **Justification Template**: A role-specific template defining what an agent must justify (interpretation, findings-to-requirements mapping, ticket traceability, change verification) and the expected response format.
- **Message Budget**: A configurable limit (k messages) on the length of a justification conversation. When exceeded, the system escalates to the user.
- **Conversational Topology Document**: A living instructional markdown file in the controller skill's `conversational-topology/` directory that defines review criteria for a specific agent type or phase gate. Evolves over sessions as new failure modes are observed.
- **Conversational Topology Reference Index**: The `conversational-topology/reference.md` file tracking all current topology documents with descriptions and the rationale for their existence.
- **Conversational Topology History**: The `conversational-topology-history/` directory preserving date-stamped previous versions of topology documents. Its own `reference.md` provides a chronological log of what changed, which goal prompted the change, and why — making it easy to trace how the conversational topology evolved and to revert changes that didn't work.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Agents can discover available peers and receive a response from list_agents within 1 second under normal workflow conditions.
- **SC-002**: Inter-agent messages are delivered and responses returned within 5 seconds for single-hop calls under normal conditions.
- **SC-003**: 100% of topology-violating calls are rejected before message delivery, with a clear error returned to the caller.
- **SC-004**: 100% of loop-creating call chains are detected and rejected, with the full chain included in the rejection message.
- **SC-005**: Every inter-agent communication action produces the corresponding event(s) with complete call chain and available-agents data.
- **SC-006**: Agents receive updated communication context (via prompt contributor) within one prompt cycle of a session opening or closing.
- **SC-007**: Failed calls return an actionable error to the caller within 3 seconds, never leaving the caller blocked indefinitely.
- **SC-008**: The communication topology can be reconfigured without restarting the workflow, taking effect on the next list_agents or call_agent invocation.
- **SC-009**: After interrupt simplification, no agent can self-initiate an interrupt request. Only human-initiated (or controller-initiated) interrupts produce interrupt routing.
- **SC-010**: When the human sends an interrupt, the agent receives a second schema (`SomeOf(InterruptRequest)`) that overrides the structured response — the agent fills only the interrupt request and returns it. The system then uses `rerouteToAgentType` to determine the routing target.
- **SC-011**: At every phase gate, the controller consults the relevant conversational topology document and follows its review criteria before approving the transition.
- **SC-012**: Justification conversations complete within the message budget (k messages) or escalate to the user.
- **SC-013**: The controller escalates to the user immediately when uncertainty arises during an agent conversation, not at the next gate.
- **SC-014**: In subscribe mode, the controller receives conversation responses within 1 second of the response being available (bounded by the tick interval), rather than waiting for the next full poll cycle.

## Assumptions

- Agent sessions are managed by the existing agent platform and can be queried for open/closed/busy status via existing mechanisms (BlackboardHistory, AgentProcess).
- The MCP session header pattern used in AcpTooling is the established mechanism for identifying the calling agent's session context.
- The existing event infrastructure supports the new event types without modification to the event bus itself.
- Orchestrators and collectors are considered equivalent in the topology hierarchy ("same level") and can freely communicate with each other.
- A "dispatched" agent is one whose session has been closed after task completion and should not be contactable.
- The maximum call chain depth has a reasonable default (e.g., 5 hops) that can be overridden in configuration.
- The victools JSON schema generator is already available as a dependency and can be configured to filter fields independently of the `SkipPropertyFilter` annotation.
- The `FilterPropertiesDecorator` already handles `SkipPropertyFilter` annotation filtering at the LLM call level — the interrupt simplification extends this existing mechanism. All `interruptRequest` fields on agent routing objects are annotated with `@SkipPropertyFilter` so agents never see interrupt routing in their normal schema. Similarly, all route-back request fields on collector routing objects are annotated with `@SkipPropertyFilter` so collectors never see route-back options during normal operation. The controller is the only entity that can inject either schema (interrupt or route-back) by overriding the filter via AddMessage.
- Removing `performReview` and `performMerge` does not affect `WorktreeMergeConflictService`, `WorktreeAutoCommitService`, or `InterruptService` — those services handle merge conflicts, auto-commits, and human interrupt resolution respectively, which are orthogonal to the removed review/merge agent actions.
- `WorktreeAutoCommitService` and `WorktreeMergeConflictService` are LLM-calling agents (COMMIT_AGENT, MERGE_CONFLICT_AGENT) that create their own `PromptContext` and run through the decorator pipeline. Their `chatId()` resolves to `contextId.parent()` — the calling agent's ArtifactKey. They participate in the communication topology and are subject to self-call filtering.
- AI pipeline tools (`AiFilterTool`, `AiPropagatorTool`, `AiTransformerTool`) run inline LLM calls within the decorator pipeline. Their session identity is resolved by `AiFilterSessionResolver` and depends on `SessionMode`. When `SAME_SESSION_FOR_AGENT`, the session key resolves to the calling agent's ArtifactKey, creating a self-call risk that must be filtered.
- All functionality previously handled by `contextManagerRequest` routing fields on subagent routing objects is replaced by `call_agent` tool calls to the context manager. This is a direct consequence of the agent topology: context manager becomes a callable agent rather than a routing target.
- Integration tests for the interrupt simplification and agent channels will be written together once the architecture is solidified and the UI is in place; test_graph integration tests are deferred.
- The message budget default (k=3) is tunable based on observation of actual conversation lengths.
