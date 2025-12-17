# Multi-Agent IDE Constitution

<!-- Sync Impact Report
Version: 0.1.0 → 1.0.0 (MINOR - initial constitution established)
New Principles: All 7 principles newly defined
Key Features: Event-driven state machine, collector nodes, human review gates, runtime graph manipulation, interactive agent context
Governance: Initial amendment and versioning procedures established
Updated Templates: plan-template.md, spec-template.md, tasks-template.md (pending validation)
-->

## Core Principles

### I. Computation Graph with State Machine Architecture
Multi-agent IDE workflows are driven by explicit computation graphs (DAGs) where node creation and transitions follow a state machine pattern. Events trigger node state changes and agent creation via AgentEventListener. Every agent invocation generates output events; sub-agents produce child events. The graph state machine ensures consistent transitions and prevents invalid state combinations. Users can inspect, prune, and manipulate the graph in real-time while workflow executes.

**Rationale**: Event-driven state machine provides deterministic workflow execution, enables auditability, supports long-running async workflows, visualizes in real-time via WebSocket, and allows user intervention without halting the system.

### II. Three-Phase Workflow Pattern
Every goal decomposition follows a mandatory three-phase workflow: **Discovery** (understand repository context) → **Planning** (create structured tickets and dependencies) → **Implementation** (ticket-based execution with testing and merge). Each phase has dedicated orchestrator nodes, work agent nodes, and collector nodes. Deviations from this pattern require constitution amendment.

**Rationale**: Structured phases ensure comprehensive codebase understanding, dependency tracking, and reproducible implementation. This pattern scales from single-ticket fixes to multi-repository monorepo changes.

### III. Orchestrator, Work, and Collector Node Pattern
Workflows use three node types per phase:
- **Orchestrator nodes**: Decide how to distribute work (multiplexing strategy), create child work nodes
- **Work nodes**: Execute actual tasks (discovery, planning, implementation) in isolation
- **Collector nodes**: Execute once ALL child work nodes complete; aggregate and consolidate results into unified artifacts

No orchestrator may invoke work directly; orchestrators only decide strategy and create child nodes. Work node outputs flow to collector nodes via event propagation.

**Rationale**: This pattern enables true parallelism, prevents premature aggregation, isolates failures, and ensures all work is complete before consolidation.

### IV. Event-Driven Node Lifecycle & Artifact Propagation
Node state transitions (READY → RUNNING → COMPLETED/FAILED/WAITING_INPUT/HUMAN_REVIEW_REQUESTED) emit graph events. AgentEventListener consumes events and creates agents. Agent outputs are captured by lifecycle handlers and written to artifact files. Collector nodes read consolidated artifacts from all children and write merged artifacts upward. Context propagates through explicit artifact files (discovery.md, plan.md, spec.md, tickets.md), never as implicit state in method parameters. Users can send messages to running agents, which are incorporated as additional context via AGENT_MESSAGE_RECEIVED events.

**Rationale**: Event-driven lifecycle ensures deterministic execution order, supports async workflows, provides audit trail, enables UI real-time visualization via WebSocket, and allows interactive agent guidance during execution.

### V. Artifact-Driven Context Propagation
All context between phases/agents is encoded in explicit markdown artifact files stored in the spec repository:
- `discovery.md` - consolidated codebase understanding
- `plan.md` - high-level goals and strategy
- `spec.md` - structured specification with requirements
- `tickets.md` - work items with dependencies and acceptance criteria
- Ticket-specific worktree files - implementation details per ticket

Agents read these artifacts and produce updated versions. Context is NEVER passed as implicit state; only artifact paths and file contents are exchanged between phases. User messages sent to agents are appended to agent context and included in next agent invocation.

**Rationale**: Artifact-driven design enables agent isolation, supports human review/intervention at phase boundaries, allows workflow resumption after failures, and provides audit trails.

### VI. Worktree Isolation & Feature Branching
Each ticket agent executes in an isolated Git worktree with a feature branch to prevent conflicts and enable parallel execution. Worktree creation, code generation, testing, and merging are coordinated through WorktreeService and WorktreeRepository. Code changes MUST NOT modify the main branch directly; all changes flow through feature branch → merge node → main branch. Merge conflicts are resolved by merger nodes with explicit merge strategy documentation. Ticket execution can be cancelled (NodePrunedEvent), which rolls back the worktree and feature branch.

**Rationale**: Worktree isolation enables true parallelism across tickets, prevents race conditions, provides rollback capability per ticket, aligns with standard Git workflows, and supports cancellation without affecting other tickets.

