package com.hayden.multiagentide.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.commitdiffcontext.cdc_config.SkipInSchemaFilter;
import com.hayden.commitdiffcontext.cdc_config.SkipMcpToolContextFilter;
import com.hayden.commitdiffcontext.cdc_config.SkipSetFromSessionHeader;
import com.hayden.commitdiffcontext.cdc_utils.SetFromHeader;
import com.hayden.commitdiffcontext.mcp.ToolCarrier;
import com.hayden.commitdiffmodel.config.DisableGraphQl;
import com.hayden.persistence.config.JpaConfig;
import com.hayden.utilitymodule.mcp.ctx.McpRequestContext;
import com.hayden.utilitymodule.schema.DelegatingSchemaReplacer;
import com.hayden.utilitymodule.schema.SpecialJsonSchemaGenerator;
import com.hayden.utilitymodule.schema.SpecialMethodToolCallbackProvider;
import com.hayden.utilitymodule.schema.SpecialMethodToolCallbackProviderFactory;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.*;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.IdeMcpServer;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import io.modelcontextprotocol.spec.McpServerTransportProviderBase;
import io.modelcontextprotocol.spec.McpStreamableServerTransportProvider;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.mcp.McpToolUtils;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerChangeNotificationProperties;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerProperties;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import org.springframework.web.context.support.StandardServletEnvironment;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Configuration
@Import({DelegatingSchemaReplacer.class, SpecialJsonSchemaGenerator.class, SpecialMethodToolCallbackProviderFactory.class, SkipInSchemaFilter.class,
         DisableGraphQl.class, SkipMcpToolContextFilter.class, SkipSetFromSessionHeader.class, JpaConfig.class})
@Slf4j
@EnableConfigurationProperties({McpServerProperties.class, McpServerChangeNotificationProperties.class})
public class SpringMcpConfig {

    @Bean
    public SecurityFilterChain security(HttpSecurity http) throws Exception {
        return http.authorizeHttpRequests(a -> a.anyRequest().permitAll())
                        .cors(AbstractHttpConfigurer::disable)
                        .csrf(AbstractHttpConfigurer::disable)
                        .build();
    }

    @Bean
    public ToolCallbackProvider tools(List<ToolCarrier> codeSearchMcpTools,
                                      SpecialMethodToolCallbackProviderFactory specialMethodToolCallbackProvider,
                                      SpecialJsonSchemaGenerator specialJsonSchemaGenerator,
                                      DelegatingSchemaReplacer schemaReplacer,
                                      ApplicationContext ctx) {
        log.info("MCP tool registration: {} ToolCarrier beans injected: {}",
                codeSearchMcpTools.size(),
                codeSearchMcpTools.stream().map(tc -> tc.getClass().getSimpleName()).toList());
        var filteredTools = codeSearchMcpTools.stream()
                .filter(SpringMcpConfig::hasToolMethod)
                .map(tc -> (Object) tc)
                .toList();
        log.info("MCP tool registration: {} passed hasToolMethod filter: {}",
                filteredTools.size(),
                filteredTools.stream().map(tc -> tc.getClass().getSimpleName()).toList());
        var tcp = specialMethodToolCallbackProvider.createToolCallbackProvider(
                filteredTools,
                specialJsonSchemaGenerator,
                schemaReplacer,
                ctx);
        return tcp;
    }

    public static boolean hasToolMethod(Object obj) {
        var objP = AopProxyUtils.getSingletonTarget(obj);
        return Arrays.stream((objP == null ? obj : objP).getClass().getDeclaredMethods())
                .anyMatch(m -> m.isAnnotationPresent(Tool.class));
    }

    @Bean
    public RouterFunction<ServerResponse> mvcMcpRouterFunction(WebMvcStreamableServerTransportProvider transportProvider) {
        return transportProvider.getRouterFunction();
    }

    @Bean
    @ConditionalOnMissingBean
    public McpSchema.ServerCapabilities.Builder capabilitiesBuilder() {
        return McpSchema.ServerCapabilities.builder();
    }

