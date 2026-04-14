package com.hayden.multiagentide.filter.prompt;

import com.embabel.agent.api.common.ContextualPromptElement;
import com.hayden.multiagentide.filter.integration.PathFilterIntegration;
import com.hayden.multiagentide.filter.service.LayerIdResolver;
import com.hayden.multiagentide.prompt.PromptContributor;
import com.hayden.multiagentide.prompt.PromptContributorAdapter;
import com.hayden.multiagentide.prompt.PromptContributorAdapterFactory;
import com.hayden.multiagentide.prompt.PromptContext;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Primary adapter factory that wraps each PromptContributor with filter integration.
 * When a layerId can be resolved, returns a FilteredPromptContributorAdapter;
 * otherwise falls back to a plain PromptContributorAdapter.
 */
@Component
@Primary
@RequiredArgsConstructor
public class FilteredPromptContributorAdapterFactory implements PromptContributorAdapterFactory {

    private final LayerIdResolver layerIdResolver;
    private final PathFilterIntegration pathFilterIntegration;

    @Override
    public ContextualPromptElement create(PromptContributor contributor, PromptContext context) {
        String layerId = layerIdResolver.resolveForPromptContributor(context).orElse(null);
        if (layerId == null) {
            return new PromptContributorAdapter(contributor, context);
        }
        return new FilteredPromptContributorAdapter(
                contributor, context, layerId,
                pathFilterIntegration
        );
    }
}