### VII. Tool Integration & Agent Capabilities
Agents invoke standardized tools defined in LangChain4jAgentTools (file operations, code analysis, repository queries, build/test execution, Git operations, worktree management). Tools are the only mechanism for agents to interact with external systems; tool results are passed back to agents for decision-making. New tools MUST be explicitly added to the tool set and documented. Tools are stateless; state is managed through artifact files and graph nodes.

**Rationale**: Standardized tool integration prevents agent hallucination, enables auditing of external operations, supports tool replacement/versioning, and centralizes system integration logic.

### VIII. Human-in-the-Loop Review Gates
ReviewAgent evaluates ticket implementation (code quality, tests, requirements). ReviewAgent can emit three outcomes:
- **APPROVED**: Proceed to merge node
- **NEEDS_REVISION**: Create revision node (TicketAgentNode retry), return to implementation
- **HUMAN_REVIEW_REQUESTED**: Transition node to WAITING_INPUT state, notify user via WebSocket, pause agent execution

When ReviewAgent requests human review, a user must explicitly approve or provide revision feedback via the UI. User feedback is sent as AGENT_MESSAGE_RECEIVED event, captured by lifecycle handler, and becomes part of review context for potential revision. Human approval transitions the node to COMPLETED and proceeds to merge.

**Rationale**: Human review gates ensure critical implementation decisions are validated by the user, provide corrective feedback loop, and prevent low-quality code merges while respecting user autonomy.

## Runtime User Capabilities

### Node Pruning & Cancellation
Users can prune nodes from the computation graph at any time while the workflow executes:
- **Prune work node**: Cancels that agent, rolls back any side effects (worktree rollback, partial artifacts discarded), emits NodePrunedEvent
- **Prune subtree**: Recursively prunes node and all descendants, cascading rollbacks
- **Prune collector node**: Cancels collector execution, discards aggregation
- Pruned nodes do NOT emit outputs to parent nodes; parent collector node recalculates aggregation without pruned children

**Constraints**: 
- Cannot prune nodes in COMPLETED or FAILED terminal states (already finalized)
- Pruning a parent node automatically prunes all children (cascading)
- Pruning does not affect sibling branches of the graph

**Rationale**: Users need the ability to cancel low-value work, redirect resources to higher-priority tickets, or discard erroneous branches without halting the entire workflow.

### Interactive Agent Context & User Messages
While an agent is RUNNING, users can send text messages via the UI. Messages are captured as AGENT_MESSAGE_RECEIVED events and queued for the agent. Lifecycle handlers append user messages to the agent's current context. On next agent invocation or stream delta, user messages are incorporated as additional instructions/feedback. Multiple user messages are accumulated and sent as a single context update.

**Message Types**:
- **Inline guidance**: "Add more error handling for network failures" → incorporated into agent reasoning
- **Constraint updates**: "Use async/await instead of callbacks" → agent re-evaluates implementation approach
- **Approval feedback**: "Looks good, proceed" → ReviewAgent uses as part of approval decision
- **Revision requests**: "Refactor this function to use dependency injection" → creates revision node with updated context

**Constraints**:
- Messages sent to READY or WAITING_INPUT nodes are buffered until agent starts execution
- Messages to COMPLETED nodes are discarded (no retry)
- Messages to FAILED nodes create revision nodes only if explicitly approved by lifecycle handler

**Rationale**: Interactive context allows users to guide agents mid-execution without halting workflow, provide real-time corrective feedback, and adapt strategy based on emerging insights from code inspection.

## Architecture & Technology Stack

### Required Technologies
- **Language**: Java 21
- **Framework**: Spring Boot 3.x
- **Agent Framework**: LangChain4j-Agentic with @Agent annotation support
- **Computation Graph**: Custom ComputationGraphOrchestrator with event-driven state machine
- **Version Control**: Git (worktree management, branching, merging)
- **Spec/Plan Management**: Markdown-based artifacts with Git version control
- **Frontend**: React app (multi_agent_ide/fe, built via Node Gradle plugin, served from Spring Boot static resources)
- **Real-time Communication**: WebSocket for UI event propagation, parallel task visualization, user message ingestion, node pruning commands
- **Serialization**: JSON for event data, structured artifact formats

### Node Status States
- **READY**: Node created, awaiting execution
- **RUNNING**: Agent executing
- **COMPLETED**: Agent finished successfully, outputs available
- **FAILED**: Agent execution failed, error captured
- **WAITING_INPUT**: Human review requested, awaiting user decision/message
- **HUMAN_REVIEW_REQUESTED**: Transition state during human review request emission
- **PRUNED**: User pruned node; side effects rolled back (transitioned from RUNNING or READY)

