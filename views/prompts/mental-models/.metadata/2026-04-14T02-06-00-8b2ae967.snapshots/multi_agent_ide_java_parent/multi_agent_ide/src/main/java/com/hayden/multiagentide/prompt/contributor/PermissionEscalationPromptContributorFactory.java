package com.hayden.multiagentide.prompt.contributor;

import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.agent.BlackboardHistory;
import com.hayden.multiagentide.prompt.PromptContext;
import com.hayden.multiagentide.prompt.PromptContributor;
import com.hayden.multiagentide.prompt.PromptContributorDescriptor;
import com.hayden.multiagentide.prompt.PromptContributorFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

/**
 * Adds guardrails for permission escalation when required work is blocked by sandbox/tool denial.
 */
@Component
public class PermissionEscalationPromptContributorFactory implements PromptContributorFactory {

    @Override
    public Set<PromptContributorDescriptor> descriptors() {
        return Set.of(PromptContributorDescriptor.of(
                "permission-escalation-guardrail-v1",
                PermissionEscalationPromptContributor.class,
                "FACTORY",
                48,
                "permission-escalation-guardrail-v1"));
    }

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || context.currentRequest() == null) {
            return List.of();
        }

        if (BlackboardHistory.isNonWorkflowRequest(context.currentRequest())) {
            return List.of();
        }

        if (context.currentRequest() instanceof AgentModels.InterruptRequest) {
            return List.of();
        }

        return List.of(new PermissionEscalationPromptContributor());
    }

    public record PermissionEscalationPromptContributor() implements PromptContributor {

        private static final String TEMPLATE = """
                ## Permission Escalation Guardrail

                If at first there is a tool-call error related to permissions or authorization
                (for example: file permission denied, cannot view, cannot modify, read-only, or
                other "not allowed" errors), raise a permission request.

                Do not fail the workflow only because of that first permission/authorization error.
                """;

        @Override
        public String name() {
            return "permission-escalation-guardrail-v1";
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
            return 48;
        }
    }
}
