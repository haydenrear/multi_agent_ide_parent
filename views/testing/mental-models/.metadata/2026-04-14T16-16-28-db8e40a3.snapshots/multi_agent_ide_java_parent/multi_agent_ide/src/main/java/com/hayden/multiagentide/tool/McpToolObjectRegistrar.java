package com.hayden.multiagentide.tool;

import com.agentclientprotocol.model.McpServer;
import com.embabel.agent.api.tool.ToolObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.config.McpProperties;
import com.hayden.acp_cdc_ai.mcp.RequiredProtocolProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
@RequiredArgsConstructor
public class McpToolObjectRegistrar {

    @Delegate
    private final EmbabelToolObjectRegistry embabelToolObjectRegistry;

    private final McpProperties mcpProperties;

    private final RequiredProtocolProperties requiredProtocolProperties;

    @Autowired(required = false)
    private ObjectMapper objectMapper = new ObjectMapper();



    private AtomicBoolean b = new AtomicBoolean(false);

    @PostConstruct
    public void initialize() {
        if (!b.getAndSet(true)) {
            for (Map.Entry<String, McpServer> entry : mcpProperties.getCollected().entrySet()) {
                String name = entry.getKey();
                McpServer value = entry.getValue();
                embabelToolObjectRegistry.register(name, new LazyToolObjectRegistration(new LazyToolObjectRegistration.McpServerDescriptor(value, requiredProtocolProperties.getRequired().get(value.getName())), entry.getKey(), objectMapper));
            }
        }
    }

    public Optional<List<ToolObject>> tool(String name) {
        initialize();
        return embabelToolObjectRegistry.tool(name);
    }
}
