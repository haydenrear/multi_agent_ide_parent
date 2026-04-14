package com.hayden.multiagentide.filter.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PolicyRegistrationRepository extends JpaRepository<PolicyRegistrationEntity, Long> {

    Optional<PolicyRegistrationEntity> findByRegistrationId(String registrationId);

    List<PolicyRegistrationEntity> findByStatus(String status);

    List<PolicyRegistrationEntity> findByFilterKind(String filterKind);

    List<PolicyRegistrationEntity> findByStatusAndFilterKind(String status, String filterKind);

    @Query("SELECT p FROM PolicyRegistrationEntity p WHERE p.status = :status AND p.layerBindingsJson LIKE %:layerId%")
    List<PolicyRegistrationEntity> findActiveByLayerId(@Param("status") String status, @Param("layerId") String layerId);
}
