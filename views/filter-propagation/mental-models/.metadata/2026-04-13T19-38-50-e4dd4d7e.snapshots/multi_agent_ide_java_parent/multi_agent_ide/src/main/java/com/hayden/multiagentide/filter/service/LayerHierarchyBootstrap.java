package com.hayden.multiagentide.filter.service;

import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.multiagentide.filter.repository.LayerEntity;
import com.hayden.multiagentide.filter.repository.LayerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bootstraps the filter layer hierarchy on startup.
 *
 * Hierarchy:
 *   controller
 *   ├── controller-ui-event-poll
 *   ├── workflow-agent
 *   │   ├── workflow actions
 *   │   ├── discovery-dispatch-subagent
 *   │   ├── planning-dispatch-subagent
 *   │   └── ticket-dispatch-subagent
 *   ├── interrupt-service
 *   ├── worktree-auto-commit
 *   ├── worktree-merge-conflict
 *   └── ai-filter
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LayerHierarchyBootstrap {

    private final LayerRepository layerRepository;

    @Bean
    public ApplicationRunner bootstrapFilterLayers() {
        return args -> seedLayersIfAbsent();
    }

    @Transactional
    public void seedLayersIfAbsent() {
        log.info("Ensuring filter layer hierarchy matches catalog...");
        Map<String, LayerEntity> existingById = new LinkedHashMap<>();
        for (LayerEntity existing : layerRepository.findAll()) {
            existingById.put(existing.getLayerId(), existing);
        }
        Map<String, LayerEntity> layersById = new LinkedHashMap<>();
        for (FilterLayerCatalog.LayerDefinition definition : FilterLayerCatalog.layerDefinitions()) {
            LayerEntity entity = existingById.get(definition.layerId());
            LayerEntity normalized = layer(
                    definition.layerId(),
                    definition.layerType(),
                    definition.layerKey(),
                    definition.parentLayerId(),
                    definition.depth()
            );
            if (entity != null) {
                normalized.setUuid(entity.getUuid());
                normalized.setMetadataJson(entity.getMetadataJson());
            }
            layersById.put(definition.layerId(), normalized);
        }
        for (FilterLayerCatalog.LayerDefinition definition : FilterLayerCatalog.layerDefinitions()) {
            if (definition.parentLayerId() == null) {
                continue;
            }
            LayerEntity parent = layersById.get(definition.parentLayerId());
            if (parent != null) {
                parent.getChildLayerIds().add(definition.layerId());
            }
        }

        layerRepository.saveAll(layersById.values());
        log.info("Ensured {} filter layers.", layersById.size());
    }

    private static LayerEntity layer(String layerId, FilterEnums.LayerType type,
                                     String key, String parentId, int depth) {
        return LayerEntity.builder()
                .layerId(layerId)
                .layerType(type.name())
                .layerKey(key)
                .parentLayerId(parentId)
                .depth(depth)
                .isInheritable(true)
                .isPropagatedToParent(false)
                .build();
    }
}
