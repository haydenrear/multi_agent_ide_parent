package com.hayden.multiagentide.service;

import com.hayden.acp_cdc_ai.acp.AcpSessionManager;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentide.topology.CommunicationTopologyProvider;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.agent.AgentType;
import com.hayden.multiagentide.prompt.contributor.NodeMappings;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Provides agent-to-agent communication capabilities: session discovery, topology enforcement,
 * and self-call filtering.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentCommunicationService {

    private final AcpSessionManager acpSessionManager;
    private final GraphRepository graphRepository;
    private final CommunicationTopologyProvider topologyProvider;
    private final SessionKeyResolutionService sessionKeyResolutionService;

    public record AgentAvailabilityEntry(
            String agentKey,
            String agentType,
            boolean busy,
            boolean callableByCurrentAgent,
            /** For prohibited agents: list of available agent keys that CAN reach this agent. Empty if callable. */
            List<String> reachableVia
    ) {
    }

    /**
     * Result of call validation — either valid or contains an error message.
     */
    public record CallValidationResult(boolean valid, @Nullable String error) {
        public static CallValidationResult ok() { return new CallValidationResult(true, null); }
        public static CallValidationResult reject(String error) { return new CallValidationResult(false, error); }
    }

    /**
     * List all available agents that the calling agent can communicate with.
     * Returns agents filtered by: topology-permitted AND session-open AND not-self.
     */
    public @org.jspecify.annotations.NonNull List<AgentAvailabilityEntry> listAvailableAgents(@Nullable String callingSessionKey, @Nullable AgentType callingAgentType) {
        if (callingSessionKey == null || callingSessionKey.isBlank()) {
            return List.of();
        }

        ArtifactKey callingKey;
        try {
            callingKey = new ArtifactKey(callingSessionKey);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid calling session key: {}", callingSessionKey);
            return List.of();
        }

        // Collect all open session keys with their agent types
        Map<ArtifactKey, AgentType> sessionAgentTypes = new LinkedHashMap<>();
        for (Map.Entry<Object, AcpSessionManager.AcpSessionContext> entry : acpSessionManager.getSessionContexts().entrySet()) {
            ArtifactKey sessionKey = entry.getValue().getChatKey();
            if (sessionKey == null) {
                continue;
            }
            AgentType agentType = resolveAgentType(sessionKey);
            if (agentType != null) {
                sessionAgentTypes.put(sessionKey, agentType);
            }
        }

        // Filter out self-calls
        Set<ArtifactKey> filteredKeys = sessionKeyResolutionService.filterSelfCalls(callingKey, sessionAgentTypes.keySet());

        // Build result with topology check and reachableVia for prohibited agents
        List<AgentAvailabilityEntry> result = new ArrayList<>();
        for (ArtifactKey key : filteredKeys) {
            AgentType targetType = sessionAgentTypes.get(key);
            boolean topologyPermitted = callingAgentType != null
                    && topologyProvider.isCommunicationAllowed(callingAgentType, targetType);

            List<String> reachableVia = List.of();
            if (!topologyPermitted && callingAgentType != null) {
                // Find agents the caller CAN reach that CAN also reach this prohibited target
                reachableVia = findReachableVia(callingAgentType, targetType, filteredKeys, sessionAgentTypes);
            }

            result.add(new AgentAvailabilityEntry(
                    key.value(),
                    targetType.wireValue(),
                    false, // busy detection deferred to Phase 6
                    topologyPermitted,
                    reachableVia
            ));
        }

        return result;
    }

    /**
     * Validates whether a call from caller to target is permitted.
     * Checks: topology, self-call, active call-chain cycle, max depth, session availability.
     */
    public @NonNull CallValidationResult validateCall(
            @NonNull ArtifactKey callingKey, @Nullable AgentType callingType,
            @NonNull ArtifactKey targetKey, @Nullable AgentType targetType,
            @Nullable List<AgentModels.CallChainEntry> callChain
    ) {
        // 1. Self-call check (includes shared-session and graph-derived cycle detection)
        Set<ArtifactKey> filtered = sessionKeyResolutionService.filterSelfCalls(callingKey, Set.of(targetKey), callChain);
        if (filtered.isEmpty()) {
            return CallValidationResult.reject(
                    "ERROR: Cannot call self or agent on the same active session. Call rejected.");
        }

        // 2. Topology check
        if (callingType != null && targetType != null
                && !topologyProvider.isCommunicationAllowed(callingType, targetType)) {
            return CallValidationResult.reject(
                    "ERROR: Communication not permitted by topology rules. %s cannot call %s."
                            .formatted(callingType.wireValue(), targetType.wireValue()));
        }

        // 3. Call chain depth check
        int depth = callChain != null ? callChain.size() : 0;
        if (depth >= topologyProvider.maxCallChainDepth()) {
            return CallValidationResult.reject(
                    "ERROR: Call chain depth %d exceeds maximum %d. Call rejected."
                            .formatted(depth, topologyProvider.maxCallChainDepth()));
        }

        // 4. Loop detection via call chain parameter (explicit chain from tool parameter)
        if (callChain != null && callChain.stream().anyMatch(e -> e.agentKey().equals(targetKey))) {
            String chain = formatCallChain(callChain, targetKey);
            return CallValidationResult.reject(
                    "ERROR: Loop detected in call chain: %s. Call rejected.".formatted(chain));
        }

        // 5. Target session availability
        AcpSessionManager.AcpSessionContext targetSession = findTargetSession(targetKey);
        if (targetSession == null) {
            return CallValidationResult.reject(
                    "ERROR: Agent %s is no longer available. Try an alternative route."
                            .formatted(targetKey.value()));
        }

        return CallValidationResult.ok();
    }

    /**
     * Finds the ACP session context for the given target agent key.
     */
    public @Nullable AcpSessionManager.AcpSessionContext findTargetSession(@NonNull ArtifactKey targetKey) {
        for (Map.Entry<Object, AcpSessionManager.AcpSessionContext> entry : acpSessionManager.getSessionContexts().entrySet()) {
            ArtifactKey sessionKey = entry.getValue().getChatKey();
            if (sessionKey != null && sessionKey.equals(targetKey)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private String formatCallChain(@NonNull List<AgentModels.CallChainEntry> chain, @NonNull ArtifactKey target) {
        StringBuilder sb = new StringBuilder();
        for (AgentModels.CallChainEntry entry : chain) {
            sb.append(entry.agentKey().value());
            if (entry.targetAgentKey() != null) {
                sb.append(" -> ").append(entry.targetAgentKey().value());
            }
            sb.append(" -> ");
        }
        sb.append(target.value());
        return sb.toString();
    }

    /**
     * For a prohibited target, find which agents the caller CAN reach that CAN also reach the target.
     * Only considers agents with active sessions (from filteredKeys).
     */
    private List<String> findReachableVia(
            @NonNull AgentType callingType,
            @NonNull AgentType prohibitedTargetType,
            @NonNull Set<ArtifactKey> availableKeys,
            @NonNull Map<ArtifactKey, AgentType> sessionAgentTypes
    ) {
        List<String> bridges = new ArrayList<>();
        for (ArtifactKey candidateKey : availableKeys) {
            AgentType candidateType = sessionAgentTypes.get(candidateKey);
            if (candidateType == null) continue;
            // Candidate must be: (1) callable by the caller, and (2) able to call the prohibited target
            if (topologyProvider.isCommunicationAllowed(callingType, candidateType)
                    && topologyProvider.isCommunicationAllowed(candidateType, prohibitedTargetType)) {
                bridges.add(candidateKey.value());
            }
        }
        return bridges;
    }

    private @Nullable AgentType resolveAgentType(ArtifactKey sessionKey) {
        return graphRepository.findById(sessionKey.value())
                .map(NodeMappings::agentTypeFromNode)
                .orElse(null);
    }

    public @Nullable AgentType resolveAgentTypePublic(@NonNull ArtifactKey key) {
        return resolveAgentType(key);
    }
}
