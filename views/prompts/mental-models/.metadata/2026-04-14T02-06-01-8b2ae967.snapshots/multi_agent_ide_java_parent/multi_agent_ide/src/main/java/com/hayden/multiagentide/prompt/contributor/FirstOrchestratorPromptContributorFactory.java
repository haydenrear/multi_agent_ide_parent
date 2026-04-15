package com.hayden.multiagentide.prompt.contributor;

import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.prompt.PromptContext;
import com.hayden.multiagentide.prompt.PromptContributor;
import com.hayden.multiagentide.prompt.PromptContributorDescriptor;
import com.hayden.multiagentide.prompt.PromptContributorFactory;
import com.hayden.multiagentide.prompt.SimplePromptContributor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component
public class FirstOrchestratorPromptContributorFactory implements PromptContributorFactory {

    @Override
    public Set<PromptContributorDescriptor> descriptors() {
        return Set.of(
                PromptContributorDescriptor.of(
                        "first-orchestrator-request",
                        FirstOrchestratorRequestPromptContributor.class,
                        "FACTORY",
                        0,
                        "first-orchestrator-request"),
                PromptContributorDescriptor.of(
                        "interrupt-history",
                        SimplePromptContributor.class,
                        "FACTORY",
                        30,
                        "interrupt-history"));
    }

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || context.blackboardHistory() == null || context.currentRequest() == null) {
            return List.of();
        }
        if (context.currentRequest() instanceof AgentModels.OrchestratorRequest r
                && context.blackboardHistory().fromHistory(h -> h.entries().isEmpty()
                || h.entries().getFirst().input() == r)) {
            return List.of(
//                  put the prompt contributor for the first orchestrator request
                    new FirstOrchestratorRequestPromptContributor(r)
            );
        }


        return new ArrayList<>();
    }

    public record FirstOrchestratorRequestPromptContributor(AgentModels.OrchestratorRequest orchestratorRequest) implements PromptContributor {

        private static final String TEMPLATE = """
                ## First Orchestrator Request

                This is the first orchestrator request in a new workflow (no prior history).
                Your job is to start discovery.

                **Required routing for this step**
                - Return an `OrchestratorRouting` with `orchestratorRequest` set to a `DiscoveryOrchestratorRequest`, as the first step will be to perform discovery to achieve our goal.
                - Do NOT return a new `OrchestratorRequest` or route back to the Orchestrator.
                - Do NOT route to `collectorRequest` or `contextManagerRequest` at this stage.

                **Discovery request guidance**
                - Set `goal` to a rich, detailed statement of the user's intent.
                - Include scope, constraints, desired outcomes, and explicit preferences.
                - If a phase was provided, incorporate it into the goal text.
                - Do not set `contextId`.

                Current orchestrator request summary:
                """;

        @Override
        public String name() {
            return "first-orchestrator-request";
        }

        @Override
        public boolean include(PromptContext promptContext) {
            return true;
        }

        @Override
        public String contribute(PromptContext context) {
            String summary = orchestratorRequest != null
                    ? orchestratorRequest.prettyPrintInterruptContinuation()
                    : "Goal: (none)";
            if (summary == null || summary.isBlank()) {
                summary = "Goal: (none)";
            }
            return TEMPLATE + summary.trim();
        }

        @Override
        public String template() {
            return TEMPLATE;
        }

        @Override
        public int priority() {
            return 0;
        }
    }
}
