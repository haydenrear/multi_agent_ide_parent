package com.hayden.utilitymodule.io;

import com.hayden.utilitymodule.result.ClosableResult;
import com.hayden.utilitymodule.result.error.SingleError;
import com.hayden.utilitymodule.result.Result;
import jakarta.annotation.Nonnull;
import lombok.SneakyThrows;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Slf4j
public class FileUtils {

    public static long count(Path repoRoot, Predicate<File> include) {
        try(var f = Files.walk(repoRoot)) {
            var counted = f.filter(p -> include.test(p.toFile()))
                    .count();

            return counted;
        } catch (IOException e) {
            return -1;
        }
    }

    public static boolean hasPathNamed(Path repoRoot, String name) {
        return hasPathNamed(repoRoot, n -> Objects.equals(name, n));
    }

    public static LocalDateTime lastUpdated(Path file) throws IOException {
        BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
        LocalDateTime fileModifiedTime = LocalDateTime.ofInstant(
                attrs.lastModifiedTime().toInstant(),
                ZoneId.systemDefault()
        );

        return fileModifiedTime;
    }

    public static int findIndexOf(Path repoRoot, Predicate<List<Path>> name) {
        List<Path> prev = new ArrayList<>();
        for (int i=0; i<repoRoot.getNameCount(); i++) {
            var r=repoRoot.getName(i);
            prev.add(r);
            if (name.test(prev))
                return i;
        }

        return -1;
    }

    public static boolean hasPathNamed(Path repoRoot, Predicate<String> name) {
        return IntStream.range(0, repoRoot.getNameCount())
                .anyMatch(i -> {
                    String name1 = repoRoot.getName(i)
                            .toFile()
                            .getName();
                    return name.test(name1);
                });
    }

    public static boolean isNotSelfNorStrictSubPath(Path child, Path parent) {
        return !Objects.equals(child, parent) && !isSelfOrStrictSubPath(child, parent);
    }

    public static boolean isSelfOrStrictSubPath(Path child, Path parent) {
        return Objects.equals(child, parent) || isStrictSubPath(child, parent);
    }

    public static boolean isStrictSubPath(Path child, Path parent) {
        return (
                !child.equals(parent) &&
                        child.normalize().startsWith(parent.normalize()) &&
                        child.getNameCount() > parent.getNameCount()
        );
    }

    public static File newTemporaryFolder() {
        String tempFileName = UUID.randomUUID().toString();
        return newFolder(temporaryFolderPath() + tempFileName);
    }

    public static String temporaryFolderPath() {
        return File.separator + System.getProperty("java.io.tmpdir");
    }

    public static File newFolder(String path) {
        File file = createFileIfPathIsNotANonEmptyDirectory(path);

        try {
            if (!file.mkdir()) {
                throw cannotCreateNewFile(path, "a file was found with the same path");
            } else {
                return file;
            }
        } catch (Exception e) {
            throw cannotCreateNewFile(path, e);
        }
    }

    public static File newFile(String path) {
        File file = createFileIfPathIsNotANonEmptyDirectory(path);

        try {
            if (!file.createNewFile()) {
                throw cannotCreateNewFile(path, "a file was found with the same path");
            } else {
                return file;
            }
        } catch (IOException e) {
            throw cannotCreateNewFile(path, e);
        }
    }

    private static File createFileIfPathIsNotANonEmptyDirectory(String path) {
        File file = new File(path);
        if (file.isDirectory() && !ArrayUtils.isEmpty(file.list())) {
            throw cannotCreateNewFile(path, "a non-empty directory was found with the same path");
        } else {
            return file;
        }
    }

    private static UncheckedIOException cannotCreateNewFile(String path, String reason) {
        throw cannotCreateNewFile(path, reason, (Exception) null);
    }

    private static UncheckedIOException cannotCreateNewFile(String path, Exception cause) {
        throw cannotCreateNewFile(path, (String) null, cause);
    }

    private static UncheckedIOException cannotCreateNewFile(String path, String reason, Exception cause) {
        String message = String.format("Unable to create the new file %s", path);
        if (!StringUtils.isEmpty(reason)) {
            message = message + ": " + reason;
        }

        if (cause == null) {
            throw new RuntimeException(message);
        } else if (cause instanceof IOException) {
            throw new UncheckedIOException(message, (IOException) cause);
        } else {
            throw new RuntimeException(message, cause);
        }
    }

