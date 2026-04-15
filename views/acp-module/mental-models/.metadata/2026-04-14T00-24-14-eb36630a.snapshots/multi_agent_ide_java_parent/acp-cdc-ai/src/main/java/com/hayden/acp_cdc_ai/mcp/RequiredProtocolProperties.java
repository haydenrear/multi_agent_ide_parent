package com.hayden.acp_cdc_ai.mcp;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@ConfigurationProperties(prefix = "mcp.protocols")
@Component
@Data
public class RequiredProtocolProperties {

    Map<String, String> required = new HashMap<>();

    Optional<String> protocolFor(String name) {
        return Optional.ofNullable(required.get(name));
    }

}
