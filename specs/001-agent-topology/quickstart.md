# Quickstart: Inter-Agent Communication Topology

## Implementation Order

This feature has strict dependencies between workstreams. Follow this order:

### Phase A: Interrupt Simplification (Story 9 — P0 pre-requisite)

**Must complete before any other work.**

1. Remove `ReviewRequest`, `MergerRequest`, `ReviewRouting`, `MergerRouting` from `AgentModels.java`
2. Delete `ReviewRoute.java`, `MergerRoute.java` annotations
3. Remove `performReview()`, `performMerge()`, `mapReview()`, `mapMerge()` from `AgentInterfaces.java`
4. Delete `InterruptPromptContributorFactory.java`
5. Delete `OrchestratorRouteBackInterruptPromptContributorFactory.java`
6. Update `FilterPropertiesDecorator.java` — remove Review/Merger handling
7. Update `InterruptLoopBreakerPromptContributorFactory.java` — remove Review/Merger cases
8. Update `InterruptController.java` — add prompt contributor injection for interrupt schema
9. Grep entire codebase for remaining `ReviewRequest`, `MergerRequest`, `performReview`, `performMerge` references and clean up
10. Verify compilation, run unit tests

### Phase B: Communication AgentRequest Subtypes, GraphNodes & Decorator Audit (Stories 1-8 foundation)

**Must complete before tools — the tools depend on these types flowing through the pipeline.**

1. Add `AgentToControllerRequest`, `AgentToAgentRequest`, `ControllerToAgentRequest` to `AgentModels.java` as `AgentRequest` implementations. Use ArtifactKey identity model: `sourceAgentKey`/`targetAgentKey` (not separate sessionId/nodeId). Request `key` = child of source key.
2. Add three new `GraphNode` implementations to `GraphNode.java` sealed interface: `AgentToAgentConversationNode`, `AgentToControllerConversationNode`, `ControllerToAgentConversationNode`. Each stores source/target keys as proper fields (not metadata map).
3. Update `PromptContext.chatId()` — add cases for all three new types returning `targetAgentKey` (or controller per-target key for `AgentToControllerRequest`)
4. Audit all decorator switch cases that match on `AgentRequest` subtypes — add cases for all three new types:
   - `FilterPropertiesDecorator.mapRequestToRoute()` → return null
   - `InterruptLoopBreakerPromptContributorFactory.resolveMapping()` → return null
   - `WeAreHerePromptContributor` → conversation context
   - `CurationHistoryContextContributorFactory` → conversation history
   - All `RequestDecorator` and `ResultDecorator` implementations → handle or pass through
5. Verify compilation with new types

### Phase C: Inter-Agent Communication (Stories 1-6)

1. Add `CommunicationTopologyConfig` with `@ConfigurationProperties`
2. Add default topology to `application.yml` — includes COMMIT_AGENT, MERGE_CONFLICT_AGENT, AI_FILTER, AI_PROPAGATOR, AI_TRANSFORMER entries
3. Create `SessionKeyResolutionService` — centralizes session key resolution (from `AiFilterSessionResolver`), message-to-session routing (from `MultiAgentEmbabelConfig.llmOutputChannel()` lines 208-260), self-call filtering (ArtifactKey ancestry check), and event enrichment with provenance markers (FR-013a–FR-013d)
4. Create `AgentCommunicationService` — session queries, topology enforcement, loop detection; delegates to `SessionKeyResolutionService` for self-call filtering
5. Add `list_agents` tool to `AcpTooling` — must filter out calling agent's own session via `SessionKeyResolutionService.filterSelfCalls()` (FR-013c)
6. Add `call_agent` tool to `AcpTooling` — builds `AgentToAgentRequest`, persists `AgentToAgentConversationNode` in GraphRepository, runs through decorator pipeline + `DefaultLlmRunner`, response type is `String.class`
7. Add communication event types to `Events.java`
8. Add `AgentTopologyPromptContributorFactory` — injects available targets into agent context
9. Run unit tests, verify topology enforcement

### Phase D: Call Controller & Justification (Stories 7-8)

