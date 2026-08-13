package com.schwab.assessment.orchestration.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only snapshot of a pipeline run's state, returned by
 * {@code GET /orchestration/status}.
 */
public record PipelineStatus(
        UUID runId,
        String scenarioType,
        String requirement,
        String overallStatus,
        Map<Stage, StageState> stageStates,
        boolean halted,
        String haltReason,
        Instant startedAt,
        Instant endedAt
) {

    public static PipelineStatus from(PipelineContext context) {
        String overall;
        if (context.isHalted()) {
            overall = "HALTED";
        } else if (context.snapshotStates().values().stream()
                .allMatch(s -> s == StageState.COMPLETED || s == StageState.SKIPPED)) {
            overall = "COMPLETED";
        } else {
            overall = "RUNNING";
        }
        return new PipelineStatus(
                context.getRunId(),
                context.getScenarioType(),
                context.getRequirement(),
                overall,
                context.snapshotStates(),
                context.isHalted(),
                context.getHaltReason(),
                context.getStartedAt(),
                context.getEndedAt()
        );
    }
}
