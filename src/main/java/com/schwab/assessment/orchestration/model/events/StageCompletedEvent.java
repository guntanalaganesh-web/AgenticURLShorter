package com.schwab.assessment.orchestration.model.events;

import com.schwab.assessment.orchestration.model.StageResult;
import org.springframework.context.ApplicationEvent;

/**
 * Published by {@link com.schwab.assessment.orchestration.StageExecutor} when
 * a stage's exit criteria are met. Consumed by
 * {@link com.schwab.assessment.orchestration.ObservabilityCollector} to
 * update success-rate and latency metrics.
 */
public class StageCompletedEvent extends ApplicationEvent {

    private final StageResult result;

    public StageCompletedEvent(Object source, StageResult result) {
        super(source);
        this.result = result;
    }

    public StageResult getResult() {
        return result;
    }
}
