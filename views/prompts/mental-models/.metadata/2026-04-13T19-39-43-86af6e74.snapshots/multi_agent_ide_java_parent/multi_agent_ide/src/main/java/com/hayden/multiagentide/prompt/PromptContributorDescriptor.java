package com.hayden.multiagentide.prompt;

import lombok.Builder;

/**
 * Static metadata describing a prompt contributor that policies may bind to.
 */
@Builder(toBuilder = true)
public record PromptContributorDescriptor(
        String name,
        String contributorClass,
        String source,
        int priority,
        String templateStaticId
) {

    public static PromptContributorDescriptor of(
            String name,
            Class<?> contributorClass,
            String source,
            int priority,
            String templateStaticId
    ) {
        return PromptContributorDescriptor.builder()
                .name(name)
                .contributorClass(contributorClass.getName())
                .source(source)
                .priority(priority)
                .templateStaticId(templateStaticId)
                .build();
    }

    public static PromptContributorDescriptor fromContributor(PromptContributor contributor, String source) {
        return PromptContributorDescriptor.builder()
                .name(contributor.name())
                .contributorClass(contributor.getClass().getName())
                .source(source)
                .priority(contributor.priority())
                .templateStaticId(contributor.templateStaticId())
                .build();
    }
}
