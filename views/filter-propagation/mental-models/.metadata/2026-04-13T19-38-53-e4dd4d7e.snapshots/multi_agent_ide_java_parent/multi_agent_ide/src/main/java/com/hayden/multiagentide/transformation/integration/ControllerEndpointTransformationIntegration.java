package com.hayden.multiagentide.transformation.integration;

import com.hayden.multiagentide.filter.service.FilterLayerCatalog;
import com.hayden.multiagentide.transformation.service.TransformerExecutionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ControllerEndpointTransformationIntegration {

    private final TransformerExecutionService transformerExecutionService;

    public TransformerExecutionService.TransformationExecutionResult maybeTransform(String controllerId,
                                                                                    String endpointId,
                                                                                    Object payload) {
        return transformerExecutionService.transform(FilterLayerCatalog.CONTROLLER, controllerId, endpointId, payload);
    }
}
