package com.hayden.multiagentide.transformation.repository;

import com.hayden.persistence.models.JpaHibernateAuditedIded;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "transformation_record", indexes = {
        @Index(name = "idx_transform_record_id", columnList = "recordId"),
        @Index(name = "idx_transform_record_reg", columnList = "registrationId"),
        @Index(name = "idx_transform_record_layer", columnList = "layerId"),
        @Index(name = "idx_transform_record_created", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class TransformationRecordEntity extends JpaHibernateAuditedIded {

    @Column(nullable = false, unique = true)
    private String recordId;

    @Column(nullable = false)
    private String registrationId;

    @Column(nullable = false)
    private String layerId;

    private String controllerId;

    private String endpointId;

    @Column(nullable = false)
    private String action;

    @Column(columnDefinition = "TEXT")
    private String beforePayload;

    @Column(columnDefinition = "TEXT")
    private String afterPayload;

    @Column(length = 8000)
    private String errorMessage;

    @Column(nullable = false)
    private Instant createdAt;
}
