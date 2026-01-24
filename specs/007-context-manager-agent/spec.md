# Feature Specification: Context Manager Agent

**Feature Branch**: `007-context-manager-agent`  
**Created**: 2026-01-22  
**Status**: Draft  
**Input**: User description: "Add Context Manager Agent with BlackboardHistory tooling for multi-agent workflow context reconstruction. The system needs to handle situations where agents lose context across long-running workflows spanning multiple agents, actions, and sessions. When workflows get stuck in degenerate loops or hung states, a Context Manager Agent should reconstruct context by querying BlackboardHistory and routing back to the appropriate agent with sufficient context to continue."

## Vision

Context Manager acts as an abstraction from which to translate context across agents through its BlackboardHistory tools, including all messages produced during execution. It monitors degenerate executions and advises on recovery, and handles other failure scenarios as detected by Embabel's stuck detection mechanisms.

We require that the message history is added and summarized at the level of the ChatModel instead of the agents, as we're using Acp, and the provider's models were trained with specific context summarization strategies. Context manager isn't summarizing each individual agent's context, or an abstraction around each individual agent's context, it's an abstraction over the blackboard, the shared state amongst the agents.

## Scope

### In Scope

- Context Manager Agent whose primary function is context creation via BlackboardHistory tools
- Tooling surface over BlackboardHistory for context reconstruction (6 tools)
- Embabel StuckHandler integration for routing stuck/degenerate workflows to Context Manager
- BlackboardHistory extensions for post-hoc notes/annotations
- Deterministic routing semantics for entering and exiting Context Manager
- Event subscription for capturing all execution messages into BlackboardHistory

### Out of Scope

- UI or interactive visualization
- Embedding/vector search implementations (interfaces may exist, implementation deferred)
- Changes to provider-level context compaction, as noted in Vision
- New orchestration layers
- Model-specific logic
- Automatic routing heuristics (routing is always explicit)

## Prompting Notes

There will be prompts for the context manager agent and the agents that route to the context manager agent. Here are some notes to keep in mind for writing the prompts.

### Diffs, Session Messages, Prompts

When an agent routes to context manager agent to retrieve context from the blackboard there should be an option for the prompt to specify for context manager to only return a diff when routing back with that agent's request, with notes referencing the previous, noting that this is a diff already present in the agent's context, and then adding the new data into the request. See the vision for more information about this, about requirements of message history being managed at the level of the AcpChatModel through the acp chat provider, which in other words is acp-codex, acp-claude, etc.

### Using the Memory Tool

The context manager agent will have access to the same memory tool that the agents have access to. Notably, this tool provides a mechanism for retain, reflect, and recall. Moreover, while the agents are running, this memory tool stores agent episodes as embeddings. The great thing about this is that the context manager can use the memory tool to retrieve relevant episodes from the memory tool, then search for those and expand around them. Then the context manager can do retain, reflect, and recall as well for this, adding notes to the Blackboard entries all the time accordingly. So we'll want to make sure to explicitly include information about this related to how the context manager will use episodic memory tool as well. You can see more information about how that works in @EpisodicMemoryPromptContributor.


## User Scenarios & Testing

### User Story 1 - History Trace Tool (Priority: P1)

Retrieve the ordered history of events for a specific action or agent execution to understand what actually happened during a step.

**Why this priority**: Fundamental tool for understanding execution flow - enables tracing what occurred during specific actions.

**Independent Test**: Execute a workflow with multiple actions of the same type, then invoke the tool filtering by action name to verify it returns only matching entries in chronological order.

**Cucumber Test Tag**: `@history-trace-tool`

**Acceptance Scenarios**:

1. **Given** a workflow has executed actions with names ["coordinateWorkflow", "kickOffDiscoveryAgents", "consolidateFindings", "kickOffDiscoveryAgents"], **When** the History Trace Tool is invoked filtering by "kickOffDiscoveryAgents", **Then** exactly 2 entries are returned in chronological order
2. **Given** a workflow has 50 historical entries, **When** the History Trace Tool is invoked with an input type filter, **Then** only entries matching that input type are returned
3. **Given** BlackboardHistory is empty, **When** the History Trace Tool is invoked, **Then** an empty result is returned with status "empty"

