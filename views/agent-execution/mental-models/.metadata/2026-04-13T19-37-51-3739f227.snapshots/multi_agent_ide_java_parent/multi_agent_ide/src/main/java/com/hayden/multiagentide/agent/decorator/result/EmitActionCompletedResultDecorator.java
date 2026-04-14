package com.hayden.multiagentide.agent.decorator.result;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.multiagentide.agent.decorator.request.SandboxResolver;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.artifacts.ExecutionScopeService;
import com.hayden.multiagentide.embabel.EmbabelUtil;
import com.hayden.multiagentide.filter.service.FilterLayerCatalog;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.agent.BlackboardHistory;
import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Result decorator that emits ActionCompletedEvent after all other decorators have run.
 * Uses Integer.MAX_VALUE ordering to ensure it executes last with the fully decorated result.
 */
@Component
@RequiredArgsConstructor
public class EmitActionCompletedResultDecorator implements FinalResultDecorator, ResultDecorator, DispatchedAgentResultDecorator {


    private final ExecutionScopeService exec;
    private final SandboxResolver sandboxResolver;

    private EventBus eventBus;

    @Autowired
    @Lazy
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public int order() {
        return 10_003;
    }

    @Override
    public <T extends AgentModels.AgentRouting> T decorate(T t, DecoratorContext context) {
        if (t == null || eventBus == null) {
            return t;
        }

        // Interrupt resolution returns agent routing types with agentResult populated,
        // but the agent is not actually complete — skip unsubscription during interrupt.
        if (context.agentRequest() instanceof AgentModels.InterruptRequest) {
            return t;
        }

        OperationContext operationContext = context.operationContext();

        // Handle unsubscription based on result type
        if (t instanceof AgentModels.OrchestratorCollectorRouting routing
                && routing.collectorResult() != null) {
            BlackboardHistory.unsubscribe(eventBus, operationContext);
        }
        if (t instanceof AgentModels.DiscoveryAgentRouting routing
                && routing.agentResult() != null) {
            BlackboardHistory.unsubscribe(eventBus, operationContext);
        }
        if (t instanceof AgentModels.PlanningAgentRouting routing
                && routing.agentResult() != null) {
            BlackboardHistory.unsubscribe(eventBus, operationContext);
        }
        if (t instanceof AgentModels.TicketAgentRouting routing
                && routing.agentResult() != null) {
            BlackboardHistory.unsubscribe(eventBus, operationContext);
        }

        return t;
    }

    @Override
    public <T extends AgentModels.AgentResult> T decorate(T t, DecoratorContext context) {
        if (t == null || eventBus == null) {
            return t;
        }

        OperationContext operationContext = context.operationContext();
        String agentName = context.agentName();
        String actionName = FilterLayerCatalog.canonicalActionName(
                agentName,
                context.actionName(),
                context.methodName()
        );

        if (t instanceof AgentModels.OrchestratorCollectorResult) {
            BlackboardHistory.unsubscribe(eventBus, operationContext);
        }

        String nodeId = resolveNodeId(operationContext);
        String outcomeType = t.getClass().getSimpleName();
        eventBus.publish(new Events.ActionCompletedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                nodeId,
                agentName,
                actionName,
                outcomeType,
                t
        ));

        if (t instanceof AgentModels.OrchestratorCollectorResult res) {
            String worktreePath;
            if (context.agentRequest() instanceof AgentModels.AgentRequest agentReq
                    && agentReq.worktreeContext() != null
                    && agentReq.worktreeContext().mainWorktree() != null
                    && agentReq.worktreeContext().mainWorktree().worktreePath() != null) {
                worktreePath = agentReq.worktreeContext().mainWorktree().worktreePath().toString();
            } else {
                worktreePath = Optional.ofNullable(sandboxResolver.resolveFromOrchestratorNode(context.operationContext()))
                        .flatMap(wt -> Optional.ofNullable(wt.mainWorktree()))
                        .flatMap(wt -> Optional.ofNullable(wt.worktreePath()))
                        .map(Path::toString)
                        .orElse(null);
            }
            eventBus.publish(new Events.GoalCompletedEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    nodeId,
                    EmbabelUtil.extractWorkflowRunId(context.operationContext()),
                    res,
                    worktreePath
            ));

//          make sure this happens last
            exec.completeExecution(EmbabelUtil.extractWorkflowRunId(context.operationContext()), Artifact.ExecutionStatus.COMPLETED);
        }

        return t;
    }

    @Override
    public <T extends AgentModels.AgentRequest> T decorateRequestResult(T t, DecoratorContext context) {
        if (t == null || eventBus == null) {
            return t;
        }

        OperationContext operationContext = context.operationContext();
        String agentName = context.agentName();
        String actionName = FilterLayerCatalog.canonicalActionName(
                agentName,
                context.actionName(),
                context.methodName()
        );

        String nodeId = resolveNodeId(operationContext);
        String outcomeType = t.getClass().getSimpleName();
        eventBus.publish(new Events.ActionCompletedEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                nodeId,
                agentName,
                actionName,
                outcomeType,
                t
        ));

        return t;
    }

    private static String resolveNodeId(OperationContext context) {
        if (context == null || context.getProcessContext() == null) {
            return "unknown";
        }
        var options = context.getProcessContext().getProcessOptions();
        if (options == null) {
            return "unknown";
        }
        String contextId = options.getContextIdString();
        return contextId != null ? contextId : "unknown";
    }

}
