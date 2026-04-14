package com.hayden.multiagentide.filter.model.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.multiagentide.filter.model.layer.FilterContext;
import com.hayden.multiagentide.filter.service.FilterDescriptor;
import com.hayden.multiagentide.filter.service.FilterResult;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executor that invokes a Java function by reflection.
 */
@Slf4j
@Builder(toBuilder = true)
public record JavaFunctionExecutor<I, O, CTX extends FilterContext>(
        String functionRef,
        String className,
        String methodName,
        int timeoutMs,
        String configVersion
) implements ExecutableTool<I, O, CTX> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        SimpleModule module = new SimpleModule();
        OBJECT_MAPPER.registerModule(module);
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
    }

    @Override
    public FilterEnums.ExecutorType executorType() {
        return FilterEnums.ExecutorType.JAVA_FUNCTION;
    }

    @Override
    public FilterResult<O> apply(I i, CTX ctx) {
        ObjectMapper objectMapper = ExecutableTool.contextObjectMapper(ctx, OBJECT_MAPPER);
        ResolvedFunction fn = null;
        try {
            fn = resolveFunction();
            Method method = resolveMethod(fn, i, ctx);
            Object[] args = buildArgs(method, i, ctx);
            Object result = method.invoke(null, args);
            O output = ExecutableTool.coerceExecutorOutput(result, i, ctx, objectMapper);
            return new FilterResult<>(output, buildJavaFunctionDescriptor(fn));
        } catch (Exception e) {
            log.error("Error when attempting to filter {}.", i, e);
            return new FilterResult<>(
                    ExecutableTool.fallbackOutput(i, ctx, objectMapper),
                    new FilterDescriptor.NoOpFilterDescriptor());
        }
    }

    private FilterDescriptor buildJavaFunctionDescriptor(ResolvedFunction fn) {
        Map<String, String> details = new LinkedHashMap<>();
        putIfPresent(details, "functionRef", functionRef);
        details.put("className", fn.className());
        details.put("methodName", fn.methodName());
        details.put("timeoutMs", String.valueOf(timeoutMs));
        putIfPresent(details, "configVersion", configVersion);

        FilterDescriptor.Entry entry = new FilterDescriptor.Entry(
                "EXECUTOR",
                null,
                null,
                null,
                null,
                null,
                "TRANSFORMED",
                FilterEnums.ExecutorType.JAVA_FUNCTION.name(),
                details,
                List.of()
        );
        return new FilterDescriptor.SimpleFilterDescriptor(List.of(), entry);
    }

    private ResolvedFunction resolveFunction() {
        String resolvedClassName = className;
        String resolvedMethodName = methodName;
        String resolvedFunctionRef = functionRef;

        if (isBlank(resolvedClassName) || isBlank(resolvedMethodName)) {
            if (isBlank(resolvedFunctionRef)) {
                throw new IllegalStateException("JAVA_FUNCTION executor requires className/methodName or functionRef");
            }

            if (resolvedFunctionRef.contains("#")) {
                String[] parts = resolvedFunctionRef.split("#", 2);
                resolvedClassName = parts[0];
                resolvedMethodName = parts[1];
            } else if (resolvedFunctionRef.contains("::")) {
                String[] parts = resolvedFunctionRef.split("::", 2);
                resolvedClassName = parts[0];
                resolvedMethodName = parts[1];
            } else {
                int idx = resolvedFunctionRef.lastIndexOf('.');
                if (idx <= 0 || idx >= resolvedFunctionRef.length() - 1) {
                    throw new IllegalStateException("Could not parse functionRef; expected Class#method or Class.method");
                }
                resolvedClassName = resolvedFunctionRef.substring(0, idx);
                resolvedMethodName = resolvedFunctionRef.substring(idx + 1);
            }
        }

        if (isBlank(resolvedClassName) || isBlank(resolvedMethodName)) {
            throw new IllegalStateException("JAVA_FUNCTION executor resolved empty className/methodName");
        }
        return new ResolvedFunction(resolvedClassName, resolvedMethodName);
    }

    private Method resolveMethod(ResolvedFunction fn, I input, CTX ctx) throws Exception {
        Class<?> clazz = Class.forName(fn.className());
        Method candidate = null;

        for (Method method : clazz.getMethods()) {
            if (!method.getName().equals(fn.methodName())) {
                continue;
            }
            if (!Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (canInvoke(method, input, ctx)) {
                if (candidate == null || method.getParameterCount() > candidate.getParameterCount()) {
                    candidate = method;
                }
            }
        }

        if (candidate == null) {
            throw new IllegalStateException("No compatible static method found for " + fn.className() + "#" + fn.methodName());
        }
        return candidate;
    }

    private boolean canInvoke(Method method, I input, CTX ctx) {
        Class<?>[] params = method.getParameterTypes();
        if (params.length == 0 || params.length > 2) {
            return false;
        }
        if (!isCompatible(params[0], input)) {
            return false;
        }
        return params.length == 1 || isCompatible(params[1], ctx);
    }

    private Object[] buildArgs(Method method, I input, CTX ctx) {
        Class<?>[] params = method.getParameterTypes();
        Object[] args = new Object[params.length];
        args[0] = input;
        if (params.length == 2) {
            args[1] = ctx;
        }
        return args;
    }

    private boolean isCompatible(Class<?> parameterType, Object value) {
        if (value == null) {
            return !parameterType.isPrimitive();
        }
        return parameterType.isAssignableFrom(value.getClass()) || parameterType == Object.class;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void putIfPresent(Map<String, String> target, String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            return;
        }
        target.put(key, value);
    }

    private record ResolvedFunction(String className, String methodName) {
    }
}
