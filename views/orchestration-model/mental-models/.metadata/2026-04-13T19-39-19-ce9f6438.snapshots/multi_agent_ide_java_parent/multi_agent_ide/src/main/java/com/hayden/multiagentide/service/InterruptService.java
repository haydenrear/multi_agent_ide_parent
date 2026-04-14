package com.hayden.multiagentide.service;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.agent.decorator.prompt.PromptContextDecorator;
import com.hayden.multiagentide.agent.decorator.tools.ToolContextDecorator;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.acp_cdc_ai.permission.IPermissionGate;
import com.hayden.multiagentide.gate.PermissionGate;
import com.hayden.multiagentide.repository.GraphRepository;
import com.hayden.multiagentide.llm.AgentLlmExecutor.DirectExecutorArgs;
import com.hayden.multiagentide.tool.ToolContext;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.agent.AgentType;
import com.hayden.multiagentide.prompt.PromptContext;
import com.hayden.multiagentide.prompt.PromptContextFactory;
import com.hayden.multiagentide.agent.BlackboardHistory;
import com.hayden.multiagentide.model.nodes.GraphNode;
import com.hayden.multiagentide.model.nodes.InterruptContext;
import com.hayden.multiagentide.model.nodes.InterruptNode;
import com.hayden.multiagentide.model.nodes.Interruptible;
import com.hayden.multiagentide.model.nodes.ReviewNode;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import static com.hayden.multiagentide.agent.decorator.request.DecorateRequestResults.decoratePromptContext;
import static com.hayden.multiagentide.agent.decorator.request.DecorateRequestResults.decorateToolContext;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterruptService {

    public static final String TEMPLATE_REVIEW_RESOLUTION = "workflow/review_resolution";
    public static final String AGENT_NAME = "interrupt-service";
    public static final String ACTION_AGENT_REVIEW = "agent-review";
    public static final String METHOD_RUN_INTERRUPT_AGENT_REVIEW = "runInterruptAgentReview";
    public static final String METHOD_HANDLE_INTERRUPT = "handleInterrupt";
    public static final String INTERRUPT_ID_NOT_FOUND = "interrupt_id_not_found";

    private final PermissionGate permissionGate;
    private final PromptContextFactory promptContextFactory;
    private final List<PromptContextDecorator> promptContextDecorators;
    private final List<ToolContextDecorator> toolContextDecorators;
    private final GraphRepository graphRepository;

    @Autowired
    @Lazy
    private AgentExecutor agentLlmExecutor;

    /**
     * Handle review interrupt using Jinja templates and PromptContext.
     * 
     * @param context The operation context
     * @param request The interrupt request
     * @param originNode The originating graph node
     * @param templateName The Jinja template name (e.g., "workflow/orchestrator")
     * @param promptContext The prompt context for contributors
     * @param templateModel The model data for template rendering
     * @param routingClass The expected routing class
     * @param <T> The routing type
     * @return Optional routing result if interrupt was handled
     */
    public <T> T handleInterrupt(
            OperationContext context,
            AgentModels.InterruptRequest request,
            GraphNode originNode,
            String templateName,
            PromptContext promptContext,
            Map<String, Object> templateModel,
            ToolContext toolContext,
            Class<T> routingClass
    ) {
        return switch (request.type()) {
            case HUMAN_REVIEW, AGENT_REVIEW, PAUSE, BRANCH, STOP, PRUNE -> {
                log.info("Handling feedback from AI.");
                String feedback = resolveInterruptFeedback(context, request, originNode, promptContext);
                log.info("Resolved feedback: {}.", feedback);

                var args = buildInterruptArgs(context, request, feedback, promptContext, templateModel, toolContext, routingClass);

                var s = agentLlmExecutor.runDirect(
                        args.toBuilder()
                                .refresh(() -> buildInterruptArgs(context, request, feedback, promptContext, templateModel, toolContext, routingClass))
                                .build()
                );

                log.info("After feedback handled: {}, {}.", s.getClass().getSimpleName(), s);
                yield s;
            }
        };
    }

    private <T> DirectExecutorArgs<T> buildInterruptArgs(OperationContext context, AgentModels.InterruptRequest request, String feedback, PromptContext promptContext, Map<String, Object> templateModel, ToolContext toolContext, Class<T> routingClass) {
        Map<String, Object> modelWithFeedback = new java.util.HashMap<>(templateModel);
        modelWithFeedback.put("interruptFeedback", feedback);
        DecoratorContext decoratorContext = new DecoratorContext(
                context, AGENT_NAME, ACTION_AGENT_REVIEW, METHOD_RUN_INTERRUPT_AGENT_REVIEW, promptContext.previousRequest(), promptContext.currentRequest()
        );
        promptContext = promptContextFactory.build(
                AgentType.REVIEW_RESOLUTION_AGENT,
                promptContext.currentRequest(),
                promptContext.previousRequest(),
                promptContext.currentRequest(),
                promptContext.blackboardHistory(),
                TEMPLATE_REVIEW_RESOLUTION,
                modelWithFeedback,
                context,
                decoratorContext
        );
        promptContext = decoratePromptContext(
                promptContext,
                promptContextDecorators,
                decoratorContext
        );

        toolContext = decorateToolContext(
                toolContext,
                request,
                promptContext.previousRequest(),
                context,
                toolContextDecorators,
                AGENT_NAME,
                ACTION_AGENT_REVIEW,
                METHOD_RUN_INTERRUPT_AGENT_REVIEW
        );
        return DirectExecutorArgs.<T>builder()
                .responseClazz(routingClass)
                .agentName(AGENT_NAME)
                .actionName(ACTION_AGENT_REVIEW)
                .methodName(METHOD_HANDLE_INTERRUPT)
                .template(TEMPLATE_REVIEW_RESOLUTION)
                .promptContext(promptContext)
                .templateModel(modelWithFeedback)
                .toolContext(toolContext)
                .operationContext(context)
                .build();
    }

    public PermissionGate.InterruptResolution awaitHumanReview(
            AgentModels.InterruptRequest request,
            @Nullable GraphNode originNodeId
    ) {
        return resolveInterruptHumanAgent(request, originNodeId);
    }

    /**
     * Sets interruptibleContext on the origin node and publishes the interrupt.
     * Does NOT block — use {@link #publishAndAwaitInterrupt} if you need to wait for resolution.
     */
    public void publishInterruptWithContext(
            String interruptId,
            String originNodeId,
            Events.InterruptType interruptType,
            String reason
    ) {
        GraphNode originNode = graphRepository.findById(originNodeId).orElse(null);
        if (originNode == null) {
            log.warn("publishInterruptWithContext: origin node {} not found in graph — interrupt {} will publish without context",
                    originNodeId, interruptId);
        } else if (originNode instanceof Interruptible interruptible) {
            updateInterruptibleContext(interruptible, interruptId, originNodeId, interruptType, reason);
        } else {
            log.warn("publishInterruptWithContext: origin node {} is {} (not Interruptible) — interrupt {} will publish without context",
                    originNodeId, originNode.getClass().getSimpleName(), interruptId);
        }
        permissionGate.publishInterrupt(interruptId, originNodeId, interruptType, reason);
    }

    private void updateInterruptibleContext(
            Interruptible interruptible,
            String interruptId,
            String originNodeId,
            Events.InterruptType interruptType,
            String reason
    ) {
        InterruptContext existing = interruptible.interruptibleContext();
        boolean needsNewContext = existing == null
                || existing.status() == InterruptContext.InterruptStatus.RESOLVED;
        boolean isDuplicateWithNewId = existing != null
                && existing.status() == InterruptContext.InterruptStatus.REQUESTED
                && !interruptId.equals(existing.interruptNodeId());

        if (!needsNewContext && !isDuplicateWithNewId) {
            // Either same interruptId already REQUESTED (no-op), or mid-lifecycle state (error).
            if (existing != null && existing.status() != InterruptContext.InterruptStatus.REQUESTED) {
                log.error("publishInterruptWithContext on node {} with in-progress status {} (expected null, REQUESTED, or RESOLVED)",
                        originNodeId, existing.status());
            } else {
                log.error("publishInterruptWithContext on existing {} for node {}",
                        existing, originNodeId);
            }
            return;
        }

        if (existing != null && existing.status() == InterruptContext.InterruptStatus.RESOLVED) {
            log.info("Resetting RESOLVED interruptibleContext on node {} for new interrupt {}", originNodeId, interruptId);
        } else if (isDuplicateWithNewId) {
            log.warn("Replacing REQUESTED interruptibleContext on node {} (existing={}, new={})",
                    originNodeId, existing.interruptNodeId(), interruptId);
        }

        var interruptContext = new InterruptContext(
                interruptType,
                InterruptContext.InterruptStatus.REQUESTED,
                reason,
                originNodeId,
                originNodeId,
                interruptId,
                null
        );
        GraphNode updated = interruptible.withInterruptibleContext(interruptContext);
        graphRepository.save(updated);
    }

    /**
     * Publishes an interrupt with proper interruptibleContext on the origin node,
     * then blocks until the interrupt is resolved and returns the resolution text.
     * <p>
     * Unlike calling {@code permissionGate.publishInterrupt()} directly, this method ensures
     * the origin node's {@link InterruptContext} is set to REQUESTED so that
     * {@code resolveInterrupt()} can later emit {@code InterruptStatusEvent(RESOLVED)}.
     *
     * @param defaultMessage fallback message if the resolution has no notes
     */
    public String publishAndAwaitInterrupt(
            String interruptId,
            String originNodeId,
            Events.InterruptType interruptType,
            String reason,
            String defaultMessage
    ) {
        publishInterruptWithContext(interruptId, originNodeId, interruptType, reason);

        IPermissionGate.InterruptResolution resolution = permissionGate.awaitInterruptBlocking(interruptId);
        String responseText = resolution != null ? resolution.getResolutionNotes() : null;
        if (responseText == null || responseText.isBlank()) {
            responseText = defaultMessage;
        }
        return responseText;
    }

    /**
     * Controller-specific convenience: publishes a HUMAN_REVIEW interrupt and blocks
     * until the controller responds.
     */
    public String publishAndAwaitControllerInterrupt(
            String interruptId,
            String originNodeId,
            Events.InterruptType interruptType,
            String reason
    ) {
        return publishAndAwaitInterrupt(
                interruptId, originNodeId, interruptType, reason,
                "Controller acknowledged but provided no response."
        );
    }

    private PermissionGate.InterruptResolution resolveInterruptHumanAgent(
            @jakarta.annotation.Nullable AgentModels.InterruptRequest request,
            @jakarta.annotation.Nullable GraphNode originNode
    ) {
        if (request == null) {
            return permissionGate.invalidInterrupt(INTERRUPT_ID_NOT_FOUND);
        }

        InterruptData result = getInterruptData(request, originNode);
        var interruptId = result.interruptId;
        var reviewContent = result.reviewContent;
        if (interruptId.isBlank()) {
            return permissionGate.invalidInterrupt(INTERRUPT_ID_NOT_FOUND);
        }
        String originNodeId = originNode != null ? originNode.nodeId() : interruptId;
        publishInterruptWithContext(interruptId, originNodeId, request.type(), reviewContent);
        return permissionGate.awaitInterruptBlocking(interruptId);
    }

    private String resolveInterruptFeedback(
            OperationContext context,
            @jakarta.annotation.Nullable AgentModels.InterruptRequest request,
            @jakarta.annotation.Nullable GraphNode originNode,
            PromptContext promptContext
    ) {
        InterruptData result = getInterruptData(request, originNode);
        if (request == null || request.type() == null) {
            var resolution = permissionGate.invalidInterrupt(result.interruptId());
            String feedback = resolution.getResolutionNotes();
            return firstNonBlank(feedback, result.reviewContent());
        }
        return switch(request.type()) {
            case HUMAN_REVIEW, PAUSE, AGENT_REVIEW, STOP, BRANCH, PRUNE -> {
                PermissionGate.InterruptResolution resolution =
                        resolveInterruptHumanAgent(request, originNode);
                String feedback = resolution != null ? resolution.getResolutionNotes() : null;
                yield firstNonBlank(feedback, result.reviewContent());
            }
        };
    }

    private @NonNull InterruptData getInterruptData(@jakarta.annotation.Nullable AgentModels.InterruptRequest request,
                                                    @jakarta.annotation.Nullable GraphNode originNode) {
        InterruptContext interruptContext = resolveInterruptContext(originNode);
        String reviewContent = firstNonBlank(
                request != null ? request.reason() : null,
                interruptContext != null ? interruptContext.resultPayload() : null
        );
        String interruptId = firstNonBlank(
                interruptContext != null ? interruptContext.interruptNodeId() : null,
                originNode != null ? originNode.nodeId() : null,
                INTERRUPT_ID_NOT_FOUND
        );
        InterruptData result = new InterruptData(reviewContent, interruptId);
        return result;
    }

    private record InterruptData(String reviewContent, String interruptId) {
    }

    private InterruptContext resolveInterruptContext(GraphNode node) {
        if (node == null) {
            return null;
        }
        if (node instanceof Interruptible interruptible) {
            return interruptible.interruptibleContext();
        }
        if (node instanceof ReviewNode reviewNode) {
            return reviewNode.interruptContext();
        }
        if (node instanceof InterruptNode interruptNode) {
            return interruptNode.interruptContext();
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
