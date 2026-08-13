package com.schwab.assessment.orchestration.model;

/**
 * The fixed set of SDLC stages the orchestration engine can execute.
 * Ordering and parallelism between stages is expressed separately by
 * {@link com.schwab.assessment.orchestration.DependencyGraph}; this enum
 * only names the stages.
 */
public enum Stage {
    REQUIREMENTS,
    ARCHITECTURE,
    TASK_PLANNING,
    IMPLEMENTATION,
    TESTING,
    DOCUMENTATION,
    RELEASE_READINESS
}
