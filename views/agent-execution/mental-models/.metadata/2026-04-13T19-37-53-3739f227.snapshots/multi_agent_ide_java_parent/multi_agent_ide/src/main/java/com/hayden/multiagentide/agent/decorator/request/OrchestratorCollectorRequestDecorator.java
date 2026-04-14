package com.hayden.multiagentide.agent.decorator.request;

import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.agent.BlackboardHistory;
import com.hayden.multiagentide.model.worktree.WorktreeSandboxContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Request decorator that merges the orchestrator's derived branches back into the
 * original base branches in the source repository when the OrchestratorCollectorRequest
 * is being prepared. Attaches a MergeDescriptor to the request with the result.
 *
 * Runs at order 9999 to execute after most other request decorators.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrchestratorCollectorRequestDecorator implements RequestDecorator {

    @Override
    public int order() {
        return 8_000;
    }

    @Override
    public <T extends AgentModels.AgentRequest> T decorate(T request, DecoratorContext context) {
        // No-op: controller handles merge-to-source after goal completion.
        // The worktree path is already on the request for the controller to use.
        return request;
    }

    private static @NonNull Optional<AgentModels.AgentRequest> getRequest(DecoratorContext context,
                                                                                 Class<? extends AgentModels.AgentRequest> req) {
        return Optional.ofNullable(BlackboardHistory.getLastFromHistory(context.operationContext(), req));
    }

    private static @NonNull Optional<WorktreeSandboxContext> getSandboxContext(DecoratorContext context,
                                                                               Class<? extends AgentModels.AgentRequest> req,
                                                                               Class<? extends AgentModels.AgentResult> res) {
        return Optional.ofNullable(BlackboardHistory.getLastFromHistory(context.operationContext(), req))
                .flatMap(tcr -> Optional.ofNullable(tcr.worktreeContext()))
                .or(() -> Optional.ofNullable(BlackboardHistory.getLastFromHistory(context.operationContext(), res))
                        .flatMap(tcr -> Optional.ofNullable(tcr.worktreeContext())));
    }

}
