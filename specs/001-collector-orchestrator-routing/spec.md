# Feature Specification: Collector Orchestrator Routing

**Feature Branch**: `001-collector-orchestrator-routing`  
**Created**: 2025-12-26  
**Status**: Draft  
**Input**: User description: "Enable collectors to return to orchestrators for additional agent runs, with optional human review after finished nodes to choose next phase or loop back."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Collector-Driven Re-Orchestration (Priority: P1)

As a workflow operator, I want collectors to be able to route results back to the orchestrator so additional agents can be launched when more work is needed.

**Why this priority**: Enables iterative planning and discovery loops without manual restarts, keeping complex workflows moving.

**Independent Test**: Can be fully tested by running a workflow where a collector indicates more work is required and verifying a new agent set is launched.

**Cucumber Test Tag**: @collector_orchestrator_reentry

**Acceptance Scenarios**:

1. **Given** an orchestrator-initiated planning phase, **When** the collector finishes and recommends additional planning, **Then** the orchestrator schedules another planning pass without advancing phases.
2. **Given** an orchestrator-initiated planning phase, **When** the collector finishes and indicates planning is complete, **Then** the orchestrator advances to the next phase without launching new planning agents.

---

### User Story 2 - Human Review Gate After Completion (Priority: P2)

As an operator, I want the option to review finished nodes before the workflow proceeds so I can direct the next phase or loop back when needed.

**Why this priority**: Human oversight is critical for high-impact decisions and allows course correction before expensive downstream work.

**Independent Test**: Can be fully tested by enabling review mode and verifying that a review request pauses execution and applies the chosen next action.

**Cucumber Test Tag**: @collector_human_review_gate

**Acceptance Scenarios**:

1. **Given** review mode is enabled and a collector completes, **When** the review request is approved to proceed, **Then** the workflow advances to the next phase.
2. **Given** review mode is enabled and a collector completes, **When** the reviewer chooses to rerun the current phase, **Then** the orchestrator launches another agent set for that phase.

---

### Edge Cases

- Review mode is enabled but no reviewer responds; the workflow remains paused and visible as awaiting review.
- A reviewer submits an invalid decision; the system rejects it and requests a valid choice.
- A collector recommends reruns beyond the configured limit; the workflow halts for human review instead of looping indefinitely.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST allow collectors to return completion results to their orchestrator.
- **FR-002**: The orchestrator MUST be able to launch additional agent runs in the same phase based on collector results.
- **FR-003**: The orchestrator MUST be able to advance to the next phase when collector results indicate completion.
- **FR-004**: The system MUST support an optional review gate that triggers when any node completes and pauses progression.
- **FR-005**: When review is enabled, reviewers MUST be able to choose one of: proceed to next phase, rerun current phase, or stop the workflow.
- **FR-006**: The system MUST apply the reviewer decision immediately and resume execution according to that choice.
- **FR-007**: The system MUST record each review decision and make it visible in the workflow history.
- **FR-008**: The system MUST prevent unbounded rerun loops by enforcing a maximum rerun count per phase.

### Key Entities *(include if feature involves data)*

- **Workflow Node**: A unit of work with a phase, status, and parent/child relationships.
- **Collector Result**: A summary of agent outcomes plus a recommended next action.
- **Review Request**: A pending decision tied to a completed node and available choices.
- **Review Decision**: The selected outcome, timestamp, reviewer identity, and optional rationale.
- **Phase Transition**: The recorded movement from one phase to another, including reruns.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In 95% of workflows, the next action (rerun or phase advance) is visible to operators within 10 seconds of collector completion.
- **SC-002**: At least 90% of workflows that require additional planning reruns complete without manual restarts.
- **SC-003**: 100% of review requests present valid choices and reflect the chosen action within 1 minute of submission.
- **SC-004**: Support tickets related to "workflow stuck after collector completion" decrease by 50% within one quarter of release.

## Out of Scope

- Redesigning workflow user interfaces beyond the review decision prompt.
- Changing authentication or permission models for who can act as a reviewer.
- Introducing new automated decision policies beyond existing collector guidance.

## Assumptions

- Review mode can be enabled or disabled per workflow execution.
- If review is disabled, the system follows default progression rules from collector results.
- If no review response is provided, the workflow remains paused until a decision is made.

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