    @SneakyThrows
    @Bean("mcpSyncServer")
    @Primary
    public McpSyncServer mcpSyncServer(McpServerTransportProviderBase transportProvider,
                                       McpSchema.ServerCapabilities.Builder capabilitiesBuilder,
                                       McpServerProperties serverProperties,
                                       ObjectMapper objectMapper,
                                       McpServerChangeNotificationProperties changeNotificationProperties,
                                       ObjectProvider<List<McpServerFeatures.SyncToolSpecification>> tools,
                                       ObjectProvider<List<McpServerFeatures.SyncResourceTemplateSpecification>> resourceTemplates,
                                       ObjectProvider<List<McpServerFeatures.SyncResourceSpecification>> resources,
                                       ObjectProvider<List<McpServerFeatures.SyncPromptSpecification>> prompts,
                                       ObjectProvider<List<McpServerFeatures.SyncCompletionSpecification>> completions,
                                       ObjectProvider<BiConsumer<McpSyncServerExchange, List<McpSchema.Root>>> rootsChangeConsumers,
                                       List<ToolCallbackProvider> toolCallbackProvider,
                                       Environment environment) {
        McpSchema.Implementation serverInfo = new McpSchema.Implementation(serverProperties.getName(),
                serverProperties.getVersion());

        IdeMcpServer.SyncSpecification<?> serverBuilder;
        if (transportProvider instanceof McpStreamableServerTransportProvider) {
            serverBuilder = IdeMcpServer.sync((McpStreamableServerTransportProvider) transportProvider);
        }
        else {
            serverBuilder = IdeMcpServer.sync((McpServerTransportProvider) transportProvider);
        }
        serverBuilder.serverInfo(serverInfo);

        int numTools = 0;

        // Tools
        if (serverProperties.getCapabilities().isTool()) {
            log.info("Enable tools capabilities, notification: "
                    + changeNotificationProperties.isToolChangeNotification());
            capabilitiesBuilder.tools(changeNotificationProperties.isToolChangeNotification());

            List<McpServerFeatures.SyncToolSpecification> toolSpecifications = new ArrayList<>(
                    tools.stream().flatMap(List::stream).toList());


            if (!CollectionUtils.isEmpty(toolSpecifications)) {
                var filtered = toolSpecifications.stream().filter(Predicate.not(st -> toolCallbackProvider.stream().anyMatch(tcp -> Arrays.stream(tcp.getToolCallbacks()).anyMatch(tc -> st.tool().name().equals(tc.getToolDefinition().name())))))
                                .toList();
                numTools += filtered.size();
                serverBuilder.tools(filtered);
            }
        }

        // Resources
        if (serverProperties.getCapabilities().isResource()) {
            log.info("Enable resources capabilities, notification: "
                    + changeNotificationProperties.isResourceChangeNotification());
            capabilitiesBuilder.resources(false, changeNotificationProperties.isResourceChangeNotification());

            List<McpServerFeatures.SyncResourceSpecification> resourceSpecifications = resources.stream().flatMap(List::stream).toList();
            if (!CollectionUtils.isEmpty(resourceSpecifications)) {
                serverBuilder.resources(resourceSpecifications);
                log.info("Registered resources: " + resourceSpecifications.size());
            }
        }

        // Resources Templates
        if (serverProperties.getCapabilities().isResource()) {
            log.info("Enable resources templates capabilities, notification: "
                    + changeNotificationProperties.isResourceChangeNotification());
            capabilitiesBuilder.resources(false, changeNotificationProperties.isResourceChangeNotification());

            List<McpServerFeatures.SyncResourceTemplateSpecification> resourceTemplateSpecifications = resourceTemplates.stream()
                    .flatMap(List::stream)
                    .toList();
            if (!CollectionUtils.isEmpty(resourceTemplateSpecifications)) {
                serverBuilder.resourceTemplates(resourceTemplateSpecifications);
                log.info("Registered resource templates: " + resourceTemplateSpecifications.size());
            }
        }

        // Prompts
        if (serverProperties.getCapabilities().isPrompt()) {
            log.info("Enable prompts capabilities, notification: "
                    + changeNotificationProperties.isPromptChangeNotification());
            capabilitiesBuilder.prompts(changeNotificationProperties.isPromptChangeNotification());

            List<McpServerFeatures.SyncPromptSpecification> promptSpecifications = prompts.stream().flatMap(List::stream).toList();
            if (!CollectionUtils.isEmpty(promptSpecifications)) {
                serverBuilder.prompts(promptSpecifications);
                log.info("Registered prompts: " + promptSpecifications.size());
            }
        }

        // Completions
        if (serverProperties.getCapabilities().isCompletion()) {
            log.info("Enable completions capabilities");
            capabilitiesBuilder.completions();

            List<McpServerFeatures.SyncCompletionSpecification> completionSpecifications = completions.stream()
                    .flatMap(List::stream)
                    .toList();
            if (!CollectionUtils.isEmpty(completionSpecifications)) {
                serverBuilder.completions(completionSpecifications);
                log.info("Registered completions: " + completionSpecifications.size());
            }
        }

        rootsChangeConsumers.ifAvailable(consumer -> {
            BiConsumer<McpSyncServerExchange, List<McpSchema.Root>> syncConsumer = (exchange, roots) -> consumer
                    .accept(exchange, roots);
            serverBuilder.rootsChangeHandler(syncConsumer);
            log.info("Registered roots change consumer");
        });

        serverBuilder.capabilities(capabilitiesBuilder.build());

        serverBuilder.instructions(serverProperties.getInstructions());

        serverBuilder.requestTimeout(serverProperties.getRequestTimeout());

        if (environment instanceof StandardServletEnvironment) {
            serverBuilder.immediateExecution(true);
        }


        McpSyncServer built = serverBuilder.build();

        var providers = toolCallbackProvider
                .stream().flatMap(t -> Arrays.stream(t.getToolCallbacks())
                        .map(tcp -> {
                            McpServerFeatures.SyncToolSpecification syncToolSpecification = McpToolUtils.toSyncToolSpecification(tcp);
                            built.addTool(new McpServerFeatures.SyncToolSpecification(
                                    syncToolSpecification.tool(),
                                    syncToolSpecification.call(),
                                    wrapToolCallWithInjection(t, syncToolSpecification)));
                            return tcp;
                        }))
                .toList();

        numTools += providers.size();

        log.info("Registered {} tool callback providers!", numTools);

        return built;
    }

