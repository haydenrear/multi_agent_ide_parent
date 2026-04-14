package com.hayden.utilitymodule.schema;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.hayden.utilitymodule.MapFunctions;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.DefaultToolCallResultConverter;
import org.springframework.ai.tool.execution.ToolCallResultConverter;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.ai.tool.method.MethodToolCallback;
import org.springframework.ai.tool.support.ToolDefinitions;
import org.springframework.ai.tool.support.ToolUtils;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.util.Assert;
import org.springframework.util.ClassUtils;
import org.springframework.util.ReflectionUtils;

public class SpecialMethodToolCallbackProvider implements ToolCallbackProvider, ApplicationContextAware {
    private static final Logger logger = LoggerFactory.getLogger(SpecialMethodToolCallbackProvider.class);


    private List<Object> toolObjects;
    private SpecialJsonSchemaGenerator specialJsonSchemaGenerator;
    private DelegatingSchemaReplacer schemaReplacer;
    private ApplicationContext applicationContext;


    public  SpecialMethodToolCallbackProvider(List<Object> toolObjects, SpecialJsonSchemaGenerator specialJsonSchemaGenerator, DelegatingSchemaReplacer schemaReplacer, ApplicationContext ctx) {
        Assert.notNull(toolObjects, "toolObjects cannot be null");
        Assert.noNullElements(toolObjects, "toolObjects cannot contain null elements");
        assertToolAnnotatedMethodsPresent(toolObjects);
        this.applicationContext = ctx;
        this.toolObjects = toolObjects;
        this.specialJsonSchemaGenerator = specialJsonSchemaGenerator;
        this.schemaReplacer = schemaReplacer;
        validateToolCallbacks(getToolCallbacks());
    }

    public Map<String, Method> toolCallbackMethods() {
        return MapFunctions.CollectMap(this.toolObjects.stream()
                .flatMap(obj -> {
                    var objP = AopProxyUtils.getSingletonTarget(obj);
                    return Arrays.stream((objP == null ? obj : objP).getClass().getDeclaredMethods())
                            .filter(m -> m.isAnnotationPresent(Tool.class))
                            .map(m -> Map.entry(m.getName(), m));
                }));
    }

    private void assertToolAnnotatedMethodsPresent(List<Object> toolObjects) {

        for (Object toolObject : toolObjects) {
            List<Method> toolMethods = Stream
                    .of(ReflectionUtils.getDeclaredMethods(
                            AopUtils.isAopProxy(toolObject) ? AopUtils.getTargetClass(toolObject) : toolObject.getClass()))
                    .filter(toolMethod -> toolMethod.isAnnotationPresent(Tool.class))
                    .filter(toolMethod -> !isFunctionalType(toolMethod))
                    .toList();

            if (toolMethods.isEmpty()) {
                throw new IllegalStateException("No @Tool annotated methods found in " + toolObject + "."
                                                + "Did you mean to pass a ToolCallback or ToolCallbackProvider? If so, you have to use .toolCallbacks() instead of .tool()");
            }
        }
    }

    public ToolDefinition getToolDefinition(Method m) {
        return ToolDefinitions.builder(m)
                               .name(m.getName())
                               .description(schemaReplacer.replace(m.getAnnotation(Tool.class).description()))
                               .inputSchema(specialJsonSchemaGenerator.generateForMethodInput(m))
                               .build();
    }

    @Override
    public ToolCallback[] getToolCallbacks() {
        var toolCallbacks = this.toolObjects.stream()
                .map(toolObject -> Stream
                        .of(ReflectionUtils.getDeclaredMethods(
                                AopUtils.isAopProxy(toolObject) ? AopUtils.getTargetClass(toolObject) : toolObject.getClass()))
                        .filter(toolMethod -> toolMethod.isAnnotationPresent(Tool.class))
                        .filter(toolMethod -> !isFunctionalType(toolMethod))
                        .map(toolMethod -> {
                            ToolCallResultConverter toolCallResultConverter = getCallResultConverter(toolMethod);
                            return MethodToolCallback.builder()
                                    .toolDefinition(getToolDefinition(toolMethod))
                                    .toolMetadata(ToolMetadata.from(toolMethod))
                                    .toolMethod(toolMethod)
                                    .toolObject(toolObject)
                                    .toolCallResultConverter(toolCallResultConverter)
                                    .build();
                        })
                        .toArray(ToolCallback[]::new))
                .flatMap(Stream::of)
                .toArray(ToolCallback[]::new);

        validateToolCallbacks(toolCallbacks);

        return toolCallbacks;
    }

    private @NotNull ToolCallResultConverter getCallResultConverter(Method toolMethod) {
        ToolCallResultConverter toolCallResultConverter = Optional.ofNullable(getToolCallResultConverter(toolMethod))
                .flatMap(c -> {
                    try {
                        ToolCallResultConverter bean = this.applicationContext.getBean(c);
                        return Optional.ofNullable(bean);
                    } catch (
                            Exception e) {
                        return Optional.empty();
                    }
                })
                .orElseGet(() -> {
                    ToolCallResultConverter r = ToolUtils.getToolCallResultConverter(toolMethod);
                    return r;
                });
        return toolCallResultConverter;
    }

    public static Class<? extends ToolCallResultConverter> getToolCallResultConverter(Method method) {
        Assert.notNull(method, "method cannot be null");
        var tool = method.getAnnotation(Tool.class);
        if (tool == null) {
            return DefaultToolCallResultConverter.class;
        }
        var type = tool.resultConverter();
        try {
            return type;
        }
        catch (Exception e) {
            throw new IllegalArgumentException("Failed to instantiate ToolCallResultConverter: " + type, e);
        }
    }

    private boolean isFunctionalType(Method toolMethod) {
        var isFunction = ClassUtils.isAssignable(toolMethod.getReturnType(), Function.class)
                         || ClassUtils.isAssignable(toolMethod.getReturnType(), Supplier.class)
                         || ClassUtils.isAssignable(toolMethod.getReturnType(), Consumer.class);

        if (isFunction) {
            logger.warn("Method {} is annotated with @Tool but returns a functional type. "
                        + "This is not supported and the method will be ignored.", toolMethod.getName());
        }

        return isFunction;
    }

    private void validateToolCallbacks(ToolCallback[] toolCallbacks) {
        List<String> duplicateToolNames = ToolUtils.getDuplicateToolNames(toolCallbacks);
        if (!duplicateToolNames.isEmpty()) {
            throw new IllegalStateException("Multiple tools with the same name (%s) found in sources: %s".formatted(
                    String.join(", ", duplicateToolNames),
                    this.toolObjects.stream().map(o -> o.getClass().getName()).collect(Collectors.joining(", "))));
        }
    }

    @Override
    public void setApplicationContext(@NotNull ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}
