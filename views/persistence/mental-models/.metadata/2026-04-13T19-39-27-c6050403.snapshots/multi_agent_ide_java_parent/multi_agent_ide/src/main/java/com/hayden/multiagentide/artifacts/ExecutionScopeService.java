package com.hayden.multiagentide.artifacts;

import com.hayden.acp_cdc_ai.acp.events.Artifact;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages execution scopes and root artifact creation.
 * 
 * Responsibilities:
 * - Creates execution root artifacts
 * - Tracks active execution scopes
 * - Provides artifact key generation within execution scopes
 * - Manages required child groups (ExecutionConfig, InputArtifacts, etc.)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionScopeService {

    @Autowired @Lazy
    private EventBus eventBus;

    private final ArtifactEventListener artifactListener;
    
    private final Map<String, ExecutionScope> activeScopes = new ConcurrentHashMap<>();
    
    /**
     * Represents an active execution scope with its artifact keys.
     */
    public record ExecutionScope(
            String workflowRunId,
            ArtifactKey executionKey,
            Instant startedAt,
            Map<String, ArtifactKey> groupKeys // group name -> group artifact key
    ) {
        public ExecutionScope(String workflowRunId, ArtifactKey executionKey) {
            this(workflowRunId, executionKey, Instant.now(), new ConcurrentHashMap<>());
        }
    }
    
    /**
     * Starts a new execution scope and creates the root artifact.
     * 
     * @param workflowRunId Unique workflow run identifier
     * @return The execution scope
     */
    public ExecutionScope startExecution(String workflowRunId, ArtifactKey executionKey) {
        ExecutionScope scope = new ExecutionScope(workflowRunId, executionKey);
        
        // Create root execution artifact
        Artifact.ExecutionArtifact rootArtifact = Artifact.ExecutionArtifact.builder()
                .artifactKey(executionKey)
                .workflowRunId(workflowRunId)
                .startedAt(scope.startedAt())
                .status(Artifact.ExecutionStatus.RUNNING)
                .metadata(Map.of())
                .children(new ArrayList<>())
                .hash(workflowRunId + "_" + UUID.randomUUID())
                .build();
        
        // Emit artifact event
        emitArtifact(rootArtifact, null);
        
        // Register with listener
        artifactListener.registerExecution(executionKey.value(), workflowRunId);
        artifactListener.registerExecutionArtifact(executionKey.value(), rootArtifact);
        activeScopes.put(workflowRunId, scope);
        log.info("Started execution scope: {} -> {}", workflowRunId, executionKey);
        
        return scope;
    }


    /**
     * Completes an execution scope with the given status.
     */
    public void completeExecution(String workflowRunId, Artifact.ExecutionStatus status) {
        ExecutionScope scope = activeScopes.get(workflowRunId);
        if (scope == null) {
            log.warn("No active scope found for completion: {}", workflowRunId);
            return;
        }

        var persisted = artifactListener.finishPersistRemove(scope.executionKey().value());

        if (persisted.isEmpty()) {
            log.error("Execution completed but artifact listener did not have an execution for {}.", workflowRunId);
        } else {
            activeScopes.remove(workflowRunId);
            log.info("Completed execution scope: {} with status {} - {} entities persisted.", workflowRunId, status, persisted.get().collectRecursiveChildren().size() + 1);
        }
    }

    /**
     * Gets an active execution scope.
     */
    public Optional<ExecutionScope> getScope(String workflowRunId) {
        return Optional.ofNullable(activeScopes.get(workflowRunId));
    }
    
    /**
     * Gets the artifact key for a group within an execution.
     */
    public Optional<ArtifactKey> getGroupKey(String workflowRunId, String groupName) {
        return getScope(workflowRunId)
                .map(scope -> scope.groupKeys().get(groupName));
    }
    

    /**
     * Emits an artifact event.
     */
    public void emitArtifact(Artifact artifact, ArtifactKey parentKey) {
        Events.ArtifactEvent event = new Events.ArtifactEvent(
                UUID.randomUUID().toString(),
                artifact.artifactKey().extractTimestamp(),
                extractNodeId(artifact),
                artifact.artifactType(),
                parentKey != null ? parentKey.value() : null,
                artifact
        );
        
        eventBus.publish(event);
    }
    
    /**
     * Emits an artifact under a specific group.
     */
    public void emitArtifactToGroup(String workflowRunId, String groupName, Artifact artifact) {
        ArtifactKey groupKey = getGroupKey(workflowRunId, groupName)
                .orElseThrow(() -> new IllegalStateException("Group not found: " + groupName));
        emitArtifact(artifact, groupKey);
    }

    private String extractNodeId(Artifact artifact) {
        return artifact.artifactKey().value();
    }


}
