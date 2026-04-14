package com.hayden.multiagentide.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.embabel.agent.api.annotation.support.AgentMetadataReader;
import com.embabel.agent.api.channel.*;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentScope;
import com.embabel.agent.core.support.DefaultAgentPlatform;
import com.embabel.agent.spi.AgentProcessIdGenerator;
import com.embabel.agent.spi.LlmService;
import com.embabel.agent.spi.support.springai.SpringAiLlmService;
import com.embabel.common.ai.model.*;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.acp_cdc_ai.acp.config.AcpChatOptionsString;
import com.hayden.multiagentide.agent.AgentInterfaces;
import com.hayden.multiagentide.agent.AgentQuestionAnswerFunction;
import com.hayden.multiagentide.agent.AskUserQuestionTool;
import com.hayden.acp_cdc_ai.acp.config.AcpModelProperties;
import com.hayden.acp_cdc_ai.acp.config.McpProperties;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.AcpChatModel;

import io.micrometer.common.util.StringUtils;
import io.micrometer.context.ThreadLocalAccessor;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.util.ReflectionUtils;
import reactor.core.publisher.Flux;

import java.lang.reflect.Field;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Embabel configuration for chat models and LLM integration.
 */
@Slf4j
@Configuration
@EnableConfigurationProperties({AcpModelProperties.class, McpProperties.class, LlmModelSelectionProperties.class, com.hayden.multiagentide.topology.CommunicationTopologyConfig.class})
@ComponentScan(basePackages = {"com.hayden.acp_cdc_ai"})
public class MultiAgentEmbabelConfig {

    @Value("${multi-agent-embabel.chat-model.provider:acp}")
    private String modelProvider;


    @SneakyThrows
    @Bean
    public ApplicationRunner deployAgents(List<AgentInterfaces> agents,
                                          AgentPlatform agentPlatform,
                                          AgentMetadataReader agentMetadataReader,
                                          BlackboardRoutingPlannerFactory b) {
        if (agentPlatform instanceof DefaultAgentPlatform) {
            Field declaredField = agentPlatform.getClass().getDeclaredField("plannerFactory");
            declaredField.trySetAccessible();
            ReflectionUtils.setField(declaredField, agentPlatform, b);
        }
        for (AgentInterfaces agent : agents) {
            AgentScope agentMetadata = agentMetadataReader.createAgentMetadata(agent);
            deployAgent(
                    agentMetadata,
                    agentPlatform,
                    agent.getClass().getName());
        }


        return args -> {
        };
    }

    @Bean
    public AskUserQuestionTool askUserQuestionTool(AgentQuestionAnswerFunction agentQuestionAnswerFunction) {
        return AskUserQuestionTool.builder()
                .questionAnswerFunction(agentQuestionAnswerFunction)
                .answersValidation(false)
                .build();
    }

    private static void deployAgent(AgentScope agentMetadataReader, AgentPlatform agentPlatform, String workflowAgent) {
        Optional.ofNullable(agentMetadataReader)
                .ifPresentOrElse(s -> {
                    log.info("Starting deployment of {}", s.getName());
                    agentPlatform.deploy(s);
                    log.info("Finished deployment of {}", s.getName());
                }, () -> log.error(
                        "Error deploying {} - could not create agent metadata.",
                        workflowAgent
                ));
    }

    @Bean
    @Profile("openai")
    public ApplicationRunner agenticApplicationRunner() {
        return args -> {

        };
    }

    @Bean
    @Primary
    public AgentProcessIdGenerator nodeIdAgentProcessIdGenerator() {
        return (agent, processOptions) -> Optional.ofNullable(processOptions.getContextIdString())
                .orElseGet(() -> AgentProcessIdGenerator.Companion.getRANDOM().createProcessId(agent, processOptions));
    }

    @Bean(name = "chatModel")
    @Primary
    public ChatModel acpChatModel(AcpChatModel chatModel) {
        return chatModel;
    }

    /**
     * Create Streaming Chat Language Model for streaming responses.
     */
    @Bean
    public StreamingChatModel streamingChatLanguageModel(AcpChatModel chatModel) {
        return chatModel;
    }

    public record EmbabelAcpChatOptions(@Delegate ChatOptions chatOptions, String sessionId, String encodedModel)
            implements ChatOptions {}

