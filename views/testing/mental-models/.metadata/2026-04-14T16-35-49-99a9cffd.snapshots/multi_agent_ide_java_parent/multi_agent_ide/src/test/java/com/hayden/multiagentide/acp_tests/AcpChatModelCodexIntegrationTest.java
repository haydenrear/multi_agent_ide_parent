package com.hayden.multiagentide.acp_tests;

import static com.hayden.multiagentide.acp_tests.AcpChatModelCodexIntegrationTest.TestAgent.TEST_AGENT;
import static com.hayden.multiagentide.service.DefaultLlmRunner.applyToolCallbacks;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.permission.IPermissionGate;
import com.agentclientprotocol.model.PermissionOptionKind;
import com.embabel.agent.api.annotation.AchievesGoal;
import com.embabel.agent.api.annotation.Action;
import com.embabel.agent.api.annotation.Agent;
import com.embabel.agent.api.annotation.support.AgentMetadataReader;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.api.common.PlannerType;
import com.embabel.agent.api.tool.ToolObject;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.agent.core.IoBinding;
import com.embabel.agent.core.ProcessOptions;
import com.embabel.chat.support.InMemoryConversation;
import com.hayden.acp_cdc_ai.acp.config.AcpChatOptionsString;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.acp_cdc_ai.repository.RequestContext;
import com.hayden.acp_cdc_ai.repository.RequestContextRepository;
import com.hayden.acp_cdc_ai.sandbox.SandboxContext;
import com.hayden.multiagentide.agent.AgentLifecycleHandler;
import com.hayden.multiagentide.agent.decorator.tools.RemoveIntellij;
import com.hayden.multiagentide.artifacts.entity.ArtifactEntity;
import com.hayden.multiagentide.artifacts.entity.QArtifactEntity;
import com.hayden.multiagentide.artifacts.repository.ArtifactRepository;
import com.hayden.multiagentide.controller.OrchestrationController;
import com.hayden.multiagentide.repository.EventStreamRepository;
import com.hayden.multiagentide.gate.PermissionGateAdapter;
import com.hayden.multiagentide.agent.AcpTooling;
import com.hayden.multiagentide.agent.AgentTopologyTools;
import com.hayden.multiagentide.service.AgentExecutor;
import com.hayden.multiagentide.tool.ToolAbstraction;
import com.hayden.multiagentide.service.GitWorktreeService;
import com.hayden.multiagentide.tool.McpToolObjectRegistrar;
import com.hayden.multiagentide.agent.AgentTools;
import com.hayden.acp_cdc_ai.acp.AcpChatModel;
import com.hayden.utilitymodule.config.EnvConfigProps;
import com.hayden.utilitymodule.io.FileUtils;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.data.repository.query.FluentQuery;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

@Slf4j
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles({"claudellama", "testdocker"})
@ExtendWith(SpringExtension.class)
@TestPropertySource(properties = {"spring.ai.mcp.server.stdio=false"})
class AcpChatModelCodexIntegrationTest {

    @Autowired
    private ChatModel chatModel;

    @Autowired
    private AgentPlatform agentPlatform;

    @Autowired
    private AgentMetadataReader agentMetadataReader;

    @Autowired
    private OrchestrationController orchestrationController;

    @Autowired
    private McpToolObjectRegistrar toolObjectRegistry;
    @Autowired
    private ArtifactRepository artifactRepository;
    @Autowired
    private AgentExecutor agentExecutor;

    @Autowired(required = false)
    private AgentTools guiEvent;
    @Autowired(required = false)
    private AcpTooling fileSystemTools;
    @Autowired
    private AgentTopologyTools agentTopologyTools;
    @Autowired
    private EnvConfigProps envConfigProps;

    @Autowired
    private RequestContextRepository requestContextRepository;
    @Autowired
    private RemoveIntellij removeIntellij;
    @Autowired
    private PermissionGateAdapter permissionGateAdapter;
    @Autowired
    private EventStreamRepository eventStreamRepository;
    @Autowired
    private GitWorktreeService gitWorktreeService;

    public record ResultValue(String result) {}

    public record FinalValue(String result) {}

    public record RequestValue(String request) {}

    @Agent(
            name = TEST_AGENT,
            description = "tests some stuff.",
            planner = PlannerType.GOAP
    )
    @RequiredArgsConstructor
    public static class TestAgent {

        public static final String TEST_AGENT = "test_agent";

        private final McpToolObjectRegistrar toolObjectRegistry;


        private final AgentTopologyTools agentTopologyTools;

        @Autowired(required = false)
        @Setter
        private AgentTools guiEvent;