    public static File replaceHomeDir(Path homeDir, String subDir) {
        if (subDir.startsWith("~/")) {
            return homeDir.resolve(subDir.replace("~/", "")).toFile();
        }

        return new File(subDir);
    }

    public static InputStream getResourceAsStream(String value) {
        return FileUtils.class.getClassLoader().getResourceAsStream(value);
    }

    public static boolean writeBytesToFile(byte[] data, Path file) {
        try {
            if (isFileInvalidNotCreatable(file)) {
                log.error("Failed to write to file {}. Could not create file", file.toFile());
                return false;
            }
            try (var fw = new FileOutputStream(file.toFile())) {
                fw.write(data);
            }
            return true;
        } catch (IOException e) {
            log.error("Failed to write to file: {}.", e.getMessage());
            return false;
        }
    }
    
    public static Optional<Integer> srcTestJavaEnd(Path p) {
        return findSubPathEndIdx(p, "src/test/java");
    }

    public static Optional<Integer> srcMainJavaEnd(Path p) {
        return findSubPathEndIdx(p, "src/main/java");
    }

    private static @NotNull Optional<Integer> findSubPathEndIdx(Path p, String path) {
        var idx = findIndexOf(p, l -> {
            if (l.size() < 3)
                return false;

            if (l.subList(l.size() - 3, l.size()).stream().map(Path::getFileName)
                    .map(Path::toString)
                    .collect(Collectors.joining("/"))
                    .equals(path))  {
                return true;
            }

            return false;

        });

        return idx == -1 ? Optional.empty() : Optional.of(idx);
    }

    public sealed interface FileReplacement {
        record JavaPackageSlashReplacement() implements FileReplacement {}
    }

    /**
     * <p>Main: resolve filePathDir under/relative to rootDir, using replacements & smart suffix matching.</p>
     * <p>
     *     If not existing, check to see if the filePathDir contains any of rootDir, then do a special rebase if
     *     does. Then if any of it did, then return that if parent exists, else return it if the parent directory
     *     of rebase exists. Similar to mkdir without -p, can only make one directory helping with semantics
     *     to not fail.
     * </p>
     */
    public static Result<Path, FileError> getPathFor(Path filePathDir,
                                                     Path rootDir,
                                                     Map<String, String> replace) {
        Path trivialResolve = rootDir.resolve(filePathDir);

        if (exists(trivialResolve)) {
            return Result.ok(trivialResolve);
        }

        trivialResolve = applyReplacements(trivialResolve, replace);

        if (exists(trivialResolve)) {
            return Result.ok(trivialResolve);
        }

        // 0) Normalize inputs
        Path rootN = normalizeSoft(rootDir);
        Path fileN = normalizeSoft(filePathDir);

        // 1) Quick wins
        //    a) exact path exists
        if (exists(fileN))
            return Result.ok(realOrSelf(fileN));
        //    b) resolve relative under root
        if (!fileN.isAbsolute()) {
            Path underRoot = rootN.resolve(fileN).normalize();
            if (exists(underRoot))
                return Result.ok(realOrSelf(underRoot));
        }

        Path rootRepl = applyReplacements(rootN, replace);
        Path fileRepl = applyReplacements(fileN, replace);
        if (exists(fileRepl))
            return Result.ok(realOrSelf(fileRepl));
        if (!fileRepl.isAbsolute()) {
            Path underRootRepl = rootRepl.resolve(fileRepl).normalize();
            if (exists(underRootRepl))
                return Result.ok(realOrSelf(underRootRepl));
        }

        // 3) If file is absolute but under a different root prefix, try "rebasing" onto root
        //    (strip common leading segments and paste remainder onto root)
        Path maybeRebased = rebaseOntoRoot(fileN, rootN);
        if (exists(maybeRebased))
            return Result.ok(realOrSelf(maybeRebased));

        Path maybeRebasedRepl = rebaseOntoRoot(fileRepl, rootRepl);
        if (exists(maybeRebasedRepl))
            return Result.ok(realOrSelf(maybeRebasedRepl));

        // 4) Suffix-match search within root (and replaced root) for the tail of the requested path
        Path suffixHit = findBySuffix(rootN, fileN);
        if (suffixHit != null)
            return Result.ok(suffixHit);

        if (!rootRepl.equals(rootN)) {
            Path suffixHit2 = findBySuffix(rootRepl, fileRepl);
            if (suffixHit2 != null)
                return Result.ok(suffixHit2);
        }

        // 5) Last chance: search by filename only (fast path)
        Path nameOnlyHit = findByFileName(rootN, lastName(fileN));
        if (nameOnlyHit != null)
            return Result.ok(nameOnlyHit);

        if (!rootRepl.equals(rootN)) {
            Path nameOnlyHit2 = findByFileName(rootRepl, lastName(fileRepl));
            if (nameOnlyHit2 != null)
                return Result.ok(nameOnlyHit2);
        }

        if (filePathDir.startsWith(rootDir)) {
            return Result.ok(filePathDir);
        }

        int i =0;
        int last = -1;
        for (var v : rootDir) {
            Path subpath = rootDir.subpath(last == -1 ? i : last, rootDir.getNameCount());
            if (filePathDir.startsWith(subpath)) {
                if (last == -1)
                    last = i;
            } else if (last != -1) {
                last = -1;
            }

            i += 1;
        }

        if (last != -1) {
            String rootDirSubPath = rootDir.subpath(last, rootDir.getNameCount()).toString();
            String filePathRootRemoved = filePathDir.toString().replace(rootDirSubPath, "");
            if (filePathRootRemoved.startsWith("/"))
                filePathRootRemoved = filePathRootRemoved.substring(1);
            filePathDir = Paths.get(filePathRootRemoved);
            Path newResolved = rootDir.resolve(filePathDir);

            if (!Files.exists(newResolved.getParent()))
                return Result.err(new FileError("Parent directory %s did not exist."
                        .formatted(maybeRebasedRepl.getParent().toString())));

            return Result.ok(newResolved);
        } else {
            if (!Files.exists(maybeRebasedRepl.getParent()))
                return Result.err(new FileError("Parent directory %s did not exist."
                        .formatted(maybeRebasedRepl.getParent().toString())));

            return Result.ok(maybeRebasedRepl);
        }
    }

