# Feature Specification: Agent Graph UI

**Feature Branch**: `002-ag-ui`  
**Created**: 2025-12-22  
**Status**: Draft  
**Input**: User description: "Add CopilotKit as the consumer of Events and build a React frontend in multi_agent_ide/fe that receives graph events, displays them, and provides UI to interact with agents."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Live Graph Updates (Priority: P1)

As a user, I want to see the graph update in real time as agents run so I can track progress without refreshing.

**Why this priority**: This is the core value of the UI and enables all other interactions.

**Independent Test**: Can be fully tested by publishing a stream of graph events and confirming the UI reflects node creation and status transitions.

**Cucumber Test Tag**: @ag_ui_live_graph

**Acceptance Scenarios**:

1. **Given** a graph event stream with node added and status change events, **When** the UI receives the events, **Then** the graph displays the node and current status.
2. **Given** a graph event stream with a worktree created event, **When** the UI receives the event, **Then** the worktree appears in the graph metadata for that node.

---

### User Story 2 - Inspect Node Details (Priority: P2)

As a user, I want to select a node to see its event history and latest messages so I can understand what the agent is doing.

**Why this priority**: Users need context to interpret what is happening and decide whether to intervene.

**Independent Test**: Can be fully tested by selecting a node and verifying its event list and latest message are shown.

**Cucumber Test Tag**: @ag_ui_node_details

**Acceptance Scenarios**:

1. **Given** a node has emitted message and status events, **When** I select the node, **Then** I can see its recent events and latest message content.

---

### User Story 3 - Control Agent Execution (Priority: P3)

As a user, I want to pause or interrupt an agent from the UI so I can intervene when needed.

**Why this priority**: User control is important but can follow basic visibility of events.

**Independent Test**: Can be fully tested by issuing a control action and confirming the corresponding control event is recorded.

**Cucumber Test Tag**: @ag_ui_agent_controls

**Acceptance Scenarios**:

1. **Given** a running agent node is visible, **When** I request a pause or interrupt, **Then** a control event is emitted and the UI reflects the requested state.

---

### Edge Cases

- What happens when the UI receives an unknown event type?
- How does the system handle out-of-order events for the same node?
- What happens when the event stream resumes after a temporary disconnect?

### Scope

**In Scope**:
- Event ingestion for graph updates
- Graph visualization with node status
- Node detail inspection with event history and messages
- Agent control actions (pause, interrupt, review request)

**Out of Scope**:
- User authentication and permissions
- Persisting UI state across sessions
- Agent execution logic changes

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST ingest graph events and make them available to the UI in near real time.
- **FR-002**: UI MUST render nodes, relationships, and statuses based on the event stream.
- **FR-003**: Users MUST be able to view a selected node's event history and latest message content.
- **FR-004**: System MUST display worktree creation and merge events in node metadata.
- **FR-005**: Users MUST be able to issue control actions (pause, interrupt, review request) for a selected node.
- **FR-006**: System MUST record and surface control actions as events so the UI reflects state changes.
- **FR-007**: System MUST handle unknown event types by surfacing them as generic entries without breaking the UI.
- **FR-008**: System MUST support resuming event display after a temporary connection loss without duplicating entries.
- **FR-009**: System MUST map backend graph events to the ag-ui event types before sending to the frontend.
- **FR-010**: System MUST support custom event mappings when no ag-ui event type exists.
- **FR-011**: System MUST expose control actions through non-WebSocket interfaces compatible with the frontend event consumer.
- **FR-012**: UI MUST support multiple viewer plugins for different node/event content types.
- **FR-013**: UI MUST select the appropriate viewer plugin based on node type, event type, or tool call metadata.
- **FR-014**: UI MUST allow new viewer plugins to be added without modifying existing viewer implementations.

### Key Entities *(include if feature involves data)*

- **Graph Event**: A time-ordered record with type, timestamp, and optional node reference and payload.
- **Ag-UI Event**: The UI-facing event type that mirrors or maps from a backend graph event.
- **Graph Node**: A unit of work with id, type, title, status, and parent relationship.
- **Worktree**: A work context tied to a node, identified by an id and path.
- **Agent Control Action**: A user-issued action targeting a node (pause, interrupt, review request).
- **Event Mapping Rule**: A definition that transforms a backend graph event into an ag-ui event or custom event.
- **Viewer Plugin**: A UI component that renders node or event content for a specific type or tool call.
- **Plugin Registry**: A list of available viewer plugins and their matching rules.

### Assumptions

- The existing event schema is the source of truth for event types and payloads.
- The ag-ui event definitions in libs are the target schema for frontend events.
- The UI will consume events through a single text/event-stream that can be replayed or resumed after disconnect.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 95% of graph events appear in the UI within 2 seconds of emission under typical load.
- **SC-002**: Users can identify the current status of any node in under 10 seconds.
- **SC-003**: 90% of users can locate a node's latest message without assistance on first attempt.
- **SC-004**: Median time to detect a stalled agent drops by 30% compared to the current workflow.

## Notes on Gherkin Feature Files For Test Graph

The functional requirements, success criteria, edge cases, and measurable outcomes will then be encoded in feature files
using Gherkin and Cucumber in test_graph/src/test/resources/features/{name}.

The translation to Gherkin feature files should adhere to the instructions test_graph/instructions.md and also
information about the test_graph framework can be found under test_graph/instructions-features.md. When completing
the integration test feature files in the test_graph, check to see if feature files already exist, and if so use the existing
step definitions if at all possible.

When writing the feature files and step definitions for those files, make sure to create as steps as possible, and make
the setup as generic as possible. It is as simple as provide information to be added to the context, then assert that
messages were received as expected. There should not be a lot of different step definitions. Additionally, Spring
is used so we can decouple initialization logic. This means by adding a bean of a type as a component accepting a
context and depending on other beans in that computation graph, we can then add an assertion over that context without
changing any of the other code. This is what it means to run the tests together but have them independent. They are
sufficiently decoupled to be useful over time and minimize regressions.
