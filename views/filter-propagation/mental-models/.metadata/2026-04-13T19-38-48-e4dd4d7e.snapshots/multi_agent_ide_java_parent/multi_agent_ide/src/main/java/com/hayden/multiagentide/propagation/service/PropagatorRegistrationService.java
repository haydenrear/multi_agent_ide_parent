package com.hayden.multiagentide.propagation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.multiagentide.model.AiTextPropagator;
import com.hayden.multiagentide.model.Propagator;
import com.hayden.multiagentide.model.PropagatorLayerBinding;
import com.hayden.multiagentide.model.PropagatorMatchOn;
import com.hayden.multiagentide.propagation.controller.dto.*;
import com.hayden.multiagentide.propagation.repository.PropagatorRegistrationEntity;
import com.hayden.multiagentide.propagation.repository.PropagatorRegistrationRepository;
import com.hayden.multiagentide.filter.model.executor.AiFilterTool;
import com.hayden.multiagentide.model.executor.AiPropagatorTool;
import com.hayden.utilitymodule.stream.StreamUtil;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PropagatorRegistrationService {

    private final PropagatorRegistrationRepository repository;
    private final ObjectMapper objectMapper;
    private final PropagatorAttachableCatalogService attachableCatalogService;

    @Transactional
    public PropagatorRegistrationResponse register(PropagatorRegistrationRequest request) {
        Instant now = Instant.now();
        String registrationId = "propagator-" + UUID.randomUUID();
        try {
            AiPropagatorTool executor = request.getExecutor();
            Propagator<?, ?, ?> propagator = buildPropagator(registrationId, request, executor, now);
            List<PropagatorLayerBinding> bindings = request.getLayerBindings().stream()
                    .map(binding -> PropagatorLayerBinding.builder()
                            .layerId(binding.getLayerId())
                            .enabled(binding.isEnabled())
                            .includeDescendants(binding.isIncludeDescendants())
                            .isInheritable(binding.isInheritable())
                            .isPropagatedToParent(binding.isPropagatedToParent())
                            .matcherKey(FilterEnums.MatcherKey.valueOf(binding.getMatcherKey()))
                            .matcherType(FilterEnums.MatcherType.valueOf(binding.getMatcherType()))
                            .matcherText(binding.getMatcherText())
                            .matchOn(PropagatorMatchOn.valueOf(binding.getMatchOn()))
                            .updatedBy("system")
                            .updatedAt(now)
                            .build())
                    .toList();

            repository.save(PropagatorRegistrationEntity.builder()
                    .registrationId(registrationId)
                    .registeredBy("system")
                    .status(request.isActivate() ? FilterEnums.PolicyStatus.ACTIVE.name() : FilterEnums.PolicyStatus.INACTIVE.name())
                    .propagatorKind(request.getPropagatorKind())
                    .isInheritable(request.isInheritable())
                    .isPropagatedToParent(request.isPropagatedToParent())
                    .propagatorJson(objectMapper.writeValueAsString(propagator))
                    .layerBindingsJson(objectMapper.writeValueAsString(bindings))
                    .activatedAt(request.isActivate() ? now : null)
                    .build());
            return PropagatorRegistrationResponse.builder().ok(true).registrationId(registrationId)
                    .status(request.isActivate() ? FilterEnums.PolicyStatus.ACTIVE.name() : FilterEnums.PolicyStatus.INACTIVE.name())
                    .message("Propagator registered")
                    .build();
        } catch (Exception e) {
            log.error("Failed to register propagator", e);
            return PropagatorRegistrationResponse.builder().ok(false).message(e.getMessage()).build();
        }
    }

    @Transactional
    public ActivatePropagatorResponse activate(String registrationId) {
        return Optional.ofNullable(enableAiPropagator(registrationId))
                .map(entity -> {
                    return ActivatePropagatorResponse.builder().ok(true)
                            .registrationId(registrationId).status(entity.getStatus()).message("Propagator deactivated").build();
                })
               .orElseGet(() -> ActivatePropagatorResponse.builder().ok(false).registrationId(registrationId).message("Propagator not found").build());
    }

    @Transactional
    public DeactivatePropagatorResponse deactivate(String registrationId) {
        return Optional.ofNullable(disableAiPropagator(registrationId))
                .map(entity -> {
                    return DeactivatePropagatorResponse.builder().ok(true)
                            .registrationId(registrationId).status(entity.getStatus()).message("Propagator deactivated").build();
                })
                .orElseGet(() -> DeactivatePropagatorResponse.builder().ok(false).registrationId(registrationId).message("Propagator not found").build());
    }

    @Transactional
    public PutPropagatorLayerResponse updateLayerBinding(String registrationId, PutPropagatorLayerRequest request) {
        var entityOpt = repository.findByRegistrationId(registrationId);
        if (entityOpt.isEmpty()) {
            return PutPropagatorLayerResponse.builder().ok(false).registrationId(registrationId).message("Propagator not found").build();
        }
        try {
            Instant now = Instant.now();
            List<PropagatorLayerBinding> bindings = objectMapper.readValue(entityOpt.get().getLayerBindingsJson(), objectMapper.getTypeFactory().constructCollectionType(List.class, PropagatorLayerBinding.class));
            var lb = request.layerBinding();
            PropagatorLayerBinding replacement = PropagatorLayerBinding.builder()
                    .layerId(lb.getLayerId())
                    .enabled(lb.isEnabled())
                    .includeDescendants(lb.isIncludeDescendants())
                    .isInheritable(lb.isInheritable())
                    .isPropagatedToParent(lb.isPropagatedToParent())
                    .matcherKey(FilterEnums.MatcherKey.valueOf(lb.getMatcherKey()))
                    .matcherType(FilterEnums.MatcherType.valueOf(lb.getMatcherType()))
                    .matcherText(lb.getMatcherText())
                    .matchOn(PropagatorMatchOn.valueOf(lb.getMatchOn()))
                    .updatedBy("system")
                    .updatedAt(now)
                    .build();
            boolean replaced = false;
            java.util.ArrayList<PropagatorLayerBinding> updated = new java.util.ArrayList<>();
            for (PropagatorLayerBinding binding : bindings) {
                if (binding.layerId().equals(replacement.layerId()) && binding.matchOn() == replacement.matchOn()) {
                    updated.add(replacement);
                    replaced = true;
                } else {
                    updated.add(binding);
                }
            }
            if (!replaced) updated.add(replacement);
            entityOpt.get().setLayerBindingsJson(objectMapper.writeValueAsString(updated));
            repository.save(entityOpt.get());
            return PutPropagatorLayerResponse.builder().ok(true).registrationId(registrationId).layerId(replacement.layerId()).message("Binding updated").build();
        } catch (Exception e) {
            return PutPropagatorLayerResponse.builder().ok(false).registrationId(registrationId).message(e.getMessage()).build();
        }
    }

    @Transactional
    public int ensureAutoAiPropagatorsRegistered() {
        Instant now = Instant.now();
        Map<String, PropagatorRegistrationEntity> existingBySourcePath = new LinkedHashMap<>();
        for (PropagatorRegistrationEntity entity : repository.findAll()) {
            extractSourcePath(entity).ifPresent(sourcePath -> existingBySourcePath.put(sourcePath, entity));
        }

        int changed = 0;
        ReadPropagatorAttachableTargetsResponse readPropagatorAttachableTargetsResponse = attachableCatalogService.readAttachableTargets();
        for (ReadPropagatorAttachableTargetsResponse.ActionTarget action : readPropagatorAttachableTargetsResponse.actions()) {
            for (String stage : action.stages()) {
                String sourcePath = autoSourcePath(action.layerId(), stage);
                PropagatorRegistrationRequest request = autoRegistrationRequest(action, stage, sourcePath);
                PropagatorRegistrationEntity existing = existingBySourcePath.get(sourcePath);
                if (existing == null) {
                    saveRegistration("propagator-" + UUID.randomUUID(), request, now, null);
                    changed++;
                    continue;
                }
                saveRegistration(existing.getRegistrationId(), request, now, existing);
                changed++;
            }
        }
        return changed;
    }

    private Propagator<?, ?, ?> buildPropagator(String registrationId, PropagatorRegistrationRequest request, AiPropagatorTool executor, Instant now) {
        return AiTextPropagator.builder()
                .id(registrationId)
                .name(request.getName())
                .description(request.getDescription())
                .sourcePath(request.getSourcePath())
                .executor(executor)
                .status(request.isActivate() ? FilterEnums.PolicyStatus.ACTIVE : FilterEnums.PolicyStatus.INACTIVE)
                .priority(request.getPriority())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private PropagatorRegistrationEntity disableRegistration(PropagatorRegistrationEntity existing) {
        try {
            var enabled = StreamUtil.toStream(objectMapper.readValue(existing.getLayerBindingsJson(), new TypeReference<List<PropagatorLayerBinding>>() {}))
                    .map(plb -> plb.toBuilder()
                            .enabled(false)
                            .build())
                    .toList();

            existing.setLayerBindingsJson(objectMapper.writeValueAsString(enabled));
            existing.setStatus(FilterEnums.PolicyStatus.INACTIVE.name());


            var propagator = objectMapper.readValue(existing.getPropagatorJson(), new TypeReference<Propagator<?,?,?>>() {})
                    .withStatus(FilterEnums.PolicyStatus.INACTIVE);

            existing.setPropagatorJson(objectMapper.writeValueAsString(propagator));

            return repository.save(existing);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save auto AI propagator registration", e);
        }
    }

    private PropagatorRegistrationEntity enableRegistration(PropagatorRegistrationEntity existing) {
        try {
            var enabled = StreamUtil.toStream(objectMapper.readValue(existing.getLayerBindingsJson(), new TypeReference<List<PropagatorLayerBinding>>() {}))
                    .map(plb -> {
                        return plb.toBuilder()
                                .enabled(true)
                                .build();
                    })
                    .toList();

            existing.setLayerBindingsJson(objectMapper.writeValueAsString(enabled));
            existing.setStatus(FilterEnums.PolicyStatus.ACTIVE.name());
            existing.setActivatedAt(Instant.now());

            var propagator = objectMapper.readValue(existing.getPropagatorJson(), new TypeReference<Propagator<?,?,?>>() {})
                    .withStatus(FilterEnums.PolicyStatus.ACTIVE);

            existing.setPropagatorJson(objectMapper.writeValueAsString(propagator));

            return repository.save(existing);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save auto AI propagator registration", e);
        }
    }

    private void saveRegistration(String registrationId,
                                  PropagatorRegistrationRequest request,
                                  Instant now,
                                  PropagatorRegistrationEntity existing) {
        try {
            AiPropagatorTool executor = request.getExecutor();
            Propagator<?, ?, ?> propagator = buildPropagator(registrationId, request, executor, now);
            List<PropagatorLayerBinding> bindings = request.getLayerBindings().stream()
                    .map(binding -> PropagatorLayerBinding.builder()
                            .layerId(binding.getLayerId())
                            .enabled(binding.isEnabled())
                            .includeDescendants(binding.isIncludeDescendants())
                            .isInheritable(binding.isInheritable())
                            .isPropagatedToParent(binding.isPropagatedToParent())
                            .matcherKey(FilterEnums.MatcherKey.valueOf(binding.getMatcherKey()))
                            .matcherType(FilterEnums.MatcherType.valueOf(binding.getMatcherType()))
                            .matcherText(binding.getMatcherText())
                            .matchOn(PropagatorMatchOn.valueOf(binding.getMatchOn()))
                            .updatedBy("system")
                            .updatedAt(now)
                            .build())
                    .toList();

            PropagatorRegistrationEntity entity = existing == null ? new PropagatorRegistrationEntity() : existing;
            entity.setRegistrationId(registrationId);
            entity.setRegisteredBy("system");
            entity.setStatus(request.isActivate() ? FilterEnums.PolicyStatus.ACTIVE.name() : FilterEnums.PolicyStatus.INACTIVE.name());
            entity.setPropagatorKind(request.getPropagatorKind());
            entity.setInheritable(request.isInheritable());
            entity.setPropagatedToParent(request.isPropagatedToParent());
            entity.setPropagatorJson(objectMapper.writeValueAsString(propagator));
            entity.setLayerBindingsJson(objectMapper.writeValueAsString(bindings));
            entity.setActivatedAt(request.isActivate() ? now : entity.getActivatedAt());
            entity.setDeactivatedAt(request.isActivate() ? null : now);
            repository.save(entity);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to save auto AI propagator registration", e);
        }
    }

    @Transactional
    public PropagatorRegistrationResponse updateSessionMode(String registrationId, AiFilterTool.SessionMode sessionMode) {
        return repository.findByRegistrationId(registrationId)
                .map(entity -> {
                    try {
                        var node = objectMapper.readTree(entity.getPropagatorJson());
                        if (!node.has("executor")) {
                            return PropagatorRegistrationResponse.builder().ok(false).registrationId(registrationId)
                                    .message("Propagator has no executor field").build();
                        }
                        var mutable = (com.fasterxml.jackson.databind.node.ObjectNode) node;
                        var executorNode = (com.fasterxml.jackson.databind.node.ObjectNode) mutable.get("executor");
                        executorNode.put("sessionMode", sessionMode.name());
                        entity.setPropagatorJson(objectMapper.writeValueAsString(mutable));
                        repository.save(entity);
                        return PropagatorRegistrationResponse.builder().ok(true).registrationId(registrationId)
                                .status(entity.getStatus())
                                .message("Session mode updated to " + sessionMode.name()).build();
                    } catch (Exception e) {
                        log.error("Failed to update session mode for {}", registrationId, e);
                        return PropagatorRegistrationResponse.builder().ok(false).registrationId(registrationId)
                                .message(e.getMessage()).build();
                    }
                })
                .orElseGet(() -> PropagatorRegistrationResponse.builder().ok(false).registrationId(registrationId)
                        .message("Propagator not found").build());
    }

    @Transactional
    public UpdateAllSessionModeResponse updateAllSessionModes(AiFilterTool.SessionMode sessionMode) {
        int updated = 0;
        int failed = 0;
        for (PropagatorRegistrationEntity entity : repository.findAll()) {
            try {
                var node = objectMapper.readTree(entity.getPropagatorJson());
                if (!node.has("executor")) continue;
                var mutable = (com.fasterxml.jackson.databind.node.ObjectNode) node;
                var executorNode = (com.fasterxml.jackson.databind.node.ObjectNode) mutable.get("executor");
                executorNode.put("sessionMode", sessionMode.name());
                entity.setPropagatorJson(objectMapper.writeValueAsString(mutable));
                repository.save(entity);
                updated++;
            } catch (Exception e) {
                log.error("Failed to update session mode for {}", entity.getRegistrationId(), e);
                failed++;
            }
        }
        return new UpdateAllSessionModeResponse(true, updated, failed, sessionMode);
    }

    private Optional<String> extractSourcePath(PropagatorRegistrationEntity entity) {
        try {
            return Optional.ofNullable(objectMapper.readTree(entity.getPropagatorJson()).path("sourcePath").asText(null));
        } catch (Exception e) {
            log.debug("Failed to read propagator sourcePath for registration {}", entity.getRegistrationId(), e);
            return Optional.empty();
        }
    }

    private PropagatorRegistrationRequest autoRegistrationRequest(ReadPropagatorAttachableTargetsResponse.ActionTarget action,
                                                                 String stage,
                                                                 String sourcePath) {
        return PropagatorRegistrationRequest.builder()
                .name(autoName(action.layerId(), stage))
                .description("Automatically bootstrapped AI propagator for " + action.layerId() + " [" + stage + "]")
                .sourcePath(sourcePath)
                .propagatorKind("AI_TEXT")
                .priority(100)
                .isInheritable(false)
                .isPropagatedToParent(false)
                .layerBindings(List.of(PropagatorRegistrationRequest.LayerBindingRequest.builder()
                        .layerId(action.layerId())
                        .layerType(FilterEnums.LayerType.WORKFLOW_AGENT_ACTION.name())
                        .layerKey(action.methodName())
                        .enabled(true)
                        .includeDescendants(false)
                        .isInheritable(false)
                        .isPropagatedToParent(false)
                        .matcherKey(FilterEnums.MatcherKey.TEXT.name())
                        .matcherType(FilterEnums.MatcherType.EQUALS.name())
                        .matcherText(action.methodName())
                        .matchOn(stage)
                        .build()))
                .executor(new AiPropagatorTool(
                        "Escalate out-of-domain, out-of-distribution, or otherwise controller-relevant request and result payloads.",
                        AiFilterTool.SessionMode.PER_INVOCATION,
                        null
                ))
                .activate(false)
                .build();
    }

    private String autoSourcePath(String layerId, String stage) {
        return "auto://ai-propagator/" + layerId + "/" + stage;
    }

    private String autoName(String layerId, String stage) {
        return "auto-ai-propagator-" + layerId.replace('/', '-') + "-" + stage.toLowerCase();
    }

    @Transactional
    public PropagatorRegistrationEntity disableAiPropagator(PropagatorRegistrationEntity found) {
        return disableRegistration(found);
    }

    @Transactional
    public @Nullable PropagatorRegistrationEntity disableAiPropagator(String registrationId) {
        var found = this.repository.findByRegistrationId(registrationId);

        return found.map(this::disableAiPropagator).orElse(null);

    }

    @Transactional
    public PropagatorRegistrationEntity enableAiPropagator(PropagatorRegistrationEntity found) {
        return enableRegistration(found);
    }

    @Transactional
    public PropagatorRegistrationEntity enableAiPropagator(String registrationId) {
        var found = this.repository.findByRegistrationId(registrationId);

        if (found.isEmpty())
            return null;

        return enableAiPropagator(found.get());
    }
}
