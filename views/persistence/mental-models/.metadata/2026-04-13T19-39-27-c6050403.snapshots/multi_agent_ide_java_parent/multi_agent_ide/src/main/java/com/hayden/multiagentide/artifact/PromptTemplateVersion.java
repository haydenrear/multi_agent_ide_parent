package com.hayden.multiagentide.artifact;

import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactHashing;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Templated;
import lombok.Builder;
import lombok.With;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Represents a versioned static prompt template reused across many executions.
 * <p>
 * Template version identity is the tuple (templateStaticId, contentHash).
 * The templateArtifactKey is stable for a given version and changes only
 * when the template text changes.
 */
@Builder(toBuilder = true)
@With
public record PromptTemplateVersion(
        String templateStaticId,
        String templateText,
        String hash,
        ArtifactKey templateArtifactKey,
        Instant lastUpdatedAt,
        List<Artifact> children
) implements Templated {

    @Override
    public Templated withArtifactKey(ArtifactKey key) {
        return withTemplateArtifactKey(key);
    }


    // Validation pattern for template static IDs
    private static final Pattern STATIC_ID_PATTERN = Pattern.compile(
            "^tpl\\.([a-z][a-z0-9_]{0,63}\\.){1,7}[a-z][a-z0-9_]{0,63}$"
    );

    // Pattern for Jinja2-style template variables: {{ variable }}
    private static final Pattern JINJA_PATTERN = Pattern.compile("\\{\\{\\s*\\w+\\s*\\}\\}");

    /**
     * Creates a new PromptTemplateVersion from template text.
     * Computes the content hash and creates a stable artifact key.
     */
    public static PromptTemplateVersion create(
            String templateStaticId,
            String templateText,
            String sourceLocation
    ) {
        validateStaticId(templateStaticId);

        String hash = ArtifactHashing.hashText(templateText);
        Instant now = Instant.now();
        ArtifactKey artifactKey = ArtifactKey.createRoot(now);

        return new PromptTemplateVersion(
                templateStaticId,
                templateText,
                hash,
                artifactKey,
                now,
                new ArrayList<>()
        );
    }

    /**
     * Creates a PromptTemplateVersion with an existing artifact key.
     * Used when loading from persistence.
     */
    public static PromptTemplateVersion fromPersisted(
            String templateStaticId,
            String templateText,
            String contentHash,
            ArtifactKey templateArtifactKey,
            Instant lastUpdatedAt
    ) {
        return new PromptTemplateVersion(
                templateStaticId,
                templateText,
                contentHash,
                templateArtifactKey,
                lastUpdatedAt,
                new ArrayList<>()
        );
    }

    @Override
    public boolean hasUnresolvedPlaceholders() {
        if (templateText == null) {
            return false;
        }
        return JINJA_PATTERN.matcher(templateText).find();
    }

    @Override
    public ArtifactKey artifactKey() {
        return templateArtifactKey;
    }

    @Override
    public Optional<String> contentHash() {
        return Optional.ofNullable(hash);
    }

    @Override
    public Map<String, String> metadata() {
        return Map.of();
    }


    /**
     * Validates a template static ID format.
     */
    public static void validateStaticId(String staticId) {
        if (staticId == null || staticId.isEmpty()) {
            throw new IllegalArgumentException("Template static ID cannot be null or empty");
        }
        if (!STATIC_ID_PATTERN.matcher(staticId).matches()) {
            throw new IllegalArgumentException(
                    "Invalid template static ID format: " + staticId +
                            ". Must match pattern: tpl.<category>.<name>");
        }
    }

    /**
     * Checks if a static ID is valid without throwing.
     */
    public static boolean isValidStaticId(String staticId) {
        if (staticId == null || staticId.isEmpty()) {
            return false;
        }
        return STATIC_ID_PATTERN.matcher(staticId).matches();
    }

    /**
     * Extracts the template family prefix (parent path in hierarchy).
     * e.g., "tpl.agent.discovery.system_prompt" -> "tpl.agent.discovery"
     */
    public String templateFamily() {
        if (templateStaticId == null) {
            return null;
        }
        int lastDot = templateStaticId.lastIndexOf('.');
        if (lastDot <= 4) { // "tpl." is 4 chars
            return templateStaticId;
        }
        return templateStaticId.substring(0, lastDot);
    }
}
