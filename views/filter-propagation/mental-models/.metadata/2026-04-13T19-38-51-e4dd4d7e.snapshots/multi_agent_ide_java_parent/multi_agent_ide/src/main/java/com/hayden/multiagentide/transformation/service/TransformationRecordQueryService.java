package com.hayden.multiagentide.transformation.service;

import com.hayden.multiagentide.transformation.controller.dto.ReadTransformationRecordsResponse;
import com.hayden.multiagentide.transformation.repository.TransformationRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TransformationRecordQueryService {

    private final TransformationRecordRepository repository;

    public ReadTransformationRecordsResponse recent(String layerId, int limit) {
        var pageable = PageRequest.of(0, Math.max(1, limit));
        var records = (layerId == null || layerId.isBlank()
                ? repository.findAllByOrderByCreatedAtDesc(pageable)
                : repository.findByLayerIdOrderByCreatedAtDesc(layerId, pageable)).stream()
                .map(record -> ReadTransformationRecordsResponse.RecordSummary.builder()
                        .recordId(record.getRecordId())
                        .registrationId(record.getRegistrationId())
                        .layerId(record.getLayerId())
                        .controllerId(record.getControllerId())
                        .endpointId(record.getEndpointId())
                        .action(record.getAction())
                        .createdAt(record.getCreatedAt())
                        .build())
                .toList();
        return ReadTransformationRecordsResponse.builder().records(records).totalCount(records.size()).build();
    }
}
