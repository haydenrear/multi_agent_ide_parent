package com.hayden.multiagentidelib.prompt.contributor;

import com.hayden.multiagentidelib.prompt.PromptContext;
import com.hayden.multiagentidelib.prompt.PromptContributor;
import com.hayden.multiagentidelib.prompt.PromptContributorFactory;
import com.hayden.multiagentidelib.prompt.WorkflowAgentGraphNode;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Dynamically generates the "Return a routing object with exactly one of:" schema block
 * from the Java routing record types via reflection. This replaces the hand-maintained
 * return schema blocks that were previously duplicated across all jinja templates.
 *
 * <p>For each request type, this factory looks up the corresponding routing type using
 * {@link WorkflowAgentGraphNode#getRequestToRoutingMap()} and generates the schema text
 * by reflecting on the routing record's components.</p>
 */
@Component
public class RoutingSchemaPromptContributorFactory implements PromptContributorFactory {

    @Override
    public List<PromptContributor> create(PromptContext context) {
        if (context == null || context.currentRequest() == null) {
            return List.of();
        }

        Class<?> requestType = context.currentRequest().getClass();
        Map<Class<?>, Class<?>> requestToRouting = WorkflowAgentGraphNode.getRequestToRoutingMap();
        Class<?> routingType = requestToRouting.get(requestType);

        if (routingType == null) {
            return List.of();
        }

        return List.of(new RoutingSchemaContributor(routingType));
    }

    record RoutingSchemaContributor(Class<?> routingType) implements PromptContributor {

        @Override
        public String name() {
            return "routing-schema-" + routingType.getSimpleName();
        }

        @Override
        public boolean include(PromptContext promptContext) {
            return true;
        }

        @Override
        public String contribute(PromptContext context) {
            return RoutingSchemaGenerator.generateSchema(routingType);
        }

        @Override
        public String template() {
            return "routing-schema:" + routingType.getName();
        }

        @Override
        public int priority() {
            return 95;
        }
    }
}
