package com.hayden.utilitymodule.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "keystore")
@Data
@Component
public class KeyConfigProperties {

    private Path keyPath;
    private String keyName;


}
