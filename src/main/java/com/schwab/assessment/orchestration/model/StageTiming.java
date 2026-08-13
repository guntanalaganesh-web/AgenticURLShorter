package com.schwab.assessment.orchestration.model;

import java.time.Instant;

/**
 * Lean, API-safe projection of a {@link StageResult}: just the timing and
 * retry facts the dashboard needs to render "attempt 2/3" and a duration
 * per stage node, without exposing the stage's raw artifact object.
 */
public record StageTiming(int attemptCount, Instant startedAt, Instant endedAt, long durationMillis) {

    public static StageTiming from(StageResult result) {
        return new StageTiming(result.attemptCount(), result.startedAt(), result.endedAt(),
                result.durationMillis());
    }
}
