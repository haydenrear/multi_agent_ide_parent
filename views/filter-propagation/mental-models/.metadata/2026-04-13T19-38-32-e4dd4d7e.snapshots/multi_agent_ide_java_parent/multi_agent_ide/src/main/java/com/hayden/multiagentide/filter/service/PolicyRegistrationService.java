package com.hayden.multiagentide.filter.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.multiagentide.filter.controller.dto.*;
import com.hayden.multiagentide.artifacts.PolicyLifecycleArtifactService;
import com.hayden.multiagentide.filter.repository.PolicyRegistrationEntity;
import com.hayden.multiagentide.filter.repository.PolicyRegistrationRepository;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.multiagentide.filter.model.policy.PolicyLayerBinding;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for policy registration, deactivation, layer binding mutations, and propagation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyRegistrationService {

    private final PolicyRegistrationRepository policyRegistrationRepository;
    private final LayerService layerService;
    private final ObjectMapper objectMapper;
    private final PolicyLifecycleArtifactService policyLifecycleArtifactService;

    @Transactional
    public PolicyRegistrationResponse registerPolicy(FilterEnums.FilterKind filterKind,
                                                     PolicyRegistrationRequest request) {
        String registrationId = "policy-" + UUID.randomUUID();
        Instant now = Instant.now();

        // Build filter JSON
        Map<String, Object> filterMap = new LinkedHashMap<>();
        filterMap.put("filterType", filterKind.type());
        filterMap.put("id", registrationId);
        filterMap.put("name", request.getName());
        filterMap.put("description", request.getDescription());
        filterMap.put("sourcePath", request.getSourcePath());
        filterMap.put("priority", request.getPriority());
        filterMap.put("status", request.isActivate() ? "ACTIVE" : "INACTIVE");
        filterMap.put("executor", request.getExecutor());
        filterMap.put("createdAt", now.toString());
        filterMap.put("updatedAt", now.toString());

        // Build layer bindings
        List<PolicyLayerBinding> bindings = request.getLayerBindings().stream()
                .map(b -> PolicyLayerBinding.builder()
                        .layerId(b.getLayerId())
                        .enabled(b.isEnabled())
                        .includeDescendants(b.isIncludeDescendants())
                        .isInheritable(b.isInheritable())
                        .isPropagatedToParent(b.isPropagatedToParent())
                        .matcherKey(FilterEnums.MatcherKey.valueOf(b.getMatcherKey()))
                        .matcherType(FilterEnums.MatcherType.valueOf(b.getMatcherType()))
                        .matcherText(b.getMatcherText())
                        .matchOn(FilterEnums.MatchOn.valueOf(b.getMatchOn()))
                        .updatedBy("system")
                        .updatedAt(now)
                        .build())
                .collect(Collectors.toList());

        // One-shot propagation
        List<PolicyLayerBinding> propagated = propagateBindings(bindings);
        bindings.addAll(propagated);

        try {
            PolicyRegistrationEntity entity = PolicyRegistrationEntity.builder()
                    .registrationId(registrationId)
                    .registeredBy("system")
                    .status(request.isActivate()
                            ? FilterEnums.PolicyStatus.ACTIVE.name()
                            : FilterEnums.PolicyStatus.INACTIVE.name())
                    .filterKind(filterKind.name())
                    .isInheritable(request.isInheritable())
                    .isPropagatedToParent(request.isPropagatedToParent())
                    .filterJson(objectMapper.writeValueAsString(filterMap))
                    .layerBindingsJson(objectMapper.writeValueAsString(bindings))
                    .activatedAt(request.isActivate() ? now : null)
                    .build();
            policyRegistrationRepository.save(entity);
            policyLifecycleArtifactService.recordRegistration(filterKind, request, entity, now);

            return PolicyRegistrationResponse.builder()
                    .ok(true)
                    .policyId(registrationId)
                    .filterType(filterKind.name())
                    .status(entity.getStatus())
                    .message("Policy registered successfully")
                    .build();
        } catch (Exception e) {
            log.error("Failed to persist policy registration", e);
            return PolicyRegistrationResponse.builder()
                    .ok(false)
                    .message("Failed to persist policy: " + e.getMessage())
                    .build();
        }
    }

    @Transactional
    public DeactivatePolicyResponse deactivatePolicy(String policyId) {
        Optional<PolicyRegistrationEntity> opt = policyRegistrationRepository.findByRegistrationId(policyId);
        if (opt.isEmpty()) {
            return DeactivatePolicyResponse.builder()
                    .ok(false)
                    .policyId(policyId)
                    .message("Policy not found")
                    .build();
        }

        PolicyRegistrationEntity entity = opt.get();
        Instant now = Instant.now();
        entity.setStatus(FilterEnums.PolicyStatus.INACTIVE.name());
        entity.setDeactivatedAt(now);
        policyRegistrationRepository.save(entity);
        policyLifecycleArtifactService.recordDeactivation(entity, now);

        return DeactivatePolicyResponse.builder()
                .ok(true)
                .policyId(policyId)
                .status(FilterEnums.PolicyStatus.INACTIVE.name())
                .deactivatedAt(now)
                .message("Policy deactivated successfully")
                .build();
    }

    @Transactional
    public TogglePolicyLayerResponse togglePolicyAtLayer(String policyId, String layerId,
                                                         boolean enabled, boolean includeDescendants) {
        if (FilterLayerCatalog.isInternalAutomationLayer(layerId)) {
            return TogglePolicyLayerResponse.builder()
                    .ok(false)
                    .policyId(policyId)
                    .layerId(layerId)
                    .message("layerId cannot target internal automation layers")
                    .build();
        }
        Optional<PolicyRegistrationEntity> opt = policyRegistrationRepository.findByRegistrationId(policyId);
        if (opt.isEmpty()) {
            return TogglePolicyLayerResponse.builder()
                    .ok(false)
                    .policyId(policyId)
                    .layerId(layerId)
                    .message("Policy not found")
                    .build();
        }

        PolicyRegistrationEntity entity = opt.get();
        try {
            Instant now = Instant.now();
            List<PolicyLayerBinding> bindings = objectMapper.readValue(
                    entity.getLayerBindingsJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, PolicyLayerBinding.class));

            Set<String> targetLayerIds = new LinkedHashSet<>();
            targetLayerIds.add(layerId);
            List<String> affectedDescendants = new ArrayList<>();

            if (includeDescendants) {
                Set<String> descendants = layerService.getDescendantLayerIds(layerId);
                targetLayerIds.addAll(descendants);
                affectedDescendants.addAll(descendants);
            }

            List<PolicyLayerBinding> updated = bindings.stream()
                    .map(b -> targetLayerIds.contains(b.layerId())
                            ? new PolicyLayerBinding(b.layerId(), enabled, b.includeDescendants(),
                                    b.isInheritable(), b.isPropagatedToParent(),
                                    b.matcherKey(), b.matcherType(), b.matcherText(), b.matchOn(),
                                    "system", now)
                            : b)
                    .collect(Collectors.toList());

            entity.setLayerBindingsJson(objectMapper.writeValueAsString(updated));
            policyRegistrationRepository.save(entity);
            policyLifecycleArtifactService.recordLayerToggle(
                    entity, layerId, enabled, includeDescendants, affectedDescendants, now);

            return TogglePolicyLayerResponse.builder()
                    .ok(true)
                    .policyId(policyId)
                    .layerId(layerId)
                    .enabled(enabled)
                    .affectedDescendantLayers(affectedDescendants)
                    .message(enabled ? "Policy enabled at layer" : "Policy disabled at layer")
                    .build();
        } catch (Exception e) {
            log.error("Failed to toggle policy layer binding", e);
            return TogglePolicyLayerResponse.builder()
                    .ok(false)
                    .policyId(policyId)
                    .layerId(layerId)
                    .message("Failed: " + e.getMessage())
                    .build();
        }
    }

    @Transactional
    public PutPolicyLayerResponse updatePolicyLayerBinding(String policyId, PutPolicyLayerRequest request) {
        String layerId = request.layerBinding() == null ? null : request.layerBinding().getLayerId();
        if (FilterLayerCatalog.isInternalAutomationLayer(layerId)) {
            return PutPolicyLayerResponse.builder()
                    .ok(false)
                    .policyId(policyId)
                    .layerId(layerId)
                    .message("Validation failed: layerId cannot target internal automation layers")
                    .build();
        }
        Optional<PolicyRegistrationEntity> opt = policyRegistrationRepository.findByRegistrationId(policyId);
        if (opt.isEmpty()) {
            return PutPolicyLayerResponse.builder()
                    .ok(false)
                    .policyId(policyId)
                    .message("Policy not found")
                    .build();
        }

        PolicyRegistrationEntity entity = opt.get();
        try {
            Instant now = Instant.now();
            List<PolicyLayerBinding> bindings = objectMapper.readValue(
                    entity.getLayerBindingsJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, PolicyLayerBinding.class));

            PolicyRegistrationRequest.LayerBindingRequest lb = request.layerBinding();
            PolicyLayerBinding newBinding = PolicyLayerBinding.builder()
                    .layerId(lb.getLayerId())
                    .enabled(lb.isEnabled())
                    .includeDescendants(lb.isIncludeDescendants())
                    .isInheritable(lb.isInheritable())
                    .isPropagatedToParent(lb.isPropagatedToParent())
                    .matcherKey(FilterEnums.MatcherKey.valueOf(lb.getMatcherKey()))
                    .matcherType(FilterEnums.MatcherType.valueOf(lb.getMatcherType()))
                    .matcherText(lb.getMatcherText())
                    .matchOn(FilterEnums.MatchOn.valueOf(lb.getMatchOn()))
                    .updatedBy("system")
                    .updatedAt(now)
                    .build();

            // Upsert: replace existing binding for layerId or add new
            boolean replaced = false;
            for (int i = 0; i < bindings.size(); i++) {
                if (bindings.get(i).layerId().equals(lb.getLayerId())) {
                    bindings.set(i, newBinding);
                    replaced = true;
                    break;
                }
            }
            if (!replaced) {
                bindings.add(newBinding);
            }

            // One-shot propagation for the new/updated binding
            List<PolicyLayerBinding> propagated = propagateBindings(List.of(newBinding));
            bindings.addAll(propagated);

            entity.setLayerBindingsJson(objectMapper.writeValueAsString(bindings));
            policyRegistrationRepository.save(entity);
            policyLifecycleArtifactService.recordLayerBindingUpdate(entity, lb, now);

            return PutPolicyLayerResponse.builder()
                    .ok(true)
                    .policyId(policyId)
                    .layerId(lb.getLayerId())
                    .message("Layer binding updated successfully")
                    .build();
        } catch (Exception e) {
            log.error("Failed to update policy layer binding", e);
            return PutPolicyLayerResponse.builder()
                    .ok(false)
                    .policyId(policyId)
                    .message("Failed: " + e.getMessage())
                    .build();
        }
    }

    /**
     * One-shot propagation: propagate bindings to descendants (isInheritable) and parent (isPropagatedToParent).
     * Propagated bindings have isInheritable=false, isPropagatedToParent=false to prevent cascading.
     */
    private List<PolicyLayerBinding> propagateBindings(List<PolicyLayerBinding> sourceBindings) {
        List<PolicyLayerBinding> propagated = new ArrayList<>();
        Instant now = Instant.now();

        for (PolicyLayerBinding binding : sourceBindings) {
            // Propagate to descendants
            if (binding.isInheritable()) {
                Set<String> descendants = layerService.getDescendantLayerIds(binding.layerId());
                for (String descendantId : descendants) {
                    propagated.add(PolicyLayerBinding.builder()
                            .layerId(descendantId)
                            .enabled(binding.enabled())
                            .includeDescendants(false)
                            .isInheritable(false)
                            .isPropagatedToParent(false)
                            .matcherKey(binding.matcherKey())
                            .matcherType(binding.matcherType())
                            .matcherText(binding.matcherText())
                            .matchOn(binding.matchOn())
                            .updatedBy("propagation")
                            .updatedAt(now)
                            .build());
                }
            }

            // Propagate to parent
            if (binding.isPropagatedToParent()) {
                layerService.getLayer(binding.layerId()).ifPresent(layer -> {
                    if (layer.getParentLayerId() != null) {
                        propagated.add(PolicyLayerBinding.builder()
                                .layerId(layer.getParentLayerId())
                                .enabled(binding.enabled())
                                .includeDescendants(false)
                                .isInheritable(false)
                                .isPropagatedToParent(false)
                                .matcherKey(binding.matcherKey())
                                .matcherType(binding.matcherType())
                                .matcherText(binding.matcherText())
                                .matchOn(binding.matchOn())
                                .updatedBy("propagation")
                                .updatedAt(now)
                                .build());
                    }
                });
            }
        }

        return propagated;
    }
}
