package com.hayden.multiagentide.prompt;

import com.hayden.multiagentide.filter.service.FilterDescriptor;

import java.util.Map;
import java.util.Objects;

/**
 * Wrapper contributor that preserves both the original and filtered contributor values.
 * The filtered contributor drives runtime contribution behavior.
 */
public final class FilteredPromptContributor implements PromptContributor {

    private final PromptContributor originalContributor;

    private FilterDescriptor filter;

    private volatile String contributed;

    public FilteredPromptContributor(PromptContributor originalContributor, FilterDescriptor filter) {
        this.originalContributor = Objects.requireNonNull(originalContributor, "originalContributor");
        this.filter = filter;
    }

    public FilterDescriptor filterDescriptor() {
        return filter;
    }

    public void andFilterDescriptor(FilterDescriptor current) {
        filter = filter.and(current);
    }

    public void setContributed(String contributed) {
        this.contributed = contributed;
    }

    public PromptContributor originalContributor() {
        return originalContributor;
    }

    @Override
    public String name() {
        return originalContributor().name();
    }

    @Override
    public boolean include(PromptContext promptContext) {
        return originalContributor.include(promptContext);
    }

    @Override
    public String contribute(PromptContext context) {
        if (contributed != null)
            return contributed;

        contributed = originalContributor.contribute(context);
        return contributed;
    }

    @Override
    public String template() {
        return originalContributor.template();
    }

    @Override
    public Map<String, Object> args() {
        return originalContributor.args();
    }

    @Override
    public int priority() {
        return originalContributor.priority();
    }
}