---

### User Story 2 - History Listing / Paging Tool (Priority: P1)

Traverse BlackboardHistory incrementally to enable scanning backward or forward through long workflows with pagination support.

**Why this priority**: Essential for navigating large histories without loading everything into memory - supports time windows, agent/action filters, and pagination.

**Independent Test**: Execute a workflow with 100 actions, then use pagination to retrieve entries in chunks, verifying offset, limit, and hasMore indicators.

**Cucumber Test Tag**: `@history-listing-tool`

**Acceptance Scenarios**:

1. **Given** a workflow has 100 historical entries, **When** the History Listing Tool is invoked with offset=0 and limit=20, **Then** the first 20 entries are returned with hasMore=true
2. **Given** a workflow has 100 historical entries, **When** the History Listing Tool is invoked with offset=90 and limit=20, **Then** the last 10 entries are returned with hasMore=false
3. **Given** a workflow has entries spanning 2 hours, **When** the History Listing Tool is invoked with a time range filter for the first hour, **Then** only entries within that time window are returned
4. **Given** a workflow has entries from multiple agents, **When** the History Listing Tool is invoked with an action name filter, **Then** only matching entries are included in the paginated results

---

### User Story 3 - History Search Tool (Priority: P1)

Search across BlackboardHistory contents to locate relevant prior decisions, errors, or artifacts using text-based search.

**Why this priority**: Critical for finding specific information in large histories - enables content-based discovery of relevant context.

**Independent Test**: Execute a workflow with varied inputs, then search for specific strings to verify matching entries are returned.

**Cucumber Test Tag**: `@history-search-tool`

**Acceptance Scenarios**:

1. **Given** BlackboardHistory contains entries with various action names, **When** the History Search Tool is invoked with query "Discovery", **Then** all entries with action names containing "Discovery" are returned
2. **Given** BlackboardHistory contains entries with different input types, **When** the History Search Tool is invoked with query "CollectorRequest", **Then** all entries with input types matching that query are returned
3. **Given** BlackboardHistory has 200 entries matching a query, **When** the History Search Tool is invoked with maxResults=50, **Then** at most 50 results are returned
4. **Given** the History Search Tool is invoked with an empty query, **When** the search executes, **Then** an error status is returned with message "Query cannot be empty"

---

### User Story 4 - History Item Retrieval Tool (Priority: P1)

Fetch a specific history entry by identifier or index when context creation references earlier events explicitly.

**Why this priority**: Enables precise retrieval of known entries when agents reference specific historical items.

**Independent Test**: Execute a workflow with 10 actions, then retrieve specific entries by index to verify exact matches.

**Cucumber Test Tag**: `@history-item-retrieval-tool`

**Acceptance Scenarios**:

1. **Given** a workflow has 10 historical entries, **When** the History Item Retrieval Tool is invoked with index=5, **Then** the entry at position 5 is returned with all details
2. **Given** a workflow has 10 historical entries, **When** the History Item Retrieval Tool is invoked with index=15, **Then** an error is returned with message "Index out of bounds"
3. **Given** BlackboardHistory is empty, **When** the History Item Retrieval Tool is invoked with any index, **Then** an error is returned with message "No history available"

---

### User Story 6 - Blackboard Note / Annotation Tool (Priority: P1)

Attach notes to BlackboardHistory entries to explain inclusion, exclusion, minimization, or routing rationale.

**Why this priority**: Essential for auditability and reasoning transparency - documents why context decisions were made.

**Independent Test**: Execute a workflow, add notes to specific entries with classification tags, and verify notes are retrievable.

**Cucumber Test Tag**: `@blackboard-note-tool`

**Acceptance Scenarios**:

1. **Given** a workflow has 10 historical entries, **When** the Blackboard Note Tool is invoked with entry indices [3, 4, 5], note content "Excluded due to irrelevance", and tags ["exclusion", "diagnostic"], **Then** a note is created with unique ID and timestamp
2. **Given** the Blackboard Note Tool is invoked with empty note content, **When** validation occurs, **Then** an error is returned with message "Note content cannot be empty"
3. **Given** the Blackboard Note Tool is invoked with entry indices [50] for a history of 10 entries, **When** validation occurs, **Then** an error is returned with message "Index out of bounds"
4. **Given** notes have been attached to entries, **When** those entries are retrieved via other tools, **Then** the notes are included in the entry views

