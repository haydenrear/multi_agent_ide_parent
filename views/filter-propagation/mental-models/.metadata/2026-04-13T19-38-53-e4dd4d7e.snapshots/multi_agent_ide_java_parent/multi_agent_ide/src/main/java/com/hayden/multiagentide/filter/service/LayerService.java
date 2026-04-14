package com.hayden.multiagentide.filter.service;

import com.hayden.multiagentide.filter.repository.LayerEntity;
import com.hayden.multiagentide.filter.repository.LayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service for layer hierarchy traversal and resolution.
 */
@Service
@RequiredArgsConstructor
public class LayerService {

    private final LayerRepository layerRepository;

    public Optional<LayerEntity> getLayer(String layerId) {
        return layerRepository.findByLayerId(layerId);
    }

    /**
     * Returns direct children, or all descendants if recursive.
     */
    public List<LayerEntity> getChildLayers(String layerId, boolean recursive) {
        if (!recursive) {
            return layerRepository.findByParentLayerId(layerId);
        }
        List<LayerEntity> result = new ArrayList<>();
        collectDescendants(layerId, result);
        return result;
    }

    /**
     * Returns effective layers for a given layer: the layer itself plus all inheritable ancestors.
     * Walks up the hierarchy collecting layers that have isInheritable=true.
     */
    public List<LayerEntity> getEffectiveLayers(String layerId) {
        List<LayerEntity> effective = new ArrayList<>();
        Optional<LayerEntity> current = layerRepository.findByLayerId(layerId);
        while (current.isPresent()) {
            LayerEntity layer = current.get();
            effective.add(layer);
            if (layer.getParentLayerId() == null) {
                break;
            }
            current = layerRepository.findByLayerId(layer.getParentLayerId());
            // Only include ancestor if it's inheritable
            if (current.isPresent() && !current.get().isInheritable()) {
                break;
            }
        }
        return effective;
    }

    /**
     * Returns all descendant layer IDs (recursive).
     */
    public Set<String> getDescendantLayerIds(String layerId) {
        Set<String> ids = new LinkedHashSet<>();
        collectDescendantIds(layerId, ids);
        return ids;
    }

    private void collectDescendants(String layerId, List<LayerEntity> result) {
        List<LayerEntity> children = layerRepository.findByParentLayerId(layerId);
        for (LayerEntity child : children) {
            result.add(child);
            collectDescendants(child.getLayerId(), result);
        }
    }

    private void collectDescendantIds(String layerId, Set<String> ids) {
        List<LayerEntity> children = layerRepository.findByParentLayerId(layerId);
        for (LayerEntity child : children) {
            ids.add(child.getLayerId());
            collectDescendantIds(child.getLayerId(), ids);
        }
    }
}
