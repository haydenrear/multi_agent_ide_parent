# Data Model: Collector Orchestrator Routing

## Entities

### CollectorDecision

- **Fields**: decisionType (ROUTE_BACK | ADVANCE_PHASE | STOP), rationale, requestedPhase
- **Validation**: decisionType required; requestedPhase required when decisionType is ADVANCE_PHASE
- **Relationships**: referenced by collector result records

### CollectedNodeStatus

- **Fields**: nodeId, title, nodeType, status
- **Validation**: nodeId and status required
- **Relationships**: stored on collector nodes as a snapshot

### CollectorNodeState

- **Fields**: collectedNodes (list of CollectedNodeStatus), consolidatedOutput, collectorDecision
- **Validation**: collectedNodes defaults to empty list; consolidatedOutput optional
- **Relationships**: owned by each collector node

### Collector Nodes

- **PlanningCollectorNode**: includes planContent, generatedTicketIds, collectedNodes, collectorDecision
- **DiscoveryCollectorNode**: includes summaryContent, collectedNodes, collectorDecision
- **TicketCollectorNode**: includes ticketSummary, collectedNodes, collectorDecision
- **OrchestratorCollectorNode**: includes orchestratorOutput, collectedNodes, collectorDecision

### Orchestrator Nodes

- **OrchestratorNode**: root workflow orchestration and phase coordination
- **DiscoveryOrchestratorNode**: partitions discovery work
- **PlanningOrchestratorNode**: partitions planning work
- **TicketOrchestratorNode**: coordinates ticket execution ordering

## State Transitions

- Collector nodes transition READY -> RUNNING -> COMPLETED/FAILED
- Collector decision applied on COMPLETED to determine routing
- Review gate may interrupt after COMPLETED before routing is applied
