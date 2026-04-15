package com.hayden.multiagentide.service;

import com.hayden.acp_cdc_ai.acp.AcpSessionManager;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.multiagentide.model.nodes.DiscoveryOrchestratorNode;
import com.hayden.multiagentide.model.worktree.MainWorktreeContext;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentide.topology.CommunicationTopologyConfig;
import com.hayden.multiagentide.topology.CommunicationTopologyProvider;
import com.hayden.multiagentide.agent.AgentType;
import com.hayden.multiagentide.model.nodes.OrchestratorNode;
import com.hayden.multiagentide.model.nodes.GraphNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for AgentCommunicationService.
 *
 * <h3>Interfaces under test</h3>
 * <ul>
 *   <li>{@link AgentCommunicationService#listAvailableAgents(String, AgentType)}</li>
 * </ul>
 *
 * <h3>Input domains</h3>
 * <ul>
 *   <li>callingSessionKey: {null, blank, invalid-ArtifactKey, valid-ArtifactKey}</li>
 *   <li>callingAgentType: {null, each AgentType}</li>
 *   <li>sessionContexts: {empty, single-session, multiple-sessions, session-with-null-chatKey}</li>
 *   <li>graphRepository resolution: {returns node, returns empty}</li>
 *   <li>topology: {allows communication, denies communication}</li>
 *   <li>self-call: {caller is in sessions, caller is not in sessions}</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AgentCommunicationServiceTest {

    @Mock
    private AcpSessionManager acpSessionManager;
    @Mock
    private GraphRepository graphRepository;
    @Mock
    private SessionKeyResolutionService sessionKeyResolutionService;

    private CommunicationTopologyProvider topologyProvider;
    private AgentCommunicationService service;

    private final ConcurrentHashMap<Object, AcpSessionManager.AcpSessionContext> sessionContexts = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        var topologyConfig = new CommunicationTopologyConfig(5, 3, Map.of(
                AgentType.ORCHESTRATOR, Set.of(AgentType.DISCOVERY_ORCHESTRATOR, AgentType.PLANNING_ORCHESTRATOR),
                AgentType.DISCOVERY_AGENT, Set.of(AgentType.DISCOVERY_ORCHESTRATOR)
        ));
        topologyProvider = new CommunicationTopologyProvider(topologyConfig, mock(org.springframework.core.env.Environment.class));
        service = new AgentCommunicationService(acpSessionManager, graphRepository, topologyProvider, sessionKeyResolutionService);
        lenient().when(acpSessionManager.getSessionContexts()).thenReturn(sessionContexts);
        lenient().when(graphRepository.findById(anyString())).thenReturn(Optional.empty());
        // Default: filterSelfCalls passes through all keys
        lenient().when(sessionKeyResolutionService.filterSelfCalls(any(), any()))
                .thenAnswer(inv -> inv.getArgument(1));
    }

    // ── Null/invalid input handling ───────────────────────────────────────

    @Nested
    class NullAndInvalidInputs {

        @Test
        void nullCallingSessionKey_returnsEmpty() {
            assertThat(service.listAvailableAgents(null, AgentType.ORCHESTRATOR)).isEmpty();
        }

        @Test
        void blankCallingSessionKey_returnsEmpty() {
            assertThat(service.listAvailableAgents("", AgentType.ORCHESTRATOR)).isEmpty();
            assertThat(service.listAvailableAgents("  ", AgentType.ORCHESTRATOR)).isEmpty();
        }

        @Test
        void invalidArtifactKey_returnsEmpty() {
            assertThat(service.listAvailableAgents("not-an-artifact-key", AgentType.ORCHESTRATOR)).isEmpty();
        }

        @Test
        void nullCallingAgentType_returnsEntriesWithCallableFalse() {
            ArtifactKey callerKey = ArtifactKey.createRoot();
            ArtifactKey targetKey = ArtifactKey.createRoot();
            addSession(targetKey, mockOrchestratorNode(targetKey));

            var result = service.listAvailableAgents(callerKey.value(), null);
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().callableByCurrentAgent()).isFalse();
        }
    }

    // ── Empty sessions ────────────────────────────────────────────────────

    @Nested
    class EmptySessions {

        @Test
        void noSessions_returnsEmpty() {
            ArtifactKey caller = ArtifactKey.createRoot();
            assertThat(service.listAvailableAgents(caller.value(), AgentType.ORCHESTRATOR)).isEmpty();
        }
    }

    // ── Topology filtering ────────────────────────────────────────────────

    @Nested
    class TopologyFiltering {

        @Test
        void allowedTarget_callableIsTrue() {
            ArtifactKey callerKey = ArtifactKey.createRoot();
            ArtifactKey targetKey = ArtifactKey.createRoot();
            addSession(targetKey, mockDiscoveryOrchestratorNode(targetKey));

            var result = service.listAvailableAgents(callerKey.value(), AgentType.ORCHESTRATOR);
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().callableByCurrentAgent()).isTrue();
            assertThat(result.getFirst().agentType()).isEqualTo("discovery-orchestrator");
        }

        @Test
        void disallowedTarget_callableIsFalse() {
            ArtifactKey callerKey = ArtifactKey.createRoot();
            ArtifactKey targetKey = ArtifactKey.createRoot();
            addSession(targetKey, mockOrchestratorNode(targetKey));

            // DISCOVERY_AGENT cannot call ORCHESTRATOR per our test topology
            var result = service.listAvailableAgents(callerKey.value(), AgentType.DISCOVERY_AGENT);
            assertThat(result).hasSize(1);
            assertThat(result.getFirst().callableByCurrentAgent()).isFalse();
        }

        @Test
        void multipleSessions_mixedTopology() {
            ArtifactKey callerKey = ArtifactKey.createRoot();
            ArtifactKey target1 = ArtifactKey.createRoot(); // discovery orchestrator — allowed
            ArtifactKey target2 = ArtifactKey.createRoot(); // orchestrator — not allowed for DISCOVERY_AGENT

            addSession(target1, mockDiscoveryOrchestratorNode(target1));
            addSession(target2, mockOrchestratorNode(target2));

            var result = service.listAvailableAgents(callerKey.value(), AgentType.DISCOVERY_AGENT);
            assertThat(result).hasSize(2);

            var callable = result.stream().filter(AgentCommunicationService.AgentAvailabilityEntry::callableByCurrentAgent).toList();
            var notCallable = result.stream().filter(e -> !e.callableByCurrentAgent()).toList();
            assertThat(callable).hasSize(1);
            assertThat(callable.getFirst().agentType()).isEqualTo("discovery-orchestrator");
            assertThat(notCallable).hasSize(1);
            assertThat(notCallable.getFirst().agentType()).isEqualTo("orchestrator");
        }
    }

    // ── Self-call filtering ───────────────────────────────────────────────

    @Nested
    class SelfCallFiltering {

        @Test
        void selfCall_delegatesToSessionKeyResolutionService() {
            ArtifactKey callerKey = ArtifactKey.createRoot();
            ArtifactKey targetKey = ArtifactKey.createRoot();
            addSession(targetKey, mockOrchestratorNode(targetKey));

            // Make filterSelfCalls return empty (all filtered out)
            when(sessionKeyResolutionService.filterSelfCalls(any(), any()))
                    .thenReturn(Set.of());

            var result = service.listAvailableAgents(callerKey.value(), AgentType.ORCHESTRATOR);
            assertThat(result).isEmpty();

            verify(sessionKeyResolutionService).filterSelfCalls(eq(callerKey), any());
        }
    }

    // ── Session with null chatKey ─────────────────────────────────────────

    @Nested
    class NullChatKeySessions {

        @Test
        void sessionWithNullChatKey_skipped() {
            ArtifactKey callerKey = ArtifactKey.createRoot();

            var ctx = mock(AcpSessionManager.AcpSessionContext.class);
            when(ctx.getChatKey()).thenReturn(null);
            sessionContexts.put("session-null", ctx);

            assertThat(service.listAvailableAgents(callerKey.value(), AgentType.ORCHESTRATOR)).isEmpty();
        }
    }

    // ── Session with unresolvable agent type ──────────────────────────────

    @Nested
    class UnresolvableAgentType {

        @Test
        void sessionNotInGraphRepository_skipped() {
            ArtifactKey callerKey = ArtifactKey.createRoot();
            ArtifactKey targetKey = ArtifactKey.createRoot();

            var ctx = mock(AcpSessionManager.AcpSessionContext.class);
            when(ctx.getChatKey()).thenReturn(targetKey);
            sessionContexts.put(targetKey.value(), ctx);
            // graphRepository returns empty for target — agent type unresolvable
            when(graphRepository.findById(targetKey.value())).thenReturn(Optional.empty());

            assertThat(service.listAvailableAgents(callerKey.value(), AgentType.ORCHESTRATOR)).isEmpty();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private void addSession(ArtifactKey key, GraphNode node) {
        var ctx = mock(AcpSessionManager.AcpSessionContext.class);
        when(ctx.getChatKey()).thenReturn(key);
        sessionContexts.put(key.value(), ctx);
        when(graphRepository.findById(key.value())).thenReturn(Optional.of(node));
    }

    private GraphNode mockOrchestratorNode(ArtifactKey key) {
        return OrchestratorNode.builder()
                .nodeId(key.value())
                .title("Orchestrator")
                .goal("Test")
                .status(com.hayden.acp_cdc_ai.acp.events.Events.NodeStatus.RUNNING)
                .childNodeIds(List.of())
                .metadata(Map.of())
                .createdAt(java.time.Instant.now())
                .lastUpdatedAt(java.time.Instant.now())
                .orchestratorOutput("")
                .worktreeContext(MainWorktreeContext.builder()
                        .worktreeId("wt-test")
                        .repositoryUrl("https://test.example.com/repo.git")
                        .worktreePath(java.nio.file.Path.of("/tmp/test"))
                        .build())
                .build();
    }

    private GraphNode mockDiscoveryOrchestratorNode(ArtifactKey key) {
        return DiscoveryOrchestratorNode.builder()
                .nodeId(key.value())
                .title("Discovery Orchestrator")
                .goal("Test")
                .status(com.hayden.acp_cdc_ai.acp.events.Events.NodeStatus.RUNNING)
                .childNodeIds(List.of())
                .metadata(Map.of())
                .createdAt(java.time.Instant.now())
                .lastUpdatedAt(java.time.Instant.now())
                .build();
    }
}
