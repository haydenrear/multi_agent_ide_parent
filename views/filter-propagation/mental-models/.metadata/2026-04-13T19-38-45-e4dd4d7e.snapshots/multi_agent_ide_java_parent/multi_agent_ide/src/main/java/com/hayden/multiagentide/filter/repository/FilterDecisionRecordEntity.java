package com.hayden.multiagentide.filter.repository;

import com.hayden.persistence.models.JpaHibernateAuditedIded;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity for persisted filter decision records.
 */
@Entity
@Table(name = "filter_decision_record", indexes = {
        @Index(name = "idx_decision_id", columnList = "decisionId"),
        @Index(name = "idx_decision_policy_id", columnList = "policyId"),
        @Index(name = "idx_decision_layer_id", columnList = "layerId"),
        @Index(name = "idx_decision_action", columnList = "action"),
        @Index(name = "idx_decision_created_at", columnList = "createdAt")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class FilterDecisionRecordEntity extends JpaHibernateAuditedIded {

    @Column(nullable = false, unique = true)
    private String decisionId;

    @Column(nullable = false)
    private String policyId;

    @Column(nullable = false)
    private String filterType;

    @Column(nullable = false)
    private String layerId;

    @Column(nullable = false)
    private String action;

    @Column(columnDefinition = "TEXT")
    private String inputJson;

    @Column(columnDefinition = "TEXT")
    private String outputJson;

    @Column(columnDefinition = "TEXT")
    private String appliedInstructionsJson;

    @Column(length = 30_000)
    private String errorMessage;

    @Column(nullable = false)
    private Instant createdAt;
}
