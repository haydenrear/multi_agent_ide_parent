package com.hayden.multiagentide.propagation.service;

import com.hayden.multiagentide.model.PropagationAction;
import com.hayden.multiagentide.model.PropagationItemStatus;
import com.hayden.multiagentide.model.PropagationResolutionType;
import com.hayden.multiagentide.propagation.controller.dto.ResolvePropagationItemResponse;
import com.hayden.multiagentide.propagation.repository.PropagationItemEntity;
import com.hayden.multiagentide.propagation.repository.PropagationItemRepository;
import org.springframework.data.domain.PageRequest;
import com.hayden.multiagentide.propagation.repository.PropagationRecordEntity;
import com.hayden.multiagentide.propagation.repository.PropagationRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PropagationItemService {

    private final PropagationItemRepository repository;
    private final PropagationRecordRepository recordRepository;

    public Optional<PropagationItemEntity> createItemIfNeeded(String registrationId,
                                                              String layerId,
                                                              String sourceNodeId,
                                                              String sourceName,
                                                              String summaryText,
                                                              String propagatedText,
                                                              String stage,
                                                              String correlationKey) {
        Optional<PropagationItemEntity> existing = repository.findFirstByCorrelationKeyAndStatusOrderByCreatedAtDesc(
                correlationKey,
                PropagationItemStatus.PENDING.name()
        );
        if (existing.isPresent()) {
            return existing;
        }
        Instant now = Instant.now();
        PropagationItemEntity entity = PropagationItemEntity.builder()
                .itemId("prop-item-" + UUID.randomUUID())
                .registrationId(registrationId)
                .layerId(layerId)
                .sourceNodeId(sourceNodeId)
                .sourceName(sourceName)
                .summaryText(summaryText)
                .propagatedText(propagatedText)
                .stage(stage)
                .status(PropagationItemStatus.PENDING.name())
                .correlationKey(correlationKey)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return Optional.of(repository.save(entity));
    }

    public List<PropagationItemEntity> findPendingItems() {
        return repository.findByStatusOrderByCreatedAtDesc(PropagationItemStatus.PENDING.name());
    }

    private static final List<String> ACTION_STAGES = List.of("ACTION_REQUEST", "ACTION_RESPONSE");

    public List<PropagationItemEntity> recentByNode(String sourceNodeId, int limit) {
        if (sourceNodeId == null || sourceNodeId.isBlank()) {
            return List.of();
        }
        return repository.findBySourceNodeIdAndStageInOrderByCreatedAtDesc(sourceNodeId, ACTION_STAGES, PageRequest.of(0, limit));
    }

    public List<PropagationItemEntity> findPendingBySourceNodeId(String sourceNodeId) {
        if (sourceNodeId == null || sourceNodeId.isBlank()) {
            return List.of();
        }
        return repository.findBySourceNodeIdAndStatusOrderByCreatedAtDesc(sourceNodeId, PropagationItemStatus.PENDING.name());
    }

    @Transactional
    public ResolvePropagationItemResponse resolve(String itemId, PropagationResolutionType resolutionType, String notes) {
        var opt = repository.findByItemId(itemId);
        if (opt.isEmpty()) {
            return ResolvePropagationItemResponse.builder().ok(false).itemId(itemId).message("Propagation item not found").build();
        }
        PropagationItemEntity entity = opt.get();
        Instant now = Instant.now();
        entity.setStatus(PropagationItemStatus.RESOLVED.name());
        entity.setResolutionType(resolutionType.name());
        entity.setResolutionNotes(notes);
        entity.setResolvedAt(now);
        entity.setUpdatedAt(now);
        repository.save(entity);

        recordRepository.save(PropagationRecordEntity.builder()
                .recordId("prop-record-" + UUID.randomUUID())
                .registrationId(entity.getRegistrationId())
                .layerId(entity.getLayerId())
                .sourceNodeId(entity.getSourceNodeId())
                .sourceType("ACTION_RESPONSE")
                .action(PropagationAction.PROPAGATION_ACKNOWLEDGED.name())
                .beforePayload(entity.getPropagatedText())
                .afterPayload(entity.getPropagatedText())
                .mode(entity.getMode())
                .correlationKey(entity.getCorrelationKey())
                .createdAt(now)
                .build());

        return ResolvePropagationItemResponse.builder()
                .ok(true)
                .itemId(itemId)
                .status(entity.getStatus())
                .resolutionType(resolutionType.name())
                .resolvedAt(now)
                .message("Propagation item resolved")
                .build();
    }
}
