package com.schwab.assessment.orchestration;

import com.schwab.assessment.orchestration.model.CyclicDependencyException;
import com.schwab.assessment.orchestration.model.Stage;
import com.schwab.assessment.orchestration.model.StageState;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DependencyGraphTest {

    @Test
    void topologicalOrderRespectsEveryDeclaredDependency() {
        DependencyGraph graph = DependencyGraph.standardSdlcGraph();
        List<Stage> order = graph.topologicalOrder();

        assertEquals(Set.copyOf(List.of(Stage.values())), Set.copyOf(order));
        assertTrue(order.indexOf(Stage.REQUIREMENTS) < order.indexOf(Stage.ARCHITECTURE));
        assertTrue(order.indexOf(Stage.ARCHITECTURE) < order.indexOf(Stage.TASK_PLANNING));
        assertTrue(order.indexOf(Stage.TASK_PLANNING) < order.indexOf(Stage.IMPLEMENTATION));
        assertTrue(order.indexOf(Stage.TASK_PLANNING) < order.indexOf(Stage.TESTING));
        assertTrue(order.indexOf(Stage.TASK_PLANNING) < order.indexOf(Stage.DOCUMENTATION));
        assertTrue(order.indexOf(Stage.IMPLEMENTATION) < order.indexOf(Stage.RELEASE_READINESS));
        assertTrue(order.indexOf(Stage.TESTING) < order.indexOf(Stage.RELEASE_READINESS));
        assertTrue(order.indexOf(Stage.DOCUMENTATION) < order.indexOf(Stage.RELEASE_READINESS));
    }

    @Test
    void implementationTestingAndDocumentationBecomeExecutableTogether() {
        DependencyGraph graph = DependencyGraph.standardSdlcGraph();
        Map<Stage, StageState> states = allPending();
        states.put(Stage.REQUIREMENTS, StageState.COMPLETED);
        states.put(Stage.ARCHITECTURE, StageState.COMPLETED);
        states.put(Stage.TASK_PLANNING, StageState.COMPLETED);

        List<Stage> executable = graph.getExecutableStages(states);

        assertEquals(new HashSet<>(List.of(Stage.IMPLEMENTATION, Stage.TESTING, Stage.DOCUMENTATION)),
                new HashSet<>(executable));
    }

    @Test
    void releaseReadinessIsNotExecutableUntilAllThreeParallelStagesComplete() {
        DependencyGraph graph = DependencyGraph.standardSdlcGraph();
        Map<Stage, StageState> states = allPending();
        states.put(Stage.REQUIREMENTS, StageState.COMPLETED);
        states.put(Stage.ARCHITECTURE, StageState.COMPLETED);
        states.put(Stage.TASK_PLANNING, StageState.COMPLETED);
        states.put(Stage.IMPLEMENTATION, StageState.COMPLETED);
        states.put(Stage.TESTING, StageState.COMPLETED);
        // DOCUMENTATION still PENDING

        assertTrue(graph.getExecutableStages(states).stream().noneMatch(s -> s == Stage.RELEASE_READINESS));

        states.put(Stage.DOCUMENTATION, StageState.COMPLETED);
        assertTrue(graph.getExecutableStages(states).contains(Stage.RELEASE_READINESS));
    }

    @Test
    void cyclicGraphThrowsOnTopologicalSort() {
        DependencyGraph graph = new DependencyGraph();
        graph.addDependency(Stage.ARCHITECTURE, Stage.REQUIREMENTS);
        graph.addDependency(Stage.TASK_PLANNING, Stage.ARCHITECTURE);
        graph.addDependency(Stage.REQUIREMENTS, Stage.TASK_PLANNING); // closes the cycle

        assertThrows(CyclicDependencyException.class, graph::topologicalOrder);
    }

    private Map<Stage, StageState> allPending() {
        Map<Stage, StageState> states = new EnumMap<>(Stage.class);
        for (Stage stage : Stage.values()) {
            states.put(stage, StageState.PENDING);
        }
        return states;
    }
}