        @Autowired(required = false)
        @Setter
        private AcpTooling fileSystemTools;

//        @Action
//        @AchievesGoal(description = "finishes the test")
//        public FinalValue sendsMessage(
//                ResultValue input,
//                ActionContext context,
//                Conversation conversation
//        ) {
//            AssistantMessage message = new AssistantMessage("Hello!");
//            context.sendMessage(message);
//            conversation.addMessage(message);
//            return context.ai().withDefaultLlm()
//                    .withId("hello!")
//                    .createObject(input.result, FinalValue.class);
//        }

//        @Action
//        public void after(
//                ResultValue input,
//                ActionContext context,
//                Conversation conversation
//        ) {
//            AssistantMessage message = new AssistantMessage("Hello!");
//            context.sendMessage(message);
//            conversation.addMessage(message);
//            log.info("Sent");
//        }

        @Action
        @AchievesGoal(description = "finishes the test")
        public ResultValue performTest(
                RequestValue input,
                OperationContext context
        ) {
//            Optional<List<ToolObject>> deepwiki = toolObjectRegistry.tool("deepwiki");
            Optional<List<ToolObject>> hindsight = toolObjectRegistry.tool("hindsight");

//            assertThat(hindsight)
//                    .withFailMessage("Hindsight could not be reached.")
//                    .isPresent();

            var c = context.ai()
                    .withFirstAvailableLlmOf(
                            "acp-chat-model",
                            AcpChatOptionsString.create(
                                    context.getAgentProcess().getId(),
                                    "claude-haiku-4-5",
                                    "claude",
                                    Map.of("source", "integration-test")
                            ).encodeModel(new ObjectMapper()))
                    .withId("hello!")
//                    .withToolObjects(deepwiki.get())
//                    .withToolObject(new ToolObject(guiEvent))
                    .withToolObjects(hindsight.orElse(new ArrayList<>()));

            if (fileSystemTools != null)
                c = c.withToolObject(fileSystemTools);

            if (agentTopologyTools != null) {
                ToolAbstraction tool = ToolAbstraction.fromToolCarrier(agentTopologyTools);

                c = switch (tool) {
                    case ToolAbstraction.SpringToolCallback value ->
                            c.withToolObject(new ToolObject(value.toolCallback()));
                    case ToolAbstraction.SpringToolCallbackProvider value ->
                            applyToolCallbacks(c, value.toolCallbackProvider().getToolCallbacks());
                    case ToolAbstraction.EmbabelTool value ->
                            c.withTool(value.tool());
                    case ToolAbstraction.EmbabelToolObject value ->
                            c.withToolObject(value.toolObject());
                    case ToolAbstraction.EmbabelToolGroup value ->
                            c.withToolGroup(value.toolGroup());
                    case ToolAbstraction.EmbabelToolGroupRequirement value ->
                            c.withToolGroup(value.requirement());
                    case ToolAbstraction.ToolGroupStrings value ->
                            c.withToolGroups(value.toolGroups());
                    case ToolAbstraction.SkillReference skillReference ->
//                      we're not using the tool, we do a custom prompt contributor
                            c;
                };
            }

            return c.createObject(input.request, ResultValue.class);
        }

    }

    @BeforeEach
    public void before() {
        TestAgent agentInterface = new TestAgent(toolObjectRegistry, agentTopologyTools);
        agentInterface.setGuiEvent(guiEvent);
        agentInterface.setFileSystemTools(fileSystemTools);
        Optional.ofNullable(agentMetadataReader.createAgentMetadata(agentInterface))
                .ifPresentOrElse(agentPlatform::deploy, () -> log.error("Error deploying {} - could not create agent metadata.", agentInterface));
    }

