package com.hayden.utilitymodule.telemetry.log;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.sdk.resources.Resource;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.fluentd.logger.errorhandler.ErrorHandler;
import org.fluentd.logger.sender.Sender;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;


/**
 * Note VM Argument Required:
 * -Dfluentd.logger.sender.class=com.hayden.utilitymodule.log.FluentDRestTemplateSender
 */
@RequiredArgsConstructor
public class FluentDRestTemplateSender implements Sender{


    @Getter
    private final AtomicBoolean set = new AtomicBoolean(false);

    @Autowired
    private Resource resource;

    private RestTemplate restTemplate;

    private ErrorHandler handler;

    private final String url;
    private final int timeout;
    private final int bufferCapacity;

    public FluentDRestTemplateSender(String host, int port,
                                     int timeout, int bufferCapacity) {
        this.url = "http://%s:%s".formatted(host, port);
        this.timeout = timeout;
        this.bufferCapacity = bufferCapacity;
    }

    @Override
    public boolean emit(String s, Map<String, Object> map) {
        try {
            Optional.ofNullable(resource)
                    .stream()
                    .flatMap(r -> r.getAttributes().asMap().entrySet().stream())
                    .forEach((attributeKeyValue) -> map.put(attributeKeyValue.getKey().getKey(), attributeKeyValue.getValue()));

            var loggingValue = new ObjectMapper().writeValueAsString(map);

            return restTemplate().map(rt -> rt.postForEntity(this.url, loggingValue, String.class))
                    .map(FluentDRestTemplateSender::parseResponseToDidSucceed)
                    .orElse(true);
        } catch (IOException e) {
            handler.handleNetworkError(e);
            System.out.printf("FAIL in the networking for the logging. How do you log about a log? %s%n", e.getMessage());
            return false;
        }
    }

    private Optional<RestTemplate> restTemplate() {
        return Optional.ofNullable(this.restTemplate)
                .or(() -> {
                    this.restTemplate = new RestTemplateBuilder()
                            .rootUri(this.url)
                            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                            .build();
                    return Optional.of(this.restTemplate);
                });
    }

    private static boolean parseResponseToDidSucceed(ResponseEntity<String> posted) {
        if (!posted.getStatusCode().is2xxSuccessful()) {
            System.out.printf("Call to fluentbit was not successful %s%n", posted.getStatusCode());
        }
        return posted.getStatusCode().is2xxSuccessful();
    }

    @Override
    public boolean emit(String s, long l, Map<String, Object> map) {
        return this.emit(s, map);
    }

    @Override
    public void flush() {
    }

    @Override
    public void close() {
    }

    @Override
    public String getName() {
        return "Logback FluentD Logger Sender";
    }

    @Override
    public boolean isConnected() {
        return true;
    }

    @Override
    public void setErrorHandler(ErrorHandler errorHandler) {
        this.handler = errorHandler;
    }

    @Override
    public void removeErrorHandler() {
    }

}
