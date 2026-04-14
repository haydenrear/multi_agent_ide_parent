package com.hayden.multiagentide.adapter;

import com.hayden.acp_cdc_ai.acp.events.EventListener;
import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.repository.EventStreamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SaveEventToEventRepositoryListener implements EventListener {

    private final EventStreamRepository eventStreamRepository;


    @Override
    public String listenerId() {
        return "save-to-event-stream";
    }

    @Override
    public void onEvent(Events.GraphEvent event) {
        eventStreamRepository.save(event);
    }
}
