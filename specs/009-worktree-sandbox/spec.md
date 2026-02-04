# Feature Specification: Worktree Sandbox for Agent File Operations

**Feature Branch**: `009-worktree-sandbox`  
**Created**: 2026-01-31  
**Status**: Draft  
**Input**: User description: "I need to better support my worktrees in my AgentModels.java. In AgentLifecycleHandler.java we are creating the worktree for the orchestrator. However, this worktree information is not passed to the OrchestratorRequest. Similarly, this information is not provided to any of the actions in AgentInterfaces.java. So in particular, to start, we'll want to make sure to add a decorator on the request that adds the worktree information. Then, we want to add sandbox for file system operations on the tools so that any file system tools are locked down by worktree. An agent can only make changes to the worktree it has access to. We'll need to add this support in AcpChatModel.kt as options passed to the acp client, and then we can also add that support in our AcpTooling.java. For that, we can use our SetFromHeader.java, and the header can be used to look up the associated node in the graph repository, and then block read, edit, write operations that happen on any other files."

## User Scenarios & Testing

### User Story 1 - Worktree Information Propagation (Priority: P1)

As a system administrator, I need worktree information to be automatically attached to agent requests so that each agent knows which file system sandbox it operates within, enabling proper isolation between concurrent agent workflows.

**Why this priority**: This is the foundational capability that all other sandboxing features depend on. Without worktree information flowing through agent requests, file system restrictions cannot be enforced.

**Independent Test**: Can be fully tested by creating an orchestrator with a worktree, triggering an agent action, and verifying the worktree path is available in the request context.

**Cucumber Test Tag**: `@worktree-propagation`

**Acceptance Scenarios**:

1. **Given** an orchestrator is initialized with a repository and worktree, **When** the orchestrator creates an agent request, **Then** the request contains the worktree path information.
2. **Given** a worktree has been created for an agent node, **When** any agent action is invoked, **Then** the action receives the worktree context through a request decorator.
3. **Given** an orchestrator with submodules, **When** agent requests are created, **Then** each request includes both the main worktree and relevant submodule worktree paths.

---

### User Story 2 - File System Read Sandboxing (Priority: P1)

As a security administrator, I need file read operations to be restricted to the agent's assigned worktree so that agents cannot access files outside their designated working area, preventing unauthorized data access across concurrent workflows.

**Why this priority**: Read access control is critical for security isolation. Agents must not be able to read files from other agents' worktrees or from the main repository outside their sandbox.

**Independent Test**: Can be tested by attempting to read a file inside the worktree (should succeed) and a file outside the worktree (should be blocked).

**Cucumber Test Tag**: `@sandbox-read`

**Acceptance Scenarios**:

1. **Given** an agent with an assigned worktree at path "/worktrees/agent-1", **When** the agent attempts to read a file within that worktree, **Then** the read operation succeeds.
2. **Given** an agent with an assigned worktree at path "/worktrees/agent-1", **When** the agent attempts to read a file at "/worktrees/agent-2/file.txt", **Then** the read operation is blocked with an appropriate error message.
3. **Given** an agent with an assigned worktree, **When** the agent attempts to read a file using a path traversal pattern (e.g., "../other-worktree/file.txt"), **Then** the read operation is blocked.

---

### User Story 3 - File System Write Sandboxing (Priority: P1)

As a security administrator, I need file write operations to be restricted to the agent's assigned worktree so that agents cannot modify files outside their designated working area, preventing cross-contamination of concurrent workflows.

**Why this priority**: Write access control is critical for maintaining workflow isolation. Agents must not be able to modify files in other agents' worktrees.

**Independent Test**: Can be tested by attempting to write a file inside the worktree (should succeed) and a file outside the worktree (should be blocked).

**Cucumber Test Tag**: `@sandbox-write`

**Acceptance Scenarios**:

1. **Given** an agent with an assigned worktree, **When** the agent attempts to write a file within that worktree, **Then** the write operation succeeds.
2. **Given** an agent with an assigned worktree at path "/worktrees/agent-1", **When** the agent attempts to write a file at "/worktrees/agent-2/file.txt", **Then** the write operation is blocked with an appropriate error message.
3. **Given** an agent with an assigned worktree, **When** the agent attempts to create a new file using path traversal, **Then** the write operation is blocked.

---

### User Story 4 - File System Edit Sandboxing (Priority: P1)

As a security administrator, I need file edit operations to be restricted to the agent's assigned worktree so that agents cannot modify existing files outside their designated working area.

**Why this priority**: Edit operations are a common attack vector and must be sandboxed alongside read and write operations.

**Independent Test**: Can be tested by attempting to edit a file inside the worktree (should succeed) and a file outside the worktree (should be blocked).

**Cucumber Test Tag**: `@sandbox-edit`

**Acceptance Scenarios**:

1. **Given** an agent with an assigned worktree, **When** the agent attempts to edit a file within that worktree, **Then** the edit operation succeeds.
2. **Given** an agent with an assigned worktree, **When** the agent attempts to edit a file outside the worktree, **Then** the edit operation is blocked with an appropriate error message.

---

