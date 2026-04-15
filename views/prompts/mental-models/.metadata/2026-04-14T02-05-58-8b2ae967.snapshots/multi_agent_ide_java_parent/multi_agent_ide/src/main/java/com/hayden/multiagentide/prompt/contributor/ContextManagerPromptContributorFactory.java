package com.hayden.multiagentide.prompt.contributor;

import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.prompt.PromptContext;
import com.hayden.multiagentide.prompt.PromptContributor;
import com.hayden.multiagentide.prompt.PromptContributorDescriptor;
import com.hayden.multiagentide.prompt.PromptContributorFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * When the current request is a {@link AgentModels.ContextManagerRequest}, this factory
 * inspects the blackboard history to determine which agent was last active and instructs
 * the LLM to route back to that agent via the correct field on
 * {@link AgentModels.ContextManagerResultRouting}.
 *
 * <p>If no previous agent can be identified, it instructs routing back to the orchestrator
 * with a clearly defined goal derived from the current context.</p>
 */
@Slf4j
@Component
public class ContextManagerPromptContributorFactory implements PromptContributorFactory {

    @Override
    public Set<PromptContributorDescriptor> descriptors() {
        return Set.of(
                PromptContributorDescriptor.of(
                        "context-manager-return-route",
                        ContextManagerReturnRoutePromptContributor.class,
                        "FACTORY",
                        10,
                        "context-manager-return-route"),
                PromptContributorDescriptor.of(
                        "context-manager-fallback-route",
                        ContextManagerFallbackRoutePromptContributor.class,
                        "FACTORY",
                        10,
                        "context-manager-fallback-route"));
    }

    @Override
    public List<PromptContributor> create(PromptContext context) {
        AgentModels.AgentRequest agentRequest = context.currentRequest();
        if (context == null || agentRequest == null || context.blackboardHistory() == null) {
            return List.of();
        }

        if (!(agentRequest instanceof AgentModels.ContextManagerRequest req)) {
            return List.of();
        }

        AgentModels.AgentRequest lastAgent = ContextManagerReturnRoutes
                        .findLastNonContextManagerRequest(context.blackboardHistory());

        if (lastAgent != null) {
            ContextManagerReturnRoutes.ReturnRouteMapping mapping =
                    ContextManagerReturnRoutes.findMapping(lastAgent.getClass());
            if (mapping != null) {
                log.error("Didn't find mapping for return route in context manager return route prompt: {}.", lastAgent.getClass());
                return List.of(new ContextManagerReturnRoutePromptContributor(mapping, lastAgent));
            }
        }

        // No identifiable previous agent - instruct to route to orchestrator
        return List.of(new ContextManagerFallbackRoutePromptContributor());
    }

    public record ContextManagerReturnRoutePromptContributor(
            ContextManagerReturnRoutes.ReturnRouteMapping mapping,
            AgentModels.AgentRequest lastAgent
    ) implements PromptContributor {

        private static final String TEMPLATE = """
                ## Context Manager Return Route
                
                The last active agent before the Context Manager was invoked was \
                **{{display_name}}** (`{{request_type}}`).
                
                {{reason}}
                {{type}}
                
                ### Previous Agent Context
                {{agent_context}}
                
                ### Required Routing
                You MUST set `{{field_name}}` on your `ContextManagerResultRouting` response \
                to route back to the **{{display_name}}**. Populate it with the relevant context \
                gathered from the context manager tools, incorporating what the agent needs to \
                continue its work.
                
                Do not route to a different agent unless you have a strong reason. The workflow \
                expects to return to the agent that requested context.
                Context manager should be used for targeted cross-agent context recovery, not general rerouting.
                
                ### Return Route Instructions
                
                You have been routed to from this agent because you possess a unique set of tools to provide context \
                across all agent sessions. This is provided through your blackboard history tools. The blackboard history \
                is the shared context where all agent messages are saved, across the entire multi-agent process. You are then \
                provided a mechanism to search through this blackboard history with your blackboard history tools, and that means \
                you have a unique ability to provide context across agents.  \
                
                Please use your tools to search the history of all agent sessions, identify additional context information relative to the \
                goal and the agent's responsibilities, and then provide that information in the return value. \
                When you do this, it will route back to that agent with that information you provided, so make sure \
                to provide as much relevant information from the blackboard history as possible. \
                
                In particular, you can use that memory tool as a starting point for searching through agent messages and \
                structured responses. You can use your memory tool to reflect on those agent's experiences, and then perform \
                a search based on the result, or you can simply perform searches over the BlackboardHistory over particular agents. \
                Then, based on your results about the goal, where in the process we are, and what information was provided and missing \
                in the context, you provide all that information to the agent in their structured response, along with any information \
                that already existed. \
                """;

        @Override
        public String name() {
            return "context-manager-return-route";
        }

        @Override
        public boolean include(PromptContext promptContext) {
            return true;
        }

        @Override
        public String contribute(PromptContext context) {
            String agentContext = lastAgent.prettyPrint();
            if (agentContext == null || agentContext.isBlank()) {
                agentContext = lastAgent.prettyPrintInterruptContinuation();
            }
            if (agentContext == null || agentContext.isBlank()) {
                agentContext = "(No detailed context available)";
            }

            if (!(context.currentRequest() instanceof AgentModels.ContextManagerRequest r)) {
                return "";
            }

            return TEMPLATE
                    .replace("{{display_name}}", mapping.displayName())
                    .replace("{{request_type}}", mapping.requestType().getSimpleName())
                    .replace("{{field_name}}", mapping.fieldName())
                    .replace("{{agent_context}}", agentContext.trim())
                    .replace("{{reason}}", "Reason routing to context manager: " + r.reason())
                    .replace("{{type}}", "Context manager request type: " + r.type().name());
        }

        @Override
        public String template() {
            return TEMPLATE;
        }

        @Override
        public int priority() {
            return 10;
        }
    }

    public record ContextManagerFallbackRoutePromptContributor() implements PromptContributor {

        private static final String TEMPLATE = """
                ## Context Manager Return Route
                
                No previous agent could be identified from the blackboard history. \
                You should route back to the **Orchestrator** to re-establish workflow direction.
                
                ### Required Routing
                Set `orchestratorRequest` on your `ContextManagerResultRouting` response with:
                - A clearly defined `goal` that summarizes the current workflow state and what \
                  needs to happen next, based on the context you have gathered
                - Include any relevant context from your tools in the goal description
                
                This ensures the workflow resumes with clear direction rather than getting stuck.
                Context manager should be used for targeted cross-agent context recovery, not general rerouting.
                """;

        @Override
        public String name() {
            return "context-manager-fallback-route";
        }

        @Override
        public boolean include(PromptContext promptContext) {
            return true;
        }

        @Override
        public String contribute(PromptContext context) {
            return TEMPLATE;
        }

        @Override
        public String template() {
            return TEMPLATE;
        }

        @Override
        public int priority() {
            return 10;
        }
    }
}
