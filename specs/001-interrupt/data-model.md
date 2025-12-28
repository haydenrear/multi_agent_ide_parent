# Data Model: Interrupt Handling & Continuations

## Entities

### InterruptRequest
- **Fields**: id, type, originNodeId, originNodeType, status, reason, requestedAt, metadata, memoryId
- **Relationships**: belongs to WorkflowStage; optionally linked to InterruptResolution
- **Validation**: type must be one of review, pause, stop, prune, branch

### InterruptResolution
- **Fields**: id, interruptRequestId, resolutionType, resolutionNotes, resolvedAt, resolvedBy
- **Relationships**: references InterruptRequest
- **Validation**: resolutionType must be compatible with interrupt type

### WorkflowStage
- **Fields**: nodeId, nodeType, status, parentNodeId, createdAt, lastUpdatedAt
- **Relationships**: may have multiple InterruptRequest entries

### InterruptRoute
- **Fields**: originNodeId, interruptNodeId, resumeNodeId, lastRoutedAt, interruptedStatus
- **Relationships**: connects InterruptRequest to WorkflowStage and the interrupt node created after status emission

## State Transitions

- **InterruptRequest.status**: REQUESTED → IN_PROGRESS → RESULT_STORED → STATUS_EMITTED → RESOLVED → APPLIED
- **WorkflowStage.status** (interrupted): RUNNING → WAITING_REVIEW/WAITING_INPUT/TERMINATED → RUNNING/COMPLETED/PRUNED
- **InterruptRoute**: created on request; updated after status emission to reference the interrupt node; closed on resolution
