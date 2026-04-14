package com.hayden.utilitymodule.git;


import com.google.common.collect.Sets;
import com.hayden.utilitymodule.io.ArchiveUtils;
import com.hayden.utilitymodule.io.FileUtils;
import com.hayden.utilitymodule.result.ClosableResult;
import com.hayden.utilitymodule.result.OneResult;
import com.hayden.utilitymodule.result.Result;
import com.hayden.utilitymodule.result.agg.AggregateError;
import com.hayden.utilitymodule.result.error.SingleError;
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.eclipse.jgit.api.DiffCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ResetCommand;
import org.eclipse.jgit.api.errors.*;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.*;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.submodule.SubmoduleWalk;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.NotTreeFilter;
import org.eclipse.jgit.treewalk.filter.OrTreeFilter;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.treewalk.filter.TreeFilter;
import org.eclipse.jgit.util.FS;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;


public interface RepoUtil {

    Logger log = LoggerFactory.getLogger(RepoUtil.class);

    static CanonicalTreeParser getForRef(String pattern, Repository repository) throws IOException {
        try (RevWalk revWalk = new RevWalk(repository)) {
            ObjectId head = repository.resolve(pattern);
            RevCommit headCommit = revWalk.parseCommit(head);
            CanonicalTreeParser treeParser = new CanonicalTreeParser();
            treeParser.reset(repository.newObjectReader(), headCommit.getTree());
            return treeParser;
        }
    }

    static Set<Path> changedPaths(Git g, String oldBranch, String newBranch) throws IOException, GitAPIException {
        var repo = g.getRepository();
        ObjectId oldHead = repo.resolve("refs/heads/%s^{tree}".formatted(oldBranch));
        ObjectId newHead = repo.resolve("refs/heads/%s^{tree}".formatted(newBranch));

        CanonicalTreeParser oldTreeIter = new CanonicalTreeParser();
        try (ObjectReader reader = repo.newObjectReader()) {
            oldTreeIter.reset(reader, oldHead);
        }

        CanonicalTreeParser newTreeIter = new CanonicalTreeParser();
        try (ObjectReader reader = repo.newObjectReader()) {
            newTreeIter.reset(reader, newHead);
        }

        List<DiffEntry> diffEntries = g.diff()
                .setOldTree(oldTreeIter)
                .setNewTree(newTreeIter)
                .setShowNameAndStatusOnly(true)
                .call();

        return diffEntries
                .stream()
                .flatMap(de -> Stream.of(de.getOldPath(), de.getNewPath()))
                .filter(StringUtils::isNotBlank)
                .map(Paths::get)
                .collect(Collectors.toSet());
    }

    static Optional<RepoUtilError> doReset(Git git) throws GitAPIException {
        try {
            log.debug("Doing reset");
            ObjectId head = git.getRepository().resolve("HEAD^1");
            git.reset().setMode(ResetCommand.ResetType.SOFT).setRef(head.name()).call();
            return Optional.empty();
        } catch (IOException e) {
            return Optional.of(new RepoUtilError(Arrays.toString(e.getStackTrace())));
        }
    }

    static void doTempCommit(Git holder) throws GitAPIException {
        holder.add().addFilepattern(".").call();
        Collection<String> uncommitted;
        try {
            var status = holder.status()
                    .setIgnoreSubmodules(SubmoduleWalk.IgnoreSubmoduleMode.ALL)
                    .call();
            uncommitted = status.getUncommittedChanges();
        } catch (Exception e) {
            log.warn("JGit status failed during temp commit. Falling back to git CLI.", e);
            uncommitted = parseChangedFilesFromStatusCli(holder.getRepository().getDirectory().toPath());
        }
        var s = uncommitted;
        var toDelete = uncommitted
                .stream()
                .filter(Predicate.not(path -> holder.getRepository().getDirectory().getParentFile().toPath().resolve(path).toFile().exists()))
                .toList();

        for (var d : toDelete) {
            holder.rm().addFilepattern(d).call();
        }

        holder.add().addFilepattern(".").call();

        var committed = holder.commit().setMessage("temp").call();
        log.debug("Temp commit: {}, {}", committed, s);
    }