### User Story 5 - Header-Based Worktree Resolution (Priority: P2)

As a system operator, I need the worktree context to be passed via headers and resolved against the graph repository so that file system operations can determine the correct sandbox boundaries without explicit configuration in each tool call.

**Why this priority**: This provides a clean integration mechanism that keeps the sandboxing logic decoupled from individual tool implementations.

**Independent Test**: Can be tested by sending a request with a worktree header, verifying the graph repository lookup succeeds, and confirming the sandbox is correctly configured.

**Cucumber Test Tag**: `@header-resolution`

**Acceptance Scenarios**:

1. **Given** a request with a worktree identifier in the header, **When** a file operation tool is invoked, **Then** the system resolves the worktree path from the graph repository using the header value.
2. **Given** a request with an invalid worktree identifier in the header, **When** a file operation is attempted, **Then** the operation fails with a clear error indicating the worktree could not be resolved.
3. **Given** a node in the graph repository with associated worktree information, **When** the header contains that node's identifier, **Then** the worktree path is correctly extracted and used for sandboxing.

---

### User Story 6 - Submodule Worktree Handling (Priority: P2)

As a developer working with multi-repository projects, I need agents to have access to their designated submodule worktrees so that they can operate on submodule files within their sandbox boundaries.

**Why this priority**: Multi-repository projects are common, and agents need proper access to submodule worktrees while maintaining isolation.

**Independent Test**: Can be tested by configuring an agent with submodule worktree access and verifying it can access submodule files but not files in other submodules.

**Cucumber Test Tag**: `@submodule-sandbox`

**Acceptance Scenarios**:

1. **Given** an agent with access to a main worktree and a specific submodule worktree, **When** the agent attempts to access files in the assigned submodule, **Then** the operation succeeds.
2. **Given** an agent with access to submodule "A", **When** the agent attempts to access files in submodule "B", **Then** the operation is blocked.

---

### User Story 7 - Ticket Orchestrator Worktree Creation (Priority: P1)

As a ticket orchestrator, I need to create separate worktrees for each ticket agent so that each agent can work on its ticket in isolation without conflicting with other agents working on parallel tickets.

**Why this priority**: Ticket agents perform code modifications and must have isolated workspaces to prevent merge conflicts and enable parallel execution of multiple tickets.

**Independent Test**: Can be tested by triggering a ticket orchestrator with multiple tickets and verifying each ticket agent receives its own worktree path in the request, created in the same directory as the original code.

**Cucumber Test Tag**: `@ticket-worktree-creation`

**Acceptance Scenarios**:

1. **Given** a ticket orchestrator with a parent worktree context, **When** the ticket orchestrator dispatches ticket agent requests, **Then** a separate worktree is created for each ticket agent in the same directory as the original code.
2. **Given** a ticket orchestrator creating worktrees for ticket agents, **When** the ticket agent request is constructed, **Then** the request contains the newly created worktree path for that specific ticket agent.
3. **Given** multiple ticket agents spawned from the same ticket orchestrator, **When** each agent performs file operations, **Then** each agent operates in its own isolated worktree without affecting other agents.

---

### Edge Cases

- What happens when an agent request is created before the worktree is initialized?
- How does the system handle symbolic links that point outside the worktree?
- What happens when two agents share the same worktree (is this allowed)?
- How does the system handle file operations on the worktree root directory itself?
- What happens when the worktree is deleted while an agent is operating?

## Requirements

### Functional Requirements

#### Worktree Context Propagation via Decorators

- **FR-001**: System MUST use existing request decorators to propagate worktree information to all agent actions.
- **FR-002**: For OrchestratorRequest, the decorator MUST look up the orchestrator node in the graph repository and set the worktree context from that node's worktree information.
- **FR-003**: For all other agent requests (non-orchestrator), the decorator MUST inherit the worktree context from the parent request, allowing worktree information to flow through the request hierarchy.
- **FR-004**: The worktree context MUST be available on ALL request types that flow through the agent system (all types implementing AgentRequest).

#### In-Memory Request Context Repository

- **FR-004a**: System MUST maintain an in-memory RequestContextRepository storing RequestContext records (using @With for immutable updates) by session ID.
- **FR-004b**: Decorators MUST populate the RequestContext using `ctx.withXxx()` pattern during request processing.
- **FR-004c**: Session ID MUST match the ArtifactKey.value() / contextId from each request (same as MCP_SESSION_HEADER).
- **FR-004d**: RequestContextRepository MUST be injectable into decorators, controller, AcpChatModel, and AcpTooling.
- **FR-004e**: AcpChatModel MUST use RequestContextRepository to translate sandbox context into provider-specific options.

#### Provider-Specific Sandbox Translation

- **FR-004f**: Sandbox translation from RequestContext to command options MUST be provider-specific (Claude Code, Codex, Goose, etc.).
- **FR-004g**: Each ACP provider MUST have its own strategy for translating worktree sandbox into environment variables and command line arguments.
- **FR-004h**: Sandbox configuration MUST be passed during ACP command creation via environment variables and/or command line arguments.
- **FR-004i**: System MUST support extensibility for adding new ACP providers with their own sandbox translation strategies.