---

### User Story 7 - Event Subscription for Message Tracking (Priority: P1)

BlackboardHistory subscribes to the EventBus to capture all messages produced during workflow execution. It implements EventSubscriber interface and subscribes to DefaultEventBus at workflow start, capturing events from AcpStreamWindowBuffer (such as NodeStreamDeltaEvent, NodeThoughtDeltaEvent, ToolCallEvent, etc.), and unsubscribes on completion. This ensures all execution messages are available for context reconstruction.

**Why this priority**: Essential capability - without capturing execution messages, the Context Manager cannot access the full communication history needed for context reconstruction.

**Independent Test**: Execute a workflow that produces various event types, verify BlackboardHistory subscribes to EventBus, captures all relevant events, and unsubscribes on completion.

**Cucumber Test Tag**: `@event-subscription-tracking`

**Acceptance Scenarios**:

1. **Given** a workflow is starting, **When** BlackboardHistory is initialized, **Then** it subscribes to the DefaultEventBus as an EventSubscriber
2. **Given** BlackboardHistory is subscribed to the EventBus, **When** AcpStreamWindowBuffer publishes events (NodeStreamDeltaEvent, NodeThoughtDeltaEvent, ToolCallEvent), **Then** BlackboardHistory captures these events
3. **Given** a workflow execution produces 50 different event types, **When** BlackboardHistory processes the events, **Then** all events are stored and available for query via BlackboardHistory tools
4. **Given** a workflow completes, **When** the cleanup phase executes, **Then** BlackboardHistory unsubscribes from the EventBus
5. **Given** BlackboardHistory has captured events, **When** the Context Manager uses history tools to search, **Then** events are included in search results alongside action entries

---

### User Story 8 - Degenerate Loop Detection and Recovery (Priority: P1)

A workflow enters a degenerate loop where the same action repeats without progress. BlackboardHistory tracks repetitions and throws a DegenerateLoopException when the threshold is exceeded. The WorkflowAgent's StuckHandler implementation catches this exception and calls the Context Manager with BlackboardHistory tools to analyze the loop pattern and reconstruct context to break the cycle.

**Why this priority**: Critical for system reliability - loops can consume resources indefinitely and block workflow completion.

**Independent Test**: Can be fully tested by creating a workflow that intentionally enters a loop, verifying BlackboardHistory throws DegenerateLoopException, and confirming the StuckHandler routes to Context Manager which successfully breaks the loop.

**Cucumber Test Tag**: `@degenerate-loop-recovery`

**Acceptance Scenarios**:

1. **Given** a workflow action executes repeatedly with the same input type, **When** BlackboardHistory detects the repetition count exceeds the configured threshold, **Then** BlackboardHistory throws a DegenerateLoopException with information about where the loop occurred
2. **Given** a DegenerateLoopException is thrown, **When** the WorkflowAgent's StuckHandler catches the exception, **Then** the StuckHandler escalates to the Context Manager Agent
3. **Given** the Context Manager receives a loop escalation via StuckHandler, **When** it uses BlackboardHistory tools to analyze the loop pattern, **Then** it identifies the repeating actions and their inputs
4. **Given** the Context Manager has analyzed the loop, **When** it assembles a recovery context response, **Then** the response includes information about the loop pattern and suggests alternative routing
5. **Given** a recovery context is created, **When** the Context Manager returns a continuation request, **Then** exactly one agent is selected for continuation with the recovery context

---

### User Story 9 - Context Loss Recovery with Explicit Agent Request (Priority: P1)

A workflow agent encounters insufficient context to continue its work after multiple agent transitions or session boundaries. The agent explicitly requests context reconstruction using the SomeOf routing mechanism (as defined in AgentModels.java and provided by the Embabel agents framework), and the Context Manager uses its tools to retrieve relevant historical information and rebuild working context.

**Why this priority**: Demonstrates the primary use case - agents proactively requesting context when they determine they lack sufficient information to proceed, using standard Embabel routing patterns.

