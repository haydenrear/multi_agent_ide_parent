package com.hayden.utilitymodule.ctx;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.core.annotation.AliasFor;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Repository;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Scope(DefaultListableBeanFactory.SCOPE_PROTOTYPE)
@Component
@Repository
public @interface PrototypeScope {
    @AliasFor(
            annotation = Component.class
    )
    String value() default "";
}
