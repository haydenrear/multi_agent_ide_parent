package com.hayden.multiagentide.artifacts;

import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.utilitymodule.stream.StreamUtil;
import jakarta.annotation.Nullable;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Trie node for the artifact tree structure.
 */
@Slf4j
public class ArtifactNode {

    @Getter
    private final ArtifactKey artifactKey;

    private final ArtifactNode parent;

    @Getter
    private Artifact artifact;

    private final Map<String, ArtifactNode> children = new ConcurrentHashMap<>();
    private final Set<String> childContentHashes = ConcurrentHashMap.newKeySet();

    public ArtifactNode(ArtifactKey artifactKey, Artifact artifact) {
        this(artifactKey, artifact, null);
    }

    public ArtifactNode(ArtifactKey artifactKey, Artifact artifact, ArtifactNode parent) {
        this.artifactKey = artifactKey;
        this.parent = parent;
        this.artifact = artifact;

        StreamUtil.toStream(artifact)
                .flatMap(a -> StreamUtil.toStream(a.children()))
                .forEach(child -> {
                    ArtifactNode childNode = new ArtifactNode(child.artifactKey(), child, this);
                    children.put(child.artifactKey().value(), childNode);
                    child.contentHash().ifPresent(childContentHashes::add);
                });
        syncChildrenToArtifact();
    }

    public static ArtifactNode createRoot(Artifact rootArtifact) {
        return new ArtifactNode(rootArtifact.artifactKey(), rootArtifact, null);
    }

    public synchronized AddResult addArtifact(Artifact artifact) {
        ArtifactKey key = artifact.artifactKey();

        boolean hashesEqual = Objects.equals(artifact.contentHash().orElse(""), this.artifact.contentHash().orElse(""));
        boolean sameKeys = key.equals(this.artifactKey);
        if (sameKeys && this.artifact instanceof Artifact.IntermediateArtifact) {
            replaceIntermediate(artifact);
            return AddResult.ADDED;
        } else if (sameKeys && hashesEqual) {
            return AddResult.DUPLICATE_KEY;
        } else if (sameKeys) {
            return addArtifact(artifact.withArtifactKey(this.artifactKey.createChild()));
        }

        Optional<ArtifactKey> parentKeyOpt = key.parent();
        if (parentKeyOpt.isEmpty()) {
            log.warn("Attempted to add root artifact when root already exists: {}", key);
            return AddResult.DUPLICATE_KEY;
        }

        ArtifactKey parentKey = parentKeyOpt.get();
        ArtifactNode parentNode = findNode(parentKey);
        if (parentNode == null) {
            parentNode = ensureIntermediatePath(parentKey, artifact);
        }
        if (parentNode == null) {
            log.debug("Parent node not found for artifact: {} (child type: {}, expected parent: {}, nearest ancestor type: {})",
                    key,
                    artifact.artifactType(),
                    parentKey,
                    nearestAncestorType(parentKey));
            return AddResult.PARENT_NOT_FOUND;
        }

        return parentNode.addChild(artifact);
    }

    private synchronized AddResult addChild(Artifact childArtifact) {
        String keyValue = childArtifact.artifactKey().value();
        ArtifactNode existingNode = children.get(keyValue);

        if (existingNode != null) {
            if (existingNode.artifact instanceof Artifact.IntermediateArtifact) {
                log.debug("Replacing placeholder child {} with {} under parent type {}",
                        keyValue,
                        childArtifact.artifactType(),
                        artifact.artifactType());
                existingNode.replaceIntermediate(childArtifact);
                syncChildrenToArtifact();
                return AddResult.ADDED;
            }
            log.debug("Duplicate key rejected: {}", keyValue);
            return AddResult.DUPLICATE_KEY;
        }

        Optional<String> contentHash = childArtifact.contentHash();
        if (contentHash.isPresent() && childContentHashes.contains(contentHash.get())) {
            log.debug("Duplicate hash accepted for key {}: {}", keyValue, contentHash.get());
        }

        ArtifactNode childNode = new ArtifactNode(childArtifact.artifactKey(), childArtifact, this);
        children.put(keyValue, childNode);
        contentHash.ifPresent(childContentHashes::add);
        syncChildrenToArtifact();

        log.trace("Added artifact: {} (hash: {})", keyValue, contentHash.orElse("none"));
        return AddResult.ADDED;
    }

