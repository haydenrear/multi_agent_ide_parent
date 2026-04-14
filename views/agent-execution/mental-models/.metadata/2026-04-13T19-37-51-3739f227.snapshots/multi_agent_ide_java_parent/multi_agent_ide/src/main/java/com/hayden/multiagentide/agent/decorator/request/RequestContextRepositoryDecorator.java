package com.hayden.multiagentide.agent.decorator.request;

import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.model.worktree.WorktreeSandboxContext;
import com.hayden.acp_cdc_ai.repository.RequestContext;
import com.hayden.acp_cdc_ai.repository.RequestContextRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RequestContextRepositoryDecorator implements RequestDecorator {

    private final RequestContextRepository requestContextRepository;

    @Override
    public int order() {
        return 3_000;
    }

    @Override
    public <T extends AgentModels.AgentRequest> T decorate(T request, DecoratorContext context) {
        if (request == null || (request.contextId() == null || request.contextId().value() == null)) {
            return request;
        }

        if (request instanceof AgentModels.AgentToAgentRequest) {
            return request;
        }

        String sessionId = request.contextId().value();

        RequestContext updated = requestContextRepository.findBySessionId(sessionId)
                .orElse(RequestContext.builder().sessionId(sessionId).build());

        WorktreeSandboxContext worktree = request.worktreeContext();

        if (worktree != null) {
            updated = updated.withSandboxContext(worktree.sandboxContext());
        }

        requestContextRepository.save(updated);
        return request;
    }

}
