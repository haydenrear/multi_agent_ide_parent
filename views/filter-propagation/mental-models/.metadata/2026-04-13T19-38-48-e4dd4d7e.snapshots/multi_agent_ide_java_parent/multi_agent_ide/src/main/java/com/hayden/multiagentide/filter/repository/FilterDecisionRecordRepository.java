package com.hayden.multiagentide.filter.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FilterDecisionRecordRepository extends JpaRepository<FilterDecisionRecordEntity, Long> {

    List<FilterDecisionRecordEntity> findByPolicyIdOrderByCreatedAtDesc(String policyId, Pageable pageable);

    List<FilterDecisionRecordEntity> findByPolicyIdAndLayerIdOrderByCreatedAtDesc(String policyId, String layerId, Pageable pageable);

    List<FilterDecisionRecordEntity> findByDecisionId(String decisionId);
}
