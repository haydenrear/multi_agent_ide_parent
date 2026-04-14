package com.hayden.multiagentide.config;

import com.hayden.multiagentide.artifacts.ArtifactEventListener;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for artifact system initialization.
 * Subscribes the ArtifactEventListener to the EventBus.
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class ArtifactConfig {
    
    private final EventBus eventBus;
    private final ArtifactEventListener artifactEventListener;
    
    @PostConstruct
    public void init() {
        eventBus.subscribe(artifactEventListener);
        log.info("Subscribed ArtifactEventListener to EventBus");
    }
}
