package com.hayden.multiagentide.propagation.repository;

import com.hayden.persistence.models.JpaHibernateAuditedIded;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "propagator_registration", indexes = {
        @Index(name = "idx_prop_reg_id", columnList = "registrationId"),
        @Index(name = "idx_prop_reg_status", columnList = "status"),
        @Index(name = "idx_prop_reg_kind", columnList = "propagatorKind")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class PropagatorRegistrationEntity extends JpaHibernateAuditedIded {

    @Column(nullable = false, unique = true)
    private String registrationId;

    @Column(nullable = false)
    private String registeredBy;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String propagatorKind;

    @Column(nullable = false)
    private boolean isInheritable;

    @Column(nullable = false)
    private boolean isPropagatedToParent;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String propagatorJson;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String layerBindingsJson;

    private Instant activatedAt;

    private Instant deactivatedAt;
}
