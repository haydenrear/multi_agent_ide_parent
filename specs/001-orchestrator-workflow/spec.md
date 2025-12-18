# Feature Specification: Complete Orchestrator Workflow Implementation

**Feature Branch**: `001-orchestrator-workflow`  
**Created**: 2025-12-17  
**Status**: Draft  
**Input**: User description: "Implement full workflow for Orchestrator, from Orchestrator, all the way through to ReviewNode, MergeNode."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Agent Review Approval Workflow (Priority: P1)

A developer submits implementation work for review. The system automatically evaluates the work against acceptance criteria and either approves it for merging or requests revisions with specific feedback. Once approved, the work progresses to merge without requiring manual quality checks.

**Why this priority**: This is the core quality gate that ensures code meets requirements before integration. Without it, no work can be safely merged into the codebase.

**Independent Test**: Submit a completed ticket implementation. The system evaluates it, provides approval/rejection decision with feedback, and either proceeds to merge or returns for revision. This delivers immediate value by automating the review process.

**Cucumber Test Tag**: `@agent-review-workflow`

**Acceptance Scenarios**:

1. **Given** an agent has completed implementation work for a ticket, **When** the work is submitted for review, **Then** a review evaluation is performed and a decision (approved/needs revision) is recorded with detailed feedback
2. **Given** review evaluation determines work meets all acceptance criteria, **When** the review completes, **Then** the work is marked as approved and automatically proceeds to merge preparation
3. **Given** review evaluation identifies issues or missing requirements, **When** the review completes, **Then** the work is marked as needs revision with specific feedback about what must be corrected
4. **Given** work has been marked as needs revision, **When** the developer addresses the feedback and resubmits, **Then** the work enters a new review cycle
5. **Given** work requires human judgment on subjective criteria, **When** agent review completes, **Then** human feedback is requested with context about the decision point

---

### User Story 2 - Automated Merge Orchestration (Priority: P1)

After work is approved, the system automatically merges the completed changes into the parent codebase. The system detects and reports merge conflicts, updates submodule pointers, and creates a summary of what was integrated. Teams see exactly what was merged and any issues that occurred.

**Why this priority**: Merging is the final step that delivers value by integrating work into the main codebase. Without automated merge, approved work cannot be integrated.

**Independent Test**: Approve a ticket implementation. The system performs the merge operation, detects any conflicts, updates submodules, and creates a merge summary showing what was integrated. This delivers value by automating the integration process.

**Cucumber Test Tag**: `@automated-merge-workflow`

**Acceptance Scenarios**:

1. **Given** work has been approved by review, **When** merge is initiated, **Then** the system merges the implementation branch into the parent worktree and records the merge result
2. **Given** a merge operation completes successfully without conflicts, **When** merge summary is generated, **Then** the summary includes count of completed tasks, files changed, and commit references
3. **Given** a merge operation encounters conflicts, **When** merge completes, **Then** conflicts are detected, documented with file locations, and marked for resolution
4. **Given** the codebase contains submodules, **When** merge operation includes submodule changes, **Then** submodule pointers are updated to reference the correct commit hashes
5. **Given** multiple tickets are queued for implementation, **When** one ticket merge completes, **Then** the system automatically proceeds to the next ticket in the queue

---

### User Story 3 - Workflow Orchestration and State Management (Priority: P2)

The orchestrator coordinates the entire workflow from ticket assignment through review and merge. Users see real-time status of each phase, understand what's currently executing, and know when the entire goal is complete. The system maintains consistent state even when failures occur.

**Why this priority**: Orchestration ensures all pieces work together correctly and provides visibility. While important, the individual review and merge operations (P1) must work first.

**Independent Test**: Initiate a multi-ticket goal. The system creates the orchestration graph, executes each phase in sequence, emits status events at each transition, and determines when the goal is complete. This delivers value by coordinating the entire process.

**Cucumber Test Tag**: `@workflow-orchestration`

**Acceptance Scenarios**:

