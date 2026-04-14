package com.hayden.multiagentide.topology;

import com.hayden.multiagentide.agent.AgentType;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Provides access to the communication topology configuration with support for runtime refresh.
 * <p>
 * Since Spring Cloud's {@code @RefreshScope} is not on the classpath, this service wraps the
 * immutable {@link CommunicationTopologyConfig} record and supports re-binding from the
 * {@link Environment} via {@link #refresh()}. The refreshed config takes effect on the next
 * call to {@link #get()}.
 */
@Service
@Slf4j
public class CommunicationTopologyProvider {

    private final Environment environment;
    private volatile CommunicationTopologyConfig current;

    public CommunicationTopologyProvider(CommunicationTopologyConfig initial, Environment environment) {
        this.environment = environment;
        this.current = initial;
    }

    @PostConstruct
    void validateOnStartup() {
        if (current.allowedCommunications().isEmpty()) {
            log.warn("Communication topology configuration is empty — no inter-agent communication will be allowed. "
                    + "Configure 'multi-agent-ide.topology.allowed-communications' to enable agent communication.");
        } else {
            log.info("Communication topology loaded: {} source agent types configured, maxCallChainDepth={}, messageBudget={}",
                    current.allowedCommunications().size(),
                    current.maxCallChainDepth(),
                    current.messageBudget());
        }
    }

    /**
     * Returns the current topology configuration.
     */
    public @NonNull CommunicationTopologyConfig get() {
        return current;
    }

    /**
     * Convenience delegate: check if communication is allowed between source and target agent types.
     */
    public boolean isCommunicationAllowed(@NonNull AgentType source, @NonNull AgentType target) {
        return current.isCommunicationAllowed(source, target);
    }

    /**
     * Convenience delegate: get max call chain depth.
     */
    public int maxCallChainDepth() {
        return current.maxCallChainDepth();
    }

    /**
     * Convenience delegate: get message budget.
     */
    public int messageBudget() {
        return current.messageBudget();
    }

    /**
     * Re-binds the topology configuration from the current {@link Environment} properties.
     * This allows runtime reconfiguration without a restart (SC-008).
     *
     * @return true if the configuration was successfully refreshed
     */
    public boolean refresh() {
        try {
            CommunicationTopologyConfig refreshed = Binder.get(environment)
                    .bind("multi-agent-ide.topology", CommunicationTopologyConfig.class)
                    .orElse(new CommunicationTopologyConfig(0, 0, Map.of()));
            this.current = refreshed;

            if (refreshed.allowedCommunications().isEmpty()) {
                log.warn("Refreshed topology configuration is empty — no inter-agent communication allowed.");
            } else {
                log.info("Topology configuration refreshed: {} source agent types configured",
                        refreshed.allowedCommunications().size());
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to refresh topology configuration: {}", e.getMessage(), e);
            return false;
        }
    }
}