    @Bean
    public LlmService<SpringAiLlmService> llm(org.springframework.ai.chat.model.ChatModel chatModel, ObjectMapper objectMapper) {
        OptionsConverter<ChatOptions> optionsConverter = new OptionsConverter<>() {
            @Override
            public @NonNull ChatOptions convertOptions(@NonNull LlmOptions options) {
                String encodedModel = resolveEncodedAcpModel(options, objectMapper);
                String sessionId = AcpChatOptionsString.fromEncodedModel(encodedModel, objectMapper).sessionArtifactKey();
                var tc = ToolCallingChatOptions.builder()
                        .model(encodedModel)
                        .temperature(options.getTemperature())
                        .topP(options.getTopP())
                        .maxTokens(options.getMaxTokens())
                        .presencePenalty(options.getPresencePenalty())
                        .frequencyPenalty(options.getFrequencyPenalty())
                        .topP(options.getTopP())
                        .build();

                return new EmbabelAcpChatOptions(tc, sessionId, encodedModel);
            }
        };

        return new SpringAiLlmService("acp-chat-model", modelProvider, chatModel)
                .withOptionsConverter(optionsConverter);
    }

    private static String resolveEncodedAcpModel(LlmOptions options, ObjectMapper objectMapper) {
        String directModel = options.getModel();
        if (AcpChatOptionsString.looksLikeEncodedModel(directModel)) {
            return directModel;
        }

        String sessionIdentity = null;
        String requestedModel = normalize(options.getModel());

        if (options.getModelSelectionCriteria() instanceof FallbackByNameModelSelectionCriteria fallback) {
            List<String> names = fallback.getNames();
            if (!names.isEmpty()) {
                sessionIdentity = normalize(names.getLast());
            }
            if (names.size() > 1) {
                requestedModel = normalize(names.get(1));
            }
        }

        if (sessionIdentity == null) {
            sessionIdentity = normalize(options.getModel());
        }
        if (sessionIdentity == null) {
            sessionIdentity = ArtifactKey.createRoot().value();
        }

        return AcpChatOptionsString.create(sessionIdentity, requestedModel, null, java.util.Map.of())
                .encodeModel(objectMapper);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank() || AcpChatOptionsString.DEFAULT_MODEL_NAME.equalsIgnoreCase(value)) {
            return null;
        }
        return value;
    }

    @Bean
    @Primary
    public OutputChannel llmOutputChannel(ChatModel chatModel, @Lazy AgentPlatform agentPlatform,
                                           com.hayden.multiagentide.service.SessionKeyResolutionService sessionKeyResolutionService) {
        return new MulticastOutputChannel(List.of(
                (OutputChannel) event -> {
                    switch (event) {
                        case MessageOutputChannelEvent evt -> {
                            String content = evt.getMessage().getContent();

                            if (StringUtils.isBlank(evt.getProcessId())) {
                                return;
                            }

                            if (StringUtils.isNotBlank(content) && content.startsWith("proc:")) {
//                                TODO:
                            } else if (StringUtils.isNotBlank(content) && content.startsWith("stop-q")) {
                                var ap = agentPlatform.getAgentProcess(evt.getProcessId());

                                Optional.ofNullable(ap)
                                        .ifPresentOrElse(
                                                proc -> {
                                                    log.info("Received STOPQ - killing process {}.", proc.getId());
                                                    var killed = proc.kill();
                                                    log.info("Received STOPQ - killed process {}, {}.", proc.getId(), killed);
                                                },
                                                () -> log.info("Received kill request for unknown process, {}", evt.getProcessId()));
                            } else {
                                try {
                                    new ArtifactKey(evt.getProcessId());
                                } catch (IllegalArgumentException e) {
                                    log.debug("Error attempting to decode {} into artifact key.", evt.getProcessId());
                                    return;
                                }

                                var thisArtifactKeyForMessage = sessionKeyResolutionService.resolveSessionForMessage(evt);

                                if (thisArtifactKeyForMessage.isEmpty()) {
                                    log.error("Could not find valid chat session to add message to for {}.", event.getProcessId());
                                    return;
                                }

                                var prev = EventBus.Process.get();

                                try {
                                    EventBus.Process.set(new EventBus.AgentNodeKey(thisArtifactKeyForMessage.get().value()));
                                    chatModel.call(new Prompt(
                                            new AssistantMessage(content),
                                            ToolCallingChatOptions.builder()
                                                    .model(thisArtifactKeyForMessage.get().value())
                                                    .build()));
                                } finally {
                                    EventBus.Process.set(prev);
                                }
                            }
                        }
                        default -> {
                            log.info("Received event {} for {} - ignoring.", event.getClass().getName(), event.getProcessId());
                        }
                    }
                },
                DevNullOutputChannel.INSTANCE
        ));
    }

    /**
     * Mock Streaming ChatLanguageModel for testing without OpenAI API key.
     */
    private static class MockStreamingChatLanguageModel implements StreamingChatModel {
        @Override
        public @org.jspecify.annotations.NonNull Flux<ChatResponse> stream(Prompt prompt) {
            return Flux.just(ChatResponse.builder().build());
        }
    }
}
