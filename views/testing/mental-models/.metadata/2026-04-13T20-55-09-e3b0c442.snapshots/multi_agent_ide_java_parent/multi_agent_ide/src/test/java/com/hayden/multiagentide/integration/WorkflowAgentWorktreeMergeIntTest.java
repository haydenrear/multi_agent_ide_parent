package com.hayden.multiagentide.integration;

import com.embabel.agent.api.annotation.support.ActionQosProvider;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.api.common.PlannerType;
import com.embabel.agent.core.ActionQos;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcessStatusCode;
import com.embabel.agent.core.ProcessOptions;
import com.hayden.multiagentide.agent.AgentInterfaces;
import com.hayden.multiagentide.agent.WorkflowGraphService;
import com.hayden.multiagentide.artifacts.ArtifactEventListener;
import com.hayden.multiagentide.artifacts.ExecutionScopeService;
import com.hayden.multiagentide.artifacts.repository.ArtifactRepository;
import com.hayden.multiagentide.filter.repository.FilterDecisionRecordRepository;
import com.hayden.multiagentide.filter.repository.LayerRepository;
import com.hayden.multiagentide.filter.repository.PolicyRegistrationRepository;
import com.hayden.multiagentide.gate.PermissionGate;
import com.hayden.multiagentide.model.nodes.DataLayerOperationNode;
import com.hayden.multiagentide.orchestration.ComputationGraphOrchestrator;
import com.hayden.multiagentide.propagation.repository.PropagationItemRepository;
import com.hayden.multiagentide.propagation.repository.PropagationRecordRepository;
import com.hayden.multiagentide.propagation.repository.PropagatorRegistrationEntity;
import com.hayden.multiagentide.propagation.repository.PropagatorRegistrationRepository;
import com.hayden.multiagentide.propagation.service.AutoAiPropagatorBootstrap;
import com.hayden.multiagentide.filter.service.LayerHierarchyBootstrap;
import com.hayden.multiagentide.propagation.service.PropagatorRegistrationService;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentide.repository.WorktreeRepository;
import com.hayden.multiagentide.service.GitWorktreeService;
import com.hayden.multiagentide.transformation.repository.TransformationRecordRepository;
import com.hayden.multiagentide.transformation.repository.TransformerRegistrationRepository;
import com.hayden.multiagentide.agent.BlackboardHistory;
import com.hayden.multiagentide.llm.LlmRunner;
import com.hayden.multiagentide.service.WorktreeAutoCommitService;
import com.hayden.multiagentide.support.AgentTestBase;
import com.hayden.multiagentide.support.QueuedLlmRunner;
import com.hayden.multiagentide.support.TestEventListener;
import com.hayden.multiagentide.tool.McpToolObjectRegistrar;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.model.nodes.OrchestratorNode;
import com.hayden.multiagentide.model.worktree.MainWorktreeContext;
import com.hayden.multiagentide.model.worktree.SubmoduleWorktreeContext;
import com.hayden.multiagentide.model.worktree.WorktreeSandboxContext;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.function.BiConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Integration tests for the worktree merge flow through the full workflow agent pipeline.
 *
 * Unlike {@link WorkflowAgentQueuedTest} which mocks {@link GitWorktreeService}, this test
 * uses the REAL service to create actual git repositories, branch worktrees, and perform
 * real merges. It validates:
 * <ul>
 *   <li>Worktrees are created correctly by {@code attachWorktreesToDiscoveryRequests} etc.</li>
 *   <li>Correct worktree contexts propagate through {@code WorktreeContextRequestDecorator}</li>
 *   <li>Trunk→child merges in {@code WorktreeMergeResultDecorator}</li>
 *   <li>Child→trunk merges in {@code WorktreeMergeResultsDecorator}</li>
 *   <li>Final merge to source in {@code OrchestratorCollectorRequestDecorator}</li>
 * </ul>
 *
 * QueuedLlmRunner callbacks simulate agent work by committing files to worktrees.
 */
@Slf4j
@SpringBootTest(properties = "multiagentide.worktrees.base-path=${java.io.tmpdir}/multi-agent-ide-merge-test-worktrees")
@ActiveProfiles({"test", "testdocker"})
class WorkflowAgentWorktreeMergeIntTest extends AgentTestBase {

    private static final String WORKTREE_BASE =
            System.getProperty("java.io.tmpdir") + "/multi-agent-ide-merge-test-worktrees";

    @Autowired
    private AgentPlatform agentPlatform;

    @Autowired
    private QueuedLlmRunner queuedLlmRunner;

    @Autowired
    private GraphRepository graphRepository;

    @Autowired
    private WorktreeRepository worktreeRepository;



    @Autowired
    private GitWorktreeService gitWorktreeService;

    @Autowired
    private TestEventListener testEventListener;

    @Autowired
    private PermissionGate permissionGate;

    @Autowired
    private ArtifactRepository artifactRepository;

    @Autowired
    private LayerRepository layerRepository;
    @Autowired
    private PolicyRegistrationRepository policyRegistrationRepository;
    @Autowired
    private FilterDecisionRecordRepository filterDecisionRecordRepository;
    @Autowired
    private PropagatorRegistrationRepository propagatorRegistrationRepository;
    @Autowired
    private PropagationItemRepository propagationItemRepository;
    @Autowired
    private PropagationRecordRepository propagationRecordRepository;
    @Autowired
    private AutoAiPropagatorBootstrap autoAiPropagatorBootstrap;
    @Autowired
    private LayerHierarchyBootstrap layerHierarchyBootstrap;
    @Autowired
    private TransformationRecordRepository transformationRecordRepository;
    @Autowired
    private TransformerRegistrationRepository transformerRegistrationRepository;
    @MockitoSpyBean
    private EventBus eventBus;

