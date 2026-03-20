# Feature Specification: Inter-Agent Communication Topology

**Feature Branch**: `001-agent-topology`
**Created**: 2026-03-20
**Status**: Draft
**Input**: User description: "Add inter-agent communication tools (call_agent, list_agents, call_controller) with configurable topology, loop detection, error handling, and event emission"

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

### User Story 7 - Call Controller (Priority: P3)

An agent can escalate a question or decision to the workflow controller, similar to asking a user question but directed at an automated controller. This enables agents to request guidance on ambiguous situations without blocking the workflow. This capability is a placeholder for future integration with the controller embabel agent.

**Why this priority**: This is explicitly deferred (unimplemented, commented out) pending the controller embabel agent integration. Included here for completeness and future planning.

**Independent Test**: N/A - placeholder for future implementation.

**Cucumber Test Tag**: `@call_controller`

**Acceptance Scenarios**:

1. **Given** call_controller tool is registered but unimplemented, **When** an agent attempts to call it, **Then** the system returns an informative message that controller communication is not yet available.

---

### Edge Cases

- What happens when an agent calls itself? The system should reject self-calls as a degenerate loop.
- What happens when a target agent's session closes between list_agents and call_agent? The system should return a graceful error indicating the agent is no longer available.
- What happens when the topology configuration is empty or missing? The system should fall back to a safe default (no inter-agent communication allowed) and log a warning.
- What happens when multiple agents try to call the same target simultaneously? Only one can be processed; others receive a "busy" error.
- What happens when the call chain exceeds a configurable maximum depth? The system should reject the call to prevent unbounded chains.

## Requirements *(mandatory)*

### Functional Requirements

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
- **FR-011**: The default topology MUST allow: worker -> own orchestrator/collector, orchestrator/collector -> other orchestrators/collectors at same level, orchestrator/collector -> workers under them.
- **FR-012**: System MUST gracefully handle call failures by catching exceptions and returning an error response advising the caller to try an alternative route.
- **FR-013**: System MUST register `call_controller` as a placeholder tool (unimplemented, commented out) for future controller embabel agent integration.
- **FR-014**: System MUST enforce a configurable maximum call chain depth to prevent unbounded communication chains.

### Key Entities

- **Communication Topology**: The configured matrix of allowed agent-role-to-agent-role communications. Defines which roles can initiate calls to which other roles.
- **Call Chain**: An ordered sequence of agent identifiers tracking the path of a communication request through the workflow. Used for loop detection and event observability.
- **Agent Communication Event**: A structured record of a communication action (initiate, intermediary, return, error) containing the call chain, involved agents, and available agents at time of emission.
- **AgentCallErrorEvent**: A specialized event recording a failed communication attempt, including the route (call chain), error details, and the target agent that was unreachable.
- **Agent Availability Entry**: A record representing a reachable agent, including its session identifier, role (orchestrator, collector, worker), and whether it is currently busy.

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

## Assumptions

- Agent sessions are managed by the existing agent platform and can be queried for open/closed/busy status via existing mechanisms (BlackboardHistory, AgentProcess).
- The MCP session header pattern used in AcpTooling is the established mechanism for identifying the calling agent's session context.
- The existing event infrastructure supports the new event types without modification to the event bus itself.
- Orchestrators and collectors are considered equivalent in the topology hierarchy ("same level") and can freely communicate with each other.
- A "dispatched" agent is one whose session has been closed after task completion and should not be contactable.
- The maximum call chain depth has a reasonable default (e.g., 5 hops) that can be overridden in configuration.
