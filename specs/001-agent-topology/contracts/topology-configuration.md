# Topology Configuration Contract

## application.yml schema

```yaml
multi-agent-ide:
  topology:
    max-call-chain-depth: 5
    message-budget: 3
    allowed-communications:
      # Workers can call their orchestrator or collector
      DISCOVERY_AGENT:
        - DISCOVERY_ORCHESTRATOR
        - DISCOVERY_COLLECTOR
      PLANNING_AGENT:
        - PLANNING_ORCHESTRATOR
        - PLANNING_COLLECTOR
      TICKET_AGENT:
        - TICKET_ORCHESTRATOR
        - TICKET_COLLECTOR
      # Orchestrators/collectors can call peers and workers
      ORCHESTRATOR:
        - DISCOVERY_ORCHESTRATOR
        - PLANNING_ORCHESTRATOR
        - TICKET_ORCHESTRATOR
        - ORCHESTRATOR_COLLECTOR
      DISCOVERY_ORCHESTRATOR:
        - ORCHESTRATOR
        - DISCOVERY_COLLECTOR
        - DISCOVERY_AGENT
      PLANNING_ORCHESTRATOR:
        - ORCHESTRATOR
        - PLANNING_COLLECTOR
        - PLANNING_AGENT
      TICKET_ORCHESTRATOR:
        - ORCHESTRATOR
        - TICKET_COLLECTOR
        - TICKET_AGENT
      # Collectors can call their orchestrator
      DISCOVERY_COLLECTOR:
        - DISCOVERY_ORCHESTRATOR
      PLANNING_COLLECTOR:
        - PLANNING_ORCHESTRATOR
      TICKET_COLLECTOR:
        - TICKET_ORCHESTRATOR
      ORCHESTRATOR_COLLECTOR:
        - ORCHESTRATOR

      # --- Worktree service agents ---
      # Commit agents can call back to the agent that created the commits
      # (the source agent is determined at runtime from the request's sourceAgentType)
      COMMIT_AGENT:
        - DISCOVERY_AGENT
        - PLANNING_AGENT
        - TICKET_AGENT
        - DISCOVERY_ORCHESTRATOR
        - PLANNING_ORCHESTRATOR
        - TICKET_ORCHESTRATOR
        - DISCOVERY_COLLECTOR
        - PLANNING_COLLECTOR
        - TICKET_COLLECTOR
        - ORCHESTRATOR
        - ORCHESTRATOR_COLLECTOR
      # Merge conflict agents can call back to agents on both sides of the merge
      MERGE_CONFLICT_AGENT:
        - DISCOVERY_AGENT
        - PLANNING_AGENT
        - TICKET_AGENT
        - DISCOVERY_ORCHESTRATOR
        - PLANNING_ORCHESTRATOR
        - TICKET_ORCHESTRATOR
        - DISCOVERY_COLLECTOR
        - PLANNING_COLLECTOR
        - TICKET_COLLECTOR
        - ORCHESTRATOR
        - ORCHESTRATOR_COLLECTOR

      # --- AI pipeline tool agents ---
      # AI filter/propagator/transformer run LLM calls inline within the
      # decorator pipeline. Their session identity depends on SessionMode.
      AI_FILTER:
        - DISCOVERY_AGENT
        - PLANNING_AGENT
        - TICKET_AGENT
        - DISCOVERY_ORCHESTRATOR
        - PLANNING_ORCHESTRATOR
        - TICKET_ORCHESTRATOR
        - DISCOVERY_COLLECTOR
        - PLANNING_COLLECTOR
        - TICKET_COLLECTOR
        - ORCHESTRATOR
        - ORCHESTRATOR_COLLECTOR
      AI_PROPAGATOR:
        - DISCOVERY_AGENT
        - PLANNING_AGENT
        - TICKET_AGENT
        - DISCOVERY_ORCHESTRATOR
        - PLANNING_ORCHESTRATOR
        - TICKET_ORCHESTRATOR
        - DISCOVERY_COLLECTOR
        - PLANNING_COLLECTOR
        - TICKET_COLLECTOR
        - ORCHESTRATOR
        - ORCHESTRATOR_COLLECTOR
      AI_TRANSFORMER:
        - DISCOVERY_AGENT
        - PLANNING_AGENT
        - TICKET_AGENT
        - DISCOVERY_ORCHESTRATOR
        - PLANNING_ORCHESTRATOR
        - TICKET_ORCHESTRATOR
        - DISCOVERY_COLLECTOR
        - PLANNING_COLLECTOR
        - TICKET_COLLECTOR
        - ORCHESTRATOR
        - ORCHESTRATOR_COLLECTOR

      # --- Interrupt service agents ---
      # REVIEW_AGENT runs agent-initiated review (InterruptService.runInterruptAgentReview).
      # REVIEW_RESOLUTION_AGENT resolves interrupt feedback into a routing decision
      # (InterruptService.handleInterrupt). Both operate on behalf of whichever agent
      # received the interrupt, so they can call back to any workflow agent.
      REVIEW_AGENT:
        - DISCOVERY_AGENT
        - PLANNING_AGENT
        - TICKET_AGENT
        - DISCOVERY_ORCHESTRATOR
        - PLANNING_ORCHESTRATOR
        - TICKET_ORCHESTRATOR
        - DISCOVERY_COLLECTOR
        - PLANNING_COLLECTOR
        - TICKET_COLLECTOR
        - ORCHESTRATOR
        - ORCHESTRATOR_COLLECTOR
      REVIEW_RESOLUTION_AGENT:
        - DISCOVERY_AGENT
        - PLANNING_AGENT
        - TICKET_AGENT
        - DISCOVERY_ORCHESTRATOR
        - PLANNING_ORCHESTRATOR
        - TICKET_ORCHESTRATOR
        - DISCOVERY_COLLECTOR
        - PLANNING_COLLECTOR
        - TICKET_COLLECTOR
        - ORCHESTRATOR
        - ORCHESTRATOR_COLLECTOR
```

