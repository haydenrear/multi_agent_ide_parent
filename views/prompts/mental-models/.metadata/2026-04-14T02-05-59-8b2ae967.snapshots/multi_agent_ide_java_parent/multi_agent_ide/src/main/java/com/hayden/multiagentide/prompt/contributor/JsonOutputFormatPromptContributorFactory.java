package com.hayden.multiagentide.prompt.contributor;

import com.google.common.collect.Lists;
import com.hayden.multiagentide.prompt.PromptContext;
import com.hayden.multiagentide.prompt.PromptContributor;
import com.hayden.multiagentide.prompt.PromptContributorDescriptor;
import com.hayden.multiagentide.prompt.PromptContributorFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Set;

@Component
public class JsonOutputFormatPromptContributorFactory implements PromptContributorFactory {

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || context.currentRequest() == null) {
            return List.of();
        }

        return Lists.newArrayList(JsonOutputFormatPromptContributor.INSTANCE);
    }

    @Override
    public Set<PromptContributorDescriptor> descriptors() {
        return Set.of(PromptContributorDescriptor.of(
                JsonOutputFormatPromptContributor.INSTANCE.name(),
                JsonOutputFormatPromptContributor.class,
                "FACTORY",
                JsonOutputFormatPromptContributor.INSTANCE.priority(),
                JsonOutputFormatPromptContributor.INSTANCE.templateStaticId()));
    }

    public record JsonOutputFormatPromptContributor() implements PromptContributor {

        public static final JsonOutputFormatPromptContributor INSTANCE = new JsonOutputFormatPromptContributor();

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
            return template();
        }

        @Override
        public String template() {
            return """
                    ## Critical JSON Output Requirements
                    
                    Your final response MUST be valid JSON matching the provided schema. Follow these rules strictly:
                    
                    1. **Escape all special characters in string values.** Newlines must be `\\n`, tabs must be `\\t`, \
                    backslashes must be `\\\\`, and double quotes must be `\\"`. Never include raw/literal newlines, \
                    tabs, or other control characters (ASCII 0-31) inside JSON string values.
                    
                    2. **Return only the JSON object.** Do not include any text, markdown, or explanation before or \
                    after the JSON. Do not wrap it in markdown code fences. The response must start with `{` and end with `}`.
                    
                    3. **Always populate at least one routing field.** If the schema has routing options \
                    (e.g. agentResult, interruptRequest, planningOrchestratorRequest), you must set at least one \
                    of them to a non-null value. Never return a routing object with all null fields.
                    
                    4. **Use compact single-line strings.** For any field containing multi-line content (like analysis \
                    output or findings), combine everything into a single string with `\\n` escape sequences for line breaks.
                    """;
        }

        @Override
        public int priority() {
            return 10_001;
        }
    }

}
