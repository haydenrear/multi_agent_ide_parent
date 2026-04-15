package com.hayden.acp_cdc_ai.acp.config;

import lombok.Builder;

import java.util.LinkedHashMap;
import java.util.Map;

@Builder
public record AcpResolvedCall(
        String sessionArtifactKey,
        AcpProvider providerName,
        String effectiveModel,
        AcpProviderDefinition providerDefinition,
        Map<String, Object> options
) {

    public AcpResolvedCall {
        options = options == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(options));
    }

    public String getSessionArtifactKey() {
        return sessionArtifactKey;
    }

    public String getEffectiveModel() {
        return effectiveModel;
    }

    public AcpProviderDefinition getProviderDefinition() {
        return providerDefinition;
    }

    public Map<String, Object> getOptions() {
        return options;
    }
}
