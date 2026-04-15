package com.hayden.multiagentide.prompt;

import com.embabel.agent.api.common.OperationContext;
import com.embabel.common.ai.prompt.PromptContributionLocation;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Adapts our PromptContributor interface to Embabel's ContextualPromptElement.
 * 
 * This allows our existing PromptContributor implementations to work seamlessly
 * with Embabel's native prompt contribution system via withPromptContributor().
 */
public class PromptContributorAdapter implements com.embabel.agent.api.common.ContextualPromptElement {

    @Getter
    private final PromptContributor contributor;
    private final PromptContext promptContext;
    
    public PromptContributorAdapter(PromptContributor contributor, PromptContext agentType) {
        this.contributor = contributor;
        this.promptContext = agentType;
    }
    
    @NotNull
    @Override
    public String contribution(@NotNull OperationContext context) {
        return contributor.contribute(promptContext);
    }

    protected PromptContext promptContext() {
        return promptContext;
    }
    
    @Nullable
    @Override
    public String getRole() {
        return contributor.name();
    }
    
    @NotNull
    @Override
    public PromptContributionLocation getPromptContributionLocation() {
        // Our contributors are generally guidance/tools, so they go at the beginning
        return PromptContributionLocation.BEGINNING;
    }
}