1. **Given** a goal with multiple tickets is initiated, **When** orchestration begins, **Then** the system creates an orchestrator node, ticket nodes, and establishes the execution graph
2. **Given** orchestration is active, **When** node status changes occur, **Then** status change events are emitted with node ID, old status, new status, and timestamp
3. **Given** a node transitions to running state, **When** the node begins execution, **Then** the system records the start time and updates node status
4. **Given** all leaf nodes in the graph are completed or pruned, **When** goal completion check runs, **Then** the system determines the goal is complete and emits completion event
5. **Given** a node execution fails, **When** the failure is detected, **Then** the system records the error, marks the node as failed, and optionally prunes dependent nodes

---

### User Story 4 - Worktree and Branch Management (Priority: P2)

Each ticket gets its own isolated working environment (worktree) to prevent interference between parallel work. Developers see which worktrees exist, which are active, and which have been merged. The system automatically creates, tracks, and cleans up worktrees throughout the lifecycle.

**Why this priority**: Worktrees enable parallel work but are supporting infrastructure. Review and merge (P1) are more critical to the core workflow.

**Independent Test**: Create a ticket node. The system creates dedicated worktrees for each submodule, tracks their status, and provides queries to find worktrees by node. This delivers value by enabling isolated parallel development.

**Cucumber Test Tag**: `@worktree-management`

**Acceptance Scenarios**:

1. **Given** a new ticket node is created, **When** worktree creation is initiated, **Then** a dedicated worktree is created for the main repository and each submodule
2. **Given** worktrees exist for multiple nodes, **When** querying worktrees for a specific node, **Then** all worktrees associated with that node are returned
3. **Given** a merge operation completes successfully, **When** worktree status is updated, **Then** the child worktree is marked as merged and parent worktree remains active
4. **Given** a ticket is canceled or fails, **When** cleanup is initiated, **Then** the associated worktrees are marked as discarded
5. **Given** the goal is complete, **When** querying all worktrees, **Then** all worktrees are in either merged or discarded state

---

### User Story 5 - Spec Context Management and Retrieval (Priority: P3)

Specs provide context for review and merge operations. Reviewers see only relevant sections (acceptance criteria, requirements) to avoid context overload. After merge, specs are updated with completion status. Teams have traceable records of what was planned, implemented, and integrated.

**Why this priority**: Specs enhance the workflow but are supplementary. The core review/merge/orchestration functionality (P1-P2) is more critical.

**Independent Test**: Create a spec with multiple sections. The review node retrieves only acceptance criteria section for evaluation. After merge, spec is updated with merge summary. This delivers value by providing context without overwhelming the review process.

**Cucumber Test Tag**: `@spec-context-management`

**Acceptance Scenarios**:

1. **Given** a spec file exists for a goal, **When** a review node is created, **Then** the review node is associated with the spec file ID
2. **Given** a review operation begins, **When** spec context is retrieved, **Then** only the acceptance criteria and requirements sections are loaded to minimize context
3. **Given** a merge operation completes, **When** spec update is triggered, **Then** the spec status section is updated with merge results and completion timestamp
4. **Given** multiple tickets exist in a goal, **When** querying all specs, **Then** specs for each ticket and the parent goal are returned
5. **Given** a spec is updated during workflow, **When** subsequent operations need context, **Then** the latest spec version is retrieved

---

### Edge Cases

- What happens when a review agent fails to respond or times out?
- How does the system handle merge conflicts that cannot be auto-resolved?
- What happens when a worktree creation fails due to disk space or git errors?
- How does the system handle concurrent updates to the same parent worktree from multiple child merges?
- What happens when human feedback is requested but no human responds within the timeout period?
- How does the system recover when a node is in RUNNING state but the agent process crashes?
- What happens when submodule pointers reference commits that don't exist in the submodule repository?

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST execute review evaluation for completed work nodes and record approval or rejection decision with detailed feedback
- **FR-002**: System MUST automatically initiate merge operations when work is approved by review
- **FR-003**: System MUST detect merge conflicts, record conflict file locations, and mark merge as requiring resolution
- **FR-004**: System MUST update submodule pointers to correct commit hashes when merge includes submodule changes
- **FR-005**: System MUST emit status change events when nodes transition between states (READY, RUNNING, COMPLETED, FAILED, etc.)
- **FR-006**: System MUST determine goal completion when all leaf nodes are completed or pruned and all worktrees are merged or discarded
- **FR-007**: System MUST create dedicated worktrees for each ticket node including main repository and all submodules
- **FR-008**: System MUST track worktree status throughout lifecycle (ACTIVE, MERGED, DISCARDED)
- **FR-009**: System MUST retrieve spec context for review operations with only relevant sections (acceptance criteria, requirements)
- **FR-010**: System MUST update spec files with merge results and completion status after successful merge
- **FR-011**: System MUST support review revision cycles where rejected work can be corrected and resubmitted for new review
- **FR-012**: System MUST request human feedback when review encounters subjective decisions that require human judgment
- **FR-013**: System MUST maintain consistent node state even when agent failures occur
- **FR-014**: System MUST provide query operations to retrieve nodes, worktrees, and specs by ID or parent relationships
- **FR-015**: System MUST record merge statistics including completed task count, failed task count, and changed file list

