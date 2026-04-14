package com.hayden.multiagentide.filter.controller.dto;

import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
public record ReadAttachableTargetsResponse(
        List<GraphEventTarget> graphEvents,
        List<PromptContributorTarget> promptContributors,
        List<String> promptContributorNames,
        List<String> errors
) {

    @Builder(toBuilder = true)
    public record GraphEventTarget(
            String eventType,
            String eventClass
    ) {
    }

    @Builder(toBuilder = true)
    public record PromptContributorTarget(
            String layerId,
            String agentName,
            String actionName,
            String methodName,
            String contributorName,
            String contributorClass,
            String source,
            int priority,
            String templateStaticId
    ) {
    }
}