    /**
     * A cleaned-up version of your search that reuses the same heuristics.
     */
    public static Path getClosestPathForSearch(Path fileToSearch, List<Path> roots) {
        Path fileN = normalizeSoft(fileToSearch);
        if (exists(fileN))
            return realOrSelf(fileN);

        // try direct equality or direct resolution under any root
        for (Path r : roots) {
            Path rN = normalizeSoft(r);
            if (fileN.equals(rN)) return fileN;
            if (!fileN.isAbsolute()) {
                Path under = rN.resolve(fileN).normalize();
                if (exists(under))
                    return realOrSelf(under);
            } else {
                Path rebased = rebaseOntoRoot(fileN, rN);
                if (rebased != null && exists(rebased))
                    return realOrSelf(rebased);
            }
        }

        // suffix match search under each root
        for (Path r : roots) {
            Path hit = findBySuffix(normalizeSoft(r), fileN);
            if (hit != null) return hit;
        }

        // filename-only fallback
        for (Path r : roots) {
            Path hit = findByFileName(normalizeSoft(r), lastName(fileN));
            if (hit != null) return hit;
        }

        // recursive tail trimming (rarely needed now, but kept as final fallback)
        if (fileN.getNameCount() > 1) {
            Path tail = fileN.subpath(1, fileN.getNameCount());
            Path rec = getClosestPathForSearch(tail, roots);
            if (rec != null) return rec;
        }
        return null;
    }

    // ---------- helpers ----------

    private static Path normalizeSoft(Path p) {
        try {
            // If it exists, prefer real path; otherwise normalize absolute/relative safely
            return exists(p) ? p.toRealPath(LinkOption.NOFOLLOW_LINKS) : p.normalize();
        } catch (IOException e) {
            return p.normalize();
        }
    }

    private static boolean exists(Path p) {
        return p != null && Files.exists(p, LinkOption.NOFOLLOW_LINKS);
    }

    private static Path realOrSelf(Path p) {
        try {
            return p.toRealPath(LinkOption.NOFOLLOW_LINKS);
        } catch (IOException e) {
            return p.normalize();
        }
    }