    private static @NonNull BiFunction<McpSyncServerExchange, McpSchema.CallToolRequest, McpSchema.CallToolResult> wrapToolCallWithInjection(ToolCallbackProvider t, McpServerFeatures.SyncToolSpecification syncToolSpecification) {
        return (s, tcr) -> {
            if (t instanceof SpecialMethodToolCallbackProvider smtc) {
                smtc.toolCallbackMethods()
                        .forEach((key, value) -> {
                    if (tcr.name().equals(key))
                        Arrays.stream(value.getParameters())
                                .forEach(p -> {
                                    if (p.getType().equals(McpRequestContext.McpToolContext.class)) {
                                        tcr.arguments().put(p.getName(), new McpRequestContext.McpToolContext(McpRequestContext.getHeaders()));
                                    }
                                    if (p.isAnnotationPresent(SetFromHeader.class)) {
                                        var header = p.getAnnotation(SetFromHeader.class).value();
                                        tcr.arguments().put(p.getName(), McpRequestContext.getHeader(header));
                                    }
                                });
                });
            }

            return syncToolSpecification.callHandler().apply(s, tcr);
        };
    }

    @Bean
    public WebMvcStreamableServerTransportProvider httpProvider(
            ObjectMapper om, McpServerProperties serverProperties) {
        return WebMvcStreamableServerTransportProvider.builder()
                .jsonMapper(new JacksonMcpJsonMapper(om))
                .mcpEndpoint("/mcp")
                .contextExtractor(request -> McpTransportContext.create(Map.of(
                        "headers", request.headers().asHttpHeaders(),
                        "attributes", request.attributes()
                )))
                .build();
    }

    @Bean
    public List<McpServerFeatures.SyncToolSpecification> syncTools(ObjectProvider<List<ToolCallback>> toolCalls,
                                                                   List<ToolCallback> toolCallbacksList,
                                                                   McpServerProperties serverProperties) {

        List<ToolCallback> tools = new ArrayList<>(toolCalls.stream().flatMap(List::stream).toList());

        if (!CollectionUtils.isEmpty(toolCallbacksList)) {
            tools.addAll(toolCallbacksList);
        }

        return this.toSyncToolSpecifications(tools, serverProperties);
    }

    private List<McpServerFeatures.SyncToolSpecification> toSyncToolSpecifications(List<ToolCallback> tools,
                                                                                   McpServerProperties serverProperties) {

        // De-duplicate tools by their name, keeping the first occurrence of each tool
        // name
        return tools.stream() // Key: tool name
                .collect(Collectors.toMap(tool -> tool.getToolDefinition().name(), tool -> tool, // Value:
                        // the
                        // tool
                        // itself
                        (existing, replacement) -> existing)) // On duplicate key, keep the
                // existing tool
                .values()
                .stream()
                .map(tool -> {
                    String toolName = tool.getToolDefinition().name();
                    MimeType mimeType = (serverProperties.getToolResponseMimeType().containsKey(toolName))
                            ? MimeType.valueOf(serverProperties.getToolResponseMimeType().get(toolName)) : null;
                    return McpToolUtils.toSyncToolSpecification(tool, mimeType);
                })
                .toList();
    }

    private IdeMcpServer.SyncSpecification<?> buildSyncSpecification(
            McpServerTransportProviderBase transportProvider) {
        if (transportProvider instanceof McpStreamableServerTransportProvider) {
            return IdeMcpServer.sync((McpStreamableServerTransportProvider) transportProvider);
        }
        return IdeMcpServer.sync((McpServerTransportProvider) transportProvider);
    }

}