    private static List<String> parseChangedFilesFromStatusCli(Path repoPath) {
        var status = runGitCommand(
                repoPath,
                List.of("-c", "core.quotepath=false", "status", "--porcelain", "--ignore-submodules=all")
        );
        if (status.isErr()) {
            log.warn("CLI fallback status failed in temp commit: {}", status.errorMessage());
            return List.of();
        }

        String output = status.r().get();
        if (output == null || output.isBlank()) {
            return List.of();
        }

        return output.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .map(line -> line.length() > 3 ? line.substring(3).trim() : line)
                .map(path -> {
                    int renameSep = path.indexOf(" -> ");
                    return renameSep >= 0 ? path.substring(renameSep + 4).trim() : path;
                })
                .map(path -> path.startsWith("\"") && path.endsWith("\"") && path.length() > 1
                        ? path.substring(1, path.length() - 1)
                        : path)
                .filter(path -> !path.isBlank())
                .toList();
    }

    static <T> @NotNull OneResult<T, RepoUtilError> doInsideCommitStaged(Git git, Supplier<T> toDo) {
        try {
            git.add().addFilepattern(".").call();
            git.commit().setMessage("temp").call();
            var retrieved = toDo.get();
            return Result.ok(retrieved);
        } catch (GitAPIException e) {
            return Result.err(new RepoUtilError(e));
        } finally {
            try {
                git.reset().setMode(ResetCommand.ResetType.SOFT).setRef("HEAD~1").call();
            } catch (GitAPIException e) {
                log.error("Error applying stash or reset .", e);
            }
        }
    }

    static <T> @NotNull OneResult<T, RepoUtilError> doInsideStash(Git git, Supplier<T> toDo) {
        return doInsideReset(git, "HEAD", toDo);
    }

    static <T> @NotNull OneResult<T, RepoUtilError> doInsideReset(Git git, String resetTo, Supplier<T> toDo) {
        try {
            git.add().addFilepattern(".").call();
            // save staged changes in case have any
            var created = git.stashCreate().call();
            ObjectId head = git.getRepository().resolve("HEAD");
            return Result.<ObjectId, RepoUtilError>fromOpt(Optional.ofNullable(head))
                    .map(AnyObjectId::name)
                    .flatMapResult(toResetTo -> {
                        try {
                            git.reset().setRef(resetTo)
                                    .setMode(ResetCommand.ResetType.HARD)
                                    .call();
                            var retrieved = toDo.get();
                            return Result.ok(retrieved);
                        } catch (GitAPIException e) {
                            return Result.err(new RepoUtilError(e));
                        } finally {
                            try {
                                git.reset().setRef(toResetTo).setMode(ResetCommand.ResetType.HARD).call();
                                if (created != null) {
                                    git.stashApply().call();
                                }
                            } catch (GitAPIException e) {
                                log.error("Error applying stash or reset .", e);
                            }
                        }
                    })
                    .one();
        } catch (IOException | GitAPIException e) {
            return Result.err(new RepoUtilError(e));
        }
    }

    static @NotNull OneResult<List<DiffEntry>, RepoUtilError> retrieveStagedChanges(Git git) {
        try {
            doTempCommit(git);
            var gd = git.diff()
                    .setOldTree(getForRef("HEAD^1", git.getRepository()))
                    .setNewTree(getForRef("HEAD", git.getRepository()))
                    .call();

            return Result.fromOpt(Optional.ofNullable(gd), doReset(git)).one();
        } catch (GitAPIException | IOException e) {
            return Result.err(new RepoUtilError("Failed when retrieving staged diff: %s.".formatted(e.getMessage())));
        }
    }

