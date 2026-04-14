package com.hayden.multiagentide.transformation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.multiagentide.model.*;
import com.hayden.multiagentide.transformation.controller.dto.*;
import com.hayden.multiagentide.transformation.repository.TransformerRegistrationEntity;
import com.hayden.multiagentide.transformation.repository.TransformerRegistrationRepository;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.filter.model.executor.ExecutableTool;
import com.hayden.multiagentide.model.layer.AiTransformerContext;
import com.hayden.multiagentide.model.layer.ControllerEndpointTransformationContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransformerRegistrationService {

    private final TransformerRegistrationRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional
    public TransformerRegistrationResponse register(TransformerRegistrationRequest request) {
        Instant now = Instant.now();
        String registrationId = "transformer-" + UUID.randomUUID();
        try {
            ExecutableTool<?, ?, ?> executor = objectMapper.convertValue(request.getExecutor(), ExecutableTool.class);
            Transformer<?, ?, ?> transformer = buildTransformer(registrationId, request, executor, now);
            List<TransformerLayerBinding> bindings = request.getLayerBindings().stream()
                    .map(binding -> TransformerLayerBinding.builder()
                            .layerId(binding.getLayerId())
                            .enabled(binding.isEnabled())
                            .includeDescendants(binding.isIncludeDescendants())
                            .isInheritable(binding.isInheritable())
                            .isPropagatedToParent(binding.isPropagatedToParent())
                            .matcherKey(FilterEnums.MatcherKey.valueOf(binding.getMatcherKey()))
                            .matcherType(FilterEnums.MatcherType.valueOf(binding.getMatcherType()))
                            .matcherText(binding.getMatcherText())
                            .matchOn(TransformerMatchOn.valueOf(binding.getMatchOn()))
                            .updatedBy("system")
                            .updatedAt(now)
                            .build())
                    .toList();

            repository.save(TransformerRegistrationEntity.builder()
                    .registrationId(registrationId)
                    .registeredBy("system")
                    .status(request.isActivate() ? FilterEnums.PolicyStatus.ACTIVE.name() : FilterEnums.PolicyStatus.INACTIVE.name())
                    .transformerKind(request.getTransformerKind())
                    .isInheritable(request.isInheritable())
                    .isPropagatedToParent(request.isPropagatedToParent())
                    .transformerJson(objectMapper.writeValueAsString(transformer))
                    .layerBindingsJson(objectMapper.writeValueAsString(bindings))
                    .activatedAt(request.isActivate() ? now : null)
                    .build());
            return TransformerRegistrationResponse.builder().ok(true).registrationId(registrationId)
                    .status(request.isActivate() ? FilterEnums.PolicyStatus.ACTIVE.name() : FilterEnums.PolicyStatus.INACTIVE.name())
                    .message("Transformer registered")
                    .build();
        } catch (Exception e) {
            log.error("Failed to register transformer", e);
            return TransformerRegistrationResponse.builder().ok(false).message(e.getMessage()).build();
        }
    }

    @Transactional
    public DeactivateTransformerResponse deactivate(String registrationId) {
        return repository.findByRegistrationId(registrationId)
                .map(entity -> {
                    entity.setStatus(FilterEnums.PolicyStatus.INACTIVE.name());
                    entity.setDeactivatedAt(Instant.now());
                    repository.save(entity);
                    return DeactivateTransformerResponse.builder().ok(true).registrationId(registrationId).status(entity.getStatus()).message("Transformer deactivated").build();
                })
                .orElseGet(() -> DeactivateTransformerResponse.builder().ok(false).registrationId(registrationId).message("Transformer not found").build());
    }

    @Transactional
    public PutTransformerLayerResponse updateLayerBinding(String registrationId, PutTransformerLayerRequest request) {
        var entityOpt = repository.findByRegistrationId(registrationId);
        if (entityOpt.isEmpty()) {
            return PutTransformerLayerResponse.builder().ok(false).registrationId(registrationId).message("Transformer not found").build();
        }
        try {
            Instant now = Instant.now();
            List<TransformerLayerBinding> bindings = objectMapper.readValue(entityOpt.get().getLayerBindingsJson(), objectMapper.getTypeFactory().constructCollectionType(List.class, TransformerLayerBinding.class));
            var lb = request.layerBinding();
            TransformerLayerBinding replacement = TransformerLayerBinding.builder()
                    .layerId(lb.getLayerId())
                    .enabled(lb.isEnabled())
                    .includeDescendants(lb.isIncludeDescendants())
                    .isInheritable(lb.isInheritable())
                    .isPropagatedToParent(lb.isPropagatedToParent())
                    .matcherKey(FilterEnums.MatcherKey.valueOf(lb.getMatcherKey()))
                    .matcherType(FilterEnums.MatcherType.valueOf(lb.getMatcherType()))
                    .matcherText(lb.getMatcherText())
                    .matchOn(TransformerMatchOn.valueOf(lb.getMatchOn()))
                    .updatedBy("system")
                    .updatedAt(now)
                    .build();
            boolean replaced = false;
            java.util.ArrayList<TransformerLayerBinding> updated = new java.util.ArrayList<>();
            for (TransformerLayerBinding binding : bindings) {
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
            return PutTransformerLayerResponse.builder().ok(true).registrationId(registrationId).layerId(replacement.layerId()).message("Binding updated").build();
        } catch (Exception e) {
            return PutTransformerLayerResponse.builder().ok(false).registrationId(registrationId).message(e.getMessage()).build();
        }
    }

    private Transformer<?, ?, ?> buildTransformer(String registrationId, TransformerRegistrationRequest request, ExecutableTool<?, ?, ?> executor, Instant now) {
        if ("AI_TEXT".equalsIgnoreCase(request.getTransformerKind())) {
            return AiTextTransformer.builder()
                    .id(registrationId)
                    .name(request.getName())
                    .description(request.getDescription())
                    .sourcePath(request.getSourcePath())
                    .executor((ExecutableTool<AgentModels.AiTransformerRequest, AgentModels.AiTransformerResult, AiTransformerContext>) executor)
                    .status(request.isActivate() ? FilterEnums.PolicyStatus.ACTIVE : FilterEnums.PolicyStatus.INACTIVE)
                    .priority(request.getPriority())
                    .replaceEndpointResponse(request.isReplaceEndpointResponse())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
        }
        return TextTransformer.builder()
                .id(registrationId)
                .name(request.getName())
                .description(request.getDescription())
                .sourcePath(request.getSourcePath())
                .executor((ExecutableTool<String, Object, ControllerEndpointTransformationContext>) executor)
                .status(request.isActivate() ? FilterEnums.PolicyStatus.ACTIVE : FilterEnums.PolicyStatus.INACTIVE)
                .priority(request.getPriority())
                .replaceEndpointResponse(request.isReplaceEndpointResponse())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
