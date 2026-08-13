package com.schwab.assessment.orchestration;

import com.schwab.assessment.config.OrchestrationProperties;
import com.schwab.assessment.orchestration.model.PipelineContext;
import com.schwab.assessment.orchestration.model.Stage;
import com.schwab.assessment.orchestration.model.StageExecutionResult;
import com.schwab.assessment.orchestration.model.StageHandler;
import com.schwab.assessment.orchestration.model.StageResult;
import com.schwab.assessment.orchestration.model.events.RollbackEvent;
import com.schwab.assessment.orchestration.model.events.StageCompletedEvent;
import com.schwab.assessment.orchestration.model.events.StageFailedEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
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

    private PipelineContext contextWithHandler(Stage stage, StageHandler handler) {
        Map<Stage, StageHandler> handlers = new EnumMap<>(Stage.class);
        handlers.put(stage, handler);
        return new PipelineContext(UUID.randomUUID(), "test", "test requirement",
                mock(ContextStore.class), handlers);
    }
}