**Independent Test**: Can be fully tested by initiating a workflow that spans multiple agents, intentionally clearing context, then requesting context reconstruction via SomeOf routing and verifying the workflow continues successfully.

**Cucumber Test Tag**: `@context-recovery`

**Acceptance Scenarios**:

1. **Given** a workflow has executed across 5 different agents, **When** an agent determines it lacks sufficient context, **Then** the agent returns a SomeOf routing request targeting the Context Manager (following AgentModels patterns)
2. **Given** the Context Manager receives a context reconstruction request via SomeOf routing, **When** it uses BlackboardHistory tools to query relevant entries, **Then** it retrieves all historical entries needed for the workflow
3. **Given** the Context Manager has retrieved historical entries, **When** it assembles a context response, **Then** the response includes curated information with links to source history items
4. **Given** a context response has been assembled, **When** the Context Manager returns a SomeOf routing response with exactly one populated branch, **Then** the agent receives sufficient context to continue the workflow

---

### User Story 10 - Review/Merge Context Routing (Priority: P2)

Review or Merge agents route to Context Manager in two scenarios using ContextManagerRequest:

1. **INTROSPECT_AGENT_CONTEXT**: When Review/Merge determines that information has been filtered too aggressively and needs to search through all contexts across previous agents to make informed decisions. Context Manager returns enriched context to Review/Merge to continue its work.

2. **PROCEED**: When Review/Merge completes its work. Review/Merge does **not** route back to the agent that requested it directly. Instead, it routes to Context Manager with PROCEED type. Context Manager reconstructs the necessary context and routes to the appropriate downstream agent (which may be the original requester or another agent).

This uses the same SomeOf routing mechanism as User Story 9 with ContextManagerRequest populated. This scenario is called out separately because Review/Merge agents have a unique intermediary role - they specifically never route directly back to their requester when forwarding results.

**Why this priority**: Important for maintaining context quality through review/merge phases, both for Review/Merge's own decisions and for ensuring downstream agents receive sufficient context. Critical invariant: Review/Merge always routes through Context Manager when proceeding, never directly back to requester.

**Independent Test**: Execute a workflow where (1) Review agent uses INTROSPECT_AGENT_CONTEXT to get more context for its decision, and (2) Review agent uses PROCEED to forward through Context Manager after completion.

**Cucumber Test Tag**: `@review-merge-context-routing`

**Acceptance Scenarios**:

1. **Given** a Review agent is evaluating work output, **When** it determines context was filtered too aggressively, **Then** it routes to Context Manager with type=INTROSPECT_AGENT_CONTEXT to search previous agent contexts
2. **Given** a Merge agent is consolidating results, **When** it lacks sufficient context to resolve conflicts, **Then** it routes to Context Manager with type=INTROSPECT_AGENT_CONTEXT
3. **Given** Context Manager receives INTROSPECT_AGENT_CONTEXT request from Review/Merge, **When** it reconstructs context using BlackboardHistory tools, **Then** it returns enriched context to the Review/Merge agent to continue its work
4. **Given** a Review agent has completed its review, **When** it needs to forward results to the next agent, **Then** Review routes to Context Manager with type=PROCEED (not directly to the requester)
5. **Given** a Merge agent has completed merging, **When** it needs to forward results, **Then** Merge routes to Context Manager with type=PROCEED (not directly to the requester)
6. **Given** Context Manager receives PROCEED request from Review/Merge, **When** it reconstructs context using BlackboardHistory tools, **Then** it routes to the appropriate downstream agent with enriched context
7. **Given** ContextManagerRequest includes returnToRequests with SomeOf fields, **When** Context Manager completes, **Then** exactly one branch in returnToRequests is populated for the continuation

---

### User Story 11 - Hung State Recovery (Priority: P2)

A workflow becomes hung or stalled without making progress. The WorkflowAgent's StuckHandler interface implementation detects the hung state and escalates to the Context Manager, which diagnoses the issue by examining BlackboardHistory and creates appropriate context for recovery.

**Why this priority**: Important for handling stuck workflows that aren't in loops but have stopped progressing, though degenerate loops are more common.

**Independent Test**: Can be fully tested by simulating a hung workflow state, verifying the WorkflowAgent StuckHandler detects it, and confirming successful recovery via Context Manager.