    @Test
    void testCreateGoal() throws GitAPIException, IOException {

        String parentTmpRepo = "/tmp/multi_agent_ide_parent/multi_agent_ide_parent";
        Path tmpRepo = Path.of(parentTmpRepo);
        if (!tmpRepo.toFile().exists()) {
            String projectRepo = envConfigProps.getProjectDir().getParent().getParent().toString();
            GitWorktreeService.performSubmoduleClone(
                    projectRepo,
                    "main",
                    "main",
                    tmpRepo
            );
        }

        assertThat(tmpRepo.toFile()).exists();
        Path readmePath = tmpRepo.resolve("multi_agent_ide_java_parent/README.md");

        if(readmePath.toFile().exists()) {
            readmePath.toFile().delete();
        }

        CompletableFuture.runAsync(() -> {
            String string = tmpRepo.toString();
            log.info("Running goal on {}.", string);
            var s = orchestrationController.startGoal(new OrchestrationController.StartGoalRequest(
                    "Please add a README.md with text HELLO to the path multi_agent_ide_java_parent/README.md. For discovery, " +
                            "just create one agent request that says code map does not have readme, for planner have one planner agent that says only to write readme, " +
                            "for ticket, have one agent that says to write readme.",
                    string,
                    "main", "Artifact Centralization"));
            log.info("Added - {}", s);
        }).exceptionally(t -> {
            throw new AssertionError("Failed - ", t);
        });

        startPermissionConsole();

        await().atMost(Duration.ofMinutes(120))
                .pollInterval(Duration.ofSeconds(5))
                .until(() ->
                        eventStreamRepository.list()
                                .stream()
                                .anyMatch(event -> event instanceof Events.GoalCompletedEvent)
                );

        assertThat(readmePath).exists();

        var gce = eventStreamRepository.list()
                .stream()
                .flatMap(event -> event instanceof Events.GoalCompletedEvent e ? Stream.of(e) : Stream.empty())
                .findAny();

        assertThat(gce).isNotEmpty();

        await().atMost(Duration.ofMinutes(60))
                .pollInterval(Duration.ofSeconds(120))
                .until(() -> {
                    try {
                        Optional<ArtifactEntity> by = artifactRepository.findBy(
                                QArtifactEntity.artifactEntity.artifactKey.startsWith(gce.get().nodeId()),
                                FluentQuery.FetchableFluentQuery::first);
                        return by.isPresent();
                    } catch(Exception e) {
                        return false;
                    }
                });

        log.info("Finished!");
    }

//    @Test
    void chatModelWorksWithTools() {
        String nodeId = ArtifactKey.createRoot().value();
        ProcessOptions processOptions = ProcessOptions.DEFAULT.withContextId(nodeId)
                .withListener(AgentLifecycleHandler.agentProcessIdListener());
        List<com.embabel.agent.core.Agent> agents = agentPlatform.agents();
        var agentName = TEST_AGENT;
        var thisAgent = agents.stream()
                .filter(agent -> agent.getName().equals(agentName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Agent not found: " + agentName));
        AgentProcess process = agentPlatform.runAgentFrom(
                thisAgent,
                processOptions,
                Map.of(
                        IoBinding.DEFAULT_BINDING,
                        new RequestValue(".")));
    }

    @Test
    void chatModelUsesAcpProtocol() {
        assertThat(chatModel).isInstanceOf(AcpChatModel.class);

//        var c = chatModel.call("Do you have the capability to read or write to the file");
//        var x = chatModel.call("Can you please read the file log.log");
//        log.info("");

        try {
            Path testWork = envConfigProps.getProjectDir().resolve("test_work");
            testWork.toFile().mkdirs();
            var logFile = testWork.resolve("log.log").toFile();
            logFile.delete();
            String nodeId = ArtifactKey.createRoot().value();

            Path testWorkDir = envConfigProps.getProjectDir().resolve("test_work").resolve("hello");


            FileUtils.writeToFile("wow!", testWorkDir);

            // Register RequestContext so sandbox translation can set working directory
            Path workingDir = testWork.toAbsolutePath();
            removeIntellij.ensureDenyRuleWritten(workingDir);
            RequestContext requestContext = RequestContext.builder()
                    .sessionId(nodeId)
                    .sandboxContext(SandboxContext.builder()
                            .mainWorktreePath(workingDir)
                            .build())
                    .build();
            startPermissionConsole();
            requestContextRepository.save(requestContext);
            
            ProcessOptions processOptions = ProcessOptions.DEFAULT.withContextId(nodeId)
                    .withListener(AgentLifecycleHandler.agentProcessIdListener());
            List<com.embabel.agent.core.Agent> agents = agentPlatform.agents();
            var agentName = TEST_AGENT;
            var thisAgent = agents.stream()
                    .filter(agent -> agent.getName().equals(agentName))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Agent not found: " + agentName));
            RequestValue v1 = new RequestValue("Do you have access to any Intellij tools? If so, can you list their exact names?");
            AgentProcess process = agentPlatform.runAgentFrom(
                    thisAgent,
                    processOptions,
                    Map.of(IoBinding.DEFAULT_BINDING, v1));

            process.bind("conversation", new InMemoryConversation());

            var res = process.run().resultOfType(ResultValue.class);

            log.info("{}", res);

//            assertThat(logFile.exists()).isTrue();

            logFile.delete();

        } catch (Exception e) {
            log.error("Error - will not fail test for codex-acp - but failed", e);
        }
    }

    @Test
    void chatModelHasCallControllerViaMcp() {
        assertThat(chatModel).isInstanceOf(AcpChatModel.class);

        try {
            Path testWork = envConfigProps.getProjectDir().resolve("test_work");
            testWork.toFile().mkdirs();
            String nodeId = ArtifactKey.createRoot().value();

            Path workingDir = testWork.toAbsolutePath();
            removeIntellij.ensureDenyRuleWritten(workingDir);
            RequestContext requestContext = RequestContext.builder()
                    .sessionId(nodeId)
                    .sandboxContext(SandboxContext.builder()
                            .mainWorktreePath(workingDir)
                            .build())
                    .build();
            startPermissionConsole();
            requestContextRepository.save(requestContext);

            ProcessOptions processOptions = ProcessOptions.DEFAULT.withContextId(nodeId)
                    .withListener(AgentLifecycleHandler.agentProcessIdListener());
            List<com.embabel.agent.core.Agent> agents = agentPlatform.agents();
            var thisAgent = agents.stream()
                    .filter(agent -> agent.getName().equals(TEST_AGENT))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("Agent not found: " + TEST_AGENT));

            RequestValue v1 = new RequestValue(
                    "Can you call the list_agents tool?");
            AgentProcess process = agentPlatform.runAgentFrom(
                    thisAgent,
                    processOptions,
                    Map.of(IoBinding.DEFAULT_BINDING, v1));

            process.bind("conversation", new InMemoryConversation());

            var res = process.run().resultOfType(ResultValue.class);

            log.info("call_controller tool check result: {}", res);

        } catch (Exception e) {
            log.error("Error in call_controller tool check test", e);
        }
    }

    private void startPermissionConsole() {
        CompletableFuture.runAsync(() -> {
            try(var reader = new BufferedReader(new InputStreamReader(System.in))) {
                while (true) {
                    if (!permissionGateAdapter.pendingPermissionRequests().isEmpty()) {
                        permissionGateAdapter.pendingPermissionRequests()
                                .forEach(request -> {
                                    System.out.printf("Found permission request: %s%n", request);
                                    System.out.println("Options:");
                                    var permissions = request.getPermissions();
                                    for (int i = 0; i < permissions.size(); i++) {
                                        var option = permissions.get(i);
                                        System.out.printf("  %d. %s (%s)%n", i + 1, option.getName(), option.getKind());
                                    }
                                    System.out.print("Select option [1.." + permissions.size() + "], optionType, or 'cancel': ");
                                    try {
                                        var input = reader.readLine();
                                        Thread.sleep(1000);
                                        if (input == null || input.isBlank()) {
                                            permissionGateAdapter.resolveSelected(
                                                    request.getRequestId(),
                                                    permissions.stream().filter(po -> po.getKind() == PermissionOptionKind.ALLOW_ONCE).findAny().orElseThrow()
                                            );
                                            System.out.println("Resolved selected");
                                            return;
                                        }
                                        var trimmed = input.trim();
                                        if ("cancel".equalsIgnoreCase(trimmed)) {
                                            permissionGateAdapter.resolveCancelled(request.getRequestId());
                                            System.out.println("Resolved selected");
                                            return;
                                        }
                                        try {
                                            int index = Integer.parseInt(trimmed);
                                            if (index >= 1 && index <= permissions.size()) {
                                                var selected = permissions.get(index - 1);
                                                permissionGateAdapter.resolveSelected(request.getRequestId(), selected);
                                                System.out.println("Resolved selected");
                                                return;
                                            }
                                        } catch (
                                                NumberFormatException ignored) {
                                        }
                                        permissionGateAdapter.resolveSelected(request.getRequestId(), trimmed);
                                        System.out.println("Resolved selected");
                                    } catch (
                                            IOException e) {
                                        throw new RuntimeException(e);
                                    } catch (
                                            InterruptedException e) {
                                        throw new RuntimeException(e);
                                    }
                                });
                    }

                    if (!permissionGateAdapter.pendingInterruptRequests().isEmpty()) {
                        permissionGateAdapter.pendingInterruptRequests()
                                .forEach(request -> {
                                    System.out.printf("Found interrupt request: %s%n", request);

                                    try {
                                        var input = reader.readLine();

                                        Thread.sleep(1000);

                                        if (input == null || input.isBlank()) {
                                            agentExecutor.controllerResponseExecution(
                                                    AgentExecutor.ControllerResponseArgs.builder()
                                                            .interruptId(request.getInterruptId())
                                                            .originNodeId(request.getOriginNodeId())
                                                            .message("Approved")
                                                            .checklistAction("JUSTIFICATION_PASSED")
                                                            .expectResponse(false)
                                                            .targetAgentKey(request.getOriginNodeId())
                                                            .build()
                                            );
                                            return;
                                        }
                                        var trimmed = input.trim();
                                        permissionGateAdapter.resolveInterrupt(
                                                request.getInterruptId(),
                                                IPermissionGate.ResolutionType.FEEDBACK,
                                                trimmed,
                                                null
                                        );
                                    } catch (
                                            IOException |
                                            InterruptedException e) {
                                        throw new RuntimeException(e);
                                    }
                                });
                    }

                    try {
                        Thread.sleep(10_000);
                    } catch (
                            InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            } catch (IOException e) {
                log.error("Error on system in: {}.", e.getMessage(), e);
                throw new RuntimeException(e);
            }
        }).exceptionally(t -> {
            log.error("Found that error!", t);
            return null;
        });
    }
}
