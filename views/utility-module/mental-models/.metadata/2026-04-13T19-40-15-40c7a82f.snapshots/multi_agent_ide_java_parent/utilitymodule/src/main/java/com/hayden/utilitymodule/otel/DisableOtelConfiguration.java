package com.hayden.utilitymodule.otel;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
@ConditionalOnProperty(value = "management.tracing.enabled", havingValue = "false", matchIfMissing = true)
public class DisableOtelConfiguration implements EnvironmentPostProcessor {
    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication application) {
        if(env.matchesProfiles("telemetry-logging")) {
            log.info("OTEL SDK is not being disabled.");
            return;
        }

        boolean tracingEnabled = env.getProperty("management.tracing.enabled", Boolean.class, false);

        if (!tracingEnabled) {
            log.info("OTEL SDK is being disabled.");
            env.getPropertySources().addFirst(
                    new MapPropertySource("otel-disable",
                            Map.of("otel.sdk.disabled", "true",
                                    "management.tracing.enabled", "false",
                                    "management.logging.export.enabled", "false",
                                    "management.otlp.metrics.export.enabled", "false"))
            );
        }
    }
}
