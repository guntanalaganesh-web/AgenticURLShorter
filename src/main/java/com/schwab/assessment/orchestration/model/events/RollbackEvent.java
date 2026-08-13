package com.schwab.assessment.orchestration.model.events;

import com.schwab.assessment.orchestration.model.Stage;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Published whenever the engine restores pipeline state to the last stable
 * {@link com.schwab.assessment.orchestration.model.Checkpoint}, whether as a
 * between-attempt recovery step or a final rollback after retries are
 * exhausted. {@link com.schwab.assessment.orchestration.ObservabilityCollector}
 * increments its rollback counter for each occurrence.
 */
public class RollbackEvent extends ApplicationEvent {

    private final UUID runId;
    private final Stage failedStage;
    private final Stage restoredToStage;
    private final String reason;

    public RollbackEvent(Object source, UUID runId, Stage failedStage, Stage restoredToStage, String reason) {
        super(source);
        this.runId = runId;
        this.failedStage = failedStage;
        this.restoredToStage = restoredToStage;
        this.reason = reason;
    }

    public UUID getRunId() {
        return runId;
    }

    public Stage getFailedStage() {
        return failedStage;
    }

    public Stage getRestoredToStage() {
        return restoredToStage;
    }

    public String getReason() {
        return reason;
    }
}