## Java configuration class

```java
@ConfigurationProperties(prefix = "multi-agent-ide.topology")
public record CommunicationTopologyConfig(
    int maxCallChainDepth,        // default: 5
    int messageBudget,            // default: 3
    Map<AgentType, Set<AgentType>> allowedCommunications
) {}
```

## Runtime behavior

- Topology is loaded at startup and can be refreshed via Spring's `@RefreshScope` or config reload (SC-008)
- `list_agents` returns only agents in the intersection of (topology-permitted) AND (session-open)
- `call_agent` validates against topology before delivering message
- All agents can always call the controller (call_controller is not subject to topology restrictions)

## SessionKeyResolutionService

A new centralized service that owns all session key resolution, self-call filtering, and session-to-message routing. This replaces the scattered logic currently in `AiFilterSessionResolver` and `MultiAgentEmbabelConfig.llmOutputChannel()`.

### Responsibilities

1. **Session key resolution** (moved from `AiFilterSessionResolver`): Resolves session keys based on `SessionMode` (`PER_INVOCATION`, `SAME_SESSION_FOR_ACTION`, `SAME_SESSION_FOR_ALL`, `SAME_SESSION_FOR_AGENT`), manages the session cache, and handles lifecycle eviction on `GoalCompletedEvent`/`ActionCompletedEvent`.

2. **Message-to-session routing** (moved from `MultiAgentEmbabelConfig` lines 208-260): The current inline lambda in `llmOutputChannel()` queries `graphRepository.getAllMatching(ChatSessionCreatedEvent.class, ...)` to find the closest session for a `MessageOutputChannelEvent`. This routing logic moves to `SessionKeyResolutionService.resolveSessionForMessage(MessageOutputChannelEvent)`, and the config bean delegates to it.

3. **Self-call filtering**: `filterSelfCalls(callingKey, candidateKeys)` checks ArtifactKey ancestry to exclude the calling agent from communication targets.

4. **Event enrichment**: All session-related events published through this service include contextual provenance: source node, agent type, session mode, and a `FromMessageChannelEvent` marker for traceability.

### Session identity collision table

| SessionMode | chatId resolution | Self-call risk? |
|---|---|---|
| `PER_INVOCATION` | Fresh child of `currentKey` | No — always unique |
| `SAME_SESSION_FOR_ACTION` | Scoped to nearest `ExecutionNode` | No — action-scoped child |
| `SAME_SESSION_FOR_ALL` | Scoped to execution root | **Maybe** — if root is the calling agent's key |
| `SAME_SESSION_FOR_AGENT` | Scoped to agent-level parent of action | **Yes** — `resolveAgentScope()` walks up to the agent's own execution node, which IS the calling agent's ArtifactKey |

When `SAME_SESSION_FOR_AGENT` is used, the AI tool's `chatId` resolves to the same ArtifactKey as the calling agent's session. This means `list_agents` or `call_agent` could return the calling agent itself as a target.

### Self-call filter algorithm

`filterSelfCalls(callingKey, candidateKeys)` excludes any candidate where:
- `targetSessionKey.equals(callingSessionKey)` — direct identity collision
- `targetSessionKey.isAncestorOf(callingSessionKey)` — target is a parent of caller
- `callingSessionKey.isAncestorOf(targetSessionKey)` — target is a child of caller (AI tool session created under calling agent)

This covers:
1. AI pipeline tools with `SAME_SESSION_FOR_AGENT` (direct collision)
2. AI pipeline tools with `SAME_SESSION_FOR_ALL` (root collision)
3. `WorktreeAutoCommitService`/`WorktreeMergeConflictService` where `chatId()` = `contextId.parent()` (calling agent's key)

Called by both `list_agents` (exclude self from results) and `call_agent` (reject self-targeting before delivery).
