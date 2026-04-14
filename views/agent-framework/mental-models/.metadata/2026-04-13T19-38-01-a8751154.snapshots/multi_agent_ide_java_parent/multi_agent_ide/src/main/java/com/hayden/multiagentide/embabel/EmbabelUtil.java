package com.hayden.multiagentide.embabel;

import com.embabel.agent.api.common.OperationContext;
import com.embabel.agent.core.AgentPlatform;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.multiagentide.agent.AgentInterfaces;

import java.util.Optional;

public interface EmbabelUtil {


    /**
     * Extracts the workflow run ID from the operation context.
     */
    static String extractWorkflowRunId(OperationContext context) {
        try {
            return new ArtifactKey(context.getAgentProcess().getId())
                    .value();
        } catch (Exception e) {
            if (context == null || context.getProcessContext() == null) {
                return null;
            }
            var options = context.getProcessContext().getProcessOptions();
            if (options == null) {
                return null;
            }
            return options.getContextIdString();
        }
    }

    /**
     * Resolves an OperationContext by walking up the ArtifactKey hierarchy until an active
     * agent process is found. Returns null if no process is found at any level.
     */
    static OperationContext resolveOperationContext(AgentPlatform agentPlatform, ArtifactKey key, String agentName) {
        if (agentPlatform == null || key == null) {
            return null;
        }
        ArtifactKey searchThrough = key;
        while (searchThrough != null) {
            OperationContext ctx = Optional.ofNullable(agentPlatform.getAgentProcess(searchThrough.value()))
                    .map(process -> AgentInterfaces.buildOpContext(process, agentName))
                    .orElse(null);
            if (ctx != null) {
                return ctx;
            }
            searchThrough = searchThrough.parent().orElse(null);
        }
        return null;
    }

    /**
     * Resolves an OperationContext, returning the provided one if non-null, otherwise
     * falling back to key-based resolution.
     */
    static OperationContext resolveOperationContext(AgentPlatform agentPlatform, OperationContext existing, ArtifactKey key, String agentName) {
        if (existing != null) {
            return existing;
        }
        return resolveOperationContext(agentPlatform, key, agentName);
    }
}
