package com.hayden.multiagentide.filter.prompt;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.multiagentide.filter.integration.PathFilterIntegration;
import com.hayden.multiagentide.filter.model.FilterSource;
import com.hayden.multiagentide.filter.model.layer.DefaultPathFilterContext;
import com.hayden.multiagentide.filter.model.layer.PromptContributorContext;
import com.hayden.multiagentide.filter.service.FilterDescriptor;
import com.hayden.multiagentide.filter.service.FilterResult;
import com.hayden.multiagentide.prompt.FilteredPromptContributor;
import com.hayden.multiagentide.prompt.PromptContributor;
import com.hayden.multiagentide.prompt.PromptContributorAdapter;
import com.hayden.multiagentide.prompt.PromptContext;
import io.micrometer.common.util.StringUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

/**
 * Wraps a PromptContributorAdapter to apply data-layer policy filters
 * (both object serializer and path filters) to the contributed text.
 */
@Slf4j
public class FilteredPromptContributorAdapter extends PromptContributorAdapter {

    private final String layerId;
    private final PathFilterIntegration pathFilterIntegration;

    @Getter
    private FilterResult<String> filterResult;

    public FilteredPromptContributorAdapter(
            PromptContributor contributor,
            PromptContext promptContext,
            String layerId,
            PathFilterIntegration pathFilterIntegration
    ) {
        super(contributor, promptContext);
        this.layerId = layerId;
        this.pathFilterIntegration = pathFilterIntegration;

        if (StringUtils.isBlank(layerId)) {
            this.filterResult = new FilterResult<>(getContributor().contribute(promptContext), new FilterDescriptor.NoOpFilterDescriptor());
        } else {
            var c = getContributor();
            var thisResult = filterContribution(c, c.contribute(promptContext));

            if (c instanceof FilteredPromptContributor f) {
                f.setContributed(thisResult.t());
                this.filterResult = new FilterResult<>(thisResult.t(), f.filterDescriptor().and(thisResult.descriptor()));
            } else {
                this.filterResult = new FilterResult<>(thisResult.t(), thisResult.descriptor());
            }
        }
    }

    @NotNull
    @Override
    public synchronized String contribution(@NotNull OperationContext context) {
        return filterResult.t();
    }

    private FilterResult<String> filterContribution(PromptContributor filteredContributor, String raw) {
        FilterResult<String> filterResult = pathFilterIntegration.applyTextPathFilters(
                layerId,
                FilterSource.promptContributor(filteredContributor),
                raw,
                new DefaultPathFilterContext(layerId, new PromptContributorContext(layerId, promptContext()))
        );

        return filterResult;
    }

    @Override
    public synchronized PromptContributor getContributor() {
        return super.getContributor();
    }

}
