package com.hayden.multiagentide.transformation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.multiagentide.transformation.repository.TransformerRegistrationEntity;
import com.hayden.multiagentide.transformation.repository.TransformerRegistrationRepository;
import com.hayden.multiagentide.model.Transformer;
import com.hayden.multiagentide.model.TransformerLayerBinding;
import com.hayden.multiagentide.model.TransformerMatchOn;
import com.hayden.multiagentide.model.executor.AiTransformerTool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TransformerDiscoveryService {

    private final TransformerRegistrationRepository repository;
    private final ObjectMapper objectMapper;
    private final AiTransformerToolHydration hydration;

    public List<TransformerRegistrationEntity> getActiveTransformersByLayer(String layerId) {
        return repository.findByStatus(FilterEnums.PolicyStatus.ACTIVE.name()).stream()
                .filter(entity -> bindings(entity).stream().anyMatch(binding -> layerId.equals(binding.layerId()) && binding.enabled()))
                .sorted(Comparator.comparingInt(this::priority))
                .toList();
    }

    public List<TransformerLayerBinding> applicableBindings(TransformerRegistrationEntity entity, String layerId, TransformerMatchOn matchOn) {
        return bindings(entity).stream()
                .filter(binding -> binding.enabled())
                .filter(binding -> layerId.equals(binding.layerId()))
                .filter(binding -> binding.matchOn() == matchOn)
                .toList();
    }

    public List<TransformerLayerBinding> bindings(TransformerRegistrationEntity entity) {
        try {
            return objectMapper.readValue(
                    entity.getLayerBindingsJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, TransformerLayerBinding.class)
            );
        } catch (Exception e) {
            return List.of();
        }
    }

    public Optional<Transformer<?, ?, ?>> deserialize(TransformerRegistrationEntity entity) {
        try {
            Transformer<?, ?, ?> transformer = objectMapper.readValue(entity.getTransformerJson(), Transformer.class);
            if (transformer.executor() instanceof AiTransformerTool aiTransformerTool) {
                hydration.hydrate(aiTransformerTool);
            }
            return Optional.of(transformer);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private int priority(TransformerRegistrationEntity entity) {
        return deserialize(entity)
                .map(Transformer::priority)
                .orElse(Integer.MAX_VALUE);
    }
}
