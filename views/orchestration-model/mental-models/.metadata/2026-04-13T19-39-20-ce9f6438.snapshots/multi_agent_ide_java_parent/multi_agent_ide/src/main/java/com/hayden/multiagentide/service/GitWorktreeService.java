package com.hayden.multiagentide.service;

import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.model.MergeResult;
import com.hayden.multiagentide.model.merge.MergeDescriptor;
import com.hayden.multiagentide.model.merge.MergeDirection;
import com.hayden.multiagentide.model.merge.SubmoduleMergeResult;
import com.hayden.multiagentide.model.worktree.MainWorktreeContext;
import com.hayden.multiagentide.model.worktree.SubmoduleWorktreeContext;
import com.hayden.multiagentide.model.worktree.WorktreeSandboxContext;
import com.hayden.multiagentide.model.worktree.WorktreeContext;
import com.hayden.multiagentide.repository.WorktreeRepository;
import com.hayden.utilitymodule.git.RepoUtil;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.CheckoutCommand;
import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.MergeCommand;
import org.eclipse.jgit.api.Status;
import org.eclipse.jgit.api.errors.EmptyCommitException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.submodule.SubmoduleStatus;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;

/**
 * Git-based implementation of WorktreeService using git worktree commands.
 * Handles both main repository and git submodule worktrees.
 */
@Slf4j
@Service
public class GitWorktreeService implements WorktreeService {

    private final WorktreeRepository worktreeRepository;
    private final String baseWorktreesPath;

    @Lazy
    @Autowired
    private EventBus eventBus;

    public GitWorktreeService(
            WorktreeRepository worktreeRepository,
            @Value("${multiagentide.worktrees.base-path:}") String baseWorktreesPath
    ) {
        this.worktreeRepository = worktreeRepository;
        if (baseWorktreesPath == null || baseWorktreesPath.isBlank()) {
            this.baseWorktreesPath = System.getProperty("user.home") + "/.multi-agent-ide/worktrees";
        } else {
            this.baseWorktreesPath = baseWorktreesPath;
        }
    }

    @Override
    public MainWorktreeContext createMainWorktree(String repositoryUrl, String baseBranch, String derivedBranch, String nodeId) {
        String worktreeId = UUID.randomUUID().toString();
        Path worktreePath = Paths.get(baseWorktreesPath, worktreeId);

        try {
            validateCloneSource(repositoryUrl, baseBranch, nodeId);
            Files.createDirectories(worktreePath);
            performSubmoduleClone(repositoryUrl, baseBranch, derivedBranch, worktreePath);
            List<String> submodulePaths = initializeSubmodule(worktreePath, derivedBranch);
            checkoutNewBranch(worktreePath, derivedBranch, derivedBranch);

//            we pass in the baseBranch of the parent here because when we clone the
//            submodules it puts it in a detached head. So we do a checkout -b ...
//            and we assume that it's either on that same commit as the branch or will
//            create a new branch with the name of the commit.
            List<SubmoduleWorktreeContext> submodules = createSubmoduleContexts(
                    submodulePaths,
                    worktreeId,
                    worktreePath,
                    nodeId,
                    derivedBranch
            );

            String commitHash = getCurrentCommitHashInternal(worktreePath);

            MainWorktreeContext context = new MainWorktreeContext(
                    worktreeId,
                    worktreePath,
                    baseBranch,
                    derivedBranch,
                    WorktreeContext.WorktreeStatus.ACTIVE,
                    null,
                    nodeId,
                    Instant.now(),
                    commitHash,
                    repositoryUrl,
                    !submodules.isEmpty(),
                    submodules,
                    new HashMap<>()
            );

            worktreeRepository.save(context);
            for (SubmoduleWorktreeContext submoduleContext : submodules) {
                worktreeRepository.save(submoduleContext);
            }
            return context;
        } catch (IOException | GitAPIException e) {
            throw new RuntimeException("Failed to create main worktree: " + e.getMessage(), e);
        }
    }

    public static void performSubmoduleClone(String repositoryUrl, String baseBranch,
                                             String derivedBranch, Path worktreePath) throws GitAPIException, IOException {
        cloneRepository(repositoryUrl, worktreePath, baseBranch);
        checkoutNewBranch(worktreePath, derivedBranch, null);
    }

