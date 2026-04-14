package com.hayden.multiagentide.prompt;

/**
 * Listener interface for prompt contribution events.
 * 
 * Implementations can capture contribution data for artifact emission,
 * logging, or other observability purposes.
 */
public interface PromptContributionListener {
    
    /**
     * Called when a prompt contributor produces a contribution.
     * 
     * @param contributorName The name of the contributor
     * @param priority The priority of the contributor
     * @param applicableAgents The agent types this contributor applies to
     * @param contributedText The text produced by the contributor
     * @param orderIndex The order in which this contribution was assembled (0-based)
     * @param context The prompt context that was used
     */
    void onContribution(
            PromptContext context,
            PromptContributor promptContributor
    );
    
}
