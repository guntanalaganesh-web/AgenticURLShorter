package com.schwab.assessment.orchestration.model;

/**
 * The raw outcome produced by a single invocation of a {@link StageHandler}.
 * {@code exitCriteriaMet} lets a handler run to completion without throwing
 * yet still signal that the stage's success condition was not satisfied
 * (e.g. a test report that came back under the coverage threshold).
 *
 * @param artifact        the domain artifact produced by the stage (may be null on failure)
 * @param exitCriteriaMet whether the stage's exit criteria were satisfied
 * @param notes           human-readable explanation, used as the failure reason when not met
 */
public record StageExecutionResult(Object artifact, boolean exitCriteriaMet, String notes) {

    public static StageExecutionResult success(Object artifact, String notes) {
        return new StageExecutionResult(artifact, true, notes);
    }

    public static StageExecutionResult failure(String notes) {
        return new StageExecutionResult(null, false, notes);
    }
}