    @MockitoSpyBean
    private ArtifactEventListener artifactEventListener;

    @MockitoSpyBean
    private ExecutionScopeService executionScopeService;

    @MockitoSpyBean
    private AgentInterfaces.WorkflowAgent workflowAgent;

    @MockitoSpyBean
    private AgentInterfaces.WorkflowAgent.DiscoveryDispatchSubagent discoveryDispatchSubagent;

    @MockitoSpyBean
    private AgentInterfaces.WorkflowAgent.PlanningDispatchSubagent planningDispatchSubagent;

    @MockitoSpyBean
    private AgentInterfaces.WorkflowAgent.TicketDispatchSubagent ticketDispatchSubagent;

    @MockitoSpyBean
    private WorkflowGraphService workflowGraphService;

    @MockitoSpyBean
    private ComputationGraphOrchestrator computationGraphOrchestrator;

    @MockitoSpyBean
    private WorktreeAutoCommitService worktreeAutoCommitService;

    @MockitoBean
    private McpToolObjectRegistrar embabelToolObjectRegistry;

    private static final Path TEST_WORK_DIR = Path.of("test_work/worktree_merge");

    private Path sourceRepo;
    private Path submoduleRepo;
    @Autowired
    private PropagatorRegistrationService propagatorRegistrationService;

    @TestConfiguration
    static class TestConfig {
        @Bean
        @Primary
        LlmRunner llmRunner() {
            return new QueuedLlmRunner();
        }

        @Bean
        QueuedLlmRunner queuedLlmRunner(LlmRunner llmRunner) {
            return (QueuedLlmRunner) llmRunner;
        }

        @Bean
        TestEventListener testEventListener() {
            return new TestEventListener();
        }

        @Bean
        ActionQosProvider manager() {
            return new ActionQosProvider() {
                @Override
                public @NonNull ActionQos provideActionQos(@NonNull Method method, @NonNull Object instance) {
                    return new ActionQos(1, 50, 5, 60000, false);
                }
            };
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        testEventListener.clear();
        queuedLlmRunner.clear();
        graphRepository.clear();
        worktreeRepository.clear();
        propagationRecordRepository.deleteAll();
        propagationItemRepository.deleteAll();
        propagatorRegistrationRepository.deleteAll();
        layerRepository.deleteAll();
        layerHierarchyBootstrap.seedLayersIfAbsent();
        autoAiPropagatorBootstrap.seedAutoAiPropagators();
        for (PropagatorRegistrationEntity entity : propagatorRegistrationRepository.findAll()) {
            propagatorRegistrationService.enableAiPropagator(entity);
        }

        Mockito.when(embabelToolObjectRegistry.tool(anyString()))
                        .thenReturn(Optional.empty());
        reset(
                workflowAgent,
                discoveryDispatchSubagent,
                planningDispatchSubagent,
                ticketDispatchSubagent,
                workflowGraphService,
                computationGraphOrchestrator,
                eventBus,
                worktreeAutoCommitService
        );

        artifactRepository.deleteAll();
        artifactRepository.flush();

        deleteRecursively(Path.of(WORKTREE_BASE));

        // Create real git repos
        submoduleRepo = createRepoWithFile("test-submodule", "lib.txt", "initial lib content", "init submodule");
        sourceRepo = createRepoWithFile("test-main", "README.md", "initial readme", "init main");
        addSubmodule(sourceRepo, submoduleRepo, "libs/test-sub");
    }

    @AfterEach
    void tearDown() throws Exception {
        deleteRecursively(Path.of(WORKTREE_BASE));
    }

    private void setLogFile(String testName) {
        Path file = TEST_WORK_DIR.resolve(testName + ".md");
        Path historyFile = TEST_WORK_DIR.resolve(testName + ".blackboard.md");
        Path graphFile = TEST_WORK_DIR.resolve(testName + ".graph.md");
        Path eventFile = TEST_WORK_DIR.resolve(testName + ".events.md");
        try {
            Files.createDirectories(TEST_WORK_DIR);
            Files.deleteIfExists(file);
            Files.deleteIfExists(historyFile);
            Files.deleteIfExists(graphFile);
            Files.deleteIfExists(eventFile);
        } catch (Exception e) {
            log.warn("Failed to clean log file: {}", file, e);
        }
        queuedLlmRunner.setLogFile(file);
        queuedLlmRunner.setBlackboardHistoryLogFile(historyFile);
        queuedLlmRunner.setTestClassName(WorkflowAgentWorktreeMergeIntTest.class.getSimpleName());
        queuedLlmRunner.setTestMethodName(testName);

        // Set up graph snapshot + event stream tracing
        var traceWriter = new com.hayden.multiagentide.support.TestTraceWriter();
        traceWriter.setGraphLogFile(graphFile);
        traceWriter.setEventLogFile(eventFile);
        traceWriter.setTestClassName(WorkflowAgentWorktreeMergeIntTest.class.getSimpleName());
        traceWriter.setTestMethodName(testName);
        queuedLlmRunner.setTraceWriter(traceWriter);
        queuedLlmRunner.setGraphRepository(graphRepository);
        testEventListener.setTraceWriter(traceWriter);
    }

    // ========================================================================
    // Test scenarios
    // ========================================================================

