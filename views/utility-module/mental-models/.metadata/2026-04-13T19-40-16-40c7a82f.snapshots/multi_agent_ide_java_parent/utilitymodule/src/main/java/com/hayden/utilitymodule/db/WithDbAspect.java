package com.hayden.utilitymodule.db;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

import static com.hayden.utilitymodule.reflection.ParameterAnnotationUtils.resolveAnnotationForMethod;

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WithDbAspect  {

    private final DbDataSourceTrigger dbDataSourceTrigger;

    @Pointcut("@annotation(com.hayden.utilitymodule.db.WithDb)")
    public void withDbAnnotation(){}


    @Around("withDbAnnotation()")
    public Object switchDb(ProceedingJoinPoint pjp) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager
                .isActualTransactionActive() && log.isDebugEnabled()) {
            log.debug("Inside of Spring transaction - make sure to provide a transaction manager bean with the abstract routing data source.");
        }
        return dbDataSourceTrigger.doOnKey(sk -> {

            resolveAnnotationForMethod(pjp, WithDb.class)
                    .map(WithDb::value)
                    .ifPresentOrElse(
                            k -> {
                                if (org.springframework.transaction.support.TransactionSynchronizationManager
                                        .isActualTransactionActive() && !Objects.equals(k, sk.curr())) {
                                    log.error(
                                            "@WithDb must run before a transaction opens; " +
                                                    "ensure this aspect has highest precedence and avoid self-invocation."
                                    );

                                }

                                sk.setKey(k);
                            },
                            () -> log.error("Could not resolve WithDb annotation!"));
            try {
                return pjp.proceed(pjp.getArgs());
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }


}
