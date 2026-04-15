package com.hayden.acp_cdc_ai.sandbox;

import jakarta.annotation.Nullable;

import java.util.List;
import java.util.Map;

/**
 * Represents the translated sandbox configuration for an ACP provider.
 * 
 * @param env Environment variables to pass to the process
 * @param args Command line arguments to append to the command
 * @param workingDirectory The working directory for the session (used for session cwd parameter)
 */
public record SandboxTranslation(
        Map<String, String> env,
        List<String> args,
        String workingDirectory,
        @Nullable String model
) {

    /**
     * Constructor for backward compatibility without workingDirectory.
     */
    public SandboxTranslation(Map<String, String> env, List<String> args, String model) {
        this(env, args, null, model);
    }

    public SandboxTranslation(Map<String, String> env, List<String> args) {
        this(env, args, null, null);
    }

    public static SandboxTranslation empty() {
        return new SandboxTranslation(Map.of(), List.of(), null);
    }
}
