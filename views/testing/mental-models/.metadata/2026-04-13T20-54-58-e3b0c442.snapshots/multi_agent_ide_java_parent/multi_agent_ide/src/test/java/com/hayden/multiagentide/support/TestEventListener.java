package com.hayden.multiagentide.support;

import com.hayden.acp_cdc_ai.acp.events.EventListener;
import com.hayden.acp_cdc_ai.acp.events.Events;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class TestEventListener implements EventListener {

    private final CopyOnWriteArrayList<Events.GraphEvent> events =
        new CopyOnWriteArrayList<>();

    private volatile TestTraceWriter traceWriter;

    @Override
    public String listenerId() {
        return "test-event-listener";
    }

    @Override
    public void onEvent(Events.GraphEvent event) {
        events.add(event);
        if (traceWriter != null) {
            traceWriter.appendEvent(event);
        }
    }

    public void setTraceWriter(TestTraceWriter traceWriter) {
        this.traceWriter = traceWriter;
    }

    public void clear() {
        events.clear();
        traceWriter = null;
    }

    public List<Events.GraphEvent> allEvents() {
        return new ArrayList<>(events);
    }

    public <T extends Events.GraphEvent> List<T> eventsOfType(Class<T> type) {
        return events.stream()
            .filter(type::isInstance)
            .map(type::cast)
            .collect(Collectors.toList());
    }

    public <T extends Events.GraphEvent> List<T> eventsOfType(
        Class<T> type,
        Predicate<T> predicate
    ) {
        return events.stream()
            .filter(type::isInstance)
            .map(type::cast)
            .filter(predicate)
            .collect(Collectors.toList());
    }

    public long countOfType(Class<? extends Events.GraphEvent> type) {
        return events.stream()
            .filter(type::isInstance)
            .count();
    }

    public boolean hasEventOfType(Class<? extends Events.GraphEvent> type) {
        return events.stream().anyMatch(type::isInstance);
    }
}
