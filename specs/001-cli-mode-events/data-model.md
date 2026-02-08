# Data Model: CLI mode interactive event rendering

## Entities

### CLI Session

- **Fields**: sessionId, startedAt, mode, activeGoalId, status
- **Notes**: Represents a single interactive CLI run.

### Goal

- **Fields**: goalId, description, startedAt, completedAt, orchestratorNodeId, status
- **Notes**: Initiates a workflow run and anchors events to a root node.

### Event

- **Fields**: eventId, eventType, timestamp, nodeId, payload, category
- **Notes**: Captures an emitted execution event that can be rendered in the CLI.

### TuiEvent (sealed interface)

- **Fields**: type-specific payload
- **Notes**: Base sealed interface for all TUI events.

### TuiInteractionEvent (sealed interface)

- **Fields**: type-specific payload
- **Notes**: Sealed interface for user-driven TUI interactions. Extends `TuiEvent`.

### TUI Interaction Event (GraphEvent)

- **Fields**: eventId, timestamp, sessionId, tuiEvent
- **Notes**: Represents a user interaction in the TUI (navigation, open/close detail, submit chat). Emitted as a `GraphEvent`.

### TuiSystemEvent (sealed interface)

- **Fields**: type-specific payload
- **Notes**: Sealed interface for non-user TUI events (e.g., incoming stream updates). Extends `TuiEvent`.

### TuiEvent Hierarchy

- **TuiEvent**
- **TuiInteractionEvent**
- **TuiSystemEvent**

### TuiInteractionEvent Implementations (mapped to actions)

- **EventStreamMoveSelection**: move selection up/down in event list (Up/Down). Fields: direction, newSelectedIndex.
- **EventStreamScroll**: scroll event list window (PageUp/PageDown). Fields: direction, newScrollOffset.
- **EventStreamOpenDetail**: open detail pane for selected event (Enter). Fields: eventId.
- **EventStreamCloseDetail**: close detail pane (Escape/Back). Fields: eventId.
- **FocusChatInput**: move focus to chat input (Tab or dedicated key). Fields: previousFocus.
- **FocusEventStream**: move focus to event list (Tab or dedicated key). Fields: previousFocus.
- **ChatInputChanged**: update draft chat input. Fields: text, cursorPosition.
- **ChatInputSubmitted**: send chat message. Fields: text.
- **ChatSearchOpened**: open search mode for chat. Fields: initialQuery.
- **ChatSearchQueryChanged**: update chat search query. Fields: query, cursorPosition.
- **ChatSearchResultNavigate**: move to next/previous search result. Fields: direction, resultIndex.
- **ChatSearchClosed**: exit search mode. Fields: query.

### TuiSystemEvent Implementations (mapped to actions)

- **EventStreamAppended**: new GraphEvent arrives for display. Fields: graphEventId, rowIndex.
- **EventStreamTrimmed**: event list truncated to max history. Fields: removedCount.
- **ChatMessageAppended**: new chat message arrives. Fields: messageId, rowIndex.
- **ChatHistoryTrimmed**: chat list truncated to max history. Fields: removedCount.

### TUI State

- **Fields**: sessionId, events, selectedIndex, scrollOffset, detailOpen, detailEventId, chatInput, focus, chatMessages, chatScrollOffset, chatSearch, chatSearchResults
- **Notes**: Derived state used to render the TUI. Updated only from `GraphEvent` streams.

### TUI State Transition

- **Fields**: previousState, graphEvent, nextState
- **Notes**: Pure reducer transition for `GraphEvent` → `TuiState`.

## Input Mapping (Keyboard)

- **Up/Down**: `EventStreamMoveSelection`
- **PageUp/PageDown**: `EventStreamScroll`
- **Enter (event stream)**: `EventStreamOpenDetail`
- **Escape/Backspace (detail open)**: `EventStreamCloseDetail`
- **Tab**: toggle focus between event stream and chat input (`FocusChatInput` / `FocusEventStream`)
- **Enter (chat input)**: `ChatInputSubmitted`
- **Ctrl+F**: `ChatSearchOpened`
- **Typing in chat search**: `ChatSearchQueryChanged`
- **Up/Down (chat search results)**: `ChatSearchResultNavigate`
- **Escape (chat search)**: `ChatSearchClosed`

### CLI Event Listener

- **Fields**: listenerId, subscribedAt, eventFilters, outputFormat
- **Notes**: Subscribes to the event system when CLI mode is active.

### Interrupt Prompt

- **Fields**: promptId, nodeId, interruptType, message, requestedAt, timeoutAt, status
- **Notes**: Represents a CLI prompt awaiting user input.

### Interrupt Response

- **Fields**: promptId, responseText, respondedAt
- **Notes**: Captures user input that resumes the workflow.

### Artifact Key

- **Fields**: keyValue, parentKey, rootKey, depth
- **Notes**: Hierarchical identifier used to derive action lifecycle context.

## Relationships

- A **CLI Session** has zero or one active **Goal** at a time.
- A **Goal** is associated with many **Events**; events reference the goal via the root segment of `nodeId` (artifact key).
- A **CLI Event Listener** is bound to one **CLI Session** and observes many **Events**.
- An **Interrupt Prompt** is linked to a specific `nodeId` and may have zero or one **Interrupt Response**.
- **Artifact Key** hierarchy provides parent/child context for action lifecycle rendering.
- A **TUI Interaction Event** is emitted for each user action and feeds the **TUI State Transition** reducer.
- **TUI State** is the sole source of truth for rendering the interactive CLI view.
