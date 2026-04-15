package com.hayden.multiagentide.prompt;

import java.util.List;
import java.util.Set;

public interface PromptContributorFactory extends RetryAware {

    List<PromptContributor> create(PromptContext context);

    Set<PromptContributorDescriptor> descriptors();
}
