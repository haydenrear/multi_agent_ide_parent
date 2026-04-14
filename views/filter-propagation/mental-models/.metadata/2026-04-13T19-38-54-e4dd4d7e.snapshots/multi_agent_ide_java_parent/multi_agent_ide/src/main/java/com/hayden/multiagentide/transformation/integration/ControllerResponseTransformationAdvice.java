package com.hayden.multiagentide.transformation.integration;

import com.hayden.multiagentide.transformation.service.TransformerExecutionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * Intercepts controller responses for endpoints that have registered transformers.
 * Delegates to {@link ControllerEndpointTransformationIntegration} for the actual
 * transformation logic.
 *
 * Only targets controllers in the multiagentide.controller package so that
 * internal propagation/transformation API responses are not recursively transformed.
 */
@ControllerAdvice(basePackages = "com.hayden.multiagentide.controller")
@RequiredArgsConstructor
@Slf4j
public class ControllerResponseTransformationAdvice implements ResponseBodyAdvice<Object> {

    private final ControllerEndpointTransformationIntegration transformationIntegration;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body,
                                  MethodParameter returnType,
                                  MediaType selectedContentType,
                                  Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request,
                                  ServerHttpResponse response) {
        if (body == null) {
            return null;
        }
        try {
            String controllerId = returnType.getContainingClass().getSimpleName();
            String endpointId = returnType.getMethod() != null ? returnType.getMethod().getName() : "unknown";
            var result = transformationIntegration.maybeTransform(controllerId, endpointId, body);
            if (result.transformed()) {
                response.getHeaders().setContentType(MediaType.TEXT_PLAIN);
                return result.body();
            }
        } catch (Exception e) {
            log.warn("Controller response transformation failed, returning original body", e);
        }
        return body;
    }
}
