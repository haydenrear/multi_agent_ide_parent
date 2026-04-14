package com.hayden.utilitymodule.ctx;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Scope;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Scope
@Bean
public @interface PrototypeBean {
    @AliasFor(
            annotation = Bean.class
    )
    String[] name() default {};
    @AliasFor(
            annotation = Scope.class,
            attribute = "value"
    )
    String scopeAlias() default "prototype";
    @AliasFor(annotation = Bean.class, attribute = "value")
    String[] value() default {};
}
