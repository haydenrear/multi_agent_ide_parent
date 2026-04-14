package com.hayden.acp_cdc_ai.sandbox;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class SandboxTranslationRegistry {

    private final Map<String, SandboxTranslationStrategy> strategies;

    public SandboxTranslationRegistry(List<SandboxTranslationStrategy> strategies) {
        this.strategies = strategies.stream()
                .collect(Collectors.toMap(
                        s -> s.providerKey().toLowerCase(Locale.ROOT),
                        Function.identity(),
                        (a, b) -> a
                ));
    }

    public Optional<SandboxTranslationStrategy> find(String providerKey) {
        if (providerKey == null || providerKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(strategies.get(providerKey.toLowerCase(Locale.ROOT)));
    }
}
