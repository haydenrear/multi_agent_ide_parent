package com.hayden.acp_cdc_ai.acp.config;

import com.agentclientprotocol.model.EnvVariable;
import com.agentclientprotocol.model.McpServer;
import com.hayden.utilitymodule.MapFunctions;
import com.hayden.utilitymodule.stream.StreamUtil;
import io.micrometer.common.util.StringUtils;
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Stream;

@ConfigurationProperties("acp.mcp")
@Component
@Slf4j
@Data
public class McpProperties {

    List<StdioValues> stdioValues = new ArrayList<>();

    List<McpServer.Http> http = new ArrayList<>();

    Map<String, McpServer> collected = new HashMap<>();

    boolean enabled = true;

    public record StdioValues(String name, String command, List<Map<String, String>> env, List<String> args) {}

    @Setter
    @Getter
    boolean enableSelf = true;

    public boolean didEnableSelf() {
        return enableSelf;
    }

    public Map<String, McpServer> getCollected() {
        if (!CollectionUtils.isEmpty(collected)) {
            return collected;
        }

        after();

        return collected;
    }

    public void after() {
        if (!enabled) {
            http = new ArrayList<>();
            stdioValues = new ArrayList<>();
            collected = new HashMap<>();
            return;
        }

        var stdio = new ArrayList<McpServer.Stdio>();

        stdioValues.forEach(s -> stdio.add(new McpServer.Stdio(s.name, s.command, s.args, s.env.stream()
                .flatMap(e -> e.entrySet().stream())
                .map(e -> new EnvVariable(e.getKey(), e.getValue(), null)).toList())));

        collected = MapFunctions.CollectMap(
                Stream.concat(
                            StreamUtil.toStream(stdio),
                            StreamUtil.toStream(http)
                        )
                        .map(m -> Map.entry(m.getName(), m)));
    }

    public Optional<McpServer> retrieve(ToolDefinition toolDefinition) {
        var m = StreamUtil.toStream(collected)
                .filter(e -> StringUtils.isNotBlank(e.getKey()))
                .filter(e -> toolDefinition.name().startsWith("%s.".formatted(e.getKey())))
                .map(Map.Entry::getValue)
                .toList();

        if (m.size() > 1) {
            throw new RuntimeException("Found multiple MCP servers that looked to have the same name when searching for %s!".formatted(toolDefinition.name()));
        }

        return m.stream().findAny();
    }

}
