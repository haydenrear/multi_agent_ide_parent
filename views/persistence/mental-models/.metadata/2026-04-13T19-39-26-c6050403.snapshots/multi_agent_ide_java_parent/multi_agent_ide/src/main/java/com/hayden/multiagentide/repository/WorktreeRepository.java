package com.hayden.multiagentide.repository;

import com.hayden.multiagentide.model.worktree.WorktreeContext;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * Repository for storing and retrieving worktree prev (main and submodules).
 */
public interface WorktreeRepository {

    /**
     * Save a worktree context.
     * @param worktree the worktree to save
     */
    void save(WorktreeContext worktree);

    /**
     * Get a worktree by ID.
     * @param worktreeId the worktree ID
     * @return the worktree if found
     */
    Optional<WorktreeContext> findById(String worktreeId);

    default Optional<WorktreeContext> findByPath(Path worktreeId) {
        return findAll().stream()
                .filter(wc -> wc.worktreePath().equals(worktreeId))
                .findFirst();
    }

    /**
     * Get all worktrees.
     * @return list of all worktrees
     */
    List<WorktreeContext> findAll();

    /**
     * Get all worktrees for a given node.
     * @param nodeId the node ID
     * @return list of worktrees for that node
     */
    List<WorktreeContext> findByNodeId(String nodeId);

    /**
     * Get all child worktrees of a parent.
     * @param parentWorktreeId the parent worktree ID
     * @return list of child worktrees
     */
    List<WorktreeContext> findByParentId(String parentWorktreeId);

    /**
     * Get worktrees by status.
     * @param status the status
     * @return list of worktrees with that status
     */
    List<WorktreeContext> findByStatus(WorktreeContext.WorktreeStatus status);

    /**
     * Delete a worktree.
     * @param worktreeId the worktree ID
     */
    void delete(String worktreeId);

    /**
     * Check if worktree exists.
     * @param worktreeId the worktree ID
     * @return true if exists
     */
    boolean exists(String worktreeId);

    /**
     * Count all worktrees.
     * @return count of worktrees
     */
    long count();

    /**
     * Clear all worktrees.
     */
    void clear();
}