### Core Infrastructure Services
- **ComputationGraphOrchestrator**: Manages DAG state, node lifecycle, state transitions, supports node pruning and ancestor/descendant traversal
- **GraphRepository**: Persists graph state across workflow executions, supports rollback on node pruning
- **AgentEventListener**: Consumes graph events and dispatches agent creation (state machine trigger)
- **AgentRunner**: Executes agents with proper parent/child context, handles AGENT_MESSAGE_RECEIVED events
- **AgentLifecycleHandler**: Enforces pre/post-agent invocation contracts, artifact management, user message queueing
- **WorktreeService/WorktreeRepository**: Manages Git worktrees and feature branch lifecycle, supports rollback on pruning
- **SpecService/SpecRepository**: Manages spec.md, plan.md, tickets.md versioning
- **LangChain4jConfiguration**: Configures all agent beans (@Agent interfaces) and tool bindings
- **WebSocketController**: Publishes node status changes, accepts user messages and prune commands, broadcasts to connected UI clients

## Development Workflow

### Node Lifecycle & Event Flow
1. **Root Initialization**: AgentLifecycleHandler.initializeOrchestrator creates root OrchestratorNode (READY status)
2. **Event Dispatch**: NodeAddedEvent for root node → AgentEventListener consumes event → creates OrchestratorAgent instance
3. **Orchestrator Execution**: Orchestrator decides multiplexing strategy, emits child node creation instructions
4. **Child Node Creation**: AgentLifecycleHandler creates child work nodes (READY status), emits NodeAddedEvent per child
5. **Work Agent Execution**: Each NodeAddedEvent → AgentEventListener → AgentRunner creates work agent instance, transitions node to RUNNING
6. **User Interaction**: User can send messages (appended to agent context) or prune node (NodePrunedEvent emitted)
7. **Work Agent Completion**: Work nodes emit NodeStatusChangedEvent → AgentLifecycleHandler captures output, updates artifacts, transitions to COMPLETED
8. **Collector Execution**: Once ALL non-pruned child work nodes reach COMPLETED status, collector node transitions to READY
9. **Collector Agent Execution**: CollectorNode (READY) → NodeAddedEvent → AgentEventListener → AgentRunner creates collector agent
10. **Collector Output**: Collector consolidates non-pruned child artifacts, emits upward to parent via NodeStatusChangedEvent
11. **Phase Transition**: Parent orchestrator of next phase receives collected context, repeats cycle

### Human Review Gate Workflow
1. **Code Review Execution**: ReviewAgentNode (READY) → executes ReviewAgent with ticket implementation
2. **Three Outcomes**:
   - **APPROVED**: Node transitions to COMPLETED, emits NodeStatusChangedEvent → proceeds to MergerNode
   - **NEEDS_REVISION**: Node creates new TicketAgentNode with revision context, emits NodeBranchedEvent → new agent executes
   - **HUMAN_REVIEW_REQUESTED**: Node transitions to WAITING_INPUT, emits NodeReviewRequestedEvent → UI notifies user
3. **User Action (if WAITING_INPUT)**:
   - User provides feedback message via UI → AGENT_MESSAGE_RECEIVED event queued
   - AgentLifecycleHandler appends message to review context
   - User approves → Node transitions to COMPLETED, emits NodeStatusChangedEvent → proceeds to MergerNode
   - User requests revision → Node creates revision TicketAgentNode with feedback, emits NodeBranchedEvent
4. **Timeout (optional)**: If WAITING_INPUT exceeds timeout threshold, escalate to alert or auto-proceed per configuration

### Three-Phase Execution Detail

**Phase 1: Discovery**
- DiscoveryOrchestratorNode decides how to partition codebase analysis across discovery agents
- Creates N DiscoveryAgentNode(s) with subdomainFocus parameters
- Each DiscoveryAgentNode executes independently, writes discovery.md fragment
- User can prune any DiscoveryAgentNode; pruned discovery is not included in merge
- DiscoveryCollectorNode waits for all non-pruned DiscoveryAgentNode(s) to complete
- DiscoveryCollectorNode merges all non-pruned fragments into unified discovery.md, emits upward

**Phase 2: Planning**
- PlanningOrchestratorNode receives unified discovery.md
- Decides multiplexing strategy (one planner or multiple planners per subdomain/component)
- Creates N PlanningAgentNode(s) with planning context
- Each PlanningAgentNode executes independently, writes plan.md fragment
- User can prune any PlanningAgentNode; pruned planning is not included in merge
- User can send messages to adjust planning strategy mid-execution
- PlanningCollectorNode merges all non-pruned fragments into unified plan.md and generates tickets.md
- SpecService updates spec.md with requirements from plan

