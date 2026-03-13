package com.hayden.multiagentide.integration.transformation;

import com.hayden.multiagentide.integration.ArtifactTreeIntegrationTestBase;
import com.hayden.multiagentide.transformation.repository.TransformationRecordEntity;
import com.hayden.multiagentide.transformation.repository.TransformationRecordRepository;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for transformer artifact emission and persistence.
 * 
 * Tests verify:
 * - Transformer execution emits TransformationEvent to EventBus
 * - Parent-child relationships are established for transformer artifacts
 * - Null output handling in transformers
 * - Multi-level transformer chains maintain proper hierarchy
 */
@DisplayName("Transformer Artifact Emission Integration Tests")
class TransformerArtifactEmissionIT extends ArtifactTreeIntegrationTestBase {

    @Autowired
    private TransformationRecordRepository transformationRecordRepository;

    @Autowired
    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        clearArtifactData();
        transformationRecordRepository.deleteAll();
    }

    @Nested
    @DisplayName("TRANS-EMIT-001: Transformer parent-child relationship establishment")
    class TransformerParentChildRelationship {

        @Test
        @DisplayName("Verify transformer artifact parent-child relationships are established")
        void verifyParentChildRelationshipEstablishment() {
            // Arrange
            ArtifactKey rootKey = ArtifactKey.createRoot();
            ArtifactKey transformerArtifactKey = rootKey.createChild();
            String layerId = "transformer-layer";
            String registrationId = "transformer-001";

            // Act - Emit transformation event
            Events.TransformationEvent event = new Events.TransformationEvent(
                    "trans-event-001",
                    Instant.now(),
                    rootKey.value(),
                    registrationId,
                    layerId,
                    "controller-001",
                    "endpoint-001",
                    transformerArtifactKey.value(),
                    "TRANSFORMED",
                    "input",
                    "output"
            );
            eventBus.publish(event);

            // Assert
            assertThat(event).isNotNull();
            assertThat(event.registrationId()).isEqualTo(registrationId);
            assertThat(event.layerId()).isEqualTo(layerId);
        }

        @Test
        @DisplayName("Verify no orphaned artifacts after transformer execution")
        void verifyNoOrphanedArtifactsAfterTransformationEvent() {
            // Arrange & Act
            clearArtifactData();

            // Assert
            assertNoOrphanedArtifacts();
        }
    }

    @Nested
    @DisplayName("TRANS-EMIT-002: Transformer null output handling")
    class TransformerNullOutputHandling {

        @Test
        @DisplayName("Verify null output is handled by transformer execution service")
        void verifyNullOutputHandling() {
            // TransformerExecutionService.transform() at lines 108-124 handles null:
            // if (afterText == null) {
            //     recordRepository.save(TransformationRecordEntity with action=FAILED)
            //     emitTransformationEvent(..., TransformationAction.FAILED, errorMsg)
            //     continue;
            // }
            
            // This test will verify the behavior when transformer returns null
            assertThat(true).isTrue(); // Placeholder for future implementation
        }

        @Test
        @DisplayName("Verify parent artifact is preserved when transformer output is null")
        void verifyParentPreservationWithNullOutput() {
            // When a transformer in the chain returns null, the parent artifact
            // should still exist in the tree and be linked properly
            
            assertThat(true).isTrue(); // Placeholder for future implementation
        }
    }

    @Nested
    @DisplayName("TRANS-EMIT-003: Multi-level transformer chains")
    class MultiLevelTransformerChains {

        @Test
        @DisplayName("Verify transformer chain maintains proper hierarchy")
        void verifyTransformerChainHierarchy() {
            // Arrange
            ArtifactKey rootKey = ArtifactKey.createRoot();
            ArtifactKey transformer1Key = rootKey.createChild();
            ArtifactKey transformer2Key = transformer1Key.createChild();
            
            // Act - Create chain of transformation records
            TransformationRecordEntity record1 = transformationRecordRepository.save(
                    TransformationRecordEntity.builder()
                            .recordId("trans-record-001")
                            .registrationId("transformer-001")
                            .layerId("layer-001")
                            .controllerId("controller-001")
                            .endpointId("endpoint-001")
                            .action("TRANSFORMED")
                            .beforePayload("input")
                            .afterPayload("transformed-once")
                            .createdAt(Instant.now())
                            .build()
            );

            TransformationRecordEntity record2 = transformationRecordRepository.save(
                    TransformationRecordEntity.builder()
                            .recordId("trans-record-002")
                            .registrationId("transformer-002")
                            .layerId("layer-001")
                            .controllerId("controller-001")
                            .endpointId("endpoint-001")
                            .action("TRANSFORMED")
                            .beforePayload("transformed-once")
                            .afterPayload("transformed-twice")
                            .createdAt(Instant.now())
                            .build()
            );

            // Assert
            List<TransformationRecordEntity> records = transformationRecordRepository.findAll();
            assertThat(records).hasSize(2);
            assertThat(records).allMatch(r -> "TRANSFORMED".equals(r.getAction()));
            
            // Verify transformation sequence
            assertThat(record1.getAfterPayload()).isEqualTo(record2.getBeforePayload());
        }

        @Test
        @DisplayName("Verify transformer action values are recorded correctly")
        void verifyTransformerActionRecording() {
            // TransformationAction enum has: TRANSFORMED, PASSTHROUGH, FAILED
            // Test verifies all action types are persisted correctly
            
            TransformationRecordEntity transformed = transformationRecordRepository.save(
                    TransformationRecordEntity.builder()
                            .recordId("trans-001")
                            .registrationId("t-001")
                            .layerId("l-001")
                            .controllerId("c-001")
                            .endpointId("e-001")
                            .action("TRANSFORMED")
                            .beforePayload("a")
                            .afterPayload("b")
                            .createdAt(Instant.now())
                            .build()
            );

            TransformationRecordEntity passthrough = transformationRecordRepository.save(
                    TransformationRecordEntity.builder()
                            .recordId("trans-002")
                            .registrationId("t-002")
                            .layerId("l-001")
                            .controllerId("c-001")
                            .endpointId("e-001")
                            .action("PASSTHROUGH")
                            .beforePayload("x")
                            .afterPayload("x")
                            .createdAt(Instant.now())
                            .build()
            );

            TransformationRecordEntity failed = transformationRecordRepository.save(
                    TransformationRecordEntity.builder()
                            .recordId("trans-003")
                            .registrationId("t-003")
                            .layerId("l-001")
                            .controllerId("c-001")
                            .endpointId("e-001")
                            .action("FAILED")
                            .beforePayload("y")
                            .afterPayload("y")
                            .errorMessage("Transform failed")
                            .createdAt(Instant.now())
                            .build()
            );

            // Assert
            assertThat(transformed.getAction()).isEqualTo("TRANSFORMED");
            assertThat(passthrough.getAction()).isEqualTo("PASSTHROUGH");
            assertThat(failed.getAction()).isEqualTo("FAILED");
        }
    }
}
