package com.hayden.multiagentide.artifacts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactHashing;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.multiagentide.filter.controller.dto.PolicyRegistrationRequest;
import com.hayden.multiagentide.filter.repository.PolicyRegistrationEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Emits standalone policy lifecycle artifacts and persists them immediately.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PolicyLifecycleArtifactService {

    private final ExecutionScopeService executionScopeService;
    private final ArtifactEventListener artifactEventListener;
    private final ObjectMapper objectMapper;

    public void recordRegistration(FilterEnums.FilterKind filterKind,
                                   PolicyRegistrationRequest request,
                                   PolicyRegistrationEntity entity,
                                   Instant registeredAt) {
        recordStandaloneArtifact(Artifact.PolicyRegistrationArtifact.builder()
                .artifactKey(ArtifactKey.createRoot(registeredAt))
                .policyId(entity.getRegistrationId())
                .filterKind(filterKind.name())
                .status(entity.getStatus())
                .registeredAt(registeredAt)
                .registeredBy(entity.getRegisteredBy())
                .filterJson(entity.getFilterJson())
                .layerBindingsJson(entity.getLayerBindingsJson())
                .activateOnCreate(request.isActivate())
                .hash(hashPayload(Map.of(
                        "policyId", entity.getRegistrationId(),
                        "filterKind", filterKind.name(),
                        "status", entity.getStatus(),
                        "filterJson", entity.getFilterJson(),
                        "layerBindingsJson", entity.getLayerBindingsJson()
                )))
                .metadata(new LinkedHashMap<>(Map.of(
                        "operation", "register",
                        "policyId", entity.getRegistrationId()
                )))
                .children(new ArrayList<>())
                .build());
    }

    public void recordDeactivation(PolicyRegistrationEntity entity, Instant deactivatedAt) {
        recordStandaloneArtifact(Artifact.PolicyDeactivationArtifact.builder()
                .artifactKey(ArtifactKey.createRoot(deactivatedAt))
                .policyId(entity.getRegistrationId())
                .status(entity.getStatus())
                .deactivatedAt(deactivatedAt)
                .hash(hashPayload(Map.of(
                        "policyId", entity.getRegistrationId(),
                        "status", entity.getStatus(),
                        "deactivatedAt", deactivatedAt.toString()
                )))
                .metadata(new LinkedHashMap<>(Map.of(
                        "operation", "deactivate",
                        "policyId", entity.getRegistrationId()
                )))
                .children(new ArrayList<>())
                .build());
    }

    public void recordLayerToggle(PolicyRegistrationEntity entity,
                                  String layerId,
                                  boolean enabled,
                                  boolean includeDescendants,
                                  List<String> affectedDescendantLayers,
                                  Instant updatedAt) {
        recordStandaloneArtifact(Artifact.PolicyLayerBindingToggleArtifact.builder()
                .artifactKey(ArtifactKey.createRoot(updatedAt))
                .policyId(entity.getRegistrationId())
                .layerId(layerId)
                .enabled(enabled)
                .includeDescendants(includeDescendants)
                .affectedDescendantLayers(List.copyOf(affectedDescendantLayers))
                .layerBindingsJson(entity.getLayerBindingsJson())
                .updatedAt(updatedAt)
                .hash(hashPayload(Map.of(
                        "policyId", entity.getRegistrationId(),
                        "layerId", layerId,
                        "enabled", enabled,
                        "includeDescendants", includeDescendants,
                        "affectedDescendantLayers", List.copyOf(affectedDescendantLayers),
                        "layerBindingsJson", entity.getLayerBindingsJson()
                )))
                .metadata(new LinkedHashMap<>(Map.of(
                        "operation", enabled ? "enable-layer-binding" : "disable-layer-binding",
                        "policyId", entity.getRegistrationId(),
                        "layerId", layerId
                )))
                .children(new ArrayList<>())
                .build());
    }

    public void recordLayerBindingUpdate(PolicyRegistrationEntity entity,
                                         PolicyRegistrationRequest.LayerBindingRequest binding,
                                         Instant updatedAt) {
        String layerBindingJson = serialize(binding);
        recordStandaloneArtifact(Artifact.PolicyLayerBindingUpdateArtifact.builder()
                .artifactKey(ArtifactKey.createRoot(updatedAt))
                .policyId(entity.getRegistrationId())
                .layerId(binding.getLayerId())
                .layerBindingJson(layerBindingJson)
                .layerBindingsJson(entity.getLayerBindingsJson())
                .updatedAt(updatedAt)
                .hash(hashPayload(Map.of(
                        "policyId", entity.getRegistrationId(),
                        "layerId", binding.getLayerId(),
                        "layerBindingJson", layerBindingJson,
                        "layerBindingsJson", entity.getLayerBindingsJson()
                )))
                .metadata(new LinkedHashMap<>(Map.of(
                        "operation", "update-layer-binding",
                        "policyId", entity.getRegistrationId(),
                        "layerId", binding.getLayerId()
                )))
                .children(new ArrayList<>())
                .build());
    }

    private void recordStandaloneArtifact(Artifact artifact) {
        try {
            executionScopeService.emitArtifact(artifact, null);
            artifactEventListener.finishPersistRemove(artifact.artifactKey().value());
        } catch (Exception e) {
            log.error("Failed to persist policy lifecycle artifact {}", artifact.artifactType(), e);
        }
    }

    private String hashPayload(Map<String, Object> payload) {
        return ArtifactHashing.hashJson(payload);
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("Failed to serialize policy lifecycle artifact payload", e);
            return "{}";
        }
    }
}