#### Ticket Orchestrator Worktree Creation

- **FR-005**: Ticket orchestrator MUST create a separate worktree for each ticket agent it spawns.
- **FR-006**: Each ticket agent worktree MUST be created in the same directory as the original code (sibling to the parent worktree).
- **FR-007**: The ticket orchestrator MUST pass the newly created worktree path to the ticket agent through the TicketAgentRequest.
- **FR-008**: A prompt contributor MUST be added specifically for ticket orchestrator requests to instruct the orchestrator about worktree creation responsibilities.
- **FR-009**: The prompt contributor MUST have access to the parent worktree information from the prompt context's previous request.

#### File System Sandboxing

- **FR-010**: System MUST block file read operations that target paths outside the agent's assigned worktree.
- **FR-011**: System MUST block file write operations that target paths outside the agent's assigned worktree.
- **FR-012**: System MUST block file edit operations that target paths outside the agent's assigned worktree.
- **FR-013**: System MUST normalize file paths to prevent path traversal attacks (e.g., using "../" patterns).
- **FR-014**: System MUST return JSON-serialized error responses (not exceptions) when file operations are blocked due to sandbox violations.

#### Integration Points

- **FR-015**: System MUST use the existing MCP_SESSION_HEADER for session identification (same header used by AgentTools).
- **FR-016**: System MUST use @SetFromHeader(MCP_SESSION_HEADER) annotation on AcpTooling methods to receive session ID.
- **FR-017**: System MUST look up worktree context from RequestContextRepository using the session ID from header.
- **FR-018**: System MUST support submodule worktrees, allowing agents to access designated submodule paths within their sandbox.
- **FR-019**: System MUST integrate sandbox enforcement with the existing tooling layer (AcpTooling).

### Key Entities

- **Worktree Context**: Represents the sandbox boundaries for an agent, including the main worktree path and any associated submodule paths.
- **Agent Request**: The request object that flows through agent actions, now enriched with worktree context. ALL request types implementing AgentRequest must have this field.
- **Request Context Repository**: In-memory storage for all context items by session ID, enabling MCP tools to retrieve context populated by decorators.
- **Request Decorator**: A component that enriches agent requests with worktree information before they are processed. For orchestrator requests, looks up the node; for other requests, inherits from parent.
- **Prompt Contributor**: A component that contributes prompt content for specific request types. The ticket orchestrator prompt contributor instructs the orchestrator to create worktrees for ticket agents.
- **Graph Node**: A node in the computation graph that has associated worktree information stored in the graph repository.
- **Sandbox Validation Result**: The result of a sandbox check, returned as JSON error response (not exception) when access is denied.

## Success Criteria

### Measurable Outcomes

- **SC-001**: 100% of agent requests in worktree-enabled workflows contain worktree context information.
- **SC-002**: 100% of file operations outside the assigned worktree are blocked.
- **SC-003**: File operations within the assigned worktree complete successfully with no degradation in performance.
- **SC-004**: Path traversal attacks (using "../" patterns) are blocked in 100% of test cases.
- **SC-005**: Error messages for sandbox violations clearly identify the attempted path and the allowed boundary.
- **SC-006**: System correctly handles 100% of submodule worktree configurations tested.
- **SC-007**: Each ticket agent spawned by a ticket orchestrator receives its own unique worktree path.
- **SC-008**: Worktree context flows correctly from parent requests to child requests through the decorator chain.

## Assumptions

- Worktrees are created and managed by the existing WorktreeService and WorktreeRepository.
- The graph repository contains node information that includes worktree identifiers.
- Agents operate in a single-threaded manner within their worktree context (no concurrent access to the same worktree by the same agent).
- The SetFromHeader annotation mechanism is already functional and can be extended for worktree resolution.
- File path normalization follows standard operating system conventions for the target platform.
- Existing request decorator infrastructure is in place and can be extended to handle worktree context propagation.
- Existing prompt contributor infrastructure is in place and can be extended to add ticket orchestrator worktree prompts.
- The prompt context passed to prompt contributors contains the previous request, which includes the parent worktree information.

## Notes on Gherkin Feature Files For Test Graph

The functional requirements, success criteria, edge cases, and measurable outcomes will then be encoded in feature files
using Gherkin and Cucumber in test_graph/src/test/resources/features/worktree-sandbox.

The translation to Gherkin feature files should adhere to the instructions test_graph/instructions.md and also
information about the test_graph framework can be found under test_graph/instructions-features.md. When completing
the integration test feature files in the test_graph, check to see if feature files already exist, and if so use the existing
step definitions if at all possible. 

When writing the feature files and step definitions for those files, make sure to create as few steps as possible, and make
the setup as generic as possible. It is as simple as provide information to be added to the context, then assert that
messages were received as expected. There should not be a lot of different step definitions. Additionally, Spring
is used so we can decouple initialization logic. This means by adding a bean of a type as a component accepting a
context and depending on other beans in that computation graph, we can then add an assertion over that context without
changing any of the other code. This is what it means to run the tests together but have them independent. They are
sufficiently decoupled to be useful over time and minimize regressions.
