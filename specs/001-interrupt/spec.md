# Feature Specification: Interrupt Handling & Continuations

**Feature Branch**: `001-interrupt`  
**Created**: 2025-12-28  
**Status**: Draft  
**Input**: User description: "Analyze and test interrupt/continuation handling; ensure interrupts route back to source node and add missing interrupt nodes in AgentRunner."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Interrupt review and resume (Priority: P1)

As a workflow operator, I need the system to pause work when an agent requests a review and then resume the original work once the review is completed, so that critical decisions are validated without losing progress.

**Why this priority**: Review interrupts block forward progress and must be handled correctly to prevent stalled or incorrect workflows.

**Independent Test**: Can be fully tested by triggering a review interrupt and verifying that the workflow resumes the original work after the review decision is recorded.

**Cucumber Test Tag**: `@interrupt_review_resume`

**Acceptance Scenarios**:

1. **Given** a workflow is running, **When** an agent requests human review, **Then** the system pauses the workflow and creates a review task that references the requesting work.
2. **Given** a review task is completed, **When** the decision is recorded, **Then** the system returns execution to the original work and continues the workflow.

---

### User Story 2 - Interrupt pause and continuation (Priority: P2)

As a workflow operator, I need the system to pause on a manual interrupt (pause or input required) and then continue the correct stage after the input is provided, so that workflows can safely wait for external decisions or data.

**Why this priority**: Manual interrupts are common in mixed automation and must not cause lost state or incorrect routing.

**Independent Test**: Can be fully tested by triggering a pause interrupt, submitting input, and verifying that the workflow resumes the same stage.

**Cucumber Test Tag**: `@interrupt_pause_resume`

**Acceptance Scenarios**:

1. **Given** a workflow is running, **When** an agent requests a pause for input, **Then** the system records the pause and exposes a clear resume point for the same work.
2. **Given** input is provided for a paused workflow, **When** the resume action is taken, **Then** the system continues from the interrupted work and advances to the next stage.

---

### User Story 3 - Interrupt stop, prune, or branch (Priority: P3)

As a workflow operator, I need the system to correctly stop, prune, or branch work when an agent requests it, so that the workflow reflects the intended decision without unintended continuation.

**Why this priority**: These interrupts change workflow scope and must be handled deterministically to avoid incorrect outputs.

**Independent Test**: Can be fully tested by triggering each interrupt type and verifying the resulting workflow state and routing.

**Cucumber Test Tag**: `@interrupt_route_control`

**Acceptance Scenarios**:

1. **Given** a workflow is running, **When** an agent requests a stop or prune, **Then** the system ends or removes the relevant work and does not continue downstream stages.
2. **Given** a workflow is running, **When** an agent requests a branch, **Then** the system creates a new work path and continues each path from the correct originating point.

---

### Edge Cases

- Multiple interrupts are requested by different work items at the same time.
- A review or pause is requested after the workflow is already marked as completed or failed.
- A stop/prune interrupt is requested for a work item that has already transitioned to a terminal state.
- A resume action is attempted without any recorded interrupt to resolve.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST capture every interrupt request and associate it with the originating work item.
- **FR-002**: System MUST send the interrupt sequence to the agent with the memory ID set to the originating node ID.
- **FR-003**: System MUST store the agent result on the originating node before emitting an interrupted status event.
- **FR-004**: System MUST pause the affected workflow stage when a review or pause interrupt is requested by moving the node to an interrupted status.
- **FR-005**: System MUST prevent continuation of downstream stages when a stop or prune interrupt is requested and mark the node terminal.
- **FR-006**: System MUST create the appropriate interrupt node after the interrupted status event is emitted.
- **FR-007**: System MUST resume execution at the originating work item after an interrupt is resolved.
- **FR-008**: System MUST create a distinct work path when a branch interrupt is requested and continue from the correct origin.
- **FR-009**: System MUST record an auditable history of interrupt requests and their resolutions.
- **FR-010**: System MUST surface clear status indicators for workflows that are awaiting review or input.

### Key Entities *(include if feature involves data)*

- **Interrupt Request**: A record of the interrupt type, originating work item, reason, and timestamp.
- **Interrupt Resolution**: A record of the decision or input that resolves an interrupt.
- **Workflow Stage**: The unit of work that can be paused, resumed, stopped, pruned, or branched.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 100% of interrupt requests result in a visible interrupt record within 1 minute.
- **SC-002**: 100% of resolved interrupts resume or terminate the workflow at the correct originating stage.
- **SC-003**: 0 workflows continue to downstream stages after a stop or prune interrupt.
- **SC-004**: At least 95% of interrupt-based workflows complete without manual rework to correct routing.

## Assumptions

- Interrupt types include review, pause, stop, prune, and branch.
- Workflow operators can provide review decisions or input needed to resume paused work.
- User-triggered stop or pause interrupts return an agent result that is persisted before status transitions are emitted.

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
