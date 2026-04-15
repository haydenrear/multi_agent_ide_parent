package com.hayden.multiagentide.config;

import com.embabel.agent.api.common.support.MultiTransformationAction;
import com.embabel.agent.core.support.BlackboardWorldState;
import com.embabel.plan.Action;
import com.embabel.plan.Goal;
import com.embabel.plan.common.condition.*;
import com.hayden.multiagentide.agent.AgentModels;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.embabel.agent.core.BlackboardKt.satisfiesType;

/**
 * Planner that routes to a single action by matching the last blackboard entry's type
 * against each action's input bindings. Avoids the overhead of full GOAP precondition evaluation.
 *
 * If the last blackboard entry is a {@link AgentModels.Routing}, extracts the first non-null
 * record component as the actual routed request before matching against actions.
 */
public class BlackboardRoutingPlanner extends AbstractConditionPlanner {

    private static final Logger log = LoggerFactory.getLogger(BlackboardRoutingPlanner.class);

    public BlackboardRoutingPlanner(WorldStateDeterminer worldStateDeterminer) {
        super(worldStateDeterminer);
    }

    @Nullable
    @Override
    public ConditionPlan planToGoal(Collection<? extends Action> actions, Goal goal) {
        var currentState = worldState();

        // Check if goal is already achieved
        if (goal instanceof ConditionGoal cg && cg.isAchievable(currentState)) {
            return new ConditionPlan(List.of(), goal, currentState);
        }

        // Get the last result from the blackboard
        if (!(currentState instanceof BlackboardWorldState bws)) {
            log.warn("World state is not BlackboardWorldState, cannot route by blackboard content");
            return null;
        }

        var lastResult = bws.getBlackboard().lastResult();
        log.info("planToGoal invoked — lastResult type: {}",
                lastResult != null ? lastResult.getClass().getSimpleName() : "(null)");
        if (lastResult == null) {
            log.debug("No last result on blackboard, no action can be selected");
            return null;
        }

        String routedFrom = null;
        // If the last result is a Routing record, extract the first non-null field as the routed request
        if (lastResult instanceof AgentModels.Routing) {
            routedFrom = lastResult.getClass().getSimpleName();
            var routed = extractFirstNonNullComponent(lastResult);
            if (routed == null) {
                log.warn("Routing record {} has no non-null components", lastResult.getClass().getSimpleName());
                return null;
            }
            log.debug("Extracted routed request {} from Routing record {}",
                    routed.getClass().getSimpleName(), lastResult.getClass().getSimpleName());
            lastResult = routed;
        }
        // assuming that this is achieves goal for the dispatched agents.
        if (lastResult instanceof AgentModels.AgentRouting) {
            return new ConditionPlan(List.of(), goal, currentState);
        }

        // Collectors always route forward — route-back is handled by conversational topology (Story 7)
        if (lastResult instanceof AgentModels.OrchestratorCollectorResult) {
            for (Action action : actions) {
                if (action.getName().endsWith(".finalCollectorResult")) {
                    return new ConditionPlan(List.of(action), goal, currentState);
                }
            }
        }

        // Find the action whose input type matches the resolved blackboard entry
        var resolved = lastResult;
        for (Action action : actions) {
            if (action instanceof MultiTransformationAction<?> agentAction) {
                boolean matches = agentAction.getInputs().stream()
                        .anyMatch(input -> satisfiesType(resolved, input.getValue().replaceFirst("it:", "")));
                if (matches) {
                    log.debug("Routing to action {} based on blackboard entry type {}",
                            agentAction.getName(), resolved.getClass().getSimpleName());
                    return new ConditionPlan(List.of(action), goal, currentState);
                }
            }
        }

        List<String> actionInputs = actions.stream()
                .filter(MultiTransformationAction.class::isInstance)
                .map(MultiTransformationAction.class::cast)
                .map(action -> action.getName() + ":" + action.getInputs())
                .toList();
        if (routedFrom != null) {
            log.warn("No action found for routed request {} from {}. Available action bindings: {}",
                    lastResult.getClass().getName(), routedFrom, actionInputs);
        } else {
            log.warn("No action found matching blackboard entry type {}. Available action bindings: {}",
                    lastResult.getClass().getName(), actionInputs);
        }
        return null;
    }

    /**
     * Extract the first non-null record component value from a record instance.
     * Routing records have nullable fields where exactly one non-null field indicates
     * the next routed request.
     */
    @Nullable
    private static Object extractFirstNonNullComponent(Object record) {
        if (!record.getClass().isRecord()) {
            return null;
        }
        List<Object> allNonNull = new ArrayList<>();
        for (RecordComponent component : record.getClass().getRecordComponents()) {
            try {
                var value = component.getAccessor().invoke(record);
                if (value != null)
                    allNonNull.add(value);
            } catch (ReflectiveOperationException e) {
                log.warn("Failed to access record component {} on {}", component.getName(), record.getClass().getSimpleName(), e);
            }
        }

        if (allNonNull.size() > 1) {
            log.error("Received SomeOf with more than one non-null - not supporting that with this simplified planner.");
        }

        return allNonNull.stream().findFirst().orElse(null);
    }

    @Override
    public ConditionPlanningSystem prune(ConditionPlanningSystem planningSystem) {
        return planningSystem;
    }
}
