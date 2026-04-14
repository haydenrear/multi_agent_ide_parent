package com.hayden.multiagentide.propagation.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PropagatorRegistrationRepository extends JpaRepository<PropagatorRegistrationEntity, Long>, QuerydslPredicateExecutor<PropagatorRegistrationEntity> {
    Optional<PropagatorRegistrationEntity> findByRegistrationId(String registrationId);
    List<PropagatorRegistrationEntity> findByStatus(String status);
}