    private SubmoduleWorktreeContext createSubmoduleWorktree(String submoduleName, String submodulePath,
                                                             String parentWorktreeId, Path parentWorktreePath, String nodeId,
                                                             String parentBranch) {
        String worktreeId = UUID.randomUUID().toString();
        Path submoduleFullPath = parentWorktreePath.resolve(submodulePath);

        try {
            List<String> updatedSubmodules = initializeSubmodule(parentWorktreePath, parentBranch);
            if (submodulePath != null && !submodulePath.isBlank()
                    && updatedSubmodules.stream().noneMatch(p -> p.equals(submodulePath))) {
                throw new RuntimeException("Failed to initialize submodule path: " + submodulePath);
            }

            try(var g = openGit(parentWorktreePath)) {
                String branch = g.getRepository().getBranch();
                ensureBranchCheckedOut(submoduleFullPath, parentBranch);
                String commitHash = getCurrentCommitHashInternal(submoduleFullPath);

                return new SubmoduleWorktreeContext(
                        worktreeId,
                        submoduleFullPath,
                        branch, // submodules typically use main or master
                        WorktreeContext.WorktreeStatus.ACTIVE,
                        parentWorktreeId,
                        nodeId,
                        Instant.now(),
                        commitHash,
                        submoduleName,
                        "", // submoduleUrl - will be fetched from .gitmodules if needed
                        parentWorktreeId,  // mainWorktreeId
                        new HashMap<>()
                );
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to create submodule worktree: " + e.getMessage(), e);
        }
    }

    private void ensureBranchCheckedOut(Path repoPath, String branchName) {
        RepoUtil.initGit(repoPath)
                .exceptEmpty(exc -> {
                    log.error("Error", exc);
                })
                .ifPresent(git -> {
                    try {
                        Set<String> collect = git.branchList().call().stream().map(Ref::getName)
                                .collect(Collectors.toSet());
                        if (collect.stream().noneMatch(s -> s.endsWith("/" + branchName))) {
                            git.checkout().setName(branchName).setCreateBranch(true).call();
                        } else if(!Objects.equals(git.getRepository().getBranch(), branchName)) {
                            git.checkout().setName(branchName).call();
                        }
                    } catch (GitAPIException |
                             IOException e) {
                        log.error("Error when attempting to checkout a branch!");
                        throw new RuntimeException(e);
                    }
                });
    }

    @Override
    public Optional<MainWorktreeContext> getMainWorktree(String worktreeId) {
        return worktreeRepository.findById(worktreeId)
                .filter(wt -> wt instanceof MainWorktreeContext)
                .map(wt -> (MainWorktreeContext) wt);
    }

    @Override
    public Optional<SubmoduleWorktreeContext> getSubmoduleWorktree(String worktreeId) {
        return worktreeRepository.findById(worktreeId)
                .filter(wt -> wt instanceof SubmoduleWorktreeContext)
                .map(wt -> (SubmoduleWorktreeContext) wt);
    }

    @Override
    public List<SubmoduleWorktreeContext> getSubmoduleWorktrees(String mainWorktreeId) {
        return worktreeRepository.findByParentId(mainWorktreeId).stream()
                .filter(wt -> wt instanceof SubmoduleWorktreeContext)
                .map(wt -> (SubmoduleWorktreeContext) wt)
                .collect(Collectors.toList());
    }

    public boolean hasSubmodules(Path repositoryPath) {
        return hasSubmodulesInternal(repositoryPath);
    }

    public List<String> getSubmoduleNames(Path repositoryPath) {
        Path gitmodulesPath = repositoryPath.resolve(".gitmodules");
        if (!Files.exists(gitmodulesPath)) {
            return new ArrayList<>();
        }

        try {
            FileBasedConfig config = new FileBasedConfig(gitmodulesPath.toFile(), FS.DETECTED);
            config.load();
            return new ArrayList<>(config.getSubsections("submodule"));
        } catch (Exception e) {
            System.err.println("Warning: Could not get submodule names: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public Path getSubmodulePath(Path repositoryPath, String submoduleName) {
        Path gitmodulesPath = repositoryPath.resolve(".gitmodules");
        if (!Files.exists(gitmodulesPath)) {
            throw new RuntimeException("Failed to get submodule path: .gitmodules not found");
        }
        try {
            FileBasedConfig config = new FileBasedConfig(gitmodulesPath.toFile(), FS.DETECTED);
            config.load();
            String pathValue = config.getString("submodule", submoduleName, "path");
            if (pathValue == null || pathValue.isBlank()) {
                throw new RuntimeException("Failed to get submodule path: missing path for " + submoduleName);
            }
            return repositoryPath.resolve(pathValue.trim());
        } catch (Exception e) {
            throw new RuntimeException("Failed to get submodule path: " + e.getMessage(), e);
        }
    }

    // ======== SUBMODULE PUSH HELPERS ========

    /**
     * Read the URL for a submodule from .gitmodules.
     */
    private String getSubmoduleUrl(Path repositoryPath, String submoduleName) {
        Path gitmodulesPath = repositoryPath.resolve(".gitmodules");
        if (!Files.exists(gitmodulesPath)) {
            return null;
        }
        try {
            FileBasedConfig config = new FileBasedConfig(gitmodulesPath.toFile(), FS.DETECTED);
            config.load();
            String url = config.getString("submodule", submoduleName, "url");
            return url != null && !url.isBlank() ? url.trim() : null;
        } catch (Exception e) {
            log.warn("Could not read submodule URL for '{}': {}", submoduleName, e.getMessage());
            return null;
        }
    }

    /**
     * Returns true if the URL is a real remote (http, https, git@, ssh://, git://)
     * rather than a local filesystem path.
     */
    private static boolean isRemoteUrl(String url) {
        if (url == null || url.isBlank()) {
            return false;
        }
        return url.startsWith("http://") || url.startsWith("https://")
                || url.startsWith("git@") || url.startsWith("ssh://")
                || url.startsWith("git://");
    }

    /**
     * Push submodule commits to their remotes so that the commit SHAs referenced by
     * the parent's submodule pointers actually exist on the remote when a downstream
     * clone fetches from .gitmodules URLs.
     *
     * This method never throws — all errors are logged and emitted on the event bus.
     * A failed push for one submodule does not prevent pushing the others.
     *
     * @param repoPath   the parent repo whose submodules should be pushed
     * @param branchHint the branch name to attempt pushing as (e.g. the derived branch)
     * @param nodeId     optional node ID for event bus error reporting (may be null)
     */
    void pushSubmoduleCommitsToRemote(Path repoPath, String branchHint, String nodeId) {
        List<String> submoduleNames;
        try {
            submoduleNames = getSubmoduleNames(repoPath);
        } catch (Exception e) {
            log.warn("Could not enumerate submodules for push at {}: {}", repoPath, e.getMessage());
            return;
        }

        for (String submoduleName : submoduleNames) {
            try {
                String url = getSubmoduleUrl(repoPath, submoduleName);
                if (!isRemoteUrl(url)) {
                    log.debug("Skipping push for submodule '{}': URL '{}' is not a remote.", submoduleName, url);
                    continue;
                }

                Path submodulePath;
                try {
                    submodulePath = getSubmodulePath(repoPath, submoduleName);
                } catch (Exception e) {
                    log.warn("Could not resolve path for submodule '{}', skipping push: {}", submoduleName, e.getMessage());
                    continue;
                }

                if (!Files.isDirectory(submodulePath)) {
                    continue;
                }

                // First attempt: push HEAD to the hinted branch name on the remote URL.
                var result = RepoUtil.runGitCommand(submodulePath,
                        List.of("push", url, "HEAD:refs/heads/" + branchHint));

                if (result.isOk()) {
                    log.debug("Pushed submodule '{}' to remote branch '{}'", submoduleName, branchHint);
                    continue;
                }

                // Fallback: push to a unique branch name (commit SHA is all that matters).
                String fallbackBranch = "push-" + UUID.randomUUID().toString().substring(0, 8);
                log.info("Push to '{}' failed for submodule '{}', retrying with fallback branch '{}': {}",
                        branchHint, submoduleName, fallbackBranch, result.errorMessage());

                var fallbackResult = RepoUtil.runGitCommand(submodulePath,
                        List.of("push", url, "HEAD:refs/heads/" + fallbackBranch));

                if (fallbackResult.isOk()) {
                    log.info("Pushed submodule '{}' to fallback branch '{}'", submoduleName, fallbackBranch);
                } else {
                    String errMsg = "Failed to push submodule '%s' to remote even with fallback branch '%s': %s"
                            .formatted(submoduleName, fallbackBranch, fallbackResult.errorMessage());
                    log.error(errMsg);
                    if (eventBus != null && nodeId != null && !nodeId.isBlank()) {
                        eventBus.publish(Events.NodeErrorEvent.err(errMsg, getKey(nodeId)));
                    }
                }
            } catch (Exception e) {
                String errMsg = "Unexpected error pushing submodule '%s' at %s: %s"
                        .formatted(submoduleName, repoPath, e.getMessage());
                log.error(errMsg, e);
                if (eventBus != null && nodeId != null && !nodeId.isBlank()) {
                    eventBus.publish(Events.NodeErrorEvent.err(errMsg, getKey(nodeId)));
                }
            }
        }
    }

    // ======== MERGE ACTION MODEL ========

    /**
     * State snapshot captured before and after each merge action for verification.
     */
    private record RepoStateSnapshot(
            String headCommit,
            String branch,
            boolean isClean
    ) {
        static RepoStateSnapshot capture(GitWorktreeService service, Path repoPath) {
            String head = service.getCurrentCommitHashInternal(repoPath);
            String branch = service.getBranchSafe(repoPath);
            boolean clean = service.isCleanIgnoringSubmodules(repoPath);
            return new RepoStateSnapshot(head, branch, clean);
        }
    }

    /**
     * Mutable state carried through the merge plan execution.
     */
    private static class MergeExecutionState {
        final List<MergeResult.MergeConflict> allConflicts = new ArrayList<>();
        final Set<Path> blockedParentPaths = new HashSet<>();

        void addConflicts(List<MergeResult.MergeConflict> conflicts, MergePlanStep step) {
            allConflicts.addAll(conflicts);
            if (step.parentOfParentPath() != null) {
                blockedParentPaths.add(step.parentOfParentPath());
            }
        }

        boolean isBlocked(MergePlanStep step) {
            return blockedParentPaths.contains(step.parentPath());
        }

        void propagateBlock(MergePlanStep step) {
            if (step.parentOfParentPath() != null) {
                blockedParentPaths.add(step.parentOfParentPath());
            }
        }

        boolean hasConflicts() {
            return !allConflicts.isEmpty();
        }
    }
   
    @Override
    public MergeResult mergeWorktrees(String childWorktreeId, String parentWorktreeId) {
        Optional<WorktreeContext> childWt = worktreeRepository.findById(childWorktreeId);
        Optional<WorktreeContext> parentWt = worktreeRepository.findById(parentWorktreeId);

        if (childWt.isEmpty() || parentWt.isEmpty()) {
            MergeResult result = new MergeResult(
                    UUID.randomUUID().toString(),
                    childWorktreeId,
                    parentWorktreeId,
                    childWt.map(WorktreeContext::worktreePath).map(this::normalizePath).orElse(null),
                    parentWt.map(WorktreeContext::worktreePath).map(this::normalizePath).orElse(null),
                    false,
                    null,
                    List.of(),
                    List.of(),
                    "Worktree not found",
                    Instant.now()
            );
            emitMergeFailure(result, childWt.orElse(null), parentWt.orElse(null), "Worktree not found for merge request.");
            return result;
        }
        
        Path parentPath = parentWt.get().worktreePath();
        Path childPath = childWt.get().worktreePath();
        String childBranch = childWt.get().derivedBranch();
        WorktreeContext childContext = childWt.get();
        WorktreeContext parentContext = parentWt.get();

        try {
            return mergeWorktrees(childContext, parentContext);
        } catch (Exception e) {
            return getMergeResult(childContext, parentContext, e, parentPath, childPath, childBranch, childWorktreeId, parentWorktreeId);
        }
    }

    @NonNull
    public MergeResult mergeWorktrees(WorktreeContext childContext, WorktreeContext parentContext)  {
        String childWorktreeId = childContext.worktreeId();
        String parentWorktreeId = parentContext.worktreeId();
        Path childPath = childContext.worktreePath();
        Path parentPath = parentContext.worktreePath();
        String childBranch = childContext.derivedBranch();

        // Phase 1: Build comprehensive merge plan (leaves first, root last).
        try {
            List<MergePlanStep> plan = buildMergePlan(childPath, parentPath, childContext.derivedBranch());
            log.info("Merge plan has {} steps", plan.size());
            log.info("Merge plan: {}", plan);

            // Phase 2: Execute each step with before/after actions and verification.
            MergeExecutionState state = new MergeExecutionState();
            for (MergePlanStep step : plan) {
                log.info("Merging next step in plan: {}", step);
                executeStepWithLifecycle(step, state, childContext, parentContext);
            }

            // Phase 3: Finalize — commit pointers, restore branches, update metadata.
            if (state.hasConflicts()) {
                switchToBranches(parentPath, childPath, childContext.derivedBranch());
                MergeResult conflictResult = buildConflictResult(childWorktreeId, parentWorktreeId, state.allConflicts);
                emitMergeFailure(conflictResult, childContext, parentContext, "Merge conflicts detected.");
                return conflictResult;
            }

            MergeResult success = finalizeSuccessfulMerge(childWorktreeId, parentWorktreeId, childContext, parentContext);
            emitMergeLifecycleEvent(success, childContext, parentContext, "main");
            return success;
        } catch (Exception e) {
            return getMergeResult(childContext, parentContext, e, parentPath, childPath, childBranch, childWorktreeId, parentWorktreeId);
        }
    }

    private @NonNull MergeResult getMergeResult(WorktreeContext childContext, WorktreeContext parentContext, Exception e, Path parentPath, Path childPath, String childBranch, String childWorktreeId, String parentWorktreeId) {
        switchToBranches(parentPath, childPath, childBranch);
        MergeResult result = new MergeResult(
                UUID.randomUUID().toString(),
                childWorktreeId,
                parentWorktreeId,
                this.normalizePath(childPath),
                this.normalizePath(parentPath),
                false,
                null,
                List.of(),
                List.of(),
                "Merge failed: " + e.getMessage(),
                Instant.now()
        );
        emitMergeFailure(result, childContext, parentContext, "Merge execution failed: " + e.getMessage());
        return result;
    }

    /**
     * Execute a single merge plan step through its full lifecycle:
     *   1. Check if blocked by child conflicts
     *   2. Capture before-state snapshot
     *   3. Execute the merge action
     *   4. Run after-actions (commit dirty pointers)
     *   5. Verify after-state (parent HEAD should have advanced, repo should be clean)
     */
    private void executeStepWithLifecycle(
            MergePlanStep step,
            MergeExecutionState state,
            WorktreeContext rootChildContext,
            WorktreeContext rootParentContext
    ) {
        // Pre-check: skip if a child step had conflicts.
        if (state.isBlocked(step)) {
            state.propagateBlock(step);
            log.debug("Skipping blocked step: sub={}", step.submodulePath());
            return;
        }

        // Before-state snapshot.
        RepoStateSnapshot parentBefore = RepoStateSnapshot.capture(this, step.parentPath());
        log.debug("Step sub={}: before parentHEAD={} branch={}", step.submodulePath(),
                parentBefore.headCommit(), parentBefore.branch());

        try {
            // Execute merge action.
            List<MergeResult.MergeConflict> conflicts = doMerge(step);

            if (!conflicts.isEmpty()) {
                emitMergeStepConflict(step, rootChildContext, rootParentContext, conflicts);
                state.addConflicts(conflicts, step);
                return;
            }

            // After-actions: commit dirty submodule pointers in the merged repo and its parent.
            commitDirtySubmodulePointers(step.parentPath());

            if (step.parentOfParentPath() != null) {
                commitDirtySubmodulePointers(step.parentOfParentPath());
            }

            // After-state snapshot and verification.
            RepoStateSnapshot parentAfter = RepoStateSnapshot.capture(this, step.parentPath());
            log.debug("Step sub={}: after parentHEAD={} branch={} clean={}",
                    step.submodulePath(), parentAfter.headCommit(), parentAfter.branch(), parentAfter.isClean());

            verifyStepResult(step, parentBefore, parentAfter);
            emitMergeStepSuccess(step, rootChildContext, rootParentContext);

            // Push submodule commits to their remotes so downstream clones can find them.
            String branchHint = step.childBranch() != null ? step.childBranch() : step.parentBranch();
            String nodeId = rootParentContext != null ? rootParentContext.associatedNodeId() : null;
            pushSubmoduleCommitsToRemote(step.parentPath(), branchHint, nodeId);
        } catch (Exception e) {
            String label = step.submodulePath() != null ? step.submodulePath() : "root";
            List<MergeResult.MergeConflict> conflicts = List.of(new MergeResult.MergeConflict(
                    label, "merge-error", "", "", "", step.formattedSubmodulePath()
            ));
            emitMergeStepConflict(step, rootChildContext, rootParentContext, conflicts);
        }
    }

    /**
     * Core merge action: fetch from child, merge into parent, handle auto-resolution.
     *
     * @return list of conflicts (empty if successful)
     */
    private List<MergeResult.MergeConflict> doMerge(MergePlanStep step) throws IOException, GitAPIException, URISyntaxException {
        String planBranch = resolveBranch(step.childPath(), step.childBranch());

        NormalizedMergeResult mergeResult = mergeFromChildRepoWithFallback(
                step.parentPath(),
                step.childPath(),
                planBranch
        );

        log.debug("Merge result sub={}: successful={}", step.submodulePath(), mergeResult.successful());

        if (mergeResult.successful()) {
            return List.of();
        }

        if (!mergeResult.hasConflicts()) {
            throw new RuntimeException("Merge failed without conflicts: " + mergeResult.errorMessage());
        }

        // Extract conflicts.
        List<MergeResult.MergeConflict> conflicts = mergeResult.conflictFiles().stream()
                .map(file -> new MergeResult.MergeConflict(
                        file, "content", "", "", "", step.formattedSubmodulePath()
                ))
                .collect(Collectors.toList());

        // Attempt auto-resolution for submodule pointer conflicts.
        if (tryAutoResolveSubmoduleConflicts(conflicts, step.parentPath())) {
            return List.of();
        }

        return conflicts;
    }

    /**
     * Verify that a merge step produced the expected state change.
     * Logs warnings if verification fails — does not throw.
     */
    private void verifyStepResult(MergePlanStep step, RepoStateSnapshot before, RepoStateSnapshot after) {
        // Verify the branch didn't change unexpectedly during the merge.
        if (!Objects.equals(before.branch(), after.branch())) {
            log.warn("Step sub={}: branch changed unexpectedly from {} to {}",
                    step.submodulePath(), before.branch(), after.branch());
        }
    }

    /**
     * Finalize a successful merge: commit pointers, restore branches, update metadata.
     * Order matters: commit dirty pointers before AND after switching branches to handle
     * both detached HEAD and named branch states.
     */
    private MergeResult finalizeSuccessfulMerge(String childWorktreeId, String parentWorktreeId,
                                                 WorktreeContext childContext, WorktreeContext parentContext) {
        Path parentPath = parentContext.worktreePath();
        Path childPath = childContext.worktreePath();

        // Commit pointers while still in current HEAD state (may be detached).
        commitDirtySubmodulePointers(parentPath);
        // Restore named branches.
        switchToBranches(parentPath, childPath, childContext.derivedBranch());
        // Commit pointers again on the named branch (catches any drift from branch switch).
        commitDirtySubmodulePointers(parentPath);

        String mergeCommit = getCurrentCommitHashInternal(parentPath);
        updateWorktreeLastCommit(parentContext, mergeCommit);
        if (childContext instanceof SubmoduleWorktreeContext submoduleChild) {
            propagateSubmodulePointerUpdates(submoduleChild, parentContext);
        }

        return new MergeResult(
                UUID.randomUUID().toString(),
                childWorktreeId,
                parentWorktreeId,
                normalizePath(childContext.worktreePath()),
                normalizePath(parentContext.worktreePath()),
                true,
                mergeCommit,
                List.of(),
                List.of(),
                "Merge successful",
                Instant.now()
        );
    }

    private MergeResult buildConflictResult(String childWorktreeId, String parentWorktreeId,
                                             List<MergeResult.MergeConflict> conflicts) {
        return new MergeResult(
                UUID.randomUUID().toString(),
                childWorktreeId,
                parentWorktreeId,
                resolveWorktreePath(childWorktreeId),
                resolveWorktreePath(parentWorktreeId),
                false,
                null,
                conflicts,
                List.of(),
                conflicts.stream().anyMatch(c -> c.submodulePath() != null)
                        ? "Merge conflicts detected in submodule"
                        : "Merge conflicts detected",
                Instant.now()
        );
    }

    private void switchToBranches(Path parentPath, Path childPath, String childBranch) {
        ActualBranches result = getActualBranches(parentPath, childPath, childBranch);
        ensureBranchMatchesContext(parentPath, result.parentBranch());
        ensureBranchMatchesContext(childPath, result.childBranchIn());
    }

    private @NonNull ActualBranches getActualBranches(Path parentPath, Path childPath, String childBranch) {
        var parentBranch = worktreeRepository.findByPath(parentPath)
                .map(wc -> wc.derivedBranch())
                .orElse(childBranch);
        var childBranchIn = worktreeRepository.findByPath(childPath)
                .map(wc -> wc.derivedBranch())
                .orElse(childBranch);
        ActualBranches result = new ActualBranches(parentBranch, childBranchIn);
        return result;
    }

    private record ActualBranches(String parentBranch, String childBranchIn) {
    }

    private String resolveBranch(Path path, String fallback) throws IOException {
        return worktreeRepository.findByPath(path)
                .map(WorktreeContext::derivedBranch)
                .orElse(fallback);
    }

    private String resolveWorktreePath(String worktreeId) {
        return worktreeRepository.findById(worktreeId)
                .map(WorktreeContext::worktreePath)
                .map(this::normalizePath)
                .orElse(null);
    }

    private void ensureBranchMatchesContext(Path path, String s) {
        if (path == null || s == null || s.isBlank()) {
            return;
        }
        try (var git = openGit(path)) {
            String current = git.getRepository().getBranch();
            if (!Objects.equals(current, s)) {
                var co = git.checkout().setName(s).call();
            }
        } catch (Exception e) {
            log.error("Failed to ensure matches context.", e);
        }
    }

    private void updateWorktreeLastCommit(WorktreeContext context, String commitHash) {
        if (context instanceof MainWorktreeContext main) {
            worktreeRepository.save(main.withLastCommit(commitHash));
        } else if (context instanceof SubmoduleWorktreeContext submodule) {
            worktreeRepository.save(submodule.withLastCommit(commitHash));
        }
    }

    private void commitDirtySubmodulePointers(Path parentPath) {
        if (parentPath == null) {
            return;
        }
        List<String> submodulePaths = new ArrayList<>();
        try {
            for (String name : getSubmoduleNames(parentPath)) {
                Path path = getSubmodulePath(parentPath, name);
                if (path != null) {
                    submodulePaths.add(parentPath.relativize(path).toString());
                }
            }
        } catch (Exception e) {
            return;
        }

        if (submodulePaths.isEmpty()) {
            return;
        }

        try (var git = openGit(parentPath)) {
            // Always stage all submodule paths unconditionally. JGit's status detection
            // for submodules can miss modifications (e.g., after merge conflict resolution),
            // so it's safer to always add and let git determine if anything actually changed.
            for (String path : submodulePaths) {
                git.add().addFilepattern(path).call();
            }
            try {
                git.commit().setMessage("Update submodule pointer(s)").call();
            } catch (EmptyCommitException ignored) {
                // No pointer changes staged.
            }
        } catch (Exception e) {
            // best-effort; leave as-is
        }
    }

    private boolean tryAutoResolveSubmoduleConflicts(List<MergeResult.MergeConflict> conflicts, Path parentPath) {
        if (parentPath == null || conflicts == null || conflicts.isEmpty()) {
            return false;
        }
        Set<String> submodulePaths = new HashSet<>();
        try {
            for (String name : getSubmoduleNames(parentPath)) {
                Path path = getSubmodulePath(parentPath, name);
                if (path != null) {
                    submodulePaths.add(parentPath.relativize(path).toString());
                }
            }
        } catch (Exception e) {
            return false;
        }

        List<String> conflictPaths = conflicts.stream()
                .map(MergeResult.MergeConflict::filePath)
                .filter(Objects::nonNull)
                .toList();

        if (conflictPaths.isEmpty() || !submodulePaths.containsAll(conflictPaths)) {
            return false;
        }

        try (var git = openGit(parentPath)) {
            // For each conflicting submodule, resolve the conflict to the submodule's
            // actual current HEAD commit. This ensures the pointer matches the working tree
            // even after intermediate merge steps have produced new commits.
            for (String conflictPath : conflictPaths) {
                git.add().addFilepattern(conflictPath).call();
            }
            git.commit().setMessage("Resolve submodule pointer conflicts").call();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void propagateSubmodulePointerUpdates(SubmoduleWorktreeContext childSubmodule,
                                                  WorktreeContext initialParent) {
        if (childSubmodule == null || initialParent == null) {
            return;
        }

        updateSubmodulePointerIfChanged(initialParent, childSubmodule.submoduleName());

        // Walk up the filesystem from the child's path, committing dirty submodule
        // pointers at each ancestor git repo. This handles nested submodules correctly
        // even when the WorktreeRepository parent chain is flat.
        Path childPath = childSubmodule.worktreePath();
        if (childPath == null) {
            return;
        }

        Path current = childPath.toAbsolutePath().normalize();
        Set<Path> committed = new HashSet<>();
        Path ancestor = current.getParent();
        while (ancestor != null && ancestor.getNameCount() > 0) {
            if (Files.exists(ancestor.resolve(".git")) && Files.exists(ancestor.resolve(".gitmodules"))) {
                if (committed.add(ancestor)) {
                    commitDirtySubmodulePointers(ancestor);
                }
            }
            ancestor = ancestor.getParent();
        }
    }

    private boolean updateSubmodulePointerIfChanged(WorktreeContext parentContext, String submodulePath) {
        if (parentContext == null || submodulePath == null || submodulePath.isBlank()) {
            return false;
        }
        try (var git = openGit(parentContext.worktreePath())) {
            git.add().addFilepattern(submodulePath).call();
            git.commit().setMessage("Update " + submodulePath + " pointer").call();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public MainWorktreeContext branchWorktree(String sourceWorktreeId, String newBranchName, String nodeId) {
        Optional<MainWorktreeContext> source = getMainWorktree(sourceWorktreeId);
        if (source.isEmpty()) {
            throw new RuntimeException("Source worktree not found: " + sourceWorktreeId);
        }

        try {
            MainWorktreeContext sourceContext = source.get();
            String worktreeId = UUID.randomUUID().toString();
            Path newWorktreePath = Paths.get(baseWorktreesPath, worktreeId);
            String parentBranch = resolveBranchForBranching(sourceContext);

            validateCloneSource(sourceContext.worktreePath().toString(), parentBranch, nodeId);

            // Clone from the source worktree itself so derived branches created in the sandbox
            // are available as branch start points for child worktrees.
            cloneRepository(sourceContext.worktreePath().toString(), newWorktreePath, parentBranch);
            checkoutNewBranch(newWorktreePath, newBranchName, parentBranch);


            String commitHash = getCurrentCommitHashInternal(newWorktreePath);

            MainWorktreeContext context = new MainWorktreeContext(
                    worktreeId,
                    newWorktreePath,
                    parentBranch,
                    newBranchName,
                    WorktreeContext.WorktreeStatus.ACTIVE,
                    sourceWorktreeId,
                    nodeId,
                    Instant.now(),
                    commitHash,
                    sourceContext.repositoryUrl(),
                    sourceContext.hasSubmodules(),
                    new ArrayList<>(),
                    new HashMap<>()
            );

            List<String> submodulePaths = initializeSubmodule(newWorktreePath, newBranchName);

//            we pass in the baseBranch of the parent here because when we clone the
//            submodules it puts it in a detached head. So we do a checkout -b ...
//            and we assume that it's either on that same commit as the branch or will
//            create a new branch with the name of the commit.
            List<SubmoduleWorktreeContext> submodules = createSubmoduleContexts(
                    submodulePaths,
                    worktreeId,
                    newWorktreePath,
                    nodeId,
                    newBranchName
            );

            for (var s : submodules) {
                worktreeRepository.save(s);
            }

            context.submoduleWorktrees().addAll(submodules);

            worktreeRepository.save(context);
            return context;
        } catch (Exception e) {
            throw new RuntimeException("Failed to branch worktree: " + e.getMessage(), e);
        }
    }

    @Override
    public SubmoduleWorktreeContext branchSubmoduleWorktree(String sourceWorktreeId, String newBranchName, String nodeId) {
        Optional<SubmoduleWorktreeContext> source = getSubmoduleWorktree(sourceWorktreeId);
        if (source.isEmpty()) {
            throw new RuntimeException("Source submodule worktree not found: " + sourceWorktreeId);
        }

        try {
            String startPoint = resolvePreferredBranch(source.get().worktreePath(), source.get().derivedBranch());
            checkoutNewBranch(source.get().worktreePath(), newBranchName, startPoint);

            String commitHash = getCurrentCommitHashInternal(source.get().worktreePath());

            SubmoduleWorktreeContext context = new SubmoduleWorktreeContext(
                    UUID.randomUUID().toString(),
                    source.get().worktreePath(),
                    newBranchName,
                    WorktreeContext.WorktreeStatus.ACTIVE,
                    source.get().parentWorktreeId(),
                    nodeId,
                    Instant.now(),
                    commitHash,
                    source.get().submoduleName(),
                    source.get().submoduleUrl(),
                    source.get().mainWorktreeId(),
                    new HashMap<>()
            );

            worktreeRepository.save(context);
            return context;
        } catch (Exception e) {
            throw new RuntimeException("Failed to branch submodule worktree: " + e.getMessage(), e);
        }
    }

    public AgentModels.DiscoveryAgentRequests attachWorktreesToDiscoveryRequests(
            AgentModels.DiscoveryAgentRequests input,
            String nodeId
    ) {
        List<AgentModels.DiscoveryAgentRequest> updated = new ArrayList<>();
        for (AgentModels.DiscoveryAgentRequest request : input.requests()) {
            if (request == null) {
                continue;
            }
            updated.add(request.toBuilder().worktreeContext(input.worktreeContext()).build());
        }
        return input.toBuilder().requests(updated).build();
    }

    public AgentModels.PlanningAgentRequests attachWorktreesToPlanningRequests(
            AgentModels.PlanningAgentRequests input,
            String nodeId
    ) {
        var updated = new ArrayList<AgentModels.PlanningAgentRequest>();
        for (var request : input.requests()) {
            if (request == null) {
                continue;
            }
            updated.add(request.toBuilder().worktreeContext(input.worktreeContext()).build());
        }
        return input.toBuilder().requests(updated).build();
    }

    public AgentModels.TicketAgentRequests attachWorktreesToTicketRequests(
            AgentModels.TicketAgentRequests input,
            String nodeId
    ) {
        var updated = new ArrayList<AgentModels.TicketAgentRequest>();
        for (var request : input.requests()) {
            if (request == null) {
                continue;
            }
            updated.add(request.toBuilder().worktreeContext(input.worktreeContext()).build());
        }
        return input.toBuilder().requests(updated).build();
    }

    @Override
    public void discardWorktree(String worktreeId) {
        Optional<WorktreeContext> wt = worktreeRepository.findById(worktreeId);
        if (wt.isEmpty()) {
            return;
        }

        try {
            // Remove from disk
            Path path = wt.get().worktreePath();
            if (Files.exists(path)) {
                deleteRecursively(path);
            }

            // Update status to DISCARDED
            if (wt.get() instanceof MainWorktreeContext) {
                MainWorktreeContext updated = ((MainWorktreeContext) wt.get()).withStatus(WorktreeContext.WorktreeStatus.DISCARDED);
                worktreeRepository.save(updated);
            } else if (wt.get() instanceof SubmoduleWorktreeContext) {
                SubmoduleWorktreeContext updated = ((SubmoduleWorktreeContext) wt.get()).withStatus(WorktreeContext.WorktreeStatus.DISCARDED);
                worktreeRepository.save(updated);
            }

            // Recursively discard children
            for (WorktreeContext child : worktreeRepository.findByParentId(worktreeId)) {
                discardWorktree(child.worktreeId());
            }
        } catch (IOException e) {
            log.error("Warning: Could not fully discard worktree: " + e.getMessage());
        }
    }

    public String getCurrentCommitHash(String worktreeId) {
        Optional<WorktreeContext> wt = worktreeRepository.findById(worktreeId);
        if (wt.isEmpty()) {
            throw new RuntimeException("Worktree not found: " + worktreeId);
        }
        return getCurrentCommitHashInternal(wt.get().worktreePath());
    }

    public boolean hasUncommittedChanges(String worktreeId, String derivedBranch) {
        Optional<WorktreeContext> wt = worktreeRepository.findById(worktreeId);
        if (wt.isEmpty()) {
            String message = "Worktree not found: " + worktreeId;
            log.error(message);
            eventBus.publish(Events.NodeErrorEvent.err(message, ArtifactKey.createRoot()));
            return true;
        }
        try (var git = openGit(wt.get().worktreePath())) {
            var hasUncommitedChanges = !git.status().setIgnoreSubmodules(SubmoduleWalk.IgnoreSubmoduleMode.ALL).call().isClean();

            if (hasUncommitedChanges) {
                return true;
            }

            if (wt.get() instanceof MainWorktreeContext m) {
                return m.submoduleWorktrees().stream().anyMatch(s -> hasUncommittedChanges(s.worktreeId(), s.derivedBranch()));
            }

            return false;
        } catch (Exception e) {
            if (hasCause(e, MissingObjectException.class)) {
                log.warn("MissingObjectException checking worktree status for {} - attempting to fix detached HEAD submodules", worktreeId);
                try {
                    fixDetachedHeadSubmodules(wt.get().worktreePath(), derivedBranch);
                    try (var git = openGit(wt.get().worktreePath())) {
                        return !git.status()
                                .setIgnoreSubmodules(SubmoduleWalk.IgnoreSubmoduleMode.ALL)
                                .call().isClean();
                    }
                } catch (Exception retryEx) {
                    String message = "Could not determine worktree status for " + worktreeId + " after fixing detached HEAD";
                    log.error(message, retryEx);
                    eventBus.publish(Events.NodeErrorEvent.err(message + ": " + retryEx.getMessage(), ArtifactKey.createRoot()));
                    return true;
                }
            }
            Boolean fallback = isCleanViaCli(wt.get().worktreePath());
            if (fallback != null) {
                return !fallback;
            }
            String message = "Could not determine worktree status for " + worktreeId;
            log.error(message, e);
            eventBus.publish(Events.NodeErrorEvent.err(message + ": " + e.getMessage(), ArtifactKey.createRoot()));
            return true;
        }
    }

    private static boolean hasCause(Throwable t, Class<? extends Throwable> causeType) {
        while (t != null) {
            if (causeType.isInstance(t)) return true;
            t = t.getCause();
        }
        return false;
    }

    private static void doPerformCheckout(Git subGit, String branchName, String key) {
        try {
            subGit.checkout().setName(branchName).setCreateBranch(true).call();
        } catch (Exception checkoutEx) {
            // Branch may already exist, try switching to it
            try {
                subGit.checkout().setName(branchName).call();
            } catch (Exception switchEx) {
                log.warn("Could not fix detached HEAD for submodule {}: {}", key, switchEx.getMessage());
            }
        }
    }

    private void fixDetachedHeadSubmodules(Path worktreePath, String derivedBranch) {
        try (var git = openGit(worktreePath)) {
            Map<String, SubmoduleStatus> submodules = git.submoduleStatus().call();
            for (Map.Entry<String, SubmoduleStatus> entry : submodules.entrySet()) {
                Path submodulePath = worktreePath.resolve(entry.getKey());
                if (!Files.isDirectory(submodulePath.resolve(".git")) && !Files.isRegularFile(submodulePath.resolve(".git"))) {
                    continue;
                }
                try (var subGit = openGit(submodulePath)) {
                    Repository subRepo = subGit.getRepository();
                    if (subRepo.resolve("HEAD") != null && subRepo.getFullBranch().startsWith("refs/heads/")) {
                        continue; // already on a branch
                    }
                    // Detached HEAD - create or checkout a branch from current commit
                    String branchName = derivedBranch;
                    log.info("Fixing detached HEAD in submodule {} by checking out branch {}", entry.getKey(), branchName);
                    doPerformCheckout(subGit, branchName, entry.getKey());
                }
            }
        } catch (Exception e) {
            log.warn("Error fixing detached HEAD submodules in {}: {}", worktreePath, e.getMessage());
        }
    }

    public List<String> changedFiles(String worktreeId) {
        Optional<WorktreeContext> wt = worktreeRepository.findById(worktreeId);
        if (wt.isEmpty()) {
            throw new RuntimeException("Worktree not found: " + worktreeId);
        }
        Path worktreePath = wt.get().worktreePath();
        try (var git = openGit(worktreePath)) {
            Status status = git.status()
                    .setIgnoreSubmodules(SubmoduleWalk.IgnoreSubmoduleMode.ALL)
                    .call();
            Set<String> files = new TreeSet<>();
            files.addAll(status.getAdded());
            files.addAll(status.getChanged());
            files.addAll(status.getModified());
            files.addAll(status.getRemoved());
            files.addAll(status.getMissing());
            files.addAll(status.getUntracked());
            files.addAll(status.getConflicting());
            return new ArrayList<>(files);
        } catch (Exception e) {
            log.warn("JGit changed-files read failed for worktree {} at {}. Falling back to git CLI.",
                    worktreeId, worktreePath, e);
            List<String> fallback = changedFilesViaCli(worktreePath);
            if (fallback != null) {
                return fallback;
            }
            throw new RuntimeException("Could not read changed files for worktree " + worktreeId, e);
        }
    }

    public String commitChanges(String worktreeId, String message) {
        Optional<WorktreeContext> wt = worktreeRepository.findById(worktreeId);
        if (wt.isEmpty()) {
            throw new RuntimeException("Worktree not found: " + worktreeId);
        }

        try {
            Path wtPath = wt.get().worktreePath();
            try (var git = openGit(wtPath)) {
                git.add().addFilepattern(".").call();
                git.commit().setMessage(message).call();
            }
            String commitHash = getCurrentCommitHashInternal(wtPath);

            if (wt.get() instanceof MainWorktreeContext) {
                MainWorktreeContext updated = ((MainWorktreeContext) wt.get()).withLastCommit(commitHash);
                worktreeRepository.save(updated);
            } else if (wt.get() instanceof SubmoduleWorktreeContext) {
                SubmoduleWorktreeContext updated = ((SubmoduleWorktreeContext) wt.get()).withLastCommit(commitHash);
                worktreeRepository.save(updated);
            }

            return commitHash;
        } catch (Exception e) {
            throw new RuntimeException("Failed to commit changes: " + e.getMessage(), e);
        }
    }

    @Override
    public void updateSubmodulePointer(String mainWorktreeId, String submoduleName) {
        Optional<MainWorktreeContext> main = getMainWorktree(mainWorktreeId);
        if (main.isEmpty()) {
            return;
        }

        try {
            Path mainPath = main.get().worktreePath();
            try (var git = openGit(mainPath)) {
                git.add().addFilepattern(submoduleName).call();
                git.commit().setMessage("Update " + submoduleName + " pointer").call();
            }
        } catch (Exception e) {
            log.error("Warning: Could not update submodule pointer: {}", e.getMessage());
        }
    }

    public List<String> detectMergeConflicts(String childWorktreeId, String parentWorktreeId) {
        Optional<MainWorktreeContext> parentWt = getMainWorktree(parentWorktreeId);
        if (parentWt.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            try (var git = openGit(parentWt.get().worktreePath())) {
                Status status = git.status()
                        .setIgnoreSubmodules(SubmoduleWalk.IgnoreSubmoduleMode.ALL)
                        .call();
                return new ArrayList<>(status.getConflicting());
            }
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @Override
    public MergeResult ensureMergeConflictsCaptured(MergeResult result) {
        if (result == null) {
            return null;
        }

        List<String> detected = detectMergeConflicts(
                result.childWorktreeId(),
                result.parentWorktreeId()
        );
        boolean parentContainsChild = parentContainsChildHead(
                result.childWorktreeId(),
                result.parentWorktreeId()
        );
        if (detected.isEmpty() && parentContainsChild) {
            return result;
        }

        List<MergeResult.MergeConflict> mergedConflicts = new ArrayList<>();
        if (result.conflicts() != null) {
            mergedConflicts.addAll(result.conflicts());
        }
        for (String file : detected) {
            boolean alreadyPresent = mergedConflicts.stream()
                    .filter(c -> c != null && c.filePath() != null)
                    .anyMatch(c -> Objects.equals(c.filePath(), file));
            if (!alreadyPresent) {
                mergedConflicts.add(new MergeResult.MergeConflict(
                        file,
                        "detected",
                        "",
                        "",
                        "",
                        null
                ));
            }
        }
        if (!parentContainsChild) {
            String markerPath = result.parentWorktreePath() != null
                    ? result.parentWorktreePath()
                    : "merge-validation";
            boolean alreadyPresent = mergedConflicts.stream()
                    .filter(c -> c != null && c.filePath() != null)
                    .anyMatch(c -> Objects.equals(c.filePath(), markerPath));
            if (!alreadyPresent) {
                mergedConflicts.add(new MergeResult.MergeConflict(
                        markerPath,
                        "missing-commit",
                        "",
                        "",
                        "",
                        null
                ));
            }
        }

        if (mergedConflicts.isEmpty()) {
            return result;
        }

        String message = result.mergeMessage();
        if (result.successful() || message == null || message.isBlank()) {
            message = parentContainsChild
                    ? "Merge conflicts detected (spot check)"
                    : "Merge validation failed: parent missing child commit";
        }

        return new MergeResult(
                result.mergeId(),
                result.childWorktreeId(),
                result.parentWorktreeId(),
                result.childWorktreePath(),
                result.parentWorktreePath(),
                false,
                result.mergeCommitHash(),
                mergedConflicts,
                result.submoduleUpdates(),
                message,
                result.mergedAt()
        );
    }

    @Override
    public MergeResult ensureMergeConflictsCaptured(MergeResult result, MainWorktreeContext mainWorktree) {
        if (result == null || mainWorktree == null) {
            return result;
        }

        String worktreePath = normalizePath(mainWorktree.worktreePath());
        String sourcePath = mainWorktree.repositoryUrl();

        if (worktreePath == null || sourcePath == null) {
            return result;
        }

        boolean sourceContainsWorktreeHead = parentContainsChildHeadByPath(
                worktreePath, sourcePath
        );

        List<MergeResult.MergeConflict> additionalConflicts = new ArrayList<>();

        if (!sourceContainsWorktreeHead) {
            String markerPath = sourcePath;
            boolean alreadyPresent = result.conflicts() != null && result.conflicts().stream()
                    .filter(c -> c != null && c.filePath() != null)
                    .anyMatch(c -> Objects.equals(c.filePath(), markerPath));
            if (!alreadyPresent) {
                additionalConflicts.add(new MergeResult.MergeConflict(
                        markerPath,
                        "missing-commit",
                        "",
                        "",
                        "",
                        null
                ));
            }
        }

        // Recursively discover submodules from the filesystem (not from
        // mainWorktree.submoduleWorktrees() which may have incorrect nesting).
        Path worktreePathObj = mainWorktree.worktreePath();
        Path sourcePathObj = Path.of(sourcePath);
        if (worktreePathObj != null) {
            collectSubmoduleConflictsRecursive(
                    worktreePathObj, sourcePathObj, "",
                    additionalConflicts, result.conflicts()
            );
        }

        if (additionalConflicts.isEmpty()) {
            return result;
        }

        List<MergeResult.MergeConflict> mergedConflicts = new ArrayList<>();
        if (result.conflicts() != null) {
            mergedConflicts.addAll(result.conflicts());
        }
        mergedConflicts.addAll(additionalConflicts);

        String message = sourceContainsWorktreeHead
                ? "Merge validation: submodule commits not fully propagated"
                : "Merge validation failed: source missing worktree commit";

        return new MergeResult(
                result.mergeId(),
                result.childWorktreeId(),
                result.parentWorktreeId(),
                result.childWorktreePath(),
                result.parentWorktreePath(),
                false,
                result.mergeCommitHash(),
                mergedConflicts,
                result.submoduleUpdates(),
                message,
                result.mergedAt()
        );
    }

    private void collectSubmoduleConflictsRecursive(Path worktreePath, Path sourcePath,
                                                      String parentRelPath,
                                                      List<MergeResult.MergeConflict> additionalConflicts,
                                                      List<MergeResult.MergeConflict> existingConflicts) {
        List<String> submoduleNames;
        try {
            submoduleNames = getSubmoduleNames(worktreePath);
        } catch (Exception e) {
            return;
        }

        for (String submoduleName : submoduleNames) {
            Path worktreeSubPath;
            Path sourceSubPath;
            String relPath = parentRelPath == null || parentRelPath.isBlank()
                    ? submoduleName
                    : parentRelPath + "/" + submoduleName;
            try {
                worktreeSubPath = getSubmodulePath(worktreePath, submoduleName);
                sourceSubPath = getSubmodulePath(sourcePath, submoduleName);
            } catch (Exception e) {
                continue;
            }

            String worktreeSubStr = normalizePath(worktreeSubPath);
            String sourceSubStr = normalizePath(sourceSubPath);

            if (worktreeSubStr != null && sourceSubStr != null) {
                boolean subContained = parentContainsChildHeadByPath(worktreeSubStr, sourceSubStr);
                if (!subContained) {
                    boolean alreadyPresent = existingConflicts != null && existingConflicts.stream()
                            .filter(c -> c != null && c.filePath() != null)
                            .anyMatch(c -> Objects.equals(c.filePath(), relPath));
                    boolean alreadyAdded = additionalConflicts.stream()
                            .filter(c -> c != null && c.filePath() != null)
                            .anyMatch(c -> Objects.equals(c.filePath(), relPath));
                    if (!alreadyPresent && !alreadyAdded) {
                        additionalConflicts.add(new MergeResult.MergeConflict(
                                relPath,
                                "missing-commit",
                                "",
                                "",
                                "",
                                formatSubmodulePath(relPath)
                        ));
                    }
                }
            }

            // Recurse into nested submodules
            collectSubmoduleConflictsRecursive(worktreeSubPath, sourceSubPath, relPath,
                    additionalConflicts, existingConflicts);
        }
    }

    @Override
    public MergeDescriptor mergeTrunkToChild(WorktreeSandboxContext trunk, WorktreeSandboxContext child) {
        try {
            MergeResult result = mergeWorktrees(
                    trunk.mainWorktree().worktreeId(),
                    child.mainWorktree().worktreeId()
            );
            result = ensureMergeConflictsCaptured(result);
            return toMergeDescriptor(result, MergeDirection.TRUNK_TO_CHILD);
        } catch (Exception e) {
            log.error("Trunk-to-child merge failed", e);
            String errorMessage = "Trunk-to-child merge failed: " + e.getMessage();
            MergeResult failed = failedMergeResult(
                    trunk != null && trunk.mainWorktree() != null ? trunk.mainWorktree().worktreeId() : "unknown-child",
                    child != null && child.mainWorktree() != null ? child.mainWorktree().worktreeId() : "unknown-parent",
                    trunk != null && trunk.mainWorktree() != null ? trunk.mainWorktree().worktreePath() : null,
                    child != null && child.mainWorktree() != null ? child.mainWorktree().worktreePath() : null,
                    errorMessage
            );
            return toMergeDescriptor(failed, MergeDirection.TRUNK_TO_CHILD)
                    .toBuilder()
                    .errorMessage(errorMessage)
                    .build();
        }
    }

    @Override
    public MergeDescriptor mergeChildToTrunk(WorktreeSandboxContext child, WorktreeSandboxContext trunk) {
        try {
            MergeResult result = mergeWorktrees(
                    child.mainWorktree().worktreeId(),
                    trunk.mainWorktree().worktreeId()
            );
            result = ensureMergeConflictsCaptured(result);

            if (result.successful() && child.submoduleWorktrees() != null) {
                for (SubmoduleWorktreeContext sub : child.submoduleWorktrees()) {
                    try {
                        updateSubmodulePointer(
                                trunk.mainWorktree().worktreeId(),
                                sub.submoduleName()
                        );
                    } catch (Exception e) {
                        log.warn("Failed to update submodule pointer for {}: {}", sub.submoduleName(), e.getMessage());
                    }
                }
            }

            return toMergeDescriptor(result, MergeDirection.CHILD_TO_TRUNK);
        } catch (Exception e) {
            log.error("Child-to-trunk merge failed", e);
            String errorMessage = "Child-to-trunk merge failed: " + e.getMessage();
            MergeResult failed = failedMergeResult(
                    child != null && child.mainWorktree() != null ? child.mainWorktree().worktreeId() : "unknown-child",
                    trunk != null && trunk.mainWorktree() != null ? trunk.mainWorktree().worktreeId() : "unknown-parent",
                    child != null && child.mainWorktree() != null ? child.mainWorktree().worktreePath() : null,
                    trunk != null && trunk.mainWorktree() != null ? trunk.mainWorktree().worktreePath() : null,
                    errorMessage
            );
            return toMergeDescriptor(failed, MergeDirection.CHILD_TO_TRUNK)
                    .toBuilder()
                    .errorMessage(errorMessage)
                    .build();
        }
    }

    @Override
    public MergeDescriptor finalMergeToSourceDescriptor(String mainWorktreeId) {
        try {
            MergeResult result = finalMergeToSource(mainWorktreeId);

            Optional<WorktreeContext> mainWt = worktreeRepository.findById(mainWorktreeId);
            if (mainWt.isPresent() && mainWt.get() instanceof MainWorktreeContext mainContext) {
                result = ensureMergeConflictsCaptured(result, mainContext);
            } else {
                result = ensureMergeConflictsCaptured(result);
            }

            return toMergeDescriptor(result, MergeDirection.WORKTREE_TO_SOURCE);
        } catch (Exception e) {
            log.error("Final merge to source descriptor failed", e);
            String errorMessage = "Final merge to source failed: " + e.getMessage();
            WorktreeContext main = worktreeRepository.findById(mainWorktreeId).orElse(null);
            String sourceWorktreeId = main != null && main.parentWorktreeId() != null && !main.parentWorktreeId().isBlank()
                    ? main.parentWorktreeId()
                    : "source-repo";
            MergeResult failed = failedMergeResult(
                    mainWorktreeId != null && !mainWorktreeId.isBlank() ? mainWorktreeId : "unknown-child",
                    sourceWorktreeId,
                    main != null ? main.worktreePath() : null,
                    null,
                    errorMessage
            );
            return toMergeDescriptor(failed, MergeDirection.WORKTREE_TO_SOURCE)
                    .toBuilder()
                    .errorMessage(errorMessage)
                    .build();
        }
    }

    private MergeResult failedMergeResult(
            String childWorktreeId,
            String parentWorktreeId,
            Path childWorktreePath,
            Path parentWorktreePath,
            String message
    ) {
        return new MergeResult(
                UUID.randomUUID().toString(),
                childWorktreeId,
                parentWorktreeId,
                childWorktreePath != null ? normalizePath(childWorktreePath) : null,
                parentWorktreePath != null ? normalizePath(parentWorktreePath) : null,
                false,
                null,
                List.of(),
                List.of(),
                message,
                Instant.now()
        );
    }

    private MergeDescriptor toMergeDescriptor(MergeResult result, MergeDirection direction) {
        List<String> conflictFiles = result.conflicts() != null
                ? result.conflicts().stream()
                    .map(MergeResult.MergeConflict::filePath)
                    .filter(Objects::nonNull)
                    .toList()
                : List.of();

        // Group conflicts by submodule path to build SubmoduleMergeResults
        Map<String, List<MergeResult.MergeConflict>> bySubmodule = result.conflicts() != null
                ? result.conflicts().stream()
                    .filter(c -> c.submodulePath() != null && !c.submodulePath().isBlank())
                    .collect(Collectors.groupingBy(MergeResult.MergeConflict::submodulePath))
                : Map.of();

        List<SubmoduleMergeResult> submoduleResults = bySubmodule.entrySet().stream()
                .map(entry -> new SubmoduleMergeResult(
                        entry.getKey(),
                        result.childWorktreePath(),
                        result.parentWorktreePath(),
                        result,
                        false
                ))
                .toList();

        return MergeDescriptor.builder()
                .mergeDirection(direction)
                .successful(result.successful())
                .conflictFiles(conflictFiles)
                .submoduleMergeResults(submoduleResults)
                .mainWorktreeMergeResult(result)
                .errorMessage(result.successful() ? null : result.mergeMessage())
                .build();
    }

    public boolean parentContainsChildHead(String childWorktreeId, String parentWorktreeId) {
        String childPath = resolveWorktreePath(childWorktreeId);
        String parentPath = resolveWorktreePath(parentWorktreeId);
        return parentContainsChildHeadByPath(childPath, parentPath);
    }

    public boolean parentContainsChildHeadByPath(String childPath, String parentPath) {
        if (childPath == null || parentPath == null) {
            return false;
        }
        Path child = Path.of(childPath);
        Path parent = Path.of(parentPath);
        if (!Files.exists(child) || !Files.exists(parent)) {
            return false;
        }

        try (var childGit = openGit(child); var parentGit = openGit(parent)) {
            ObjectId childHead = childGit.getRepository().resolve(Constants.HEAD);
            ObjectId parentHead = parentGit.getRepository().resolve(Constants.HEAD);
            if (childHead == null || parentHead == null) {
                return false;
            }

            try (RevWalk walk = new RevWalk(parentGit.getRepository())) {
                RevCommit childCommit = walk.parseCommit(childHead);
                RevCommit parentCommit = walk.parseCommit(parentHead);
                return walk.isMergedInto(childCommit, parentCommit);
            }
        } catch (Exception e) {
            return false;
        }
    }

    // ======== PRIVATE HELPERS ========

    private void validateCloneSource(String repositoryUrl, String expectedBranch, String nodeId) {
        if (repositoryUrl == null || repositoryUrl.isBlank()) {
            String message = "Clone source repository path is required but was blank.";
            emitCloneValidationFailure(nodeId, message, null);
            throw new IllegalStateException(message);
        }

        Path sourcePath;
        try {
            sourcePath = Paths.get(repositoryUrl).toAbsolutePath().normalize();
        } catch (Exception e) {
            String message = "Clone source path '" + repositoryUrl + "' is invalid.";
            emitCloneValidationFailure(nodeId, message, e);
            throw new IllegalStateException(message, e);
        }

        if (!Files.exists(sourcePath)) {
            // Non-local repository URLs are validated by clone itself.
            return;
        }

        try (Repository sourceRepo = RepoUtil.findRepo(sourcePath)) {
            if (expectedBranch == null || expectedBranch.isBlank()) {
                return;
            }

            String fullBranch = sourceRepo.getFullBranch();
            boolean detachedHead = fullBranch == null || !fullBranch.startsWith(Constants.R_HEADS);
            String currentBranch = detachedHead ? null : Repository.shortenRefName(fullBranch);

            if (detachedHead) {
                String message = "Clone source repository '" + sourcePath + "' is in detached HEAD. "
                        + "Expected branch '" + expectedBranch + "' before cloning.";
                emitCloneValidationFailure(nodeId, message, null);
                throw new IllegalStateException(message);
            }

            if (!Objects.equals(currentBranch, expectedBranch)) {
                String message = "Clone source repository '" + sourcePath + "' is on branch '"
                        + currentBranch + "' but expected '" + expectedBranch + "'.";
                emitCloneValidationFailure(nodeId, message, null);
                throw new IllegalStateException(message);
            }
        } catch (IOException e) {
            String message = "Failed validating clone source repository '" + sourcePath + "': " + e.getMessage();
            emitCloneValidationFailure(nodeId, message, e);
            throw new IllegalStateException(message, e);
        }
    }

    private void emitCloneValidationFailure(String nodeId, String message, Exception e) {
        if (e == null) {
            log.error(message);
        } else {
            log.error(message, e);
        }
        if (eventBus != null && nodeId != null && !nodeId.isBlank()) {
            eventBus.publish(Events.NodeErrorEvent.err(message, getKey(nodeId)));
        }
    }

    public static void cloneRepository(String repositoryUrl, Path worktreePath, String baseBranch) throws GitAPIException, IOException {
        try(Repository build = RepoUtil.findRepo(Paths.get(repositoryUrl))) {
            CloneCommand clone = Git.cloneRepository()
                    .setURI(build.getDirectory().toString())
                    .setDirectory(worktreePath.toFile());

            try (
                    Git git = clone.call()
            ) {
                RepoUtil.runGitCommand(worktreePath, List.of("submodule", "update", "--init", "--recursive"));
                String resolvedBaseRef = resolveBranchRef(git.getRepository(), baseBranch);
                if (resolvedBaseRef != null) {
                    String resolvedBaseBranch = branchNameFromRef(resolvedBaseRef);
                    if (!Objects.equals(git.getRepository().getBranch(), resolvedBaseBranch)) {
                        CheckoutCommand checkout = git.checkout().setName(resolvedBaseBranch);
                        if (git.getRepository().findRef("refs/heads/" + resolvedBaseBranch) == null) {
                            checkout.setCreateBranch(true).setStartPoint(resolvedBaseRef);
                        }
                        checkout.call();
                    }
                } else if (baseBranch != null && !baseBranch.isBlank()) {
                    log.warn(
                            "Requested clone base branch '{}' not found for repository '{}'; continuing with current HEAD",
                            baseBranch,
                            repositoryUrl
                    );
                }
            }
        }


        var updating = RepoUtil.updateSubmodulesRecursively(worktreePath);

        updating.doOnError(e -> {
            e.errors().forEach(re -> {
                log.error("Error updating submodule {}.", re);
            });
        });
        updating.doOnEach(e -> {
            log.debug("Updated submodules {}", e);
        });
    }

    private static void checkoutNewBranch(Path repoPath, String newBranchName, String startPoint) throws IOException, GitAPIException {
        try (var git = openGit(repoPath)) {
            if (newBranchName == null || newBranchName.isBlank()) {
                throw new IllegalArgumentException("newBranchName is required");
            }

            Ref existingBranchRef = git.getRepository().findRef("refs/heads/" + newBranchName);
            CheckoutCommand checkout = git.checkout().setName(newBranchName);
            if (existingBranchRef == null) {
                checkout.setCreateBranch(true);
                String resolvedStartRef = resolveBranchRef(git.getRepository(), startPoint);
                if (resolvedStartRef != null) {
                    checkout.setStartPoint(resolvedStartRef);
                } else if (startPoint != null && !startPoint.isBlank()) {
                    log.warn(
                            "Start branch '{}' not found in {}; creating '{}' from current HEAD",
                            startPoint,
                            repoPath,
                            newBranchName
                    );
                }
            }
            checkout.call();
        } catch (Exception e) {
            log.error("Error doing checkout new branch...", e);
        }

        RepoUtil.runGitCommand(repoPath, List.of("submodule", "foreach", "--recursive", "git checkout -b %s || true".formatted(newBranchName)));
        RepoUtil.runGitCommand(repoPath, List.of("submodule", "foreach", "--recursive", "git checkout %s || true".formatted(newBranchName)));
    }

    private String resolveBranchForBranching(MainWorktreeContext sourceContext) {
        if (sourceContext == null || sourceContext.worktreePath() == null) {
            return "main";
        }

        String resolved = resolvePreferredBranch(
                sourceContext.worktreePath(),
                sourceContext.derivedBranch(),
                sourceContext.baseBranch()
        );
        if (resolved != null && !resolved.isBlank()) {
            return resolved;
        }
        if (sourceContext.derivedBranch() != null && !sourceContext.derivedBranch().isBlank()) {
            return sourceContext.derivedBranch();
        }
        if (sourceContext.baseBranch() != null && !sourceContext.baseBranch().isBlank()) {
            return sourceContext.baseBranch();
        }
        return "main";
    }

    private String resolvePreferredBranch(Path repoPath, String... candidates) {
        if (repoPath == null) {
            return null;
        }
        try (var git = openGit(repoPath)) {
            Repository repository = git.getRepository();
            if (candidates != null) {
                for (String candidate : candidates) {
                    String ref = resolveBranchRef(repository, candidate);
                    if (ref != null) {
                        return branchNameFromRef(ref);
                    }
                }
            }

            String current = repository.getBranch();
            if (current != null && !current.isBlank()) {
                return current;
            }
        } catch (Exception e) {
            log.warn("Failed to resolve preferred branch for {}", repoPath, e);
        }
        return null;
    }

    private static String resolveBranchRef(Repository repository, String branchName) throws IOException {
        if (repository == null || branchName == null || branchName.isBlank()) {
            return null;
        }
        String candidate = branchName.trim();
        List<String> refs = List.of(
                "refs/heads/" + candidate,
                "refs/remotes/origin/" + candidate,
                candidate
        );
        for (String refName : refs) {
            Ref ref = repository.findRef(refName);
            if (ref != null) {
                return ref.getName();
            }
        }
        return null;
    }

    private static String branchNameFromRef(String refName) {
        if (refName == null || refName.isBlank()) {
            return refName;
        }
        if (refName.startsWith("refs/heads/")) {
            return refName.substring("refs/heads/".length());
        }
        if (refName.startsWith("refs/remotes/origin/")) {
            return refName.substring("refs/remotes/origin/".length());
        }
        return refName;
    }

    private List<String> initializeSubmodule(Path parentWorktreePath, String derivedBranch) {
        var result = RepoUtil.updateSubmodulesRecursively(parentWorktreePath);
        if (result.isErr()) {
            throw new RuntimeException(result.errorMessage());
        }
        // Fix detached HEAD submodules left by git submodule update --init
        fixDetachedHeadSubmodules(parentWorktreePath, derivedBranch);
        return result.unwrap();
    }

    private record NormalizedMergeResult(boolean successful, List<String> conflictFiles, String errorMessage) {
        boolean hasConflicts() {
            return conflictFiles != null && !conflictFiles.isEmpty();
        }
    }

    private NormalizedMergeResult mergeFromChildRepoJgitFallback(Path parentPath, Path childPath, String childBranch) {
        try {
            var result = mergeFromChildRepo(parentPath, childPath, childBranch);
            List<String> conflicts = result.getConflicts() == null
                    ? List.of()
                    : new ArrayList<>(result.getConflicts().keySet());
            return new NormalizedMergeResult(result.getMergeStatus().isSuccessful(), conflicts, null);
        } catch (Exception e) {
            log.warn("JGit merge failed for parent {} child {}. Falling back to CLI merge.", parentPath, childPath, e);
            return mergeFromChildRepoCli(parentPath, childPath, childBranch, e.getMessage());
        }
    }

    private org.eclipse.jgit.api.MergeResult mergeFromChildRepo(Path parentPath, Path childPath, String childBranch) throws IOException, GitAPIException, URISyntaxException {
        String remoteNamespace = "child-" + shortId(childPath.getFileName().toString());
        String remoteRefBase = "refs/remotes/" + remoteNamespace + "/";
        String headRef = remoteRefBase + "HEAD";

        try (var parentGit = openGit(parentPath);
             var rep = new FileRepositoryBuilder().findGitDir(childPath.toFile()).build()) {

            parentGit.remoteAdd().setName("child")
                    .setUri(new URIish(rep.getDirectory().toString()))
                    .call();

            // Fetch both HEAD (for detached HEAD submodules) and named branches.
            FetchCommand fetch = parentGit.fetch()
                    .setRemote("child")
                    .setRefSpecs(
                            new RefSpec("+HEAD:" + headRef),
                            new RefSpec("+refs/heads/*:" + remoteRefBase + "*")
                    );

            fetch.call();

            parentGit.remoteRemove().setRemoteName("child")
                    .call();

            // Prefer HEAD (always represents the child's actual current state,
            // works for both named branches and detached HEAD submodules).
            // Fall back to the named branch ref if HEAD is unavailable.
            Repository repo = parentGit.getRepository();
            ObjectId mergeHead = repo.resolve(headRef);
            if (mergeHead == null) {
                String branchRef = remoteRefBase + (childBranch == null || childBranch.isBlank()
                        ? "main"
                        : childBranch);
                mergeHead = repo.resolve(branchRef);
            }
            if (mergeHead == null) {
                throw new IllegalStateException("Unable to resolve merge ref for child at " + childPath);
            }

            return parentGit.merge()
                    .include(mergeHead)
                    .setCommit(true)
                    .setFastForward(MergeCommand.FastForwardMode.NO_FF)
                    .call();
        }
    }

    private NormalizedMergeResult mergeFromChildRepoWithFallback(Path parentPath, Path childPath, String childBranch) {
        return mergeFromChildRepoJgitFallback(parentPath, childPath, childBranch);
    }

    private NormalizedMergeResult mergeFromChildRepoCli(
            Path parentPath,
            Path childPath,
            String childBranch,
            String jgitError
    ) {
        String remoteNamespace = "child-" + shortId(childPath.getFileName().toString());
        String remoteRefBase = "refs/remotes/" + remoteNamespace + "/";
        String headRef = remoteRefBase + "HEAD";

        try {
            RepoUtil.runGitCommand(parentPath, List.of("remote", "remove", remoteNamespace));
            var add = RepoUtil.runGitCommand(parentPath, List.of("remote", "add", remoteNamespace, childPath.toString()));
            if (add.isErr()) {
                return new NormalizedMergeResult(false, List.of(), "CLI remote add failed: " + add.errorMessage());
            }

            var fetch = RepoUtil.runGitCommand(parentPath, List.of(
                    "fetch",
                    remoteNamespace,
                    "+HEAD:" + headRef,
                    "+refs/heads/*:" + remoteRefBase + "*"
            ));
            if (fetch.isErr()) {
                return new NormalizedMergeResult(false, List.of(), "CLI fetch failed: " + fetch.errorMessage());
            }

            String mergeRef = headRef;
            var headExists = RepoUtil.runGitCommand(parentPath, List.of("rev-parse", "--verify", "--quiet", headRef));
            if (headExists.isErr() || headExists.r().get() == null || headExists.r().get().isBlank()) {
                mergeRef = remoteRefBase + (childBranch == null || childBranch.isBlank() ? "main" : childBranch);
            }

            var merge = RepoUtil.runGitCommand(parentPath, List.of("merge", "--no-ff", "--no-edit", mergeRef));
            if (merge.isOk()) {
                return new NormalizedMergeResult(true, List.of(), null);
            }

            var conflictFiles = RepoUtil.runGitCommand(parentPath, List.of("diff", "--name-only", "--diff-filter=U"));
            if (conflictFiles.isOk()) {
                List<String> conflicts = conflictFiles.r().get().lines()
                        .map(String::trim)
                        .filter(s -> !s.isBlank())
                        .toList();
                if (!conflicts.isEmpty()) {
                    return new NormalizedMergeResult(false, conflicts, merge.errorMessage());
                }
            }

            return new NormalizedMergeResult(
                    false,
                    List.of(),
                    "CLI merge failed after JGit error [" + jgitError + "]: " + merge.errorMessage()
            );
        } finally {
            RepoUtil.runGitCommand(parentPath, List.of("remote", "remove", remoteNamespace));
        }
    }

    private WorktreeSandboxContext branchSandboxContext(
            WorktreeSandboxContext parentContext,
            String branchName,
            String nodeId
    ) {
        try {
            MainWorktreeContext main = branchWorktree(
                    parentContext.mainWorktree().worktreeId(),
                    branchName,
                    nodeId
            );
            return new WorktreeSandboxContext(main, main.submoduleWorktrees());
        } catch (Exception ex) {
            String parentMainId = parentContext != null && parentContext.mainWorktree() != null
                    ? parentContext.mainWorktree().worktreeId()
                    : "unknown-parent";
            String message = "Failed to branch sandbox worktree context for nodeId=" + nodeId
                    + ", branchName=" + branchName
                    + ", parentMainWorktreeId=" + parentMainId
                    + ". Manual review required before retrying worktree operations.";
            log.error(message, ex);
            if (eventBus != null && nodeId != null && !nodeId.isBlank()) {
                eventBus.publish(Events.NodeErrorEvent.err(message, getKey(nodeId)));
            }
            throw new IllegalStateException(message, ex);
        }
    }

    private List<SubmoduleWorktreeContext> createSubmoduleContexts(
            List<String> submodulePaths,
            String parentWorktreeId,
            Path parentWorktreePath,
            String nodeId,
            String parentBranch
    ) {
        if (submodulePaths == null || submodulePaths.isEmpty()) {
            return List.of();
        }
        List<SubmoduleWorktreeContext> contexts = new ArrayList<>();
        for (String submodulePath : submodulePaths) {
            if (submodulePath == null || submodulePath.isBlank()) {
                continue;
            }
            try {
                SubmoduleWorktreeContext context = createSubmoduleWorktree(
                        submodulePath,
                        submodulePath,
                        parentWorktreeId,
                        parentWorktreePath,
                        nodeId,
                        parentBranch
                );
                contexts.add(context);
            } catch (Exception e) {
                log.warn("Skipping submodule '{}': {}", submodulePath, e.getMessage());
                eventBus.publish(Events.NodeErrorEvent.err(
                        "Failed to initialize submodule '%s': %s".formatted(submodulePath, e.getMessage()),
                        getKey(nodeId)
                ));
            }
        }
        return contexts;
    }

    @Override
    public MergeResult finalMergeToSource(String mainWorktreeId) {
        Optional<WorktreeContext> mainWt = worktreeRepository.findById(mainWorktreeId);
        if (mainWt.isEmpty() || !(mainWt.get() instanceof MainWorktreeContext mainContext)) {
            return new MergeResult(
                    UUID.randomUUID().toString(),
                    mainWorktreeId, "source",
                    null, null,
                    false, null, List.of(), List.of(),
                    "Main worktree not found: " + mainWorktreeId,
                    Instant.now()
            );
        }

        Path worktreePath = mainContext.worktreePath();
        Path sourcePath = Path.of(mainContext.repositoryUrl());
        String derivedBranch = mainContext.derivedBranch();
        String baseBranch = mainContext.baseBranch();

        try {
            // Phase 1: Build merge plan (leaves-first). Child = worktree, Parent = source repo.
            List<MergePlanStep> plan = buildFinalMergePlan(worktreePath, sourcePath, derivedBranch, baseBranch);
            log.debug("Final merge plan has {} steps", plan.size());

            // Phase 2: Execute each step with lifecycle.
            MergeExecutionState state = new MergeExecutionState();
            WorktreeContext sourceContext = createSourceMergeContext(mainContext, sourcePath, baseBranch);
            for (MergePlanStep step : plan) {
                executeStepWithLifecycle(step, state, mainContext, sourceContext);
            }

            // Phase 3: Finalize.
            if (state.hasConflicts()) {
                return buildConflictResult(mainWorktreeId, "source", state.allConflicts);
            }

            // Reconcile each submodule repo directly against the worktree tree, even if the
            // root repo appears up-to-date. Parent commit equality does not guarantee
            // submodule repos (or pointer commits) were propagated in source.
            List<MergeResult.MergeConflict> reconciliationConflicts =
                    reconcileFinalMergeSubmoduleRepos(worktreePath, sourcePath, "");
            if (!reconciliationConflicts.isEmpty()) {
                return buildConflictResult(mainWorktreeId, "source", reconciliationConflicts);
            }

            // Commit submodule pointers, ensure source is on baseBranch.
            commitDirtySubmodulePointers(sourcePath);
            ensureBranchMatchesContext(sourcePath, baseBranch);
            commitDirtySubmodulePointers(sourcePath);

            String mergeCommit = getCurrentCommitHashInternal(sourcePath);
            return new MergeResult(
                    UUID.randomUUID().toString(),
                    mainWorktreeId, "source",
                    normalizePath(worktreePath),
                    normalizePath(sourcePath),
                    true, mergeCommit,
                    List.of(), List.of(),
                    "Final merge to source successful",
                    Instant.now()
            );
        } catch (Exception e) {
            log.error("Final merge to source failed", e);
            return new MergeResult(
                    UUID.randomUUID().toString(),
                    mainWorktreeId, "source",
                    normalizePath(worktreePath),
                    normalizePath(sourcePath),
                    false, null, List.of(), List.of(),
                    "Final merge failed: " + e.getMessage(),
                    Instant.now()
            );
        }
    }

    private List<MergeResult.MergeConflict> reconcileFinalMergeSubmoduleRepos(
            Path worktreeRepoPath,
            Path sourceRepoPath,
            String parentRelPath
    ) {
        List<MergeResult.MergeConflict> conflicts = new ArrayList<>();
        List<String> submoduleNames;
        try {
            submoduleNames = getSubmoduleNames(worktreeRepoPath);
        } catch (Exception e) {
            return conflicts;
        }

        for (String submoduleName : submoduleNames) {
            Path worktreeSubmodulePath;
            Path sourceSubmodulePath;
            String relPath = (parentRelPath == null || parentRelPath.isBlank())
                    ? submoduleName
                    : parentRelPath + "/" + submoduleName;
            try {
                worktreeSubmodulePath = getSubmodulePath(worktreeRepoPath, submoduleName);
                sourceSubmodulePath = getSubmodulePath(sourceRepoPath, submoduleName);
            } catch (Exception e) {
                continue;
            }

            conflicts.addAll(reconcileFinalMergeSubmoduleRepos(worktreeSubmodulePath, sourceSubmodulePath, relPath));

            String childHead = getCurrentCommitHashInternal(worktreeSubmodulePath);
            String parentHead = getCurrentCommitHashInternal(sourceSubmodulePath);
            if (Objects.equals(childHead, parentHead)) {
                continue;
            }

            try {
                String childBranch = resolveBranch(worktreeSubmodulePath, getBranchSafe(worktreeSubmodulePath));
                NormalizedMergeResult mergeResult = mergeFromChildRepoWithFallback(
                        sourceSubmodulePath,
                        worktreeSubmodulePath,
                        childBranch
                );
                if (!mergeResult.successful()) {
                    if (mergeResult.hasConflicts()) {
                        conflicts.addAll(mergeResult.conflictFiles().stream()
                                .map(file -> new MergeResult.MergeConflict(
                                        file,
                                        "content",
                                        "",
                                        "",
                                        "",
                                        formatSubmodulePath(relPath)
                                ))
                                .toList());
                    } else {
                        conflicts.add(new MergeResult.MergeConflict(
                                relPath,
                                "merge-error",
                                "",
                                "",
                                "",
                                formatSubmodulePath(relPath)
                        ));
                    }
                    continue;
                }
                commitDirtySubmodulePointers(sourceRepoPath);
            } catch (Exception e) {
                conflicts.add(new MergeResult.MergeConflict(
                        relPath,
                        "merge-error",
                        "",
                        "",
                        "",
                        formatSubmodulePath(relPath)
                ));
            }
        }

        return conflicts;
    }

    private List<MergeResult.MergeConflict> extractMergeConflicts(
            org.eclipse.jgit.api.MergeResult mergeResult,
            String relPath
    ) {
        Map<String, int[][]> conflictMap = mergeResult.getConflicts();
        if (conflictMap == null || conflictMap.isEmpty()) {
            return List.of(new MergeResult.MergeConflict(
                    relPath,
                    "merge-conflict",
                    "",
                    "",
                    "",
                    formatSubmodulePath(relPath)
            ));
        }
        return conflictMap.keySet().stream()
                .map(file -> new MergeResult.MergeConflict(
                        file,
                        "content",
                        "",
                        "",
                        "",
                        formatSubmodulePath(relPath)
                ))
                .toList();
    }

    private WorktreeContext createSourceMergeContext(
            MainWorktreeContext childContext,
            Path sourcePath,
            String baseBranch
    ) {
        if (childContext == null) {
            throw new IllegalArgumentException("childContext is required to build source merge context");
        }
        String sourceId = "source:" + childContext.worktreeId();
        String sourceCommit = null;
        try {
            sourceCommit = getCurrentCommitHashInternal(sourcePath);
        } catch (Exception ignored) {
            // Best-effort only for event enrichment.
        }
        return new MainWorktreeContext(
                sourceId,
                sourcePath,
                baseBranch,
                baseBranch,
                WorktreeContext.WorktreeStatus.ACTIVE,
                null,
                childContext.associatedNodeId(),
                Instant.now(),
                sourceCommit,
                sourcePath != null ? sourcePath.toString() : sourceId,
                false,
                List.of(),
                new HashMap<>()
        );
    }

    /**
     * Build a merge plan for merging worktree derived branches back into the source repo's
     * original branches. Ordered leaves-first, root-last.
     */
    private List<MergePlanStep> buildFinalMergePlan(Path worktreePath, Path sourcePath,
                                                     String derivedBranch, String baseBranch) throws IOException {
        List<MergePlanStep> plan = new ArrayList<>();

        // Ensure source repo submodules are initialized.
        try {
            initializeSubmodule(sourcePath, derivedBranch);
        } catch (Exception e) {
            log.debug("Source submodule init skipped: {}", e.getMessage());
        }

        // Collect submodule steps (leaves first).
        collectFinalMergeSubmoduleSteps(worktreePath, sourcePath, sourcePath, "", plan);

        // Add the root merge step last.
        String worktreeCurrentBranch;
        try (var g = openGit(worktreePath)) {
            worktreeCurrentBranch = g.getRepository().getBranch();
        }

        // Ensure source is on baseBranch before merging.
        ensureBranchMatchesContext(sourcePath, baseBranch);

        plan.add(new MergePlanStep(
                worktreePath,       // child
                sourcePath,         // parent
                null,               // root has no submodule path
                null,               // root has no formatted submodule path
                worktreeCurrentBranch,  // child branch (derivedBranch)
                baseBranch,             // parent branch (baseBranch in source)
                null                    // root has no parent-of-parent
        ));

        return plan;
    }

    /**
     * Recursively collect merge steps for submodules when merging back to source.
     * Reads the parent (target) branch from the source repo's submodule git state.
     */
    private void collectFinalMergeSubmoduleSteps(Path worktreePath, Path sourcePath,
                                                  Path sourceParentOfParent, String parentRelPath,
                                                  List<MergePlanStep> plan) throws IOException {
        List<String> submoduleNames;
        try {
            submoduleNames = getSubmoduleNames(worktreePath);
        } catch (Exception e) {
            return;
        }

        for (String submoduleName : submoduleNames) {
            Path worktreeSubPath;
            Path sourceSubPath;
            String relPath = parentRelPath == null || parentRelPath.isBlank()
                    ? submoduleName
                    : parentRelPath + "/" + submoduleName;
            try {
                worktreeSubPath = getSubmodulePath(worktreePath, submoduleName);
                sourceSubPath = getSubmodulePath(sourcePath, submoduleName);
            } catch (Exception e) {
                continue;
            }

            // Recurse into nested submodules first (leaves before parents).
            collectFinalMergeSubmoduleSteps(worktreeSubPath, sourceSubPath, sourcePath, relPath, plan);

            // Child branch: whatever the worktree's submodule is on (the derived branch).
            String childBranch;
            try (var g = openGit(worktreeSubPath)) {
                childBranch = g.getRepository().getBranch();
            }

            // Parent branch: whatever the source repo's submodule is on (the original branch).
            String parentBranch;
            try (var g = openGit(sourceSubPath)) {
                parentBranch = g.getRepository().getBranch();
            }

            plan.add(new MergePlanStep(
                    worktreeSubPath,
                    sourceSubPath,
                    relPath,
                    formatSubmodulePath(relPath),
                    childBranch,
                    parentBranch,
                    sourcePath
            ));
        }
    }

    private static String shortId(String id) {
        if (id == null || id.isBlank()) {
            return "unknown";
        }
        String shortened = id.length() <= 8 ? id : id.substring(0, 8);
        String sanitized = shortened.replaceAll("[^A-Za-z0-9._-]", "-");
        if (sanitized.isBlank()) {
            return "unknown";
        }
        return sanitized;
    }

    /**
     * Build a comprehensive merge plan ordered leaves-first, root-last.
     * Each step knows its parent-of-parent path so that after a successful merge,
     * dirty submodule pointers can be committed in the correct containing repo.
     */
    public List<MergePlanStep> buildMergePlan(Path childPath, Path parentPath, String rootChildBranch) throws IOException {
        List<MergePlanStep> plan = new ArrayList<>();
        collectSubmoduleSteps(childPath, parentPath, parentPath, "", plan);

        // Add the root module as the final step (no submodulePath, no parent-of-parent).
        String childBranch;
        try (var g = openGit(childPath)) {
            childBranch = g.getRepository().getBranch();
        }
        String parentBranch;
        try (var g = openGit(parentPath)) {
            parentBranch = g.getRepository().getBranch();
        }
        plan.add(new MergePlanStep(
                childPath,
                parentPath,
                null,  // root has no submodule path
                null,  // root has no formatted submodule path
                childBranch,
                parentBranch,
                null   // root has no parent-of-parent
        ));

        return plan;
    }

    /**
     * Recursively collect merge steps for submodules, depth-first (leaves added before parents).
     */
    private void collectSubmoduleSteps(Path childPath, Path parentPath,
                                       Path parentOfParentPath, String parentRelPath,
                                       List<MergePlanStep> plan) throws IOException {
        List<String> submoduleNames;
        try {
            submoduleNames = getSubmoduleNames(childPath);
        } catch (Exception e) {
            return;
        }

        for (String submoduleName : submoduleNames) {
            Path childSubPath;
            Path parentSubPath;
            String relPath = parentRelPath == null || parentRelPath.isBlank()
                    ? submoduleName
                    : parentRelPath + "/" + submoduleName;
            try {
                childSubPath = getSubmodulePath(childPath, submoduleName);
                parentSubPath = getSubmodulePath(parentPath, submoduleName);
            } catch (Exception e) {
                continue;
            }

            // Recurse into nested submodules first (leaves before parents).
            collectSubmoduleSteps(childSubPath, parentSubPath, parentPath, relPath, plan);

            String childBranch;
            try (var g = openGit(childSubPath)) {
                childBranch = g.getRepository().getBranch();
            } catch (IOException e) {
                log.info("Skipping submodule {}: {}.", childPath, e.getMessage());
                continue;
            }
            String parentBranch;
            try (var g = openGit(parentSubPath)) {
                parentBranch = g.getRepository().getBranch();
            }
            plan.add(new MergePlanStep(
                    childSubPath,
                    parentSubPath,
                    relPath,
                    formatSubmodulePath(relPath),
                    childBranch,
                    parentBranch,
                    parentPath
            ));
        }
    }

    /**
     * A single step in the merge plan. Includes the parent-of-parent path so that
     * after a successful submodule merge, we commit dirty pointers in the containing repo.
     */
    public record MergePlanStep(
            Path childPath,
            Path parentPath,
            String submodulePath,
            String formattedSubmodulePath,
            String childBranch,
            String parentBranch,
            Path parentOfParentPath
    ) {}

    private String formatSubmodulePath(String path) {
        if (path == null) {
            return null;
        }
        return path.replace("/", "_");
    }

    private boolean hasSubmodulesInternal(Path repositoryPath) {
        return Files.exists(repositoryPath.resolve(".gitmodules"));
    }

    private void emitMergeStepSuccess(
            MergePlanStep step,
            WorktreeContext rootChildContext,
            WorktreeContext rootParentContext
    ) {
        WorktreeContext child = worktreeRepository.findByPath(step.childPath()).orElse(rootChildContext);
        WorktreeContext parent = worktreeRepository.findByPath(step.parentPath()).orElse(rootParentContext);
        MergeResult result = new MergeResult(
                UUID.randomUUID().toString(),
                resolveWorktreeId(step.childPath(), child),
                resolveWorktreeId(step.parentPath(), parent),
                normalizePath(step.childPath()),
                normalizePath(step.parentPath()),
                true,
                getCurrentCommitHashInternal(step.parentPath()),
                List.of(),
                List.of(),
                "Merge step successful",
                Instant.now()
        );
        emitMergeLifecycleEvent(result, child, parent, step.submodulePath() != null ? "submodule" : "main");
    }

    private void emitMergeStepConflict(
            MergePlanStep step,
            WorktreeContext rootChildContext,
            WorktreeContext rootParentContext,
            List<MergeResult.MergeConflict> conflicts
    ) {
        WorktreeContext child = worktreeRepository.findByPath(step.childPath()).orElse(rootChildContext);
        WorktreeContext parent = worktreeRepository.findByPath(step.parentPath()).orElse(rootParentContext);
        MergeResult conflictResult = new MergeResult(
                UUID.randomUUID().toString(),
                resolveWorktreeId(step.childPath(), child),
                resolveWorktreeId(step.parentPath(), parent),
                normalizePath(step.childPath()),
                normalizePath(step.parentPath()),
                false,
                null,
                conflicts != null ? conflicts : List.of(),
                List.of(),
                "Merge step conflict",
                Instant.now()
        );
        emitMergeFailure(conflictResult, child, parent, "Merge step failed for " + Optional.ofNullable(step.submodulePath()).orElse("root"));
    }

    private void emitMergeFailure(
            MergeResult result,
            WorktreeContext childContext,
            WorktreeContext parentContext,
            String reason
    ) {
        emitMergeLifecycleEvent(result, childContext, parentContext, inferWorktreeType(childContext));
        String nodeId = resolveMergeNodeId(childContext, parentContext);
        if (eventBus == null || nodeId == null || nodeId.isBlank()) {
            return;
        }
        eventBus.publish(Events.NodeErrorEvent.err(
                "Worktree merge failure: " + reason + " | child=" + result.childWorktreeId()
                        + " parent=" + result.parentWorktreeId()
                        + " conflicts=" + (result.conflicts() != null ? result.conflicts().size() : 0),
                getKey(nodeId)
        ));
    }

    private static ArtifactKey getKey(String nodeId) {
        try {
            return new ArtifactKey(nodeId);
        } catch (IllegalArgumentException e) {
            log.error("Error - could not create artifact key for {} when trying to push merge failure event.", nodeId);
            return ArtifactKey.createRoot();
        }
    }

    private void emitMergeLifecycleEvent(
            MergeResult result,
            WorktreeContext childContext,
            WorktreeContext parentContext,
            String worktreeType
    ) {
        if (eventBus == null || result == null) {
            return;
        }
        String nodeId = resolveMergeNodeId(childContext, parentContext);
        if (nodeId == null || nodeId.isBlank()) {
            return;
        }
        List<String> conflictFiles = Optional.ofNullable(result.conflicts()).orElse(List.of())
                .stream()
                .map(conflict -> {
                    String submodule = conflict.submodulePath();
                    String file = conflict.filePath();
                    if (submodule == null || submodule.isBlank()) {
                        return file;
                    }
                    return submodule + "/" + file;
                })
                .toList();
        eventBus.publish(new Events.WorktreeMergedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                result.childWorktreeId(),
                result.parentWorktreeId(),
                result.mergeCommitHash(),
                !result.successful(),
                conflictFiles,
                worktreeType != null ? worktreeType : "main",
                nodeId
        ));
    }

    private String resolveWorktreeId(Path path, WorktreeContext context) {
        if (context != null && context.worktreeId() != null && !context.worktreeId().isBlank()) {
            return context.worktreeId();
        }
        return normalizePath(path);
    }

    private String resolveMergeNodeId(WorktreeContext childContext, WorktreeContext parentContext) {
        if (parentContext != null && parentContext.associatedNodeId() != null && !parentContext.associatedNodeId().isBlank()) {
            return parentContext.associatedNodeId();
        }
        if (childContext != null && childContext.associatedNodeId() != null && !childContext.associatedNodeId().isBlank()) {
            return childContext.associatedNodeId();
        }
        return null;
    }

    private String inferWorktreeType(WorktreeContext context) {
        if (context instanceof SubmoduleWorktreeContext) {
            return "submodule";
        }
        return "main";
    }

    private String getCurrentCommitHashInternal(Path worktreePath) {
        try (var git = openGit(worktreePath)) {
            Repository repo = git.getRepository();
            ObjectId head = repo.resolve(Constants.HEAD);
            return head != null ? head.name() : "unknown";
        } catch (Exception e) {
            emitNodeErrorInternal("Failed to resolve HEAD for " + normalizePath(worktreePath) + ": " + e.getMessage());
            return "unknown";
        }
    }

    private String getBranchSafe(Path path) {
        try (var g = openGit(path)) {
            return g.getRepository().getBranch();
        } catch (Exception e) {
            emitNodeErrorInternal("Failed to resolve branch for " + normalizePath(path) + ": " + e.getMessage());
            return "error:" + e.getMessage();
        }
    }

    private String normalizePath(Path path) {
        if (path == null) {
            return null;
        }
        return path.toAbsolutePath().normalize().toString();
    }

    private boolean isCleanIgnoringSubmodules(Path repoPath) {
        try (var git = openGit(repoPath)) {
            return git.status()
                    .setIgnoreSubmodules(SubmoduleWalk.IgnoreSubmoduleMode.ALL)
                    .call()
                    .isClean();
        } catch (Exception e) {
            log.warn("JGit status check failed for {}. Falling back to git CLI.", repoPath, e);
            Boolean fallback = isCleanViaCli(repoPath);
            return fallback != null && fallback;
        }
    }

    private Boolean isCleanViaCli(Path repoPath) {
        var result = RepoUtil.runGitCommand(
                repoPath,
                List.of("-c", "core.quotepath=false", "status", "--porcelain", "--ignore-submodules=all")
        );
        if (result.isErr()) {
            log.warn("git status CLI fallback failed for {}: {}", repoPath, result.e().toString());
            emitNodeErrorInternal("git status CLI fallback failed for " + normalizePath(repoPath) + ": " + result.e().toString());
            return null;
        }
        String output = result.r().get();
        if (output == null) {
            return true;
        }
        return output.lines().map(String::trim).filter(s -> !s.isEmpty()).findAny().isEmpty();
    }

    private List<String> changedFilesViaCli(Path repoPath) {
        var result = RepoUtil.runGitCommand(
                repoPath,
                List.of("-c", "core.quotepath=false", "status", "--porcelain", "--ignore-submodules=all")
        );
        if (result.isErr()) {
            log.warn("git status CLI fallback for changed files failed for {}: {}", repoPath, result.e().toString());
            emitNodeErrorInternal("git status CLI changed-files fallback failed for " + normalizePath(repoPath) + ": " + result.e().toString());
            return null;
        }

        Set<String> files = new TreeSet<>();
        String output = result.r().get();
        if (output == null || output.isBlank()) {
            return new ArrayList<>();
        }

        output.lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .forEach(line -> {
                    if (line.startsWith("?? ")) {
                        files.add(line.substring(3).trim());
                        return;
                    }
                    if (line.length() < 4) {
                        return;
                    }
                    String pathPart = line.substring(3).trim();
                    int renameSep = pathPart.indexOf(" -> ");
                    if (renameSep >= 0) {
                        pathPart = pathPart.substring(renameSep + 4).trim();
                    }
                    if (pathPart.startsWith("\"") && pathPart.endsWith("\"") && pathPart.length() > 1) {
                        pathPart = pathPart.substring(1, pathPart.length() - 1);
                    }
                    if (!pathPart.isBlank()) {
                        files.add(pathPart);
                    }
                });
        return new ArrayList<>(files);
    }

    private static Git openGit(Path repoPath) throws IOException {
        return RepoUtil.initGitOrThrow(repoPath);
    }

    private void emitNodeErrorInternal(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        log.error(message);
        if (eventBus == null) {
            return;
        }
        eventBus.publish(Events.NodeErrorEvent.err(message, ArtifactKey.createRoot()));
    }

    private void deleteRecursively(Path path) throws IOException {
//        if (Files.isDirectory(path)) {
//            Files.list(path).forEach(child -> {
//                try {
//                    deleteRecursively(child);
//                } catch (IOException e) {
//                    System.err.println("Warning: Could not delete " + child + ": " + e.getMessage());
//                }
//            });
//        }
//        Files.delete(path);
    }
}
