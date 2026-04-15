package com.hayden.multiagentide.prompt.contributor;

import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.agent.AgentType;
import com.hayden.multiagentide.service.AgentCommunicationService;
import com.hayden.multiagentide.topology.CommunicationTopologyProvider;
import com.hayden.multiagentide.prompt.PromptContext;
import com.hayden.multiagentide.prompt.PromptContributor;
import com.hayden.multiagentide.prompt.PromptContributorDescriptor;
import com.hayden.multiagentide.prompt.PromptContributorFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class AgentTopologyPromptContributorFactory implements PromptContributorFactory {

    private final AgentCommunicationService agentCommunicationService;
    private final CommunicationTopologyProvider topologyProvider;

    @Override
    public Set<PromptContributorDescriptor> descriptors() {
        return Set.of(PromptContributorDescriptor.of(
                AgentTopologyPromptContributor.class.getSimpleName(),
                AgentTopologyPromptContributor.class,
                "FACTORY",
                9_000,
                AgentTopologyPromptContributor.class.getSimpleName()));
    }

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || context.currentRequest() == null) {
            return List.of();
        }

        if (isExcludedRequestType(context.currentRequest())) {
            return List.of();
        }

        String sessionKey = context.currentContextId() != null ? context.currentContextId().value() : null;
        AgentType agentType = context.agentType();

        var agents = agentCommunicationService.listAvailableAgents(sessionKey, agentType);
        if (agents.isEmpty()) {
            return List.of();
        }

        int maxDepth = topologyProvider.maxCallChainDepth();
        int messageBudget = topologyProvider.messageBudget();

        List<PromptContributor> result = new ArrayList<>();
        result.add(new AgentTopologyPromptContributor(agents, maxDepth, messageBudget));
        return result;
    }

    public record AgentTopologyPromptContributor(
            List<AgentCommunicationService.AgentAvailabilityEntry> agents,
            int maxCallChainDepth,
            int messageBudget
    ) implements PromptContributor {

        @Override
        public String name() {
            return this.getClass().getSimpleName();
        }

        @Override
        public boolean include(PromptContext promptContext) {
            return !agents.isEmpty();
        }

        @Override
        public String contribute(PromptContext context) {
            StringBuilder sb = new StringBuilder();
            sb.append("<available_agents>\n");
            for (var agent : agents) {
                sb.append("<agent>\n");
                sb.append("<key>").append(agent.agentKey()).append("</key>\n");
                sb.append("<type>").append(agent.agentType()).append("</type>\n");
                sb.append("<busy>").append(agent.busy()).append("</busy>\n");
                sb.append("<callable>").append(agent.callableByCurrentAgent()).append("</callable>\n");
                sb.append("</agent>\n");
            }
            sb.append("</available_agents>");

            String topologyRules = "";
            if (maxCallChainDepth > 0) {
                topologyRules += "- Maximum call chain depth: %d\n".formatted(maxCallChainDepth);
            }
            if (messageBudget > 0) {
                topologyRules += "- Message budget per conversation: %d\n".formatted(messageBudget);
            }

            return template().formatted(sb.toString(),
                    topologyRules.isEmpty() ? "No additional constraints." : topologyRules.strip());
        }

        @Override
        public String template() {
            return """
                    You have access to other agents via the call_agent and list_agents tools. \
                    Here are the agents currently available for communication:

                    %s

                    Topology rules:
                    %s

                    Only agents marked as callable=true can be contacted. Busy agents may have delayed responses.
                    """;
        }

        @Override
        public int priority() {
            return 9_000;
        }
    }

    private static boolean isExcludedRequestType(AgentModels.AgentRequest request) {
        return request instanceof AgentModels.AgentToAgentRequest
                || request instanceof AgentModels.AgentToControllerRequest
                || request instanceof AgentModels.ControllerToAgentRequest
                || request instanceof AgentModels.AiFilterRequest
                || request instanceof AgentModels.AiPropagatorRequest
                || request instanceof AgentModels.AiTransformerRequest;
    }
}
