package com.hayden.multiagentide.filter.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;

@ConfigurationProperties(prefix = "filter")
@Component
@Data
public class FilterConfigProperties {

    Path uv;

    Path bins;

}