**Phase 3: Implementation**
- TicketOrchestratorNode receives tickets.md with dependency graph
- Creates N TicketAgentNode(s) for independent tickets (respects dependencies, skips pruned tickets)
- Each TicketAgentNode executes in isolated worktree with feature branch:
  - Reads ticket details and worktree context
  - Generates code, creates/modifies files in worktree
  - Runs tests in worktree (JUnit 5)
  - Commits to feature branch
  - User can send messages ("add logging", "use different library") → incorporated in next agent action
  - User can prune ticket → worktree rolled back, feature branch deleted, no merge attempted
- ReviewAgentNode evaluates ticket implementation (code quality, tests, requirements)
  - If APPROVED: proceeds to MergerNode
  - If NEEDS_REVISION: creates revision TicketAgentNode with revision context
  - If HUMAN_REVIEW_REQUESTED: transitions to WAITING_INPUT, user reviews and provides feedback via message
- MergerNode(s) handle feature branch → main branch merge, resolve conflicts
- TicketCollectorNode waits for all tickets merged (excluding pruned), marks goal completed

### Code Generation Standards
- Generated code MUST follow Java 21 conventions (records, sealed classes, var inference)
- All generated code MUST have corresponding unit tests (JUnit 5, >= 70% coverage)
- Generated code MUST compile without warnings in the isolated worktree
- All generated code MUST pass CheckStyle and similar static analysis
- Code generation output MUST be diff-reviewed by ReviewAgent before merge
- User messages during generation are incorporated as additional requirements/constraints

### Testing Requirements
- TicketAgent MUST run tests in isolated worktree before committing
- Test execution failures MUST create revision nodes in graph; MUST NOT proceed to merge
- ReviewAgent verifies test results and coverage as part of acceptance criteria
- Integration tests required for inter-service communication, shared schema changes
- All tests MUST be committed to feature branch alongside implementation

## Governance

### Amendment Procedure
Constitution amendments follow this process:
1. Proposed amendment specifies affected principle(s), old text, new text, and detailed rationale
2. Amendment MUST address impact on:
   - Event-driven state machine logic (if phase/node types change)
   - Artifact file formats or propagation (if context mechanism changes)
   - Agent tool set (if new capabilities are added)
   - User runtime capabilities (if graph manipulation or interactive context changes)
3. Version number is incremented per semantic versioning:
   - **MAJOR**: Backward incompatible principle removal or fundamental redefinition (e.g., abandoning three-phase workflow, removing state machine, removing human review gates)
   - **MINOR**: New principle added or existing principle materially expanded with new requirements (e.g., adding new node type, new artifact file, new user capability)
   - **PATCH**: Clarifications, typo fixes, technology stack updates (e.g., Java version bump), non-semantic refinements
4. All dependent templates updated (plan-template.md, spec-template.md, tasks-template.md, agent command files)
5. Ratification date updated if first version, Last Amended date always updated
6. Sync Impact Report prepended as HTML comment

### Compliance Verification
- **State Machine Enforcement**: ComputationGraphOrchestrator validates node transitions match expected phase patterns; invalid transitions raise exceptions
- **Event Dispatch Auditing**: AgentEventListener is instrumented with comprehensive logging of all node creation, agent dispatch, event handling, and user interactions
- **Lifecycle Enforcement**: AgentLifecycleHandler enforces pre-conditions (artifact existence, parent node state) and post-conditions (artifact updates, graph state updates)
- **Artifact Validation**: SpecService validates all spec.md, plan.md, tickets.md updates conform to expected formats
- **Code Review Gate**: ReviewAgent verifies generated code compliance with constitution standards before merge approval; human review is a strict gate
- **Pruning Audit Trail**: All node pruning operations logged with user ID, timestamp, reason (if provided), and rollback verification
- **User Message Audit**: All user messages to agents logged, timestamped, and included in agent context audit trail
- **Git Audit Trail**: All merges include constitution compliance notes in commit messages

### Tooling & Automation
- **SpecKit Commands**: /speckit.specify, /speckit.tasks, /speckit.plan generate artifacts aligned with this constitution
- **Graph Query Tools**: Agents have access to graph traversal tools to understand workflow state, dependencies, and phase context
- **Artifact Versioning**: Prior artifact versions retained in Git history; rollback supported via Git operations
- **WebSocket Events**: All node transitions, user messages, pruning commands emit WebSocket events for real-time UI visualization
- **User Interaction API**: WebSocket endpoints for:
  - Send message to agent: `POST /ws/agent/{nodeId}/message`
  - Prune node: `POST /ws/graph/prune/{nodeId}`
  - Approve human review: `POST /ws/review/{nodeId}/approve`
  - Request revision: `POST /ws/review/{nodeId}/revise`

---

**Version**: 1.0.0 | **Ratified**: 2025-12-17 | **Last Amended**: 2025-12-17
