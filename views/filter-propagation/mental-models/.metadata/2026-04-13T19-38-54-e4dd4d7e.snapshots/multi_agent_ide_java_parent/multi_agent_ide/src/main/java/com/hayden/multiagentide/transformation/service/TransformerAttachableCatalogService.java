package com.hayden.multiagentide.transformation.service;

import com.hayden.multiagentide.filter.service.FilterLayerCatalog;
import com.hayden.multiagentide.transformation.controller.dto.ReadTransformerAttachableTargetsResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.condition.PatternsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class TransformerAttachableCatalogService {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    public ReadTransformerAttachableTargetsResponse readAttachableTargets() {
        List<ReadTransformerAttachableTargetsResponse.EndpointTarget> endpoints = handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(entry -> entry.getValue().getBeanType().getPackageName().startsWith("com.hayden.multiagentide.controller"))
                .map(entry -> toTarget(entry.getKey(), entry.getValue()))
                .filter(java.util.Objects::nonNull)
                .toList();
        return ReadTransformerAttachableTargetsResponse.builder().endpoints(endpoints).build();
    }

    private ReadTransformerAttachableTargetsResponse.EndpointTarget toTarget(RequestMappingInfo info, HandlerMethod handlerMethod) {
        Set<String> paths = new LinkedHashSet<>();
        if (info.getPathPatternsCondition() != null) {
            info.getPathPatternsCondition().getPatternValues().forEach(paths::add);
        }
        PatternsRequestCondition patternsCondition = info.getPatternsCondition();
        if (patternsCondition != null) {
            paths.addAll(patternsCondition.getPatterns());
        }
        Set<RequestMethod> methods = info.getMethodsCondition().getMethods();
        String httpMethod = methods.isEmpty() ? "ANY" : methods.iterator().next().name();
        String path = paths.isEmpty() ? "" : paths.iterator().next();
        return ReadTransformerAttachableTargetsResponse.EndpointTarget.builder()
                .layerId(FilterLayerCatalog.CONTROLLER)
                .controllerId(handlerMethod.getBeanType().getSimpleName())
                .controllerClass(handlerMethod.getBeanType().getName())
                .endpointId(handlerMethod.getMethod().getName())
                .httpMethod(httpMethod)
                .path(path)
                .build();
    }
}
