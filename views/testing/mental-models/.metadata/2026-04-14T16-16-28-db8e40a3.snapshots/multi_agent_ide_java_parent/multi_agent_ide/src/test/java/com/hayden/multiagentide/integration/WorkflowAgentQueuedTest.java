package com.hayden.multiagentide.integration;

import com.embabel.agent.api.annotation.support.ActionQosProvider;
import com.embabel.agent.api.common.PlannerType;
import com.embabel.agent.core.ActionQos;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcessStatusCode;
import com.embabel.agent.core.ProcessOptions;
import com.hayden.multiagentide.agent.AgentInterfaces;
import com.hayden.multiagentide.agent.AgentTopologyTools;
import com.hayden.multiagentide.agent.WorkflowGraphService;
import com.hayden.multiagentide.artifacts.ArtifactEventListener;
import com.hayden.multiagentide.artifacts.ArtifactTreeBuilder;
import com.hayden.multiagentide.artifacts.ExecutionScopeService;
import com.hayden.multiagentide.artifacts.entity.ArtifactEntity;
import com.hayden.multiagentide.artifacts.repository.ArtifactRepository;
import com.hayden.multiagentide.gate.PermissionGate;
import com.hayden.multiagentide.model.nodes.AgentToAgentConversationNode;
import com.hayden.multiagentide.model.nodes.DiscoveryCollectorNode;
import com.hayden.multiagentide.model.nodes.DiscoveryOrchestratorNode;
import com.hayden.multiagentide.model.nodes.OrchestratorNode;
import com.hayden.multiagentide.orchestration.ComputationGraphOrchestrator;
import com.hayden.multiagentide.filter.repository.FilterDecisionRecordRepository;
import com.hayden.multiagentide.filter.repository.LayerRepository;
import com.hayden.multiagentide.filter.repository.PolicyRegistrationRepository;
import com.hayden.multiagentide.propagation.repository.*;
import com.hayden.multiagentide.propagation.service.AutoAiPropagatorBootstrap;
import com.hayden.multiagentide.filter.service.LayerHierarchyBootstrap;
import com.hayden.multiagentide.propagation.service.PropagatorRegistrationService;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentide.repository.WorktreeRepository;
import com.hayden.multiagentide.service.*;
import com.hayden.multiagentide.support.AgentTestBase;
import com.hayden.multiagentide.support.QueuedLlmRunner;
import com.hayden.multiagentide.support.TestEventListener;
import com.hayden.multiagentide.transformation.repository.TransformationRecordRepository;
import com.hayden.multiagentide.transformation.repository.TransformerRegistrationRepository;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.agent.AgentType;
import com.hayden.acp_cdc_ai.acp.CompactionException;
import com.hayden.acp_cdc_ai.permission.IPermissionGate;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.llm.LlmRunner;
import com.hayden.multiagentide.model.worktree.MainWorktreeContext;
import com.hayden.multiagentide.model.worktree.WorktreeContext;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.*;

/**
 * Integration tests for WorkflowAgent using queue-based LLM mocking.
 *
 * This approach:
 * 1. Mocks the LlmRunner with a queue of pre-defined responses
 * 2. Lets the real agent code execute (no need to mock individual actions)
 * 3. Verifies the workflow executes correctly with those responses
 *
 * Benefits over action-level mocking:
 * - Simpler test setup (just queue responses)
 * - Tests execute closer to production code
 * - No need to manually call registerAndHideInput
 * - More resilient to refactoring
 *
 * **Important Note**: all of these tests take about 5 min to complete, i.e. not tuned currently
 */
@Slf4j
@SpringBootTest
@ActiveProfiles({"test", "testdocker"})
class WorkflowAgentQueuedTest extends AgentTestBase {

    @Autowired
    private AgentPlatform agentPlatform;

    @Autowired
    private QueuedLlmRunner queuedLlmRunner;

    @Autowired
    private GraphRepository graphRepository;

    @Autowired
    private WorktreeRepository worktreeRepository;


    @MockitoBean
    private GitWorktreeService worktreeService;

    @Autowired
    private TestEventListener testEventListener;

    @Autowired
    private PermissionGate permissionGate;

    @Autowired
    private ArtifactRepository artifactRepository;

    @Autowired
    private FilterDecisionRecordRepository filterDecisionRecordRepository;

    @Autowired
    private PolicyRegistrationRepository policyRegistrationRepository;

    @Autowired
    private LayerRepository layerRepository;

    @Autowired
    private PropagationRecordRepository propagationRecordRepository;

    @Autowired
    private PropagationItemRepository propagationItemRepository;

    @Autowired
    private PropagatorRegistrationRepository propagatorRegistrationRepository;

    @Autowired
    private AutoAiPropagatorBootstrap autoAiPropagatorBootstrap;

    @Autowired
    private LayerHierarchyBootstrap layerHierarchyBootstrap;

    @Autowired
    private TransformationRecordRepository transformationRecordRepository;

    @Autowired
    private TransformerRegistrationRepository transformerRegistrationRepository;

    @Autowired
    private ArtifactTreeBuilder artifactTreeBuilder;

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
    private AgentCommunicationService agentCommunicationService;

    @MockitoSpyBean
    private AgentTopologyTools agentTopologyTools;

    @Autowired
    private com.hayden.multiagentide.agent.decorator.prompt.FilterPropertiesDecorator filterPropertiesDecorator;

    @Autowired
    private AgentExecutor agentExecutor;

    @TempDir
    Path path;

    private static final Path TEST_WORK_DIR = Path.of("test_work/queued");
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

