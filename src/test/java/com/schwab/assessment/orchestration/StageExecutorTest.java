package com.schwab.assessment.orchestration;

import com.schwab.assessment.config.OrchestrationProperties;
import com.schwab.assessment.orchestration.model.Checkpoint;
import com.schwab.assessment.orchestration.model.PipelineContext;
import com.schwab.assessment.orchestration.model.Stage;
import com.schwab.assessment.orchestration.model.StageExecutionResult;
import com.schwab.assessment.orchestration.model.StageHandler;
import com.schwab.assessment.orchestration.model.StageResult;
import com.schwab.assessment.orchestration.model.StageState;
import com.schwab.assessment.orchestration.model.events.RollbackEvent;
import com.schwab.assessment.orchestration.model.events.StageCompletedEvent;
import com.schwab.assessment.orchestration.model.events.StageFailedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class StageExecutorTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    private StageExecutor executor;

    @BeforeEach
    void setUp() {
        // maxAttempts=3, ~0ms backoff so the test runs fast
        OrchestrationProperties properties = new OrchestrationProperties(
                new OrchestrationProperties.Gates(true),
                new OrchestrationProperties.Retry(3, 1L, 1.0));
        executor = new StageExecutor(properties, eventPublisher);
    }

    @Test
    void retriesAFailingStageAndSucceedsOnTheThirdAttempt() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        StageHandler flakyThenSucceeds = (context, stage, attempt) -> {
            if (calls.incrementAndGet() < 3) {
                throw new RuntimeException("simulated transient failure on attempt " + attempt);
            }
            return StageExecutionResult.success("built-artifact", "succeeded on retry");
        };

        PipelineContext context = contextWithHandler(Stage.IMPLEMENTATION, flakyThenSucceeds);

        StageResult result = executor.execute(context, Stage.IMPLEMENTATION);

        assertTrue(result.success());
        assertEquals(3, result.attemptCount());
        assertEquals(3, calls.get());
        assertEquals("built-artifact", result.output());
        verify(eventPublisher, times(1)).publishEvent(any(StageCompletedEvent.class));
        verify(eventPublisher, times(2)).publishEvent(any(StageFailedEvent.class));
    }

    @Test
    void rollsBackToLastCheckpointWhenRetriesAreExhausted() throws Exception {
        StageHandler alwaysFails = (context, stage, attempt) -> {
            throw new RuntimeException("simulated permanent failure");
        };

        PipelineContext context = contextWithHandler(Stage.IMPLEMENTATION, alwaysFails);

        StageResult result = executor.execute(context, Stage.IMPLEMENTATION);

        assertFalse(result.success());
        assertEquals(3, result.attemptCount());
        assertTrue(result.failureReason().contains("simulated permanent failure"));
        verify(eventPublisher, atLeastOnce()).publishEvent(any(RollbackEvent.class));
        verify(eventPublisher, times(3)).publishEvent(any(StageFailedEvent.class));
    }

    @Test
    void successOnFirstAttempt_publishesNoFailureEvents() throws Exception {
        StageHandler succeedsImmediately = (context, stage, attempt) ->
                StageExecutionResult.success("artifact", "succeeded on the first try");

        PipelineContext context = contextWithHandler(Stage.IMPLEMENTATION, succeedsImmediately);

        StageResult result = executor.execute(context, Stage.IMPLEMENTATION);

        assertTrue(result.success());
        assertEquals(1, result.attemptCount());
        verify(eventPublisher, times(1)).publishEvent(any(StageCompletedEvent.class));
        verify(eventPublisher, never()).publishEvent(any(StageFailedEvent.class));
    }

    @Test
    void exponentialBackoff_secondAttemptWaitsLongerThanFirst() throws Exception {
        // Distinct executor with a real (non-1ms) backoff, so the gap between
        // attempts is actually measurable -- the shared `executor` from
        // setUp() uses ~0ms backoff deliberately to keep every other test fast.
        OrchestrationProperties slowBackoffProperties = new OrchestrationProperties(
                new OrchestrationProperties.Gates(true),
                new OrchestrationProperties.Retry(3, 100L, 2.0));
        StageExecutor slowExecutor = new StageExecutor(slowBackoffProperties, eventPublisher);

        List<Long> attemptTimestamps = new ArrayList<>();
        StageHandler failsOnceThenSucceeds = (context, stage, attempt) -> {
            attemptTimestamps.add(System.currentTimeMillis());
            if (attempt < 2) {
                throw new RuntimeException("fail attempt 1 to force a backoff wait");
            }
            return StageExecutionResult.success("artifact", "succeeded on attempt " + attempt);
        };

        PipelineContext context = contextWithHandler(Stage.IMPLEMENTATION, failsOnceThenSucceeds);
        slowExecutor.execute(context, Stage.IMPLEMENTATION);

        assertEquals(2, attemptTimestamps.size());
        long gapMs = attemptTimestamps.get(1) - attemptTimestamps.get(0);
        // Tolerant bound (base delay is 100ms) to avoid flakiness on slow CI runners.
        assertTrue(gapMs >= 80, "expected backoff gap >= 80ms between attempt 1 and attempt 2, was " + gapMs);
    }

    @Test
    void maxRetriesExceeded_publishesFailedEventPerAttemptAndReturnsFailedResult() throws Exception {
        // Same failure path as rollsBackToLastCheckpointWhenRetriesAreExhausted
        // above; kept as its own test because it asserts the exact per-attempt
        // StageFailedEvent count explicitly, which that test only checks via atLeastOnce() for rollback.
        StageHandler alwaysFails = (context, stage, attempt) -> {
            throw new RuntimeException("simulated permanent failure");
        };

        PipelineContext context = contextWithHandler(Stage.IMPLEMENTATION, alwaysFails);

        StageResult result = executor.execute(context, Stage.IMPLEMENTATION);

        assertFalse(result.success());
        assertEquals(3, result.attemptCount());
        verify(eventPublisher, times(3)).publishEvent(any(StageFailedEvent.class));
    }

    @Test
    void checkpointRestored_betweenFailedAttempts() throws Exception {
        AtomicReference<Object> implementationArtifactSeenOnAttempt2 = new AtomicReference<>();
        StageHandler failsOnceThenObservesRestoredState = (context, stage, attempt) -> {
            if (attempt == 1) {
                throw new RuntimeException("attempt 1 fails after leaving partial state behind");
            }
            implementationArtifactSeenOnAttempt2.set(context.getArtifact(Stage.IMPLEMENTATION));
            return StageExecutionResult.success("artifact-v2", "succeeded on attempt 2");
        };

        PipelineContext context = contextWithHandler(Stage.IMPLEMENTATION, failsOnceThenObservesRestoredState);

        // A stable checkpoint taken after REQUIREMENTS completed -- this is what
        // the executor should roll back to once attempt 1 fails. (An artifact
        // must be set before snapshotting: PipelineContext.snapshotArtifacts()
        // copies via `new EnumMap<>(artifacts)`, and EnumMap's copy constructor
        // throws IllegalArgumentException on an empty source map.)
        context.setArtifact(Stage.REQUIREMENTS, "artifact-v1");
        context.setState(Stage.REQUIREMENTS, StageState.COMPLETED);
        context.pushCheckpoint(Checkpoint.capture(Stage.REQUIREMENTS, context.snapshotStates(), context.snapshotArtifacts()));

        // Simulate partial work a handler left behind before throwing -- exactly
        // what restoreCheckpoint() between attempts is meant to undo.
        context.setArtifact(Stage.IMPLEMENTATION, "stray-partial-write-from-attempt-1");

        executor.execute(context, Stage.IMPLEMENTATION);

        assertNull(implementationArtifactSeenOnAttempt2.get(),
                "checkpoint restore should have cleared the stray write before attempt 2 ran");
    }

    @Test
    void stageCompletedEvent_containsCorrectStageAndRunId() throws Exception {
        StageHandler handler = (context, stage, attempt) -> StageExecutionResult.success("artifact", "ok");
        PipelineContext context = contextWithHandler(Stage.TESTING, handler);

        executor.execute(context, Stage.TESTING);

        ArgumentCaptor<StageCompletedEvent> captor = ArgumentCaptor.forClass(StageCompletedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        StageResult publishedResult = captor.getValue().getResult();

        assertEquals(Stage.TESTING, publishedResult.stage());
        assertNotNull(publishedResult.runId());
        assertEquals(context.getRunId(), publishedResult.runId());
    }

    private PipelineContext contextWithHandler(Stage stage, StageHandler handler) {
        Map<Stage, StageHandler> handlers = new EnumMap<>(Stage.class);
        handlers.put(stage, handler);
        return new PipelineContext(UUID.randomUUID(), "test", "test requirement",
                mock(ContextStore.class), handlers);
    }
}