    record GitInitError(String getMessage) implements SingleError {}

    record RepoUtilError(String getMessage) implements SingleError {
        public RepoUtilError(Throwable getMessage) {
            this(SingleError.parseStackTraceToString(getMessage));
        }
    }

    record RepoUtilAggregateError(Set<RepoUtilError> errors) implements AggregateError<RepoUtilError> {
    }

    static Result<Path, RepoUtilError> cloneIfRemote(String url, String branchName, File gitDir) {
        if (url.startsWith("http") || url.startsWith("git") || url.startsWith("ssh")) {
            return RepoUtil.cloneRepo(gitDir, url, branchName)
                    .mapError(gitInitError -> new RepoUtilError("Failed to clone git repo: %s.".formatted(gitInitError.getMessage())))
                    .map(git -> gitDir.toPath());
        }

        return returnEmptyOrErrIfNotExists(url);
    }

    static Result<Path, RepoUtilError> cloneIfRemote(String url, String branchName) {
        return cloneIfRemote(url, branchName, FileUtils.newTemporaryFolder());
    }

    static Result<Path, RepoUtilError> decompressIfArchive(String url) {
        if (url.endsWith(".tar")) {
            if (!new File(url).exists()) {
                return Result.err(new RepoUtilError("Repo archive %s did not exist.".formatted(url)));
            }
            var tempDir = FileUtils.newTemporaryFolder();
            Path tarPath = Paths.get(url);
            Path unzippedPath = tempDir.toPath();
            var unzipped = ArchiveUtils.prepareTestRepos(tarPath.getParent(), unzippedPath, tarPath.getFileName().toString());
            return unzipped
                    .mapError(se -> new RepoUtilError(se.getMessage()))
                    .map(unzippedFiles -> unzippedPath);
        }

        return returnEmptyOrErrIfNotExists(url);
    }

    static Result<Path, RepoUtilError> doDecompressCloneRepo(String url, String branchName) {
        return decompressIfArchive(url)
                .dropErr()
                .one()
                .or(() -> cloneIfRemote(url, branchName))
                .dropErr()
                .one()
                .or(() -> returnPathOrErrIfNotExists(url));
    }

    private static @NotNull Result<Path, RepoUtilError> returnEmptyOrErrIfNotExists(String url) {
        var f = new File(url);

        if (f.exists())
            return Result.empty();

        return Result.err(new RepoUtilError("Failed to clone git repo - %s did not exist.".formatted(url)));
    }

    private static @NotNull Result<Path, RepoUtilError> returnPathOrErrIfNotExists(String url) {
        var f = new File(url);

        if (f.exists())
            return Result.ok(f.toPath());

        return Result.err(new RepoUtilError("Failed to clone git repo - %s did not exist.".formatted(url)));
    }


    static Result<Git, RepoUtilError> cloneRepo(File gitDir, String toClone, String branch) {
        return Result.<Git, RepoUtilError>tryFrom(() ->
                Git.cloneRepository().setFs(FS.detect())
                        .setDirectory(gitDir)
                        .setURI(toClone)
                        .setBranch(branch)
                        .call())
                .flatExcept(exc -> Result.err(new RepoUtilError(exc)));
    }

    static Result<String, RepoUtilError> worktreeAdd(Path repoPath, Path worktreePath, String branchName) {
        if (repoPath == null || worktreePath == null) {
            return Result.err(new RepoUtilError("repoPath/worktreePath required"));
        }
        List<String> args = new ArrayList<>(List.of("worktree", "add", worktreePath.toString()));
        if (branchName != null && !branchName.isBlank()) {
            args.add(branchName);
        }
        return runGitCommand(repoPath, args);
    }

    static Result<String, RepoUtilError> worktreeRemove(Path repoPath, Path worktreePath, boolean force) {
        if (repoPath == null || worktreePath == null) {
            return Result.err(new RepoUtilError("repoPath/worktreePath required"));
        }
        List<String> args = new ArrayList<>(List.of("worktree", "remove"));
        if (force) {
            args.add("--force");
        }
        args.add(worktreePath.toString());
        return runGitCommand(repoPath, args);
    }

