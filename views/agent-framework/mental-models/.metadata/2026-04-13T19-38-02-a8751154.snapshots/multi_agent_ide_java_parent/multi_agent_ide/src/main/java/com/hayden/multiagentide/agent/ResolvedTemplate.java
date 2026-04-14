package com.hayden.multiagentide.agent;

import java.util.Map;

/**
 * Result of template resolution in AgentExecutor. Determines whether the normal
 * template model is used or replaced with an error-specific model.
 */
public sealed interface ResolvedTemplate
        permits ResolvedTemplate.NoErrorTemplate, ResolvedTemplate.ErrorTemplate {

    String templateName();

    /**
     * Normal execution — use the default template and the caller's template model.
     */
    record NoErrorTemplate(String templateName) implements ResolvedTemplate {}

    /**
     * Error retry — use the error-specific template and its own model (replaces the caller's model).
     */
    record ErrorTemplate(String templateName, Map<String, Object> model) implements ResolvedTemplate {}
}
