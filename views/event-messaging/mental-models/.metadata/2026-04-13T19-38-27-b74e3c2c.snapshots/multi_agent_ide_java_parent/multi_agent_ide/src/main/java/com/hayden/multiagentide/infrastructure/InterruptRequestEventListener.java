package com.hayden.multiagentide.infrastructure;

import com.hayden.acp_cdc_ai.acp.events.EventBus;
import com.hayden.acp_cdc_ai.acp.events.EventListener;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.agent.decorator.InterruptAddMessageComposer;
import com.hayden.multiagentide.agent.decorator.prompt.FilterPropertiesDecorator;
import com.hayden.multiagentide.orchestration.ComputationGraphOrchestrator;
import com.hayden.multiagentide.model.nodes.GraphNode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class InterruptRequestEventListener implements EventListener {

    private final ComputationGraphOrchestrator orchestrator;
    private final FilterPropertiesDecorator filterPropertiesDecorator;
    private final InterruptAddMessageComposer addMessageComposer;

    private EventBus eventBus;

    @Autowired @Lazy
    public void setEventBus(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    @Override
    public String listenerId() {
        return "InterruptRequestEventListener";
    }

    @Override
    public void onEvent(Events.GraphEvent event) {
        if (!(event instanceof Events.InterruptRequestEvent interruptRequestEvent)) {
            return;
        }

        Optional<GraphNode> nodeOpt = orchestrator.getNode(interruptRequestEvent.nodeId());
        if (nodeOpt.isEmpty() || nodeOpt.get().status() != Events.NodeStatus.RUNNING) {
            eventBus.publish(new Events.InterruptStatusEvent(
                    UUID.randomUUID().toString(),
                    Instant.now(),
                    interruptRequestEvent.nodeId(),
                    String.valueOf(interruptRequestEvent.interruptType()),
                    "IGNORED_TARGET_NOT_ACTIVE",
                    interruptRequestEvent.nodeId(),
                    null
            ));
            return;
        }

        GraphNode node = nodeOpt.get();
        String addMessage = addMessageComposer.composeInterruptMessage(interruptRequestEvent, node);
        eventBus.publish(new Events.AddMessageEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                interruptRequestEvent.nodeId(),
                addMessage
        ));
        filterPropertiesDecorator.storeEvent(interruptRequestEvent);
        eventBus.publish(new Events.InterruptStatusEvent(
                UUID.randomUUID().toString(),
                Instant.now(),
                interruptRequestEvent.nodeId(),
                String.valueOf(interruptRequestEvent.interruptType()),
                "REQUESTED",
                interruptRequestEvent.nodeId(),
                null
        ));
    }
}
