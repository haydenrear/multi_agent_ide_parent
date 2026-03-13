package com.hayden.multiagentide.integration;

import com.hayden.multiagentide.artifacts.ArtifactNode;
import com.hayden.multiagentide.artifacts.ArtifactTreeBuilder;
import com.hayden.multiagentide.artifacts.repository.ArtifactEntity;
import com.hayden.multiagentide.artifacts.repository.ArtifactRepository;
import com.hayden.multiagentide.graph.repository.GraphEntity;
import com.hayden.multiagentide.graph.repository.GraphRepository;
import com.hayden.multiagentidelib.artifact.PromptTemplateVersion;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for artifact tree integration tests.
 * Provides common setup, cleanup, and assertion utilities.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "testdocker"})
@Transactional
public abstract class ArtifactTreeIntegrationTestBase {

    @Autowired
    protected GraphRepository graphRepository;

    @Autowired
    protected ArtifactRepository artifactRepository;

    @Autowired
    protected EntityManager entityManager;

    @Autowired
    protected ArtifactTreeBuilder artifactTreeBuilder;

    /**
     * Clears all artifact and graph data.
     */
    protected void clearArtifactData() {
        artifactRepository.deleteAll();
        graphRepository.deleteAll();
        entityManager.flush();
    }

    /**
     * Asserts that an artifact exists in the database with the given key.
     */
    protected ArtifactEntity assertArtifactExists(ArtifactKey key) {
        Optional<ArtifactEntity> artifact = artifactRepository.findByArtifactKey(key.value());
        assertThat(artifact).as("Artifact with key %s should exist", key.value())
                .isPresent();
        return artifact.get();
    }

    /**
     * Asserts that an artifact with the given parent key exists.
     */
    protected void assertArtifactHasParent(ArtifactKey childKey, ArtifactKey parentKey) {
        ArtifactEntity child = assertArtifactExists(childKey);
        assertThat(child.getParentKey()).as("Artifact %s should have parent %s", childKey.value(), parentKey.value())
                .isEqualTo(parentKey.value());
    }

    /**
     * Asserts that no orphaned artifacts exist in the tree.
     * An orphan is a node whose parent key is not null but parent doesn't exist in DB.
     */
    protected void assertNoOrphanedArtifacts() {
        List<ArtifactEntity> artifacts = artifactRepository.findAll();
        for (ArtifactEntity artifact : artifacts) {
            if (artifact.getParentKey() != null && !artifact.getParentKey().isEmpty()) {
                Optional<ArtifactEntity> parent = artifactRepository.findByArtifactKey(artifact.getParentKey());
                assertThat(parent).as("Parent of artifact %s should exist", artifact.getArtifactKey())
                        .isPresent();
            }
        }
    }

    /**
     * Asserts that a graph entity exists for the given key.
     */
    protected GraphEntity assertGraphEntityExists(String key) {
        Optional<GraphEntity> entity = graphRepository.findById(key);
        assertThat(entity).as("Graph entity with key %s should exist", key)
                .isPresent();
        return entity.get();
    }

    /**
     * Asserts that content hash deduplication is working.
     * Only one artifact should exist with the given content hash.
     */
    protected void assertContentHashIsUnique(String contentHash) {
        List<ArtifactEntity> artifacts = artifactRepository.findAll();
        long count = artifacts.stream()
                .filter(a -> contentHash.equals(a.getContentHash()))
                .count();
        assertThat(count).as("Content hash %s should be unique (found %d artifacts)", contentHash, count)
                .isLessThanOrEqualTo(1);
    }

    /**
     * Asserts that the artifact tree has the expected depth.
     */
    protected void assertTreeDepth(ArtifactKey rootKey, int expectedDepth) {
        Optional<ArtifactNode> root = artifactTreeBuilder.getExecutionTree(rootKey.value());
        assertThat(root).isPresent();
        
        int actualDepth = calculateTreeDepth(root.get());
        assertThat(actualDepth).as("Tree rooted at %s should have depth %d", rootKey.value(), expectedDepth)
                .isEqualTo(expectedDepth);
    }

    /**
     * Calculates the maximum depth of a tree rooted at the given node.
     */
    private int calculateTreeDepth(ArtifactNode node) {
        if (node.getChildren().isEmpty()) {
            return 0;
        }
        return 1 + node.getChildren().values().stream()
                .mapToInt(this::calculateTreeDepth)
                .max()
                .orElse(0);
    }

    /**
     * Asserts that the artifact tree has the expected number of nodes.
     */
    protected void assertTreeNodeCount(ArtifactKey rootKey, int expectedCount) {
        Optional<ArtifactNode> root = artifactTreeBuilder.getExecutionTree(rootKey.value());
        assertThat(root).isPresent();
        
        int actualCount = countTreeNodes(root.get());
        assertThat(actualCount).as("Tree rooted at %s should have %d nodes", rootKey.value(), expectedCount)
                .isEqualTo(expectedCount);
    }

    /**
     * Counts total nodes in a tree rooted at the given node.
     */
    private int countTreeNodes(ArtifactNode node) {
        return 1 + node.getChildren().values().stream()
                .mapToInt(this::countTreeNodes)
                .sum();
    }
}
