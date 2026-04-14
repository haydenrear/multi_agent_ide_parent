package com.hayden.multiagentide.config;

import com.embabel.agent.core.ProcessOptions;
import com.embabel.agent.spi.PlannerFactory;
import com.embabel.plan.Plan;
import com.embabel.plan.Planner;
import com.embabel.plan.PlanningSystem;
import com.embabel.plan.WorldState;
import com.embabel.plan.common.condition.WorldStateDeterminer;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/**
 * PlannerFactory that creates {@link BlackboardRoutingPlanner} instances.
 * Routes to the single action whose input type matches the last blackboard entry.
 */
@Component
public class BlackboardRoutingPlannerFactory implements PlannerFactory {

    @NotNull
    @Override
    public Planner<? extends PlanningSystem, ? extends WorldState, ? extends Plan> createPlanner(
            @NotNull ProcessOptions processOptions,
            @NotNull WorldStateDeterminer worldStateDeterminer
    ) {
        return new BlackboardRoutingPlanner(worldStateDeterminer);
    }
}
