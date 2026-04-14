package com.hayden.multiagentide.agent.decorator.request;

import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.multiagentide.agent.BlackboardHistory;
import com.hayden.multiagentide.agent.BlackboardHistoryService;
import com.hayden.multiagentide.agent.WorkflowGraphState;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

/**
 * Request decorator that registers the input in blackboard history.
 * Runs at order 9,000 — after all request mutations but before decorators
 * that read the blackboard (propagators at 10,000).
 *
 * Must ensure the BlackboardHistory exists before registering, since
 * EmitActionStartedRequestDecorator (which also creates it) runs at 10,000.
 */
@Component
@RequiredArgsConstructor
public class RegisterBlackboardHistoryInputRequestDecorator implements DispatchedAgentRequestDecorator, RequestDecorator {

    private final BlackboardHistoryService blackboardHistoryService;

    private EventBus eventBus;

    @Autowired
    @Lazy
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
    * This one had to happen after all the changes to the request
    * were made, but before the decorators that may look through the blackboard.
    */
    @Override
    public int order() {
        return 9_000;
    }

    @Override
    public <T extends AgentModels.AgentRequest> T decorate(T request, DecoratorContext context) {
        if (request == null) {
            return null;
        }

        // Ensure BlackboardHistory exists before registering — on the first action
        // of a run, no history exists yet and register() would silently drop the entry.
        BlackboardHistory.ensureSubscribed(
                eventBus,
                context.operationContext(),
                () -> WorkflowGraphState.initial(request.key().value())
        );

        blackboardHistoryService.register(
                context.operationContext(),
                context.methodName(),
                request
        );

        return request;
    }
}
