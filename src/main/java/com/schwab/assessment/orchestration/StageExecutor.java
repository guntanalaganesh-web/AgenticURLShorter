package com.schwab.assessment.orchestration;

import com.schwab.assessment.config.OrchestrationProperties;
import com.schwab.assessment.orchestration.model.Checkpoint;
import com.schwab.assessment.orchestration.model.ExitCriteriaNotMetException;
import com.schwab.assessment.orchestration.model.PipelineContext;
import com.schwab.assessment.orchestration.model.Stage;
import com.schwab.assessment.orchestration.model.StageExecutionResult;
import com.schwab.assessment.orchestration.model.StageHandler;
import com.schwab.assessment.orchestration.model.StageResult;
import com.schwab.assessment.orchestration.model.events.RollbackEvent;
import com.schwab.assessment.orchestration.model.events.StageCompletedEvent;
import com.schwab.assessment.orchestration.model.events.StageFailedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Executes a single {@link Stage}'s {@link StageHandler} with bounded
 * retries and exponential backoff. On success, publishes
 * {@link StageCompletedEvent}; on every failed attempt, publishes
 * {@link StageFailedEvent}; and once retries are exhausted, restores
 * pipeline state to the last stable {@link Checkpoint} and publishes
 * {@link RollbackEvent}.
 */
@Component
public class StageExecutor {

    private static final Logger log = LoggerFactory.getLogger(StageExecutor.class);

    private final OrchestrationProperties properties;
    private final ApplicationEventPublisher eventPublisher;

    public StageExecutor(OrchestrationProperties properties, ApplicationEventPublisher eventPublisher) {
        this.properties = properties;
        this.eventPublisher = eventPublisher;
    }

    public StageResult execute(PipelineContext context, Stage stage) {
        StageHandler handler = context.getHandler(stage);
        int maxAttempts = properties.retry().maxAttempts();
        Instant startedAt = Instant.now();
        String lastFailureReason = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                StageExecutionResult attemptResult = handler.execute(context, stage, attempt);
                if (!attemptResult.exitCriteriaMet()) {
                    throw new ExitCriteriaNotMetException(
                            attemptResult.notes() == null ? "exit criteria not met" : attemptResult.notes());
                }

                Instant endedAt = Instant.now();
                context.setArtifact(stage, attemptResult.artifact());
                StageResult result = new StageResult(stage, context.getRunId(), attempt, startedAt, endedAt,
                        true, attemptResult.artifact(), null);
                eventPublisher.publishEvent(new StageCompletedEvent(this, result));
                log.info("Stage {} completed for run {} on attempt {}/{}", stage, context.getRunId(), attempt,
                        maxAttempts);
                return result;

            } catch (Exception e) {
                lastFailureReason = describe(e);
                boolean finalAttempt = attempt == maxAttempts;
                eventPublisher.publishEvent(
                        new StageFailedEvent(this, context.getRunId(), stage, attempt, finalAttempt, lastFailureReason));
                log.warn("Stage {} attempt {}/{} failed for run {}: {}", stage, attempt, maxAttempts,
                        context.getRunId(), lastFailureReason);

                Checkpoint lastCheckpoint = context.getLastCheckpoint();
                Stage restoredTo = lastCheckpoint == null ? null : lastCheckpoint.afterStage();
                context.restoreCheckpoint(lastCheckpoint);
                eventPublisher.publishEvent(new RollbackEvent(this, context.getRunId(), stage, restoredTo,
                        finalAttempt ? "max retries exceeded" : "retrying after attempt " + attempt + " failure"));

                if (!finalAttempt) {
                    backoff(attempt);
                }
            }
        }

        Instant endedAt = Instant.now();
        return new StageResult(stage, context.getRunId(), maxAttempts, startedAt, endedAt, false, null,
                lastFailureReason);
    }

    private void backoff(int attempt) {
        long delayMs = (long) (properties.retry().initialBackoffMs()
                * Math.pow(properties.retry().backoffMultiplier(), attempt - 1));
        try {
            Thread.sleep(delayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private String describe(Exception e) {
        return e.getClass().getSimpleName() + ": " + e.getMessage();
    }
}
