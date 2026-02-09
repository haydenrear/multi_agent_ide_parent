# Feature Specification: CLI mode interactive event rendering

**Feature Branch**: `001-cli-mode-events`  
**Created**: 2026-02-07  
**Status**: Draft  
**Input**: User description: "CLI mode for multi-agent IDE with interactive goal prompt, interrupt input, and event rendering"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Run a goal from the CLI (Priority: P1)

As a user, I can start the application in CLI mode, enter a goal, and watch the execution events stream in real time so I can follow progress without a GUI.

**Why this priority**: It is the primary workflow for running the app in CLI mode.

**Independent Test**: Start in CLI mode, enter a goal, and verify that a running session starts and events are rendered.

**Cucumber Test Tag**: `@cli_goal_run`

**Acceptance Scenarios**:

1. **Given** the application is started in CLI mode, **When** I enter a non-empty goal, **Then** the goal starts and events are rendered to the console.
2. **Given** the goal is running, **When** tool calls occur, **Then** the tool call events are rendered in the CLI event stream.
3. **Given** the application is started in non-CLI mode, **When** a goal runs, **Then** CLI event rendering is not active.

---

### User Story 2 - Handle interrupts interactively (Priority: P1)

As a user, when an interrupt occurs, I am prompted for input and the workflow resumes after I respond in the CLI chat input, so I can keep the run moving without leaving the CLI.

**Why this priority**: Interrupt handling is necessary to complete many workflows.

**Independent Test**: Start a goal that triggers an interrupt, enter a response, and verify continuation events appear.

**Cucumber Test Tag**: `@cli_interrupt_input`

**Acceptance Scenarios**:

1. **Given** a running goal, **When** an interrupt is raised, **Then** the CLI prompts me for input.
2. **Given** I submit an interrupt response, **When** the workflow resumes, **Then** the CLI shows the resume event and continues rendering subsequent events.
3. **Given** an interrupt is pending, **When** I press Enter in the TUI chat with my response, **Then** the input is interpreted as interrupt resolution and is not forwarded as a normal chat message.
4. **Given** the CLI fails to start a goal, **When** the failure is detected, **Then** a clear error is shown and I am re-prompted for a goal.

---

### User Story 3 - See action lifecycle with hierarchy (Priority: P2)

As a user, I can see when an agent action starts and completes, including where it sits in the agent graph hierarchy, so I understand which part of the workflow is executing.

**Why this priority**: It improves transparency and debuggability.

**Independent Test**: Run a goal with multiple actions and confirm action-start and action-completed events are rendered with hierarchy context.

**Cucumber Test Tag**: `@cli_action_started`

**Acceptance Scenarios**:

1. **Given** a goal with multiple actions, **When** a new action starts, **Then** an action-started event appears in the CLI output with its hierarchy context.
2. **Given** a running goal, **When** an action completes, **Then** an action-completed event appears in the CLI output with the same hierarchy context.

---

### User Story 4 - See diverse event types in the CLI (Priority: P2)

As a user, I can see different categories of events in the CLI so I can understand the full execution lifecycle without a GUI.

**Why this priority**: It provides comprehensive visibility and improves debugging.

**Independent Test**: Run a goal that emits multiple event categories and verify each category is rendered.

**Cucumber Test Tag**: `@cli_event_categories`

**Acceptance Scenarios**:

1. **Given** a running goal, **When** node lifecycle events occur, **Then** those events are rendered in the CLI output.
2. **Given** a running goal, **When** interrupt and permission events occur, **Then** those events are rendered in the CLI output.
3. **Given** a running goal, **When** worktree and plan update events occur, **Then** those events are rendered in the CLI output.
4. **Given** a running goal, **When** UI and rendering events occur, **Then** those events are rendered in the CLI output.

---

### User Story 5 - Use an interactive TUI for event history and chat (Priority: P1)

As a user, I can navigate a scrolling event history with the keyboard while still typing into a persistent chat box, so I can inspect past events without losing the ability to send input.

**Why this priority**: It is required for the interactive CLI experience and for future supervisor automation.

**Independent Test**: Start a goal, verify events stream into a scrollable list, move selection with arrow keys, open a detail pane, and send chat input without leaving the view.

**Cucumber Test Tag**: `@cli_tui_event_navigation`

**Acceptance Scenarios**:

1. **Given** the CLI TUI is running, **When** I press Up/Down, **Then** the event selection changes within the scrollable history.
2. **Given** an event is selected, **When** I press Enter, **Then** a detail pane opens showing expanded event data.
3. **Given** the TUI is running, **When** I type in the chat box and press Enter, **Then** the message is sent while the event history remains visible.
4. **Given** many events have streamed, **When** I scroll beyond the visible area, **Then** the TUI loads earlier events in the list without losing the current chat input.
5. **Given** a permission request is pending, **When** I submit input in the chat box, **Then** the input is interpreted as a permission resolution command.
6. **Given** a permission or interrupt request is pending, **When** chat input is consumed to resolve it, **Then** no normal user message is emitted for that submission.

---

### Edge Cases

