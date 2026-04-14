package com.hayden.utilitymodule.telemetry;

import com.hayden.utilitymodule.telemetry.log.AttributeProvider;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface TelemetryAttributesProvider {


    Map<String, String> attributes();

    default AttributeProvider toAttributeProvider() {
        return new AttributeProvider() {
            @Override
            public @NotNull List<Attributes> getAttributes() {
                var ab = Attributes.builder();
                attributes().forEach((key, value) -> ab.put(AttributeKey.stringKey(key), String.valueOf(value)));
                // TODO: load from file
                ab.put("node-id", UUID.randomUUID().toString());
                return List.of(ab.build());
            }
        };
    }

}
