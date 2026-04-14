package com.hayden.multiagentide.transformation.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransformationRecordRepository extends JpaRepository<TransformationRecordEntity, Long> {
    List<TransformationRecordEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
    List<TransformationRecordEntity> findByLayerIdOrderByCreatedAtDesc(String layerId, Pageable pageable);
}