    private synchronized ArtifactNode ensureIntermediatePath(ArtifactKey targetKey, Artifact requestedArtifact) {
        if (targetKey.equals(this.artifactKey)) {
            return this;
        }

        Optional<ArtifactKey> parentKey = targetKey.parent();
        if (parentKey.isEmpty()) {
            return null;
        }

        ArtifactNode parentNode = findNode(parentKey.get());
        if (parentNode == null) {
            parentNode = ensureIntermediatePath(parentKey.get(), requestedArtifact);
        }
        if (parentNode == null) {
            return null;
        }

        ArtifactNode existingNode = parentNode.findDirectChild(targetKey);
        if (existingNode != null) {
            return existingNode;
        }

        Artifact.IntermediateArtifact intermediateArtifact = Artifact.IntermediateArtifact.builder()
                .artifactKey(targetKey)
                .metadata(new LinkedHashMap<>())
                .children(new ArrayList<>())
                .expectedArtifactType(null)
                .build();
        log.debug("Creating intermediate artifact placeholder for {} under parent type {} while adding child type {}",
                targetKey.value(),
                parentNode.getArtifact().artifactType(),
                requestedArtifact.artifactType());
        AddResult result = parentNode.addChild(intermediateArtifact);
        if (result == AddResult.ADDED || result == AddResult.DUPLICATE_KEY) {
            return parentNode.findDirectChild(targetKey);
        }
        return null;
    }

    private synchronized ArtifactNode findDirectChild(ArtifactKey targetKey) {
        return children.get(targetKey.value());
    }

    private synchronized String nearestAncestorType(ArtifactKey targetKey) {
        ArtifactNode current = findNode(targetKey);
        ArtifactKey cursor = targetKey;
        while (current == null && cursor.parent().isPresent()) {
            cursor = cursor.parent().get();
            current = findNode(cursor);
        }
        return current == null ? "NONE" : current.getArtifact().artifactType();
    }

    private synchronized void replaceIntermediate(Artifact replacementArtifact) {
        log.debug("Replacing intermediate artifact {} with {} (parent type: {})",
                artifactKey.value(),
                replacementArtifact.artifactType(),
                parent == null ? "ROOT" : parent.getArtifact().artifactType());
        for (Artifact directChild : StreamUtil.toStream(replacementArtifact.children()).toList()) {
            ArtifactNode existingChild = findDirectChild(directChild.artifactKey());
            if (existingChild == null) {
                log.debug("Attaching child {} ({}) beneath placeholder {}",
                        directChild.artifactKey().value(),
                        directChild.artifactType(),
                        artifactKey.value());
                children.put(directChild.artifactKey().value(), new ArtifactNode(directChild.artifactKey(), directChild, this));
            } else if (existingChild.getArtifact() instanceof Artifact.IntermediateArtifact) {
                log.debug("Replacing nested placeholder child {} with {} beneath {}",
                        directChild.artifactKey().value(),
                        directChild.artifactType(),
                        artifactKey.value());
                existingChild.replaceIntermediate(directChild);
            }
            directChild.contentHash().ifPresent(childContentHashes::add);
        }

        this.artifact = replacementArtifact.withChildren(sortedChildArtifacts());
        if (parent != null) {
            parent.syncChildrenToArtifact();
        }
    }

    public synchronized @Nullable ArtifactNode findNode(ArtifactKey targetKey) {
        if (StringUtils.isEmpty(targetKey.value())) {
            return null;
        }
        if (targetKey.equals(this.artifactKey)) {
            return this;
        }
        if (!targetKey.value().startsWith(this.artifactKey.value())) {
            return null;
        }

        for (ArtifactNode child : children.values()) {
            if (targetKey.equals(child.artifactKey)) {
                return child;
            }
            if (targetKey.value().startsWith(child.artifactKey.value() + "/")) {
                ArtifactNode found = child.findNode(targetKey);
                if (found != null) {
                    return found;
                }
            }
        }

        return null;
    }

    public synchronized boolean hasSiblingWithHash(String contentHash) {
        return childContentHashes.contains(contentHash);
    }

    public synchronized Collection<ArtifactNode> getChildren() {
        return Collections.unmodifiableCollection(children.values());
    }

    public synchronized Artifact buildArtifactTree() {
        syncChildrenToArtifact();
        return artifact;
    }

    public synchronized List<Artifact> collectAll() {
        List<Artifact> result = new ArrayList<>();
        collectAllRecursive(result);
        return result;
    }

    private synchronized void collectAllRecursive(List<Artifact> accumulator) {
        accumulator.add(artifact);
        for (ArtifactNode child : children.values()) {
            child.collectAllRecursive(accumulator);
        }
    }

    public synchronized int size() {
        int count = 1;
        for (ArtifactNode child : children.values()) {
            count += child.size();
        }
        return count;
    }

    private synchronized void syncChildrenToArtifact() {
        this.artifact = this.artifact.withChildren(sortedChildArtifacts());
    }

    private synchronized List<Artifact> sortedChildArtifacts() {
        return children.values().stream()
                .sorted(Comparator.comparing(node -> node.artifactKey.value()))
                .map(ArtifactNode::getArtifact)
                .toList();
    }

    public enum AddResult {
        ADDED,
        DUPLICATE_KEY,
        DUPLICATE_HASH,
        PARENT_NOT_FOUND
    }
}
