package com.hayden.multiagentide.agent.decorator.prompt;

import com.embabel.agent.api.tool.Tool;
import com.embabel.common.textio.template.CompiledTemplate;
import com.embabel.common.textio.template.TemplateRenderer;
import com.google.common.collect.Lists;
import com.hayden.multiagentide.artifacts.ArtifactService;
import com.hayden.multiagentide.artifacts.ExecutionScopeService;
import com.hayden.multiagentide.filter.prompt.FilteredPromptContributorAdapter;
import com.hayden.multiagentide.filter.service.FilterDescriptor;
import com.hayden.multiagentide.tool.ToolAbstraction;
import com.hayden.multiagentide.artifact.PromptTemplateVersion;
import com.hayden.multiagentide.filter.service.FilterResult;
import com.hayden.multiagentide.prompt.FilteredPromptContributor;
import com.hayden.multiagentide.prompt.PromptContext;
import com.hayden.multiagentide.prompt.PromptContributor;
import com.hayden.multiagentide.prompt.PromptContributorAdapter;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactHashing;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Decorator that emits artifact events for LLM interactions.
 * <p>
 * This decorator hooks into the LLM call pipeline to:
 * - Emit rendered prompt artifacts before LLM calls
 * - Could be extended to emit response artifacts after LLM calls
 * - Emit tool call artifacts during execution
 * <p>
 * Artifacts are added to the execution's AgentExecutionArtifacts group.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ArtifactEmissionLlmCallDecorator implements LlmCallDecorator {

    private final ExecutionScopeService executionScopeService;

    private final ArtifactService artifactService;


    @Override
    public int order() {
        return 10_000;
    }

    @Override
    public <T> LlmCallContext<T> decorate(LlmCallContext<T> context) {
        if (context == null || context.promptContext() == null) {
            return context;
        }

        PromptContext promptContext = context.promptContext();
        emitRenderedPrompt(promptContext, context, context.op().agentPlatform().getPlatformServices().getTemplateRenderer());
        return context;
    }

    /**
     * Retrieves an existing artifact by hash or creates a new one.
     * This method recursively processes all child artifacts as well.
     *
     * @param artifactBuilder A supplier that creates the artifact (will only be called if not found in DB)
     * @param hash The content hash of the artifact
     * @param artifactKey The artifact key to use for this execution context
     * @return The artifact (either retrieved or newly created)
     */
    private Artifact getOrCreateArtifact(
            Supplier<Artifact> artifactBuilder,
            String hash,
            ArtifactKey artifactKey
    ) {
        if (hash == null || hash.isEmpty()) {
            return artifactBuilder.get();
        }

        // Check if artifact with this hash already exists
        Optional<Artifact> existingArtifact = artifactService.decorateDuplicate(hash, artifactKey);

        if (existingArtifact.isPresent()) {
            Artifact existing = existingArtifact.get();
            
            log.debug("Reusing existing artifact with hash: {} and type: {}",
                    hash, existing.artifactType());
            return existing;
        }

        // Create new artifact
        Artifact newArtifact = artifactBuilder.get();
        log.debug("Created new artifact with hash: {} and type: {}", 
                hash, newArtifact.artifactType());
        return newArtifact;
    }

    /**
     * Emits a rendered prompt artifact.
     * First checks if an artifact with the same hash exists in the database.
     * If found, reuses the existing artifact; otherwise, creates a new one.
     * Recursively checks all child artifacts as well.
     */
    private void emitRenderedPrompt(
            PromptContext promptContext,
            LlmCallContext llmCallContext,
            TemplateRenderer templateRenderer) {
        try {
            // Get the artifact key for the agent execution group
            ArtifactKey groupKey = promptContext.currentRequest().contextId();

            // Create child key for this prompt
            ArtifactKey promptKey = groupKey.createChild();

            // Extract rendered text from template operations or prompt context
            String renderedText = extractRenderedText(templateRenderer, llmCallContext);

            if (renderedText == null)
                return;

            // Compute hash of the rendered text
            String hash = promptContext.hashContext().hash(renderedText);

            // Build the prompt artifact with all children, checking hash for each
            Artifact promptArtifact = getOrCreateArtifact(
                    () -> buildRenderedPromptArtifact(promptContext, llmCallContext, promptKey, renderedText, hash),
                    hash,
                    promptKey
            );

            // Emit artifact
            executionScopeService.emitArtifact(promptArtifact, groupKey);

            log.debug("Emitted rendered prompt artifact: {} with hash: {} for agent type: {}",
                    promptKey, hash, promptContext.agentType());

        } catch (Exception e) {
            log.error("Failed to emit rendered prompt artifact", e);
        }
    }

    public record DecoratedFilterPromptContributor(@Delegate PromptContributor promptContributor, FilterResult<String> result)
            implements PromptContributor {}

    /**
     * Builds a RenderedPromptArtifact with all its children, checking hash for each child.
     */
    private Artifact.RenderedPromptArtifact buildRenderedPromptArtifact(
            PromptContext promptContext,
            LlmCallContext llmCallContext,
            ArtifactKey promptKey,
            String renderedText,
            String hash
    ) {
        ArtifactKey promptTemplateVersionKey = promptKey.createChild();

        var bareTemplate = llmCallContext.op().agentPlatform().getPlatformServices().getTemplateRenderer().load(promptContext.templateName());

        // Build PromptTemplateVersion (checking hash)
        Artifact templateVersion = getOrCreateArtifact(
                () -> PromptTemplateVersion.builder()
                        .templateStaticId(promptContext.templateName())
                        .templateArtifactKey(promptTemplateVersionKey)
                        .hash(promptContext.hashContext().hash(bareTemplate))
                        .templateText(bareTemplate)
                        .lastUpdatedAt(Instant.now())
                        .build(),
                null, // TODO: compute proper hash for template version
                promptTemplateVersionKey
        );

        // Build PromptArgsArtifact (checking hash)
        String argsHash = promptContext.hashContext().hashMap(llmCallContext.templateArgs());
        Artifact argsArtifact = getOrCreateArtifact(
                () -> Artifact.PromptArgsArtifact.builder()
                        .metadata(new HashMap<>())
                        .artifactKey(promptKey.createChild())
                        .args(llmCallContext.templateArgs())
                        .hash(argsHash)
                        .metadata(Map.of(
                                "argCount", String.valueOf(llmCallContext.templateArgs().size())
                        ))
                        .build(),
                argsHash,
                promptKey.createChild()
        );

        // Build prompt contributor artifacts (checking hash for each)
        List<Artifact> contributorArtifacts = promptContext.promptContributors()
                .stream()
                .flatMap(context -> {
                    if (context instanceof FilteredPromptContributorAdapter f) {
                        return Stream.of(new DecoratedFilterPromptContributor(f.getContributor(), f.getFilterResult()));
                    }
                    if (context instanceof PromptContributorAdapter adapter) {
                        return Stream.of(adapter.getContributor());
                    }

                    log.error("Found prompt element {} that was unknown template and could not add artifact or versioning - {}: {}.",
                            context.getPromptContributionLocation(), promptContext.agentType(), promptContext.currentContextId());
                    return Stream.empty();
                })
                .map(pc -> buildRenderedPromptContributor(promptContext, pc, promptKey.createChild()))
                .collect(Collectors.toCollection(ArrayList::new));

        // Build tool prompt artifacts (tool descriptions provided to LLM)
        List<Artifact> toolPromptArtifacts = buildToolPromptArtifacts(llmCallContext, promptKey);

        // Combine all children
        List<Artifact> allChildren = new ArrayList<>();
        allChildren.add(templateVersion);
        allChildren.add(argsArtifact);
        allChildren.addAll(contributorArtifacts);
        allChildren.addAll(toolPromptArtifacts);

        return Artifact.RenderedPromptArtifact.builder()
                .artifactKey(promptKey)
                .renderedText(renderedText)
                .hash(hash)
                .promptName(promptContext.templateName())
                .metadata(Map.of(
                        "agentType", promptContext.agentType().toString(),
                        "templateName", promptContext.templateName()
                ))
                .children(allChildren)
                .build();
    }

    /**
     * Creates tool prompt artifacts from the LlmCallContext.
     * These artifacts represent the tool descriptions that are provided to the LLM
     * as part of the prompt context (not tool call results).
     */
    private List<Artifact> buildToolPromptArtifacts(
            LlmCallContext llmCallContext,
            ArtifactKey parentKey
    ) {
        if (llmCallContext == null || llmCallContext.tcc() == null || llmCallContext.tcc().tools().isEmpty()) {
            return new ArrayList<>();
        }

        List<Artifact> artifacts = new ArrayList<>();

        // Build tool prompt artifacts
        List<Artifact> toolArtifacts = llmCallContext.tcc().tools()
                .stream()
                .flatMap(ta -> extractToolDescriptions(ta).stream())
                .map(td -> buildToolPrompt(td, parentKey.createChild(), llmCallContext.promptContext()))
                .collect(Collectors.toCollection(ArrayList::new));


        // Build skill prompt artifacts
        List<Artifact> skillArtifacts = llmCallContext.tcc().tools()
                .stream()
                .flatMap(ta -> extractSkillDescriptions(ta).stream())
                .map(sd -> buildSkillPrompt(sd, parentKey.createChild(), llmCallContext.promptContext()))
                .collect(Collectors.toCollection(ArrayList::new));

        artifacts.addAll(toolArtifacts);
        artifacts.addAll(skillArtifacts);

        return artifacts;
    }
    
    /**
     * Extracts tool descriptions from a ToolAbstraction.
     * Handles all sealed subtypes of ToolAbstraction.
     */
    private List<ToolDescription> extractToolDescriptions(ToolAbstraction toolAbstraction) {
        return switch (toolAbstraction) {
            case ToolAbstraction.SpringToolCallback stc -> {
                var def = stc.toolCallback().getToolDefinition();
                yield List.of(new ToolDescription(
                        def.name(),
                        def.description(),
                        def.inputSchema()
                ));
            }
            case ToolAbstraction.SpringToolCallbackProvider stcp -> {
                var callbacks = stcp.toolCallbackProvider().getToolCallbacks();
                yield Arrays.stream(callbacks)
                        .map(tc -> {
                            var def = tc.getToolDefinition();
                            return new ToolDescription(
                                    def.name(),
                                    def.description(),
                                    def.inputSchema()
                            );
                        })
                        .toList();
            }
            case ToolAbstraction.EmbabelTool et -> {
                var def = et.tool().getDefinition();
                yield List.of(new ToolDescription(
                        def.getName(),
                        def.getDescription(),
                        def.getInputSchema().toJsonSchema()
                ));
            }
            case ToolAbstraction.EmbabelToolObject eto -> {
                // ToolObject contains objects with @LlmTool annotated methods
                // We extract tools using Tool.safelyFromInstance
                var tools = eto.toolObject().getObjects().stream()
                        .flatMap(obj -> Tool.safelyFromInstance(obj).stream())
                        .toList();
                yield tools.stream()
                        .map(t -> {
                            var def = t.getDefinition();
                            return new ToolDescription(
                                    def.getName(),
                                    def.getDescription(),
                                    def.getInputSchema().toJsonSchema()
                            );
                        })
                        .toList();
            }
            case ToolAbstraction.EmbabelToolGroup etg -> {
                yield etg.toolGroup().getTools().stream()
                        .map(tc -> {
                            var def = tc.getDefinition();
                            return new ToolDescription(
                                    def.getName(),
                                    def.getDescription(),
                                    def.getInputSchema().toJsonSchema()
                            );
                        })
                        .toList();
            }
            case ToolAbstraction.EmbabelToolGroupRequirement etgr -> {
                // ToolGroupRequirement is just a role reference, not actual tools
                // The actual tools are resolved at runtime by ToolGroupResolver
                log.debug("ToolGroupRequirement '{}' cannot be resolved to tool descriptions without ToolGroupResolver",
                        etgr.requirement().getRole());
                yield List.of();
            }
            case ToolAbstraction.ToolGroupStrings tgs -> {
                // ToolGroupStrings are string references, not actual tools
                log.debug("ToolGroupStrings {} cannot be resolved to tool descriptions without ToolGroupResolver",
                        tgs.toolGroups());
                yield List.of();
            }
            case ToolAbstraction.SkillReference skillReference -> {
                yield List.of();
            }
        };
    }
    
    /**
     * Internal record for holding extracted tool description data.
     */
    private record ToolDescription(String name, String description, String inputSchema) {}

    /**
     * Internal record for holding extracted skill description data.
     */
    private record SkillDescription(String name, String description, String activationText) {}

    /**
     * Extracts skill descriptions from a ToolAbstraction.
     * Only handles SkillReference type, returns empty list for other types.
     */
    private List<SkillDescription> extractSkillDescriptions(ToolAbstraction toolAbstraction) {
        return switch (toolAbstraction) {
            case ToolAbstraction.SkillReference skillReference -> {
                var skillDecorator = skillReference.loadedSkills();
                if (skillDecorator != null && skillDecorator.getSkill() != null) {
                    var skills = skillDecorator.getSkill();
                    yield skills.getSkills()
                            .stream()
                            .map(ls -> new SkillDescription(
                                    ls.getName(),
                                    ls.getDescription(),
                                    ls.getActivationText()
                            ))
                            .toList();
                }
                yield List.of();
            }
            default -> List.of();
        };
    }
    
    /**
     * Creates a single tool prompt artifact with hash checking.
     */
    private Artifact buildToolPrompt(
            ToolDescription toolDescription,
            ArtifactKey artifactKey,
            PromptContext promptContext
    ) {
        // Build the full tool description text for hashing
        String fullDescription = buildToolDescriptionText(toolDescription);
        String hash = promptContext.hashContext().hash(fullDescription);
        
        return getOrCreateArtifact(
                () -> Artifact.ToolPrompt.builder()
                        .templateArtifactKey(artifactKey)
                        .toolCallName(toolDescription.name())
                        .toolDescription(fullDescription)
                        .hash(hash)
                        .metadata(Map.of(
                                "toolName", toolDescription.name(),
                                "hasInputSchema", String.valueOf(toolDescription.inputSchema() != null && !toolDescription.inputSchema().isEmpty())
                        ))
                        .children(new ArrayList<>())
                        .build(),
                hash,
                artifactKey
        );
    }
    
    /**
     * Builds a complete tool description text including name, description, and input schema.
     */
    private String buildToolDescriptionText(ToolDescription toolDescription) {
        StringBuilder sb = new StringBuilder();
        sb.append("Tool: ").append(toolDescription.name()).append("\n");
        if (toolDescription.description() != null && !toolDescription.description().isEmpty()) {
            sb.append("Description: ").append(toolDescription.description()).append("\n");
        }
        if (toolDescription.inputSchema() != null && !toolDescription.inputSchema().isEmpty()) {
            sb.append("Input Schema: ").append(toolDescription.inputSchema()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Creates a single skill prompt artifact with hash checking.
     */
    private Artifact buildSkillPrompt(
            SkillDescription skillDescription,
            ArtifactKey artifactKey,
            PromptContext promptContext
    ) {
        // Build the full skill description text for hashing
        String fullDescription = buildSkillDescriptionText(skillDescription);
        String hash = promptContext.hashContext().hash(fullDescription);

        return getOrCreateArtifact(
                () -> Artifact.SkillPrompt.builder()
                        .templateArtifactKey(artifactKey)
                        .skillName(skillDescription.name())
                        .skillDescription(fullDescription)
                        .activationText(skillDescription.activationText())
                        .hash(hash)
                        .metadata(Map.of(
                                "skillName", skillDescription.name() != null ? skillDescription.name() : ""
                        ))
                        .children(new ArrayList<>())
                        .build(),
                hash,
                artifactKey
        );
    }

    /**
     * Builds a complete skill description text including name and description.
     */
    private String buildSkillDescriptionText(SkillDescription skillDescription) {
        StringBuilder sb = new StringBuilder();
        sb.append("Skill: ").append(skillDescription.name()).append("\n");
        if (skillDescription.description() != null && !skillDescription.description().isEmpty()) {
            sb.append("Description:\n").append(skillDescription.description()).append("\n");
        }
        if (skillDescription.activationText() != null && !skillDescription.activationText().isEmpty()) {
            sb.append("Activation Text:\n").append(skillDescription.activationText()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Extracts the rendered text from template operations or builds it from prompt context.
     */
    private String extractRenderedText(TemplateRenderer templateOps, LlmCallContext llmCallContext) {
        if (templateOps == null) {
            return null;
        }

        CompiledTemplate compiledTemplate = templateOps.compileLoadedTemplate(llmCallContext.promptContext().templateName());

        return compiledTemplate
                .render(llmCallContext.templateArgs());
    }


    /**
     * Builds a RenderedPromptArtifact for a prompt contributor, checking hash for all children.
     */
    private Artifact buildRenderedPromptContributor(
            PromptContext promptContext,
            PromptContributor pc,
            ArtifactKey key
    ) {
        var renderedText = pc.contribute(promptContext);
        var hash = promptContext.hashContext().hash(renderedText);
        String contributorName = pc.name();
        Map<String, String> contributorMetadata = promptContributorMetadata(promptContext, pc);

        // Check if this contributor artifact already exists
        return getOrCreateArtifact(
                () -> {
                    var contributionTemplateKey = key.createChild();

                    // Materialize the contributor node itself so descendants do not rely on placeholder insertion.
                    var contributionArgs = buildPromptContributorTemplate(promptContext, pc, contributionTemplateKey.createChild());
                    var filterDescriptor = buildFilterDescriptor(promptContext, pc, contributionTemplateKey.createChild());

                    List<Artifact> contributionChildren = Lists.newArrayList(contributionArgs);
                    Optional.ofNullable(filterDescriptor).ifPresent(contributionChildren::add);

                    var contributionTemplate = buildPromptContributorArtifact(
                            promptContext,
                            pc,
                            contributionTemplateKey,
                            contributionChildren
                    );

                    List<Artifact> children = Lists.newArrayList(contributionTemplate);

                    return Artifact.RenderedPromptArtifact.builder()
                            .artifactKey(key)
                            .renderedText(renderedText)
                            .hash(hash)
                            .metadata(contributorMetadata)
                            .children(children)
                            .promptName(contributorName)
                            .build();
                },
                hash,
                key
        );
    }

    /**
     * Builds a PromptContributionTemplate artifact, checking hash for reuse.
     */
    private Artifact buildPromptContributorArtifact(
            PromptContext context,
            PromptContributor promptContributor,
            ArtifactKey key,
            List<Artifact> children
    ) {
        var promptContributorName = promptContributor.name();
        var contributedText = promptContributor.template();
        var hash = context.hashContext().hash(contributedText);

        return getOrCreateArtifact(
                () -> Artifact.PromptContributionTemplate.builder()
                        .templateArtifactKey(key)
                        .contributorName(promptContributorName)
                        .templateText(contributedText)
                        .hash(hash)
                        .metadata(promptContributorMetadata(context, promptContributor))
                        .children(new ArrayList<>(children))
                        .build(),
                hash,
                key
        );
    }

    /**
     * Builds a PromptArgsArtifact for a prompt contributor, checking hash for reuse.
     */
    private Artifact buildFilterDescriptor(
            PromptContext context,
            PromptContributor promptContributor,
            ArtifactKey key
    ) {
        Map<String, String> metadata = new LinkedHashMap<>(promptContributorMetadata(context, promptContributor));

        if (promptContributor instanceof DecoratedFilterPromptContributor f) {
            var descriptorView = toDescriptorView(f.result.descriptor());
            var hash = ArtifactHashing.hashJson(descriptorView);
            return getOrCreateArtifact(
                    () -> Artifact.FilterDescriptorArtifact.builder()
                            .hash(hash)
                            .children(Lists.newArrayList())
                            .descriptorView(descriptorView)
                            .artifactKey(key)
                            .metadata(metadata)
                            .build(),
                    hash,
                    key
            );
        }

        return null;
    }

    private Artifact.FilterDescriptorArtifact.FilterDescriptorView toDescriptorView(FilterDescriptor descriptor) {
        if (descriptor == null) {
            return new Artifact.FilterDescriptorArtifact.FilterDescriptorView(List.of(), List.of());
        }
        List<Artifact.FilterDescriptorArtifact.FilterDescriptorView.DescriptorEntry> descriptors = descriptor.entries().stream()
                .map(this::toDescriptorEntry)
                .toList();
        return new Artifact.FilterDescriptorArtifact.FilterDescriptorView(
                descriptor.instructions(),
                descriptors
        );
    }

    private Artifact.FilterDescriptorArtifact.FilterDescriptorView.DescriptorEntry toDescriptorEntry(
            FilterDescriptor.Entry entry) {
        return new Artifact.FilterDescriptorArtifact.FilterDescriptorView.DescriptorEntry(
                entry.descriptorType(),
                entry.policyId(),
                entry.filterId(),
                entry.filterName(),
                entry.filterKind(),
                entry.sourcePath(),
                entry.action(),
                entry.executorType(),
                entry.executorDetails(),
                entry.instructions()
        );
    }

    /**
     * Builds a PromptArgsArtifact for a prompt contributor, checking hash for reuse.
     */
    private Artifact buildPromptContributorTemplate(
            PromptContext context,
            PromptContributor promptContributor,
            ArtifactKey key
    ) {
        var hash = context.hashContext().hashMap(promptContributor.args());
        Map<String, String> metadata = new LinkedHashMap<>(promptContributorMetadata(context, promptContributor));
        metadata.put("argCount", String.valueOf(promptContributor.args().size()));
        var args = getOrCreateArtifact(
                () -> Artifact.PromptArgsArtifact.builder()
                        .hash(hash)
                        .children(Lists.newArrayList())
                        .artifactKey(key)
                        .args(promptContributor.args())
                        .metadata(metadata)
                        .build(),
                hash,
                key
        );

        return args;
    }

    private Map<String, String> promptContributorMetadata(PromptContext context, PromptContributor promptContributor) {
        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("agentType", context.agentType() != null ? context.agentType().name() : "UNKNOWN");
        metadata.put("promptContributorName", promptContributor.name());
        metadata.put("templateName", context.templateName());

        if (promptContributor instanceof FilteredPromptContributor filteredPromptContributor) {
            PromptContributor original = filteredPromptContributor.originalContributor();
            metadata.put("isFilteredContributor", "true");
            metadata.put("originalPromptContributorName", original.name());
            metadata.put("originalPromptContributorPriority", String.valueOf(original.priority()));
            if (context.hashContext() != null) {
                if (original.template() != null) {
                    metadata.put("originalPromptContributorTemplateHash", context.hashContext().hash(original.template()));
                }
            }
        } else {
            metadata.put("isFilteredContributor", "false");
        }
        return metadata;
    }
}
