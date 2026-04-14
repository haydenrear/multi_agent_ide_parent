package com.hayden.multiagentide.prompt;

import com.embabel.agent.api.common.ContextualPromptElement;
import com.embabel.agent.api.common.OperationContext;
import com.embabel.common.textio.template.CompiledTemplate;
import com.embabel.common.textio.template.TemplateRenderer;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.agent.AgentType;
import com.hayden.multiagentide.agent.ErrorDescriptor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service that provides PromptContributors adapted for use with Embabel's
 * native prompt contribution system.
 * 
 * Usage:
 * <pre>
 * var contributors = promptContributorService.getContributors(AgentType.ORCHESTRATOR);
 * 
 * var result = context.ai()
 *     .withDefaultLlm()
 *     .withPromptElements(contributors)
 *     .withTemplate("workflow/orchestrator")
 *     .createObject(MyClass.class, model);
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptContributorService {
    
    private final PromptContributorRegistry registry;

    private final List<PromptContributorFactory> factories;

    private final PromptContributorAdapterFactory filteredPromptContributorAdapterFactory;

    @Autowired
    @Lazy
    private EventBus eventBus;
    
    /**
     * Get contributors for the given prompt context.
     * 
     * @param promptContext The prompt context
     * @return List of ContextualPromptElements
     */
    public List<ContextualPromptElement> getContributors(PromptContext promptContext) {
        AgentType agentType = promptContext.agentType();
        if (agentType == null) {
            promptContext = promptContext.toBuilder().agentType(AgentType.ALL).build();
        }

        return retrievePromptContributors(promptContext);
    }

    /**
     * Assembles the full prompt text: rendered template + prompt contributors in priority order.
     * Applies retry-aware filtering when an error descriptor is present on the prompt context.
     *
     * @param promptContext the prompt context (carries error descriptor for retry filtering)
     * @param templateModel template variables for rendering
     * @param context operation context for template renderer access
     * @return the assembled prompt text
     */
    public String assemblePrompt(PromptContext promptContext, Map<String, Object> templateModel, OperationContext context) {
        StringBuilder sb = new StringBuilder();

        // 1. Render the template
        String templateText = renderTemplate(promptContext, templateModel, context);
        if (templateText != null && !templateText.isBlank()) {
            sb.append(templateText.trim());
        }

        // 2. Resolve and render prompt contributors (retry filtering happens inside getContributors)
        List<ContextualPromptElement> elements = getContributors(promptContext);
        List<PromptContributor> contributors = unwrapContributors(elements);
        contributors.sort(Comparator.comparingInt(PromptContributor::priority));

        for (int i = 0; i < contributors.size(); i++) {
            PromptContributor pc = contributors.get(i);
            String content;
            try {
                content = pc.contribute(promptContext);
            } catch (Exception e) {
                log.debug("Contributor {} threw during prompt assembly — skipping", pc.name(), e);
                continue;
            }
            if (content == null || content.isBlank()) {
                continue;
            }
            if (i == 0) {
                sb.append("\n\n--- start [").append(pc.name()).append("] ---\n");
            } else {
                sb.append("\n--- end [").append(contributors.get(i - 1).name()).append("] ");
                sb.append("--- start [").append(pc.name()).append("] ---\n");
            }
            sb.append(content.trim());
        }

        if (!contributors.isEmpty()) {
            sb.append("\n--- end [").append(contributors.getLast().name()).append("] ---");
        }

        String result = sb.toString().trim();
        if (result.isEmpty() && promptContext.currentRequest() != null) {
            String goal = promptContext.currentRequest().goalExtraction();
            if (goal != null && !goal.isBlank()) {
                return goal;
            }
            if (promptContext.currentRequest() instanceof AgentModels.AgentToControllerRequest acr
                    && acr.justificationMessage() != null) {
                return acr.justificationMessage();
            }
        }
        return result;
    }

    private String renderTemplate(PromptContext promptContext, Map<String, Object> templateModel, OperationContext context) {
        try {
            TemplateRenderer renderer = context.agentPlatform().getPlatformServices().getTemplateRenderer();
            if (renderer == null) {
                return null;
            }
            CompiledTemplate compiled = renderer.compileLoadedTemplate(promptContext.templateName());
            return compiled.render(templateModel);
        } catch (Exception e) {
            log.debug("Could not render template for prompt assembly", e);
            return null;
        }
    }

    private List<PromptContributor> unwrapContributors(List<ContextualPromptElement> elements) {
        List<PromptContributor> result = new ArrayList<>();
        if (elements == null) {
            return result;
        }
        for (var element : elements) {
            if (element instanceof PromptContributorAdapter adapter) {
                result.add(adapter.getContributor());
            }
        }
        return result;
    }

    private @NonNull List<ContextualPromptElement> retrievePromptContributors(PromptContext promptContext) {
        ErrorDescriptor error = promptContext.errorDescriptor();
        boolean isRetry = error != null && !(error instanceof ErrorDescriptor.NoError);

        List<PromptContributor> contributors = new java.util.ArrayList<>(registry.getContributors(promptContext));
        if (!CollectionUtils.isEmpty(factories)) {
            for (PromptContributorFactory factory : factories) {
                if (factory == null) {
                    continue;
                }
                // On retry, skip factories that don't opt in for this error type
                if (isRetry && !includeRetryAware(factory, error)) {
                    log.debug("Excluding factory {} on retry (RetryAware returned false for {})",
                            factory.getClass().getSimpleName(), error.getClass().getSimpleName());
                    continue;
                }
                try {
                    List<PromptContributor> created = factory.create(promptContext);
                    if (created != null && !created.isEmpty()) {
                        contributors.addAll(created);
                    }
                } catch (Exception e) {
                    log.error("Error {}.", e.getMessage(), e);
                    eventBus.publish(Events.NodeErrorEvent.err("Error with prompt contributor factory %s: %s.".formatted(factory.getClass().getName(), e.getMessage()),
                            promptContext.currentContextId()));
                }
            }
        }

        // On retry, filter individual contributors by RetryAware
        if (isRetry) {
            contributors = filterForRetry(contributors, error);
        }

        contributors.sort(
                Comparator.comparingInt(PromptContributor::priority)
                        .thenComparing(PromptContributor::name, String.CASE_INSENSITIVE_ORDER));

        return contributors.stream()
                .map(contributor -> filteredPromptContributorAdapterFactory.create(contributor, promptContext))
                .collect(Collectors.toList());
    }

    /**
     * Filters prompt contributors for retry scenarios. Since PromptContributor extends RetryAware,
     * all contributors have default methods that return false — meaning they are excluded on retry
     * unless they explicitly opt in for the specific error type.
     */
    List<PromptContributor> filterForRetry(List<PromptContributor> contributors, ErrorDescriptor error) {
        List<PromptContributor> filtered = new ArrayList<>();
        for (PromptContributor pc : contributors) {
            if (includeRetryAware(pc, error)) {
                filtered.add(pc);
            } else {
                log.debug("Excluding contributor {} on retry (RetryAware returned false for {})",
                        pc.name(), error.getClass().getSimpleName());
            }
        }
        return filtered;
    }

    /**
     * Checks whether a RetryAware instance opts in for the given error type.
     */
    private boolean includeRetryAware(RetryAware ra, ErrorDescriptor error) {
        return switch (error) {
            case ErrorDescriptor.CompactionError e ->
                    e.compactionStatus() == ErrorDescriptor.CompactionStatus.MULTIPLE
                            ? ra.includeOnCompactionMultiple(e)
                            : ra.includeOnCompaction(e);
            case ErrorDescriptor.ParseError e -> ra.includeOnParseError(e);
            case ErrorDescriptor.TimeoutError e -> ra.includeOnTimeout(e);
            case ErrorDescriptor.UnparsedToolCallError e -> ra.includeOnUnparsedToolCall(e);
            case ErrorDescriptor.NullResultError e -> ra.includeOnNullResult(e);
            case ErrorDescriptor.IncompleteJsonError e -> ra.includeOnIncompleteJson(e);
            case ErrorDescriptor.NoError _ -> true;
        };
    }
}
