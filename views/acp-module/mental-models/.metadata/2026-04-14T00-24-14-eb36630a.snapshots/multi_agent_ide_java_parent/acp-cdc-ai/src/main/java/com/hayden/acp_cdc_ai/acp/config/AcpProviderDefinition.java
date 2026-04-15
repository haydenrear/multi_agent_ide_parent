package com.hayden.acp_cdc_ai.acp.config;

import java.util.LinkedHashMap;
import java.util.Map;

public record AcpProviderDefinition(
        String name,
        String transport,
        String command,
        String args,
        String workingDirectory,
        String endpoint,
        String apiKey,
        String authMethod,
        Map<String, String> env,
        String defaultModel
) {

    public AcpProviderDefinition {
        env = env == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(env));
    }

    public Map<String, String> envCopy() {
        return new LinkedHashMap<>(env);
    }

    public String getName() {
        return name;
    }

    public String getTransport() {
        return transport;
    }

    public String getCommand() {
        return command;
    }

    public String getArgs() {
        return args;
    }

    public String getWorkingDirectory() {
        return workingDirectory;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getApiKey() {
        return apiKey;
    }

    public String getAuthMethod() {
        return authMethod;
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public String getDefaultModel() {
        return defaultModel;
    }
}
