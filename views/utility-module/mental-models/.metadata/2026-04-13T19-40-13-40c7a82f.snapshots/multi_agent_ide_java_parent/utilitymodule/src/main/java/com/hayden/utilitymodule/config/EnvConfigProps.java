package com.hayden.utilitymodule.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "env-props")
@Component
@Data
public class EnvConfigProps {

    Path homeDir;

    Path projectDir;

    Path errorLog = null;

}