1. Add `call_controller` tool to `AcpTooling` — builds `AgentToControllerRequest`, persists `AgentToControllerConversationNode`, runs through decorator pipeline + `DefaultLlmRunner`, publishes interrupt via `PermissionGate.publishInterrupt()`
2. Create `AgentConversationController.java` — dedicated REST controller for controller→agent responses at `POST /api/agent-conversations/respond`. Resolves `controllerConversationKey` (lookup/create from GraphRepository if null), builds `ControllerToAgentRequest` with optional `checklistAction`, persists `ControllerToAgentConversationNode`, emits `Artifact.ControllerChecklistTurn` if checklistAction present (NOT passed to model), runs through decorator pipeline, delivers enriched message to agent session
3. Add `ChecklistAction` record and `Artifact.ControllerChecklistTurn` to `AgentModels.java`
4. Create `JustificationPromptContributorFactory` — matches on `AgentToControllerRequest`/`ControllerToAgentRequest` types + blackboard history, injects justification templates
5. Add message budget tracking to `call_controller`
6. Run unit tests

### Phase E: Conversational Topology Documents (Story 10)

1. Create `conversational-topology/` directory in controller skill
2. Write `checklist.md`, agent-specific checklists with ACTION rows (Step/ACTION/Description/Gate columns), `reference.md`
3. Create `conversational-topology-history/` with `reference.md`
4. Update `SKILL.md` with full instructions
5. Update `SKILL_PARENT.md` with brief reference

## Key Integration Points

- **ArtifactKey identity**: nodeId = sessionId = ArtifactKey — all the same thing. `sourceAgentKey`/`targetAgentKey` identify agents. Request `key` = child of source key.
- **AgentModels.java**: Three new `AgentRequest` subtypes (`AgentToControllerRequest`, `AgentToAgentRequest`, `ControllerToAgentRequest`) flow through the entire decorator pipeline
- **GraphNode.java**: Three new `GraphNode` implementations (`AgentToAgentConversationNode`, `AgentToControllerConversationNode`, `ControllerToAgentConversationNode`) added to sealed permits list for persistence/searchability
- **PromptContext.chatId()**: Returns `targetAgentKey` for all communication requests (controller per-target key for `AgentToControllerRequest`)
- **AcpTooling.java**: `list_agents`, `call_agent`, `call_controller` tools registered here, following `@Tool` + `@SetFromHeader` pattern. Tools build the appropriate request type, persist GraphNode, run through decorators + `DefaultLlmRunner`
- **AgentConversationController.java**: Dedicated REST controller for controller→agent responses — resolves `controllerConversationKey` (lookup/create if null), builds `ControllerToAgentRequest`, persists GraphNode, runs through decorator pipeline, delivers to agent session
- **AgentCommunicationService**: New service that wraps `AcpSessionManager` queries with topology enforcement
- **PermissionGate**: `call_controller` reuses interrupt publishing — no permission gate changes needed
- **BlackboardHistory**: Auto-subscribes to new events via existing `EventListener` pattern
- **FilterPropertiesDecorator**: Modified for interrupt simplification; new request types return null from `mapRequestToRoute()`
- **All decorator switch cases**: Must add cases for the three new `AgentRequest` subtypes — the decorator audit (Phase B) ensures nothing breaks

## Verification Checklist

- [ ] `ReviewRequest` / `MergerRequest` completely removed (grep returns 0 hits)
- [ ] No agent can self-initiate an interrupt (grep for interrupt prompt contributors returns only retained ones)
- [ ] `list_agents` returns only open, non-dispatched sessions within topology rules
- [ ] `call_agent` rejects topology violations, busy agents, loops, and max depth
- [ ] `call_controller` publishes interrupt and returns controller's resolution notes
- [ ] GraphNodes persisted for all three communication paths (searchable by type and parent)
- [ ] Controller conversation key created on first call, reused on subsequent calls, auto-lookup if null
- [ ] Communication events emitted for all call_agent actions
- [ ] Topology reconfigurable without restart
- [ ] Conversational topology documents exist in controller skill
- [ ] SKILL.md and SKILL_PARENT.md updated with topology document references
