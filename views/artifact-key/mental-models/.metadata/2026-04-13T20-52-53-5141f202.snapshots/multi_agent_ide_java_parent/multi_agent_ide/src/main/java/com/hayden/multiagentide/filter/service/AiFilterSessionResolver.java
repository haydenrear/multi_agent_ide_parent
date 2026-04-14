package com.hayden.multiagentide.filter.service;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventListener;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.service.SessionKeyResolutionService;
import com.hayden.multiagentide.filter.model.executor.AiFilterTool;
import com.hayden.multiagentide.prompt.PromptContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Thin wrapper that delegates to {@link SessionKeyResolutionService} for all session key
 * resolution and lifecycle eviction. Kept for backward compatibility with existing callers.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AiFilterSessionResolver implements EventListener {

    private final SessionKeyResolutionService sessionKeyResolutionService;

    public ArtifactKey resolveSessionKey(String policyId,
                                         AiFilterTool.SessionMode sessionMode,
                                         PromptContext promptContext) {
        return sessionKeyResolutionService.resolveSessionKey(policyId, sessionMode, promptContext);
    }

    @Override
    public String listenerId() {
        return "ai-filter-session-resolver";
    }

    @Override
    public boolean isInterestedIn(Events.GraphEvent eventType) {
        // Lifecycle eviction is now handled by SessionKeyResolutionService
        return false;
    }

    @Override
    public void onEvent(Events.GraphEvent event) {
        // No-op — delegated to SessionKeyResolutionService
    }
}
