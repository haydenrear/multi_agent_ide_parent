package com.hayden.multiagentide.propagation.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hayden.acp_cdc_ai.acp.filter.FilterEnums;
import com.hayden.multiagentide.propagation.repository.PropagatorRegistrationEntity;
import com.hayden.multiagentide.propagation.repository.PropagatorRegistrationRepository;
import com.hayden.multiagentide.model.Propagator;
import com.hayden.multiagentide.model.PropagatorLayerBinding;
import com.hayden.multiagentide.model.PropagatorMatchOn;
import com.hayden.multiagentide.model.executor.AiPropagatorTool;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PropagatorDiscoveryService {

    private final PropagatorRegistrationRepository repository;
    private final ObjectMapper objectMapper;
    private final AiPropagatorToolHydration hydration;

    public List<PropagatorRegistrationEntity> getActivePropagatorsByLayer(String layerId) {
        return repository.findByStatus(FilterEnums.PolicyStatus.ACTIVE.name()).stream()
                .filter(entity -> bindings(entity).stream()
                        .anyMatch(binding -> layerId.equals(binding.layerId()) && binding.enabled()))
                .sorted(Comparator.comparingInt(this::priority))
                .toList();
    }

    public List<PropagatorLayerBinding> applicableBindings(PropagatorRegistrationEntity entity, String layerId, PropagatorMatchOn matchOn) {
        return bindings(entity).stream()
                .filter(PropagatorLayerBinding::enabled)
                .filter(binding -> layerId.equals(binding.layerId()))
                .filter(binding -> binding.matchOn() == matchOn)
                .toList();
    }

    public List<PropagatorLayerBinding> bindings(PropagatorRegistrationEntity entity) {
        try {
            return objectMapper.readValue(
                    entity.getLayerBindingsJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, PropagatorLayerBinding.class)
            );
        } catch (Exception e) {
            return List.of();
        }
    }

    public Optional<Propagator<?, ?, ?>> deserialize(PropagatorRegistrationEntity entity) {
        try {
            Propagator<?, ?, ?> propagator = objectMapper.readValue(entity.getPropagatorJson(), Propagator.class);
            if (propagator.executor() instanceof AiPropagatorTool aiPropagatorTool) {
                hydration.hydrate(aiPropagatorTool);
            }
            return Optional.of(propagator);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private int priority(PropagatorRegistrationEntity entity) {
        return deserialize(entity)
                .map(Propagator::priority)
                .orElse(Integer.MAX_VALUE);
    }
}
