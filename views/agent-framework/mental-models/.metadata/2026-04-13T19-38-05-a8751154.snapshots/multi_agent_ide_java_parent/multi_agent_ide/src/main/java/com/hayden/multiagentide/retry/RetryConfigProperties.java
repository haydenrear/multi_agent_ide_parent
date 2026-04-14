package com.hayden.multiagentide.retry;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "multi-agent-ide.retry")
@Data
public class RetryConfigProperties {

    int maxCompactionPolls = 30;

    int compactionPollIntervalMs = 1_000;

}
