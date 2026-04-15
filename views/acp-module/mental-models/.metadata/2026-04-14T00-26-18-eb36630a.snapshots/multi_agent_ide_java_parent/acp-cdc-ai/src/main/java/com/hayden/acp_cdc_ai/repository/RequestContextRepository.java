package com.hayden.acp_cdc_ai.repository;

import java.util.Optional;

public interface RequestContextRepository {

    RequestContext save(RequestContext context);

    Optional<RequestContext> findBySessionId(String sessionId);

    void deleteBySessionId(String sessionId);

    void clear();
}
