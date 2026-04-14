package com.hayden.multiagentide.artifacts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.multiagentide.artifacts.entity.ArtifactEntity;
import com.hayden.multiagentide.artifacts.repository.ArtifactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds and persists artifact trees from artifact events.
 *
 * Uses a trie-like structure where:
 * - Artifacts are inserted at positions determined by their hierarchical key
 * - Deduplication happens automatically via content hash comparison among siblings
 * - Messages come in order (no level skipping), so no splitting required
 * - Nodes are never removed
 *
 * Responsibilities:
 * - Maintains in-memory trie structure during execution
 * - Handles hash-based deduplication at insert time
 * - Converts Artifact objects to ArtifactEntity for persistence
 * - Handles batched persistence for performance
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ArtifactTreeBuilder {

    private final ArtifactRepository artifactRepository;
    private final ObjectMapper objectMapper;
    private final ArtifactService artifactService;

    private final Map<String, ArtifactNode> executionTrees = new ConcurrentHashMap<>();

    /**
     * Adds an artifact to the tree using trie-based insertion with hash deduplication.
     *
     * The artifact is inserted at the position determined by its hierarchical key.
     * Before insertion, siblings are checked for matching content hashes to enable
     * automatic deduplication.
     *
     * @param artifact The artifact to add
     * @return true if added successfully, false if duplicate (key or hash)
     */
    public boolean addArtifact(com.hayden.acp_cdc_ai.acp.events.Artifact artifact) {
        var executionKey = getInsertionExecutionKey(artifact);

        return executionKey.map(e -> addArtifactResult(e, artifact) == ArtifactNode.AddResult.ADDED)
                .orElse(false);
    }

    public boolean addArtifact(String executionKey, com.hayden.acp_cdc_ai.acp.events.Artifact artifact) {
        return addArtifactResult(executionKey, artifact) == ArtifactNode.AddResult.ADDED;
    }

    public ArtifactNode.AddResult addArtifactResult(String executionKey, com.hayden.acp_cdc_ai.acp.events.Artifact artifact) {
        com.hayden.acp_cdc_ai.acp.events.ArtifactKey key = artifact.artifactKey();

        // Get or create the execution tree
        ArtifactNode root = executionTrees.get(executionKey);

        if (root == null) {
            // This is the first artifact - it should be the root
            if (key.isRoot() || key.depth() == 1) {
                root = ArtifactNode.createRoot(artifact);
                executionTrees.put(executionKey, root);
                log.debug("Created execution tree root: {}", key);
                return ArtifactNode.AddResult.ADDED;
            } else {
                log.debug("First artifact for execution {} is not a root yet: {}", executionKey, key);
                return ArtifactNode.AddResult.PARENT_NOT_FOUND;
            }
        }

        // Add to the trie structure
        ArtifactNode.AddResult result = root.addArtifact(artifact);

        switch (result) {
            case ADDED -> {
                log.debug("Added artifact: {} (type: {})", key, artifact.artifactType());
                return ArtifactNode.AddResult.ADDED;
            }
            case DUPLICATE_KEY -> {
                log.debug("Duplicate artifact key in execution {}: {}", executionKey, key);
                return ArtifactNode.AddResult.DUPLICATE_KEY;
            }
            case DUPLICATE_HASH -> {
                log.debug("Skipped duplicate hash for artifact: {}", key);
                return ArtifactNode.AddResult.DUPLICATE_HASH;
            }
            case PARENT_NOT_FOUND -> {
                log.debug("Parent not found for artifact: {}", key);
                return ArtifactNode.AddResult.PARENT_NOT_FOUND;
            }
            default -> {
                log.error("Unexpected add result: {}", result);
                return result;
            }
        }
    }



    private Optional<String> getInsertionExecutionKey(Artifact artifact) {
        return executionTrees.keySet().stream().filter(s -> artifact.artifactKey().value().startsWith(s))
                .max(Comparator.comparing(String::length));
    }

    /**
     * Gets an artifact from the in-memory tree.
     */
    public Optional<com.hayden.acp_cdc_ai.acp.events.Artifact> getArtifact(String executionKey, String artifactKeyStr) {
        ArtifactNode root = executionTrees.get(executionKey);
        if (root == null) {
            return Optional.empty();
        }

        try {
            com.hayden.acp_cdc_ai.acp.events.ArtifactKey artifactKey = new com.hayden.acp_cdc_ai.acp.events.ArtifactKey(artifactKeyStr);
            ArtifactNode node = root.findNode(artifactKey);
            return node != null ? Optional.of(node.getArtifact()) : Optional.empty();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid artifact key format: {}", artifactKeyStr);
            return Optional.empty();
        }
    }

    /**
     * Gets all artifacts in an execution (in-memory).
     */
    public Collection<com.hayden.acp_cdc_ai.acp.events.Artifact> getExecutionArtifacts(String executionKey) {
        ArtifactNode root = executionTrees.get(executionKey);
        if (root == null) {
            return Collections.emptyList();
        }
        return root.collectAll();
    }

    /**
     * Gets the root node of an execution tree.
     */
    public Optional<ArtifactNode> getExecutionTree(String executionKey) {
        return Optional.ofNullable(executionTrees.get(executionKey));
    }

    public Optional<com.hayden.acp_cdc_ai.acp.events.Artifact> buildRemoveArtifactTree(String executionKey) {
        ArtifactNode root = executionTrees.remove(executionKey);
        if (root == null) {
            return Optional.empty();
        }
        return Optional.of(root.buildArtifactTree());
    }

    /**
     * Builds and returns the artifact tree for an execution.
     * Returns the root artifact with all children populated recursively.
     *
     * @param executionKey The execution key
     * @return The root artifact with children, or empty if no tree exists
     */
    public Optional<com.hayden.acp_cdc_ai.acp.events.Artifact> buildArtifactTree(String executionKey) {
        ArtifactNode root = executionTrees.get(executionKey);
        if (root == null) {
            return Optional.empty();
        }
        return Optional.of(root.buildArtifactTree());
    }

    /**
     * Checks if a sibling with the given hash exists under a parent key.
     */
    public boolean hasSiblingWithHash(String executionKey, com.hayden.acp_cdc_ai.acp.events.ArtifactKey parentKey, String contentHash) {
        ArtifactNode root = executionTrees.get(executionKey);
        if (root == null) {
            return false;
        }

        ArtifactNode parentNode = root.findNode(parentKey);
        if (parentNode == null) {
            return false;
        }

        return parentNode.hasSiblingWithHash(contentHash);
    }

    public Optional<com.hayden.acp_cdc_ai.acp.events.Artifact> persistRemoveExecution(String executionKey) {
        var toRemove = persistExecutionTree(executionKey);
        if (toRemove.isPresent()) {
            this.executionTrees.remove(executionKey);
        }
        return toRemove;
    }

    /**
     * Marks an execution as finished and persists the artifact tree.
     * This is the primary way to complete an execution and persist its artifacts.
     *
     * @param executionKey The execution key
     * @return The root artifact with all children populated, or empty if no tree exists
     */
    @Transactional
    public Optional<com.hayden.acp_cdc_ai.acp.events.Artifact> persistExecutionTree(String executionKey) {
        ArtifactNode root = executionTrees.get(executionKey);
        if (root == null) {
            log.warn("No execution tree found for key: {}", executionKey);
            return Optional.empty();
        }

        artifactService.doPersist(executionKey, root);
        return Optional.of(root.buildArtifactTree());
    }

    /**
     * Persists all pending artifacts for an execution without clearing state.
     */
    @Transactional
    public void persistExecution(String executionKey) {
        ArtifactNode root = executionTrees.get(executionKey);
        if (root == null) {
            log.warn("No execution tree found for key: {}", executionKey);
            return;
        }

        artifactService.doPersist(executionKey, root);
    }


    /**
     * Clears the in-memory state for an execution.
     * Call this only when you're done with the execution and want to free memory.
     */
    public void clearExecution(String executionKey) {
        executionTrees.remove(executionKey);
        log.debug("Cleared execution state for: {}", executionKey);
    }


    /**
     * Loads an execution tree from the database.
     */
    @Transactional(readOnly = true)
    public Optional<com.hayden.acp_cdc_ai.acp.events.Artifact> loadExecution(String executionKey) {
        List<ArtifactEntity> entities = artifactRepository.findByExecutionKeyOrderByArtifactKey(executionKey);
        if (entities.isEmpty()) {
            return Optional.empty();
        }

        return rebuildTree(entities);
    }

    /**
     * Persists a shared artifact (e.g., template version).
     */
    @Transactional
    public ArtifactEntity persistSharedArtifact(ArtifactEntity entity) {
        entity.setShared(true);
        return artifactRepository.save(entity);
    }

    // ========== Private Helpers ==========

    private Optional<com.hayden.acp_cdc_ai.acp.events.Artifact> rebuildTree(List<ArtifactEntity> entities) {
        if (entities.isEmpty()) {
            return Optional.empty();
        }

        // Parse all artifacts
        Map<String, com.hayden.acp_cdc_ai.acp.events.Artifact> artifactMap = new LinkedHashMap<>();
        for (ArtifactEntity entity : entities) {
            com.hayden.acp_cdc_ai.acp.events.Artifact artifact = deserializeArtifact(entity);
            if (artifact != null) {
                artifactMap.put(entity.getArtifactKey(), artifact);
            }
        }

        // Find root (depth 1)
        return artifactMap.values().stream()
                .filter(a -> a.artifactKey().isRoot())
                .findFirst();
    }

    private com.hayden.acp_cdc_ai.acp.events.Artifact deserializeArtifact(ArtifactEntity entity) {
        String type = entity.getArtifactType();
        try {
            return objectMapper.readValue(entity.getContentJson(), com.hayden.acp_cdc_ai.acp.events.Artifact.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize artifact: {} (type: {})", entity.getArtifactKey(), type, e);
            return null;
        }
    }
}
