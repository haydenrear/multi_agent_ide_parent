package com.hayden.multiagentide.repository;

import com.hayden.acp_cdc_ai.acp.events.Events;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Stream;

public interface EventStreamRepository {

    void save(Events.GraphEvent graphEvent);

    List<Events.GraphEvent> list();

    Optional<Events.GraphEvent> findById(String eventId);

    <T extends Events.GraphEvent> Optional<T> getLastMatching(Class<T> v, Predicate<T> toMatch);

    <T extends Events.GraphEvent> Stream<T> getAllMatching(Class<T> v, Predicate<T> toMatch);

    /**
     * Compute event-based metrics for a single graph node.
     * Scans events where event.nodeId() is equal to or a descendant of the given nodeId.
     */
    Optional<NodeEventMetrics> computeMetrics(String nodeId);

    /**
     * Compute aggregate statistics for all events under a root scope.
     */
    ScopedEventStats computeScopedStats(String rootNodeId, Instant errorWindowStart);

    record NodeEventMetrics(
            int totalEvents,
            int nodeErrorCount,
            int chatSessionEvents,
            int chatMessageEvents,
            int thoughtDeltas,
            long thoughtTokens,
            int streamDeltas,
            long streamTokens,
            int toolEvents,
            int otherEvents
    ) {}

    record ScopedEventStats(
            int totalEvents,
            Map<String, Integer> eventTypeCounts,
            int recentErrorCount,
            Map<String, Integer> recentErrorsByNodeType,
            long totalThoughtTokens,
            long totalStreamTokens,
            int chatSessionEvents,
            int chatMessageEvents
    ) {}

}
