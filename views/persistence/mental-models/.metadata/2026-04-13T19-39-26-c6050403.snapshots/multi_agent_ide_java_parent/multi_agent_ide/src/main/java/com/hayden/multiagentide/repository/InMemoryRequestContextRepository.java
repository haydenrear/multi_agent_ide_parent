package com.hayden.multiagentide.repository;

import com.hayden.acp_cdc_ai.repository.RequestContext;
import com.hayden.acp_cdc_ai.repository.RequestContextRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryRequestContextRepository implements RequestContextRepository {

    private final ConcurrentHashMap<String, RequestContext> contexts = new ConcurrentHashMap<>();

    @Override
    public RequestContext save(RequestContext context) {
        if (context == null || context.sessionId() == null) {
            return context;
        }
        contexts.put(context.sessionId(), context);
        return context;
    }

    @Override
    public Optional<RequestContext> findBySessionId(String sessionId) {
        return Optional.ofNullable(contexts.get(sessionId));
    }

    @Override
    public void deleteBySessionId(String sessionId) {
        if (sessionId == null) {
            return;
        }
        contexts.remove(sessionId);
    }

    @Override
    public void clear() {
        contexts.clear();
    }
}
