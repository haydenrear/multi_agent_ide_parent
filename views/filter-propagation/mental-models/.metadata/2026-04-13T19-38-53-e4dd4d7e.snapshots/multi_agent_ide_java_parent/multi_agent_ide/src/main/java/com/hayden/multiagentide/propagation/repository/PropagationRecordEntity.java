package com.hayden.multiagentide.propagation.repository;

import com.hayden.persistence.models.JpaHibernateAuditedIded;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "propagation_record", indexes = {
        @Index(name = "idx_prop_record_id", columnList = "recordId"),
        @Index(name = "idx_prop_record_reg", columnList = "registrationId"),
        @Index(name = "idx_prop_record_layer", columnList = "layerId"),
        @Index(name = "idx_prop_record_created", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class PropagationRecordEntity extends JpaHibernateAuditedIded {

    @Column(nullable = false, unique = true)
    private String recordId;

    @Column(nullable = false)
    private String registrationId;

    @Column(nullable = false)
    private String layerId;

    @Column(length = 30_000)
    private String sourceNodeId;

    @Column
    private String sourceType;

    @Column(nullable = false)
    private String action;

    @Column(columnDefinition = "TEXT")
    private String beforePayload;

    @Column(columnDefinition = "TEXT")
    private String afterPayload;

    @Column(length = 30_000)
    private String mode;

    @Column(length = 8000)
    private String errorMessage;

    @Column(length = 30_000)
    private String correlationKey;

    @Column(nullable = false)
    private Instant createdAt;
}
