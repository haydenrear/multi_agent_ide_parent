package com.hayden.multiagentide.repository;

import com.hayden.acp_cdc_ai.acp.events.ArtifactKey;
import com.hayden.acp_cdc_ai.acp.events.Events;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;

@Slf4j
@Component
public class InMemoryEventStreamRepository implements EventStreamRepository {

    private final Map<String, List<Events.GraphEvent>> events = new ConcurrentHashMap<>();
    private final Map<String, Events.GraphEvent> byId = new ConcurrentHashMap<>();

    @Override
    public void save(Events.GraphEvent graphEvent) {
        events.compute(graphEvent.nodeId(), (key, prev) -> {
            if (prev == null)
                prev = new ArrayList<>();

            prev.add(graphEvent);

            return prev;
        });
        if (graphEvent != null && graphEvent.eventId() != null) {
            byId.put(graphEvent.eventId(), graphEvent);
        }
    }

    @Override
    public List<Events.GraphEvent> list() {
        return events.values()
                .stream()
                .flatMap(Collection::stream)
                .toList();
    }

    @Override
    public java.util.Optional<Events.GraphEvent> findById(String eventId) {
        if (eventId == null) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(byId.get(eventId));
    }

    @Override
    public <T extends Events.GraphEvent> Optional<T> getLastMatching(Class<T> v, Predicate<T> toMatch) {
        return events.values().stream()
                .flatMap(Collection::stream)
                .filter(gn -> gn.getClass().equals(v) || v.isAssignableFrom(gn.getClass()))
                .map(ge -> (T) ge)
                .filter(toMatch)
                .max(Comparator.comparing(Events.GraphEvent::timestamp))
                .map(t -> {
                    log.info("Found last matching {}:{}, {}", t.getClass(), t.nodeId(), t);
                    return t;
                });
    }

    @Override
    public <T extends Events.GraphEvent> Stream<T> getAllMatching(Class<T> v, Predicate<T> toMatch) {
        return events.values().stream()
                .flatMap(Collection::stream)
                .filter(gn -> gn.getClass().equals(v) || v.isAssignableFrom(gn.getClass()))
                .map(ge -> (T) ge)
                .filter(toMatch);
    }

    @Override
    public Optional<NodeEventMetrics> computeMetrics(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) return Optional.empty();

        List<Events.GraphEvent> scoped = scopedEvents(nodeId);
        if (scoped.isEmpty()) return Optional.empty();

        int totalEvents = 0, errorCount = 0, chatSessions = 0, chatMessages = 0;
        int thoughtDeltas = 0, streamDeltas = 0, toolEvents = 0, otherEvents = 0;
        long thoughtTokens = 0L, streamTokens = 0L;

        for (Events.GraphEvent event : scoped) {
            totalEvents++;
            boolean specific = true;
            switch (event) {
                case Events.NodeErrorEvent ignored -> errorCount++;
                case Events.ChatSessionCreatedEvent ignored -> chatSessions++;
                case Events.AddMessageEvent ignored -> chatMessages++;
                case Events.NodeThoughtDeltaEvent t -> { thoughtDeltas++; thoughtTokens += t.tokenCount(); }
                case Events.NodeStreamDeltaEvent s -> { streamDeltas++; streamTokens += s.tokenCount(); }
                case Events.ToolCallEvent ignored -> toolEvents++;
                default -> specific = false;
            }
            if (!specific) otherEvents++;
        }

        return Optional.of(new NodeEventMetrics(
                totalEvents, errorCount, chatSessions, chatMessages,
                thoughtDeltas, thoughtTokens, streamDeltas, streamTokens,
                toolEvents, otherEvents
        ));
    }

    @Override
    public ScopedEventStats computeScopedStats(String rootNodeId, Instant errorWindowStart) {
        List<Events.GraphEvent> scoped = scopedEvents(rootNodeId);

        Map<String, Integer> eventTypeCounts = new LinkedHashMap<>();
        Map<String, Integer> recentErrorsByNodeType = new LinkedHashMap<>();
        int recentErrorCount = 0;
        long totalThoughtTokens = 0L, totalStreamTokens = 0L;
        int chatSessions = 0, chatMessages = 0;

        for (Events.GraphEvent event : scoped) {
            eventTypeCounts.merge(event.eventType(), 1, Integer::sum);
            switch (event) {
                case Events.NodeErrorEvent error -> {
                    if (errorWindowStart != null && !event.timestamp().isBefore(errorWindowStart)) {
                        recentErrorCount++;
                        String key = error.nodeType() == null ? "UNKNOWN" : error.nodeType().name();
                        recentErrorsByNodeType.merge(key, 1, Integer::sum);
                    }
                }
                case Events.NodeThoughtDeltaEvent t -> totalThoughtTokens += t.tokenCount();
                case Events.NodeStreamDeltaEvent s -> totalStreamTokens += s.tokenCount();
                case Events.ChatSessionCreatedEvent ignored -> chatSessions++;
                case Events.AddMessageEvent ignored -> chatMessages++;
                default -> {}
            }
        }

        return new ScopedEventStats(
                scoped.size(), eventTypeCounts,
                recentErrorCount, recentErrorsByNodeType,
                totalThoughtTokens, totalStreamTokens,
                chatSessions, chatMessages
        );
    }

    private List<Events.GraphEvent> scopedEvents(String scopeNodeId) {
        return events.entrySet().stream()
                .filter(e -> matchesNodeScope(scopeNodeId, e.getKey()))
                .flatMap(e -> e.getValue().stream())
                .toList();
    }

    private static boolean matchesNodeScope(String scopeNodeId, String eventNodeId) {
        if (scopeNodeId == null || scopeNodeId.isBlank() || eventNodeId == null || eventNodeId.isBlank()) {
            return false;
        }
        if (scopeNodeId.equals(eventNodeId)) {
            return true;
        }
        try {
            return new ArtifactKey(eventNodeId).isDescendantOf(new ArtifactKey(scopeNodeId));
        } catch (Exception ignored) {
            return eventNodeId.startsWith(scopeNodeId + "/");
        }
    }
}