    /**
     * Sometimes the AI puts dashes where package does not allow dashes.
     * @param p
     * @return
     */
    public static Path doReplacementForJavaPackage(Path p) {
        return FileUtils.srcMainJavaEnd(p)
                .or(() -> FileUtils.srcTestJavaEnd(p))
                .map(end -> {
                    var f =  IntStream.range(0, p.getNameCount())
                            .boxed()
                            .map(i -> Map.entry(i, p.getName(i)))
                            .map(pi -> {
                                if (pi.getValue().getFileName().toString().contains("-") && pi.getKey() > end) {
                                    return Paths.get(pi.getValue().getFileName().toString().replaceAll("-", ""));
                                }

                                return pi.getValue();
                            })
                            .map(Path::toString)
                            .toList();

                    if (f.size() <= 1)
                        return p;

                    return Paths.get(f.getFirst(), f.subList(1, f.size()).toArray(String[]::new));
                })
                .orElse(p);
    }

    private static Path applyReplacements(Path p, Map<String, String> replace) {
        p = doReplacementForJavaPackage(p);
        String s = p.toString();
        if (replace != null) {
            for (var e : replace.entrySet()) {
                // LITERAL replace; if you really want regex semantics switch to Pattern/Matcher safely
                s = s.replace(e.getKey(), e.getValue());
            }
        }
        return Paths.get(s).normalize();
    }

    /**
     * If file is absolute and shares a prefix with some other base (or can be sliced),
     * strip the longest common prefix between file and its own root, then paste the remainder onto root.
     * Works for cases like:
     * root=/Users/first/okay, file=/okay/one/two  -> /Users/first/okay/one/two
     */
    private static Path rebaseOntoRoot(Path fileAbsOrRel, Path root) {
        Path f = fileAbsOrRel.normalize();
        Path r = root.normalize();

        // If file is already under root, just return it
        try {
            if (f.toRealPath().startsWith(r.toRealPath()))
                return f;
        } catch (
                IOException ignored) { /* proceed */ }

        // Try to remove an initial segment of file and append to root
        // Strategy: find the longest suffix of 'f' that exists under 'r'
        Path attempt = null;

        // If f is absolute, try to relativize by chopping leading names until it fits
        int start = 0;
        if (f.isAbsolute()) {
            // drop the first name and try
            start = 1;
        }
        for (int i = start; i < f.getNameCount(); i++) {
            Path suffix = f.subpath(i, f.getNameCount()); // tail
            Path candidate = r.resolve(suffix).normalize();
            if (exists(candidate))
                return candidate;
            // keep the first viable candidate even if it doesn't exist yet (optional behavior)
            if (attempt == null)
                attempt = candidate;
        }

        // If nothing exists but we built a sane candidate, return it (caller will check again)
        return attempt;
    }

