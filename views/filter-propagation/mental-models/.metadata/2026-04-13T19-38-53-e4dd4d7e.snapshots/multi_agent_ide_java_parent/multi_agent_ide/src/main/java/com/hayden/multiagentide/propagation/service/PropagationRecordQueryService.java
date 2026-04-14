package com.hayden.multiagentide.propagation.service;

import com.hayden.multiagentide.propagation.controller.dto.ReadPropagationRecordsResponse;
import com.hayden.multiagentide.propagation.repository.PropagationRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PropagationRecordQueryService {

    private final PropagationRecordRepository repository;

    public ReadPropagationRecordsResponse recent(String layerId, int limit) {
        var pageable = PageRequest.of(0, Math.max(1, limit));
        var records = (layerId == null || layerId.isBlank()
                ? repository.findAllByOrderByCreatedAtDesc(pageable)
                : repository.findByLayerIdOrderByCreatedAtDesc(layerId, pageable)).stream()
                .map(record -> ReadPropagationRecordsResponse.RecordSummary.builder()
                        .recordId(record.getRecordId())
                        .registrationId(record.getRegistrationId())
                        .layerId(record.getLayerId())
                        .sourceType(record.getSourceType())
                        .action(record.getAction())
                        .sourceNodeId(record.getSourceNodeId())
                        .createdAt(record.getCreatedAt())
                        .build())
                .toList();
        return ReadPropagationRecordsResponse.builder().records(records).totalCount(records.size()).build();
    }
}
