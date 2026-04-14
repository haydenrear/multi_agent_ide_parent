package com.hayden.multiagentide.filter.service;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.filter.controller.dto.ReadAttachableTargetsResponse;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.agent.BlackboardHistory;
import com.hayden.multiagentide.prompt.PromptContext;
import com.hayden.multiagentide.prompt.PromptContributor;
import com.hayden.multiagentide.prompt.PromptContributorDescriptor;
import com.hayden.multiagentide.prompt.PromptContributorFactory;
import com.hayden.multiagentide.prompt.PromptContributorRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class FilterAttachableCatalogService {

    private final PromptContributorRegistry promptContributorRegistry;
    private final List<PromptContributorFactory> promptContributorFactories;

    public ReadAttachableTargetsResponse readAttachableTargets() {
        List<String> errors = new ArrayList<>();

        List<ReadAttachableTargetsResponse.GraphEventTarget> graphEvents = graphEventTargets();
        List<ReadAttachableTargetsResponse.PromptContributorTarget> promptContributors =
                promptContributorTargets(errors);

        Set<String> promptContributorNames = new LinkedHashSet<>();
        promptContributorNames.addAll(promptContributors.stream()
                .map(ReadAttachableTargetsResponse.PromptContributorTarget::contributorName)
                .filter(Objects::nonNull)
                .toList());
        promptContributorNames.addAll(promptContributorRegistry.getDescriptors().stream()
                .map(PromptContributorDescriptor::name)
                .filter(Objects::nonNull)
                .toList());
        promptContributorNames.addAll(promptContributorFactories.stream()
                .filter(Objects::nonNull)
                .map(PromptContributorFactory::descriptors)
                .filter(Objects::nonNull)
                .flatMap(Set::stream)
                .map(PromptContributorDescriptor::name)
                .filter(Objects::nonNull)
                .toList());
        return ReadAttachableTargetsResponse.builder()
                .graphEvents(graphEvents)
                .promptContributors(promptContributors)
                .promptContributorNames(List.copyOf(promptContributorNames))
                .errors(errors)
                .build();
    }

    private List<ReadAttachableTargetsResponse.GraphEventTarget> graphEventTargets() {
        JsonSubTypes subTypes = Events.GraphEvent.class.getAnnotation(JsonSubTypes.class);
        if (subTypes == null) {
            return List.of();
        }

        return java.util.Arrays.stream(subTypes.value())
                .map(type -> ReadAttachableTargetsResponse.GraphEventTarget.builder()
                        .eventType(type.name())
                        .eventClass(type.value().getName())
                        .build())
                .sorted(Comparator.comparing(ReadAttachableTargetsResponse.GraphEventTarget::eventType))
                .toList();
    }

    private List<ReadAttachableTargetsResponse.PromptContributorTarget> promptContributorTargets(List<String> errors) {
        List<ReadAttachableTargetsResponse.PromptContributorTarget> results = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();

        for (FilterLayerCatalog.ActionDefinition actionDefinition : FilterLayerCatalog.userAttachableActionDefinitions()) {
            PromptContext context = promptContextFor(actionDefinition);
            for (PromptContributor contributor : collectPromptContributors(actionDefinition, context, errors)) {
                String key = String.join("|",
                        actionDefinition.layerId(),
                        safe(contributor.name()),
                        contributor.getClass().getName(),
                        safe(contributor.templateStaticId()));
                if (!seen.add(key)) {
                    continue;
                }
                results.add(ReadAttachableTargetsResponse.PromptContributorTarget.builder()
                        .layerId(actionDefinition.layerId())
                        .agentName(actionDefinition.agentName())
                        .actionName(actionDefinition.actionName())
                        .methodName(actionDefinition.methodName())
                        .contributorName(contributor.name())
                        .contributorClass(contributor.getClass().getName())
                        .source(resolveSource(contributor))
                        .priority(contributor.priority())
                        .templateStaticId(contributor.templateStaticId())
                        .build());
            }
        }

        results.sort(Comparator
                .comparing(ReadAttachableTargetsResponse.PromptContributorTarget::layerId)
                .thenComparing(ReadAttachableTargetsResponse.PromptContributorTarget::contributorName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ReadAttachableTargetsResponse.PromptContributorTarget::contributorClass));
        return List.copyOf(results);
    }

    private List<PromptContributor> collectPromptContributors(FilterLayerCatalog.ActionDefinition actionDefinition,
                                                              PromptContext context,
                                                              List<String> errors) {
        List<PromptContributor> contributors = new ArrayList<>(promptContributorRegistry.getContributors(context));
        if (!CollectionUtils.isEmpty(promptContributorFactories)) {
            for (PromptContributorFactory factory : promptContributorFactories) {
                if (factory == null) {
                    continue;
                }
                try {
                    List<PromptContributor> created = factory.create(context);
                    if (created != null && !created.isEmpty()) {
                        contributors.addAll(created);
                    }
                } catch (Exception e) {
                    String message = "Prompt contributor factory failed for layer %s (%s): %s"
                            .formatted(actionDefinition.layerId(), factory.getClass().getName(), e.getMessage());
                    log.warn(message, e);
                    errors.add(message);
                }
            }
        }

        contributors.sort(
                Comparator.comparingInt(PromptContributor::priority)
                        .thenComparing(PromptContributor::name, String.CASE_INSENSITIVE_ORDER)
        );
        return contributors;
    }

    private PromptContext promptContextFor(FilterLayerCatalog.ActionDefinition actionDefinition) {
        ArtifactKey actionKey = ArtifactKey.createRoot().createChild();
        AgentModels.AgentRequest currentRequest = minimalRequestFor(actionDefinition, actionKey);
        return PromptContext.builder()
                .agentType(actionDefinition.agentType())
                .currentContextId(actionKey)
                .blackboardHistory(new BlackboardHistory(new BlackboardHistory.History(), actionKey.value(), null))
                .currentRequest(currentRequest)
                .metadata(new HashMap<>())
                .model(Map.of())
                .hashContext(Artifact.HashContext.defaultHashContext())
                .agentName(actionDefinition.agentName())
                .methodName(actionDefinition.methodName())
                .agentType(actionDefinition.agentType())
                .actionName(actionDefinition.actionName())
                .build();
    }

    private AgentModels.AgentRequest minimalRequestFor(FilterLayerCatalog.ActionDefinition actionDefinition,
                                                       ArtifactKey actionKey) {
        return actionDefinition.requestTypes().stream()
                .filter(AgentModels.AgentRequest.class::isAssignableFrom)
                .map(type -> instantiateRequest(type, actionKey))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private AgentModels.AgentRequest instantiateRequest(Class<?> requestType, ArtifactKey actionKey) {
        try {
            Object builder = requestType.getMethod("builder").invoke(null);
            invokeBuilderSetter(builder, "contextId", ArtifactKey.class, actionKey);
            invokeBuilderSetter(builder, "goal", String.class, "attachable target discovery");
            Object built = builder.getClass().getMethod("build").invoke(builder);
            return built instanceof AgentModels.AgentRequest agentRequest ? agentRequest : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void invokeBuilderSetter(Object builder, String methodName, Class<?> parameterType, Object value) {
        try {
            builder.getClass().getMethod(methodName, parameterType).invoke(builder, value);
        } catch (Exception ignored) {
            // best-effort builder hydration for heterogeneous request types
        }
    }

    private String resolveSource(PromptContributor contributor) {
        return contributor.getClass().getEnclosingClass() == null
                ? "REGISTRY"
                : "FACTORY";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
