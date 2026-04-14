package com.hayden.multiagentide.filter.controller.dto;

import lombok.Builder;

@Builder(toBuilder = true)
public record ReadLayerChildrenRequest(
        String layerId,
        boolean recursive
) {
}