    /**
     * Find a path under root whose path suffix matches target (Path.endsWith).
     */
    private static Path findBySuffix(Path root, Path target) {
        if (!Files.isDirectory(root)) return null;
        Path suffix = target.isAbsolute() ? target.normalize().getNameCount() > 0
                ? target.normalize().subpath(0, target.getNameCount())
                : target.normalize()
                : target.normalize();

        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> endsWithPath(p, suffix))
                    .findFirst()
                    .map(FileUtils::realOrSelf)
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Find by filename only (last path segment) under root.
     */
    private static Path findByFileName(Path root, String fileName) {
        if (!Files.isDirectory(root)) return null;
        try (Stream<Path> s = Files.walk(root)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().equals(fileName))
                    .findFirst()
                    .map(FileUtils::realOrSelf)
                    .orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    private static String lastName(Path p) {
        return p.getNameCount() == 0 ? p.toString() : p.getName(p.getNameCount() - 1).toString();
    }

    /**
     * Path.endsWith for multi-segment suffix (portable across platforms).
     */
    private static boolean endsWithPath(Path p, Path suffix) {
        Path pn = p.normalize();
        Path sn = suffix.normalize();
        if (sn.getNameCount() == 0)
            return pn.equals(sn);
        if (sn.getNameCount() > pn.getNameCount())
            return false;

        // Compare last N name elements
        int offset = pn.getNameCount() - sn.getNameCount();
        for (int i = 0; i < sn.getNameCount(); i++) {
            if (!pn.getName(offset + i).toString().equals(sn.getName(i).toString())) {
                return false;
            }
        }
        return true;
    }

    public record FileError(String getMessage) implements SingleError {
    }

    public static Result<Boolean, FileError> appendToFileRes(String data, Path file) {
        try {
            if (isFileInvalidNotCreatable(file)) {
                var f = "Failed to write to file %s. Could not create file".formatted(file.toFile());
                log.error(f);
                return Result.err(new FileError(f));
            }

            if (data != null)
                try (FileWriter fw = new FileWriter(file.toFile(), true)) {
                    fw.write(data);
                }

            return Result.ok(true);
        } catch (IOException e) {
            var f = "Failed to write to file: %s.".formatted(e.getMessage());
            return Result.err(new FileError(f));
        }
    }

    public static Result<Boolean, FileError> writeToFileRes(String data, Path file) {
        try {
            if (isFileInvalidNotCreatable(file)) {
                var f = "Failed to write to file %s. Could not create file".formatted(file.toFile());
                log.error(f);
                return Result.err(new FileError(f));
            }

            if (data != null)
                try (FileWriter fw = new FileWriter(file.toFile())) {
                    fw.write(data);
                }

            return Result.ok(true);
        } catch (IOException e) {
            var f = "Failed to write to file: %s.".formatted(e.getMessage());
            return Result.err(new FileError(f));
        }
    }

    public static boolean writeToFile(String data, Path file) {
        try {
            if (isFileInvalidNotCreatable(file)) {
                log.error("Failed to write to file {}. Could not create file", file.toFile());
                return false;
            }
            try (FileWriter fw = new FileWriter(file.toFile())) {
                fw.write(data);
            }
            return true;
        } catch (IOException e) {
            log.error("Failed to write to file: {}.", e.getMessage());
            return false;
        }
    }

    private static boolean isFileInvalidNotCreatable(Path file) throws IOException {
        createFile(file);
        return !file.toFile().exists();
    }

    private static void createDirs(Path file) {
        file.getParent().toFile().mkdirs();
    }

    private static void createFile(Path file) throws IOException {
        if (file.toFile().exists())
            return;
        createDirs(file);
        file.toFile().createNewFile();

    }

    /**
     * Searches upwards from the given starting directory to find the closest
     * parent directory that contains a file with the specified name.
     *
     * @param startPath      The starting directory path.
     * @param targetFileName The name of the file to search for.
     * @return The File object of the directory that contains the file, or null if not found.
     */
    public static File findClosestParentWithFile(Path startPath, Predicate<String> targetFileName) {
        var p = searchForRecursive(startPath, f -> targetFileName.test(f.getName()));

        if (p.isPresent())
            return p.get().toFile();

        Path current = startPath.toAbsolutePath();
        while (current != null) {
            File[] matchingFiles = current.toFile().listFiles((dir, name) -> targetFileName.test(name));
            if (matchingFiles != null && matchingFiles.length > 0) {
                return current.toFile();
            }
            current = current.getParent();
        }

        return null;
    }

    public static Optional<Path> getTestWorkDir() {
        return getTestWorkDir(new File("./").toPath());
    }

    public static Optional<Path> getTestWorkDir(Path path) {
        return searchForRecursive(path, f -> f.isDirectory() && Objects.equals(f.getName(), "test_work"));
    }

    public static Optional<Path> searchForRecursive(Path path, Predicate<File> fileName) {

        if (fileName.test(path.toFile()))
            return Optional.of(path);

        var did = Optional.ofNullable(path.toFile().listFiles())
                .stream();

        return did.flatMap(Arrays::stream)
                .flatMap(p -> {
                    if (fileName.test(p)) {
                        return Stream.of(p.toPath());
                    } else if (p.isDirectory()) {
                        var didDelete = searchForRecursive(p.toPath(), fileName);
                        if (didDelete.isPresent()) {
                            return didDelete.stream();
                        }
                    }

                    return Stream.empty();
                })
                .findAny();
    }

    public static Optional<Path> searchForFileRecursive(Path path, String fileName) {
        return searchForRecursive(path, p -> p.isFile() && fileName.equals(p.getName()));
    }

    public static Path findVisit(Path source, Predicate<Path> found, Predicate<Path> continueWalking) {
        AtomicReference<Path> result = new AtomicReference<>();
        try {
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (found.test(dir)) {
                        result.set(dir);
                        return FileVisitResult.TERMINATE;
                    }
                    if (continueWalking.test(dir))
                        return FileVisitResult.CONTINUE;

                    return FileVisitResult.SKIP_SUBTREE;
                }

                @Override
                public FileVisitResult visitFile(Path dir, BasicFileAttributes attrs) {
                    if (found.test(dir)) {
                        result.set(dir);
                        return FileVisitResult.TERMINATE;
                    }
                    if (continueWalking.test(dir))
                        return FileVisitResult.CONTINUE;

                    return FileVisitResult.SKIP_SUBTREE;
                }
            });
        } catch (IOException e) {
            log.error("Failed to visit file: {}.", e.getMessage());
        }
        return result.get();
    }


    public static void copyAll(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetPath = target.resolve(source.relativize(dir));
                Files.createDirectories(targetPath);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetPath = target.resolve(source.relativize(file));
                Files.copy(file, targetPath, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static boolean doOnFilesRecursive(Path path, Function<Path, Boolean> toDo) {
        return doOnFilesRecursive(path, toDo, r -> false);
    }

    @SneakyThrows
    public static boolean doOnFilesRecursive(Path path, Function<Path, Boolean> toDo, Predicate<Path> skipTree) {
        AtomicBoolean result = new AtomicBoolean(true);
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public @NotNull FileVisitResult visitFile(@NotNull Path file, @NotNull BasicFileAttributes attrs) {
                if (file.toFile().isFile()) {
                    if (!toDo.apply(file)) {
                        result.set(false);
                    }
                }

                if (skipTree.test(file)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }

                return FileVisitResult.CONTINUE;
            }
        });

        return result.get();
    }

    @SneakyThrows
    public static boolean doOnFilesRecursiveParallel(Path path, Function<Path, Boolean> toDo) {
        try (var f = Files.walk(path)) {
            return f.parallel().allMatch(toDo::apply);
        }
    }

    /**
     * returns AutoClosable - must use in try with resources or close explicitly.
     *
     * @param path
     * @return
     */
    public static Stream<Path> getFilesRecursive(Path path) {
        try {
            return Files.walk(path);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static <T> T doOverFilePathLines(Path file, BiConsumer<String, AtomicReference<T>> toDo, AtomicReference<T> update) {
        if (file.toFile().isFile()) {
            try (
                    FileReader fr = new FileReader(file.toFile());
                    BufferedReader bf = new BufferedReader(fr)
            ) {
                bf.lines().forEach(l -> toDo.accept(l, update));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return update.get();
    }

    @SneakyThrows
    public static Iterator<Path> fileIteratorRecursive(Path path) {
        Stream<Path> walk = Files.walk(path);
        var f = walk.iterator();
        AtomicBoolean isClosed = new AtomicBoolean(false);
        return new Iterator<>() {

            @Override
            public boolean hasNext() {
                if (isClosed.get())
                    return false;

                var n = f.hasNext();

                if (n)
                    return true;

                isClosed.set(true);
                walk.close();
                return false;
            }

            @Override
            public @Nullable Path next() {
                try {
                    var p = f.next();
                    return p;
                } catch (
                        NoSuchElementException e) {
                    isClosed.set(true);
                    walk.close();
                    throw e;
                }

            }
        };
    }

    public static Stream<File> getFileStream(Path path) {
        return Optional.ofNullable(path.toFile().listFiles())
                .stream()
                .flatMap(Arrays::stream);
    }

    public static boolean isEmpty(Path path) {
        return getFileStream(path).findAny().isEmpty();
    }

    public static boolean deleteFilesRecursive(Path path) {
        Path dir = path.normalize();
        if (!dir.startsWith(path)) {
            log.warn("Skipping delete outside allowed dir: {}", dir);
            return false;
        }
        return deleteFilesRecursive(dir, s -> true);
    }

    public static boolean deleteFilesRecursive(Path target, Predicate<Path> doDelete) {
        return deleteFilesRecursive(target, doDelete, r -> false);
    }

    public static boolean deleteFilesRecursive(Path target, Predicate<Path> doDelete, Predicate<Path> skipTree) {
        try {
            Files.walkFileTree(target, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (Files.isSymbolicLink(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (dir.toFile().isDirectory() && skipTree.test(dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public @NotNull FileVisitResult postVisitDirectory(@NotNull Path dir, @Nullable IOException exc) throws IOException {
                    if (doDelete.test(dir))
                        Files.deleteIfExists(dir);

                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if ((file.toFile().isFile() || Files.isSymbolicLink(file)) && doDelete.test(file)) {
                        Files.deleteIfExists(file);
                    }

                    return FileVisitResult.CONTINUE;
                }
            });
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void logNotDeleted(Path p) {
        log.error("Could not delete {}.", p);
    }

    public static @Nonnull Result<String, SingleError> readToString(File f) {
        try (
                FileReader fileReader = new FileReader(f);
                BufferedReader bf = new BufferedReader(fileReader)
        ) {
            return Result.ok(bf.lines().collect(Collectors.joining(System.lineSeparator())));
        } catch (IOException e) {
            return Result.err(SingleError.fromE(e));
        }
    }

    public static String randomFilename(String appendTo) {
        return "%s-%s".formatted(UUID.randomUUID().toString().replaceAll("-", ""), appendTo);
    }

    public static Result<Boolean, FileError> doesFileMatchContent(File f, String content) {
        return FileUtils.readToLazyIterator(f)
                .map(iter -> {
                    int i = 0;
                    String[] array;
                    if (content == null)
                        array = new String[]{null};
                    else
                        array = content.split(System.lineSeparator());

                    while (iter.hasNext()) {
                        var first = iter.next();
                        if (first == null) {
                            log.error("Error reading from lazy iterator.");
                        }

                        var second = array[i];
                        if (!Objects.equals(first, second)) {
                            iter.doClose();
                            return false;
                        }
                        i += 1;
                    }


                    return true;
                })
                .mapError(se -> new FileError(se.getMessage()));
    }

    public record LazyIterator(
            @Delegate Iterator<String> iter,
            BufferedReader reader,
            boolean[] isClosed)
            implements Iterator<String> {

        @SneakyThrows
        public void doClose() {
            reader.close();
            ClosableResult.registerClosed(reader);
            isClosed[0] = true;
        }

    }

    public static @Nonnull Result<LazyIterator, SingleError> readToLazyIterator(File f) {
        if (!f.exists())
            return Result.err(SingleError.fromMessage("File %s did not exist.".formatted(f.getAbsolutePath())));

        return Result.<BufferedReader, SingleError>tryFrom(() -> Files.newBufferedReader(f.toPath()))
                .except(exc -> {
                    throw new RuntimeException(exc);
                })
                .map(bfr -> {
                    final boolean[] isClosed = {false};
                    Iterator<String> iter = new Iterator<>() {

                        @SneakyThrows
                        private void doPerformClose() {
                            if (!isClosed[0]) {
                                bfr.close();
                                ClosableResult.registerClosed(bfr);
                                isClosed[0] = true;
                            }
                        }

                        String nextLine = null;

                        @SneakyThrows
                        @Override
                        public boolean hasNext() {
                            if (isClosed[0])
                                return false;

                            if (nextLine != null) {
                                return true;
                            } else {
                                nextLine = bfr.readLine();
                                var hasNext = (nextLine != null);

                                if (!hasNext) {
                                    doPerformClose();
                                }

                                return hasNext;
                            }
                        }

                        @SneakyThrows
                        @Override
                        public String next() {
                            if (isClosed[0]) {
                                log.error("Called next when no more elements.");
                                return null;
                            }

                            if (nextLine != null || hasNext()) {
                                String line = nextLine;
                                nextLine = null;
                                return line;
                            } else {
                                doPerformClose();
                                return null;
                            }
                        }
                    };

                    return new LazyIterator(iter, bfr, isClosed);
                });

    }

    public static Path retrieveParentDirectoryMatching(Predicate<Path> toMatch, Path starting) {
        starting = starting.toAbsolutePath();
        if (toMatch.test(starting))
            return starting;

        if (isRoot(starting))
            return null;

        while (!isRoot(starting)) {
            starting = starting.getParent();
            if (toMatch.test(starting))
                return starting;
        }

        return null;
    }

    public static boolean hasParentDirectoryMatching(Predicate<Path> toMatch, Path starting) {
        return retrieveParentDirectoryMatching(toMatch, starting.toAbsolutePath()) != null;
    }

    public static boolean isRoot(Path starting) {
        return starting.getParent() == null || starting.getParent().equals(starting) || starting.getParent().equals(starting.getRoot());
    }

}
