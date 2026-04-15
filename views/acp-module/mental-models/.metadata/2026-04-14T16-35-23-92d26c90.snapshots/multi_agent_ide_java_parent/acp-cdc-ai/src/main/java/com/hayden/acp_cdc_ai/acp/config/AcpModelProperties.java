package com.hayden.acp_cdc_ai.acp.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@ConfigurationProperties(prefix = "multi-agent-embabel.acp")
public record AcpModelProperties(
        AcpProvider defaultProvider,
        Map<AcpProvider, AcpProviderDefinition> providers
) {

    public AcpModelProperties {
        providers = providers == null ? Map.of() : copyProviders(providers);
    }

    public Optional<AcpProviderDefinition> findProvider(AcpProvider provider) {
        if (provider == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(providers.get(provider));
    }

    public Optional<AcpProviderDefinition> findProvider(String providerName) {
        return findProvider(parseProvider(providerName));
    }

    public AcpProvider resolveProviderEnum(String requestedProvider) {
        AcpProvider provider = parseProvider(requestedProvider);
        if (provider != null) {
            if (!providers.containsKey(provider)) {
                throw new IllegalStateException("Unknown ACP provider: " + requestedProvider);
            }
            return provider;
        }
        if (defaultProvider == null) {
            throw new IllegalStateException("No ACP provider requested and no default ACP provider configured");
        }
        if (!providers.containsKey(defaultProvider)) {
            throw new IllegalStateException("Configured default ACP provider does not exist: " + defaultProvider.wireValue());
        }
        return defaultProvider;
    }

    public AcpProvider resolveProviderName(String requestedProvider) {
        return resolveProviderEnum(requestedProvider);
    }

    public AcpProviderDefinition resolveProvider(String requestedProvider) {
        return providers.get(resolveProviderEnum(requestedProvider));
    }

    public Map<AcpProvider, AcpProviderDefinition> getProviders() {
        return providers;
    }

    private static AcpProvider parseProvider(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return AcpProvider.fromWireValue(value);
    }

    private static Map<AcpProvider, AcpProviderDefinition> copyProviders(Map<AcpProvider, AcpProviderDefinition> providers) {
        Map<AcpProvider, AcpProviderDefinition> copied = new LinkedHashMap<>();
        providers.forEach((key, provider) -> {
            if (provider != null) {
                String name = key.wireValue();
                copied.put(key, provider.name() == null || provider.name().isBlank()
                        ? new AcpProviderDefinition(
                        name,
                        provider.transport(),
                        provider.command(),
                        provider.args(),
                        provider.workingDirectory(),
                        provider.endpoint(),
                        provider.apiKey(),
                        provider.authMethod(),
                        provider.env(),
                        provider.defaultModel()
                )
                        : provider);
            }
        });
        return Map.copyOf(copied);
    }
}
