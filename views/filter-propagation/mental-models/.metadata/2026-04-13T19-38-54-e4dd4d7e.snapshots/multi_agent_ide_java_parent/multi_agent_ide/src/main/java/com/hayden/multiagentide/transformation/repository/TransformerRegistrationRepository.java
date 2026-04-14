package com.hayden.multiagentide.transformation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TransformerRegistrationRepository extends JpaRepository<TransformerRegistrationEntity, Long> {
    Optional<TransformerRegistrationEntity> findByRegistrationId(String registrationId);
    List<TransformerRegistrationEntity> findByStatus(String status);
}
