package com.hayden.multiagentide.artifacts;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.artifacts.entity.ArtifactEntity;
import com.hayden.multiagentide.artifacts.repository.ArtifactRepository;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Templated;
import com.hayden.multiagentide.config.SerdesConfiguration;
import com.hayden.utilitymodule.stream.StreamUtil;
import io.micrometer.common.util.StringUtils;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.PersistenceException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service for managing artifact persistence and retrieval.
 * Provides methods for:
 * - Hash-based artifact deduplication
 * - JSON serialization/deserialization of artifacts
 * - Artifact retrieval from the repository
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArtifactService {

    private final ArtifactRepository artifactRepository;

    private ObjectMapper objectMapper;

    private EventBus eventBus;

    @Autowired
    @Lazy
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @PostConstruct
    public void configure() {
        var j = new Jackson2ObjectMapperBuilder();
        SerdesConfiguration.artifactAndAgentModelMixIn().customize(j);

        this.objectMapper = j.build();
    }


    @Transactional
    public void doPersist(String executionKey, ArtifactNode root) {
        var collected = new ArrayList<>(root.collectAll());

        var groupedByKey = collected.stream()
                .filter(a -> a.artifactKey() != null && StringUtils.isNotBlank(a.artifactKey().value()))
                .collect(Collectors.groupingBy(Artifact::artifactKey));

        // records are special
        Map<Artifact, Artifact> updates = new IdentityHashMap<>();

        for (var entry : groupedByKey.entrySet()) {
            var list = entry.getValue();
            updates.put(list.getFirst(), list.getFirst());

            if (list.size() <= 1)
                continue;

            // decide allSameHash using “present+nonblank” logic
            if (!entry.getValue().stream().allMatch(art -> art.contentHash().orElse("").equals(list.getFirst().contentHash().orElse("")))) {
                for (int i = 1; i < list.size(); i++) {
                    var e = list.get(i);
                    updates.put(e, e.withArtifactKey(e.artifactKey().createChild()));
                }
            }
        }

        var allArtifacts = new ArrayList<>(updates.values());

        // Group by content hash to find duplicates
        var groupedByHash = allArtifacts.stream()
                .map(a -> {
                    if (a.contentHash().isPresent() && StringUtils.isNotBlank(a.contentHash().get()))
                        return a;

                    return a.withHash(UUID.randomUUID().toString());
                })
                .collect(Collectors.groupingBy(a -> a.contentHash().orElseThrow()));

        var refsToSave = new ArrayList<Artifact>();

        for (var entry : groupedByHash.entrySet()) {
            var contentHash = entry.getKey();
            var artifacts = entry.getValue();
            if (artifacts.isEmpty())
                continue;


            // Check if this hash already exists in the DB
            var existingOpt = artifactRepository.findByContentHash(contentHash);

            if (existingOpt.isPresent()) {
                // Original already in DB - decorateDuplicate all of them
                for (var artifact : artifacts) {
                    decorateDuplicate(contentHash, artifact.artifactKey())
                            .ifPresent(refsToSave::add);
                }
            } else {
                // Save the first as the original (no refs yet)
                var original = artifacts.getFirst();
                toEntity(executionKey, original)
                        .ifPresent(this::save);

                // decorateDuplicate the rest
                for (int i = 1; i < artifacts.size(); i++) {
                    var duplicate = artifacts.get(i);
                    decorateDuplicate(contentHash, duplicate.artifactKey())
                            .ifPresent(refsToSave::add);
                }
            }
        }

        artifactRepository.saveAllAndFlush(
                refsToSave.stream()
                        .flatMap(a -> toEntity(executionKey, a).stream())
                        .toList());
    }


    @Transactional(readOnly = true)
    public Optional<Artifact> decorateDuplicate(String contentHash, @NotNull ArtifactKey artifact) {
        if (contentHash == null || contentHash.isEmpty()) {
            return Optional.empty();
        }

        try {
            return artifactRepository.findByContentHash(contentHash)
                    .flatMap(this::deserializeArtifact)
                    .flatMap(a -> switch (a) {
                        case Templated t -> {
                            ArtifactKey refKey = a.artifactKey().equals(artifact)
                                    ? artifact.createChild()
                                    : artifact;
                            yield Optional.of(new Artifact.TemplateDbRef(
                                    refKey,
                                    t.templateStaticId(),
                                    UUID.randomUUID().toString(),
                                    t,
                                    remapChildren(t, refKey),
                                    safeMetadata(t.metadata()),
                                    t.artifactType()));
                        }
                        case Artifact t -> {
                            ArtifactKey refKey = t.artifactKey().equals(artifact)
                                    ? artifact.createChild()
                                    : artifact;
                            yield Optional.of(new Artifact.ArtifactDbRef(
                                    refKey,
                                    UUID.randomUUID().toString(),
                                    t,
                                    remapChildren(t, refKey),
                                    safeMetadata(t.metadata()),
                                    t.artifactType()));
                        }
                    });
        } catch (Exception e) {
            publishPersistenceError("Failed to decorate duplicate artifact for key " + artifact.value(), artifact, e);
            return Optional.empty();
        }
    }

    /**
     * Recursively remaps children's artifact keys under {@code newParentKey},
     * wrapping each child as a DbRef that points back to the original artifact
     * (preserving dedup history and reference chain). Works entirely from the
     * already-deserialized in-memory tree — no additional DB lookups — to avoid
     * infinite recursion through decorateDuplicate → remapChildren → decorateDuplicate.
     */
    private List<Artifact> remapChildren(Artifact source, ArtifactKey newParentKey) {
        List<Artifact> children = StreamUtil.toStream(source.children()).toList();
        if (children.isEmpty()) {
            return new ArrayList<>();
        }
        List<Artifact> remapped = new ArrayList<>(children.size());
        for (Artifact child : children) {
            ArtifactKey childKey = newParentKey.createChild();
            List<Artifact> remappedGrandchildren = remapChildren(child, childKey);

            // Wrap as a DbRef pointing to the original child, with remapped grandchildren
            Artifact ref = switch (child) {
                case Templated t -> new Artifact.TemplateDbRef(
                        childKey,
                        t.templateStaticId(),
                        UUID.randomUUID().toString(),
                        t,
                        remappedGrandchildren,
                        safeMetadata(t.metadata()),
                        t.artifactType());
                case Artifact t -> new Artifact.ArtifactDbRef(
                        childKey,
                        UUID.randomUUID().toString(),
                        t,
                        remappedGrandchildren,
                        safeMetadata(t.metadata()),
                        t.artifactType());
            };
            remapped.add(ref);
        }
        return remapped;
    }

    @Transactional(readOnly = true)
    public boolean existsByArtifactKey(String artifactKey) {
        return artifactKey != null
                && !artifactKey.isBlank()
                && artifactRepository.existsByArtifactKey(artifactKey);
    }

    private Map<String, String> safeMetadata(Map<String, String> metadata) {
        return metadata == null ? new HashMap<>() : new HashMap<>(metadata);
    }

    /**
     * Deserializes an ArtifactEntity's JSON content back to an Artifact instance.
     *
     * @param entity The ArtifactEntity containing JSON content
     * @return Optional containing the deserialized Artifact, or empty if deserialization fails
     */
    public Optional<Artifact> deserializeArtifact(ArtifactEntity entity) {
        if (entity == null || entity.getContentJson() == null) {
            return Optional.empty();
        }

        try {
            // Deserialize using the artifact type as a hint
            Artifact artifact = objectMapper.readValue(entity.getContentJson(), Artifact.class);

            artifact = switch (artifact) {
                case Artifact.TemplateDbRef t ->
                        this.artifactRepository.findByArtifactKey(entity.getReferencedArtifactKey())
                                .flatMap(this::deserializeArtifact)
                                .map(ae -> {
                                    if (ae instanceof Templated templated)
                                        return t.toBuilder()
                                                .ref(templated)
                                                .artifactType(Artifact.TemplateDbRef.class.getSimpleName())
                                                .build();

                                    log.error("Found artifact incompateible with templated {}.", t);

                                    return t;
                                })
                                .orElseGet(() -> {
                                    log.error("Could not find referenced artifact in repository!");
                                    return t;
                                });
                case Artifact.ArtifactDbRef t ->
                        this.artifactRepository.findByArtifactKey(entity.getReferencedArtifactKey())
                                .flatMap(this::deserializeArtifact)
                                .map(ae -> t.toBuilder()
                                        .ref(ae)
                                        .artifactType(Artifact.ArtifactDbRef.class.getSimpleName())
                                        .build())
                                .orElseGet(() -> {
                                    log.error("Could not find referenced artifact in repository!");
                                    return t;
                                });
                default -> artifact;
            };
            log.debug("Successfully deserialized artifact: {} of type: {}",
                    entity.getArtifactKey(), entity.getArtifactType());
            return Optional.of(artifact);
        } catch (Exception e) {
            var err = "Failed to deserialize artifact: %s of type: %s"
                    .formatted(entity.getArtifactKey(), entity.getArtifactType());
            log.error(err, e);
            ArtifactKey artifactKey = null;
            try {
                artifactKey = new ArtifactKey(entity.getArtifactKey());
            } catch (IllegalArgumentException i) {
            }
            publishPersistenceError(e.getMessage(), artifactKey, e);
            return Optional.empty();
        }
    }


    /**
     * Saves an artifact entity to the repository.
     * This is a pass-through method that can be used for consistency.
     *
     * @param entity The artifact entity to save
     * @return The saved entity
     */
    @Transactional
    public Optional<ArtifactEntity> save(ArtifactEntity entity) {
        if (entity == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(artifactRepository.save(entity));
        } catch (Exception e) {
            publishPersistenceError(
                    "Failed to save artifact entity " + entity.getArtifactKey(),
                    safeArtifactKey(entity.getArtifactKey()),
                    e
            );
            return Optional.empty();
        }
    }

    @Transactional
    public boolean persistDirectArtifact(String executionKey, Artifact artifact) {
        if (artifact == null || artifact.artifactKey() == null) {
            publishPersistenceError("Cannot persist null artifact directly", null, null);
            return false;
        }

        String artifactKey = artifact.artifactKey().value();
        try {
            if (artifactRepository.existsByArtifactKey(artifactKey)) {
                log.debug("Skipping direct artifact persist because key already exists: {}", artifactKey);
                return false;
            }

            return toEntity(executionKey, artifact)
                    .flatMap(this::save)
                    .isPresent();
        } catch (Exception e) {
            publishPersistenceError("Failed to persist artifact directly " + artifactKey, artifact.artifactKey(), e);
            return false;
        }
    }

    Optional<ArtifactEntity> toEntity(String executionKey, Artifact artifact) {
        if (artifact == null || artifact.artifactKey() == null) {
            publishPersistenceError("Cannot convert null artifact or artifact key to entity", null, null);
            return Optional.empty();
        }

        ArtifactKey key = artifact.artifactKey();
        try {
            String contentJson = objectMapper.writeValueAsString(artifact);
            String parentKey = key.parent().map(ArtifactKey::value).orElse(null);

            var templated = artifact instanceof Templated temp ? temp : null;
            var templateDbRef = artifact instanceof Artifact.TemplateDbRef temp ? temp : null;
            var artifactDbRef = artifact instanceof Artifact.ArtifactDbRef temp ? temp : null;

            if (templateDbRef != null && templateDbRef.ref() == null) {
                throw new IllegalArgumentException("Ref was null for TemplateDbRef provided.");
            }
            if (artifactDbRef != null && artifactDbRef.ref() == null) {
                throw new IllegalArgumentException("Ref was null for ArtifactDbRef provided.");
            }

            return Optional.of(ArtifactEntity.builder()
                    .artifactKey(key.value())
                    .referencedArtifactKey(
                            Optional.ofNullable(templateDbRef)
                                    .map(td -> td.ref().templateArtifactKey().value())
                                    .or(() -> Optional.ofNullable(artifactDbRef).map(td -> td.ref().artifactKey().value()))
                                    .orElse(null))
                    .templateStaticId(Optional.ofNullable(templated).map(Templated::templateStaticId).orElse(null))
                    .parentKey(parentKey)
                    .executionKey(executionKey)
                    .artifactType(artifact.artifactType())
                    .contentHash(artifact.contentHash().orElse(null))
                    .contentJson(contentJson)
                    .depth(key.depth())
                    .shared(false)
                    .childIds(
                            StreamUtil.toStream(artifact.children()).flatMap(a -> Stream.ofNullable(a.artifactKey()))
                                    .flatMap(ak -> StreamUtil.toStream(ak.value()))
                                    .collect(Collectors.toCollection(ArrayList::new)))
                    .build());
        } catch (Exception e) {
            publishPersistenceError("Failed to convert artifact to entity " + key.value(), key, e);
            return Optional.empty();
        }
    }

    private void publishPersistenceError(String message, ArtifactKey artifactKey, Exception exception) {
        if (exception == null) {
            log.error(message);
        } else {
            log.error(message, exception);
        }
        if (eventBus == null || artifactKey == null) {
            return;
        }
        eventBus.publish(Events.NodeErrorEvent.err(message, artifactKey));
    }

    private ArtifactKey safeArtifactKey(String artifactKeyValue) {
        try {
            return artifactKeyValue == null || artifactKeyValue.isBlank() ? null : new ArtifactKey(artifactKeyValue);
        } catch (Exception e) {
            log.warn("Failed to parse artifact key for persistence error: {}", artifactKeyValue, e);
            return null;
        }
    }

}
