package com.hayden.multiagentide.filter.model.layer;

import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import lombok.Builder;

import java.util.List;
import java.util.Map;

/**
 * Layer entity representing a node in the layer hierarchy.
 */
@Builder(toBuilder = true)
public record LayerEntity(
        String layerId,
        FilterEnums.LayerType layerType,
        String layerKey,
        String parentLayerId,
        List<String> childLayerIds,
        boolean isInheritable,
        boolean isPropagatedToParent,
        int depth,
        Map<String, String> metadata
) {
}