### Key Entities

- **OrchestratorNode**: Represents the root coordinator that manages overall workflow execution, tracks child nodes, and determines goal completion
- **ReviewNode**: Represents an agent-based review operation with fields for reviewed content, approval decision, feedback, and reviewer agent type
- **MergeNode**: Represents a merge operation with fields for summary content, completed/failed task counts, and merge result details
- **EditorNode**: Represents implementation work for a ticket with associated worktrees and code changes
- **Worktree**: Represents an isolated git working environment with fields for node association, branch name, repository path, and status
- **Spec**: Represents a specification document with sections for requirements, acceptance criteria, plan, and status
- **MergeResult**: Represents the outcome of a merge operation with fields for merge commit hash, conflicts list, and submodule updates
- **NodeStatus**: Enumeration representing lifecycle states (PENDING, READY, RUNNING, WAITING_REVIEW, WAITING_INPUT, COMPLETED, FAILED, CANCELED, PRUNED)
- **StatusChangeEvent**: Represents a node status transition with fields for node ID, old status, new status, and timestamp

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Review evaluation completes within 60 seconds for typical ticket implementations (under 500 lines of code)
- **SC-002**: 95% of merge operations without conflicts complete within 30 seconds
- **SC-003**: Orchestrator correctly determines goal completion with 100% accuracy when all leaf nodes reach terminal states
- **SC-004**: System maintains consistent state with zero data corruption across node failures and restarts
- **SC-005**: Worktree creation succeeds for 99% of ticket nodes (failures only due to disk space or git repository corruption)
- **SC-006**: Review revision cycles (reject → fix → resubmit → approve) complete within 5 minutes total for typical tickets
- **SC-007**: System handles at least 10 concurrent ticket executions without performance degradation
- **SC-008**: Status change events are emitted within 1 second of actual status transitions
- **SC-009**: Spec context retrieval loads only requested sections, reducing context size by at least 70% compared to full spec
- **SC-010**: Merge conflict detection identifies all conflicting files with 100% accuracy (zero false negatives)

## Assumptions

- The underlying git infrastructure is functional and accessible
- Agent implementations (review agent, merge agent) will be provided via dependency injection
- The event bus infrastructure is operational and can deliver events reliably
- Worktree paths are properly configured and have sufficient disk space
- Spec files are valid Markdown documents with expected section structure
- The graph repository maintains node relationships correctly
- Submodule repositories are accessible and properly initialized
- Review criteria are defined in spec acceptance scenarios
- Human feedback mechanisms will be implemented separately if auto-review cannot make a decision
- Network latency between services is reasonable (< 100ms) for event delivery

## Implementation Notes (current)

- Ticket orchestration now branches a dedicated worktree (and submodule worktrees when present), queues tickets from planning output, and advances via per-ticket review/merge before a final review and merge back to the parent worktree.
- Review nodes persist approval/feedback decisions, request human input when needed, and hand off approved tickets to merge nodes that record conflict status and update worktree metadata.
- Merge nodes drive worktree merges via `WorktreeService`, mark conflicts as `WAITING_INPUT`, and mark child worktrees as `MERGED` when clean.
- Context propagation pulls discovery/planning outputs from the parent chain; spec acceptance/requirements sections are injected into review requests to keep context minimal.
- Revision cycles spawn new ticket nodes with embedded feedback so rejected work re-enters the queue automatically.
