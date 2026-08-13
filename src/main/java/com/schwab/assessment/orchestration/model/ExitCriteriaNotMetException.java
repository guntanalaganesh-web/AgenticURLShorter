package com.schwab.assessment.orchestration.model;

/**
 * Raised internally by {@link com.schwab.assessment.orchestration.StageExecutor}
 * when a {@link StageHandler} returns normally but reports that the
 * stage's exit criteria were not satisfied, so the failure flows through
 * the same retry/rollback path as a thrown exception.
 */
public class ExitCriteriaNotMetException extends RuntimeException {

    public ExitCriteriaNotMetException(String message) {
        super(message);
    }
}
