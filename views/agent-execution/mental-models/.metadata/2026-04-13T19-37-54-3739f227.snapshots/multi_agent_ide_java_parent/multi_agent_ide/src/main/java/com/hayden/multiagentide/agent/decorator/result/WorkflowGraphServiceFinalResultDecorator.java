package com.hayden.multiagentide.agent.decorator.result;

import com.hayden.multiagentide.agent.WorkflowGraphService;
import com.hayden.multiagentide.agent.AgentModels;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WorkflowGraphServiceFinalResultDecorator implements FinalResultDecorator {

    private final WorkflowGraphService graphService;

    @Override
    public int order() {
        return 10_003;
    }

    @Override
    public <T extends AgentModels.AgentResult> T decorateFinalResult(T t, FinalResultDecoratorContext context) {
        if (t == null) {
            return t;
        }

        AgentModels.AgentResult completeRes = switch (t) {
            case AgentModels.OrchestratorCollectorResult res -> {
                if (context != null && context.decoratorContext() != null) {
                    graphService.completeOrchestratorCollectorResult(
                            context.decoratorContext().operationContext(),
                            res);
                }
                yield res;
            }
            default -> {
                log.error("Found unexpected decorate for decoreateFinalResult.");
                yield t;
            }
        };
        return (T) completeRes;
    }
}
