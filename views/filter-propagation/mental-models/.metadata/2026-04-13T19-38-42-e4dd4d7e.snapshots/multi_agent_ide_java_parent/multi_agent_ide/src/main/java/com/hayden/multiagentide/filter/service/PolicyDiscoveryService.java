package com.hayden.multiagentide.filter.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.multiagentide.filter.repository.LayerEntity;
import com.hayden.multiagentide.filter.repository.PolicyRegistrationEntity;
import com.hayden.multiagentide.filter.repository.PolicyRegistrationRepository;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.multiagentide.filter.model.policy.PolicyLayerBinding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for discovering active policies for a given layer,
 * including inherited policies from ancestor layers.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyDiscoveryService {

    private final PolicyRegistrationRepository policyRegistrationRepository;
    private final LayerService layerService;
    private final ObjectMapper objectMapper;

    /**
     * Returns all active policies that apply to the given layer,
     * including inherited policies from ancestor layers.
     */
    public List<PolicyRegistrationEntity> getActivePoliciesByLayer(String layerId) {
        List<LayerEntity> effectiveLayers = layerService.getEffectiveLayers(layerId);
        Set<String> effectiveLayerIds = effectiveLayers.stream()
                .map(LayerEntity::getLayerId)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<PolicyRegistrationEntity> allActive = policyRegistrationRepository
                .findByStatus(FilterEnums.PolicyStatus.ACTIVE.name());

        return allActive.stream()
                .filter(policy -> hasEnabledBindingForAnyLayer(policy, effectiveLayerIds))
                .sorted(Comparator.comparingInt(p -> parsePriority(p)))
                .collect(Collectors.toList());
    }

    /**
     * Returns active policies for a specific layer only (no ancestor walk).
     */
    public List<PolicyRegistrationEntity> getActivePoliciesDirectlyBound(String layerId) {
        List<PolicyRegistrationEntity> allActive = policyRegistrationRepository
                .findByStatus(FilterEnums.PolicyStatus.ACTIVE.name());

        return allActive.stream()
                .filter(policy -> hasEnabledBindingForLayer(policy, layerId))
                .sorted(Comparator.comparingInt(p -> parsePriority(p)))
                .collect(Collectors.toList());
    }

    private boolean hasEnabledBindingForAnyLayer(PolicyRegistrationEntity policy, Set<String> layerIds) {
        List<PolicyLayerBinding> bindings = parseBindings(policy.getLayerBindingsJson());
        return bindings.stream()
                .anyMatch(b -> b.enabled() && layerIds.contains(b.layerId()));
    }

    private boolean hasEnabledBindingForLayer(PolicyRegistrationEntity policy, String layerId) {
        List<PolicyLayerBinding> bindings = parseBindings(policy.getLayerBindingsJson());
        return bindings.stream()
                .anyMatch(b -> b.enabled() && b.layerId().equals(layerId));
    }

    private List<PolicyLayerBinding> parseBindings(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Failed to parse layer bindings JSON", e);
            return List.of();
        }
    }

    private int parsePriority(PolicyRegistrationEntity policy) {
        try {
            var filterNode = objectMapper.readTree(policy.getFilterJson());
            return filterNode.has("priority") ? filterNode.get("priority").asInt(0) : 0;
        } catch (Exception e) {
            return 0;
        }
    }
}
