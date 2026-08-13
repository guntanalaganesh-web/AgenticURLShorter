package com.schwab.assessment.orchestration.model;

import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/**
 * An immutable snapshot of pipeline state taken immediately after a stage
 * completes successfully. {@link com.schwab.assessment.orchestration.StageExecutor}
 * and {@link com.schwab.assessment.orchestration.OrchestrationEngine} restore
 * to the most recent checkpoint when a stage exhausts its retries, undoing
 * any partial progress made by the failing stage.
 *
 * @param afterStage   the stage whose completion produced this checkpoint
 * @param stageStates  snapshot of every stage's state at checkpoint time
 * @param artifacts    snapshot of every stage's latest artifact at checkpoint time
 * @param takenAt      when the checkpoint was captured
 */
public record Checkpoint(
        Stage afterStage,
        Map<Stage, StageState> stageStates,
        Map<Stage, Object> artifacts,
        Instant takenAt
) {

    public static Checkpoint capture(Stage afterStage, Map<Stage, StageState> states, Map<Stage, Object> artifacts) {
        return new Checkpoint(afterStage, new EnumMap<>(states), new EnumMap<>(artifacts), Instant.now());
    }
}
