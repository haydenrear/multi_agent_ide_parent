package com.hayden.multiagentide.tool;

import com.embabel.agent.api.tool.ToolObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Component
public class EmbabelToolObjectRegistry implements EmbabelToolObjectProvider {

    Map<String, LazyToolObjectRegistration> toolObjectMap = new ConcurrentHashMap<>();

    interface ToolRegistration {

        Optional<List<ToolObject>> computeToolObject(LazyToolObjectRegistration toolObjectRegistration);

    }

    void register(String name, LazyToolObjectRegistration toolObject) {
        toolObjectMap.put(name, toolObject);
    }

    @Override
    public Optional<List<ToolObject>> tool(String name) {
        var t = RetryTemplate.builder()
                .maxAttempts(5)
                .retryOn(RuntimeException.class)
                .fixedBackoff(Duration.ofSeconds(3))
                .build()
                .execute(r -> {
                    AtomicReference<List<ToolObject>> tools = new AtomicReference<>();

//                  for concurrency/lock striping
                    toolObjectMap.computeIfPresent(name, (ignored, v) -> {
                                v.compute().ifPresent(tools::set);
                                return v;
                            });

                    var f = Optional.ofNullable(tools.get());

                    if (f.isPresent() && CollectionUtils.isEmpty(f.get())) {
                        log.error("Found tools list response with empty list - {}.", name);
                    }

                    if (f.isEmpty()) {
                        log.error("Failed to boot MCP server - {} number of retries.", r.getRetryCount());
                    }

                    return f;
                });

        return t;
    }
}
