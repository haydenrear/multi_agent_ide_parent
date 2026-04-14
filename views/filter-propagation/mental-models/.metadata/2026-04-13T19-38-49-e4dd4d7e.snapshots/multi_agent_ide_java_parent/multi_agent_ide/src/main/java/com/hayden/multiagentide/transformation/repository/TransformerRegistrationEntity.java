package com.hayden.multiagentide.transformation.repository;

import com.hayden.persistence.models.JpaHibernateAuditedIded;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "transformer_registration", indexes = {
        @Index(name = "idx_transformer_reg_id", columnList = "registrationId"),
        @Index(name = "idx_transformer_reg_status", columnList = "status"),
        @Index(name = "idx_transformer_reg_kind", columnList = "transformerKind")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class TransformerRegistrationEntity extends JpaHibernateAuditedIded {

    @Column(nullable = false, unique = true)
    private String registrationId;

    @Column(nullable = false)
    private String registeredBy;

    @Column(nullable = false)
    private String status;

    @Column(nullable = false)
    private String transformerKind;

    @Column(nullable = false)
    private boolean isInheritable;

    @Column(nullable = false)
    private boolean isPropagatedToParent;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String transformerJson;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String layerBindingsJson;

    @Column
    private Instant activatedAt;

    @Column
    private Instant deactivatedAt;
}