**Cucumber Test Tag**: `@hung-state-recovery`

**Acceptance Scenarios**:

1. **Given** a workflow has not progressed for a configured timeout period, **When** the WorkflowAgent's StuckHandler implementation detects the hung state, **Then** it escalates to the Context Manager with diagnostic information
2. **Given** the Context Manager receives a hung state escalation from StuckHandler, **When** it examines recent BlackboardHistory entries using query tools, **Then** it identifies the point where progress stopped
3. **Given** the Context Manager has identified the hung point, **When** it creates a diagnostic context response, **Then** the response includes the last successful action and relevant state information
4. **Given** a diagnostic context is created, **When** the Context Manager determines the appropriate recovery agent, **Then** it returns a single continuation request to resume the workflow

---

### User Story 12 - Reasoning Documentation via Note Tool (Priority: P3)

The Context Manager uses the Blackboard Note Tool (User Story 6) to document its reasoning when assembling context responses. Notes are attached to historical entries for followup, explaining why information was included, excluded, or how it influenced routing decisions.

**Why this priority**: Valuable for auditability and understanding context reconstruction decisions, but this is already provided by the Blackboard Note Tool - this story documents the usage pattern.

**Independent Test**: Can be fully tested by triggering context reconstruction, verifying the Context Manager uses the note tool to attach reasoning to relevant history entries, and confirming notes contain appropriate justification.

**Cucumber Test Tag**: `@context-manager-note-usage`

**Acceptance Scenarios**:

1. **Given** the Context Manager assembles a context response, **When** it selects historical entries to include, **Then** it uses the Blackboard Note Tool to attach notes explaining the selection criteria
2. **Given** the Context Manager attaches notes via the tool, **When** another agent queries those history entries, **Then** the notes are included in the entry views for followup analysis
3. **Given** the Context Manager makes a routing decision, **When** it documents the decision, **Then** it uses the note tool with tags like "routing" and "diagnostic" to explain the rationale
4. **Given** multiple context reconstructions have occurred, **When** a user searches notes by classification tag using the note tool, **Then** matching notes are retrieved showing the Context Manager's reasoning over time

---

## Routing Semantics

### Fundamental Invariant

The Context Manager serves two primary purposes for any agent routing:

1. **INTROSPECT_AGENT_CONTEXT**: An agent determines it lacks sufficient context to proceed. Context Manager searches through all contexts across previous agents to curate relevant historical information. The agent receives the enriched context and continues its work.

2. **PROCEED**: An agent (particularly Review/Merge) completes its work but the next agent in the workflow needs context that the current agent cannot provide. Review/Merge does **not** route back to the requesting agent directly. Instead, it routes to Context Manager with PROCEED type, which reconstructs the necessary context and then routes to the appropriate downstream agent.

### Context Manager Request Structure

Agents request context reconstruction by returning a **ContextManagerRoutingRequest** in their routing response. The routing action then assembles the full ContextManagerRequest using the most recent agent request in BlackboardHistory.

The ContextManagerRequest includes:

- **type**: `INTROSPECT_AGENT_CONTEXT` | `PROCEED`
  - `INTROSPECT_AGENT_CONTEXT`: Search through all contexts across previous agents for the requesting agent's benefit. The `returnToRequests` field specifies routing back to the agent that requested the introspection (i.e., the requesting agent receives the enriched context to continue its work).
  - `PROCEED`: Forward-looking context reconstruction for the next agent in the workflow. The `returnToRequests` field specifies routing to the downstream agent that the requesting agent does not have sufficient context to provide directly (i.e., Context Manager reconstructs context for the target agent).
- **reason**: Why context reconstruction is needed
- **goal**: What the context reconstruction should achieve
- **additionalContext**: Any supplementary information to guide reconstruction
- **returnToRequest**: SomeOf-style fields for all request types that could be routed to after context reconstruction (matching existing AgentModels patterns). For `INTROSPECT_AGENT_CONTEXT`, this routes back to the requesting agent. For `PROCEED`, this routes to the downstream agent that needs context. Exactly one `returnTo*` field must be non-null.

### Entry Points to Context Manager

