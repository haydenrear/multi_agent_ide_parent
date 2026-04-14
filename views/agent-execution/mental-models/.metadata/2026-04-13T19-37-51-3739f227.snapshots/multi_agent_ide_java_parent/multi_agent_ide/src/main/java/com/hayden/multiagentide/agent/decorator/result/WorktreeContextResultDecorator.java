package com.hayden.multiagentide.agent.decorator.result;

import com.hayden.multiagentide.agent.decorator.request.SandboxResolver;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.model.worktree.WorktreeSandboxContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Ensures AgentResult instances carry worktree context from the active request.
 * This keeps worktree identity available even when merge metadata is unavailable.
 */
@Slf4j
@Component
public class WorktreeContextResultDecorator implements DispatchedAgentResultDecorator, ResultDecorator, FinalResultDecorator {

    private final SandboxResolver sandboxResolver;

    public WorktreeContextResultDecorator(SandboxResolver sandboxResolver) {
        this.sandboxResolver = sandboxResolver;
    }

    @Override
    public int order() {
        return 900;
    }

    @Override
    public <T extends AgentModels.AgentRouting> T decorate(T routing, DecoratorContext context) {
        if (routing == null) {
            return null;
        }

        return switch (routing) {
            case AgentModels.DiscoveryAgentRouting r ->
                    (T) r.toBuilder().agentResult(withWorktreeContext(r.agentResult(), context)).build();
            case AgentModels.PlanningAgentRouting r ->
                    (T) r.toBuilder().agentResult(withWorktreeContext(r.agentResult(), context)).build();
            case AgentModels.TicketAgentRouting r ->
                    (T) r.toBuilder().agentResult(withWorktreeContext(r.agentResult(), context)).build();
            case AgentModels.DiscoveryCollectorRouting r ->
                    (T) r.toBuilder().collectorResult(withWorktreeContext(r.collectorResult(), context)).build();
            case AgentModels.PlanningCollectorRouting r ->
                    (T) r.toBuilder().collectorResult(withWorktreeContext(r.collectorResult(), context)).build();
            case AgentModels.TicketCollectorRouting r ->
                    (T) r.toBuilder().collectorResult(withWorktreeContext(r.collectorResult(), context)).build();
            case AgentModels.OrchestratorCollectorRouting r ->
                    (T) r.toBuilder().collectorResult(withWorktreeContext(r.collectorResult(), context)).build();
            default -> routing;
        };
    }

    @Override
    public <T extends AgentModels.AgentResult> T decorate(T result, DecoratorContext context) {
        return withWorktreeContext(result, context);
    }

    private <T extends AgentModels.AgentResult> T withWorktreeContext(T result, DecoratorContext context) {
        if (result == null || result.worktreeContext() != null) {
            return result;
        }

        WorktreeSandboxContext resolved = resolveWorktreeContext(context);
        if (resolved == null) {
            log.debug("Skipping result worktree context enrichment for {}", result.getClass().getSimpleName());
            return result;
        }

        return switch (result) {
            case AgentModels.OrchestratorAgentResult r -> (T) r.toBuilder().worktreeContext(resolved).build();
            case AgentModels.DiscoveryOrchestratorResult r -> (T) r.toBuilder().worktreeContext(resolved).build();
            case AgentModels.PlanningOrchestratorResult r -> (T) r.toBuilder().worktreeContext(resolved).build();
            case AgentModels.TicketOrchestratorResult r -> (T) r.toBuilder().worktreeContext(resolved).build();
            case AgentModels.DiscoveryAgentResult r -> (T) r.toBuilder().worktreeContext(resolved).build();
            case AgentModels.PlanningAgentResult r -> (T) r.toBuilder().worktreeContext(resolved).build();
            case AgentModels.TicketAgentResult r -> (T) r.toBuilder().worktreeContext(resolved).build();
            case AgentModels.CommitAgentResult r -> (T) r.toBuilder().worktreeContext(resolved).build();
            case AgentModels.MergeConflictResult r -> (T) r.toBuilder().worktreeContext(resolved).build();
            case AgentModels.ReviewAgentResult r -> (T) r.toBuilder().worktreeContext(resolved).build();
            case AgentModels.MergerAgentResult r -> (T) r.toBuilder().worktreeContext(resolved).build();
            case AgentModels.DiscoveryCollectorResult r -> (T) r.toBuilder().worktreeContext(resolved).build();
            case AgentModels.PlanningCollectorResult r -> (T) r.toBuilder().worktreeContext(resolved).build();
            case AgentModels.OrchestratorCollectorResult r -> (T) r.toBuilder().worktreeContext(resolved).build();
            case AgentModels.TicketCollectorResult r -> (T) r.toBuilder().worktreeContext(resolved).build();
            case AgentModels.AiFilterResult r -> (T) r.toBuilder().worktreeContext(resolved).build();
            case AgentModels.AiPropagatorResult r -> (T) r.toBuilder().worktreeContext(resolved).build();
            case AgentModels.AiTransformerResult r -> (T) r.toBuilder().worktreeContext(resolved).build();
            case AgentModels.AgentCallResult r -> (T) r.toBuilder().worktreeContext(resolved).build();
            case AgentModels.ControllerCallResult r -> (T) r.toBuilder().worktreeContext(resolved).build();
            case AgentModels.ControllerResponseResult r -> (T) r.toBuilder().worktreeContext(resolved).build();
        };
    }

    private WorktreeSandboxContext resolveWorktreeContext(DecoratorContext context) {
        if (context == null) {
            return null;
        }

        if (context.agentRequest() instanceof AgentModels.AgentRequest request
                && request.worktreeContext() != null) {
            return request.worktreeContext();
        }
        if (context.lastRequest() instanceof AgentModels.AgentRequest request
                && request.worktreeContext() != null) {
            return request.worktreeContext();
        }
        return null;
    }
}
