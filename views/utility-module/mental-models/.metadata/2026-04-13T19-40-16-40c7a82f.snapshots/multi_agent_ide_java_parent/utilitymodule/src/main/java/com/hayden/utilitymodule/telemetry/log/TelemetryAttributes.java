package com.hayden.utilitymodule.telemetry.log;

import com.hayden.utilitymodule.telemetry.TelemetryAttributesProvider;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Data
@NoArgsConstructor
@AllArgsConstructor
@ConfigurationProperties(prefix = "telemetry-resources")
@Profile("telemetry-logging")
public class TelemetryAttributes implements TelemetryAttributesProvider {

    @Value("#{${telemetry-resources.attributes:null}}")
    Map<String, String> attributes = new HashMap<>();

    @Override
    public Map<String, String> attributes() {
        return attributes;
    }

}
