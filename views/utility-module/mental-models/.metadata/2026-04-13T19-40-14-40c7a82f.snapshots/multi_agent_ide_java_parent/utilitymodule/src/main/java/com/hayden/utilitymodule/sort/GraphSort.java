package com.hayden.utilitymodule.sort;

import com.hayden.utilitymodule.MapFunctions;
import com.hayden.utilitymodule.proxies.ProxyUtil;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.aop.framework.AopProxyUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@UtilityClass
public class GraphSort {

    public static <T extends GraphSortable> Optional<T> is(GraphSortable graphSortable) {
        try {
            return Optional.ofNullable((T) graphSortable) ;
        } catch (ClassCastException e) {
            return Optional.empty();
        }
    }

    public interface GraphSortable {

        default <T extends GraphSortable> Optional<T> is(GraphSortable graphSortable) {
            try {
                return Optional.ofNullable((T) graphSortable) ;
            } catch (ClassCastException e) {
                return Optional.empty();
            }
        }

        default <T extends GraphSortable> List<Class<? extends T>> dependsOn() {
            return new ArrayList<>() ;
        }

        default <T extends GraphSortable>  List<T> parseAllDepsNotThis(Map<Class<? extends T>, T> values) {
            var r = retrieve(new HashSet<>(), values);
            r.remove(this);
            return r.stream().distinct().toList();
        }

        default <T extends GraphSortable>  List<T> parseAllDeps(Map<Class<? extends T>, T> values) {
            log.info("Parsing dependencies");
            return retrieve(new HashSet<>(), values);
        }

        default <T extends GraphSortable> List<T> retrieve(Set<String> prev,
                                                           Map<Class<? extends T>, T> r) {
            List<T> out = new ArrayList<>();
            prev.add(this.getClass().getName());
            List<T> notSorted = retrieveRecursive((T) this, prev, r, out);
            List<T> newSorted = new ArrayList<>(notSorted);
            newSorted.add((T) this);
            return newSorted;
        }

        private @NotNull <T extends GraphSortable> List<T> retrieveRecursive(T selfG,
                                                                             Set<String> prev,
                                                                             Map<Class<? extends T>, T> r,
                                                                             List<T> out) {
            return selfG.dependsOn()
                    .stream()
                    .peek(s -> {
                        if (prev.contains(s.getName()) && r.get(s).dependsOn().contains(selfG.getClass())) {
                            throw new RuntimeException("Found cycle.: %s, %s".formatted(s.getName(), r.getClass().getName()));
                        }
                        prev.add(s.getName());
                    })
                    .flatMap(i -> {
                        var f = r.get(i);
                        return Optional.ofNullable(f).stream();
                    })
                    .flatMap(s -> {
                        List<T> retrieve = s.retrieve(prev, r);
                        return retrieve.stream();
                    })
                    .collect(Collectors.toCollection(() -> out));
        }

    }

    public class GraphSortAlgo<T extends GraphSortable> {
        Map<Class<? extends T>, T> mappings;
        List<T> graphs;

        public GraphSortAlgo(List<T> mappings) {
            this.mappings = MapFunctions.CollectMap(mappings.stream()
                    .map(e -> Map.entry(getGraphClazz(e), e)));
            this.graphs = mappings;
        }

        public List<T> sort() {
            return sort(GraphSort::is) ;
        }

        public List<T> sort(Function<GraphSortable, Optional<T>> isDep) {
            // Create a map to store the dependencies of each graph
            Map<Class<? extends T>, List<Class<? extends T>>> dependencies = new HashMap<>();
            // Create a set to keep track of visited graphs during DFS
            Set<Class<? extends T>> visited = new HashSet<>();
            // Create a set to keep track of graphs that are currently being visited (i.e., in the recursion stack)
            Set<Class<? extends T>> recursionStack = new HashSet<>();
            // Create a list to store the sorted graphs
            List<Class<? extends T>> sortedGraphs = new ArrayList<>();

            // Initialize the dependencies map
            for (GraphSortable graph : graphs) {
//                TODO: handle proxies - jdk proxies and cglib proxies both
                dependencies.put((Class<? extends T>) graph.getClass(), graph.dependsOn());
            }

            // Perform DFS on each unvisited graph
            for (T graph : graphs) {
                if (!visited.contains(graph.getClass())) {
                    // If a cycle is detected, throw an exception
                    if (dfs((Class<? extends T>) graph.getClass(), dependencies, visited, recursionStack, sortedGraphs)) {
                        throw new RuntimeException("Cycle detected in the graph");
                    }
                }
            }

            return sortedGraphs.stream().map(mappings::get).filter(Objects::nonNull).toList();
        }


        /**
         * Performs a depth-first search on the given graph.
         *
         * @param graph         Current graph being visited
         * @param dependencies  Map of graph dependencies
         * @param visited       Set of visited graphs
         * @param recursionStack Set of graphs in the recursion stack
         * @param sortedGraphs  List of sorted graphs
         * @return True if no cycle is detected, false otherwise
         */
        private boolean dfs(Class<? extends T> graph, Map<Class<? extends T>, List<Class<? extends T>>> dependencies,
                            Set<Class<? extends T>> visited, Set<Class<? extends T>> recursionStack, List<Class<? extends T>> sortedGraphs) {
            // Mark the current graph as visited and add it to the recursion stack
            visited.add(graph);
            recursionStack.add(graph);

            // Visit each dependency of the current graph
            List<Class<? extends T>> graphDependencies = dependencies.getOrDefault(graph, List.of());
            for (Class<? extends T> dependency : graphDependencies) {
                // If the dependency is not visited, visit it
                if (!visited.contains(dependency)) {
                    if (dfs(dependency, dependencies, visited, recursionStack, sortedGraphs)) {
                        return true;
                    }
                }
                // If the dependency is in the recursion stack, a cycle is detected
                else if (recursionStack.contains(dependency)) {
                    return true;
                }
            }

            // Remove the current graph from the recursion stack and add it to the sorted list
            recursionStack.remove(graph);
            sortedGraphs.add(graph);

            return false;
        }

        private static <T extends GraphSortable> @NotNull Class<? extends T> getGraphClazz(T graph) {
            return (Class<? extends T>) graph.getClass();
        }

    }
    public static <T extends GraphSortable> List<T> sort(List<T> graphs) {
        return sort(graphs, GraphSort::is);
    }

    public static <T extends GraphSortable> List<T> sort(List<T> graphs,
                                                         Function<GraphSortable, Optional<T>> isDep) {
        var evilProxy = graphs.stream().filter(ProxyUtil::isProxy).findAny();
        if (evilProxy.isPresent()) {
            throw new RuntimeException("Detected proxy in graph: %s."
                    .formatted(evilProxy.map(t -> t.getClass()).map(Object::toString).orElse(null)));
        }
        GraphSortAlgo<T> algo = new GraphSortAlgo<>(graphs);
        return algo.sort();
    }

}
