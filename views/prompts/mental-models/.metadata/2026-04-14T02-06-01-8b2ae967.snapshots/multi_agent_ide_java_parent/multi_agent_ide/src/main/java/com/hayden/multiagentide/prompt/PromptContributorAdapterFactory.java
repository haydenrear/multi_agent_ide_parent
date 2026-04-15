package com.hayden.multiagentide.prompt;

import com.embabel.agent.api.common.ContextualPromptElement;

/**
 * Factory for creating ContextualPromptElement adapters from PromptContributors.
 * The default implementation returns a plain PromptContributorAdapter.
 * The app module provides a @Primary override that applies data-layer policy filters.
 */
public interface PromptContributorAdapterFactory {
    ContextualPromptElement create(PromptContributor contributor, PromptContext context);
}
