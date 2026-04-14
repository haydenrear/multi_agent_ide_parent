package com.hayden.multiagentide.tool;

import com.agentclientprotocol.model.McpServer;
import com.embabel.agent.api.tool.ToolObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.utilitymodule.MapFunctions;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.DelegatingHttpClientStreamableHttpTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.jackson.JacksonMcpJsonMapper;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;


@Slf4j
class LazyToolObjectRegistration {

    final EmbabelToolObjectRegistry.ToolRegistration underlying;

    final String name;

    final CountDownLatch countDownLatch = new CountDownLatch(1);

    volatile List<McpSyncClient> clients;

    volatile List<ToolObject> setValues;

    private final ObjectMapper objectMapper;

    public record McpServerDescriptor(McpServer server, String requiredProtocol) {}

    LazyToolObjectRegistration(McpServerDescriptor value, String name, ObjectMapper objectMapper) {
        this.name = name;
        this.objectMapper = objectMapper;
        underlying = tor -> initializeToolObjects(value, tor);
    }

    private @NonNull Optional<List<ToolObject>> initializeToolObjects(McpServerDescriptor value, LazyToolObjectRegistration tor) {
        var t = toToolObjects(tor, name, value);
        return t;
    }

    synchronized Optional<List<ToolObject>> compute() {
        if (prev().isPresent())
            return prev();

        var g = computeToolObject(this);
        g.ifPresent(this::setToolObjects);
        return g;
    }

    private Consumer<List<McpSchema.Tool>> toolsChangeConsumer() {
        return t -> doToolChangeConsumer();
    }

    private void doToolChangeConsumer() {
        log.info("Received tool change notification - updating tools.");
        try {
            boolean waitedCountdownLatch = countDownLatch.await(5, TimeUnit.SECONDS);
            if (waitedCountdownLatch && clients != null) {
                log.info("Resetting tools from notification.");
                setValues();
            } else if (clients == null && waitedCountdownLatch) {
                log.error("Found tools change consumer execution but clients was null. " +
                          "A race thing before it was set but ultimately very weird - a supposed impossible state.");
            } else {
                log.error("Clients: {}, waitedCountdownLatch: {}.", clients, waitedCountdownLatch);
            }
        } catch (InterruptedException e) {
            log.error("Found interrupted exception while waiting for countdown latch.");
            Thread.currentThread().interrupt();
        }
    }

    private void setValues() {
        List<McpSyncClient> localClients = clients;
        var tc = SyncMcpToolCallbackProvider.syncToolCallbacks(localClients);
        setToolObjects(EmbabelToolObjectProvider.parseToolObjects(name, tc));
    }

    private Optional<List<ToolObject>> computeToolObject(LazyToolObjectRegistration toolObjectRegistration) {
        return underlying.computeToolObject(toolObjectRegistration);
    }

    private Optional<List<ToolObject>> prev() {
        return Optional.ofNullable(setValues);
    }

    private void setToolObjects(List<ToolObject> values) {
        this.setValues = List.copyOf(values);
    }

    private @NonNull Optional<List<ToolObject>> toToolObjects(LazyToolObjectRegistration tor, String name, McpServerDescriptor value) {
        var tc = resolveToolCallbacks(name, value, tor);
        return tc.map(toolCallbacks -> EmbabelToolObjectProvider.parseToolObjects(name, toolCallbacks));
    }

    private @NonNull Optional<List<ToolCallback>> resolveToolCallbacks(String name,
                                                                       McpServerDescriptor value,
                                                                       LazyToolObjectRegistration toolObjectRegistration) {

        try {
            switch (value.server) {
                case McpServer.Http http -> {
                    DelegatingHttpClientStreamableHttpTransport b = httpTransport(value, http);
                    var m = McpClient.sync(b)
                            .toolsChangeConsumer(toolObjectRegistration.toolsChangeConsumer())
                            .build();

                    List<McpSyncClient> m1 = List.of(m);
                    var tc = SyncMcpToolCallbackProvider.syncToolCallbacks(m1);
                    publishClients(m1);
                    return Optional.of(tc);
                }
                case McpServer.Stdio stdio -> {
                    var m = McpClient.sync(stdioTransport(stdio, value))
                            .toolsChangeConsumer(toolObjectRegistration.toolsChangeConsumer())
                            .build();

                    List<McpSyncClient> m1 = List.of(m);
                    var tc = SyncMcpToolCallbackProvider.syncToolCallbacks(m1);
                    publishClients(m1);
                    return Optional.of(tc);
                }
                case McpServer.Sse sse -> {
                    log.error("Dont support SSE!");
                }
                default -> {

                }
            }

        } catch (Exception e) {
            log.error("Error attempting to build {}.", name);

            if (countDownLatch.getCount() != 1L && clients == null) {
                log.error("Found inconsistent count in countdown latch.");
            }
        }

        return Optional.empty();
    }

    private @NonNull StdioClientTransport stdioTransport(McpServer.Stdio stdio, McpServerDescriptor value) {
        ServerParameters.Builder args = ServerParameters.builder(stdio.getCommand())
                .env(MapFunctions.CollectMap(stdio.getEnv().stream()
                        .map(e -> Map.entry(e.getName(), e.getValue()))))
                .args(stdio.getArgs());
        return new StdioClientTransport(args.build(), new JacksonMcpJsonMapper(objectMapper));
    }

    private DelegatingHttpClientStreamableHttpTransport httpTransport(McpServerDescriptor value, McpServer.Http http) {
        String url = http.getUrl();

        var uri = UriComponentsBuilder
                .fromUriString(url)
                .replacePath("")
                .build()
                .toUriString();
        var path = UriComponentsBuilder
                .fromUriString(url)
                .build()
                .getPath();
        DelegatingHttpClientStreamableHttpTransport.Builder httpBuilder = DelegatingHttpClientStreamableHttpTransport
                .builder(uri)
                .endpoint(path);

        Optional.ofNullable(value.requiredProtocol)
                .map(List::of)
                .ifPresent(httpBuilder::supportedProtocolVersions);

        httpBuilder.jsonMapper(new JacksonMcpJsonMapper(objectMapper));

        return httpBuilder
                .build();
    }

    private void publishClients(List<McpSyncClient> m1) {
        this.clients = m1;
        this.countDownLatch.countDown();
    }
}
