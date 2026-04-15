package com.hayden.multiagentide.prompt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.multiagentide.artifacts.entity.ArtifactEntity;
import com.hayden.multiagentide.artifacts.repository.ArtifactRepository;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.multiagentide.artifact.PromptTemplateVersion;
import com.hayden.utilitymodule.stream.StreamUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Store for prompt template versions with deduplication by content hash.
 * 
 * Responsibilities:
 * - Persists template versions to artifact storage
 * - Deduplicates by (templateStaticId, contentHash)
 * - Provides lookup by static ID and version
 * - Groups templates by family prefix
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PromptTemplateStore {
    
    private final ArtifactRepository artifactRepository;

    private final ObjectMapper objectMapper;
    
    // In-memory cache: templateStaticId -> (contentHash -> PromptTemplateVersion)
    private final Map<String, Map<String, PromptTemplateVersion>> templateCache = new ConcurrentHashMap<>();
    
    // Latest version cache: templateStaticId -> PromptTemplateVersion
    private final Map<String, PromptTemplateVersion> latestVersions = new ConcurrentHashMap<>();
    
    /**
     * Registers a template version, deduplicating by content hash.
     * 
     * @param template The template to register
     * @return Empty if this is a new version, or the existing version if already registered
     */
    @Transactional
    public Optional<PromptTemplateVersion> registerTemplate(PromptTemplateVersion template) {
        String staticId = template.templateStaticId();
        String contentHash = template.contentHash().orElse(null);
        
        // Check cache first
        Map<String, PromptTemplateVersion> versions = templateCache.get(staticId);
        if (versions != null && versions.containsKey(contentHash)) {
            log.debug("Template version already cached: {} (hash: {})", staticId, contentHash.substring(0, 8));
            return Optional.of(versions.get(contentHash));
        }
        
        // Check persistence
        Optional<ArtifactEntity> existing = artifactRepository
                .findByTemplateStaticIdAndContentHashAndSharedTrue(staticId, contentHash);
        
        if (existing.isPresent()) {
            // Load from persistence into cache
            PromptTemplateVersion persisted = fromEntity(existing.get());
            cacheTemplate(persisted);
            log.debug("Template version already persisted: {} (hash: {})", staticId, contentHash.substring(0, 8));
            return Optional.of(persisted);
        }
        
        // New version - persist it
        ArtifactEntity entity = toEntity(template);
        artifactRepository.save(entity);
        
        // Update cache
        cacheTemplate(template);
        
        log.info("Registered new template version: {} (hash: {})", staticId, contentHash.substring(0, 8));
        return Optional.empty();
    }
    
    /**
     * Gets a template by static ID and content hash.
     */
    public Optional<PromptTemplateVersion> getVersion(String staticId, String contentHash) {
        // Check cache
        Map<String, PromptTemplateVersion> versions = templateCache.get(staticId);
        if (versions != null && versions.containsKey(contentHash)) {
            return Optional.of(versions.get(contentHash));
        }
        
        // Check persistence
        return artifactRepository
                .findByTemplateStaticIdAndContentHashAndSharedTrue(staticId, contentHash)
                .map(this::fromEntity);
    }
    
    /**
     * Gets the latest (most recently updated) version of a template.
     */
    public Optional<PromptTemplateVersion> getLatestVersion(String staticId) {
        // Check cache
        PromptTemplateVersion cached = latestVersions.get(staticId);
        if (cached != null) {
            return Optional.of(cached);
        }
        
        // Check persistence
        List<ArtifactEntity> versions = artifactRepository
                .findByTemplateStaticIdAndSharedTrueOrderByCreatedTimeDesc(staticId);
        
        if (versions.isEmpty()) {
            return Optional.empty();
        }
        
        PromptTemplateVersion latest = fromEntity(versions.get(0));
        latestVersions.put(staticId, latest);
        return Optional.of(latest);
    }
    
    /**
     * Gets all versions of a template family (by static ID prefix).
     */
    public List<PromptTemplateVersion> getTemplateFamily(String staticIdPrefix) {
        List<ArtifactEntity> entities = artifactRepository
                .findTemplatesByStaticIdPrefix(staticIdPrefix);
        
        return entities.stream()
                .map(this::fromEntity)
                .toList();
    }
    
    /**
     * Gets all distinct template static IDs.
     */
    public Set<String> getAllStaticIds() {
        Set<String> ids = new HashSet<>(templateCache.keySet());
        
        // Also check persistence for any not in cache
        ids.addAll(artifactRepository.findAllTemplateStaticIds());
        
        return ids;
    }
    
    /**
     * Gets a template by its artifact key.
     */
    public Optional<PromptTemplateVersion> getByArtifactKey(ArtifactKey key) {
        return artifactRepository.findByArtifactKey(key.value())
                .filter(e -> Boolean.TRUE.equals(e.getShared()))
                .map(this::fromEntity);
    }
    
    /**
     * Clears the in-memory cache.
     */
    public void clearCache() {
        templateCache.clear();
        latestVersions.clear();
        log.info("Template cache cleared");
    }
    
    // ========== Private Helpers ==========
    
    private void cacheTemplate(PromptTemplateVersion template) {
        String staticId = template.templateStaticId();
        String contentHash = template.contentHash().orElse(null);
        
        templateCache
                .computeIfAbsent(staticId, k -> new ConcurrentHashMap<>())
                .put(contentHash, template);
        
        // Update latest if this is newer
        PromptTemplateVersion current = latestVersions.get(staticId);
        if (current == null || template.lastUpdatedAt().isAfter(current.lastUpdatedAt())) {
            latestVersions.put(staticId, template);
        }
    }
    
    private ArtifactEntity toEntity(PromptTemplateVersion template) {
        return ArtifactEntity.builder()
                .artifactKey(template.templateArtifactKey().value())
                .parentKey(null) // Shared templates have no parent
                .executionKey(template.templateArtifactKey().value()) // Self-referential for shared
                .artifactType("PromptTemplateVersion")
                .contentHash(template.contentHash().orElse(null))
                .contentJson(template.templateText()) // Store raw text
                .templateStaticId(template.templateStaticId())
                .depth(1)
                .childIds(
                        StreamUtil.toStream(template.children()).flatMap(a -> Stream.ofNullable(a.artifactKey()))
                                .flatMap(ak -> StreamUtil.toStream(ak.value()))
                                .toList())
                .shared(true)
                .build();
    }
    
    private PromptTemplateVersion fromEntity(ArtifactEntity entity) {
        try {
            return objectMapper.readValue(entity.getContentJson(), PromptTemplateVersion.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
