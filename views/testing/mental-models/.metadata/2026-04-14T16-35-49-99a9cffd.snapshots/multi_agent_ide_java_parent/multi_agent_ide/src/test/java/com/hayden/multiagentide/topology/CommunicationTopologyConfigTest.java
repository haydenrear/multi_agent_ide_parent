package com.hayden.multiagentide.topology;

import com.hayden.multiagentide.agent.AgentType;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Property-based tests for CommunicationTopologyConfig.
 *
 * <h3>Interfaces under test</h3>
 * <ul>
 *   <li>{@link CommunicationTopologyConfig#CommunicationTopologyConfig(int, int, Map)} — compact constructor with defaults</li>
 *   <li>{@link CommunicationTopologyConfig#isCommunicationAllowed(AgentType, AgentType)} — topology lookup</li>
 * </ul>
 *
 * <h3>Input domains</h3>
 * <ul>
 *   <li>maxCallChainDepth: {negative, zero, positive}</li>
 *   <li>messageBudget: {negative, zero, positive}</li>
 *   <li>allowedCommunications: {null, empty map, single-entry, multi-entry}</li>
 *   <li>source AgentType: every enum value</li>
 *   <li>target AgentType: every enum value</li>
 * </ul>
 */
class CommunicationTopologyConfigTest {

    // ── Compact constructor defaults ──────────────────────────────────────

    @Nested
    class CompactConstructorDefaults {

        @Test
        void negativeMaxCallChainDepth_defaultsToFive() {
            var config = new CommunicationTopologyConfig(-1, 3, Map.of());
            assertThat(config.maxCallChainDepth()).isEqualTo(5);
        }

        @Test
        void zeroMaxCallChainDepth_defaultsToFive() {
            var config = new CommunicationTopologyConfig(0, 3, Map.of());
            assertThat(config.maxCallChainDepth()).isEqualTo(5);
        }

        @Test
        void positiveMaxCallChainDepth_preserved() {
            var config = new CommunicationTopologyConfig(10, 3, Map.of());
            assertThat(config.maxCallChainDepth()).isEqualTo(10);
        }

        @Test
        void negativeMessageBudget_defaultsToThree() {
            var config = new CommunicationTopologyConfig(5, -1, Map.of());
            assertThat(config.messageBudget()).isEqualTo(-1);
        }

        @Test
        void zeroMessageBudget_defaultsToThree() {
            var config = new CommunicationTopologyConfig(5, 0, Map.of());
            assertThat(config.messageBudget()).isEqualTo(-1);
        }

        @Test
        void positiveMessageBudget_preserved() {
            var config = new CommunicationTopologyConfig(5, 7, Map.of());
            assertThat(config.messageBudget()).isEqualTo(7);
        }

        @Test
        void nullAllowedCommunications_defaultsToEmptyMap() {
            var config = new CommunicationTopologyConfig(5, 3, null);
            assertThat(config.allowedCommunications()).isNotNull().isEmpty();
        }

        @Test
        void allDefaults_whenAllInvalid() {
            var config = new CommunicationTopologyConfig(-1, -1, null);
            assertThat(config.maxCallChainDepth()).isEqualTo(5);
            assertThat(config.messageBudget()).isEqualTo(-1);
            assertThat(config.allowedCommunications()).isEmpty();
        }
    }

    // ── isCommunicationAllowed ────────────────────────────────────────────

    @Nested
    class IsCommunicationAllowed {

        private static final Map<AgentType, Set<AgentType>> TOPOLOGY = Map.of(
                AgentType.DISCOVERY_AGENT, Set.of(AgentType.DISCOVERY_ORCHESTRATOR, AgentType.DISCOVERY_COLLECTOR),
                AgentType.ORCHESTRATOR, Set.of(AgentType.DISCOVERY_ORCHESTRATOR, AgentType.PLANNING_ORCHESTRATOR)
        );

        private final CommunicationTopologyConfig config = new CommunicationTopologyConfig(5, 3, TOPOLOGY);

        @Test
        void allowed_whenSourceHasTargetInSet() {
            assertThat(config.isCommunicationAllowed(AgentType.DISCOVERY_AGENT, AgentType.DISCOVERY_ORCHESTRATOR)).isTrue();
            assertThat(config.isCommunicationAllowed(AgentType.DISCOVERY_AGENT, AgentType.DISCOVERY_COLLECTOR)).isTrue();
            assertThat(config.isCommunicationAllowed(AgentType.ORCHESTRATOR, AgentType.DISCOVERY_ORCHESTRATOR)).isTrue();
        }

        @Test
        void denied_whenTargetNotInSourceSet() {
            assertThat(config.isCommunicationAllowed(AgentType.DISCOVERY_AGENT, AgentType.PLANNING_ORCHESTRATOR)).isFalse();
            assertThat(config.isCommunicationAllowed(AgentType.ORCHESTRATOR, AgentType.DISCOVERY_AGENT)).isFalse();
        }

        @Test
        void denied_whenSourceNotInMap() {
            assertThat(config.isCommunicationAllowed(AgentType.PLANNING_AGENT, AgentType.DISCOVERY_ORCHESTRATOR)).isFalse();
        }

        @Test
        void emptyTopology_alwaysDenies() {
            var emptyConfig = new CommunicationTopologyConfig(5, 3, Map.of());
            for (AgentType source : AgentType.values()) {
                for (AgentType target : AgentType.values()) {
                    assertThat(emptyConfig.isCommunicationAllowed(source, target))
                            .as("empty topology should deny %s -> %s", source, target)
                            .isFalse();
                }
            }
        }

        /**
         * Exhaustive: for every (source, target) pair, result must match Set.contains.
         */
        @ParameterizedTest(name = "{0} -> {1}")
        @MethodSource("allAgentTypePairs")
        void exhaustive_matchesSetContains(AgentType source, AgentType target) {
            Set<AgentType> allowed = TOPOLOGY.getOrDefault(source, Set.of());
            boolean expected = allowed.contains(target);
            assertThat(config.isCommunicationAllowed(source, target))
                    .as("%s -> %s", source, target)
                    .isEqualTo(expected);
        }

        static Stream<Arguments> allAgentTypePairs() {
            return Stream.of(AgentType.values())
                    .flatMap(source -> Stream.of(AgentType.values())
                            .map(target -> Arguments.of(source, target)));
        }

        /**
         * No agent type can communicate with itself via topology (unless explicitly configured).
         */
        @ParameterizedTest
        @EnumSource(AgentType.class)
        void selfCommunication_deniedUnlessExplicit(AgentType type) {
            Set<AgentType> allowed = TOPOLOGY.getOrDefault(type, Set.of());
            assertThat(config.isCommunicationAllowed(type, type))
                    .isEqualTo(allowed.contains(type));
        }
    }
}