- What happens when the user submits an empty goal?
- How does the system handle an interrupt prompt timing out without user input?
- What happens if the event stream emits a malformed event payload?
- What happens if an event is missing hierarchy context for an action?
- How does the CLI handle an unknown or new event type?
- How does the CLI behave when a goal completes while an interrupt prompt is visible?
- How does the TUI behave if events arrive while a detail pane is open?
- How does the TUI behave if a user scrolls while events stream in rapidly?
- How should the system behave when both a permission request and an interrupt request are pending at the same time?
- What user feedback is shown when permission input cannot be matched to an explicit option?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST provide a CLI mode that runs the application without a GUI.
- **FR-002**: System MUST prompt the user for a goal when CLI mode starts.
- **FR-003**: System MUST reject empty goals and re-prompt the user.
- **FR-004**: System MUST render the event stream to the CLI in arrival order.
- **FR-005**: System MUST render tool call events in the CLI output.
- **FR-006**: System MUST render action-started events in the CLI output.
- **FR-007**: When an interrupt occurs, the system MUST prompt for user input in the CLI.
- **FR-008**: After receiving interrupt input, the system MUST resume the workflow and render the resume event.
- **FR-009**: CLI event rendering MUST be active only when the application is running in CLI mode.
- **FR-010**: System MUST surface a clear error message when the CLI cannot start a goal.
- **FR-011**: System MUST connect a CLI event listener to the event system when CLI mode is active.
- **FR-012**: System MUST render emitted events across major categories, including node lifecycle, action lifecycle, interrupts, permissions, tool calls, worktree changes, plan updates, UI/render updates, and user message chunks.
- **FR-013**: Action-started and action-completed events MUST include hierarchy context derived from artifact keys.
- **FR-014**: The CLI MUST display hierarchy context for action lifecycle events in a consistent, readable format.
- **FR-015**: Unknown event types MUST be rendered with a generic fallback that includes event type and available metadata.
- **FR-016**: The CLI TUI MUST provide a scrollable event history with keyboard navigation (Up/Down) and a detail pane opened via Enter.
- **FR-017**: The CLI TUI MUST provide a persistent chat input box that can send messages without leaving the event history view.
- **FR-018**: Every TUI interaction MUST emit a `GraphEvent` representation of that interaction.
- **FR-019**: The TUI MUST be state-driven: `GraphEvent` → `TuiState` → rendered TUI changes.
- **FR-020**: On each chat submission, the system MUST first check for pending permission or interrupt requests before treating the input as a normal chat message.
- **FR-021**: If a permission request is pending, the system MUST parse chat input as a permission resolution command (for example: index selection, explicit option identifier, or cancel) and resolve that permission request.
- **FR-022**: If no permission request is pending and an interrupt request is pending, the system MUST parse chat input as an interrupt resolution payload and resolve that interrupt request.
- **FR-023**: When chat input is consumed to resolve a pending permission or interrupt, the system MUST NOT emit a normal user chat message for that submission.
- **FR-024**: Permission and interrupt resolution through TUI chat MUST use the same underlying resolution semantics as the existing permission/interrupt resolution APIs.

### Key Entities *(include if feature involves data)*

- **CLI Session**: A single interactive run of the application in CLI mode.
- **Goal**: The user-provided objective that initiates an execution run.
- **Event**: A structured notification emitted during execution, including tool calls and action-started events.
- **CLI Event Listener**: The component that subscribes to the event system when CLI mode is active.
- **Interrupt Prompt**: The CLI request for user input triggered by an interrupt.
- **Interrupt Response**: The user’s input that allows the workflow to continue.
- **Permission Prompt**: A pending request that asks the user to choose a permission option for a tool call.
- **Permission Resolution Command**: Chat input interpreted as a permission decision for the pending permission request.
- **Artifact Key**: The hierarchical identifier that defines parent/child relationships in the agent graph.
- **TUI Interaction Event**: A `GraphEvent` emitted for user interactions (navigation, open detail, send message).
- **TUI State**: The derived state used to render the interactive CLI view.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 95% of CLI sessions display the goal prompt within 2 seconds of startup.
- **SC-002**: 100% of tool call events are rendered in the CLI output for a completed run.
- **SC-003**: 100% of action-started events are rendered in the CLI output for a completed run.
- **SC-004**: 90% of users can complete an interrupt response and see the workflow continue without restarting the session.
- **SC-005**: 100% of emitted event categories in a representative run are rendered in the CLI output.
- **SC-006**: 100% of action lifecycle events display hierarchy context derived from artifact keys.

## Assumptions

- CLI mode uses the same execution logic as the standard application run, with only the interaction and rendering channel changed.
- A single CLI session handles one active goal at a time.
- Event rendering does not require additional configuration beyond starting in CLI mode.
- The event system exposes a stable listener interface for CLI mode to consume execution events.
- Pending permission and interrupt requests are queryable during chat submit handling.

## Out of Scope

- Building a graphical UI for CLI mode.
- Persisting CLI transcripts outside the standard application logs.
- Multi-goal concurrent execution within a single CLI session.

## Notes on Gherkin Feature Files For Test Graph

Per request, Gherkin feature files and test_graph updates are deferred for this feature at this time.