The Context Manager Agent is invoked **only** through explicit mechanisms (no implicit or automatic routing):

1. **Explicit Agent Request** (User Story 9): Any agent returns a routing request with ContextManagerRoutingRequest populated. Uses `INTROSPECT_AGENT_CONTEXT` type to search across previous agent contexts; the routing action builds the ContextManagerRequest.

2. **StuckHandler Routing** (User Stories 8, 11): WorkflowAgent's StuckHandler catches DegenerateLoopException or hung state and routes to Context Manager with diagnostic information.

3. **Review/Merge Context Routing** (User Story 10): Review or Merge agents route to Context Manager in two cases:
   - **INTROSPECT_AGENT_CONTEXT**: Review/Merge needs to search through previous agent contexts to make decisions
   - **PROCEED**: Review/Merge has completed but does not route back to the requesting agent directly. Instead, routes to Context Manager which reconstructs context and forwards to the appropriate next agent.

### Exit Semantics

- Context Manager returns a **SomeOf-style outcome** consistent with existing AgentModels patterns, including any request type listed in ContextManagerRequest return routes
- **Exactly one branch is populated** in the response
- That branch is a **request for another agent** which resumes the workflow
- For `PROCEED` type: The populated branch targets the downstream agent (not the original requester of Review/Merge)
- For `INTROSPECT_AGENT_CONTEXT` type: The populated branch returns to the requesting agent with enriched context
- This enforces simple, deterministic reasoning with no ambiguity

---

### Edge Cases

- What happens when BlackboardHistory is empty and context reconstruction is requested?
- How does the system handle context reconstruction requests during an active context reconstruction?
- What happens when the Context Manager itself enters a loop or hung state?
- How does the system behave when historical entries reference data that is no longer available?
- What happens when the degenerate loop threshold is set too low and triggers false positives?
- How does the system handle concurrent workflows using the same session identifier?

## Requirements

### Functional Requirements

**Loop and Hung Detection**
- **FR-001**: System MUST detect when a workflow action repeats with the same input type beyond a configurable threshold
- **FR-002**: System MUST throw a DegenerateLoopException when loop detection threshold is exceeded, containing loop pattern information
- **FR-003**: System MUST detect when a workflow has not progressed within a configurable timeout period (hung state)
- **FR-004**: WorkflowAgent MUST implement StuckHandler interface to catch hung and loop exceptions and escalate to Context Manager
- **FR-005**: System MUST update loop detection state every time an entry is added to BlackboardHistory
- **FR-006**: System MUST clear loop detection state when workflow receives interrupt or user input (interrupts reset detection)

**BlackboardHistory Tools**
- **FR-007**: Context Manager MUST provide History Trace Tool to retrieve ordered history for specific actions or agents
- **FR-008**: Context Manager MUST provide History Search Tool to search entries by content, action name, or input type
- **FR-009**: Context Manager MUST provide History Listing Tool with pagination support (offset, limit, hasMore)
- **FR-010**: Context Manager MUST provide History Item Retrieval Tool to fetch specific entries by identifier or index
- **FR-011**: Context Manager MUST provide Message Paging Tool to retrieve message events by entry id
- **FR-012**: Context Manager MUST provide Blackboard Note Tool to attach notes to historical entries with classification tags
- **FR-013**: All Context Manager tools MUST retrieve BlackboardHistory using session identifier mapped to agent process

**Event Subscription**
- **FR-014**: BlackboardHistory MUST implement EventSubscriber interface and subscribe to DefaultEventBus at workflow start
- **FR-015**: BlackboardHistory MUST capture all events from AcpStreamWindowBuffer (NodeStreamDeltaEvent, NodeThoughtDeltaEvent, ToolCallEvent, etc.)
- **FR-016**: BlackboardHistory MUST unsubscribe from EventBus on workflow completion

**Routing Semantics**
- **FR-017**: Context Manager MUST be invoked only via explicit agent request, StuckHandler escalation, or Review/Merge escalation
- **FR-018**: Context Manager MUST return exactly one continuation request (SomeOf-style with exactly one branch populated)
- **FR-019**: System MUST prevent recursive context reconstruction (Context Manager never calls itself)
- **FR-020**: Review and Merge agents MUST be able to explicitly request context reconstruction via SomeOf routing

