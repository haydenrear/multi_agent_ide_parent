package com.hayden.multiagentide.prompt.contributor;

import com.google.common.collect.Lists;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.prompt.PromptContext;
import com.hayden.multiagentide.prompt.PromptContributor;
import com.hayden.multiagentide.prompt.PromptContributorDescriptor;
import com.hayden.multiagentide.prompt.PromptContributorFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Factory that creates {@link ContextManagerRoutingPromptContributor} instances
 * for agents whose routing types include a ContextManagerRoutingRequest field.
 *
 * <p>The factory matches the current request type from the PromptContext to determine
 * if the agent can route to the Context Manager. The mapping is based on analyzing
 * which Routing record types contain a ContextManagerRoutingRequest field:</p>
*/
// @Component
public class RouteToContextManagerPromptContributorFactory implements PromptContributorFactory {

    /**
     * Maps request types to their corresponding AgentType for agents that can route to Context Manager.
     */
    private static final Set<Class<? extends AgentModels.AgentRequest>> REQUEST_TO_AGENT_TYPE = Set.of(
            AgentModels.DiscoveryOrchestratorRequest.class,
            AgentModels.DiscoveryAgentRequests.class,
            AgentModels.DiscoveryCollectorRequest.class,
            AgentModels.PlanningOrchestratorRequest.class,
            AgentModels.PlanningAgentRequests.class,
            AgentModels.PlanningCollectorRequest.class,
            AgentModels.TicketOrchestratorRequest.class,
            AgentModels.TicketAgentRequests.class,
            AgentModels.TicketCollectorRequest.class,
            AgentModels.OrchestratorCollectorRequest.class
    );

    @Override
    public Set<PromptContributorDescriptor> descriptors() {
        return Set.of(PromptContributorDescriptor.of(
                "context-manager-routing",
                ContextManagerRoutingPromptContributor.class,
                "FACTORY",
                50,
                "context-manager-routing"));
    }

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || context.currentRequest() == null) {
            return List.of();
        }

        if (context.blackboardHistory().copyOfEntries().size() < 3) {
            return new ArrayList<>();
        }

        if (REQUEST_TO_AGENT_TYPE.contains(context.currentRequest().getClass()))
            return Lists.newArrayList(new ContextManagerRoutingPromptContributor());

        return new ArrayList<>();
    }

    /**
     * Prompt contributor that provides guidance for agents that can route to the Context Manager.
     * This contributor is dynamically created by {@link RouteToContextManagerPromptContributorFactory}
     * based on whether the current agent's routing type includes a ContextManagerRoutingRequest field.
     */
    public record ContextManagerRoutingPromptContributor() implements PromptContributor {

        private static final String TEMPLATE = """
                ## Context Manager Routing Option

                You have the ability to route to the **Context Manager** agent. The Context Manager has
                access to tools that can query and retrieve the shared state across all agents in the
                workflow. This includes the blackboard history, artifact store, and execution traces
                from previous agent invocations.

                ### When to Route to Context Manager

                Route to Context Manager by populating `contextManagerRequest` in your routing response when
                you need more context from a previous agent's execution:

                - **Missing execution details** - You need to understand what a previous agent discovered,
                  decided, or produced, but that information was not passed forward in the request
                - **Incomplete handoff** - The upstream agent's results are truncated or summarized, and
                  you need the full details to proceed
                - **Cross-phase context** - You need information from an earlier workflow phase (e.g.,
                  discovery findings while in planning, or planning decisions while executing tickets)
                - **Artifact retrieval** - You need to access artifacts, curations, or intermediate results
                  that were produced but not included in your current request

                ### Context Manager Capabilities

                The Context Manager can:
                1. Query the blackboard history to retrieve previous agent requests and results
                2. Access the artifact store to find curations, code maps, and other persisted data
                3. Trace the execution path to understand how the workflow reached the current state
                4. Reconstruct context from multiple sources and consolidate it for your use

                ### Important

                - Only route to Context Manager when you genuinely need specific missing context from another agent chat/history
                - Be specific about what information you need so the Context Manager can retrieve it efficiently
                - The Context Manager will reconstruct the needed context and route back to continue your work
                - Do not use Context Manager as a general rerouting mechanism.
                """;

        @Override
        public String name() {
            return "context-manager-routing";
        }

        @Override
        public boolean include(PromptContext promptContext) {
            return true;
        }

        @Override
        public String contribute(PromptContext context) {
    //        TODO: the context manager prompt should only start to be shown after some loops or danger of loops.
            return template();
        }

        @Override
        public String template() {
            return TEMPLATE;
        }

        @Override
        public int priority() {
            return 50;
        }
    }
}