    static Result<String, RepoUtilError> worktreeList(Path repoPath) {
        if (repoPath == null) {
            return Result.err(new RepoUtilError("repoPath required"));
        }
        return runGitCommand(repoPath, List.of("worktree", "list", "--porcelain"));
    }

    static Result<List<String>, RepoUtilAggregateError> updateSubmodulesRecursively(Path repoPath) {
        return initGit(repoPath)
                .mapError(err -> new RepoUtilAggregateError(Sets.newHashSet(new RepoUtilError(err.getMessage()))))
                .flatMapResult(git -> updateSubmodulesRecursively(git.getRepository()))
                .one();
    }

    private static Result<List<String>, RepoUtilAggregateError> updateSubmodulesRecursively(Repository repo) {
        RepoUtil.runGitCommand(repo.getDirectory().toPath(), List.of("submodule", "update", "--init", "--recursive"));
        RepoUtil.runGitCommand(repo.getDirectory().toPath(), List.of("submodule", "foreach", "--recursive", "git reset --hard || true"));
        return updateSubmodulesRecursively(repo, "");
    }

    private static Result<List<String>, RepoUtilAggregateError> updateSubmodulesRecursively(Repository repo, String parent) {
        List<String> updated = new ArrayList<>();
        Set<RepoUtilError> errs = new HashSet<>();
        try (Git git = new Git(repo)) {


            try (SubmoduleWalk walk = SubmoduleWalk.forIndex(repo)) {
                try {
                    while (walk.next()) {
                        String path = walk.getPath();
                        String relPath = StringUtils.isNotBlank(parent) ? "%s/%s".formatted(parent, path) : path;
                        if (path != null) {
                            updated.add(relPath);
                        }
                        try (Repository subRepo = walk.getRepository()) {
                            if (subRepo != null) {
                                var u = updateSubmodulesRecursively(subRepo, relPath);
                                if (u.isErr()) {
                                    u.doOnError(a -> errs.addAll(a.errors));
                                } else {
                                    updated.addAll(u.r().get());
                                }
                            }
                        } catch (IOException e) {
                            errs.add(new RepoUtilError("Failed to get repo %s: %s".formatted(walk.getPath(), e.getMessage())));
                        }
                    }
                } catch (IOException e) {
                    errs.add(new RepoUtilError("Failed to walk repo %s: %s".formatted(repo.getDirectory(), e.getMessage())));
                }
            } catch (IOException e) {
                errs.add(new RepoUtilError("Failed to get index on repo %s: %s".formatted(repo.getDirectory(), e.getMessage())));
            }
        }

        return Result.from(updated, new RepoUtilAggregateError(errs));
    }

    static Git initGitOrThrow(Path path) throws IOException {
        var initialized = initGit(path) ;

        if (initialized.isOk())
            return initialized.r().get();

        throw new IOException(initialized.e().toString());
    }

    static ClosableResult<Git, GitInitError> initGit(Path path) {
        if (path.toFile().isDirectory() && !path.toFile().getName().endsWith(".git")) {
            path = path.resolve(".git");
        }

        if (!path.toFile().getName().endsWith(".git")) {
            return Result.tryErr(new GitInitError("Invalid git path: " + path));
        }

        final Path repoPath = path;

        if (repoPath.toFile().exists()) {
            if (repoPath.toFile().isFile()) {
                return Result.tryFrom(() -> {
                    Repository repo = findRepo(repoPath);
                    return new Git(repo);
                });
            }

            return Result.tryFrom(() -> Git.open(repoPath.toFile(), FS.detect()));
        }

        return Result.tryFrom(
                () -> Git.init().setGitDir(repoPath.toFile())
                        .setInitialBranch("main")
                        .setFs(FS.detect())
                        .setDirectory(repoPath.toFile().getParentFile())
                        .call());
    }

