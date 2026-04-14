package com.hayden.multiagentide.repository;

import com.hayden.acp_cdc_ai.acp.events.Events;
import com.hayden.multiagentide.model.nodes.GraphNode;
import com.hayden.multiagentide.model.nodes.InterruptNode;
import com.hayden.multiagentide.model.nodes.ReviewNode;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * In-memory implementation of GraphRepository using ConcurrentHashMap for thread safety.
 */
@Repository
public class InMemoryGraphRepository implements GraphRepository {

    private final ConcurrentHashMap<String, GraphNode> nodes = new ConcurrentHashMap<>();

    @Override
    public void save(GraphNode node) {
        nodes.put(node.nodeId(), node);
    }

    @Override
    public Optional<GraphNode> findById(String nodeId) {
        return Optional.ofNullable(nodes.get(nodeId));
    }

    @Override
    public Optional<GraphNode> findInterruptByOrigin(String nodeId) {
        return nodes.entrySet()
                .stream()
                .flatMap(s -> {
                    if (s.getValue() instanceof ReviewNode r && Objects.equals(nodeId, r.reviewedNodeId())) {
                        return Stream.of(s.getValue());
                    }

                    if (s.getValue() instanceof InterruptNode r && Objects.equals(nodeId, r.interruptOriginNodeId())) {
                        return Stream.of(s.getValue());
                    }

                    return Stream.empty();
                })
                .findFirst();
    }

    @Override
    public List<GraphNode> findAll() {
        return new ArrayList<>(nodes.values());
    }

    @Override
    public List<GraphNode> findByParentId(String parentNodeId) {
        return nodes.values().stream()
                .filter(node -> parentNodeId.equals(node.parentNodeId()))
                .collect(Collectors.toList());
    }

    @Override
    public List<GraphNode> findByType(Events.NodeType nodeType) {
        return nodes.values().stream()
                .filter(node -> node.nodeType() == nodeType)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String nodeId) {
        nodes.remove(nodeId);
    }

    @Override
    public boolean exists(String nodeId) {
        return nodes.containsKey(nodeId);
    }

    @Override
    public long count() {
        return nodes.size();
    }

    @Override
    public void clear() {
        nodes.clear();
    }

    @Override
    public Map<String, GraphNode> findSubtree(String rootNodeId) {
        Map<String, GraphNode> result = new LinkedHashMap<>();
        collectSubtree(rootNodeId, result);
        return result;
    }

    private void collectSubtree(String nodeId, Map<String, GraphNode> result) {
        if (nodeId == null || result.containsKey(nodeId)) return;
        GraphNode node = nodes.get(nodeId);
        if (node == null) return;
        result.put(nodeId, node);
        for (String childId : node.childNodeIds()) {
            collectSubtree(childId, result);
        }
    }
}
