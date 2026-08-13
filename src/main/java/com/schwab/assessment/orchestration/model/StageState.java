package com.schwab.assessment.orchestration.model;

/**
 * Lifecycle state of a single {@link Stage} within one pipeline run.
 */
public enum StageState {
    PENDING,
    BLOCKED,
    RUNNING,
    COMPLETED,
    FAILED,
    SKIPPED
}
