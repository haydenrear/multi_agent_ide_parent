package com.hayden.utilitymodule.git;

import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.TreeFilter;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.Objects;

public class WildcardPathFilter extends TreeFilter {

    private final PathMatcher matcher;
    private final String name;
    private final Path baseDir;

    public WildcardPathFilter(String pattern, Path baseDir) {
        this.matcher = FileSystems.getDefault().getPathMatcher("glob:" + pattern);
        this.name = pattern;
        this.baseDir = baseDir;
    }

    @Override
    public boolean include(TreeWalk walker) {
        Path path = baseDir.resolve(Paths.get(walker.getPathString())).toAbsolutePath();
        boolean matchesPath = matcher.matches(path);
        return Objects.equals(path.toFile().getName(), name) || matchesPath; // Exclude matching paths
    }

    @Override
    public boolean shouldBeRecursive() {
        return true;
    }

    @Override
    public TreeFilter clone() {
        return new WildcardPathFilter(matcher.toString(), this.baseDir);
    }
}
