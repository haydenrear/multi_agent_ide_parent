package com.hayden.multiagentide.filter.repository;

import com.hayden.persistence.models.JpaHibernateAuditedIded;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * JPA entity for persisted layer hierarchy nodes.
 */
@Entity
@Table(name = "filter_layer", indexes = {
        @Index(name = "idx_layer_id", columnList = "layerId"),
        @Index(name = "idx_layer_type", columnList = "layerType"),
        @Index(name = "idx_layer_key", columnList = "layerKey"),
        @Index(name = "idx_layer_parent", columnList = "parentLayerId"),
        @Index(name = "idx_layer_depth", columnList = "depth")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder(toBuilder = true)
public class LayerEntity extends JpaHibernateAuditedIded {

    @Column(nullable = false, unique = true)
    private String layerId;

    @Column(nullable = false)
    private String layerType;

    @Column(nullable = false)
    private String layerKey;

    private String parentLayerId;

    @ElementCollection
    @CollectionTable(
            name = "filter_layer_child_ids",
            joinColumns = @JoinColumn(name = "layer_entity_id", nullable = false)
    )
    @Column(name = "child_layer_id", nullable = false)
    @OrderColumn(name = "child_order")
    @Builder.Default
    private List<String> childLayerIds = new ArrayList<>();

    @Column(nullable = false)
    @Builder.Default
    private boolean isInheritable = false;

    @Column(nullable = false)
    @Builder.Default
    private boolean isPropagatedToParent = false;

    @Column(nullable = false)
    private int depth;

    @Column(columnDefinition = "TEXT")
    private String metadataJson;
}
