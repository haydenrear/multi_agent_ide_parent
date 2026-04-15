package com.hayden.multiagentide.prompt.contributor;

import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.agent.AgentType;
import com.hayden.multiagentide.prompt.PromptContext;
import com.hayden.multiagentide.prompt.PromptContributor;
import com.hayden.multiagentide.prompt.PromptContributorDescriptor;
import com.hayden.multiagentide.prompt.PromptContributorFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Injects role-specific justification guidance when an agent is in a controller conversation.
 * Matches on AgentToControllerRequest and ControllerToAgentRequest types,
 * selecting the appropriate template based on the agent's role (FR-018, FR-019).
 */
@Component
@RequiredArgsConstructor
public class JustificationPromptContributorFactory implements PromptContributorFactory {

    @Override
    public Set<PromptContributorDescriptor> descriptors() {
        return Set.of(PromptContributorDescriptor.of(
                JustificationPromptContributor.class.getSimpleName(),
                JustificationPromptContributor.class,
                "FACTORY",
                8_500,
                JustificationPromptContributor.class.getSimpleName()));
    }

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || context.currentRequest() == null) {
            return List.of();
        }

        AgentModels.AgentRequest request = context.currentRequest();
        if (!(request instanceof AgentModels.AgentToControllerRequest)
                && !(request instanceof AgentModels.ControllerToAgentRequest)) {
            return List.of();
        }

        // Only include justification guidance when we have an active checklist action
        // that is NOT a terminal approval. When checklistAction is null/missing or is
        // JUSTIFICATION_PASSED/APPROVED, the agent should return its result, not justify.
        if (request instanceof AgentModels.ControllerToAgentRequest cta) {
            if (cta.checklistAction() == null
                    || cta.checklistAction().actionType() == null
                    || "JUSTIFICATION_PASSED".equals(cta.checklistAction().actionType())
                    || "APPROVED".equals(cta.checklistAction().actionType())) {
                return List.of();
            }
        }

        AgentType agentType = resolveSourceAgentType(request);
        String template = selectTemplate(agentType);

        List<PromptContributor> result = new ArrayList<>();
        result.add(new JustificationPromptContributor(template, agentType));
        return result;
    }

    private AgentType resolveSourceAgentType(AgentModels.AgentRequest request) {
        if (request instanceof AgentModels.AgentToControllerRequest atc) {
            return atc.sourceAgentType();
        }
        if (request instanceof AgentModels.ControllerToAgentRequest cta) {
            return cta.targetAgentType();
        }
        return null;
    }

    private String selectTemplate(AgentType agentType) {
        if (agentType == null) return TEMPLATE_DEFAULT;
        return switch (agentType) {
            case DISCOVERY_AGENT, DISCOVERY_AGENT_DISPATCH,
                 DISCOVERY_ORCHESTRATOR, DISCOVERY_COLLECTOR -> TEMPLATE_DISCOVERY;
            case PLANNING_AGENT, PLANNING_AGENT_DISPATCH,
                 PLANNING_ORCHESTRATOR, PLANNING_COLLECTOR -> TEMPLATE_PLANNING;
            case TICKET_AGENT, TICKET_AGENT_DISPATCH,
                 TICKET_ORCHESTRATOR, TICKET_COLLECTOR -> TEMPLATE_TICKET;
            default -> TEMPLATE_DEFAULT;
        };
    }

    private static final String TEMPLATE_DISCOVERY = """
            ## Justification Guidance: Discovery Phase

            You are in a justification conversation with the controller. As a discovery agent, structure your justification around:

            1. **Interpretation**: How you interpreted the codebase findings and their significance
            2. **Findings Mapping**: How discovered elements map to the stated goals and requirements
            3. **Confidence**: Your confidence level in the findings and any ambiguities that need resolution
            4. **Next Steps**: What you propose based on these findings

            Be specific — reference concrete files, patterns, or code structures you found.
            """;

    private static final String TEMPLATE_PLANNING = """
            ## Justification Guidance: Planning Phase

            You are in a justification conversation with the controller. As a planning agent, structure your justification around:

            1. **Ticket-to-Requirement Traceability**: How each planned ticket traces back to a specific requirement
            2. **Completeness**: Whether all requirements are covered by the planned work
            3. **Dependencies**: How tickets relate to each other and any ordering constraints
            4. **Risk Assessment**: Potential issues and mitigation strategies

            Reference specific requirements and show the mapping to planned work items.
            """;

    private static final String TEMPLATE_TICKET = """
            ## Justification Guidance: Ticket Execution Phase

            You are in a justification conversation with the controller. As a ticket agent, structure your justification around:

            1. **Change Justification**: Why specific code changes are necessary and correct
            2. **Verification Summary**: How you verified the changes work (tests, manual inspection, etc.)
            3. **Impact Analysis**: What other code or systems are affected by these changes
            4. **Completeness**: Whether all aspects of the ticket are addressed

            Reference specific code changes and their rationale.
            """;

    private static final String TEMPLATE_DEFAULT = """
            ## Justification Guidance

            You are in a justification conversation with the controller. Structure your response clearly:

            1. **What**: Describe what you did or propose
            2. **Why**: Explain the reasoning behind your approach
            3. **Evidence**: Provide supporting evidence or references
            4. **Next Steps**: What you need from the controller to proceed
            """;

    public record JustificationPromptContributor(
            String justificationTemplate,
            AgentType sourceAgentType
    ) implements PromptContributor {

        @Override
        public String name() {
            return this.getClass().getSimpleName();
        }

        @Override
        public boolean include(PromptContext promptContext) {
            return true;
        }

        @Override
        public String contribute(PromptContext context) {
            return justificationTemplate;
        }

        @Override
        public String template() {
            return justificationTemplate;
        }

        @Override
        public int priority() {
            return 8_500;
        }
    }
}
