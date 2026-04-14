package com.hayden.multiagentide.agent.decorator.tools;

import com.hayden.multiagentide.agent.DecoratorContext;
import com.hayden.multiagentide.tool.ToolContext;

public interface ToolContextDecorator {

    /**
     * Ordering for decorator execution. Lower values execute first.
     * Default decorators should use 0. The emitActionCompleted decorator
     * should use Integer.MAX_VALUE to ensure it runs last with the fully
     * decorated result.
     */
    default int order() {
        return 0;
    }


    default ToolContext decorate(ToolContext t, DecoratorContext decoratorContext) {
        return t;
    }

}
