package com.hayden.utilitymodule.telemetry.log;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.more.appenders.DataFluentAppender;
import com.hayden.utilitymodule.telemetry.TelemetryAttributesProvider;
import org.fluentd.logger.FluentLogger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.util.ReflectionUtils;

import java.util.Optional;


@Configuration
@Profile("telemetry-logging")
@ConditionalOnProperty(prefix = "tracing", value = "enabled", havingValue = "true")
public class LoggingConfig {

    @Bean
    YamlPropertySourceLoader yamlPropertySourceLoader() {
        return new YamlPropertySourceLoader();
    }

    @Bean
    @Order(Integer.MIN_VALUE)
    CommandLineRunner initializeLogger(ApplicationContext ctx) {
        var lf = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        if (lf instanceof Logger l) {
            l.iteratorForAppenders().forEachRemaining(a -> {
                if (a instanceof DataFluentAppender<ILoggingEvent> d) {
                    try {
                        var fluentLogger = d.getClass().getDeclaredField("fluentLogger");
                        fluentLogger.trySetAccessible();
                        var f = (FluentLogger) ReflectionUtils.getField(fluentLogger, d);
                        Optional.ofNullable(f).flatMap(s -> Optional.ofNullable(s.getSender()))
                                .stream()
                                .peek(ctx.getAutowireCapableBeanFactory()::autowireBean)
                                .peek(s -> {})
                                .forEach(s -> {});
                    } catch (NoSuchFieldException ignored) {}
                }
            });
        }

        return args -> {};
    }

    @Bean
    @ConditionalOnBean(TelemetryAttributesProvider.class)
    AttributeProvider attributesProvider(TelemetryAttributesProvider telemetryAttributes) {
        return telemetryAttributes.toAttributeProvider();
    }

}
