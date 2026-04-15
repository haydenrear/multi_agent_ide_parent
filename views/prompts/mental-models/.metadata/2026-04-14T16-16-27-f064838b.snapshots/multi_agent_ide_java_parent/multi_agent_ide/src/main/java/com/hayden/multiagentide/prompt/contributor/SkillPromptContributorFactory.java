package com.hayden.multiagentide.prompt.contributor;

import com.hayden.multiagentide.agent.BlackboardHistory;
import com.hayden.multiagentide.skills.SkillDecorator;
import com.hayden.multiagentide.skills.SkillFinder;
import com.hayden.multiagentide.prompt.PromptContext;
import com.hayden.multiagentide.prompt.PromptContributor;
import com.hayden.multiagentide.prompt.PromptContributorDescriptor;
import com.hayden.multiagentide.prompt.PromptContributorFactory;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class SkillPromptContributorFactory implements PromptContributorFactory {

    private final SkillFinder skillFinder;

    @Override
    public Set<PromptContributorDescriptor> descriptors() {
        return Set.of(PromptContributorDescriptor.of(
                SkillPromptContributor.class.getSimpleName(),
                SkillPromptContributor.class,
                "FACTORY",
                10_000,
                SkillPromptContributor.class.getSimpleName()));
    }

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || context.currentRequest() == null) {
            return List.of();
        }

        if (BlackboardHistory.isNonWorkflowRequest(context.currentRequest())) {
            return List.of();
        }

        return skillFinder.findSkillDecorator(context.currentRequest().getClass().getSimpleName())
                .map(SkillPromptContributor::new)
                .stream()
                .collect(Collectors.toCollection(ArrayList::new));

    }

    public record SkillPromptContributor(SkillDecorator decorator)
            implements PromptContributor {

        @Override
        public String name() {
            return this.getClass().getSimpleName();
        }

        @Override
        public boolean include(PromptContext promptContext) {
            return CollectionUtils.isNotEmpty(decorator.getSkill().getSkills());
        }

        @Override
        public String contribute(PromptContext context) {
            var skills = decorator.getSkill().getSkills();

            StringBuilder skillsXml = new StringBuilder();
            skillsXml.append("<available_skills>\n");
            
            for (var skill : skills) {
                skillsXml.append("<skill>\n");
                skillsXml.append("<name>").append(escapeXml(skill.getName())).append("</name>\n");
                skillsXml.append("<description>").append(escapeXml(skill.getDescription())).append("</description>\n");
                skillsXml.append("<location>").append(escapeXml(skill.getBasePath().toString())).append("</location>\n");
                skillsXml.append("</skill>\n");
            }
            
            skillsXml.append("</available_skills>");
            
            return template().formatted(skillsXml.toString());
        }
        
        private String escapeXml(String input) {
            if (input == null) {
                return "";
            }
            return input
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                    .replace("\"", "&quot;")
                    .replace("'", "&apos;");
        }

        @Override
        public String template() {
            return """
                    Here are the skills available to you:
                    
                    %s
                    
                    You can use the skill tool to provide more information about them, or feel free to read
                    them directly if they are local skills.
                    """;
        }

        @Override
        public int priority() {
            return 10_000;
        }
    }

}
