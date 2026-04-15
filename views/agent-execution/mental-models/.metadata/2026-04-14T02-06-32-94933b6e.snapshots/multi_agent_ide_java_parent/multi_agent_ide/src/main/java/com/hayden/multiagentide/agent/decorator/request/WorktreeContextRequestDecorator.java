package com.hayden.multiagentide.agent.decorator.request;

import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.service.WorktreeService;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.events.DegenerateLoopException;
import com.hayden.multiagentide.model.worktree.WorktreeSandboxContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorktreeContextRequestDecorator implements RequestDecorator, DispatchedAgentRequestDecorator {

    private final WorktreeService worktreeService;
    private final SandboxResolver sandboxResolver;

    private EventBus eventBus;

    @Autowired @Lazy
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public int order() {
        return -5_000;
    }

    @Override
    public <T extends AgentModels.AgentRequest> T decorate(T request, DecoratorContext context) {
        if (request == null || request.worktreeContext() != null) {
            return request;
        }

        if (request instanceof AgentModels.DispatchedRequest || request instanceof AgentModels.ResultsRequest) {
            String formatted = "Worktree context was not attached correctly to dispatched request %s.".formatted(request);
            log.error(formatted);
            eventBus.publish(Events.NodeErrorEvent.err(formatted, request.key()));
        }

        WorktreeSandboxContext sandboxContext = sandboxResolver.resolveSandboxContext(context);

        String message = """
                    No worktree was provided by orchestrator during it's request.
                    And therefore the worktree could not be provided for the downstream request.
                    """;

        if (sandboxContext == null
                && (request instanceof AgentModels.AiPropagatorRequest || request instanceof AgentModels.AiTransformerRequest || request instanceof AgentModels.AiFilterRequest)) {
            eventBus.publish(Events.NodeErrorEvent.err(message, request.key()));
            return request;
        }

        if (sandboxContext == null) {
            log.error("Sandbox context could not be resolved.");
            eventBus.publish(Events.NodeErrorEvent.err(message, request.key()));
            throw new DegenerateLoopException(message, request.artifactType(), request.getClass(), 1);
        }

        return (T) withWorktreeContext(request, sandboxContext);
    }


    AgentModels.AgentRequest withWorktreeContext(AgentModels.AgentRequest request, WorktreeSandboxContext worktreeContext) {
        if (request == null || worktreeContext == null) {
            return request;
        }
        return switch (request) {
            // Dispatch requests: pass the same worktree to collection and each child request.
            // No branching — all agents share the single orchestrator worktree.
            case AgentModels.DiscoveryAgentRequests r ->
                    worktreeService.attachWorktreesToDiscoveryRequests(r.toBuilder().worktreeContext(worktreeContext).build(), request.contextId().value());
            case AgentModels.PlanningAgentRequests r ->
                    worktreeService.attachWorktreesToPlanningRequests(r.toBuilder().worktreeContext(worktreeContext).build(), request.contextId().value());
            case AgentModels.TicketAgentRequests r ->
                    worktreeService.attachWorktreesToTicketRequests(r.toBuilder().worktreeContext(worktreeContext).build(), request.contextId().value());
            case AgentModels.OrchestratorRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.OrchestratorCollectorRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.DiscoveryOrchestratorRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.DiscoveryAgentRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.DiscoveryCollectorRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.DiscoveryAgentResults r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.PlanningOrchestratorRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.PlanningAgentRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.PlanningCollectorRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.PlanningAgentResults r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.TicketOrchestratorRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.TicketAgentRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.CommitAgentRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.MergeConflictRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.TicketCollectorRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.TicketAgentResults r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.ContextManagerRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.ContextManagerRoutingRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.OrchestratorInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.OrchestratorCollectorInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.DiscoveryOrchestratorInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.DiscoveryAgentInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.DiscoveryCollectorInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.DiscoveryAgentDispatchInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.PlanningOrchestratorInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.PlanningAgentInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.PlanningCollectorInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.PlanningAgentDispatchInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.TicketOrchestratorInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.TicketAgentInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.TicketCollectorInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.TicketAgentDispatchInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.ContextManagerInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.InterruptRequest.QuestionAnswerInterruptRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.AiFilterRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.AiPropagatorRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.AiTransformerRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.AgentToAgentRequest r -> r;
            case AgentModels.AgentToControllerRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
            case AgentModels.ControllerToAgentRequest r -> r.toBuilder().worktreeContext(worktreeContext).build();
        };
    }


}