    static Repository findRepo(Path repoPath) throws IOException {
        Repository repo = new FileRepositoryBuilder()
                .findGitDir(repoPath.toFile())   // resolves even when .git is a file
                .build();
        return repo;
    }

    static Result<String, RepoUtilError> runGitCommand(Path repoPath, List<String> args) {
        List<String> command = new ArrayList<>();
        if (args.isEmpty())
            return Result.err(new RepoUtilError("Did not even have any args!"));

        if (args.getFirst().equals("git")) {
            args = new ArrayList<>(args);
            args.removeFirst();
        }
        command.add("git");
        command.addAll(args);
        ProcessBuilder pb = new ProcessBuilder(command)
                .directory(repoPath.endsWith(".git") ? repoPath.getParent().toFile() : repoPath.toFile());
        pb.redirectErrorStream(true);
        StringBuilder output = new StringBuilder();
        try {
            Process process = pb.start();
            try (var reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            int exit = process.waitFor();
            if (exit != 0) {
                return Result.err(new RepoUtilError("Git command failed: %s\n%s"
                        .formatted(String.join(" ", command), output)));
            }
            return Result.ok(output.toString());
        } catch (Exception e) {
            return Result.err(new RepoUtilError(e));
        }
    }

    static Path getGitRepo() {
        var f= new File("");
        return getGitRepo(f);
    }

    static @NotNull Path getGitRepo(File f) {
        var p = f.toPath();

        if (p.resolve(".git").toFile().exists()) {
            return f.toPath().resolve(".git").toAbsolutePath();
        } else {
            while (f.getParentFile() != null && !f.getParentFile().toPath().resolve(".git").toFile().exists()) {
                var pf = f.getParentFile();
                if (pf.equals(f) || pf.equals(f.toPath().getRoot().toFile()))
                    break;
                else
                    f = pf;
            }

            if (f.toPath().resolve(".git").toFile().exists()) {
                return f.toPath().resolve(".git").toAbsolutePath();
            }
        }


        throw new RuntimeException("Could not find git repository");
    }

    // Helper method to find the last commit before the file was deleted
    @SneakyThrows
    static Result<RevCommit, RepoUtilError> getLastCommitBeforeDeletion(Git repository, String filePath) {
        // Assuming the file was deleted in the latest commit, we walk backwards to find the commit that last contained the file
        var commits = repository.log().addPath(filePath).call();
        for (RevCommit commit : commits) {
            return Result.ok(commit); // This is the last commit where the file exists
        }

        return Result.err(new RepoUtilError("Could not find commit containing %s".formatted(filePath)));
    }

    static Result<String, RepoUtilError> retrieveDeletedContent(String path, Git git) {
        return getLastCommitBeforeDeletion(git, path)
                .filterResult(Objects::nonNull)
                .flatMapResult(lastCommit -> treeWalkForPathInCommit(lastCommit, git, path))
                .flatMapResult(treeWalk -> {
                    // Step 3: Retrieve the file content from that commit
                    try {
                        if (treeWalk.next()) {
                            // Get the file content from the object id at that commit
                            try {
                                ObjectId fileId = treeWalk.getObjectId(0);
                                byte[] fileContent = git.getRepository().open(fileId).getBytes();
                                return Result.ok(new String(fileContent, StandardCharsets.UTF_8));
                            } catch (IOException e) {
                                return Result.err(new RepoUtilError("Failed to open file when retrieving deleted: %s.".formatted(e.getMessage())));
                            }
                        } else {
                            return Result.err(new RepoUtilError("Tree walk did not have next for %s".formatted(path)));
                        }
                    } catch (IOException e) {
                        return Result.err(new RepoUtilError("Failed to perform tree walk when retrieving deleted: %s.".formatted(e.getMessage())));
                    }
                });

        }

    static Result<TreeWalk, RepoUtil.RepoUtilError> treeWalkForPathInCommit(RevCommit lastCommit, Git git, String path) {
        TreeWalk treeWalk = new TreeWalk(git.getRepository());
        try {
            treeWalk.addTree(lastCommit.getTree());
            treeWalk.setRecursive(true);
            treeWalk.setFilter(PathFilter.create(path));
            return Result.ok(treeWalk);
        } catch (IOException e) {
            return Result.err(new RepoUtil.RepoUtilError("Could not do tree walk when retrieving deleted: %s.".formatted(e.getMessage())));
        }
    }

    static Map.Entry<Ref, String> retrieveBranch(String branch, Git git) throws GitAPIException {
        var branchStart = git.branchList().call().stream()
                .filter(r -> r.getName().contains(branch))
                .map(r -> Map.entry(r, branch))
                .findAny()
                .orElseThrow(() -> new RuntimeException("Branch %s not found.".formatted(branch)));
        return branchStart;
    }

    static OneResult<RevCommit, RepoUtilError> getLatestCommit(Git git, String branch) {
        try {
            var branchStart = retrieveBranch(branch, git);
            Iterator<RevCommit> commits = git.log().add(branchStart.getKey().getObjectId()).call().iterator();
            return Result.ok(commits.next());
        } catch (GitAPIException |
                 IncorrectObjectTypeException |
                 MissingObjectException e) {
            return Result.err(new RepoUtilError(SingleError.parseStackTraceToString(e)));
        }
    }

    static Result<List<DiffEntry>, RepoUtilError> retrieveDiffEntries(String childHash, String parentHash,
                                                                      Git git,
                                                                      Set<String> excludePattern) {
        try (var reader = git.getRepository().newObjectReader()) {
            var oldTree = new CanonicalTreeParser();
            var r = git.getRepository().resolve("%s^{tree}".formatted(childHash));
            oldTree.reset(reader, r);
            var newTree = new CanonicalTreeParser();
            var p = git.getRepository().resolve("%s^{tree}".formatted(parentHash));
            newTree.reset(reader, p);
            Path parent = git.getRepository().getDirectory().toPath().getParent();
            DiffCommand diffCommand = git.diff().setOldTree(oldTree).setNewTree(newTree);
            diffCommand = buildFilter(excludePattern, parent, diffCommand);
            var diffEntries = diffCommand
                    .setContextLines(0).call();
            return Result.ok(diffEntries);
        } catch (GitAPIException | IOException e) {
            return Result.err(new RepoUtilError(e.getMessage()));
        }
    }

    static DiffFormatter buildFilter(Set<String> excludePattern, Path parent, DiffFormatter diffCommand) {
        var pathFilter = buildFilter(excludePattern, parent);
        DiffFormatter finalDiffCommand = diffCommand;
        return pathFilter
                .map(d -> {
                    finalDiffCommand.setPathFilter(d);
                    return finalDiffCommand;
                })
                .orElse(diffCommand);
    }

    static DiffCommand buildFilter(Set<String> excludePattern, Path parent, DiffCommand diffCommand) {
        var pathFilter = buildFilter(excludePattern, parent);
        DiffCommand finalDiffCommand = diffCommand;
        return pathFilter
                .map(finalDiffCommand::setPathFilter)
                .orElse(diffCommand);
    }

    static Optional<TreeFilter> buildFilter(Set<String> excludePattern, Path parent) {
        if (excludePattern.isEmpty())
            return Optional.empty();
        if (excludePattern.size() == 1)
            return Optional.of(NotTreeFilter.create(new WildcardPathFilter(excludePattern.stream().findAny().get(), parent)));


        return Optional.of(NotTreeFilter.create(
                OrTreeFilter.create(
                        excludePattern.stream()
                                .map(w -> new WildcardPathFilter(w, parent))
                                .toArray(WildcardPathFilter[]::new))));
    }


}