    @Nested
    class HappyPathMerges {

        @Test
        @DisplayName("Full workflow with real merges — agent changes reach source repo")
        void fullWorkflow_realMerges_changesReachSource() throws Exception {
            setLogFile("fullWorkflow_realMerges_changesReachSource");
            var contextId = seedOrchestratorWithRealWorktree();
            queuedLlmRunner.setThread(contextId.value());

            enqueueHappyPathWithWork("Implement feature");

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId.value()).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(contextId, "Implement feature", "DISCOVERY"))
            );

            assertThat(output.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();

            // Resolve the worktree path from the GoalCompletedEvent
            Path worktreePath = resolveWorktreePathFromGoalCompleted();

            // Verify agent-committed files are in the shared worktree (not source repo — controller handles merge)
            assertThat(Files.readString(worktreePath.resolve("discovery-output.md")))
                    .contains("discovery findings");
            assertThat(Files.readString(worktreePath.resolve("planning-output.md")))
                    .contains("planning results");
            assertThat(Files.readString(worktreePath.resolve("ticket-output.md")))
                    .contains("ticket implementation");

            assertClean(worktreePath);

            // Verify GoalCompletedEvent was emitted with worktree path
            var goalCompleted = testEventListener.eventsOfType(Events.GoalCompletedEvent.class);
            assertThat(goalCompleted).hasSize(1);
            assertThat(goalCompleted.getFirst().worktreePath()).isNotNull();
        }

