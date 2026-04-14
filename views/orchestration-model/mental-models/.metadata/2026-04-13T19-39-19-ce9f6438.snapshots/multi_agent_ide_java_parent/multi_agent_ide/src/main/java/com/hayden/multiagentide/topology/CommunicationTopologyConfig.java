package com.hayden.multiagentide.topology;

import com.hayden.multiagentide.agent.AgentType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;
import java.util.Set;

@ConfigurationProperties(prefix = "multi-agent-ide.topology")
public record CommunicationTopologyConfig(
        int maxCallChainDepth,
        int messageBudget,
        @NonNull Map<AgentType, Set<AgentType>> allowedCommunications
) {
    public CommunicationTopologyConfig {
        if (maxCallChainDepth <= 0) maxCallChainDepth = 5;
        if (messageBudget == 0) messageBudget = -1;
        if (allowedCommunications == null) allowedCommunications = Map.of();
    }

    /**
     * Check if a source agent type is allowed to communicate with a target agent type.
     */
    public boolean isCommunicationAllowed(@NonNull AgentType source, @NonNull AgentType target) {
        @Nullable Set<AgentType> allowed = allowedCommunications.get(source);
        return allowed != null && allowed.contains(target);
    }
}
