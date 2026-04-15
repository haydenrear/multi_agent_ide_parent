package com.hayden.acp_cdc_ai.acp.config;

import java.util.Locale;

public enum AcpProvider {
    CLAUDE,
    CODEX,
    CLAUDE_OPENROUTER,
    CLAUDE_LLAMA;

    /**
     * The config/profile key for this provider (e.g. "claude", "codex").
     * Used in YAML keys and Spring profile names.
     */
    public String wireValue() {
        return switch (this) {
            case CLAUDE -> "claude";
            case CODEX -> "codex";
            case CLAUDE_OPENROUTER -> "claudeopenrouter";
            case CLAUDE_LLAMA -> "claudellama";
        };
    }

    /**
     * The ACP sandbox command key for this provider (e.g. "claude-agent-acp", "codex-acp").
     * Matches the value returned by {@link com.hayden.acp_cdc_ai.sandbox.SandboxTranslationStrategy#providerKey()}.
     */
    public String providerKey() {
        return switch (this) {
            case CLAUDE, CLAUDE_OPENROUTER, CLAUDE_LLAMA -> "claude-agent-acp";
            case CODEX -> "codex-acp";
        };
    }

    public static AcpProvider fromWireValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "claude" -> CLAUDE;
            case "codex" -> CODEX;
            case "claudeopenrouter", "claude-openrouter", "claude_openrouter" -> CLAUDE_OPENROUTER;
            case "claudellama", "claude-llama", "claude_llama" -> CLAUDE_LLAMA;
            default -> throw new IllegalArgumentException("Unknown ACP provider: " + value);
        };
    }
}
