package com.hayden.multiagentide.service;

import com.embabel.agent.api.channel.MessageOutputChannelEvent;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.EventListener;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.repository.EventStreamRepository;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.filter.model.executor.AiFilterTool;
import com.hayden.multiagentide.model.nodes.AgentToAgentConversationNode;
import com.hayden.multiagentide.model.nodes.ExecutionNode;
import com.hayden.multiagentide.model.nodes.GraphNode;
import com.hayden.multiagentide.model.nodes.HasChatId;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentide.prompt.PromptContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Centralizes session key resolution, self-call filtering, and message-to-session routing.
 * Absorbs logic previously scattered across AiFilterSessionResolver and MultiAgentEmbabelConfig.llmOutputChannel().
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class SessionKeyResolutionService implements EventListener {

    public record SessionScopeKey(String policyId,
                                   AiFilterTool.SessionMode sessionMode,
                                   String scopeNodeId,
                                   String scopeQualifier,
                                   String rootNodeId) {
    }

    private record ResolvedScope(ArtifactKey sessionParentKey, SessionScopeKey sessionScopeKey) {
    }

    private final GraphRepository graphRepository;
    private final EventStreamRepository eventStreamRepository;

    private EventBus eventBus;

    private final ConcurrentHashMap<SessionScopeKey, ArtifactKey> sessionCache = new ConcurrentHashMap<>();

    @Autowired
    @Lazy
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    // ── Session key resolution (FR-013a) ──────────────────────────────────

    public @NonNull ArtifactKey resolveSessionKey(@Nullable String policyId,
                                         AiFilterTool.@Nullable SessionMode sessionMode,
                                         @Nullable PromptContext promptContext) {
        ArtifactKey currentKey = promptContext == null ? null : promptContext.currentContextId();
        if (currentKey == null) {
            return ArtifactKey.createRoot();
        }

        AiFilterTool.SessionMode effectiveMode = sessionMode == null
                ? AiFilterTool.SessionMode.PER_INVOCATION
                : sessionMode;

        if (effectiveMode == AiFilterTool.SessionMode.PER_INVOCATION) {
            ArtifactKey created = currentKey.createChild();
            publishSessionCreatedEvent(
                    new SessionScopeKey(
                            policyId == null ? "<unknown-policy>" : policyId,
                            effectiveMode,
                            currentKey.value(),
                            resolveActionQualifier(promptContext),
                            resolveExecutionRoot(currentKey).value()
                    ),
                    currentKey,
                    created
            );
            return created;
        }

        ResolvedScope resolvedScope = resolveScope(
                policyId == null ? "<unknown-policy>" : policyId,
                effectiveMode,
                promptContext,
                currentKey
        );

        return sessionCache.computeIfAbsent(
                resolvedScope.sessionScopeKey(),
                ignored -> {
                    ArtifactKey created = resolvedScope.sessionParentKey().createChild();
                    publishSessionCreatedEvent(
                            resolvedScope.sessionScopeKey(),
                            resolvedScope.sessionParentKey(),
                            created
                    );
                    return created;
                }
        );
    }

    private ResolvedScope resolveScope(String policyId,
                                       AiFilterTool.SessionMode sessionMode,
                                       PromptContext promptContext,
                                       ArtifactKey currentKey) {
        ArtifactKey rootKey = resolveExecutionRoot(currentKey);
        return switch (sessionMode) {
            case PER_INVOCATION -> throw new IllegalStateException("PER_INVOCATION is handled before scope resolution");

            case SAME_SESSION_FOR_ALL -> new ResolvedScope(
                    rootKey,
                    new SessionScopeKey(
                            policyId,
                            sessionMode,
                            rootKey.value(),
                            "ALL",
                            rootKey.value()
                    )
            );

            case SAME_SESSION_FOR_ACTION -> {
                ArtifactKey actionScope = resolveActionScope(currentKey);
                yield new ResolvedScope(
                        actionScope,
                        new SessionScopeKey(
                                policyId,
                                sessionMode,
                                actionScope.value(),
                                resolveActionQualifier(promptContext),
                                rootKey.value()
                        )
                );
            }

            case SAME_SESSION_FOR_AGENT -> {
                ArtifactKey agentScope = resolveAgentScope(currentKey);
                yield new ResolvedScope(
                        agentScope,
                        new SessionScopeKey(
                                policyId,
                                sessionMode,
                                agentScope.value(),
                                resolveAgentQualifier(currentKey),
                                rootKey.value()
                        ));
            }
        };
    }

    private ArtifactKey resolveActionScope(ArtifactKey currentKey) {
        ArtifactKey nearestExecution = findNearestExecutionNode(currentKey);
        if (nearestExecution != null) {
            return nearestExecution;
        }
        return resolveExecutionRoot(currentKey);
    }

    private ArtifactKey resolveAgentScope(ArtifactKey currentKey) {
        ArtifactKey actionScope = resolveActionScope(currentKey);
        ArtifactKey probe = actionScope.parent().orElse(null);
        while (probe != null) {
            if (isExecutionNode(probe)) {
                return probe;
            }
            probe = probe.parent().orElse(null);
        }
        return actionScope;
    }

    private ArtifactKey resolveExecutionRoot(ArtifactKey currentKey) {
        ArtifactKey rootKey = currentKey.isRoot() ? currentKey : currentKey.root();
        ArtifactKey nearestExecution = findNearestExecutionNode(currentKey);
        if (nearestExecution == null) {
            return rootKey;
        }

        ArtifactKey topExecution = nearestExecution;
        ArtifactKey probe = topExecution.parent().orElse(null);
        while (probe != null && isExecutionNode(probe)) {
            topExecution = probe;
            probe = probe.parent().orElse(null);
        }
        return topExecution;
    }

    private ArtifactKey findNearestExecutionNode(ArtifactKey currentKey) {
        ArtifactKey probe = currentKey;
        while (probe != null) {
            if (isExecutionNode(probe)) {
                return probe;
            }
            probe = probe.parent().orElse(null);
        }
        return null;
    }

    private boolean isExecutionNode(ArtifactKey key) {
        return key != null && getExecutionNode(key).isPresent();
    }

    private Optional<ExecutionNode> getExecutionNode(ArtifactKey key) {
        return graphRepository.findById(key.value())
                .flatMap(gn -> gn instanceof ExecutionNode en ? Optional.of(en) : Optional.empty());
    }

    private String resolveActionQualifier(PromptContext promptContext) {
        return promptContext != null && promptContext.agentType() != null
                ? promptContext.agentType().name()
                : "UNKNOWN_AGENT";
    }

    private String resolveAgentQualifier(ArtifactKey promptContext) {
        return getExecutionNode(promptContext)
                .map(ExecutionNode::agent)
                .orElse("UNKNOWN");
    }

    // ── Message-to-session routing (FR-013b) ──────────────────────────────

    /**
     * Resolves which chat session a MessageOutputChannelEvent should be routed to.
     * Absorbs the inline lambda logic from MultiAgentEmbabelConfig.llmOutputChannel().
     */
    public @NonNull Optional<ArtifactKey> resolveSessionForMessage(@NonNull MessageOutputChannelEvent evt) {
        return eventStreamRepository.getAllMatching(
                        Events.ChatSessionCreatedEvent.class,
                        n -> matchesThisSession(evt, n))
                .min(Comparator.comparing(c -> c.chatModelId().value().replace(evt.getProcessId(), "").length()))
                .map(Events.ChatSessionCreatedEvent::chatModelId);
    }

    private static boolean matchesThisSession(MessageOutputChannelEvent evt, Events.ChatSessionCreatedEvent n) {
        try {
            return evt.getProcessId().equals(n.chatModelId().value());
        } catch (Exception e) {
            log.error("Error finding session {}", e.getMessage(), e);
            return false;
        }
    }

    // ── Call chain derivation from workflow graph ──────────────────────────

    /**
     * Finds an active AgentToAgentConversationNode where the given session
     * is the target — meaning someone called this session and it's part of an existing chain.
     */
    public @Nullable AgentToAgentConversationNode findIncomingCallNode(@NonNull String sessionId) {
        return graphRepository.findByType(Events.NodeType.AGENT_TO_AGENT_CONVERSATION).stream()
                .filter(AgentToAgentConversationNode.class::isInstance)
                .map(AgentToAgentConversationNode.class::cast)
                .filter(n -> sessionId.equals(n.targetAgentKey())
                        || sessionId.equals(n.targetNodeId()))
                .filter(n -> n.status() == Events.NodeStatus.RUNNING)
                .findFirst()
                .orElse(null);
    }

    /**
     * Builds the call chain by walking AgentToAgentConversationNode ancestors
     * from the current session backwards through the graph.
     */
    public @NonNull List<AgentModels.CallChainEntry> buildCallChainFromGraph(@NonNull String sessionId) {
        List<AgentModels.CallChainEntry> chain = new ArrayList<>();
        AgentToAgentConversationNode current = findIncomingCallNode(sessionId);
        while (current != null) {
            chain.addFirst(new AgentModels.CallChainEntry(
                    new ArtifactKey(current.sourceAgentKey()),
                    current.sourceAgentType(),
                    current.targetAgentKey() != null ? new ArtifactKey(current.targetAgentKey()) : null,
                    current.targetAgentType(),
                    current.createdAt()
            ));
            String sourceKey = current.sourceAgentKey();
            AgentToAgentConversationNode parent = findIncomingCallNode(sourceKey);
            if (parent == current) {
                break;
            }
            current = parent;
        }
        return chain;
    }

    /**
     * Resolves a graph node by session key. Searches all nodes where either the nodeId
     * or chatSessionKey (for HasChatSessionKey nodes) matches, and returns the most
     * recently updated match.
     */
    public @Nullable GraphNode resolveNodeBySessionKey(@NonNull String sessionKey) {
        return graphRepository.findAll().stream()
                .filter(n -> sessionKey.equals(n.nodeId())
                        || (n instanceof HasChatId hck && sessionKey.equals(hck.chatId())))
                .max(java.util.Comparator.comparing(GraphNode::lastUpdatedAt))
                .orElse(null);
    }

    // ── Self-call filtering (FR-013c) ─────────────────────────────────────

    /**
     * Filters out candidate session keys to prevent cycles in the inter-agent
     * communication chain and direct self-calls.
     * <p>
     * Excludes any candidate where:
     * <ul>
     *   <li>The candidate resolves to the same owning agent as the caller (self)</li>
     *   <li>The candidate is currently in the caller's active call chain (cycle prevention)</li>
     * </ul>
     * <p>
     * Does NOT filter based on ArtifactKey ancestor/descendant relationships —
     * agents at different levels (e.g. discovery agent → discovery orchestrator)
     * are allowed to communicate.
     */
    public @NonNull Set<ArtifactKey> filterSelfCalls(@Nullable ArtifactKey callingKey, @Nullable Set<ArtifactKey> candidateKeys) {
        return filterSelfCalls(callingKey, candidateKeys, null);
    }

    /**
     * Filters candidate keys using self-call detection and graph-derived call chain for cycle detection.
     * When a call chain is provided it is used directly; otherwise one is built from the graph.
     */
    public @NonNull Set<ArtifactKey> filterSelfCalls(
            @Nullable ArtifactKey callingKey,
            @Nullable Set<ArtifactKey> candidateKeys,
            @Nullable List<AgentModels.CallChainEntry> callChain
    ) {
        if (callingKey == null || candidateKeys == null) {
            return candidateKeys != null ? candidateKeys : Set.of();
        }

        List<AgentModels.CallChainEntry> effectiveChain = callChain != null && !callChain.isEmpty()
                ? callChain
                : buildCallChainFromGraph(callingKey.value());

        String callerNodeId = resolveOwningNodeId(callingKey);

        Set<ArtifactKey> filtered = new LinkedHashSet<>();
        for (ArtifactKey candidate : candidateKeys) {
            String candidateNodeId = resolveOwningNodeId(candidate);

            // Self check: same owning agent
            if (callerNodeId.equals(candidateNodeId)) {
                continue;
            }

            // Cycle check using graph-derived call chain
            if (!effectiveChain.isEmpty()
                    && effectiveChain.stream().anyMatch(e -> e.agentKey().equals(candidate))) {
                log.debug("Filtered candidate {} — present in call chain from graph", candidateNodeId);
                continue;
            }

            filtered.add(candidate);
        }
        return filtered;
    }

    /**
     * Resolves a chat session key to its owning agent's nodeId.
     * Some agents use a different chatId than their nodeId (e.g., via AiFilterSessionResolver
     * or PromptContext.chatId()). This method maps the session key back to the nodeId
     * by looking up ChatSessionCreatedEvent records.
     *
     * @return the owning nodeId, or the key's value if no mapping is found
     */
    @NonNull String resolveOwningNodeId(@NonNull ArtifactKey sessionKey) {
        return eventStreamRepository.getLastMatching(
                Events.ChatSessionCreatedEvent.class,
                e -> sessionKey.value().equals(e.chatModelId().value())
        ).map(Events.ChatSessionCreatedEvent::nodeId).orElse(sessionKey.value());
    }

    // ── Event enrichment (FR-013d) ────────────────────────────────────────

    private void publishSessionCreatedEvent(SessionScopeKey scopeKey,
                                            ArtifactKey nodeKey,
                                            ArtifactKey sessionContextId) {
        if (eventBus == null || scopeKey == null || nodeKey == null || sessionContextId == null) {
            return;
        }
        eventBus.publish(new Events.AiFilterSessionEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                nodeKey.value(),
                scopeKey.policyId(),
                scopeKey.sessionMode().name(),
                scopeKey.scopeNodeId(),
                scopeKey.scopeQualifier(),
                scopeKey.rootNodeId(),
                sessionContextId
        ));
    }

    // ── Lifecycle eviction ────────────────────────────────────────────────

    @Override
    public String listenerId() {
        return "session-key-resolution-service";
    }

    @Override
    public boolean isInterestedIn(Events.GraphEvent eventType) {
        return eventType instanceof Events.GoalCompletedEvent
                || eventType instanceof Events.ActionCompletedEvent;
    }

    @Override
    public void onEvent(Events.GraphEvent event) {
        switch (event) {
            case Events.GoalCompletedEvent goal -> evictGoalScopedSessions(goal.nodeId());
            case Events.ActionCompletedEvent action -> evictActionScopedSessions(action.nodeId());
            default -> {
            }
        }
    }

    private void evictGoalScopedSessions(String goalNodeId) {
        if (goalNodeId == null || goalNodeId.isBlank()) {
            return;
        }
        int before = sessionCache.size();
        sessionCache.keySet().removeIf(key ->
                goalNodeId.equals(key.rootNodeId()));
        int removed = before - sessionCache.size();
        if (removed > 0) {
            log.debug("Evicted {} session entries for completed goal {}", removed, goalNodeId);
        }
    }

    private void evictActionScopedSessions(String actionNodeId) {
        if (actionNodeId == null || actionNodeId.isBlank()) {
            return;
        }
        int before = sessionCache.size();
        sessionCache.keySet().removeIf(key ->
                key.sessionMode() == AiFilterTool.SessionMode.SAME_SESSION_FOR_ACTION
                        && actionNodeId.equals(key.scopeNodeId()));
        int removed = before - sessionCache.size();
        if (removed > 0) {
            log.debug("Evicted {} session entries for completed action {}", removed, actionNodeId);
        }
    }
}
