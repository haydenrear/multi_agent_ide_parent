# Communication Events Contract

## Event types

All events extend `Events.GraphEvent` and are published via the existing `EventBus`.

### AgentCallEvent

Covers both agent-to-agent (`call_agent`) and controller↔agent (`call_controller`, `/api/agent-conversations/respond`) communication.

```java
record AgentCallEvent(
    String eventId,
    Instant timestamp,
    String nodeId,           // caller's node
    AgentCallEventType eventType,
    String callerSessionId,
    String callerAgentType,  // null for controller-initiated calls
    String targetSessionId,
    String targetAgentType,
    List<CallChainEntry> callChain,
    List<AgentAvailabilityEntry> availableAgents,
    String message,          // for INITIATED
    String response,         // for RETURNED
    String errorDetail,      // for ERROR
    String checklistAction   // checklist ACTION identifier (e.g. "VERIFY_REQUIREMENTS_MAPPING") when part of a structured checklist review, null otherwise
) implements Events.GraphEvent {}
```

### AgentCallEventType

```java
enum AgentCallEventType {
    INITIATED,     // caller sent message to target
    INTERMEDIARY,  // target forwarded to another agent
    RETURNED,      // target responded to caller
    ERROR          // call failed
}
```

## Event emission points

| Trigger | Event Type | Content |
|---------|-----------|---------|
| `call_agent` invoked | INITIATED | message, full chain, available agents |
| Target forwards to another agent | INTERMEDIARY | updated chain |
| Target responds | RETURNED | response, full chain |
| `call_controller` invoked (agent → controller) | INITIATED | justification message, callerAgentType set, callerSessionId = agent key |
| Controller responds via `/api/agent-conversations/respond` | RETURNED | response, checklistAction (if present) |
| Controller escalates to user during conversation | INITIATED | escalation message, callerAgentType = null |
| Topology violation | ERROR | error detail: "topology violation" |
| Target busy | ERROR | error detail: "agent busy" |
| Target unavailable | ERROR | error detail: "agent unavailable" |
| Loop detected | ERROR | error detail: "loop detected", full chain |
| Max depth exceeded | ERROR | error detail: "max depth exceeded" |

## BlackboardHistory integration

`BlackboardHistory` already subscribes to all `Events.GraphEvent` subtypes. `AgentCallEvent` will be automatically captured in session history without additional subscription code.