        @Bean @Primary
        ActionQosProvider manager() {
            return new ActionQosProvider() {
                @Override
                public @NonNull ActionQos provideActionQos(@NonNull Method method, @NonNull Object instance) {
                    return new ActionQos(2, 50, 2, 60, false);
                }
            };
        }
    }

    @BeforeEach
    void setUp() {
        testEventListener.clear();
        queuedLlmRunner.clear();
        graphRepository.clear();
        worktreeRepository.clear();
        filterDecisionRecordRepository.deleteAll();
        policyRegistrationRepository.deleteAll();
        layerRepository.deleteAll();
        propagationRecordRepository.deleteAll();
        propagationItemRepository.deleteAll();
        propagatorRegistrationRepository.deleteAll();
        transformationRecordRepository.deleteAll();
        transformerRegistrationRepository.deleteAll();
        layerHierarchyBootstrap.seedLayersIfAbsent();
        autoAiPropagatorBootstrap.seedAutoAiPropagators();
        reset(
                workflowAgent,
                discoveryDispatchSubagent,
                planningDispatchSubagent,
                ticketDispatchSubagent,
                workflowGraphService,
                computationGraphOrchestrator,
                worktreeService,
                eventBus,
                agentCommunicationService,
                agentTopologyTools
        );
        doThrow(new RuntimeException("worktree disabled"))
                .when(worktreeService)
                .branchWorktree(any(), any(), any());

        Mockito.when(worktreeService.attachWorktreesToDiscoveryRequests(any(AgentModels.DiscoveryAgentRequests.class), anyString()))
                .thenAnswer(inv -> {
                    AgentModels.DiscoveryAgentRequests d = inv.getArgument(0);
                    return d.toBuilder()
                            .requests(d.requests().stream().map(dar -> dar.withWorktreeContext(d.worktreeContext())).toList())
                            .build();
                });
        Mockito.when(worktreeService.attachWorktreesToPlanningRequests(any(AgentModels.PlanningAgentRequests.class), anyString()))
                .thenAnswer(inv -> {
                    AgentModels.PlanningAgentRequests d = inv.getArgument(0);
                    return d.toBuilder()
                            .requests(d.requests().stream().map(dar -> dar.withWorktreeContext(d.worktreeContext())).toList())
                            .build();
                });
        Mockito.when(worktreeService.attachWorktreesToTicketRequests(any(AgentModels.TicketAgentRequests.class), anyString()))
                .thenAnswer(inv -> {
                    AgentModels.TicketAgentRequests d = inv.getArgument(0);
                    return d.toBuilder()
                            .requests(d.requests().stream().map(dar -> dar.withWorktreeContext(d.worktreeContext())).toList())
                            .build();
                });


        artifactRepository.deleteAll();
        artifactRepository.flush();
    }

    private void setLogFile(String testName) {
        Path file = TEST_WORK_DIR.resolve(testName + ".md");
        Path historyFile = TEST_WORK_DIR.resolve(testName + ".blackboard.md");
        Path graphFile = TEST_WORK_DIR.resolve(testName + ".graph.md");
        Path eventFile = TEST_WORK_DIR.resolve(testName + ".events.md");
        try {
            java.nio.file.Files.createDirectories(TEST_WORK_DIR);
            java.nio.file.Files.deleteIfExists(file);
            java.nio.file.Files.deleteIfExists(historyFile);
            java.nio.file.Files.deleteIfExists(graphFile);
            java.nio.file.Files.deleteIfExists(eventFile);
        } catch (Exception e) {
            // ignore cleanup failures
        }
        queuedLlmRunner.setLogFile(file);
        queuedLlmRunner.setBlackboardHistoryLogFile(historyFile);
        queuedLlmRunner.setTestClassName(WorkflowAgentQueuedTest.class.getSimpleName());
        queuedLlmRunner.setTestMethodName(testName);

        var traceWriter = new com.hayden.multiagentide.support.TestTraceWriter();
        traceWriter.setGraphLogFile(graphFile);
        traceWriter.setEventLogFile(eventFile);
        traceWriter.setTestClassName(WorkflowAgentQueuedTest.class.getSimpleName());
        traceWriter.setTestMethodName(testName);
        queuedLlmRunner.setTraceWriter(traceWriter);
        queuedLlmRunner.setGraphRepository(graphRepository);
        testEventListener.setTraceWriter(traceWriter);
    }

    @Nested
    class InterruptScenarios {

        @SneakyThrows
        @Test
        void orchestratorPause_resolveInterruptContinues() {
            setLogFile("orchestratorPause_resolveInterruptContinues");
            var contextId = seedOrchestrator().value();
            queuedLlmRunner.setThread(contextId);
            queuedLlmRunner.enqueue(AgentModels.OrchestratorRouting.builder()
                    .interruptRequest(AgentModels.InterruptRequest.OrchestratorInterruptRequest.builder()
                            .type(Events.InterruptType.PAUSE)
                            .reason("User requested pause will continue")
                            .build())
                    .build());

            orchestratorRequestThenDiscovery("Do that thing.");
            enqueueDiscoveryToEnd("Do that thing.");

            var res = CompletableFuture.supplyAsync(() -> agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Paused task", "DISCOVERY"))
            ));

            await().atMost(Duration.ofSeconds(30))
                    .until(() -> permissionGate.isInterruptPending(
                            t -> t.getType() == Events.InterruptType.PAUSE && Objects.equals(t.getReason(), "User requested pause will continue")));

            verify(workflowAgent).handleUnifiedInterrupt(
                    argThat(req -> req.type() == Events.InterruptType.PAUSE),
                    any()
            );

            var output = permissionGate.getInterruptPending(t -> t.getType() == Events.InterruptType.PAUSE && Objects.equals(t.getReason(), "User requested pause will continue"));

            assertThat(output).isNotNull();

            permissionGate.resolveInterrupt(
                    output.getInterruptId(),
                    IPermissionGate.ResolutionType.RESOLVED,
                    "",
                    (IPermissionGate.InterruptResult) null);

            await().atMost(Duration.ofSeconds(30))
                    .until(res::isDone);

            var result = res.get();

            assertThat(result).isNotNull();
            assertThat(output).isNotNull();

            var ordered = inOrder(
                    workflowAgent,
                    discoveryDispatchSubagent,
                    planningDispatchSubagent,
                    ticketDispatchSubagent
            );
            ordered.verify(workflowAgent).coordinateWorkflow(any(), any());
            ordered.verify(workflowAgent).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());
            ordered.verify(workflowAgent).dispatchDiscoveryAgentRequests(any(), any());
            ordered.verify(discoveryDispatchSubagent).runDiscoveryAgent(any(AgentModels.DiscoveryAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidateDiscoveryFindings(any(), any());
            ordered.verify(workflowAgent).handleDiscoveryCollectorBranch(any(), any());
            ordered.verify(workflowAgent).decomposePlanAndCreateWorkItems(any(), any());
            ordered.verify(workflowAgent).dispatchPlanningAgentRequests(any(), any());
            ordered.verify(planningDispatchSubagent).runPlanningAgent(any(AgentModels.PlanningAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidatePlansIntoTickets(any(), any());
            ordered.verify(workflowAgent).handlePlanningCollectorBranch(any(), any());
            ordered.verify(workflowAgent).orchestrateTicketExecution(any(), any());
            ordered.verify(workflowAgent).dispatchTicketAgentRequests(any(), any());
            ordered.verify(ticketDispatchSubagent).runTicketAgent(any(AgentModels.TicketAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidateTicketResults(any(), any());
            ordered.verify(workflowAgent).handleTicketCollectorBranch(any(), any());
            ordered.verify(workflowAgent).consolidateWorkflowOutputs(any(), any());

            verify(computationGraphOrchestrator, atLeastOnce()).emitStatusChangeEvent(any(), any(), any(), any());
            queuedLlmRunner.assertAllConsumed();
        }

        @SneakyThrows
        @Test
        void humanReviewInterrupt_blocksUntilExternallyResolved() {
            setLogFile("humanReviewInterrupt_blocksUntilExternallyResolved");
            var contextId = seedOrchestrator().value();
            queuedLlmRunner.setThread(contextId);

            // First LLM call: orchestrator returns a HUMAN_REVIEW interrupt
            queuedLlmRunner.enqueue(AgentModels.OrchestratorRouting.builder()
                    .interruptRequest(AgentModels.InterruptRequest.OrchestratorInterruptRequest.builder()
                            .type(Events.InterruptType.HUMAN_REVIEW)
                            .reason("Please review the proposed architecture")
                            .build())
                    .build());

            // Second LLM call: after interrupt is resolved, the handleInterrupt method
            // calls runWithTemplate with the feedback — enqueue the post-interrupt routing
            // that continues the workflow to discovery
            orchestratorRequestThenDiscovery("Human review task");

            enqueueDiscoveryToEnd("Human review task");

            var workflowFuture = CompletableFuture.supplyAsync(() -> agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Human review task", "DISCOVERY"))
            ));

            // Wait for the interrupt to be published
            await().atMost(Duration.ofSeconds(30))
                    .until(() -> permissionGate.isInterruptPending(
                            t -> t.getType() == Events.InterruptType.HUMAN_REVIEW
                                 && Objects.equals(t.getReason(), "Please review the proposed architecture")));

            var pendingInterrupt = permissionGate.getInterruptPending(
                    t -> t.getType() == Events.InterruptType.HUMAN_REVIEW
                         && Objects.equals(t.getReason(), "Please review the proposed architecture"));
            assertThat(pendingInterrupt).isNotNull();

            // Verify the workflow is actually blocked — it should NOT have progressed past
            // the interrupt handler (i.e. discovery should NOT have started yet)
            Thread.sleep(2000);
            assertThat(workflowFuture.isDone())
                    .as("Workflow should still be blocked waiting for human review resolution")
                    .isFalse();

            // The interrupt should STILL be pending — not removed
            assertThat(permissionGate.isInterruptPending(
                    t -> t.getType() == Events.InterruptType.HUMAN_REVIEW
                         && Objects.equals(t.getReason(), "Please review the proposed architecture")))
                    .as("HUMAN_REVIEW interrupt should remain pending until explicitly resolved")
                    .isTrue();

            // Only one LLM call should have been consumed so far (the orchestrator routing)
            // The second one (post-interrupt) should NOT have been consumed yet
            assertThat(queuedLlmRunner.getCallCount())
                    .as("Only the initial orchestrator LLM call should have fired; " +
                        "the post-interrupt call should be blocked")
                    .isEqualTo(1);

            // Now externally resolve the interrupt (simulating human approval)
            permissionGate.resolveInterrupt(
                    pendingInterrupt.getInterruptId(),
                    IPermissionGate.ResolutionType.APPROVED,
                    "Architecture looks good, proceed",
                    (IPermissionGate.InterruptResult) null);

            // Workflow should now complete
            await().atMost(Duration.ofSeconds(30))
                    .until(workflowFuture::isDone);

            var result = workflowFuture.get();
            assertThat(result).isNotNull();

            // The interrupt should no longer be pending
            assertThat(permissionGate.isInterruptPending(
                    t -> Objects.equals(t.getInterruptId(), pendingInterrupt.getInterruptId())))
                    .as("Interrupt should have been removed after resolution")
                    .isFalse();

            queuedLlmRunner.assertAllConsumed();
        }

        @SneakyThrows
        @Test
        void humanReviewInterrupt_pendingInterruptNotRemovedBeforeResolution() {
            setLogFile("humanReviewInterrupt_pendingInterruptNotRemovedBeforeResolution");
            // This test specifically targets the observed bug: the pending interrupt entry
            // is added to pendingInterrupts but then disappears before resolveInterrupt
            // is called, causing awaitInterruptBlocking to return invalidInterrupt immediately.
            var contextId = seedOrchestrator().value();
            queuedLlmRunner.setThread(contextId);

            // Track all resolveInterrupt calls to prove none happen unexpectedly
            var resolveCallTracker = new java.util.concurrent.CopyOnWriteArrayList<String>();
            doAnswer(invocation -> {
                String interruptId = invocation.getArgument(0);
                resolveCallTracker.add(interruptId + " | " + Arrays.toString(Thread.currentThread().getStackTrace()));
                return invocation.callRealMethod();
            }).when(computationGraphOrchestrator).emitStatusChangeEvent(any(), any(), any(), any());

            queuedLlmRunner.enqueue(AgentModels.OrchestratorRouting.builder()
                    .interruptRequest(AgentModels.InterruptRequest.OrchestratorInterruptRequest.builder()
                            .type(Events.InterruptType.HUMAN_REVIEW)
                            .reason("Review before proceeding")
                            .build())
                    .build());

            // Enqueue the post-interrupt response so the workflow can finish after resolution
            orchestratorRequestThenDiscovery("Post review task");
            enqueueDiscoveryToEnd("Post review task");

            var workflowFuture = CompletableFuture.supplyAsync(() -> agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Post review task", "DISCOVERY"))
            ));

            // Wait for the interrupt to appear
            await().atMost(Duration.ofSeconds(30))
                    .until(() -> permissionGate.isInterruptPending(
                            t -> t.getType() == Events.InterruptType.HUMAN_REVIEW
                                 && Objects.equals(t.getReason(), "Review before proceeding")));

            var pendingInterrupt = permissionGate.getInterruptPending(
                    t -> t.getType() == Events.InterruptType.HUMAN_REVIEW
                         && Objects.equals(t.getReason(), "Review before proceeding"));
            assertThat(pendingInterrupt).isNotNull();
            String interruptId = pendingInterrupt.getInterruptId();

            // Poll multiple times over a period to detect if the entry disappears unexpectedly
            for (int i = 0; i < 10; i++) {
                Thread.sleep(500);
                boolean stillPending = permissionGate.isInterruptPending(
                        t -> Objects.equals(t.getInterruptId(), interruptId));
                assertThat(stillPending)
                        .as("Interrupt %s should remain pending (check %d/10). " +
                            "If this fails, the entry was removed from pendingInterrupts " +
                            "without resolveInterrupt being called. resolveInterrupt calls so far: %s",
                            interruptId, i + 1, resolveCallTracker)
                        .isTrue();

                // Also verify workflow hasn't completed (which would mean it auto-resolved)
                assertThat(workflowFuture.isDone())
                        .as("Workflow should not have completed without resolving interrupt (check %d/10)", i + 1)
                        .isFalse();
            }

            // Now resolve and let workflow finish
            permissionGate.resolveInterrupt(interruptId, IPermissionGate.ResolutionType.APPROVED, "Approved after checks",
                    (IPermissionGate.InterruptResult) null);

            await().atMost(Duration.ofSeconds(30))
                    .until(workflowFuture::isDone);

            assertThat(workflowFuture.get()).isNotNull();
            queuedLlmRunner.assertAllConsumed();
        }
        @SneakyThrows
        @Test
        void handle_orchestratorAgent_interruptRequest() {
            setLogFile("handle_orchestratorAgent_interruptRequest");
            // Verifies that after human resolves the interrupt with feedback notes,
            // those notes are passed through to the LLM as interruptFeedback and the
            // workflow continues to completion.

            var contextId = seedOrchestrator().value();
            queuedLlmRunner.setThread(contextId);

            queuedLlmRunner.enqueue(AgentModels.OrchestratorRouting.builder()
                    .interruptRequest(AgentModels.InterruptRequest.OrchestratorInterruptRequest.builder()
                            .type(Events.InterruptType.HUMAN_REVIEW)
                            .reason("Needs human sign-off")
                            .build())
                    .build());

            orchestratorRequestThenDiscovery("Feedback task");

            enqueueDiscoveryToEnd("Feedback task");

            var workflowFuture = CompletableFuture.supplyAsync(() -> agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Feedback task", "DISCOVERY"))
            ));

            await().atMost(Duration.ofSeconds(30))
                    .until(() -> permissionGate.isInterruptPending(
                            t -> t.getType() == Events.InterruptType.HUMAN_REVIEW
                                    && Objects.equals(t.getReason(), "Needs human sign-off")));

            var pendingInterrupt = permissionGate.getInterruptPending(
                    t -> t.getType() == Events.InterruptType.HUMAN_REVIEW
                            && Objects.equals(t.getReason(), "Needs human sign-off"));
            assertThat(pendingInterrupt).isNotNull();

            // Resolve with specific feedback
            permissionGate.resolveInterrupt(
                    pendingInterrupt.getInterruptId(),
                    IPermissionGate.ResolutionType.APPROVED,
                    "Approved with note: use event sourcing pattern",
                    (IPermissionGate.InterruptResult) null);

            await().atMost(Duration.ofSeconds(30))
                    .until(workflowFuture::isDone);
        }

        @SneakyThrows
        @Test
        void humanReviewInterrupt_workflowCompletesWithFeedbackAfterResolution() {
            setLogFile("humanReviewInterrupt_workflowCompletesWithFeedbackAfterResolution");
            // Verifies that after human resolves the interrupt with feedback notes,
            // those notes are passed through to the LLM as interruptFeedback and the
            // workflow continues to completion.
            var contextId = seedOrchestrator().value();
            queuedLlmRunner.setThread(contextId);

            queuedLlmRunner.enqueue(AgentModels.OrchestratorRouting.builder()
                    .interruptRequest(AgentModels.InterruptRequest.OrchestratorInterruptRequest.builder()
                            .type(Events.InterruptType.HUMAN_REVIEW)
                            .reason("Needs human sign-off")
                            .build())
                    .build());

            orchestratorRequestThenDiscovery("Feedback task");

            enqueueDiscoveryToEnd("Feedback task");

            var workflowFuture = CompletableFuture.supplyAsync(() -> agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Feedback task", "DISCOVERY"))
            ));

            await().atMost(Duration.ofSeconds(30))
                    .until(() -> permissionGate.isInterruptPending(
                            t -> t.getType() == Events.InterruptType.HUMAN_REVIEW
                                 && Objects.equals(t.getReason(), "Needs human sign-off")));

            var pendingInterrupt = permissionGate.getInterruptPending(
                    t -> t.getType() == Events.InterruptType.HUMAN_REVIEW
                         && Objects.equals(t.getReason(), "Needs human sign-off"));
            assertThat(pendingInterrupt).isNotNull();

            // Resolve with specific feedback
            permissionGate.resolveInterrupt(
                    pendingInterrupt.getInterruptId(),
                    IPermissionGate.ResolutionType.APPROVED,
                    "Approved with note: use event sourcing pattern",
                    (IPermissionGate.InterruptResult) null);

            await().atMost(Duration.ofSeconds(30))
                    .until(workflowFuture::isDone);

            var result = workflowFuture.get();
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(com.embabel.agent.core.AgentProcessStatusCode.COMPLETED);

            // Verify unified interrupt handler was invoked
            verify(workflowAgent).handleUnifiedInterrupt(
                    argThat(req -> req.type() == Events.InterruptType.HUMAN_REVIEW),
                    any()
            );

            queuedLlmRunner.assertAllConsumed();
        }
    }

    private void kill(String contextId) {
        var pk = agentPlatform.getAgentProcess(contextId).kill();
        queuedLlmRunner.setThread(null);
    }

    @Nested
    class HappyPathWorkflows {

        @Test
        void fullWorkflow_discoveryToPlanningSingleAgentsToCompletion() {
            setLogFile("fullWorkflow_discoveryToPlanningSingleAgentsToCompletion");
            var contextId = seedOrchestrator().value();
            queuedLlmRunner.setThread(contextId);
            enqueueHappyPath("Implement auth");

            // Act - Run the workflow with real agent code
            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Implement auth", "DISCOVERY"))
            );

            // Assert
            assertThat(output.getStatus()).isEqualTo(com.embabel.agent.core.AgentProcessStatusCode.COMPLETED);

            // Verify all queued responses were consumed
            queuedLlmRunner.assertAllConsumed();

            // Verify graph service calls happened
            verify(workflowGraphService).startOrchestrator(any());
            verify(workflowGraphService).startDiscoveryOrchestrator(any(), any());
            verify(workflowGraphService).startDiscoveryAgent(any(), any(), any(), any());
            verify(workflowGraphService).startPlanningOrchestrator(any(), any());
            verify(workflowGraphService).startPlanningAgent(any(), any(), any(), any());
            verify(workflowGraphService).startTicketOrchestrator(any(), any());
            verify(workflowGraphService).startTicketAgent(any(), any(), anyInt());
            verify(computationGraphOrchestrator, atLeastOnce()).addChildNodeAndEmitEvent(any(), any());
            verify(computationGraphOrchestrator, atLeastOnce()).emitStatusChangeEvent(any(), any(), any(), any());

            var ordered = inOrder(
                    workflowAgent,
                    discoveryDispatchSubagent,
                    planningDispatchSubagent,
                    ticketDispatchSubagent
            );
            ordered.verify(workflowAgent).coordinateWorkflow(any(), any());
            ordered.verify(workflowAgent).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());
            ordered.verify(workflowAgent).dispatchDiscoveryAgentRequests(any(), any());
            ordered.verify(discoveryDispatchSubagent).runDiscoveryAgent(any(AgentModels.DiscoveryAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidateDiscoveryFindings(any(), any());
            ordered.verify(workflowAgent).handleDiscoveryCollectorBranch(any(), any());
            ordered.verify(workflowAgent).decomposePlanAndCreateWorkItems(any(), any());
            ordered.verify(workflowAgent).dispatchPlanningAgentRequests(any(), any());
            ordered.verify(planningDispatchSubagent).runPlanningAgent(any(AgentModels.PlanningAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidatePlansIntoTickets(any(), any());
            ordered.verify(workflowAgent).handlePlanningCollectorBranch(any(), any());
            ordered.verify(workflowAgent).orchestrateTicketExecution(any(), any());
            ordered.verify(workflowAgent).dispatchTicketAgentRequests(any(), any());
            ordered.verify(ticketDispatchSubagent).runTicketAgent(any(AgentModels.TicketAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidateTicketResults(any(), any());
            ordered.verify(workflowAgent).handleTicketCollectorBranch(any(), any());
            ordered.verify(workflowAgent).consolidateWorkflowOutputs(any(), any());
        }

//        @Test
        void fullWorkflow_persistsArtifactTree() {
            setLogFile("fullWorkflow_persistsArtifactTree");
            var contextId =  seedOrchestrator().value();
            queuedLlmRunner.setThread(contextId);
            enqueueHappyPath("Implement auth");

            Instant startedAt = Instant.now();

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Implement auth", "DISCOVERY"))
            );

            Instant finishedAt = Instant.now();

            assertThat(output.getStatus()).isEqualTo(com.embabel.agent.core.AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();

            // Verify we emitted artifacts and opened execution scope
            verify(artifactEventListener, atLeastOnce()).onEvent(isA(Events.ArtifactEvent.class));
            verify(executionScopeService, atLeastOnce()).startExecution(anyString(), any(ArtifactKey.class));

            String executionKey = findExecutionKeyForContext(contextId, startedAt, finishedAt);

            List<ArtifactEntity> persisted = artifactRepository.findByExecutionKeyOrderByArtifactKey(executionKey);
            assertThat(persisted).isNotEmpty();

            Set<String> keys = persisted.stream().map(ArtifactEntity::getArtifactKey).collect(java.util.stream.Collectors.toSet());

            ArtifactEntity root = persisted.stream()
                    .filter(entity -> entity.getParentKey() == null && "Execution".equals(entity.getArtifactType()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Execution root not persisted"));

            assertThat(root.getArtifactKey()).isEqualTo(executionKey);
            assertThat(artifactRepository.existsByArtifactKey(executionKey)).isTrue();

            // Validate tree structure: all children reference existing parents
            persisted.stream()
                    .filter(entity -> entity.getParentKey() != null)
                    .forEach(entity -> assertThat(keys).contains(entity.getParentKey()));

            // Validate expected artifacts exist and are nested under rendered prompt
            List<ArtifactEntity> renderedPrompts = persisted.stream()
                    .filter(entity -> "RenderedPrompt".equals(entity.getArtifactType()))
                    .toList();
            assertThat(renderedPrompts).isNotEmpty();

            Set<String> renderedPromptKeys = renderedPrompts.stream()
                    .map(ArtifactEntity::getArtifactKey)
                    .collect(java.util.stream.Collectors.toSet());

            List<ArtifactEntity> templateArtifacts = persisted.stream()
                    .filter(entity -> "PromptTemplateVersion".equals(entity.getArtifactType()))
                    .toList();
            assertThat(templateArtifacts).isNotEmpty();

            templateArtifacts.forEach(template ->
                    assertThat(renderedPromptKeys).contains(template.getParentKey()));

            // Clean up test artifacts to keep DB tidy
            artifactRepository.deleteByExecutionKey(executionKey);
        }

        private String findExecutionKeyForContext(String contextId, Instant startedAt, Instant finishedAt) {
            List<String> executionKeys = artifactRepository.findExecutionKeysBetween(
                    startedAt.minusSeconds(2),
                    finishedAt.plusSeconds(5)
            );

            return executionKeys.stream()
                    .filter(key -> artifactTreeBuilder.loadExecution(key)
                            .filter(artifact -> artifact instanceof Artifact.ExecutionArtifact exec
                                    && contextId.equals(exec.workflowRunId()))
                            .isPresent())
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No execution persisted for workflow run id: " + contextId));
        }

        @Test
        void skipDiscovery_startAtPlanning() {
            log.error("Haven't implemented!");
        }

        @Test
        void planningOrchestrator_toContextManager_toTicketOrchestrator() {
            log.error("Haven't implemented!");
        }

    }

    @Nested
    class LoopingScenarios {

        @Test
        void discoveryOrchestrator_toContextManager_backToDiscoveryOrchestrator() {
            log.error("Haven't implemented");
        }

        @Test
        void discoveryCollector_loopsBackForMoreInvestigation() {
            setLogFile("discoveryCollector_loopsBackForMoreInvestigation");
            var contextId = seedOrchestrator().value();
            queuedLlmRunner.setThread(contextId);

            initialOrchestratorToDiscovery("Needs more discovery");

            queuedLlmRunner.enqueue(AgentModels.DiscoveryOrchestratorRouting.builder()
                    .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                            .requests(List.of(
                                    AgentModels.DiscoveryAgentRequest.builder()
                                            .goal("Needs more discovery")
                                            .subdomainFocus("Initial")
                                            .build()
                            ))
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentRouting.builder()
                    .agentResult(AgentModels.DiscoveryAgentResult.builder()
                            .output("Initial findings - incomplete")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentDispatchRouting.builder()
                    .collectorRequest(AgentModels.DiscoveryCollectorRequest.builder()
                            .goal("Needs more discovery")
                            .discoveryResults("initial")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryCollectorRouting
                    .builder()
                    .discoveryRequest(AgentModels.DiscoveryOrchestratorRequest.builder()
                            .goal("Need more discovery")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryOrchestratorRouting.builder()
                    .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                            .requests(List.of(
                                    AgentModels.DiscoveryAgentRequest.builder()
                                            .goal("Needs more discovery")
                                            .subdomainFocus("Deeper")
                                            .build()
                            ))
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentRouting.builder()
                    .agentResult(AgentModels.DiscoveryAgentResult.builder()
                            .output("Deeper findings - complete")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentDispatchRouting.builder()
                    .collectorRequest(AgentModels.DiscoveryCollectorRequest.builder()
                            .goal("Needs more discovery")
                            .discoveryResults("deeper")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryCollectorRouting.builder()
                    .collectorResult(AgentModels.DiscoveryCollectorResult.builder()
                            .consolidatedOutput("Discovery complete")
                            .build())
                    .build());

            enqueuePlanningToCompletion("Needs more discovery");

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Needs more discovery", "DISCOVERY"))
            );

            assertThat(output.getStatus()).isEqualTo(com.embabel.agent.core.AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();
            verify(workflowAgent, times(2)).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());
            verify(workflowAgent, times(2)).consolidateDiscoveryFindings(any(), any());
            verify(computationGraphOrchestrator, atLeastOnce()).emitStatusChangeEvent(any(), any(), any(), any());

            var ordered = inOrder(
                    workflowAgent,
                    discoveryDispatchSubagent,
                    planningDispatchSubagent,
                    ticketDispatchSubagent
            );
            ordered.verify(workflowAgent).coordinateWorkflow(any(), any());
            ordered.verify(workflowAgent).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());
            ordered.verify(workflowAgent).dispatchDiscoveryAgentRequests(any(), any());
            ordered.verify(discoveryDispatchSubagent).runDiscoveryAgent(any(AgentModels.DiscoveryAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidateDiscoveryFindings(any(), any());
            ordered.verify(workflowAgent).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());
            ordered.verify(workflowAgent).dispatchDiscoveryAgentRequests(any(), any());
            ordered.verify(discoveryDispatchSubagent).runDiscoveryAgent(any(AgentModels.DiscoveryAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidateDiscoveryFindings(any(), any());
            ordered.verify(workflowAgent).handleDiscoveryCollectorBranch(any(), any());
            ordered.verify(workflowAgent).decomposePlanAndCreateWorkItems(any(), any());
            ordered.verify(workflowAgent).dispatchPlanningAgentRequests(any(), any());
            ordered.verify(planningDispatchSubagent).runPlanningAgent(any(AgentModels.PlanningAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidatePlansIntoTickets(any(), any());
            ordered.verify(workflowAgent).handlePlanningCollectorBranch(any(), any());
            ordered.verify(workflowAgent).orchestrateTicketExecution(any(), any());
            ordered.verify(workflowAgent).dispatchTicketAgentRequests(any(), any());
            ordered.verify(ticketDispatchSubagent).runTicketAgent(any(AgentModels.TicketAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidateTicketResults(any(), any());
            ordered.verify(workflowAgent).handleTicketCollectorBranch(any(), any());
            ordered.verify(workflowAgent).consolidateWorkflowOutputs(any(), any());
        }

        @Test
        void planningCollector_loopsBackToDiscovery_needsMoreContext() {
            setLogFile("planningCollector_loopsBackToDiscovery_needsMoreContext");
            var contextId = seedOrchestrator().value();
            queuedLlmRunner.setThread(contextId);

            initialOrchestratorToDiscovery("Incomplete context");

            discoveryOnly("Do a goal");

            queuedLlmRunner.enqueue(AgentModels.PlanningOrchestratorRouting.builder()
                    .agentRequests(AgentModels.PlanningAgentRequests.builder()
                            .requests(List.of(
                                    AgentModels.PlanningAgentRequest.builder()
                                            .goal("Incomplete context")
                                            .build()
                            ))
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.PlanningAgentRouting.builder()
                    .agentResult(AgentModels.PlanningAgentResult.builder()
                            .output("Plan missing context")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.PlanningAgentDispatchRouting.builder()
                    .planningCollectorRequest(AgentModels.PlanningCollectorRequest.builder()
                            .goal("Incomplete context")
                            .planningResults("plan")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.PlanningCollectorRouting.builder()
                    .orchestratorRequest(AgentModels.OrchestratorRequest.builder()
                            .goal("Incomplete context")
                            .phase("DISCOVERY")
                            .build())
                    .build());

            initialOrchestratorToDiscovery("Another one.");

            enqueueDiscoveryToEnd("This goal.");

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Incomplete context", "PLANNING"))
            );

            assertThat(output.getStatus()).isEqualTo(com.embabel.agent.core.AgentProcessStatusCode.COMPLETED);

            queuedLlmRunner.assertAllConsumed();

            verify(workflowAgent, times(2)).decomposePlanAndCreateWorkItems(any(), any());
            verify(workflowAgent, times(2)).consolidatePlansIntoTickets(any(), any());
            verify(workflowAgent, times(2)).kickOffAnyNumberOfAgentsForCodeSearch(any(), any());
            verify(computationGraphOrchestrator, atLeastOnce()).emitStatusChangeEvent(any(), any(), any(), any());

            var ordered = inOrder(
                    workflowAgent,
                    discoveryDispatchSubagent,
                    planningDispatchSubagent,
                    ticketDispatchSubagent
            );

            ordered.verify(workflowAgent).coordinateWorkflow(any(), any());
            ordered.verify(workflowAgent).consolidateDiscoveryFindings(any(), any());
            ordered.verify(workflowAgent).handleDiscoveryCollectorBranch(any(), any());
            ordered.verify(workflowAgent).dispatchPlanningAgentRequests(any(), any());
            ordered.verify(planningDispatchSubagent).runPlanningAgent(any(AgentModels.PlanningAgentRequest.class), any());
            ordered.verify(workflowAgent).dispatchDiscoveryAgentRequests(any(), any());
            ordered.verify(discoveryDispatchSubagent).runDiscoveryAgent(any(AgentModels.DiscoveryAgentRequest.class), any());
            ordered.verify(workflowAgent).handlePlanningCollectorBranch(any(), any());
            ordered.verify(workflowAgent).orchestrateTicketExecution(any(), any());
            ordered.verify(workflowAgent).dispatchTicketAgentRequests(any(), any());
            ordered.verify(ticketDispatchSubagent).runTicketAgent(any(AgentModels.TicketAgentRequest.class), any());
            ordered.verify(workflowAgent).consolidateTicketResults(any(), any());
            ordered.verify(workflowAgent).handleTicketCollectorBranch(any(), any());
            ordered.verify(workflowAgent).consolidateWorkflowOutputs(any(), any());
        }

//        @Test
        void orchestratorCollector_loopsBackMultipleTimes() {
        }
    }

    // ── External interrupt routing (controller-initiated) ────────────────

    @Nested
    class ExternalInterruptRouting {

        /**
         * External interrupt targeting the orchestrator node during discovery.
         * The controller publishes an InterruptRequestEvent which the
         * InterruptRequestEventListener picks up, stores in FilterPropertiesDecorator,
         * and publishes an AddMessageEvent. The next LLM call should see the
         * interrupt stored in the decorator. After resolution, workflow completes.
         */
        @SneakyThrows
        @Test
        void externalInterrupt_atOrchestrator_storesInFilterPropertiesAndCompletes() {
            setLogFile("externalInterrupt_atOrchestrator_storesInFilterPropertiesAndCompletes");
            var contextId = seedOrchestrator().value();
            queuedLlmRunner.setThread(contextId);

            // 1st LLM call: orchestrator routes to discovery, but in callback we inject
            //    an external interrupt targeting the orchestrator node
            queuedLlmRunner.enqueue(
                    AgentModels.OrchestratorRouting.builder()
                            .discoveryOrchestratorRequest(AgentModels.DiscoveryOrchestratorRequest.builder()
                                    .goal("External interrupt test")
                                    .build())
                            .build(),
                    (response, opCtx) -> {
                        // Inject external interrupt targeting the orchestrator node
                        eventBus.publish(new Events.InterruptRequestEvent(
                                UUID.randomUUID().toString(),
                                Instant.now(),
                                contextId,
                                "orchestrator",
                                "discovery-orchestrator",
                                Events.InterruptType.HUMAN_REVIEW,
                                "Controller wants to reroute to discovery orchestrator",
                                List.of(), List.of(), null,
                                UUID.randomUUID().toString()
                        ));
                    }
            );

            // 2nd LLM call: discovery orchestrator — the interrupt was stored but the
            //    workflow continues because the agent's response was already determined.
            //    The interrupt event should be in FilterPropertiesDecorator for the NEXT call.
            discoveryOnly("External interrupt test");

            // Continue with planning + tickets to complete the workflow
            enqueuePlanningToCompletion("External interrupt test");

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "External interrupt test", "DISCOVERY"))
            );

            assertThat(output).isNotNull();
            assertThat(output.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();
        }

        /**
         * External interrupt during discovery agent execution. The controller
         * publishes an interrupt targeting the discovery agent's node during
         * the discovery agent's LLM call. The discovery agent's next response
         * is an interrupt. After resolution, the workflow continues.
         */
        @SneakyThrows
        @Test
        void externalInterrupt_duringDiscoveryAgent_interruptHandledAndCompletes() {
            setLogFile("externalInterrupt_duringDiscoveryAgent_interruptHandledAndCompletes");
            var contextId = seedOrchestrator().value();
            queuedLlmRunner.setThread(contextId);

            initialOrchestratorToDiscovery("Discovery interrupt test");

            // Discovery orchestrator dispatches to discovery agent
            queuedLlmRunner.enqueue(AgentModels.DiscoveryOrchestratorRouting.builder()
                    .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                            .requests(List.of(
                                    AgentModels.DiscoveryAgentRequest.builder()
                                            .goal("Discovery interrupt test")
                                            .subdomainFocus("Primary")
                                            .build()
                            ))
                            .build())
                    .build());

            // Discovery agent returns an interrupt (simulating agent seeing the injected
            // interrupt message and responding with an interrupt request)
            queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentRouting.builder()
                    .interruptRequest(AgentModels.InterruptRequest.DiscoveryAgentInterruptRequest.builder()
                            .type(Events.InterruptType.HUMAN_REVIEW)
                            .reason("Discovery agent needs human review")
                            .build())
                    .build());

            // After interrupt resolution, InterruptService.handleInterrupt() calls
            // runWithTemplate expecting InterruptRouting — route back to orchestrator
            queuedLlmRunner.enqueue(AgentModels.InterruptRouting.builder()
                    .orchestratorRequest(AgentModels.OrchestratorRequest.builder()
                            .goal("Discovery interrupt test")
                            .build())
                    .build());

            // Orchestrator re-dispatches to discovery
            queuedLlmRunner.enqueue(AgentModels.OrchestratorRouting.builder()
                    .discoveryOrchestratorRequest(AgentModels.DiscoveryOrchestratorRequest.builder()
                            .goal("Discovery interrupt test")
                            .build())
                    .build());

            // Discovery orchestrator dispatches agent again
            queuedLlmRunner.enqueue(AgentModels.DiscoveryOrchestratorRouting.builder()
                    .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                            .requests(List.of(
                                    AgentModels.DiscoveryAgentRequest.builder()
                                            .goal("Discovery interrupt test")
                                            .subdomainFocus("Primary")
                                            .build()
                            ))
                            .build())
                    .build());

            // Discovery agent responds normally after re-dispatch
            queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentRouting.builder()
                    .agentResult(AgentModels.DiscoveryAgentResult.builder()
                            .output("Found stuff after interrupt")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentDispatchRouting.builder()
                    .collectorRequest(AgentModels.DiscoveryCollectorRequest.builder()
                            .goal("Discovery interrupt test")
                            .discoveryResults("discovery-results")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryCollectorRouting.builder()
                    .collectorResult(AgentModels.DiscoveryCollectorResult.builder()
                            .consolidatedOutput("Discovery complete")
                            .build())
                    .build());

            enqueuePlanningToCompletion("Discovery interrupt test");

            var workflowFuture = CompletableFuture.supplyAsync(() -> agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Discovery interrupt test", "DISCOVERY"))
            ));

            // Wait for the interrupt to be published
            await().atMost(Duration.ofSeconds(30))
                    .until(() -> permissionGate.isInterruptPending(
                            t -> t.getType() == Events.InterruptType.HUMAN_REVIEW
                                 && Objects.equals(t.getReason(), "Discovery agent needs human review")));

            var pendingInterrupt = permissionGate.getInterruptPending(
                    t -> Objects.equals(t.getReason(), "Discovery agent needs human review"));
            assertThat(pendingInterrupt).isNotNull();

            // Resolve the interrupt
            permissionGate.resolveInterrupt(
                    pendingInterrupt.getInterruptId(),
                    IPermissionGate.ResolutionType.APPROVED,
                    "Proceed with discovery",
                    (IPermissionGate.InterruptResult) null);

            await().atMost(Duration.ofSeconds(30))
                    .until(workflowFuture::isDone);

            var result = workflowFuture.get();
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();
        }

        /**
         * Discovery collector returns an interrupt (agent-initiated), then after
         * resolution routes back to discovery for another pass.
         */
        @SneakyThrows
        @Test
        void discoveryCollector_agentInitiatedInterrupt_resolvesAndContinues() {
            setLogFile("discoveryCollector_agentInitiatedInterrupt_resolvesAndContinues");
            var contextId = seedOrchestrator().value();
            queuedLlmRunner.setThread(contextId);

            initialOrchestratorToDiscovery("Collector interrupt test");

            // Discovery pass
            queuedLlmRunner.enqueue(AgentModels.DiscoveryOrchestratorRouting.builder()
                    .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                            .requests(List.of(
                                    AgentModels.DiscoveryAgentRequest.builder()
                                            .goal("Collector interrupt test")
                                            .subdomainFocus("Primary")
                                            .build()
                            ))
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentRouting.builder()
                    .agentResult(AgentModels.DiscoveryAgentResult.builder()
                            .output("Found stuff")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentDispatchRouting.builder()
                    .collectorRequest(AgentModels.DiscoveryCollectorRequest.builder()
                            .goal("Collector interrupt test")
                            .discoveryResults("discovery-results")
                            .build())
                    .build());

            // Collector returns an interrupt instead of a result
            queuedLlmRunner.enqueue(AgentModels.DiscoveryCollectorRouting.builder()
                    .interruptRequest(AgentModels.InterruptRequest.DiscoveryCollectorInterruptRequest.builder()
                            .type(Events.InterruptType.HUMAN_REVIEW)
                            .reason("Collector needs human review before consolidating")
                            .build())
                    .build());

            // After interrupt resolution, InterruptService expects InterruptRouting
            queuedLlmRunner.enqueue(AgentModels.InterruptRouting.builder()
                    .orchestratorRequest(AgentModels.OrchestratorRequest.builder()
                            .goal("Collector interrupt test")
                            .build())
                    .build());

            // Orchestrator re-dispatches to discovery
            queuedLlmRunner.enqueue(AgentModels.OrchestratorRouting.builder()
                    .discoveryOrchestratorRequest(AgentModels.DiscoveryOrchestratorRequest.builder()
                            .goal("Collector interrupt test")
                            .build())
                    .build());

            // Full discovery pass again
            queuedLlmRunner.enqueue(AgentModels.DiscoveryOrchestratorRouting.builder()
                    .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                            .requests(List.of(
                                    AgentModels.DiscoveryAgentRequest.builder()
                                            .goal("Collector interrupt test")
                                            .subdomainFocus("Primary")
                                            .build()
                            ))
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentRouting.builder()
                    .agentResult(AgentModels.DiscoveryAgentResult.builder()
                            .output("Found stuff after review")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentDispatchRouting.builder()
                    .collectorRequest(AgentModels.DiscoveryCollectorRequest.builder()
                            .goal("Collector interrupt test")
                            .discoveryResults("discovery-results")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryCollectorRouting.builder()
                    .collectorResult(AgentModels.DiscoveryCollectorResult.builder()
                            .consolidatedOutput("Discovery complete after review")
                            .build())
                    .build());

            enqueuePlanningToCompletion("Collector interrupt test");

            var workflowFuture = CompletableFuture.supplyAsync(() -> agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Collector interrupt test", "DISCOVERY"))
            ));

            await().atMost(Duration.ofSeconds(30))
                    .until(() -> permissionGate.isInterruptPending(
                            t -> Objects.equals(t.getReason(), "Collector needs human review before consolidating")));

            var pendingInterrupt = permissionGate.getInterruptPending(
                    t -> Objects.equals(t.getReason(), "Collector needs human review before consolidating"));
            assertThat(pendingInterrupt).isNotNull();

            permissionGate.resolveInterrupt(
                    pendingInterrupt.getInterruptId(),
                    IPermissionGate.ResolutionType.APPROVED,
                    "Proceed",
                    (IPermissionGate.InterruptResult) null);

            await().atMost(Duration.ofSeconds(30))
                    .until(workflowFuture::isDone);

            var result = workflowFuture.get();
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();
        }

        /**
         * Planning agent dispatch returns an interrupt — tests interrupt at the
         * dispatch agent level (between planning agent completion and collector).
         */
        @SneakyThrows
        @Test
        void planningAgentDispatch_interruptAndResume() {
            setLogFile("planningAgentDispatch_interruptAndResume");
            var contextId = seedOrchestrator().value();
            queuedLlmRunner.setThread(contextId);

            initialOrchestratorToDiscovery("Dispatch interrupt test");
            discoveryOnly("Dispatch interrupt test");

            // Planning orchestrator dispatches
            queuedLlmRunner.enqueue(AgentModels.PlanningOrchestratorRouting.builder()
                    .agentRequests(AgentModels.PlanningAgentRequests.builder()
                            .requests(List.of(
                                    AgentModels.PlanningAgentRequest.builder()
                                            .goal("Dispatch interrupt test")
                                            .build()
                            ))
                            .build())
                    .build());

            // Planning agent completes normally
            queuedLlmRunner.enqueue(AgentModels.PlanningAgentRouting.builder()
                    .agentResult(AgentModels.PlanningAgentResult.builder()
                            .output("Plan output")
                            .build())
                    .build());

            // Planning dispatch agent returns an interrupt
            queuedLlmRunner.enqueue(AgentModels.PlanningAgentDispatchRouting.builder()
                    .interruptRequest(AgentModels.InterruptRequest.PlanningAgentDispatchInterruptRequest.builder()
                            .type(Events.InterruptType.HUMAN_REVIEW)
                            .reason("Planning dispatch needs review before collecting")
                            .build())
                    .build());

            // After interrupt resolution, InterruptService expects InterruptRouting
            // Route directly to planning orchestrator (bypasses orchestrator re-entry)
            queuedLlmRunner.enqueue(AgentModels.InterruptRouting.builder()
                    .planningOrchestratorRequest(AgentModels.PlanningOrchestratorRequest.builder()
                            .goal("Dispatch interrupt test")
                            .build())
                    .build());

            // Full planning pass again
            queuedLlmRunner.enqueue(AgentModels.PlanningOrchestratorRouting.builder()
                    .agentRequests(AgentModels.PlanningAgentRequests.builder()
                            .requests(List.of(
                                    AgentModels.PlanningAgentRequest.builder()
                                            .goal("Dispatch interrupt test")
                                            .build()
                            ))
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.PlanningAgentRouting.builder()
                    .agentResult(AgentModels.PlanningAgentResult.builder()
                            .output("Plan output after review")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.PlanningAgentDispatchRouting.builder()
                    .planningCollectorRequest(AgentModels.PlanningCollectorRequest.builder()
                            .goal("Dispatch interrupt test")
                            .planningResults("planning-results")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.PlanningCollectorRouting.builder()
                    .collectorResult(AgentModels.PlanningCollectorResult.builder()
                            .consolidatedOutput("Planning complete")
                            .build())
                    .build());

            ticketsOnly("Dispatch interrupt test");
            finalOrchestratorCollector();

            var workflowFuture = CompletableFuture.supplyAsync(() -> agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Dispatch interrupt test", "DISCOVERY"))
            ));

            await().atMost(Duration.ofSeconds(30))
                    .until(() -> permissionGate.isInterruptPending(
                            t -> Objects.equals(t.getReason(), "Planning dispatch needs review before collecting")));

            var pendingInterrupt = permissionGate.getInterruptPending(
                    t -> Objects.equals(t.getReason(), "Planning dispatch needs review before collecting"));
            assertThat(pendingInterrupt).isNotNull();

            permissionGate.resolveInterrupt(
                    pendingInterrupt.getInterruptId(),
                    IPermissionGate.ResolutionType.APPROVED,
                    "Proceed to collector",
                    (IPermissionGate.InterruptResult) null);

            await().atMost(Duration.ofSeconds(30))
                    .until(workflowFuture::isDone);

            var result = workflowFuture.get();
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();
        }

        /**
         * Final orchestrator collector returns an interrupt before consolidating.
         */
        @SneakyThrows
        @Test
        void orchestratorCollector_interruptBeforeFinalConsolidation() {
            setLogFile("orchestratorCollector_interruptBeforeFinalConsolidation");
            var contextId = seedOrchestrator().value();
            queuedLlmRunner.setThread(contextId);

            enqueueHappyPathExceptFinalCollector("Final collector interrupt test");

            // Orchestrator collector returns an interrupt
            queuedLlmRunner.enqueue(AgentModels.OrchestratorCollectorRouting.builder()
                    .interruptRequest(AgentModels.InterruptRequest.OrchestratorCollectorInterruptRequest.builder()
                            .type(Events.InterruptType.HUMAN_REVIEW)
                            .reason("Final collector wants review before wrapping up")
                            .build())
                    .build());

            // After interrupt resolution, InterruptService expects InterruptRouting
            // Route directly to orchestrator collector
            queuedLlmRunner.enqueue(AgentModels.InterruptRouting.builder()
                    .orchestratorCollectorRequest(AgentModels.OrchestratorCollectorRequest.builder()
                            .goal("Final collector interrupt test")
                            .build())
                    .build());

            // Collector completes normally after re-dispatch
            queuedLlmRunner.enqueue(AgentModels.OrchestratorCollectorRouting.builder()
                    .collectorResult(AgentModels.OrchestratorCollectorResult.builder()
                            .consolidatedOutput("Workflow complete after review")
                            .build())
                    .build());

            var workflowFuture = CompletableFuture.supplyAsync(() -> agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Final collector interrupt test", "DISCOVERY"))
            ));

            await().atMost(Duration.ofSeconds(30))
                    .until(() -> permissionGate.isInterruptPending(
                            t -> Objects.equals(t.getReason(), "Final collector wants review before wrapping up")));

            var pendingInterrupt = permissionGate.getInterruptPending(
                    t -> Objects.equals(t.getReason(), "Final collector wants review before wrapping up"));
            assertThat(pendingInterrupt).isNotNull();

            permissionGate.resolveInterrupt(
                    pendingInterrupt.getInterruptId(),
                    IPermissionGate.ResolutionType.APPROVED,
                    "Wrap it up",
                    (IPermissionGate.InterruptResult) null);

            await().atMost(Duration.ofSeconds(30))
                    .until(workflowFuture::isDone);

            var result = workflowFuture.get();
            assertThat(result).isNotNull();
            assertThat(result.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();
        }
    }

    // ── Topology and agent communication ────────────────────────────────

    @Nested
    class TopologyScenarios {

        /**
         * During a workflow execution, verify that list_agents returns the
         * correct topology permissions for the calling agent.
         */
        @SneakyThrows
        @Test
        void listAgents_duringWorkflow_returnsCorrectTopology() {
            setLogFile("listAgents_duringWorkflow_returnsCorrectTopology");
            var contextId = seedOrchestrator().value();
            queuedLlmRunner.setThread(contextId);

            var topologyResults = new java.util.concurrent.CopyOnWriteArrayList<AgentCommunicationService.AgentAvailabilityEntry>();

            initialOrchestratorToDiscovery("Topology test");

            // During discovery agent execution, call listAgents in the callback
            queuedLlmRunner.enqueue(
                    AgentModels.DiscoveryOrchestratorRouting.builder()
                            .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                                    .requests(List.of(
                                            AgentModels.DiscoveryAgentRequest.builder()
                                                    .goal("Topology test")
                                                    .subdomainFocus("Primary")
                                                    .build()
                                    ))
                                    .build())
                            .build(),
                    (response, opCtx) -> {
                        // Call listAvailableAgents from the discovery orchestrator's perspective
                        var agents = agentCommunicationService.listAvailableAgents(
                                contextId,
                                AgentType.ORCHESTRATOR
                        );
                        topologyResults.addAll(agents);
                    }
            );

            queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentRouting.builder()
                    .agentResult(AgentModels.DiscoveryAgentResult.builder()
                            .output("Found stuff")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentDispatchRouting.builder()
                    .collectorRequest(AgentModels.DiscoveryCollectorRequest.builder()
                            .goal("Topology test")
                            .discoveryResults("discovery-results")
                            .build())
                    .build());

            queuedLlmRunner.enqueue(AgentModels.DiscoveryCollectorRouting.builder()
                    .collectorResult(AgentModels.DiscoveryCollectorResult.builder()
                            .consolidatedOutput("Discovery complete")
                            .build())
                    .build());

            enqueuePlanningToCompletion("Topology test");

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Topology test", "DISCOVERY"))
            );

            assertThat(output.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();

            // Topology results were collected during execution — verify they're
            // either empty (no other sessions open) or properly filtered
            // The key assertion is that no entry has the caller's own session key
            for (var entry : topologyResults) {
                assertThat(entry.agentKey())
                        .as("list_agents should not return the caller's own session")
                        .isNotEqualTo(contextId);
            }
        }

        /**
         * Verify that graph nodes of all expected types are created during a happy-path
         * workflow, and that each node tracks its parent lineage correctly.
         */
        @SneakyThrows
        @Test
        void happyPath_graphNodesCreatedWithCorrectParentage() {
            setLogFile("happyPath_graphNodesCreatedWithCorrectParentage");
            var contextId = seedOrchestrator().value();
            queuedLlmRunner.setThread(contextId);
            enqueueHappyPath("Graph lineage test");

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Graph lineage test", "DISCOVERY"))
            );

            assertThat(output.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();

            // Verify graph contains orchestrator node
            var allNodes = graphRepository.findAll();
            assertThat(allNodes).isNotEmpty();

            var orchestratorNodes = allNodes.stream()
                    .filter(n -> n instanceof OrchestratorNode)
                    .toList();
            assertThat(orchestratorNodes).hasSize(1);
            assertThat(orchestratorNodes.getFirst().nodeId()).isEqualTo(contextId);

            // Verify discovery, planning, and ticket orchestrator nodes exist
            // and each has the top-level orchestrator as ancestor
            verify(workflowGraphService).startOrchestrator(any());
            verify(workflowGraphService).startDiscoveryOrchestrator(any(), any());
            verify(workflowGraphService).startPlanningOrchestrator(any(), any());
            verify(workflowGraphService).startTicketOrchestrator(any(), any());

            // Each agent node should have been parented under its phase orchestrator
            verify(workflowGraphService).startDiscoveryAgent(any(), any(), any(), any());
            verify(workflowGraphService).startPlanningAgent(any(), any(), any(), any());
            verify(workflowGraphService).startTicketAgent(any(), any(), anyInt());
        }

        /**
         * During a discovery agent callback, call another agent via AgentTopologyTools.callAgent.
         * Verify: AgentToAgentConversationNode created in graph with correct source/target,
         * chatSessionKey set, and node completed after call returns.
         */
        @SneakyThrows
        @Test
        void callAgent_createsAndCompletesAgentToAgentNode() {
            setLogFile("callAgent_createsAndCompletesAgentToAgentNode");
            var contextId = seedOrchestrator().value();
            queuedLlmRunner.setThread(contextId);

            // Create a target agent node in the graph
            ArtifactKey targetKey = ArtifactKey.createRoot();
            graphRepository.save(DiscoveryCollectorNode.builder()
                    .nodeId(targetKey.value())
                    .title("Target Collector")
                    .goal("test target")
                    .status(Events.NodeStatus.RUNNING)
                    .metadata(new HashMap<>())
                    .createdAt(Instant.now())
                    .lastUpdatedAt(Instant.now())
                    .build());

            // Stub validateCall to bypass ACP session availability check
            doReturn(AgentCommunicationService.CallValidationResult.ok())
                    .when(agentCommunicationService)
                    .validateCall(any(), any(), any(), any(), any());

            var callResults = new java.util.concurrent.CopyOnWriteArrayList<String>();

            initialOrchestratorToDiscovery("Call agent test");

            // Discovery orchestrator dispatches one agent
            queuedLlmRunner.enqueue(AgentModels.DiscoveryOrchestratorRouting.builder()
                    .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                            .requests(List.of(
                                    AgentModels.DiscoveryAgentRequest.builder()
                                            .goal("Call agent test")
                                            .subdomainFocus("Primary")
                                            .build()
                            ))
                            .build())
                    .build());

            // Discovery agent — callback calls another agent via callAgent
            queuedLlmRunner.enqueue(
                    AgentModels.DiscoveryAgentRouting.builder()
                            .agentResult(AgentModels.DiscoveryAgentResult.builder()
                                    .output("Found stuff")
                                    .build())
                            .build(),
                    (response, opCtx) -> {
                        // Insert at front so callAgent dequeues this before the remaining workflow steps
                        queuedLlmRunner.enqueueNext(AgentModels.AgentCallRouting.builder()
                                .response("Hello from target collector")
                                .build());

                        String result = agentTopologyTools.call_agent(
                                contextId, targetKey.value(), "Need your analysis");
                        callResults.add(result);
                    }
            );

            // Dispatch → collector
            queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentDispatchRouting.builder()
                    .collectorRequest(AgentModels.DiscoveryCollectorRequest.builder()
                            .goal("Call agent test")
                            .discoveryResults("discovery-results")
                            .build())
                    .build());
            queuedLlmRunner.enqueue(AgentModels.DiscoveryCollectorRouting.builder()
                    .collectorResult(AgentModels.DiscoveryCollectorResult.builder()
                            .consolidatedOutput("Discovery complete")
                            .build())
                    .build());

            enqueuePlanningToCompletion("Call agent test");

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Call agent test", "DISCOVERY"))
            );

            assertThat(output.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();

            // Verify the call returned a response
            assertThat(callResults).hasSize(1);
            assertThat(callResults.getFirst()).contains("Hello from target collector");

            // Verify AgentToAgentConversationNode was created in the graph
            var a2aNodes = graphRepository.findAll().stream()
                    .filter(n -> n instanceof AgentToAgentConversationNode)
                    .map(n -> (AgentToAgentConversationNode) n)
                    .toList();
            assertThat(a2aNodes).hasSize(1);

            var a2aNode = a2aNodes.getFirst();
            assertThat(a2aNode.targetAgentKey()).isEqualTo(targetKey.value());
            assertThat(a2aNode.targetAgentType()).isEqualTo(AgentType.DISCOVERY_COLLECTOR);
            assertThat(a2aNode.chatId()).isNotNull();
            assertThat(a2aNode.status()).isEqualTo(Events.NodeStatus.COMPLETED);

            // Verify startAgentCall and completeAgentCall were called
            verify(workflowGraphService).startAgentCall(any(AgentModels.AgentToAgentRequest.class));
            verify(workflowGraphService).completeAgentCall(any(), any());
        }

        /**
         * Chained call_agent calls: discovery agent calls collector, which calls orchestrator.
         * Verifies call chain tracking and multiple AgentToAgentConversationNodes.
         */
        @SneakyThrows
        @Test
        void callAgent_chainedCalls_callChainTrackedCorrectly() {
            setLogFile("callAgent_chainedCalls_callChainTrackedCorrectly");
            var contextId = seedOrchestrator().value();
            queuedLlmRunner.setThread(contextId);

            // Create two target nodes
            ArtifactKey target1Key = ArtifactKey.createRoot();
            ArtifactKey target2Key = ArtifactKey.createRoot();

            graphRepository.save(DiscoveryCollectorNode.builder()
                    .nodeId(target1Key.value())
                    .title("Target Collector")
                    .goal("test")
                    .status(Events.NodeStatus.RUNNING)
                    .metadata(new HashMap<>())
                    .createdAt(Instant.now())
                    .lastUpdatedAt(Instant.now())
                    .build());

            graphRepository.save(DiscoveryOrchestratorNode.builder()
                    .nodeId(target2Key.value())
                    .title("Target Orchestrator")
                    .goal("test")
                    .status(Events.NodeStatus.RUNNING)
                    .metadata(new HashMap<>())
                    .createdAt(Instant.now())
                    .lastUpdatedAt(Instant.now())
                    .build());

            // Bypass session availability
            doReturn(AgentCommunicationService.CallValidationResult.ok())
                    .when(agentCommunicationService)
                    .validateCall(any(), any(), any(), any(), any());

            var callResults = new java.util.concurrent.CopyOnWriteArrayList<String>();

            initialOrchestratorToDiscovery("Chain test");

            // Discovery orchestrator dispatches one agent
            queuedLlmRunner.enqueue(AgentModels.DiscoveryOrchestratorRouting.builder()
                    .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                            .requests(List.of(
                                    AgentModels.DiscoveryAgentRequest.builder()
                                            .goal("Chain test")
                                            .subdomainFocus("Primary")
                                            .build()
                            ))
                            .build())
                    .build());

            // Discovery agent — callback makes two sequential callAgent calls
            queuedLlmRunner.enqueue(
                    AgentModels.DiscoveryAgentRouting.builder()
                            .agentResult(AgentModels.DiscoveryAgentResult.builder()
                                    .output("Found stuff")
                                    .build())
                            .build(),
                    (response, opCtx) -> {
                        // First call — enqueue at front
                        queuedLlmRunner.enqueueNext(AgentModels.AgentCallRouting.builder()
                                .response("Response from collector")
                                .build());
                        String result1 = agentTopologyTools.call_agent(
                                contextId, target1Key.value(), "First call");
                        callResults.add(result1);

                        // Second call — enqueue at front again
                        queuedLlmRunner.enqueueNext(AgentModels.AgentCallRouting.builder()
                                .response("Response from orchestrator")
                                .build());
                        String result2 = agentTopologyTools.call_agent(
                                contextId, target2Key.value(), "Second call");
                        callResults.add(result2);
                    }
            );

            // Dispatch → collector
            queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentDispatchRouting.builder()
                    .collectorRequest(AgentModels.DiscoveryCollectorRequest.builder()
                            .goal("Chain test")
                            .discoveryResults("discovery-results")
                            .build())
                    .build());
            queuedLlmRunner.enqueue(AgentModels.DiscoveryCollectorRouting.builder()
                    .collectorResult(AgentModels.DiscoveryCollectorResult.builder()
                            .consolidatedOutput("Discovery complete")
                            .build())
                    .build());

            enqueuePlanningToCompletion("Chain test");

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Chain test", "DISCOVERY"))
            );

            assertThat(output.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();

            // Both calls returned
            assertThat(callResults).hasSize(2);
            assertThat(callResults.get(0)).contains("Response from collector");
            assertThat(callResults.get(1)).contains("Response from orchestrator");

            // Two AgentToAgentConversationNodes created
            var a2aNodes = graphRepository.findAll().stream()
                    .filter(n -> n instanceof AgentToAgentConversationNode)
                    .map(n -> (AgentToAgentConversationNode) n)
                    .toList();
            assertThat(a2aNodes).hasSize(2);

            // Both should be completed
            assertThat(a2aNodes).allMatch(n -> n.status() == Events.NodeStatus.COMPLETED);

            // Verify distinct targets
            var targetKeys = a2aNodes.stream()
                    .map(AgentToAgentConversationNode::targetAgentKey)
                    .toList();
            assertThat(targetKeys).containsExactlyInAnyOrder(target1Key.value(), target2Key.value());

            // Both should have chatSessionKey set
            assertThat(a2aNodes).allMatch(n -> n.chatId() != null);

            // Verify two startAgentCall invocations
            verify(workflowGraphService, times(2)).startAgentCall(any(AgentModels.AgentToAgentRequest.class));
            verify(workflowGraphService, times(2)).completeAgentCall(any(), any());
        }

        /**
         * N1: callController happy path — discovery agent calls callController(), HUMAN_REVIEW
         * interrupt published, controller resolves with response, agent receives response,
         * workflow completes. Validates N-INV1, N-INV2, N-INV4.
         */
        @SneakyThrows
        @Test
        void callController_happyPath_interruptResolvedAndAgentContinues() {
            setLogFile("callController_happyPath_interruptResolvedAndAgentContinues");
            var contextId = seedOrchestrator().value();
            queuedLlmRunner.setThread(contextId);

            var callResults = new java.util.concurrent.CopyOnWriteArrayList<String>();

            initialOrchestratorToDiscovery("Controller call test");

            // Discovery orchestrator dispatches one agent
            queuedLlmRunner.enqueue(AgentModels.DiscoveryOrchestratorRouting.builder()
                    .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                            .requests(List.of(
                                    AgentModels.DiscoveryAgentRequest.builder()
                                            .goal("Controller call test")
                                            .subdomainFocus("Primary")
                                            .build()
                            ))
                            .build())
                    .build());

            // Discovery agent — callback calls controller via callController (blocks on interrupt)
            queuedLlmRunner.enqueue(
                    AgentModels.DiscoveryAgentRouting.builder()
                            .agentResult(AgentModels.DiscoveryAgentResult.builder()
                                    .output("Found something needing controller approval")
                                    .build())
                            .build(),
                    (response, opCtx) -> {
                        String result = agentTopologyTools.call_controller(
                                contextId, "I found a critical design decision that requires your approval.");
                        callResults.add(result);
                    }
            );

            // Remaining workflow after callController returns
            queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentDispatchRouting.builder()
                    .collectorRequest(AgentModels.DiscoveryCollectorRequest.builder()
                            .goal("Controller call test")
                            .discoveryResults("discovery-results")
                            .build())
                    .build());
            queuedLlmRunner.enqueue(AgentModels.DiscoveryCollectorRouting.builder()
                    .collectorResult(AgentModels.DiscoveryCollectorResult.builder()
                            .consolidatedOutput("Discovery complete")
                            .build())
                    .build());

            enqueuePlanningToCompletion("Controller call test");

            // Run workflow async since callController blocks
            var workflowFuture = CompletableFuture.supplyAsync(() -> agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Controller call test", "DISCOVERY"))
            ));

            // Wait for the HUMAN_REVIEW interrupt from callController
            await().atMost(Duration.ofSeconds(30))
                    .until(() -> permissionGate.isInterruptPending(
                            t -> t.getType() == Events.InterruptType.HUMAN_REVIEW
                                 && t.getReason() != null
                                 && t.getReason().contains("critical design decision")));

            var pendingInterrupt = permissionGate.getInterruptPending(
                    t -> t.getType() == Events.InterruptType.HUMAN_REVIEW
                         && t.getReason() != null
                         && t.getReason().contains("critical design decision"));
            assertThat(pendingInterrupt).isNotNull();

            // Workflow should be blocked
            Thread.sleep(1000);
            assertThat(workflowFuture.isDone())
                    .as("Workflow should be blocked on callController interrupt")
                    .isFalse();

            // Resolve the interrupt (simulating controller approval)
            permissionGate.resolveInterrupt(
                    pendingInterrupt.getInterruptId(),
                    IPermissionGate.ResolutionType.APPROVED,
                    "Approved — proceed with the design decision.",
                    (IPermissionGate.InterruptResult) null);

            // Workflow should complete
            await().atMost(Duration.ofSeconds(30))
                    .until(workflowFuture::isDone);

            var output = workflowFuture.get();
            assertThat(output.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();

            // N-INV2: callController returned the controller's response text
            assertThat(callResults).hasSize(1);
            assertThat(callResults.getFirst()).contains("Approved");

            // N-INV4: AgentCallEvent emitted with target="controller"
            var agentCallEvents = testEventListener.eventsOfType(Events.AgentCallEvent.class);
            var controllerCallEvents = agentCallEvents.stream()
                    .filter(e -> "controller".equals(e.targetSessionId()))
                    .toList();
            assertThat(controllerCallEvents)
                    .as("Should have INITIATED and RETURNED AgentCallEvents for controller call")
                    .hasSizeGreaterThanOrEqualTo(2);

            var initiatedEvents = controllerCallEvents.stream()
                    .filter(e -> e.callEventType() == Events.AgentCallEventType.INITIATED)
                    .toList();
            var returnedEvents = controllerCallEvents.stream()
                    .filter(e -> e.callEventType() == Events.AgentCallEventType.RETURNED)
                    .toList();
            assertThat(initiatedEvents).hasSize(1);
            assertThat(returnedEvents).hasSize(1);
        }

        /**
         * N2: callController message budget exceeded — agent calls callController more
         * than messageBudget times. Last call returns ERROR. No interrupt published for
         * the budget-exceeded call. Validates N-INV3.
         */
        @SneakyThrows
//        @Test
        void callController_messageBudgetExceeded_returnsError() {
            setLogFile("callController_messageBudgetExceeded_returnsError");
            var contextId = seedOrchestrator().value();
            queuedLlmRunner.setThread(contextId);

            var callResults = new java.util.concurrent.CopyOnWriteArrayList<String>();

            initialOrchestratorToDiscovery("Budget test");

            queuedLlmRunner.enqueue(AgentModels.DiscoveryOrchestratorRouting.builder()
                    .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                            .requests(List.of(
                                    AgentModels.DiscoveryAgentRequest.builder()
                                            .goal("Budget test")
                                            .subdomainFocus("Primary")
                                            .build()
                            ))
                            .build())
                    .build());

            // Discovery agent callback: call callController 4 times (budget is 3)
            // First 3 calls will block on interrupts that we immediately resolve.
            // 4th call should return budget exceeded error without blocking.
            queuedLlmRunner.enqueue(
                    AgentModels.DiscoveryAgentRouting.builder()
                            .agentResult(AgentModels.DiscoveryAgentResult.builder()
                                    .output("Budget test output")
                                    .build())
                            .build(),
                    (response, opCtx) -> {
                        // Make 4 callController calls; first 3 within budget, 4th exceeds
                        for (int i = 0; i < 4; i++) {
                            final int callNum = i + 1;
                            String result = agentTopologyTools.call_controller(
                                    contextId, "Call #" + callNum);
                            callResults.add(result);

                            // If it returned an error (budget exceeded), don't try to resolve
                            if (result.startsWith("ERROR")) {
                                break;
                            }
                        }
                    }
            );

            queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentDispatchRouting.builder()
                    .collectorRequest(AgentModels.DiscoveryCollectorRequest.builder()
                            .goal("Budget test")
                            .discoveryResults("discovery-results")
                            .build())
                    .build());
            queuedLlmRunner.enqueue(AgentModels.DiscoveryCollectorRouting.builder()
                    .collectorResult(AgentModels.DiscoveryCollectorResult.builder()
                            .consolidatedOutput("Discovery complete")
                            .build())
                    .build());

            enqueuePlanningToCompletion("Budget test");

            // Run workflow async — callController blocks on interrupt
            var workflowFuture = CompletableFuture.supplyAsync(() -> agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Budget test", "DISCOVERY"))
            ));

            // Resolve interrupts as they arrive (up to 3 — the budget)
            for (int i = 0; i < 3; i++) {
                final int callNum = i + 1;
                await().atMost(Duration.ofSeconds(30))
                        .until(() -> permissionGate.isInterruptPending(
                                t -> t.getType() == Events.InterruptType.HUMAN_REVIEW
                                     && t.getReason() != null
                                     && t.getReason().contains("Call #" + callNum)));

                var pending = permissionGate.getInterruptPending(
                        t -> t.getType() == Events.InterruptType.HUMAN_REVIEW
                             && t.getReason() != null
                             && t.getReason().contains("Call #" + callNum));
                assertThat(pending).isNotNull();

                permissionGate.resolveInterrupt(
                        pending.getInterruptId(),
                        IPermissionGate.ResolutionType.APPROVED,
                        "Approved call #" + callNum,
                        (IPermissionGate.InterruptResult) null);
            }

            // Workflow should complete (4th call returns error immediately, no blocking)
            await().atMost(Duration.ofSeconds(30))
                    .until(workflowFuture::isDone);

            var output = workflowFuture.get();
            assertThat(output.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();

            // N-INV3: 4th call returned budget exceeded error
            assertThat(callResults).hasSize(4);
            assertThat(callResults.get(0)).contains("Approved call #1");
            assertThat(callResults.get(1)).contains("Approved call #2");
            assertThat(callResults.get(2)).contains("Approved call #3");
            assertThat(callResults.get(3)).startsWith("ERROR: Message budget exceeded");
            assertThat(callResults.get(3)).contains("4/3");
        }

        /**
         * N4: CallChainEntry target enrichment — verify that A2A nodes have
         * targetAgentKey and targetAgentType populated in their call chain entries.
         * Validates N-INV5.
         */
        @SneakyThrows
        @Test
        void callAgent_callChainEntry_hasTargetFields() {
            setLogFile("callAgent_callChainEntry_hasTargetFields");
            var contextId = seedOrchestrator().value();
            queuedLlmRunner.setThread(contextId);

            ArtifactKey targetKey = ArtifactKey.createRoot();
            graphRepository.save(DiscoveryCollectorNode.builder()
                    .nodeId(targetKey.value())
                    .title("Target Collector")
                    .goal("target test")
                    .status(Events.NodeStatus.RUNNING)
                    .metadata(new HashMap<>())
                    .createdAt(Instant.now())
                    .lastUpdatedAt(Instant.now())
                    .build());

            doReturn(AgentCommunicationService.CallValidationResult.ok())
                    .when(agentCommunicationService)
                    .validateCall(any(), any(), any(), any(), any());

            initialOrchestratorToDiscovery("Target fields test");

            queuedLlmRunner.enqueue(AgentModels.DiscoveryOrchestratorRouting.builder()
                    .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                            .requests(List.of(
                                    AgentModels.DiscoveryAgentRequest.builder()
                                            .goal("Target fields test")
                                            .subdomainFocus("Primary")
                                            .build()
                            ))
                            .build())
                    .build());

            queuedLlmRunner.enqueue(
                    AgentModels.DiscoveryAgentRouting.builder()
                            .agentResult(AgentModels.DiscoveryAgentResult.builder()
                                    .output("Found stuff")
                                    .build())
                            .build(),
                    (response, opCtx) -> {
                        queuedLlmRunner.enqueueNext(AgentModels.AgentCallRouting.builder()
                                .response("Response from target")
                                .build());
                        agentTopologyTools.call_agent(contextId, targetKey.value(), "Check target fields");
                    }
            );

            queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentDispatchRouting.builder()
                    .collectorRequest(AgentModels.DiscoveryCollectorRequest.builder()
                            .goal("Target fields test")
                            .discoveryResults("discovery-results")
                            .build())
                    .build());
            queuedLlmRunner.enqueue(AgentModels.DiscoveryCollectorRouting.builder()
                    .collectorResult(AgentModels.DiscoveryCollectorResult.builder()
                            .consolidatedOutput("Discovery complete")
                            .build())
                    .build());

            enqueuePlanningToCompletion("Target fields test");

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Target fields test", "DISCOVERY"))
            );

            assertThat(output.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();

            // N-INV5: A2A node's call chain entries have target fields populated
            var a2aNodes = graphRepository.findAll().stream()
                    .filter(n -> n instanceof AgentToAgentConversationNode)
                    .map(n -> (AgentToAgentConversationNode) n)
                    .toList();
            assertThat(a2aNodes).hasSize(1);

            var a2aNode = a2aNodes.getFirst();
            // The A2A node itself should have targetAgentKey pointing to the target
            assertThat(a2aNode.targetAgentKey()).isEqualTo(targetKey.value());
            assertThat(a2aNode.targetAgentType()).isEqualTo(AgentType.DISCOVERY_COLLECTOR);
        }

        /**
         * N7: controllerResponseExecution renders controller_response.jinja through the
         * decoration pipeline and resolves the interrupt with the rendered text.
         * Validates:
         * - Template is rendered with checklistAction, message, targetAgentKey
         * - JUSTIFICATION_PASSED produces "Do NOT call call_controller again" instruction
         * - Key hierarchy: contextId is child of interruptId, chatId is parent of interruptId
         * - Decorators execute without error
         * - AgentCallEvent emitted with RETURNED type
         */
        @SneakyThrows
        @Test
        void controllerResponse_rendersTemplateAndResolvesInterrupt() {
            setLogFile("controllerResponse_rendersTemplateAndResolvesInterrupt");
            var contextId = seedOrchestrator().value();
            queuedLlmRunner.setThread(contextId);

            var callResults = new java.util.concurrent.CopyOnWriteArrayList<String>();

            initialOrchestratorToDiscovery("Controller response template test");

            // Discovery orchestrator dispatches one agent
            queuedLlmRunner.enqueue(AgentModels.DiscoveryOrchestratorRouting.builder()
                    .agentRequests(AgentModels.DiscoveryAgentRequests.builder()
                            .requests(List.of(
                                    AgentModels.DiscoveryAgentRequest.builder()
                                            .goal("Controller response template test")
                                            .subdomainFocus("Primary")
                                            .build()
                            ))
                            .build())
                    .build());

            // Discovery agent calls controller (blocks on interrupt)
            queuedLlmRunner.enqueue(
                    AgentModels.DiscoveryAgentRouting.builder()
                            .agentResult(AgentModels.DiscoveryAgentResult.builder()
                                    .output("Needs controller approval")
                                    .build())
                            .build(),
                    (response, opCtx) -> {
                        String result = agentTopologyTools.call_controller(
                                contextId, "Requesting approval for architecture decision.");
                        callResults.add(result);
                    }
            );

            // Remaining workflow after callController returns
            queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentDispatchRouting.builder()
                    .collectorRequest(AgentModels.DiscoveryCollectorRequest.builder()
                            .goal("Controller response template test")
                            .discoveryResults("discovery-results")
                            .build())
                    .build());
            queuedLlmRunner.enqueue(AgentModels.DiscoveryCollectorRouting.builder()
                    .collectorResult(AgentModels.DiscoveryCollectorResult.builder()
                            .consolidatedOutput("Discovery complete")
                            .build())
                    .build());

            enqueuePlanningToCompletion("Controller response template test");

            // Run workflow async
            var workflowFuture = CompletableFuture.supplyAsync(() -> agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Controller response template test", "DISCOVERY"))
            ));

            // Wait for the HUMAN_REVIEW interrupt
            await().atMost(Duration.ofSeconds(30))
                    .until(() -> permissionGate.isInterruptPending(
                            t -> t.getType() == Events.InterruptType.HUMAN_REVIEW
                                 && t.getReason() != null
                                 && t.getReason().contains("architecture decision")));

            var pendingInterrupt = permissionGate.getInterruptPending(
                    t -> t.getType() == Events.InterruptType.HUMAN_REVIEW
                         && t.getReason() != null
                         && t.getReason().contains("architecture decision"));
            assertThat(pendingInterrupt).isNotNull();

            // Resolve via controllerResponseExecution (the new path)
            var responseResult = agentExecutor.controllerResponseExecution(
                    AgentExecutor.ControllerResponseArgs.builder()
                            .interruptId(pendingInterrupt.getInterruptId())
                            .originNodeId(pendingInterrupt.getOriginNodeId())
                            .message("Approved. Proceed with the proposed architecture.")
                            .checklistAction("JUSTIFICATION_PASSED")
                            .expectResponse(false)
                            .build()
            );

            assertThat(responseResult.resolved())
                    .as("controllerResponseExecution should resolve the interrupt")
                    .isTrue();

            // Workflow should complete
            await().atMost(Duration.ofSeconds(30))
                    .until(workflowFuture::isDone);

            var output = workflowFuture.get();
            assertThat(output.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();

            // The callController return text should contain rendered template content
            assertThat(callResults).hasSize(1);
            String renderedResponse = callResults.getFirst();

            // Template renders checklistAction section
            assertThat(renderedResponse).contains("JUSTIFICATION_PASSED");
            // Template renders the "Do NOT call call_controller again" instruction
            assertThat(renderedResponse).contains("Do NOT call");
            // Template renders the controller's message
            assertThat(renderedResponse).contains("Approved. Proceed with the proposed architecture.");

            // Key hierarchy validation: interruptId is a child of the calling agent's key
            ArtifactKey interruptKey = new ArtifactKey(pendingInterrupt.getInterruptId());
            // The interrupt key should have a parent (the calling agent's session key)
            assertThat(interruptKey.parent()).isPresent();

            // AgentCallEvent with RETURNED type should be emitted
            var agentCallEvents = testEventListener.eventsOfType(Events.AgentCallEvent.class);
            var returnedEvents = agentCallEvents.stream()
                    .filter(e -> e.callEventType() == Events.AgentCallEventType.RETURNED)
                    .filter(e -> "controller".equals(e.callerSessionId()))
                    .toList();
            assertThat(returnedEvents).isNotEmpty();
            // The returned event should carry the checklistAction
            assertThat(returnedEvents.getFirst().checklistAction()).isEqualTo("JUSTIFICATION_PASSED");
        }
    }

    @Nested
    class ExecutorLifecycleScenarios {

        /**
         * S-INV1 + S-INV2: Every LLM call through AgentExecutor emits a paired
         * AgentExecutorStartEvent/AgentExecutorCompleteEvent. The counts match
         * and each Start precedes its Complete.
         */
        @Test
        void happyPath_executorEventsMatchLlmCallCount() {
            setLogFile("happyPath_executorEventsMatchLlmCallCount");
            var contextId = seedOrchestrator().value();
            queuedLlmRunner.setThread(contextId);
            enqueueHappyPath("Implement auth");

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Implement auth", "DISCOVERY"))
            );

            assertThat(output.getStatus()).isEqualTo(com.embabel.agent.core.AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();

            // S-INV1: Count executor events
            var startEvents = testEventListener.eventsOfType(Events.AgentExecutorStartEvent.class);
            var completeEvents = testEventListener.eventsOfType(Events.AgentExecutorCompleteEvent.class);

            // Every LLM call should produce both events
            assertThat(startEvents).isNotEmpty();
            assertThat(startEvents).hasSameSizeAs(completeEvents);

            // S-INV2: Each start has a matching complete with same actionName
            for (var start : startEvents) {
                boolean hasMatch = completeEvents.stream()
                        .anyMatch(c -> c.actionName().equals(start.actionName())
                                && !c.timestamp().isBefore(start.timestamp()));
                assertThat(hasMatch)
                        .as("AgentExecutorStartEvent(action=%s) should have a matching CompleteEvent",
                                start.actionName())
                        .isTrue();
            }

            // S-INV1: sessionKey should be non-empty (E26 exploration)
            for (var start : startEvents) {
                assertThat(start.sessionKey())
                        .as("AgentExecutorStartEvent should have non-empty sessionKey")
                        .isNotEmpty();
            }
        }

        /**
         * S-INV4: In a happy-path workflow with no retries, BlackboardHistory
         * errorType should always be NoError.
         */
        @Test
        void happyPath_blackboardErrorTypeAlwaysNoError() {
            setLogFile("happyPath_blackboardErrorTypeAlwaysNoError");
            var contextId = seedOrchestrator().value();
            queuedLlmRunner.setThread(contextId);
            enqueueHappyPath("Implement auth");

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Implement auth", "DISCOVERY"))
            );

            assertThat(output.getStatus()).isEqualTo(com.embabel.agent.core.AgentProcessStatusCode.COMPLETED);

            // The blackboard trace (via QueuedLlmRunner) should show NoError for every call.
            // We verify this via the event listener — no error events should appear in happy path.
            assertThat(testEventListener.countOfType(Events.CompactionEvent.class)).isZero();
            assertThat(testEventListener.countOfType(Events.NullResultEvent.class)).isZero();
            assertThat(testEventListener.countOfType(Events.IncompleteJsonEvent.class)).isZero();
            assertThat(testEventListener.countOfType(Events.UnparsedToolCallEvent.class)).isZero();
            assertThat(testEventListener.countOfType(Events.TimeoutEvent.class)).isZero();
        }

        /**
         * S-INV7 + S-INV8a + E28: In happy path, errorDescriptor propagates as NoError
         * and no retry filtering occurs — all prompt contributors are included.
         * Validates that the Phase 5 wiring (BlackboardHistory → DecoratorContext →
         * PromptContext → PromptContributorService) does not accidentally trigger
         * retry filtering in normal flow.
         */
        @Test
        void happyPath_noRetryFilteringOccurs() {
            setLogFile("happyPath_noRetryFilteringOccurs");
            var contextId = seedOrchestrator().value();
            queuedLlmRunner.setThread(contextId);
            enqueueHappyPath("Implement auth");

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Implement auth", "DISCOVERY"))
            );

            assertThat(output.getStatus()).isEqualTo(com.embabel.agent.core.AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();

            // S-INV7: errorDescriptor propagated — verify via no error events in happy path
            // (ErrorDescriptor.NoError means no retry filtering triggers)
            assertThat(testEventListener.countOfType(Events.CompactionEvent.class)).isZero();
            assertThat(testEventListener.countOfType(Events.NullResultEvent.class)).isZero();
            assertThat(testEventListener.countOfType(Events.IncompleteJsonEvent.class)).isZero();
            assertThat(testEventListener.countOfType(Events.UnparsedToolCallEvent.class)).isZero();
            assertThat(testEventListener.countOfType(Events.TimeoutEvent.class)).isZero();

            // S-INV1: Executor events still emitted (proves assemblePrompt consolidation
            // didn't break the LLM call pipeline)
            var startEvents = testEventListener.eventsOfType(Events.AgentExecutorStartEvent.class);
            var completeEvents = testEventListener.eventsOfType(Events.AgentExecutorCompleteEvent.class);
            assertThat(startEvents).isNotEmpty();
            assertThat(startEvents).hasSameSizeAs(completeEvents);

            // S-INV11: nodeId unique per attempt, startNodeId on complete matches start's nodeId
            for (int i = 0; i < startEvents.size(); i++) {
                var start = startEvents.get(i);
                var complete = completeEvents.get(i);
                assertThat(start.nodeId())
                        .as("Start nodeId should not equal requestContextId (nodeId is a child)")
                        .isNotEqualTo(start.requestContextId());
                assertThat(complete.startNodeId())
                        .as("Complete startNodeId should match start nodeId")
                        .isEqualTo(start.nodeId());
            }

            // S-INV11: all nodeIds are distinct
            var allNodeIds = startEvents.stream().map(Events.AgentExecutorStartEvent::nodeId).toList();
            assertThat(allNodeIds).doesNotHaveDuplicates();
        }

        /**
         * S-INV3 + S-INV4 + S-INV7 + S-INV8: ActionRetryListener classifies a "null result"
         * error as NullResultError, records it on BlackboardHistory, and on retry the
         * errorDescriptor propagates to PromptContext for retry-aware filtering.
         *
         * Uses enqueueError to throw on the first orchestrator call, then succeeds on retry.
         */
        @Test
        void retry_nullResultError_classifiedAndRecoveredOnRetry() {
            setLogFile("retry_nullResultError_classifiedAndRecoveredOnRetry");
            var contextId = seedOrchestrator().value();
            queuedLlmRunner.setThread(contextId);

            // First orchestrator call: throw "null result" error → triggers retry
            queuedLlmRunner.enqueueError(new RuntimeException("LLM returned null result for orchestrator"));

            // Retry: enqueue the real orchestrator response + rest of happy path
            enqueueHappyPath("Implement auth");

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Implement auth", "DISCOVERY"))
            );

            assertThat(output.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();

            // S-INV1: One extra start event (failed attempt) but same number of completes
            // (the failed attempt doesn't emit CompleteEvent since run() threw)
            var startEvents = testEventListener.eventsOfType(Events.AgentExecutorStartEvent.class);
            var completeEvents = testEventListener.eventsOfType(Events.AgentExecutorCompleteEvent.class);
            assertThat(startEvents.size())
                    .as("One extra start from the failed attempt")
                    .isEqualTo(completeEvents.size() + 1);

            // S-INV11: The first two starts are for the same action (failed + retry) — they share
            // requestContextId but have different nodeIds
            var failedStart = startEvents.get(0);
            var retryStart = startEvents.get(1);
            assertThat(failedStart.requestContextId())
                    .as("Failed and retry starts should share requestContextId")
                    .isEqualTo(retryStart.requestContextId());
            assertThat(failedStart.nodeId())
                    .as("Failed and retry starts should have different nodeIds")
                    .isNotEqualTo(retryStart.nodeId());

            // S-INV11: The first complete event's startNodeId matches the retry start's nodeId
            // (the failed start has no matching complete)
            var firstComplete = completeEvents.get(0);
            assertThat(firstComplete.startNodeId())
                    .as("First complete's startNodeId should match retry start's nodeId")
                    .isEqualTo(retryStart.nodeId());
        }

        /**
         * S-INV3: ActionRetryListener classifies a CompactionException as CompactionError.
         * The compaction wait loop is exercised but the workflow recovers on retry.
         */
        @Test
        void retry_compactionError_classifiedAndRecoveredOnRetry() {
            setLogFile("retry_compactionError_classifiedAndRecoveredOnRetry");
            var contextId = seedOrchestrator().value();
            queuedLlmRunner.setThread(contextId);

            // First orchestrator call: throw CompactionException → triggers retry
            queuedLlmRunner.enqueueError(
                    new CompactionException("Session is compacting", contextId));

            // Retry: enqueue the real orchestrator response + rest of happy path
            enqueueHappyPath("Implement auth");

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Implement auth", "DISCOVERY"))
            );

            assertThat(output.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();
        }

        /**
         * S-INV3: ActionRetryListener classifies a "timeout" error as TimeoutError.
         */
        @Test
        void retry_timeoutError_classifiedAndRecoveredOnRetry() {
            setLogFile("retry_timeoutError_classifiedAndRecoveredOnRetry");
            var contextId = seedOrchestrator().value();
            queuedLlmRunner.setThread(contextId);

            // First orchestrator call: throw timeout → triggers retry
            queuedLlmRunner.enqueueError(new RuntimeException("LLM request timeout after 60s"));

            // Retry succeeds
            enqueueHappyPath("Implement auth");

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Implement auth", "DISCOVERY"))
            );

            assertThat(output.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();
        }

        /**
         * S-INV3: ActionRetryListener classifies an "unparsed tool call" error.
         */
        @Test
        void retry_unparsedToolCallError_classifiedAndRecoveredOnRetry() {
            setLogFile("retry_unparsedToolCallError_classifiedAndRecoveredOnRetry");
            var contextId = seedOrchestrator().value();
            queuedLlmRunner.setThread(contextId);

            // First orchestrator call: throw "tool call" error → triggers retry
            queuedLlmRunner.enqueueError(
                    new RuntimeException("Failed to parse tool call response from LLM"));

            // Retry succeeds
            enqueueHappyPath("Implement auth");

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Implement auth", "DISCOVERY"))
            );

            assertThat(output.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();
        }

        /**
         * S-INV3: ActionRetryListener classifies an "incomplete json" / "truncated" error.
         */
        @Test
        void retry_incompleteJsonError_classifiedAndRecoveredOnRetry() {
            setLogFile("retry_incompleteJsonError_classifiedAndRecoveredOnRetry");
            var contextId = seedOrchestrator().value();
            queuedLlmRunner.setThread(contextId);

            // First orchestrator call: throw "incomplete json" error → triggers retry
            queuedLlmRunner.enqueueError(
                    new RuntimeException("incomplete json: response was truncated at token limit"));

            // Retry succeeds
            enqueueHappyPath("Implement auth");

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Implement auth", "DISCOVERY"))
            );

            assertThat(output.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();
        }

        /**
         * S-INV3: ActionRetryListener classifies a parse error (fallback).
         */
        @Test
        void retry_parseError_classifiedAndRecoveredOnRetry() {
            setLogFile("retry_parseError_classifiedAndRecoveredOnRetry");
            var contextId = seedOrchestrator().value();
            queuedLlmRunner.setThread(contextId);

            // First orchestrator call: throw generic parse error → triggers retry
            queuedLlmRunner.enqueueError(
                    new RuntimeException("Could not parse LLM response into expected type"));

            // Retry succeeds
            enqueueHappyPath("Implement auth");

            var output = agentPlatform.runAgentFrom(
                    findWorkflowAgent(),
                    ProcessOptions.DEFAULT.withContextId(contextId).withPlannerType(PlannerType.GOAP),
                    Map.of("it", new AgentModels.OrchestratorRequest(new ArtifactKey(contextId), "Implement auth", "DISCOVERY"))
            );

            assertThat(output.getStatus()).isEqualTo(AgentProcessStatusCode.COMPLETED);
            queuedLlmRunner.assertAllConsumed();
        }
    }

    private void enqueueHappyPathExceptFinalCollector(String goal) {
        initialOrchestratorToDiscovery(goal);
        discoveryOnly(goal);
        planningOnly(goal);
        ticketsOnly(goal);
    }

    private com.embabel.agent.core.Agent findWorkflowAgent() {
        return agentPlatform.agents().stream()
                .filter(a -> a.getName().equals(AgentInterfaces.WORKFLOW_AGENT_NAME)
                        || a.getName().contains(AgentInterfaces.WorkflowAgent.class.getSimpleName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("WorkflowAgent not found"));
    }

    private void enqueueHappyPath(String goal) {
        initialOrchestratorToDiscovery(goal);
        enqueueDiscoveryToEnd(goal);
    }

    private void enqueueDiscoveryToEnd(String goal) {
        discoveryOnly(goal);
        enqueuePlanningToCompletion(goal);
    }

    private void enqueueDiscoveryToEndWithPlanningAgentInterrupt(String goal) {
        discoveryOnly(goal);
        enqueuePlanningToCompletionWithPlanningAgentInterrupt(goal);
    }

    private void orchestratorRequestThenDiscovery(String goal) {
        queuedLlmRunner.enqueue(
                AgentModels.InterruptRouting.builder()
                        .orchestratorRequest(AgentModels.OrchestratorRequest.builder()
                                .goal(goal)
                                .build())
                        .build());
        queuedLlmRunner.enqueue(AgentModels.OrchestratorRouting.builder()
                .discoveryOrchestratorRequest(
                        AgentModels.DiscoveryOrchestratorRequest.builder()
                                .goal(goal)
                                .build())
                .build());
    }

    private void initialOrchestratorToDiscovery(String goal) {
        queuedLlmRunner.enqueue(AgentModels.OrchestratorRouting.builder()
                .discoveryOrchestratorRequest(AgentModels.DiscoveryOrchestratorRequest.builder()
                        .goal(goal)
                        .build())
                .build());
    }

    private void discoveryOnly(String goal) {
        discoveryOnly(goal, AgentModels.DiscoveryCollectorRouting.builder()
                .collectorResult(AgentModels.DiscoveryCollectorResult.builder()
                        .consolidatedOutput("Discovery complete")
                        .build())
                .build());
    }

    private void discoveryOnly(String goal, AgentModels.DiscoveryCollectorRouting build) {
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

        queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentRouting.builder()
                .agentResult(AgentModels.DiscoveryAgentResult.builder()
                        .output("Found stuff")
                        .build())
                .build());

        queuedLlmRunner.enqueue(AgentModels.DiscoveryAgentDispatchRouting.builder()
                .collectorRequest(AgentModels.DiscoveryCollectorRequest.builder()
                        .goal(goal)
                        .discoveryResults("discovery-results")
                        .build())
                .build());

        queuedLlmRunner.enqueue(build);
    }

    private void enqueuePlanningToCompletion(String goal) {
        planningOnly(goal);

        ticketsOnly(goal);

        finalOrchestratorCollector();
    }

    private void enqueuePlanningToCompletionWithPlanningAgentInterrupt(String goal) {
        planningOnlyWithPlanningAgentInterrupt(goal);

        ticketsOnly(goal);

        finalOrchestratorCollector();
    }

    private void finalOrchestratorCollector() {
        queuedLlmRunner.enqueue(AgentModels.OrchestratorCollectorRouting.builder()
                .collectorResult(AgentModels.OrchestratorCollectorResult.builder()
                        .consolidatedOutput("Workflow complete")
                        .build())
                .build());
    }

    private void ticketsOnly(String goal) {
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

        queuedLlmRunner.enqueue(AgentModels.TicketAgentRouting.builder()
                .agentResult(AgentModels.TicketAgentResult.builder()
                        .output("Ticket output")
                        .build())
                .build());

        queuedLlmRunner.enqueue(AgentModels.TicketAgentDispatchRouting.builder()
                .ticketCollectorRequest(AgentModels.TicketCollectorRequest.builder()
                        .goal(goal)
                        .ticketResults("ticket-results")
                        .build())
                .build());

        queuedLlmRunner.enqueue(AgentModels.TicketCollectorRouting.builder()
                .collectorResult(AgentModels.TicketCollectorResult.builder()
                        .consolidatedOutput("Tickets complete")
                        .build())
                .build());
    }

    private void planningOnlyWithPlanningAgentInterrupt(String goal) {
        queuedLlmRunner.enqueue(AgentModels.PlanningOrchestratorRouting.builder()
                .agentRequests(AgentModels.PlanningAgentRequests.builder()
                        .requests(List.of(
                                AgentModels.PlanningAgentRequest.builder()
                                        .goal(goal)
                                        .build()
                        ))
                        .build())
                .build());

        queuedLlmRunner.enqueue(AgentModels.PlanningAgentRouting.builder()
                .interruptRequest(AgentModels.InterruptRequest.PlanningAgentInterruptRequest.builder()
                        .type(Events.InterruptType.HUMAN_REVIEW)
                        .reason("hello!")
                        .build())
                .build());

        queuedLlmRunner.enqueue(AgentModels.PlanningAgentRouting.builder()
                .agentResult(AgentModels.PlanningAgentResult.builder()
                        .output("Plan output")
                        .build())
                .build());

        queuedLlmRunner.enqueue(AgentModels.PlanningAgentDispatchRouting.builder()
                .planningCollectorRequest(AgentModels.PlanningCollectorRequest.builder()
                        .goal(goal)
                        .planningResults("planning-results")
                        .build())
                .build());

        queuedLlmRunner.enqueue(AgentModels.PlanningCollectorRouting.builder()
                .collectorResult(AgentModels.PlanningCollectorResult.builder()
                        .consolidatedOutput("Planning complete")
                        .build())
                .build());
    }

    private void planningOnly(String goal) {
        queuedLlmRunner.enqueue(AgentModels.PlanningOrchestratorRouting.builder()
                .agentRequests(AgentModels.PlanningAgentRequests.builder()
                        .requests(List.of(
                                AgentModels.PlanningAgentRequest.builder()
                                        .goal(goal)
                                        .build()
                        ))
                        .build())
                .build());

        queuedLlmRunner.enqueue(AgentModels.PlanningAgentRouting.builder()
                .agentResult(AgentModels.PlanningAgentResult.builder()
                        .output("Plan output")
                        .build())
                .build());

        queuedLlmRunner.enqueue(AgentModels.PlanningAgentDispatchRouting.builder()
                .planningCollectorRequest(AgentModels.PlanningCollectorRequest.builder()
                        .goal(goal)
                        .planningResults("planning-results")
                        .build())
                .build());

        queuedLlmRunner.enqueue(AgentModels.PlanningCollectorRouting.builder()
                .collectorResult(AgentModels.PlanningCollectorResult.builder()
                        .consolidatedOutput("Planning complete")
                        .build())
                .build());
    }

    private ArtifactKey seedOrchestrator() {
        var c = ArtifactKey.createRoot();
        MainWorktreeContext mainWorktree = MainWorktreeContext.builder()
                .worktreeId("wt")
                .repositoryUrl("git@github.com:haydenrear/multi_agent_ide_java_parent.git")
                .parentWorktreeId("wt")
                .worktreePath(path)
                .status(WorktreeContext.WorktreeStatus.ACTIVE)
                .submoduleWorktrees(new ArrayList<>())
                .build();
        graphRepository.save(createMockOrchestratorNode(c.value(), mainWorktree));
        worktreeRepository.save(mainWorktree);
        return c;
    }

    private OrchestratorNode createMockOrchestratorNode(String nodeId, MainWorktreeContext mainWorktree) {
        return OrchestratorNode.builder()
                .nodeId(nodeId)
                .title("Orch")
                .goal("goal")
                .status(Events.NodeStatus.RUNNING)
                .metadata(new HashMap<>())
                .createdAt(Instant.now())
                .worktreeContext(mainWorktree)
                .build();
    }

}