        @Test
        @DisplayName("Full workflow with submodule changes — propagate to source")
        void fullWorkflow_withSubmoduleChanges_propagateToSource() throws Exception {
            setLogFile("fullWorkflow_withSubmoduleChanges_propagateToSource");

            var contextId = seedOrchestratorWithRealWorktree();
            queuedLlmRunner.setThread(contextId.value());

            enqueueHappyPathWithSubmoduleWork("Implement feature with lib changes");

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId.value()).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(contextId, "Implement feature with lib changes", "DISCOVERY"))
            );

            assertThat(output.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();

            // Resolve the worktree path — changes live there, not in source repo
            Path worktreePath = resolveWorktreePathFromGoalCompleted();

            // Verify main worktree changes
            assertThat(Files.readString(worktreePath.resolve("discovery-output.md")))
                    .contains("discovery findings");

            // Verify submodule changes are in the worktree's submodule
            Path worktreeSubPath = worktreePath.resolve("libs/test-sub");
            if (Files.exists(worktreeSubPath)) {
                assertThat(Files.readString(worktreeSubPath.resolve("lib.txt")))
                        .contains("updated by discovery agent");
            }

            assertClean(worktreePath);

            // Verify GoalCompletedEvent carries the worktree path
            var goalCompleted = testEventListener.eventsOfType(Events.GoalCompletedEvent.class);
            assertThat(goalCompleted).hasSize(1);
            assertThat(goalCompleted.getFirst().worktreePath()).isNotNull();
        }
    }

    @Nested
    class ParallelAgentMerges {

        @Test
        @DisplayName("Two discovery agents editing different files — both merge successfully")
        void discoveryPhase_twoAgents_bothMergeSuccessfully() throws Exception {
            setLogFile("discoveryPhase_twoAgents_bothMergeSuccessfully");
            var contextId = seedOrchestratorWithRealWorktree();

            enqueueParallelDiscoveryWithDistinctFiles("Discover features");
            queuedLlmRunner.setThread(contextId.value());

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId.value()).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(contextId, "Discover features", "DISCOVERY"))
            );

            assertThat(output.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();

            // Resolve the worktree path — both agents share the same worktree
            Path worktreePath = resolveWorktreePathFromGoalCompleted();

            // Verify both agent files are in the shared worktree
            assertThat(Files.readString(worktreePath.resolve("agent1-findings.md")))
                    .contains("agent 1 findings");
            assertThat(Files.readString(worktreePath.resolve("agent2-findings.md")))
                    .contains("agent 2 findings");

            assertClean(worktreePath);

            // Verify GoalCompletedEvent carries the worktree path
            var goalCompleted = testEventListener.eventsOfType(Events.GoalCompletedEvent.class);
            assertThat(goalCompleted).hasSize(1);
            assertThat(goalCompleted.getFirst().worktreePath()).isNotNull();
        }

        @Test
        @DisplayName("Two discovery agents editing same file — last write wins in shared worktree")
        void discoveryPhase_twoAgents_lastWriteWins() throws Exception {
            setLogFile("discoveryPhase_twoAgents_lastWriteWins");
            var contextId = seedOrchestratorWithRealWorktree();
            queuedLlmRunner.setThread(contextId.value());

            enqueueParallelDiscoveryWithConflict("Discover features");

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId.value()).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(contextId, "Discover features", "DISCOVERY"))
            );

            assertThat(output.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();

            // With a single shared worktree, both agents write to the same file sequentially.
            // The second agent's commit overwrites the first — no merge conflict, last write wins.
            Path worktreePath = resolveWorktreePathFromGoalCompleted();
            assertThat(Files.readString(worktreePath.resolve("shared-findings.md")))
                    .contains("Agent 2 wrote completely different content");

            // No merge events — merging is removed in single-worktree model
            var mergeCompleted = testEventListener.eventsOfType(Events.MergePhaseCompletedEvent.class);
            assertThat(mergeCompleted).isEmpty();

            assertClean(worktreePath);
        }
    }

    @Nested
    class SharedWorktreeAcrossTickets {

        @Test
        @DisplayName("Five ticket agents each commit a file — all five present in shared worktree")
        void ticketPhase_fiveTickets_sharedWorktreeCarriesAllChanges() throws Exception {
            setLogFile("ticketPhase_fiveTickets_sharedWorktreeCarriesAllChanges");
            var contextId = seedOrchestratorWithRealWorktree();
            queuedLlmRunner.setThread(contextId.value());

            // Discovery + planning with minimal work, then 5-ticket dispatch
            initialOrchestratorToDiscovery("Multi-ticket test");
            discoveryOnlyWithWork("Multi-ticket test", "discovery-output.md", "# Discovery\n\ndiscovery findings");
            planningOnlyWithWork("Multi-ticket test", "planning-output.md", "# Planning\n\nplanning results");
            ticketsWithFiveAgents("Multi-ticket test");
            finalOrchestratorCollector();

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId.value()).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(contextId, "Multi-ticket test", "DISCOVERY"))
            );

            assertThat(output.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();

            Path worktreePath = resolveWorktreePathFromGoalCompleted();

            // All five ticket agent files must be present in the single shared worktree
            for (int i = 1; i <= 5; i++) {
                String fileName = "ticket-%d-output.md".formatted(i);
                assertThat(worktreePath.resolve(fileName))
                        .as("File from ticket agent %d should exist in shared worktree", i)
                        .exists();
                assertThat(Files.readString(worktreePath.resolve(fileName)))
                        .contains("ticket %d implementation".formatted(i));
            }

            assertClean(worktreePath);

            var goalCompleted = testEventListener.eventsOfType(Events.GoalCompletedEvent.class);
            assertThat(goalCompleted).hasSize(1);
            assertThat(goalCompleted.getFirst().worktreePath()).isNotNull();
        }
    }

    @Nested
    class DataLayerNodeAndChatKeyValidation {

        @Test
        @DisplayName("DataLayerOperationNodes created for propagator calls during workflow")
        void dataLayerOperationNodes_createdDuringWorkflow() throws Exception {
            setLogFile("dataLayerOperationNodes_createdDuringWorkflow");
            var contextId = seedOrchestratorWithRealWorktree();
            queuedLlmRunner.setThread(contextId.value());

            enqueueHappyPathWithWork("DataLayer test");

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId.value()).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(contextId, "DataLayer test", "DISCOVERY"))
            );

            assertThat(output.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();

            // Verify DataLayerOperationNode entries were created in the graph
            verify(workflowGraphService, atLeastOnce()).startDataLayerOperation(any(), anyString(), any());

            // Check that DataLayerOperationNode instances exist in the graph
            var dataLayerNodes = graphRepository.findAll().stream()
                    .filter(n -> n instanceof DataLayerOperationNode)
                    .map(n -> (DataLayerOperationNode) n)
                    .toList();
            assertThat(dataLayerNodes).isNotEmpty();

            // Each data layer node should have a valid operation type
            for (var node : dataLayerNodes) {
                assertThat(node.operationType()).isNotBlank();
                assertThat(node.nodeId()).isNotBlank();
            }
        }

        @Test
        @DisplayName("AiPropagator DataLayerOperationNodes have chatSessionKey set to parent agent key")
        void propagator_chatSessionKey_setCorrectly() throws Exception {
            setLogFile("propagator_chatSessionKey_setCorrectly");
            var contextId = seedOrchestratorWithRealWorktree();
            queuedLlmRunner.setThread(contextId.value());

            enqueueHappyPathWithWork("PropagatorChatKey test");

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId.value()).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(contextId, "PropagatorChatKey test", "DISCOVERY"))
            );

            assertThat(output.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();

            // AiPropagator data layer nodes should exist with chatSessionKey set
            var propagatorNodes = graphRepository.findAll().stream()
                    .filter(n -> n instanceof DataLayerOperationNode dln
                            && "AiPropagator".equals(dln.operationType()))
                    .map(n -> (DataLayerOperationNode) n)
                    .toList();

            assertThat(propagatorNodes)
                    .as("AiPropagator DataLayerOperationNodes should be created during workflow")
                    .isNotEmpty();

            for (var node : propagatorNodes) {
                assertThat(node.chatId())
                        .as("AiPropagator DataLayerOperationNode should have chatSessionKey set (parent's key)")
                        .isNotNull();
                assertThat(node.chatId())
                        .as("chatSessionKey should differ from nodeId (routes to parent session)")
                        .isNotEqualTo(node.nodeId());
            }
        }

        @Test
        @DisplayName("Parallel agents — each propagator node tracks distinct chatSessionKey")
        void parallelAgents_propagatorNodes_distinctChatKeys() throws Exception {
            setLogFile("parallelAgents_propagatorNodes_distinctChatKeys");
            var contextId = seedOrchestratorWithRealWorktree();
            queuedLlmRunner.setThread(contextId.value());

            enqueueParallelDiscoveryWithDistinctFiles("Parallel chatKey test");

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId.value()).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(contextId, "Parallel chatKey test", "DISCOVERY"))
            );

            assertThat(output.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();

            var propagatorNodes = graphRepository.findAll().stream()
                    .filter(n -> n instanceof DataLayerOperationNode dln
                            && "AiPropagator".equals(dln.operationType()))
                    .map(n -> (DataLayerOperationNode) n)
                    .toList();

            assertThat(propagatorNodes).hasSizeGreaterThanOrEqualTo(2);

            // All propagator nodes should have chatSessionKey set
            for (var node : propagatorNodes) {
                assertThat(node.chatId()).isNotNull();
            }
        }
    }

    // ========================================================================
    // Orchestrator seeding
    // ========================================================================

    private ArtifactKey seedOrchestratorWithRealWorktree() {
        ArtifactKey contextId = ArtifactKey.createRoot();
        String nodeId = contextId.value();
        // Use a short suffix for the derived branch name (ULID is long)
        String branchSuffix = nodeId.substring(nodeId.length() - 8);
        MainWorktreeContext mainWorktree = gitWorktreeService.createMainWorktree(
                sourceRepo.toString(),
                "main",
                "derived-" + branchSuffix,
                nodeId
        );

        OrchestratorNode node = OrchestratorNode.builder()
                .nodeId(nodeId)
                .title("Test Orchestrator")
                .goal("Test goal")
                .status(Events.NodeStatus.RUNNING)
                .metadata(new HashMap<>())
                .createdAt(Instant.now())
                .worktreeContext(mainWorktree)
                .build();

        graphRepository.save(node);
        return contextId;
    }

    // ========================================================================
    // Enqueue helpers — happy path with agent work callbacks
    // ========================================================================

    private void enqueueHappyPathWithWork(String goal) {
        initialOrchestratorToDiscovery(goal);
        discoveryOnlyWithWork(goal, "discovery-output.md", "# Discovery\n\ndiscovery findings");
        planningOnlyWithWork(goal, "planning-output.md", "# Planning\n\nplanning results");
        ticketsOnlyWithWork(goal, "ticket-output.md", "# Ticket\n\nticket implementation");
        finalOrchestratorCollector();
    }

    private void enqueueHappyPathWithSubmoduleWork(String goal) {
        initialOrchestratorToDiscovery(goal);
        discoveryOnlyWithSubmoduleWork(goal);
        planningOnlyWithWork(goal, "planning-output.md", "# Planning\n\nplanning results");
        ticketsOnlyWithWork(goal, "ticket-output.md", "# Ticket\n\nticket implementation");
        finalOrchestratorCollector();
    }

    private void enqueueParallelDiscoveryWithDistinctFiles(String goal) {
        initialOrchestratorToDiscovery(goal);

        // Discovery orchestrator creates TWO agent requests
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(AgentModels.DiscoveryOrchestratorRouting.builder()
                .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                        .requests(List.of(
                                AgentModels.DiscoveryAgentRequest.builder()
                                        .goal(goal)
                                        .subdomainFocus("Area 1")
                                        .build(),
                                AgentModels.DiscoveryAgentRequest.builder()
                                        .goal(goal)
                                        .subdomainFocus("Area 2")
                                        .build()
                        ))
                        .build())
                .build());
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());

        // Agent 1 result — commits a unique file
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(
                AgentModels.DiscoveryAgentRouting.builder()
                        .agentResult(AgentModels.DiscoveryAgentResult.builder()
                                .output("Agent 1 findings")
                                .build())
                        .build(),
                (BiConsumer<AgentModels.DiscoveryAgentRouting, OperationContext>)
                        (response, ctx) -> simulateAgentWork(ctx, "agent1-findings.md", "agent 1 findings")
        );

        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());

        // Agent 2 result — commits a different file
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(
                AgentModels.DiscoveryAgentRouting.builder()
                        .agentResult(AgentModels.DiscoveryAgentResult.builder()
                                .output("Agent 2 findings")
                                .build())
                        .build(),
                (BiConsumer<AgentModels.DiscoveryAgentRouting, OperationContext>)
                        (response, ctx) -> simulateAgentWork(ctx, "agent2-findings.md", "agent 2 findings")
        );
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());

        // Dispatch routing → collector
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentDispatchRouting.builder()
                .collectorRequest(AgentModels.DiscoveryCollectorRequest.builder()
                        .goal(goal)
                        .discoveryResults("discovery-results")
                        .build())
                .build());
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());

        // Collector advances to planning
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(AgentModels.DiscoveryCollectorRouting.builder()
                .collectorResult(AgentModels.DiscoveryCollectorResult.builder()
                        .consolidatedOutput("Discovery complete")
                        .build())
                .build());
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());

        planningOnlyWithWork(goal, "planning-output.md", "# Planning\n\nplanning results");
        ticketsOnlyWithWork(goal, "ticket-output.md", "# Ticket\n\nticket implementation");
        finalOrchestratorCollector();
    }

    private void enqueueParallelDiscoveryWithConflict(String goal) {
        initialOrchestratorToDiscovery(goal);

        // Discovery orchestrator creates TWO agent requests
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(AgentModels.DiscoveryOrchestratorRouting.builder()
                .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                        .requests(List.of(
                                AgentModels.DiscoveryAgentRequest.builder()
                                        .goal(goal)
                                        .subdomainFocus("Area 1")
                                        .build(),
                                AgentModels.DiscoveryAgentRequest.builder()
                                        .goal(goal)
                                        .subdomainFocus("Area 2")
                                        .build()
                        ))
                        .build())
                .build());
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());

        // Agent 1 — writes to shared file
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(
                AgentModels.DiscoveryAgentRouting.builder()
                        .agentResult(AgentModels.DiscoveryAgentResult.builder()
                                .output("Agent 1 findings")
                                .build())
                        .build(),
                (BiConsumer<AgentModels.DiscoveryAgentRouting, OperationContext>)
                        (response, ctx) -> simulateAgentWork(ctx, "shared-findings.md", "Agent 1 wrote this content")
        );

        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());

        // Agent 2 — overwrites same file in shared worktree
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(
                AgentModels.DiscoveryAgentRouting.builder()
                        .agentResult(AgentModels.DiscoveryAgentResult.builder()
                                .output("Agent 2 findings")
                                .build())
                        .build(),
                (BiConsumer<AgentModels.DiscoveryAgentRouting, OperationContext>)
                        (response, ctx) -> simulateAgentWork(ctx, "shared-findings.md", "Agent 2 wrote completely different content")
        );
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());

        // Dispatch routing → collector
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentDispatchRouting.builder()
                .collectorRequest(AgentModels.DiscoveryCollectorRequest.builder()
                        .goal(goal)
                        .discoveryResults("discovery-results")
                        .build())
                .build());
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());

        // Collector advances
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(AgentModels.DiscoveryCollectorRouting.builder()
                .collectorResult(AgentModels.DiscoveryCollectorResult.builder()
                        .consolidatedOutput("Discovery complete")
                        .build())
                .build());
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());

        planningOnlyWithWork(goal, "planning-output.md", "# Planning\n\nplanning results");
        ticketsOnlyWithWork(goal, "ticket-output.md", "# Ticket\n\nticket implementation");
        finalOrchestratorCollector();
    }

    // ========================================================================
    // Phase-level enqueue helpers
    // ========================================================================

    private void initialOrchestratorToDiscovery(String goal) {
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(AgentModels.OrchestratorRouting.builder()
                .discoveryOrchestratorRequest(AgentModels.DiscoveryOrchestratorRequest.builder()
                        .goal(goal)
                        .build())
                .build());
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
    }

    private void discoveryOnlyWithWork(String goal, String fileName, String content) {
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(AgentModels.DiscoveryOrchestratorRouting.builder()
                .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                        .requests(List.of(
                                AgentModels.DiscoveryAgentRequest.builder()
                                        .goal(goal)
                                        .subdomainFocus("Primary")
                                        .build()
                        ))
                        .build())
                .build());
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());

        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(
                AgentModels.DiscoveryAgentRouting.builder()
                        .agentResult(AgentModels.DiscoveryAgentResult.builder()
                                .output("Found stuff")
                                .build())
                        .build(),
                (BiConsumer<AgentModels.DiscoveryAgentRouting, OperationContext>)
                        (response, ctx) -> simulateAgentWork(ctx, fileName, content)
        );

        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());

        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentDispatchRouting.builder()
                .collectorRequest(AgentModels.DiscoveryCollectorRequest.builder()
                        .goal(goal)
                        .discoveryResults("discovery-results")
                        .build())
                .build());
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());

        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(AgentModels.DiscoveryCollectorRouting.builder()
                .collectorResult(AgentModels.DiscoveryCollectorResult.builder()
                        .consolidatedOutput("Discovery complete")
                        .build())
                .build());
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
    }

    private void discoveryOnlyWithSubmoduleWork(String goal) {
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(AgentModels.DiscoveryOrchestratorRouting.builder()
                .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                        .requests(List.of(
                                AgentModels.DiscoveryAgentRequest.builder()
                                        .goal(goal)
                                        .subdomainFocus("Primary")
                                        .build()
                        ))
                        .build())
                .build());
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());

        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(
                AgentModels.DiscoveryAgentRouting.builder()
                        .agentResult(AgentModels.DiscoveryAgentResult.builder()
                                .output("Found stuff")
                                .build())
                        .build(),
                (BiConsumer<AgentModels.DiscoveryAgentRouting, OperationContext>)
                        (response, ctx) -> simulateAgentWorkWithSubmodule(ctx)
        );
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());


        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentDispatchRouting.builder()
                .collectorRequest(AgentModels.DiscoveryCollectorRequest.builder()
                        .goal(goal)
                        .discoveryResults("discovery-results")
                        .build())
                .build());
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());

        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(AgentModels.DiscoveryCollectorRouting.builder()
                .collectorResult(AgentModels.DiscoveryCollectorResult.builder()
                        .consolidatedOutput("Discovery complete")
                        .build())
                .build());
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
    }

    private void planningOnlyWithWork(String goal, String fileName, String content) {
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(AgentModels.PlanningOrchestratorRouting.builder()
                .agentRequests(AgentModels.PlanningAgentRequests.builder()
                        .requests(List.of(
                                AgentModels.PlanningAgentRequest.builder()
                                        .goal(goal)
                                        .build()
                        ))
                        .build())
                .build());
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());

        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());

        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(
                AgentModels.PlanningAgentRouting.builder()
                        .agentResult(AgentModels.PlanningAgentResult.builder()
                                .output("Plan output")
                                .build())
                        .build(),
                (BiConsumer<AgentModels.PlanningAgentRouting, OperationContext>)
                        (response, ctx) -> simulateAgentWork(ctx, fileName, content)
        );
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());

        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(AgentModels.PlanningAgentDispatchRouting.builder()
                .planningCollectorRequest(AgentModels.PlanningCollectorRequest.builder()
                        .goal(goal)
                        .planningResults("planning-results")
                        .build())
                .build());
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());

        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(AgentModels.PlanningCollectorRouting.builder()
                .collectorResult(AgentModels.PlanningCollectorResult.builder()
                        .consolidatedOutput("Planning complete")
                        .build())
                .build());
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
    }

    private void ticketsOnlyWithWork(String goal, String fileName, String content) {

        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(AgentModels.TicketOrchestratorRouting.builder()
                .agentRequests(AgentModels.TicketAgentRequests.builder()
                        .requests(List.of(
                                AgentModels.TicketAgentRequest.builder()
                                        .ticketDetails(goal)
                                        .ticketDetailsFilePath("ticket-1.md")
                                        .build()
                        ))
                        .build())
                .build());
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());

        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());

        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(
                AgentModels.TicketAgentRouting.builder()
                        .agentResult(AgentModels.TicketAgentResult.builder()
                                .output("Ticket output")
                                .build())
                        .build(),
                (BiConsumer<AgentModels.TicketAgentRouting, OperationContext>)
                        (response, ctx) -> simulateAgentWork(ctx, fileName, content)
        );
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());

        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(AgentModels.TicketAgentDispatchRouting.builder()
                .ticketCollectorRequest(AgentModels.TicketCollectorRequest.builder()
                        .goal(goal)
                        .ticketResults("ticket-results")
                        .build())
                .build());
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());

        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(AgentModels.TicketCollectorRouting.builder()
                .collectorResult(AgentModels.TicketCollectorResult.builder()
                        .consolidatedOutput("Tickets complete")
                        .build())
                .build());
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
    }

    private void ticketsWithFiveAgents(String goal) {
        // Ticket orchestrator dispatches 5 ticket agent requests
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(AgentModels.TicketOrchestratorRouting.builder()
                .agentRequests(AgentModels.TicketAgentRequests.builder()
                        .requests(List.of(
                                AgentModels.TicketAgentRequest.builder().ticketDetails(goal).ticketDetailsFilePath("ticket-1.md").build(),
                                AgentModels.TicketAgentRequest.builder().ticketDetails(goal).ticketDetailsFilePath("ticket-2.md").build(),
                                AgentModels.TicketAgentRequest.builder().ticketDetails(goal).ticketDetailsFilePath("ticket-3.md").build(),
                                AgentModels.TicketAgentRequest.builder().ticketDetails(goal).ticketDetailsFilePath("ticket-4.md").build(),
                                AgentModels.TicketAgentRequest.builder().ticketDetails(goal).ticketDetailsFilePath("ticket-5.md").build()
                        ))
                        .build())
                .build());
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());

        // Each ticket agent commits a unique file to the shared worktree
        for (int i = 1; i <= 5; i++) {
            String fileName = "ticket-%d-output.md".formatted(i);
            String content = "# Ticket %d\n\nticket %d implementation".formatted(i, i);

            queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
            if (i == 1) {
                // First agent gets an extra propagator result (matches single-ticket pattern)
                queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
            }
            queuedLlmRunner.enqueue(
                    AgentModels.TicketAgentRouting.builder()
                            .agentResult(AgentModels.TicketAgentResult.builder()
                                    .output("Ticket %d output".formatted(i))
                                    .build())
                            .build(),
                    (BiConsumer<AgentModels.TicketAgentRouting, OperationContext>)
                            (response, ctx) -> simulateAgentWork(ctx, fileName, content)
            );
            queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        }

        // Dispatch routing → collector
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(AgentModels.TicketAgentDispatchRouting.builder()
                .ticketCollectorRequest(AgentModels.TicketCollectorRequest.builder()
                        .goal(goal)
                        .ticketResults("ticket-results")
                        .build())
                .build());
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());

        // Collector completes
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(AgentModels.TicketCollectorRouting.builder()
                .collectorResult(AgentModels.TicketCollectorResult.builder()
                        .consolidatedOutput("All 5 tickets complete")
                        .build())
                .build());
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
    }

    private void finalOrchestratorCollector() {
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
        queuedLlmRunner.enqueue(AgentModels.OrchestratorCollectorRouting.builder()
                .collectorResult(AgentModels.OrchestratorCollectorResult.builder()
                        .consolidatedOutput("Workflow complete")
                        .build())
                .build());
        queuedLlmRunner.enqueue(AgentModels.AiPropagatorResult.builder().build());
    }

    // ========================================================================
    // Callback helpers — simulate agent work
    // ========================================================================

    /**
     * Simulates agent work by committing a file to the agent's worktree.
     * Resolves the worktree from the OperationContext (the decorated request has worktree context).
     */
    @SneakyThrows
    private void simulateAgentWork(OperationContext context, String fileName, String content) {
        AgentModels.AgentRequest request = resolveCurrentRequest(context);
        if (request == null || request.worktreeContext() == null) {
            log.warn("Cannot simulate agent work: no worktree context on current request");
            return;
        }

        MainWorktreeContext wt = request.worktreeContext().mainWorktree();
        if (wt == null || wt.worktreePath() == null) {
            log.warn("Cannot simulate agent work: no main worktree path");
            return;
        }

        Path worktreePath = wt.worktreePath();
        configureUser(worktreePath);
        commitFile(worktreePath, fileName, content, "Agent work: " + fileName);
        log.info("Simulated agent work: committed {} to {}", fileName, worktreePath);
    }

    /**
     * Simulates agent work that modifies both the main repo and a submodule.
     */
    @SneakyThrows
    private void simulateAgentWorkWithSubmodule(OperationContext context) {
        AgentModels.AgentRequest request = resolveCurrentRequest(context);
        if (request == null || request.worktreeContext() == null) {
            log.warn("Cannot simulate submodule work: no worktree context");
            return;
        }

        WorktreeSandboxContext sandbox = request.worktreeContext();
        MainWorktreeContext mainWt = sandbox.mainWorktree();
        if (mainWt == null || mainWt.worktreePath() == null) {
            return;
        }

        // Commit to main repo
        Path mainPath = mainWt.worktreePath();
        configureUser(mainPath);
        commitFile(mainPath, "discovery-output.md", "# Discovery\n\ndiscovery findings", "Agent: discovery output");

        // Commit to submodule if available
        if (sandbox.submoduleWorktrees() != null && !sandbox.submoduleWorktrees().isEmpty()) {
            SubmoduleWorktreeContext subWt = sandbox.submoduleWorktrees().getFirst();
            if (subWt.worktreePath() != null) {
                Path subPath = subWt.worktreePath();
                configureUser(subPath);
                commitFile(subPath, "lib.txt", "updated by discovery agent", "Agent: update lib");

                // Update submodule pointer in main
                try {
                    runGit(mainPath, "git", "add", subWt.submoduleName());
                    runGit(mainPath, "git", "commit", "-m", "Update submodule pointer");
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }

        log.info("Simulated agent work with submodule changes in {}", mainPath);
    }

    private AgentModels.AgentRequest resolveCurrentRequest(OperationContext context) {
        if (context == null) {
            return null;
        }
        // The decorated request should be on the process context
        AgentModels.AgentRequest request = context.last(AgentModels.AgentRequest.class);
        // Fallback: try blackboard history

        var history = BlackboardHistory.getEntireBlackboardHistory(context);
        return BlackboardHistory.findLastWorkflowRequest(history);
    }

    // ========================================================================
    // Worktree path resolution from GoalCompletedEvent
    // ========================================================================

    private Path resolveWorktreePathFromGoalCompleted() {
        var goalCompleted = testEventListener.eventsOfType(Events.GoalCompletedEvent.class);
        assertThat(goalCompleted).as("GoalCompletedEvent should have been emitted").isNotEmpty();
        String worktreePathStr = goalCompleted.getFirst().worktreePath();
        assertThat(worktreePathStr).as("GoalCompletedEvent should carry worktree path").isNotNull();
        return Path.of(worktreePathStr);
    }

    // ========================================================================
    // Workflow agent finder
    // ========================================================================

    private com.embabel.agent.core.Agent findWorkflowAgent() {
        return agentPlatform.agents().stream()
                .filter(a -> a.getName().equals(AgentInterfaces.WORKFLOW_AGENT_NAME)
                        || a.getName().contains(AgentInterfaces.WorkflowAgent.class.getSimpleName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("WorkflowAgent not found"));
    }

    // ========================================================================
    // Git helper methods (from GitWorktreeServiceIntTest pattern)
    // ========================================================================

    private Path createRepoWithFile(String prefix, String fileName, String content, String message) throws Exception {
        Path repoDir = Files.createTempDirectory(prefix);
        initRepo(repoDir);
        commitFile(repoDir, fileName, content, message);
        return repoDir;
    }

    private void addSubmodule(Path mainRepo, Path subRepo, String submodulePath) throws Exception {
        runGit(mainRepo, "git", "-c", "protocol.file.allow=always",
                "submodule", "add", subRepo.toString(), submodulePath);
        commitAll(mainRepo, "add submodule");
    }

    private void initSubmodules(Path repoDir) throws Exception {
        runGit(repoDir, "git", "-c", "protocol.file.allow=always",
                "submodule", "update", "--init", "--recursive");
        runGit(repoDir, "git", "submodule", "foreach", "--recursive", "git", "switch", "main");
    }

    private void initRepo(Path repoDir) throws Exception {
        runGit(repoDir, "git", "init", "-b", "main");
        configureUser(repoDir);
    }

    private void configureUser(Path repoDir) throws Exception {
        runGit(repoDir, "git", "config", "user.email", "test@example.com");
        runGit(repoDir, "git", "config", "user.name", "Test User");
    }

    private void commitFile(Path repoDir, String fileName, String content, String message) throws Exception {
        Path file = repoDir.resolve(fileName);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        commitAll(repoDir, message);
    }

    private void commitAll(Path repoDir, String message) throws Exception {
        runGit(repoDir, "git", "add", ".");
        runGit(repoDir, "git", "commit", "-m", message);
    }

    private void runGit(Path repoDir, String... command) throws Exception {
        var o = gitOutput(repoDir, command);
        log.info("{}", o);
    }

    private void assertClean(Path repoDir) throws Exception {
        String status = gitOutput(repoDir, "git", "status", "--porcelain").trim();
        assertThat(status).isEmpty();
    }

    private String gitSubmodulePointer(Path repoDir, String submodulePath) throws Exception {
        return gitOutput(repoDir, "git", "rev-parse", "HEAD:" + submodulePath).trim();
    }

    private int gitRevisionCountForPath(Path repoDir, String filePath) throws Exception {
        String count = gitOutput(repoDir, "git", "rev-list", "--count", "HEAD", "--", filePath).trim();
        return Integer.parseInt(count);
    }

    private String gitOutput(Path repoDir, String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(repoDir.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }
        int exit = process.waitFor();
        if (exit != 0) {
            throw new IllegalStateException("Git command failed: " + String.join(" ", command) + "\n" + output);
        }
        return output.toString();
    }

    private void deleteRecursively(Path path) throws Exception {
        if (!Files.exists(path)) {
            return;
        }
        if (Files.isDirectory(path)) {
            try (var stream = Files.list(path)) {
                for (Path child : stream.toList()) {
                    deleteRecursively(child);
                }
            }
        }
        Files.deleteIfExists(path);
    }

}
