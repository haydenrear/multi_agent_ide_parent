package com.hayden.multiagentide.filter.repository;

import com.hayden.persistence.models.JpaHibernateAuditedIded;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity for persisted policy registrations.
 * The filter definition and layer bindings are stored as JSON columns.
 */
@Entity
@Table(name = "policy_registration", indexes = {
        @Index(name = "idx_policy_reg_id", columnList = "registrationId"),
        @Index(name = "idx_policy_status", columnList = "status"),
        @Index(name = "idx_policy_filter_kind", columnList = "filterKind"),
        @Index(name = "idx_policy_registered_by", columnList = "registeredBy")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class PolicyRegistrationEntity extends JpaHibernateAuditedIded {

    @Column(nullable = false, unique = true)
    private String registrationId;

    @Column(nullable = false)
    private String registeredBy;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String filterKind;

    @Column(nullable = false)
    private boolean isInheritable;

    @Column(nullable = false)
    private boolean isPropagatedToParent;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String filterJson;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String layerBindingsJson;

    private Instant activatedAt;

    private Instant deactivatedAt;
}
