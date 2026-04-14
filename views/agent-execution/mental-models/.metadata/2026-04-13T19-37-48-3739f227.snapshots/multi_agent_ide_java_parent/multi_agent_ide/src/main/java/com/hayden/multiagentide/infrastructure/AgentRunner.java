package com.hayden.multiagentide.infrastructure;

import com.embabel.agent.api.channel.MessageOutputChannelEvent;
import com.embabel.agent.api.channel.OutputChannel;
import com.embabel.agent.core.AgentPlatform;
import com.embabel.agent.core.AgentProcess;
import com.embabel.chat.UserMessage;
import com.hayden.multiagentide.agent.AgentInterfaces;
import com.hayden.multiagentide.agent.AgentLifecycleHandler;
import com.hayden.multiagentide.agent.AgentModels;
import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.model.nodes.GraphNode;
import com.hayden.multiagentide.model.nodes.OrchestratorNode;
import java.util.List;
import javax.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

/**
 * Executes the workflow agent once and forwards user messages to the agent process.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AgentRunner {

    private final AgentLifecycleHandler agentLifecycleHandler;
    private final OutputChannel llmOutputChannel;
    private final AgentPlatform agentPlatform;

    public record AgentDispatchArgs(
            GraphNode self,
            @Nullable GraphNode parent,
            List<GraphNode> children,
            Events.AgentEvent agentEvent
    ) {
    }

    private void addMessageToAgent(AgentDispatchArgs input, Events.AddMessageEvent addMessageEvent) {
        addMessageToAgent(addMessageEvent.toAddMessage(), addMessageEvent.nodeId());
    }

    private void addMessageToAgent(String input, String nodeId) {
        llmOutputChannel.send(new MessageOutputChannelEvent(nodeId, new UserMessage(input)));
    }

    public void runOnAgent(AgentDispatchArgs d) {
        switch (d.agentEvent) {
            case Events.AddMessageEvent addMessageEvent -> addMessageToAgent(d, addMessageEvent);
            case Events.NodeAddedEvent added -> maybeRunWorkflow(added, d.self);
            default -> {}
        }
    }

    private void maybeRunWorkflow(@NotNull Events.NodeAddedEvent addedEvent,
                                  GraphNode node) {
        if (!(node instanceof OrchestratorNode orchestratorNode))
            return;
        if (orchestratorNode.status() != Events.NodeStatus.READY)
            return;
        try {
            ArtifactKey contextId = null;
            AgentProcess agentProcess = agentPlatform.getAgentProcess(orchestratorNode.nodeId());
            if (agentProcess == null) {
                contextId = new ArtifactKey(orchestratorNode.nodeId());
            } else {
                if (agentProcess.getStatus() == null) {
                    throw new RuntimeException("This should never happen - because of kotlin nullity guarantees!");
                }

                switch(agentProcess.getStatus()) {
                    case PAUSED, STUCK, NOT_STARTED, COMPLETED, WAITING ->
                            contextId = new ArtifactKey(orchestratorNode.nodeId());
                    case RUNNING -> {
                        log.warn("Received a request to run an orchestrator node that was already in running status. " +
                                 "Would/will be ignored");
                        return;
                    }
                    case KILLED, TERMINATED, FAILED ->
                            throw new RuntimeException("Attempted to run orchestrator with ID that already existed and was in incompatible state: %s."
                                    .formatted(agentProcess.getStatus()));
                }
            }

            agentLifecycleHandler.runAgent(
                    AgentInterfaces.ORCHESTRATOR_AGENT,
                    new AgentModels.OrchestratorRequest(contextId, orchestratorNode.goal(), "ORCHESTRATOR_ONBOARDING"),
                    AgentModels.OrchestratorCollectorResult.class,
                    orchestratorNode.nodeId());


        } catch (Exception e) {
            log.error("Failed to start workflow agent for orchestrator {}", orchestratorNode.nodeId(), e);
            throw e;
        }
    }
}