**Notes**
- **FR-022**: Notes MUST support classification tags for categorizing reasoning (diagnostic, routing, exclusion, etc.)
- **FR-023**: Notes MUST be additive and non-destructive (cannot modify original history entries)
- **FR-024**: Context Manager MUST store notes in a queryable format alongside history
- **FR-025**: Historical queries MUST support filtering by time range, action name, and input type
- **FR-026**: System MUST maintain session identifier mapping to agent process identifier for history retrieval

### Key Entities

- **BlackboardHistory Entry**: Represents a single historical action execution with timestamp, action name, input object, and input type
- **Event Entry**: Represents a captured event from EventBus (NodeStreamDeltaEvent, NodeThoughtDeltaEvent, ToolCallEvent, etc.) with timestamp and event data
- **History Note**: An annotation attached to one or more historical entries, with content, classification tags, and timestamp; additive and non-destructive
- **Degenerate Loop Exception**: Exception thrown when action repetition threshold is exceeded, containing loop pattern information (action name, input type, repetition count)
- **Hung Exception**: Exception thrown when workflow progress stalls beyond timeout, containing diagnostic information (last action, stall duration)
- **Context Reconstruction Request**: Request to the Context Manager for rebuilding context, including reason, current state, and source (agent request, StuckHandler, or Review/Merge)
- **Continuation Request**: Response from Context Manager specifying which agent to route to with reconstructed context; SomeOf-style with exactly one branch populated

## Success Criteria

### Measurable Outcomes

- **SC-001**: Workflows that lose context across agent transitions can recover without manual intervention in 95% of cases
- **SC-002**: Degenerate loops are detected within 3 iterations of the repeating pattern
- **SC-003**: Hung workflows are detected within 2 minutes of stalling
- **SC-004**: Context reconstruction completes and returns continuation request within 30 seconds
- **SC-005**: Historical queries return results within 5 seconds for workflows with up to 1000 historical entries
- **SC-006**: Context responses reference all relevant historical entries needed for recovery
- **SC-007**: Zero recursive context reconstruction attempts occur (Context Manager never calls itself)
- **SC-008**: Notes attached to historical entries are retrievable alongside the entries in all query operations
- **SC-009**: Pagination of historical entries supports workflows with unlimited history growth
- **SC-010**: 90% of degenerate loop recoveries successfully break the loop on first attempt
- **SC-011**: 100% of events published to EventBus during workflow execution are captured by BlackboardHistory
- **SC-012**: Context Manager returns exactly one continuation request in 100% of invocations (no ambiguous routing)
- **SC-013**: Interrupts reset loop detection state within 1 second of receipt
- **SC-014**: Review/Merge escalations to Context Manager successfully reconstruct context in 90% of cases

## Assumptions

- Loop detection thresholds will be configurable per deployment environment
- Session identifiers uniquely map to agent process identifiers within the same execution context
- BlackboardHistory already exists in the system and stores entries for all agent actions
- The Embabel framework provides stuck handler extension points
- Historical entries remain available for the duration of the workflow execution
- Agent routing decisions can be made based solely on reconstructed context without external input
- The system has sufficient storage for maintaining historical entries and notes
- Notes do not need to persist beyond workflow completion

## Testing Strategy

This feature will be tested using **unit tests** rather than integration tests, as the functionality involves internal tool mechanisms and BlackboardHistory manipulation that don't cross application boundaries.

Key unit test areas:
- BlackboardHistory tool implementations (trace, list, search, retrieve, note)
- Degenerate loop detection logic and threshold checking
- Hung state detection and timeout mechanisms
- Note attachment and retrieval from BlackboardHistory
- Session identifier to agent process mapping for history retrieval
- Exception throwing for hung and degenerate loop conditions
- StuckHandler integration and escalation logic

Unit tests will verify:
1. Each tool correctly queries and manipulates BlackboardHistory
2. Loop detection accurately counts repetitions and triggers at threshold
3. Hung detection monitors progress and escalates appropriately
4. Context responses reference correct historical entries
5. Notes are properly attached and queryable
6. Session IDs correctly map to agent processes for history access
