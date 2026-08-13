package com.schwab.assessment.orchestration;

import com.schwab.assessment.orchestration.model.CyclicDependencyException;
import com.schwab.assessment.orchestration.model.Stage;
import com.schwab.assessment.orchestration.model.StageState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Directed acyclic graph of SDLC stage dependencies. Owns the topological
 * ordering rules the orchestration engine uses to decide which stages are
 * runnable at any point in time, including which stages may run in
 * parallel (any set of stages whose dependencies are all satisfied).
 *
 * <p>The standard graph, built by {@link #standardSdlcGraph()}, wires:
 * REQUIREMENTS &rarr; ARCHITECTURE &rarr; TASK_PLANNING &rarr;
 * {IMPLEMENTATION, TESTING, DOCUMENTATION} &rarr; RELEASE_READINESS, with
 * the three middle stages eligible to run concurrently.
 */
public class DependencyGraph {

    private final Map<Stage, Set<Stage>> dependencies = new EnumMap<>(Stage.class);
    private final Map<Stage, Set<Stage>> dependents = new EnumMap<>(Stage.class);

    public static DependencyGraph standardSdlcGraph() {
        DependencyGraph graph = new DependencyGraph();
        for (Stage stage : Stage.values()) {
            graph.addNode(stage);
        }
        graph.addDependency(Stage.ARCHITECTURE, Stage.REQUIREMENTS);
        graph.addDependency(Stage.TASK_PLANNING, Stage.ARCHITECTURE);
        graph.addDependency(Stage.IMPLEMENTATION, Stage.TASK_PLANNING);
        graph.addDependency(Stage.TESTING, Stage.TASK_PLANNING);
        graph.addDependency(Stage.DOCUMENTATION, Stage.TASK_PLANNING);
        graph.addDependency(Stage.RELEASE_READINESS, Stage.IMPLEMENTATION);
        graph.addDependency(Stage.RELEASE_READINESS, Stage.TESTING);
        graph.addDependency(Stage.RELEASE_READINESS, Stage.DOCUMENTATION);
        return graph;
    }

    public void addNode(Stage stage) {
        dependencies.computeIfAbsent(stage, s -> new LinkedHashSet<>());
        dependents.computeIfAbsent(stage, s -> new LinkedHashSet<>());
    }

    /**
     * Declares that {@code stage} cannot run until {@code dependsOn} has completed.
     */
    public void addDependency(Stage stage, Stage dependsOn) {
        addNode(stage);
        addNode(dependsOn);
        dependencies.get(stage).add(dependsOn);
        dependents.get(dependsOn).add(stage);
    }

    public Set<Stage> getDirectDependencies(Stage stage) {
        return Set.copyOf(dependencies.getOrDefault(stage, Set.of()));
    }

    public Set<Stage> getDirectDependents(Stage stage) {
        return Set.copyOf(dependents.getOrDefault(stage, Set.of()));
    }

    /**
     * All stages reachable by following "depends on me" edges from
     * {@code stage}, i.e. everything downstream of it.
     */
    public Set<Stage> getTransitiveDependents(Stage stage) {
        Set<Stage> visited = new LinkedHashSet<>();
        Deque<Stage> queue = new ArrayDeque<>(getDirectDependents(stage));
        while (!queue.isEmpty()) {
            Stage current = queue.poll();
            if (visited.add(current)) {
                queue.addAll(getDirectDependents(current));
            }
        }
        return visited;
    }

    /**
     * Kahn's algorithm topological sort over the full node set.
     *
     * @throws CyclicDependencyException if the graph contains a cycle
     */
    public List<Stage> topologicalOrder() {
        Map<Stage, Integer> inDegree = new EnumMap<>(Stage.class);
        for (Stage stage : dependencies.keySet()) {
            inDegree.put(stage, dependencies.get(stage).size());
        }

        Deque<Stage> ready = new ArrayDeque<>();
        inDegree.forEach((stage, degree) -> {
            if (degree == 0) {
                ready.add(stage);
            }
        });

        List<Stage> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            Stage current = ready.poll();
            order.add(current);
            for (Stage dependent : getDirectDependents(current)) {
                int updated = inDegree.merge(dependent, -1, Integer::sum);
                if (updated == 0) {
                    ready.add(dependent);
                }
            }
        }

        if (order.size() != dependencies.size()) {
            Set<Stage> unresolved = new LinkedHashSet<>(dependencies.keySet());
            unresolved.removeAll(order);
            throw new CyclicDependencyException("Cycle detected among stages: " + unresolved);
        }
        return Collections.unmodifiableList(order);
    }

    /**
     * Stages that are still PENDING and whose direct dependencies have all
     * reached a terminal, satisfying state (COMPLETED or SKIPPED). This is
     * what makes {@link OrchestrationEngine} run IMPLEMENTATION, TESTING and
     * DOCUMENTATION concurrently once TASK_PLANNING completes.
     */
    public List<Stage> getExecutableStages(Map<Stage, StageState> currentStates) {
        List<Stage> executable = new ArrayList<>();
        for (Stage stage : dependencies.keySet()) {
            if (currentStates.get(stage) != StageState.PENDING) {
                continue;
            }
            boolean dependenciesSatisfied = getDirectDependencies(stage).stream()
                    .allMatch(dep -> currentStates.get(dep) == StageState.COMPLETED
                            || currentStates.get(dep) == StageState.SKIPPED);
            if (dependenciesSatisfied) {
                executable.add(stage);
            }
        }
        return executable;
    }

    public boolean isTerminal(Map<Stage, StageState> currentStates) {
        return currentStates.values().stream().allMatch(
                state -> state == StageState.COMPLETED || state == StageState.SKIPPED || state == StageState.FAILED);
    }
}
