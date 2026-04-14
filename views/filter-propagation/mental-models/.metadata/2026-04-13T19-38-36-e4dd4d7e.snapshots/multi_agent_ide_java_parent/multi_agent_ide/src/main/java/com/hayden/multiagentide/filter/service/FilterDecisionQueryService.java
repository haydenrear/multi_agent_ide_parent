package com.hayden.multiagentide.filter.service;

import com.hayden.multiagentide.filter.controller.dto.ReadRecentFilteredRecordsResponse;
import com.hayden.multiagentide.filter.repository.FilterDecisionRecordEntity;
import com.hayden.multiagentide.filter.repository.FilterDecisionRecordRepository;
import com.hayden.multiagentide.filter.repository.PolicyRegistrationEntity;
import com.hayden.multiagentide.filter.repository.PolicyRegistrationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service for querying filter decision records scoped by policy and layer.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FilterDecisionQueryService {

    private final FilterDecisionRecordRepository decisionRecordRepository;
    private final PolicyRegistrationRepository policyRegistrationRepository;

    public ReadRecentFilteredRecordsResponse getRecentRecordsByPolicyAndLayer(
            String policyId, String layerId, int limit, String cursor) {

        // Verify policy exists
        Optional<PolicyRegistrationEntity> policyOpt = policyRegistrationRepository.findByRegistrationId(policyId);
        if (policyOpt.isEmpty()) {
            return ReadRecentFilteredRecordsResponse.builder()
                    .policyId(policyId)
                    .layerId(layerId)
                    .records(List.of())
                    .totalCount(0)
                    .build();
        }

        // Check if policy is inactive — still return records but they represent historical data
        PolicyRegistrationEntity policy = policyOpt.get();

        List<FilterDecisionRecordEntity> records = decisionRecordRepository
                .findByPolicyIdAndLayerIdOrderByCreatedAtDesc(policyId, layerId, PageRequest.of(0, limit));

        List<ReadRecentFilteredRecordsResponse.FilteredRecordSummary> summaries = records.stream()
                .map(r -> ReadRecentFilteredRecordsResponse.FilteredRecordSummary.builder()
                        .decisionId(r.getDecisionId())
                        .filterType(r.getFilterType())
                        .action(r.getAction())
                        .inputSummary(truncate(r.getInputJson(), 200))
                        .outputSummary(truncate(r.getOutputJson(), 200))
                        .appliedInstructionCount(countInstructions(r.getAppliedInstructionsJson()))
                        .errorMessage(r.getErrorMessage())
                        .createdAt(r.getCreatedAt())
                        .build())
                .toList();

        String nextCursor = records.size() == limit && !records.isEmpty()
                ? records.getLast().getDecisionId()
                : null;

        return ReadRecentFilteredRecordsResponse.builder()
                .policyId(policyId)
                .layerId(layerId)
                .records(summaries)
                .totalCount(summaries.size())
                .nextCursor(nextCursor)
                .build();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    private int countInstructions(String json) {
        if (json == null || json.isBlank()) return 0;
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readTree(json).size();
        } catch (Exception e) {
            return 0;
        }
    }
}
