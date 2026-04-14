package com.hayden.multiagentide.propagation.service;

import com.hayden.multiagentide.filter.service.FilterLayerCatalog;
import com.hayden.multiagentide.propagation.controller.dto.ReadPropagatorAttachableTargetsResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PropagatorAttachableCatalogService {

    public ReadPropagatorAttachableTargetsResponse readAttachableTargets() {
        List<ReadPropagatorAttachableTargetsResponse.ActionTarget> actions = FilterLayerCatalog.userAttachableActionDefinitions().stream()
                .map(action -> ReadPropagatorAttachableTargetsResponse.ActionTarget.builder()
                        .layerId(action.layerId())
                        .agentName(action.agentName())
                        .actionName(action.actionName())
                        .methodName(action.methodName())
                        .stages(List.of("ACTION_REQUEST", "ACTION_RESPONSE"))
                        .build())
                .toList();
        return ReadPropagatorAttachableTargetsResponse.builder().actions(actions).build();
    }
}
