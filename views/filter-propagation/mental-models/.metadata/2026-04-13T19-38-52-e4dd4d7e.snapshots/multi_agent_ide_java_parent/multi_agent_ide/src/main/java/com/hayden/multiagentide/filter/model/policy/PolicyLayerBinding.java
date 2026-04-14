package com.hayden.multiagentide.filter.model.policy;

import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import lombok.Builder;

import java.time.Instant;

/**
 * Binding of a policy to a specific layer with matcher fields for narrowing
 * which PromptContributor or GraphEvent the policy applies to within that layer.
 */
@Builder(toBuilder = true)
public record PolicyLayerBinding(
        String layerId,
        boolean enabled,
        boolean includeDescendants,
        boolean isInheritable,
        boolean isPropagatedToParent,
        FilterEnums.MatcherKey matcherKey,
        FilterEnums.MatcherType matcherType,
        String matcherText,
        FilterEnums.MatchOn matchOn,
        String updatedBy,
        Instant updatedAt
) {
}
