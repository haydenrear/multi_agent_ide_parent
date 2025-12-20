# Data Model

## Core Entities

### Computation Graph Node
- **Description**: A unit of workflow execution with status, metadata, and parent-child relationships.
- **Key Fields**: nodeId, title, goal, status, parentNodeId, childNodeIds, metadata, timestamps.
- **Relationships**: Parent/child links form the orchestration graph.

### Worktree Context
- **Description**: Represents an execution sandbox for a repository or submodule.
- **Key Fields**: worktreeId, worktreePath, status, parentWorktreeId, associatedNodeId, commit hash.
- **Relationships**: Main worktrees can own submodule worktrees.

### Graph Event
- **Description**: Lifecycle signal emitted when nodes or worktrees change state.
- **Key Fields**: eventId, eventType, timestamp, nodeId/worktreeId, status details.
- **Relationships**: Events are consumed by listeners to drive orchestration.

### Agent Invocation
- **Description**: Execution attempt for an agent tied to a graph node.
- **Key Fields**: nodeId, agent type, output, interrupt requests.
- **Relationships**: Produces node status changes and triggers downstream nodes.
