package com.hayden.utilitymodule.reflection;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotatedElementUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public interface ParameterAnnotationUtils {

    public record AnnotatedArg<T extends Annotation>(Integer idx, T t) {}

    static <T extends Annotation> List<AnnotatedArg<T>> retrieveArgsIndex(ProceedingJoinPoint pjp, Class<T> annotationClazz) {
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        Class<?> targetClass = pjp.getTarget() != null ? pjp.getTarget().getClass() : sig.getDeclaringType();
        Method specific = AopUtils.getMostSpecificMethod(method, targetClass);

        // 2) Locate any parameter annotated with @SessionVariable
        return findAllAnnotatedParameterIndex(specific, annotationClazz);
    }

    static Optional<Integer> retrieveArgIndex(ProceedingJoinPoint pjp, Class<? extends Annotation> annotationClazz) {
        // 1) Resolve the concrete Method (handle proxies)
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        Method method = sig.getMethod();
        Class<?> targetClass = pjp.getTarget() != null ? pjp.getTarget().getClass() : sig.getDeclaringType();
        Method specific = AopUtils.getMostSpecificMethod(method, targetClass);

        // 2) Locate any parameter annotated with @SessionVariable
        return findFirstAnnotatedParameterIndex(specific, annotationClazz);
    }

    private static <T extends Annotation> List<AnnotatedArg<T>> findAllAnnotatedParameterIndex(Method m, Class<T> annoType) {
        Parameter[] params = m.getParameters();

        return IntStream.range(0, params.length)
                .boxed()
                .flatMap(i -> {
                    T mergedAnnotation = AnnotatedElementUtils.findMergedAnnotation(params[i], annoType);
                    if (mergedAnnotation != null) {
                        return Stream.of(new AnnotatedArg<T>(i, mergedAnnotation));
                    }

                    return Stream.empty();
                })
                .toList();
    }

    /** Returns the first parameter index annotated with the given annotation type. */
    private static Optional<Integer> findFirstAnnotatedParameterIndex(Method m, Class<? extends Annotation> annoType) {
        Parameter[] params = m.getParameters();
        for (int i = 0; i < params.length; i++) {
            // If you ever add meta-annotations, AnnotatedElementUtils can merge them:
            if (AnnotatedElementUtils.findMergedAnnotation(params[i], annoType) != null) {
                return Optional.of(i);
            }
        }
        return Optional.empty();
    }

    static <T extends Annotation> Optional<T> resolveAnnotationForMethod(ProceedingJoinPoint pjp,
                                                                          Class<T> annotationClazz) {
        var sig = (MethodSignature) pjp.getSignature();
        var method = sig.getMethod();
        var ann = org.springframework.core.annotation.AnnotatedElementUtils
                .findMergedAnnotation(method, annotationClazz);

        return Optional.ofNullable(ann)
                .or(() -> {
                    Class<?> target = pjp.getTarget() != null ? pjp.getTarget().getClass() : sig.getDeclaringType();
                    return Optional.ofNullable(org.springframework.core.annotation.AnnotatedElementUtils
                            .findMergedAnnotation(target, annotationClazz));
                });
    }
}
