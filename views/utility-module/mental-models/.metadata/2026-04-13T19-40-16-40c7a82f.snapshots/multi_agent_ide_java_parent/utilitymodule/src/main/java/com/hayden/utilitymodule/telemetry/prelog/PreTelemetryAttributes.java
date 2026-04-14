package com.hayden.utilitymodule.telemetry.prelog;

import com.hayden.utilitymodule.telemetry.log.AttributeProvider;
import com.hayden.utilitymodule.telemetry.TelemetryAttributesProvider;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Before the context is initialized, there needs to be traces with attributes also.
 */
@Component
@Data
@NoArgsConstructor
@AllArgsConstructor
@Profile("telemetry-logging")
public class PreTelemetryAttributes implements TelemetryAttributesProvider {

    @Value("#{${telemetry-resources.attributes:null}}")
    Map<String, String> attributes = new HashMap<>();

    @Override
    public Map<String, String> attributes() {
        return attributes;
    }
}
