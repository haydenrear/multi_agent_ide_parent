package com.hayden.multiagentide.propagation.controller.dto;

import lombok.Builder;

import java.util.List;

@Builder(toBuilder = true)
public record ReadPropagatorAttachableTargetsResponse(List<ActionTarget> actions) {
    @Builder(toBuilder = true)
    public record ActionTarget(String layerId, String agentName, String actionName, String methodName, List<String> stages) {
    }
}
