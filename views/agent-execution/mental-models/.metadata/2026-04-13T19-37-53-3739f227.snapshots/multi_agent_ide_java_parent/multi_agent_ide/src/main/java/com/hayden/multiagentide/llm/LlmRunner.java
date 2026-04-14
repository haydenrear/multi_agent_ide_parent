package com.hayden.multiagentide.llm;

import com.embabel.agent.api.common.OperationContext;
import com.hayden.multiagentide.prompt.PromptContext;
import com.hayden.multiagentide.tool.ToolContext;

import java.util.Map;

/**
 * Service for running LLM queries and getting structured responses.
 * This abstraction allows for easier testing by providing a mockable interface
 * for LLM interactions.
 * 
 * Uses Embabel's native pattern:
 * - PromptContributors for dynamic content (via ContextualPromptElement)
 * - Jinja templates for base prompts
 * - Fluent API: withPromptContributor() + withTemplate() + createObject()
 */
public interface LlmRunner {
    
    /**
     * Run an LLM query using a Jinja template with a provided PromptContext.
     * The PromptContext is used to retrieve applicable contributors.
     * 
     * @param templateName The name of the Jinja template (e.g., "workflow/orchestrator")
     * @param promptContext The prompt context (contains agent type and other metadata)
     * @param model The data model to render the template with
     * @param toolContext The tool context for tool abstractions
     * @param responseClass The class type expected for the response
     * @param context The operation context containing the agent process and AI interface
     * @param <T> The type of the response object
     * @return The structured response from the LLM
     */
    <T> T runWithTemplate(
            String templateName,
            PromptContext promptContext,
            Map<String, Object> model,
            ToolContext toolContext,
            Class<T> responseClass,
            OperationContext context
    );

}
