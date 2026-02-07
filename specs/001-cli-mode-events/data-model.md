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
