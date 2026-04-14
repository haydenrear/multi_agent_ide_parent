package com.hayden.multiagentide.artifacts;

import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for emitting artifacts during agent execution.
 * 
 * CRITICAL: Artifacts are emitted as children of the current agent's ArtifactKey (contextId),
 * NOT as children of the workflow run ID. This preserves the hierarchical tree structure
 * that mirrors the agent execution tree.
 * 
 * Provides methods to emit:
 * - AgentRequestArtifact
 * - AgentResultArtifact
 * - InterruptRequestArtifact
 * - InterruptResolutionArtifact
 * - ExecutionConfigArtifact
 * - OutcomeEvidenceArtifact
 * - RenderedPromptArtifact
 * - PromptArgsArtifact
 * - GraphEvent as EventArtifact
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ArtifactEmissionService {
    
    private final ExecutionScopeService executionScopeService;
    private EventBus eventBus;

    @Autowired
    @Lazy
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    /**
     * Emits an AgentRequestArtifact for a model being processed.
     *
     * The artifact is created as a child of the model's own contextId (ArtifactKey),
     * preserving the hierarchical structure.
     *
     * @param model The agent model to emit
     */
    public void emitAgentModel(
            Artifact.AgentModel model,
            Artifact.HashContext context
    ) {
        if (model == null) {
            log.debug("Skipping null agent model emission");
            return;
        }

        // The parent key is the model's own contextId - artifacts for this
        // agent's execution are children of this model
        ArtifactKey parentKey = model.key();
        if (parentKey == null) {
            log.warn("AgentRequest has no contextId, cannot emit artifact: {}",
                    model.getClass().getSimpleName());
            emitNodeError("Artifact emission skipped: model has null contextId for type " + model.getClass().getSimpleName(), null);
            return;
        }

        try {
            var artifact = model.toArtifact(context);
            var artifactKey = artifact.artifactKey();
            executionScopeService.emitArtifact(artifact, parentKey);
            log.debug("Emitted AgentRequestArtifact: {} under {}", artifactKey, parentKey);

        } catch (Exception e) {
            String missingPath = findFirstMissingContextPath(model, model.getClass().getSimpleName());
            String message = "Failed to emit AgentRequestArtifact for " + model.getClass().getSimpleName()
                    + " at path " + missingPath
                    + " under key " + parentKey.value()
                    + ": " + e.getClass().getSimpleName() + " - " + e.getMessage();
            log.error(message, e);
            emitNodeError(message, parentKey);
        }
    }

    private void emitNodeError(String message, @Nullable ArtifactKey fallbackKey) {
        if (eventBus == null) {
            return;
        }
        ArtifactKey nodeKey = fallbackKey != null ? fallbackKey : ArtifactKey.createRoot();
        eventBus.publish(Events.NodeErrorEvent.err(message, nodeKey));
    }

    private String findFirstMissingContextPath(Artifact.AgentModel model, String path) {
        if (model == null) {
            return path + " (null model)";
        }
        if (model.key() == null) {
            return path + " (missing contextId)";
        }
        List<Artifact.AgentModel> children = model.children();
        if (children == null || children.isEmpty()) {
            return path + " (no missing nested contextId found)";
        }
        for (int i = 0; i < children.size(); i++) {
            Artifact.AgentModel child = children.get(i);
            String childType = child != null ? child.getClass().getSimpleName() : "null";
            String childPath = path + " -> " + childType + "[" + i + "]";
            if (child == null || child.key() == null) {
                return childPath + " (missing contextId)";
            }
            String nested = findFirstMissingContextPath(child, childPath);
            if (nested.contains("(missing contextId)")) {
                return nested;
            }
        }
        return path + " (no missing nested contextId found)";
    }
}
