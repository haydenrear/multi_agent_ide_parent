package com.hayden.multiagentide.repository;

import com.hayden.multiagentide.model.RunPersistenceCheck;

import java.util.List;

public interface RunPersistenceCheckRepository {

    void saveAll(String runId, List<RunPersistenceCheck> checks);

    List<RunPersistenceCheck> findByRunId(String runId);
}
