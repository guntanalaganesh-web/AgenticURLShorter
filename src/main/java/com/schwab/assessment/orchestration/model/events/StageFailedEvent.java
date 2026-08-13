package com.schwab.assessment.orchestration.model.events;

import com.schwab.assessment.orchestration.model.Stage;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Published by {@link com.schwab.assessment.orchestration.StageExecutor} on
 * every failed attempt of a stage (not only the final one), so
 * {@link com.schwab.assessment.orchestration.ObservabilityCollector} can
 * track retry frequency and mean-time-to-recovery.
 */
public class StageFailedEvent extends ApplicationEvent {

    private final UUID runId;
    private final Stage stage;
    private final int attempt;
    private final boolean finalFailure;
    private final String reason;

    public StageFailedEvent(Object source, UUID runId, Stage stage, int attempt, boolean finalFailure, String reason) {
        super(source);
        this.runId = runId;
        this.stage = stage;
        this.attempt = attempt;
        this.finalFailure = finalFailure;
        this.reason = reason;
    }

    public UUID getRunId() {
        return runId;
    }

    public Stage getStage() {
        return stage;
    }

    public int getAttempt() {
        return attempt;
    }

    public boolean isFinalFailure() {
        return finalFailure;
    }

    public String getReason() {
        return reason;
    }
}
