package com.hayden.multiagentide.config;

import com.hayden.acp_cdc_ai.acp.config.AcpProvider;
import com.hayden.multiagentide.agent.AgentType;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.HashMap;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "llm.model-selection")
public class LlmModelSelectionProperties {

    private ModelEntry defaultModel;

    private Map<String, ModelEntry> byAgentType = new HashMap<>();

    private Map<String, ModelEntry> byTemplate = new HashMap<>();

    @Data
    public static class ModelEntry {
        private String model;
        private AcpProvider provider;
    }

    public record ResolvedModel(String model, AcpProvider provider) {
        public String providerWireValue() {
            return provider != null ? provider.wireValue() : null;
        }
    }

    /**
     * Resolve model and provider for a given agent type and template.
     * Priority: byTemplate > byAgentType > defaultModel > null.
     */
    public ResolvedModel resolve(AgentType agentType, String templateName) {
        if (templateName != null && byTemplate.containsKey(templateName)) {
            return toResolved(byTemplate.get(templateName));
        }
        if (agentType != null && byAgentType.containsKey(agentType.wireValue())) {
            return toResolved(byAgentType.get(agentType.wireValue()));
        }
        return toResolved(defaultModel);
    }

    private ResolvedModel toResolved(ModelEntry entry) {
        if (entry == null) {
            return new ResolvedModel(null, null);
        }
        return new ResolvedModel(entry.getModel(), entry.getProvider());
    }
}
