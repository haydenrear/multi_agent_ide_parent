package com.hayden.multiagentide.propagation.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PropagationRecordRepository extends JpaRepository<PropagationRecordEntity, Long> {
    List<PropagationRecordEntity> findByLayerIdOrderByCreatedAtDesc(String layerId, Pageable pageable);
    List<PropagationRecordEntity> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
