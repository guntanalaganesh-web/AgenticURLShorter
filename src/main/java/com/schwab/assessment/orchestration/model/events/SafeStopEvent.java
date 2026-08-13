package com.schwab.assessment.orchestration.model.events;

import com.schwab.assessment.orchestration.model.Stage;
import org.springframework.context.ApplicationEvent;

import java.util.UUID;

/**
 * Published by {@link com.schwab.assessment.orchestration.OrchestrationEngine}
 * when the pipeline halts safely, either because a stage exhausted its
 * retries or because a {@link com.schwab.assessment.orchestration.GateKeeper}
 * gate was rejected. Signals that no further stages will execute for this run.
 */
public class SafeStopEvent extends ApplicationEvent {

    private final UUID runId;
    private final Stage haltedAtStage;
    private final String reason;

    public SafeStopEvent(Object source, UUID runId, Stage haltedAtStage, String reason) {
        super(source);
        this.runId = runId;
        this.haltedAtStage = haltedAtStage;
        this.reason = reason;
    }

    public UUID getRunId() {
        return runId;
    }

    public Stage getHaltedAtStage() {
        return haltedAtStage;
    }

    public String getReason() {
        return reason;
    }
}
