package com.hayden.multiagentide.filter.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LayerRepository extends JpaRepository<LayerEntity, Long> {

    Optional<LayerEntity> findByLayerId(String layerId);

    List<LayerEntity> findByParentLayerId(String parentLayerId);

    List<LayerEntity> findByLayerType(String layerType);

    @Query("SELECT l FROM LayerEntity l WHERE l.layerKey LIKE :prefix%")
    List<LayerEntity> findByLayerKeyPrefix(@Param("prefix") String prefix);

    @Query("SELECT l FROM LayerEntity l WHERE l.depth <= :maxDepth ORDER BY l.depth ASC")
    List<LayerEntity> findByMaxDepth(@Param("maxDepth") int maxDepth);

    Optional<LayerEntity> findByLayerTypeAndLayerKey(String layerType, String layerKey);
}
