# Feature Specification: ACP Model Type Support

**Feature Branch**: `001-acp-chatmodel`  
**Created**: 2025-12-22  
**Status**: Draft  
**Input**: User description: "Create an implementation of dev.langchain4j.model.chat.ChatModel that uses agentclientprotocol https://github.com/agentclientprotocol/kotlin-sdk/blob/master/samples/kotlin-acp-client-sample which has a kotlin SDK - and can therefore be used with Java. So we'll be starting to provide codex implementation so that we can run with codex."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - End-to-End Orchestration Without Submodules (Priority: P1)

As a workflow operator, I want the full orchestration flow to complete for a repository without submodules when the model type is ACP so I can validate that ACP-backed runs behave the same as the baseline workflow.

**Why this priority**: The non-submodule flow is the default path and must remain reliable when ACP is selected.

**Independent Test**: Can be tested by executing orchestrator → discovery → planning → ticket → review → merge with model type set to ACP and validating lifecycle events.

**Cucumber Test Tag**: @multi_agent_ide_end_to_end_no_submodules

**Acceptance Scenarios**:

1. **Given** a computation graph for a goal without submodules and model type set to ACP, **When** the graph execution completes, **Then** node-added and node-status-changed events are emitted for each phase.
2. **Given** a computation graph for a goal without submodules and model type set to ACP, **When** the graph execution completes, **Then** worktree-created events are emitted only for the main repository.
3. **Given** an orchestration run without submodules and model type set to ACP, **When** the graph execution completes, **Then** review-requested and merge completion events are emitted in sequence.

---

### User Story 2 - End-to-End Orchestration With Submodules (Priority: P2)

As a workflow operator, I want the full orchestration flow to complete for a repository with submodules when the model type is ACP so I can validate submodule coordination remains correct.

**Why this priority**: Submodule workflows are higher risk and must be verified end-to-end with ACP.

**Independent Test**: Can be tested by running an orchestration that creates main and submodule worktrees with model type set to ACP and validating events.

**Cucumber Test Tag**: @multi_agent_ide_end_to_end_with_submodules

**Acceptance Scenarios**:

1. **Given** a computation graph for a goal with submodules and model type set to ACP, **When** the graph execution completes, **Then** worktree-created events are emitted for the main repository and each submodule.
2. **Given** a submodule-enabled run with discovery, planning, ticket, review, and merge nodes and model type set to ACP, **When** the graph execution completes, **Then** node-status-changed events are emitted for each phase.
3. **Given** a submodule-enabled run and model type set to ACP, **When** the graph execution completes, **Then** merge completion events are emitted for both main and submodule worktrees.

---

### User Story 3 - Revision Cycle and Failure Handling (Priority: P3)

As a workflow operator, I want revision cycles and failures to be visible in the event stream when the model type is ACP so I can validate recovery and review decisions are unchanged.

**Why this priority**: Revision and failure paths are common in agent workflows and must remain observable with ACP.

**Independent Test**: Can be tested by simulating a review rejection or execution failure with model type set to ACP and validating lifecycle events.

**Cucumber Test Tag**: @multi_agent_ide_revision_failure

**Acceptance Scenarios**:

1. **Given** a computation graph where a review node requests changes and model type set to ACP, **When** the graph execution completes, **Then** the ticket node is re-queued and emits new status-change events.
2. **Given** a computation graph where a work node fails during execution and model type set to ACP, **When** the graph execution completes, **Then** the node transitions to FAILED with captured error metadata and a status-change event.

### Edge Cases

- A node status update arrives for a node that is not present in the graph.
- A graph with no submodules emits only main worktree creation events.
- A review node approves with no content and still emits a completion event.
- An agent execution fails and emits a status change to FAILED with error metadata.
- A revision cycle is triggered multiple times before approval.
- Model type is set to ACP but no ACP endpoint is configured.

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
- **FR-011**: System MUST allow the model type to be selected per run, including ACP and HTTP options.

## Assumptions

- Event streaming is available to test harnesses through a supported subscription channel.
- Worktree creation is enabled for orchestration runs that require repository isolation.
- Selecting ACP does not introduce user-visible workflow changes beyond the underlying model provider.
- Scope is limited to graph lifecycle, worktree coordination, and event visibility; user management and UI concerns are out of scope.
- This feature is scoped to the multi_agent_ide application.

### Key Entities *(include if feature involves data)*

- **Computation Graph Node**: A unit of work with status, metadata, parent-child links, and lifecycle timestamps.
- **Worktree Context**: An execution sandbox for a repository or submodule, linked to a graph node.
- **Graph Event**: A published lifecycle signal for node creation, status changes, review requests, or worktree actions.
- **Agent Invocation**: An execution attempt tied to a graph node that produces output and status updates.
- **Model Selection**: The chosen model type for a run, such as ACP or HTTP.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of node additions produce a corresponding node-added event visible to subscribers.
- **SC-002**: 100% of node status transitions produce a corresponding node-status-changed event visible to subscribers.
- **SC-003**: Worktree-enabled runs emit a worktree-created event for every created worktree.
- **SC-004**: End-to-end runs without submodules complete with all expected phase events observed in order when model type is ACP.
- **SC-005**: End-to-end runs with submodules complete with worktree and merge events observed for each submodule when model type is ACP.

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
