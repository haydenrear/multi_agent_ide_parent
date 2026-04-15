package com.hayden.acp_cdc_ai.sandbox;

import lombok.Builder;

import java.nio.file.Path;
import java.util.List;

@Builder(toBuilder = true)
public record SandboxContext(
        Path mainWorktreePath,
        List<Path> submoduleWorktreePaths
) {
}
