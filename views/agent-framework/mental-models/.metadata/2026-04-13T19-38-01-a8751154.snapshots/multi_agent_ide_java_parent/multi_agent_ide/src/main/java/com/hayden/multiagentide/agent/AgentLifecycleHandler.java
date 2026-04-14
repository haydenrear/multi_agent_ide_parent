package com.hayden.multiagentide.agent;

import com.embabel.agent.api.event.*;
import com.embabel.agent.core.*;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.model.nodes.OrchestratorNode;
import com.hayden.multiagentide.model.worktree.MainWorktreeContext;
import com.hayden.multiagentide.model.worktree.SubmoduleWorktreeContext;
import com.hayden.multiagentide.orchestration.ComputationGraphOrchestrator;
import com.hayden.multiagentide.repository.WorktreeRepository;
import com.hayden.multiagentide.service.WorktreeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;

/**
 * Handles agent lifecycle events for the multi-agent system.
 * Manages node creation, updates, and removal in the computation graph
 * when agents are invoked (including when invoked by other agents).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgentLifecycleHandler {


    private final ComputationGraphOrchestrator orchestrator;
    private final WorktreeRepository worktreeRepository;
    private final WorktreeService worktreeService;
    private final AgentPlatform agentPlatform;

    public Agent resolveAgent(String agentName) {
        List<Agent> agents = agentPlatform.agents();
        return agents.stream()
                .filter(agent -> agent.getName().equals(agentName))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Agent not found: " + agentName));
    }

    public static AgenticEventListener agentProcessIdListener() {
        return new AgenticEventListener() {
            @Override
            public void onProcessEvent(@NonNull AgentProcessEvent event) {
                EventBus.AgentNodeKey prev = null;
                if (event instanceof LlmRequestEvent creation) {
                    prev = setAgentKey(creation);
                }
                if (event instanceof AgentProcessCreationEvent creation) {
                    prev = setAgentKey(creation);
                }
                if (event instanceof AgentProcessFinishedEvent) {
                    if (prev == null)
                        EventBus.Process.remove();
                    else
                        EventBus.Process.set(prev);
                }
            }

            private static EventBus.AgentNodeKey setAgentKey(AbstractAgentProcessEvent creation) {
                var prev = EventBus.Process.get();
                var ar = BlackboardHistory.getLastFromHistory(creation.getAgentProcess(), AgentModels.AgentRequest.class);

                if (ar == null || ar.key() == null || ar.key().value() == null)
                    EventBus.Process.set(new EventBus.AgentNodeKey(creation.getProcessId()));
                else
                    EventBus.Process.set(new EventBus.AgentNodeKey(ar.key().value()));

                return prev;
            }
        };
    }

    public <T> T runAgent(AgentInterfaces agentInterface, AgentModels.OrchestratorRequest input, Class<T> outputClass, String nodeId) {
        log.info("Starting agent {}: {}, {}", agentInterface.getClass().getSimpleName(), nodeId, input);
        Agent agent = resolveAgent(agentInterface.multiAgentAgentName());
        ProcessOptions processOptions = ProcessOptions.DEFAULT.withContextId(nodeId)
                .withListener(agentProcessIdListener());

        AgentProcess process = agentPlatform.runAgentFrom(
                agent,
                processOptions,
                Map.of(IoBinding.DEFAULT_BINDING, input.withContextId(new ArtifactKey(nodeId)))
        );
        var rot = process.run().resultOfType(outputClass);
        return rot;
    }


    /**
     * Initialize an orchestrator node with a goal.
     * Creates main worktree, submodule worktrees, and base spec.
     */
    public void initializeOrchestrator(String repositoryUrl, String baseBranch,
                                       String goal, String title, String nodeId) {
        log.info("Initializing {}:{}.", repositoryUrl, baseBranch);

        String resolvedNodeId = nodeId != null ? nodeId : ArtifactKey.createRoot().value();

        if (nodeExists(resolvedNodeId)) {
            log.info("Orchestrator node {} already exists; skipping initialization", resolvedNodeId);
            return;
        }

        String derivedBranch = baseBranch + "-" + UUID.randomUUID();
        // Create main worktree
        MainWorktreeContext mainWorktree = worktreeService.createMainWorktree(
                repositoryUrl, baseBranch, derivedBranch, resolvedNodeId);
        List<SubmoduleWorktreeContext> submoduleContexts = mainWorktree.submoduleWorktrees();

        // Create orchestrator node
        OrchestratorNode orchestrator = OrchestratorNode.builder()
                .nodeId(resolvedNodeId)
                .title(Optional.ofNullable(title).orElse("Orchestrator")).goal(goal)
                .status(Events.NodeStatus.READY)
                .createdAt(Instant.now())
                .lastUpdatedAt(Instant.now())
                .worktreeContext(mainWorktree)
                .build();

        if (mainWorktree.hasSubmodules()) {
            for (SubmoduleWorktreeContext subWorktree : submoduleContexts) {
                this.orchestrator.emitWorktreeCreatedEvent(subWorktree.worktreeId(), nodeId,
                        subWorktree.worktreePath().toString(), "submodule", subWorktree.submoduleName());
                worktreeRepository.save(subWorktree);
            }
        }


        // Save to repositories
        this.orchestrator.emitNodeAddedEvent(orchestrator, orchestrator.parentNodeId());
        worktreeRepository.save(mainWorktree);

        this.orchestrator.emitWorktreeCreatedEvent(mainWorktree.worktreeId(), resolvedNodeId,
                mainWorktree.worktreePath().toString(), "main", null);
    }

    private boolean nodeExists(String nodeId) {
        return nodeId != null && orchestrator.getNode(nodeId).isPresent();
    }

}
