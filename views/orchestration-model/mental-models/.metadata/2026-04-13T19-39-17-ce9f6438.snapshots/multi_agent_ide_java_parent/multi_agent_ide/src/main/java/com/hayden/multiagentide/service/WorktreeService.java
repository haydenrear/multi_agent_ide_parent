package com.hayden.multiagentide.service;

import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.model.worktree.MainWorktreeContext;
import com.hayden.multiagentide.model.worktree.SubmoduleWorktreeContext;
import com.hayden.multiagentide.model.worktree.WorktreeSandboxContext;
import com.hayden.multiagentide.model.MergeResult;
import com.hayden.multiagentide.model.merge.MergeDescriptor;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing git worktrees including main repo and submodules.
 */
public interface WorktreeService {

    /**
     * Create a worktree for the main repository.
     * @param repositoryUrl the repository URL
     * @param baseBranch the base branch (e.g., "main", "develop")
     * @param nodeId the associated node ID
     * @return the created main worktree context
     */
    MainWorktreeContext createMainWorktree(String repositoryUrl, String baseBranch, String derivedBranch, String nodeId);

    AgentModels.DiscoveryAgentRequests attachWorktreesToDiscoveryRequests(
            AgentModels.DiscoveryAgentRequests input,
            String nodeId
    );
    AgentModels.PlanningAgentRequests attachWorktreesToPlanningRequests(
            AgentModels.PlanningAgentRequests input,
            String nodeId
    );
    AgentModels.TicketAgentRequests attachWorktreesToTicketRequests(
            AgentModels.TicketAgentRequests input,
            String nodeId
    );

    /**
     * Get a worktree context by ID.
     * @param worktreeId the worktree ID
     * @return the worktree context if found
     */
    Optional<MainWorktreeContext> getMainWorktree(String worktreeId);

    /**
     * Get a submodule worktree context by ID.
     * @param worktreeId the worktree ID
     * @return the submodule worktree context if found
     */
    Optional<SubmoduleWorktreeContext> getSubmoduleWorktree(String worktreeId);

    /**
     * Get all submodule worktrees for a main worktree.
     * @param mainWorktreeId the main worktree ID
     * @return list of submodule worktree prev
     */
    List<SubmoduleWorktreeContext> getSubmoduleWorktrees(String mainWorktreeId);

    /**
     * Merge a child worktree into a parent worktree.
     * @param childWorktreeId the child worktree ID
     * @param parentWorktreeId the parent worktree ID
     * @return merge result with conflict information
     */
    MergeResult mergeWorktrees(String childWorktreeId, String parentWorktreeId);

    /**
     * Branch a worktree (create a new independent worktree from existing).
     * @param sourceWorktreeId the source worktree ID
     * @param newBranchName the new branch name
     * @param nodeId the associated node ID for the branch
     * @return the created branched worktree context
     */
    MainWorktreeContext branchWorktree(String sourceWorktreeId, String newBranchName, String nodeId);

    /**
     * Branch a submodule worktree.
     * @param sourceWorktreeId the source submodule worktree ID
     * @param newBranchName the new branch name
     * @param nodeId the associated node ID for the branch
     * @return the created branched submodule worktree context
     */
    SubmoduleWorktreeContext branchSubmoduleWorktree(String sourceWorktreeId, String newBranchName, String nodeId);

    /**
     * Discard/remove a worktree and its children.
     * @param worktreeId the worktree ID
     */
    void discardWorktree(String worktreeId);

    /**
     * Update submodule pointer in main worktree after submodule changes.
     * @param mainWorktreeId the main worktree ID
     * @param submoduleName the submodule name
     */
    void updateSubmodulePointer(String mainWorktreeId, String submoduleName);

    /**
     * Merge the orchestrator's derived branches back into the original base branches
     * in the source repository. This is the final merge that pushes all agent work
     * back to the source repo after the orchestrator completes.
     *
     * For each submodule: fetches from worktree submodule, merges derived branch
     * into the source repo's original submodule branch.
     * For the main repo: fetches from worktree, merges derivedBranch into baseBranch.
     * Updates submodule pointers in the source repo.
     *
     * @param mainWorktreeId the orchestrator's main worktree ID
     * @return merge result with conflict information
     */
    MergeResult finalMergeToSource(String mainWorktreeId);

    /**
     * Ensure merge conflicts are fully captured after a merge, including detected
     * git conflicts or missing child commits in the parent.
     *
     * @param result the merge result to validate
     * @return updated merge result with conflicts populated if needed
     */
    MergeResult ensureMergeConflictsCaptured(MergeResult result);

    /**
     * Ensure merge conflicts are fully captured for final merges to source, including
     * missing commits in the source repo and submodules.
     *
     * @param result the merge result to validate
     * @param mainWorktree the orchestrator's main worktree context
     * @return updated merge result with conflicts populated if needed
     */
    MergeResult ensureMergeConflictsCaptured(MergeResult result, MainWorktreeContext mainWorktree);

    /**
     * Merge trunk worktree into child worktree and return a MergeDescriptor.
     * Uses the internal merge plan which handles submodules leaves-first
     * and continues merging non-conflicting siblings.
     *
     * @param trunk the trunk (parent) sandbox context
     * @param child the child sandbox context
     * @return MergeDescriptor with TRUNK_TO_CHILD direction
     */
    MergeDescriptor mergeTrunkToChild(WorktreeSandboxContext trunk, WorktreeSandboxContext child);

    /**
     * Merge child worktree into trunk worktree and return a MergeDescriptor.
     * Also updates submodule pointers in trunk on success.
     *
     * @param child the child sandbox context
     * @param trunk the trunk (parent) sandbox context
     * @return MergeDescriptor with CHILD_TO_TRUNK direction
     */
    MergeDescriptor mergeChildToTrunk(WorktreeSandboxContext child, WorktreeSandboxContext trunk);

    /**
     * Final merge of worktree derived branches back into the source repository.
     * Calls finalMergeToSource internally and translates to MergeDescriptor.
     *
     * @param mainWorktreeId the orchestrator's main worktree ID
     * @return MergeDescriptor with WORKTREE_TO_SOURCE direction
     */
    MergeDescriptor finalMergeToSourceDescriptor(String mainWorktreeId);

}
