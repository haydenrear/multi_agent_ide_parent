# Feature Specification: Multi-Agent IDE Baseline

**Feature Branch**: `001-multi-agent-ide-specs`  
**Created**: 2025-12-20  
**Status**: Draft  
**Input**: User description: "Create specifications for multi_agent_ide in the current state to prepare for testing and integration testing."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - End-to-End Orchestration Without Submodules (Priority: P1)

As a workflow operator, I want the full orchestration flow to complete for a repository without submodules so I can validate the baseline workflow.

**Why this priority**: The non-submodule flow is the default path and must be reliable before adding complexity.

**Independent Test**: Can be tested by executing orchestrator → discovery → planning → ticket → review → merge and validating lifecycle events.

**Cucumber Test Tag**: @multi_agent_ide_end_to_end_no_submodules

**Acceptance Scenarios**:

1. **Given** a computation graph for a goal with discovery, planning, ticket, review, and merge nodes, **When** the graph execution completes, **Then** node-added and node-status-changed events are emitted for each phase.
2. **Given** a computation graph for a goal without submodules, **When** the graph execution completes, **Then** worktree-created events are emitted only for the main repository.
3. **Given** an orchestration run without submodules, **When** the graph execution completes, **Then** review-requested and merge completion events are emitted in sequence.

---

### User Story 2 - End-to-End Orchestration With Submodules (Priority: P2)

As a workflow operator, I want the full orchestration flow to complete for a repository with submodules so I can validate submodule worktree coordination and merges.

**Why this priority**: Submodule workflows are higher risk and must be verified end-to-end.

**Independent Test**: Can be tested by running an orchestration that creates main and submodule worktrees and validating events.

**Cucumber Test Tag**: @multi_agent_ide_end_to_end_with_submodules

**Acceptance Scenarios**:

1. **Given** a computation graph for a goal with submodules, **When** the graph execution completes, **Then** worktree-created events are emitted for the main repository and each submodule.
2. **Given** a submodule-enabled run with discovery, planning, ticket, review, and merge nodes, **When** the graph execution completes, **Then** node-status-changed events are emitted for each phase.
3. **Given** a submodule-enabled run, **When** the graph execution completes, **Then** merge completion events are emitted for both main and submodule worktrees.

---

### User Story 3 - Revision Cycle and Failure Handling (Priority: P3)

As a workflow operator, I want revision cycles and failures to be visible in the event stream so I can validate recovery and review decisions.

**Why this priority**: Revision and failure paths are common in agent workflows and must be observable.

**Independent Test**: Can be tested by simulating a review rejection or execution failure and validating lifecycle events.

**Cucumber Test Tag**: @multi_agent_ide_revision_failure

**Acceptance Scenarios**:

1. **Given** a computation graph where a review node requests changes, **When** the graph execution completes, **Then** the ticket node is re-queued and emits new status-change events.
2. **Given** a computation graph where a work node fails during execution, **When** the graph execution completes, **Then** the node transitions to FAILED with captured error metadata and a status-change event.

### Edge Cases

- A node status update arrives for a node that is not present in the graph.
- A graph with no submodules emits only main worktree creation events.
- A review node approves with no content and still emits a completion event.
- An agent execution fails and emits a status change to FAILED with error metadata.
- A revision cycle is triggered multiple times before approval.
- No external model credentials are configured and mock responses are used instead.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST store computation graph nodes with their status, parent-child relationships, and timestamps.
- **FR-002**: System MUST emit events when nodes are added to the graph.
- **FR-003**: System MUST emit events for node status transitions (READY, RUNNING, COMPLETED, FAILED).
- **FR-004**: System MUST emit events when worktrees are created for main repositories and submodules.
- **FR-005**: System MUST support a multi-phase workflow that can create discovery, planning, ticket, review, and merge nodes.
- **FR-006**: System MUST allow event subscribers to receive the current event stream without requiring access to internal storage.
- **FR-007**: System MUST record failure metadata when a node transitions to FAILED.
- **FR-008**: System MUST support revision cycles that re-queue ticket execution after review feedback.
- **FR-009**: System MUST emit review-requested events when a review step is initiated.
- **FR-010**: System MUST emit merge completion events when merges are completed.

## Assumptions

- Event streaming is available to test harnesses through a supported subscription channel.
- Worktree creation is enabled for orchestration runs that require repository isolation.
- Scope is limited to graph lifecycle, worktree coordination, and event visibility; user management and UI concerns are out of scope.

### Key Entities *(include if feature involves data)*

- **Computation Graph Node**: A unit of work with status, metadata, parent-child links, and lifecycle timestamps.
- **Worktree Context**: An execution sandbox for a repository or submodule, linked to a graph node.
- **Graph Event**: A published lifecycle signal for node creation, status changes, review requests, or worktree actions.
- **Agent Invocation**: An execution attempt tied to a graph node that produces output and status updates.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of node additions produce a corresponding node-added event visible to subscribers.
- **SC-002**: 100% of node status transitions produce a corresponding node-status-changed event visible to subscribers.
- **SC-003**: Worktree-enabled runs emit a worktree-created event for every created worktree.
- **SC-004**: End-to-end runs without submodules complete with all expected phase events observed in order.
- **SC-005**: End-to-end runs with submodules complete with worktree and merge events observed for each submodule.

## Notes on Gherkin Feature Files For Test Graph

The functional requirements, success criteria, edge cases, and measurable outcomes will then be encoded in feature files
using Gherkin and Cucumber in test_graph/src/test/resources/features/{name}.

The translation to Gherkin feature files should adhere to the instructions test_graph/instructions.md and also
information about the test_graph framework can be found under test_graph/instructions-features.md. When completing
the integration test feature files in the test_graph, check to see if feature files already exist, and if so use the existing
step definitions if at all possible. 

When writing the feature files and step definitions for those files, make sure to create as steps as possible, and make
the setup as generic as possible. It is as simple as provide information to be added to the context, then assert that
messages were received as expected. There should not be a lot of different step definitions. Additionally, the test
framework decouples initialization logic so assertions can be added without changing other components. This is what
it means to run the tests together but have them independent. They are sufficiently decoupled to be useful over time
and minimize regressions.
