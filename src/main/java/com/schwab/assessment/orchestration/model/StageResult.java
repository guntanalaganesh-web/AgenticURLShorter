package com.schwab.assessment.orchestration.model;

import java.time.Instant;

/**
 * The finalized outcome of executing a {@link Stage} for one pipeline run,
 * after all retry attempts have been exhausted or the stage has succeeded.
 * This is what {@link com.schwab.assessment.orchestration.StageExecutor}
 * returns to the {@link com.schwab.assessment.orchestration.OrchestrationEngine}.
 *
 * @param stage         the stage that was executed
 * @param runId         the pipeline run this result belongs to
 * @param attemptCount  number of attempts made (1..maxAttempts)
 * @param startedAt     wall-clock time of the first attempt
 * @param endedAt       wall-clock time the final attempt concluded
 * @param success       whether the stage ultimately completed successfully
 * @param output        the artifact produced (null on failure)
 * @param failureReason human-readable failure explanation (null on success)
 */
public record StageResult(
        Stage stage,
        java.util.UUID runId,
        int attemptCount,
        Instant startedAt,
        Instant endedAt,
        boolean success,
        Object output,
        String failureReason
) {

    public long durationMillis() {
        return java.time.Duration.between(startedAt, endedAt).toMillis();
    }
}
