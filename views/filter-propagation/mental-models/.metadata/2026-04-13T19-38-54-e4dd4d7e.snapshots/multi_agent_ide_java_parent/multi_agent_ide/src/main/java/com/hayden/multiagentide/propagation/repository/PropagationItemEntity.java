package com.hayden.multiagentide.propagation.repository;

import com.hayden.persistence.models.JpaHibernateAuditedIded;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "propagation_item", indexes = {
        @Index(name = "idx_prop_item_id", columnList = "itemId"),
        @Index(name = "idx_prop_item_status", columnList = "status"),
        @Index(name = "idx_prop_item_source_node", columnList = "sourceNodeId"),
        @Index(name = "idx_prop_item_corr", columnList = "correlationKey")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class PropagationItemEntity extends JpaHibernateAuditedIded {

    @Column(nullable = false, unique = true)
    private String itemId;

    @Column(nullable = false)
    private String registrationId;

    @Column(nullable = false)
    private String layerId;

    @Column(length = 30_000)
    private String sourceNodeId;

    @Column
    private String sourceName;

    @Column(length = 4000)
    private String summaryText;

    @Column(columnDefinition = "TEXT")
    private String propagatedText;

    @Column
    private String stage;

    @Column
    private String mode;

    @Column(nullable = false)
    private String status;

    @Column
    private String resolutionType;

    @Column(length = 4000)
    private String resolutionNotes;

    @Column(nullable = false, length = 30_000)
    private String correlationKey;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Column
    private Instant resolvedAt;
}
