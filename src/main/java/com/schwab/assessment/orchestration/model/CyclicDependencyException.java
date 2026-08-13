package com.schwab.assessment.orchestration.model;

/**
 * Thrown by {@link com.schwab.assessment.orchestration.DependencyGraph} when
 * a topological sort is attempted on a graph containing a cycle.
 */
public class CyclicDependencyException extends RuntimeException {

    public CyclicDependencyException(String message) {
        super(message);
    }
}
