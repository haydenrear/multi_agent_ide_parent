package com.hayden.multiagentide.repository;

import com.hayden.multiagentide.model.RunPersistenceCheck;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class InMemoryRunPersistenceCheckRepository implements RunPersistenceCheckRepository {

    private final Map<String, List<RunPersistenceCheck>> checksByRun = new ConcurrentHashMap<>();

    @Override
    public void saveAll(String runId, List<RunPersistenceCheck> checks) {
        if (runId == null || runId.isBlank()) {
            return;
        }
        checksByRun.put(runId, new ArrayList<>(checks));
    }

    @Override
    public List<RunPersistenceCheck> findByRunId(String runId) {
        if (runId == null || runId.isBlank()) {
            return List.of();
        }
        return List.copyOf(checksByRun.getOrDefault(runId, List.of()));
    }
}
