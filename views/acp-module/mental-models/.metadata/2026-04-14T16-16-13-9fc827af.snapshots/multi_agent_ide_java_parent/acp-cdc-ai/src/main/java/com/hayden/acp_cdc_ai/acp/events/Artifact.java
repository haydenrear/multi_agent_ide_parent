package com.hayden.acp_cdc_ai.acp.events;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.hayden.acp_cdc_ai.acp.filter.Instruction;
import com.hayden.utilitymodule.schema.SpecialJsonSchemaGenerator;
import com.hayden.utilitymodule.stream.StreamUtil;
import lombok.Builder;
import lombok.With;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Base interface for all artifacts in the execution tree.
 *
 * Artifacts are immutable nodes that capture execution state:
 * - prompts, templates, arguments
 * - tool I/O
 * - configuration
 * - outcomes
 * - captured events
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public sealed interface Artifact
        permits Artifact.AgentModelArtifact, Artifact.ArtifactDbRef, Artifact.ControllerChecklistTurnArtifact, Artifact.EventArtifact, Artifact.ExecutionArtifact, Artifact.ExecutionConfigArtifact, Artifact.FilterDecisionRecordArtifact, Artifact.FilterDescriptorArtifact, Artifact.IntermediateArtifact, Artifact.OutcomeEvidenceArtifact, Artifact.PolicyDeactivationArtifact, Artifact.PolicyLayerBindingToggleArtifact, Artifact.PolicyLayerBindingUpdateArtifact, Artifact.PolicyRegistrationArtifact, Artifact.PromptArgsArtifact, Artifact.RenderedPromptArtifact, Artifact.ToolCallArtifact, MessageStreamArtifact, Templated {

    String SCHEMA = "schema";

    @JsonIgnore
    default String artifactType() {
        return this.getClass().getSimpleName();
    }

    Artifact withArtifactKey(ArtifactKey key);

    default List<Artifact> collectRecursiveChildren() {
        var l = StreamUtil.toStream(this.children())
                .collect(Collectors.toCollection(ArrayList::new));
        StreamUtil.toStream(this.children())
                .flatMap(a -> StreamUtil.toStream(a.collectRecursiveChildren()))
                .forEach(l::add);
        return l;
    }

    Artifact withChildren(List<Artifact> children);

    Artifact withHash(String hash);

    /**
     * Hierarchical, time-sortable identifier.
     */
    ArtifactKey artifactKey();

    /**
     * SHA-256 hash of content bytes (if applicable).
     */
    @JsonIgnore
    Optional<String> contentHash();

    /**
     * Optional metadata map.
     */
    Map<String, String> metadata();

    /**
     * Child artifacts (tree structure).
     */
    List<Artifact> children();

    @Builder
    @With
    record AgentModelArtifact(List<Artifact> children,
                              AgentModel agentModel,
                              Map<String, String> metadata,
                              String hash) implements Artifact {

        @Override
        public ArtifactKey artifactKey() {
            return agentModel.key();
        }

        @Override
        @JsonIgnore
        public String artifactType() {
            return agentModel().artifactType();
        }

        @Override
        public Artifact withArtifactKey(ArtifactKey key) {
            return this.withAgentModel(agentModel.withContextId(key));
        }

        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }
    }

    interface HashContext {
        String hash(String in);

        default String hashMap(Map<String, Object> doHash) {
            return ArtifactHashing.hashMap(doHash);
        }

        static HashContext defaultHashContext() {
            return ArtifactHashing::hashText;
        }
    }

    interface AgentModel extends HasContextId {

        String computeHash(HashContext hashContext);

        @JsonIgnore
        List<AgentModel> children();

        @JsonIgnore
        ArtifactKey key();

        @JsonIgnore
        default String artifactType() {
            return this.getClass().getSimpleName();
        }

        AgentModel withContextId(ArtifactKey key);

        @JsonIgnore
        <T extends AgentModel> T withChildren(List<AgentModel> c);

        @JsonIgnore
        default Map<String, String> metadata() {
            return new HashMap<>();
        }

        default Artifact toArtifact(HashContext hashContext) {
            var childArtifacts = children().stream()
                    .map(a -> a.toArtifact(hashContext))
                    .collect(Collectors.toCollection(ArrayList::new));

            if (childArtifacts.stream().noneMatch(a -> Objects.equals(a.artifactType(), SCHEMA))) {
                var schema = SpecialJsonSchemaGenerator.generateForType(this.getClass());
                childArtifacts.add(
                        Artifact.SchemaArtifact.builder()
                                .schema(schema)
                                .hash(hashContext.hash(schema))
                                .templateArtifactKey(key().createChild())
                                .metadata(new HashMap<>())
                                .build());
            }

            return new Artifact.AgentModelArtifact(childArtifacts, this, metadata(), computeHash(hashContext));
        }

    }

    @Builder(toBuilder = true)
    @With
    @Slf4j
    record TemplateDbRef(
            ArtifactKey templateArtifactKey,
            String templateStaticId,
            String hash,
            @JsonIgnore
            Templated ref,
            List<Artifact> children,
            Map<String, String> metadata,
            String artifactType
    ) implements Templated {

        //      This is the part that's deduped.
        @Override
        @JsonIgnore
        public String templateText() {
            return Optional.ofNullable(ref).map(Templated::templateText)
                    .orElseGet(() -> null);
        }

        @Override
        public Templated withArtifactKey(ArtifactKey key) {
            return withTemplateArtifactKey(key);
        }

        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash) ;
        }

    }

    @Builder(toBuilder = true)
    @With
    record ArtifactDbRef(
            ArtifactKey artifactKey,
            String hash,
            @JsonIgnore
            Artifact ref,
            List<Artifact> children,
            Map<String, String> metadata,
            String artifactType
    ) implements Artifact {

        @JsonIgnore
        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }

    }

    @Builder(toBuilder = true)
    @With
    record SchemaArtifact(
            ArtifactKey templateArtifactKey,
            String templateStaticId,
            String hash,
            String templateText,
            Map<String, String> metadata,
            String schema
    ) implements Templated {

        @Override
        public Templated withArtifactKey(ArtifactKey key) {
            return withTemplateArtifactKey(key);
        }

        @Override
        public Artifact withChildren(List<Artifact> children) {
            return this;
        }

        @Override
        @JsonIgnore
        public ArtifactKey artifactKey() {
            return templateArtifactKey;
        }

        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }

        @Override
        public List<Artifact> children() {
            return List.of();
        }
    }

    // ========== Execution Root ==========

    /**
     * Root artifact for an execution tree.
     */
    @Builder(toBuilder = true)
    @With
    record ExecutionArtifact(
            ArtifactKey artifactKey,
            String workflowRunId,
            Instant startedAt,
            Instant finishedAt,
            ExecutionStatus status,
            Map<String, String> metadata,
            List<Artifact> children,
            String hash
    ) implements Artifact {


        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }
    }

    enum ExecutionStatus {
        RUNNING, COMPLETED, FAILED, STOPPED
    }

    // ========== Execution Config ==========

    /**
     * Configuration snapshot for reconstructability.
     */
    @Builder(toBuilder = true)
    @With
    record ExecutionConfigArtifact(
            ArtifactKey artifactKey,
            String repositorySnapshotId,
            Map<String, Object> modelRefs,
            Map<String, Object> toolPolicy,
            Map<String, Object> routingPolicy,
            String hash,
            Map<String, String> metadata,
            List<Artifact> children
    ) implements Artifact {


        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }
    }

    // ========== Prompts ==========

    /**
     * Fully rendered prompt text with references to template and args.
     */
    @Builder(toBuilder = true)
    @With
    record RenderedPromptArtifact(
            ArtifactKey artifactKey,
            String renderedText,
            String hash,
            Map<String, String> metadata,
            List<Artifact> children,
            String promptName
    ) implements Artifact {


        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }
    }

    /**
     * Dynamic inputs bound into a template.
     */
    @Builder(toBuilder = true)
    @With
    record PromptArgsArtifact(
            ArtifactKey artifactKey,
            Map<String, Object> args,
            String hash,
            Map<String, String> metadata,
            List<Artifact> children
    ) implements Artifact {


        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }
    }

    /**
     * Placeholder node used to keep the artifact tree connected until the
     * concrete artifact for a key arrives later in the stream.
     */
    @Builder(toBuilder = true)
    @With
    record IntermediateArtifact(
            ArtifactKey artifactKey,
            String hash,
            Map<String, String> metadata,
            List<Artifact> children,
            String expectedArtifactType
    ) implements Artifact {

        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }
    }

    @Builder(toBuilder = true)
    @With
    record FilterDecisionRecordArtifact(
            ArtifactKey artifactKey,
            String hash,
            Map<String, String> metadata,
            List<Artifact> children,
            String inputJson,
            String outputJson,
            String instructionsJson
    ) implements Artifact {

        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }
    }

    @Builder(toBuilder = true)
    @With
    record FilterDescriptorArtifact(
            ArtifactKey artifactKey,
            String hash,
            Map<String, String> metadata,
            List<Artifact> children,
            FilterDescriptorView descriptorView
    ) implements Artifact {

        public record FilterDescriptorView(
                List<Instruction> instructions,
                List<DescriptorEntry> descriptors
        ) {
            public record DescriptorEntry(
                    String descriptorType,
                    String policyId,
                    String filterId,
                    String filterName,
                    String filterKind,
                    String sourcePath,
                    String action,
                    String executorType,
                    Map<String, String> executorDetails,
                    List<Instruction> instructions
            ) {
            }
        }

        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }
    }

    // ========== Policy Lifecycle ==========

    /**
     * Captures the persisted state of a newly registered policy.
     */
    @Builder(toBuilder = true)
    @With
    record PolicyRegistrationArtifact(
            ArtifactKey artifactKey,
            String policyId,
            String filterKind,
            String status,
            Instant registeredAt,
            String registeredBy,
            String filterJson,
            String layerBindingsJson,
            boolean activateOnCreate,
            String hash,
            Map<String, String> metadata,
            List<Artifact> children
    ) implements Artifact {

        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }
    }

    /**
     * Captures a policy deactivation event and resulting persisted status.
     */
    @Builder(toBuilder = true)
    @With
    record PolicyDeactivationArtifact(
            ArtifactKey artifactKey,
            String policyId,
            String status,
            Instant deactivatedAt,
            String hash,
            Map<String, String> metadata,
            List<Artifact> children
    ) implements Artifact {

        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }
    }

    /**
     * Captures enable/disable operations for policy layer bindings.
     */
    @Builder(toBuilder = true)
    @With
    record PolicyLayerBindingToggleArtifact(
            ArtifactKey artifactKey,
            String policyId,
            String layerId,
            boolean enabled,
            boolean includeDescendants,
            List<String> affectedDescendantLayers,
            String layerBindingsJson,
            Instant updatedAt,
            String hash,
            Map<String, String> metadata,
            List<Artifact> children
    ) implements Artifact {

        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }
    }

    /**
     * Captures a full layer binding upsert for a policy.
     */
    @Builder(toBuilder = true)
    @With
    record PolicyLayerBindingUpdateArtifact(
            ArtifactKey artifactKey,
            String policyId,
            String layerId,
            String layerBindingJson,
            String layerBindingsJson,
            Instant updatedAt,
            String hash,
            Map<String, String> metadata,
            List<Artifact> children
    ) implements Artifact {

        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }
    }

    /**
     * Single prompt contributor output.
     */
    @Builder(toBuilder = true)
    @With
    record PromptContributionTemplate(
            ArtifactKey templateArtifactKey,
            String contributorName,
            int priority,
            List<String> agentTypes,
            String templateText,
            int orderIndex,
            String hash,
            Map<String, String> metadata,
            List<Artifact> children
    ) implements Templated {

        @Override
        @JsonIgnore
        public String templateStaticId() {
            return contributorName;
        }

        @Override
        public Artifact withArtifactKey(ArtifactKey key) {
            return this.withTemplateArtifactKey(templateArtifactKey);
        }

        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }

    }

    @Builder(toBuilder = true)
    @With
    record ToolPrompt(
            ArtifactKey templateArtifactKey,
            Map<String, String> metadata,
            List<Artifact> children,
            String toolCallName,
            String toolDescription,
            String hash
    ) implements Templated {

        @Override
        public String templateStaticId() {
            return toolCallName;
        }

        @Override
        public String templateText() {
            return toolDescription;
        }

        @Override
        public Templated withArtifactKey(ArtifactKey key) {
            return withTemplateArtifactKey(key);
        }

        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }

        @Override
        public ArtifactKey templateArtifactKey() {
            return templateArtifactKey;
        }
    }

    @Builder(toBuilder = true)
    @With
    record SkillPrompt(
            ArtifactKey templateArtifactKey,
            Map<String, String> metadata,
            List<Artifact> children,
            String skillName,
            String skillDescription,
            String hash,
            String activationText
    ) implements Templated {

        @Override
        public String templateStaticId() {
            return skillName;
        }

        @Override
        public String templateText() {
            return skillDescription;
        }


        @Override
        public Artifact withArtifactKey(ArtifactKey key) {
            return withTemplateArtifactKey(key);
        }

        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }

        @Override
        public ArtifactKey templateArtifactKey() {
            return templateArtifactKey;
        }
    }

    // ========== Tools ==========

    /**
     * Tool invocation with input/output.
     */
    @Builder(toBuilder = true)
    @With
    record ToolCallArtifact(
            ArtifactKey artifactKey,
            String toolCallId,
            String toolName,
            String inputJson,
            String inputHash,
            String outputJson,
            String outputHash,
            String error,
            Map<String, String> metadata,
            List<Artifact> children
    ) implements Artifact {

        @Override
        public Artifact withHash(String hash) {
            return this.toBuilder()
                    .inputHash(hash)
                    .build();
        }


        @Override
        public Optional<String> contentHash() {
            // Could hash combined input+output
            return Optional.ofNullable(inputHash);
        }
    }

    // ========== Outcomes ==========

    /**
     * Objective evidence for outcomes.
     */
    @Builder(toBuilder = true)
    @With
    record OutcomeEvidenceArtifact(
            ArtifactKey artifactKey,
            String evidenceType,
            String payload,
            String hash,
            Map<String, String> metadata,
            List<Artifact> children
    ) implements Artifact {


        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }
    }

    // ========== Controller Checklist ==========

    /**
     * Audit trail artifact for controller checklist turns during justification conversations.
     * Persisted for observability only — NOT passed to the model.
     */
    @Builder(toBuilder = true)
    @With
    record ControllerChecklistTurnArtifact(
            ArtifactKey artifactKey,
            ArtifactKey targetAgentKey,
            String targetAgentType,
            String checklistActionType,
            String completedStep,
            String stepDescription,
            String controllerMessage,
            ArtifactKey conversationKey,
            Instant timestamp,
            String hash,
            Map<String, String> metadata,
            List<Artifact> children
    ) implements Artifact {

        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }
    }

    // ========== Events ==========

    /**
     * Captured GraphEvent as source artifact.
     */
    @Builder(toBuilder = true)
    @With
    record EventArtifact(
            ArtifactKey artifactKey,
            String eventId,
            Instant eventTimestamp,
            String eventType,
            Map<String, Object> payloadJson,
            String hash,
            Map<String, String> metadata,
            List<Artifact> children
    ) implements Artifact {


        @Override
        public Optional<String> contentHash() {
            return Optional.ofNullable(hash);
        }
    }


}
