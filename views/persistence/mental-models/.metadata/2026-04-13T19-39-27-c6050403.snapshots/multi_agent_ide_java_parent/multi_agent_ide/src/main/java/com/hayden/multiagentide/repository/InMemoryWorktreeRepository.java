package com.hayden.multiagentide.repository;

import com.hayden.multiagentide.model.worktree.WorktreeContext;
import org.springframework.stereotype.Repository;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of WorktreeRepository using ConcurrentHashMap for thread safety.
 */
@Repository
public class InMemoryWorktreeRepository implements WorktreeRepository {

    private final ConcurrentHashMap<String, WorktreeContext> worktrees = new ConcurrentHashMap<>();

    @Override
    public void save(WorktreeContext worktree) {
        worktrees.put(worktree.worktreeId(), worktree);
    }

    @Override
    public Optional<WorktreeContext> findById(String worktreeId) {
        return Optional.ofNullable(worktrees.get(worktreeId));
    }

    @Override
    public List<WorktreeContext> findAll() {
        return new ArrayList<>(worktrees.values());
    }

    @Override
    public List<WorktreeContext> findByNodeId(String nodeId) {
        return worktrees.values().stream()
                .filter(wt -> nodeId.equals(wt.associatedNodeId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<WorktreeContext> findByParentId(String parentWorktreeId) {
        return worktrees.values().stream()
                .filter(wt -> parentWorktreeId.equals(wt.parentWorktreeId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<WorktreeContext> findByStatus(WorktreeContext.WorktreeStatus status) {
        return worktrees.values().stream()
                .filter(wt -> wt.status() == status)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String worktreeId) {
        worktrees.remove(worktreeId);
    }

    @Override
    public boolean exists(String worktreeId) {
        return worktrees.containsKey(worktreeId);
    }

    @Override
    public long count() {
        return worktrees.size();
    }

    @Override
    public void clear() {
        worktrees.clear();
    }
}
