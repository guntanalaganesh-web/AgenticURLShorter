package com.schwab.assessment.orchestration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.schwab.assessment.orchestration.model.OrchestrationRunLogEntity;
import com.schwab.assessment.orchestration.model.PipelineContext;
import com.schwab.assessment.orchestration.model.Stage;
import com.schwab.assessment.orchestration.model.StageResult;
import com.schwab.assessment.orchestration.model.events.RollbackEvent;
import com.schwab.assessment.orchestration.model.events.SafeStopEvent;
import com.schwab.assessment.orchestration.model.events.StageCompletedEvent;
import com.schwab.assessment.orchestration.model.events.StageFailedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks reliability metrics for the orchestration engine -- per-stage
 * success rate, retry frequency, mean-time-to-recovery, rollback count, and
 * end-to-end pipeline latency -- exposing them via Micrometer
 * ({@code /actuator/metrics}) and persisting a per-run summary to the
 * {@code orchestration_run_log} table when a run concludes.
 */
@Component
public class ObservabilityCollector {

    private static final Logger log = LoggerFactory.getLogger(ObservabilityCollector.class);

    private final MeterRegistry meterRegistry;
    private final OrchestrationRunLogRepository runLogRepository;
    private final ObjectMapper objectMapper;
    private final Map<UUID, RunStats> runStatsByRunId = new ConcurrentHashMap<>();

    public ObservabilityCollector(MeterRegistry meterRegistry, OrchestrationRunLogRepository runLogRepository,
                                   ObjectMapper objectMapper) {
        this.meterRegistry = meterRegistry;
        this.runLogRepository = runLogRepository;
        this.objectMapper = objectMapper;
    }

    @EventListener
    public void onStageCompleted(StageCompletedEvent event) {
        StageResult result = event.getResult();
        RunStats stats = runStatsByRunId.computeIfAbsent(result.runId(), r -> new RunStats());
        stats.attempts.merge(result.stage(), 1, Integer::sum);
        stats.completions.merge(result.stage(), 1, Integer::sum);

        meterRegistry.counter("orchestration.stage.attempts", "stage", result.stage().name()).increment();
        meterRegistry.counter("orchestration.stage.completed", "stage", result.stage().name()).increment();

        Instant firstFailure = stats.firstFailureAt.remove(result.stage());
        if (firstFailure != null) {
            Duration mttr = Duration.between(firstFailure, Instant.now());
            stats.mttrMillis.put(result.stage(), mttr.toMillis());
            meterRegistry.timer("orchestration.stage.mttr", "stage", result.stage().name()).record(mttr);
        }
    }

    @EventListener
    public void onStageFailed(StageFailedEvent event) {
        RunStats stats = runStatsByRunId.computeIfAbsent(event.getRunId(), r -> new RunStats());
        stats.attempts.merge(event.getStage(), 1, Integer::sum);
        stats.firstFailureAt.putIfAbsent(event.getStage(), Instant.now());

        meterRegistry.counter("orchestration.stage.attempts", "stage", event.getStage().name()).increment();
        meterRegistry.counter("orchestration.stage.failures", "stage", event.getStage().name()).increment();

        if (!event.isFinalFailure()) {
            stats.retries.merge(event.getStage(), 1, Integer::sum);
            meterRegistry.counter("orchestration.stage.retries", "stage", event.getStage().name()).increment();
        }
    }

    @EventListener
    public void onRollback(RollbackEvent event) {
        RunStats stats = runStatsByRunId.computeIfAbsent(event.getRunId(), r -> new RunStats());
        stats.rollbackCount++;
        meterRegistry.counter("orchestration.rollback.count", "stage", event.getFailedStage().name()).increment();
    }

    @EventListener
    public void onSafeStop(SafeStopEvent event) {
        meterRegistry.counter("orchestration.pipeline.safestop",
                "stage", event.getHaltedAtStage() == null ? "unknown" : event.getHaltedAtStage().name()).increment();
        log.warn("Pipeline {} issued SafeStop at stage {}: {}", event.getRunId(), event.getHaltedAtStage(),
                event.getReason());
    }

    /**
     * Called by {@link com.schwab.assessment.orchestration.OrchestrationEngine}
     * once per completed stage attempt, in addition to the event-driven
     * counters above, to surface a structured line for operational visibility.
     */
    public void record(StageResult result) {
        log.info("stage={} run={} attempts={} success={} durationMs={}", result.stage(), result.runId(),
                result.attemptCount(), result.success(), result.durationMillis());
    }

    public void recordPipelineStart(PipelineContext context) {
        runStatsByRunId.putIfAbsent(context.getRunId(), new RunStats());
        log.info("Pipeline run {} ({}) started", context.getRunId(), context.getScenarioType());
    }

    /**
     * Finalizes metrics for a run: records end-to-end latency and a
     * completion counter to Micrometer, then persists a JSON summary of
     * every tracked metric to {@code orchestration_run_log}.
     */
    @Transactional
    public void recordPipelineEnd(PipelineContext context) {
        RunStats stats = runStatsByRunId.getOrDefault(context.getRunId(), new RunStats());
        boolean success = !context.isHalted();

        Duration latency = (context.getStartedAt() != null && context.getEndedAt() != null)
                ? Duration.between(context.getStartedAt(), context.getEndedAt())
                : Duration.ZERO;

        meterRegistry.timer("orchestration.pipeline.latency", "scenario", context.getScenarioType()).record(latency);
        meterRegistry.counter("orchestration.pipeline.completed",
                "scenario", context.getScenarioType(), "success", String.valueOf(success)).increment();

        Map<String, Object> summary = buildMetricsSummary(stats, latency, success);
        try {
            String metricsJson = objectMapper.writeValueAsString(summary);
            runLogRepository.save(new OrchestrationRunLogEntity(context.getRunId(), context.getScenarioType(),
                    context.getStartedAt(), context.getEndedAt(), success, metricsJson));
        } catch (Exception e) {
            log.error("Failed to persist run log for run {}", context.getRunId(), e);
        }

        runStatsByRunId.remove(context.getRunId());
        log.info("Pipeline run {} ({}) ended success={} latencyMs={}", context.getRunId(),
                context.getScenarioType(), success, latency.toMillis());
    }

    private Map<String, Object> buildMetricsSummary(RunStats stats, Duration latency, boolean success) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("success", success);
        summary.put("latencyMs", latency.toMillis());
        summary.put("rollbackCount", stats.rollbackCount);

        Map<String, Object> perStage = new LinkedHashMap<>();
        for (Stage stage : Stage.values()) {
            int attempts = stats.attempts.getOrDefault(stage, 0);
            int completions = stats.completions.getOrDefault(stage, 0);
            if (attempts == 0) {
                continue;
            }
            Map<String, Object> stageSummary = new LinkedHashMap<>();
            stageSummary.put("attempts", attempts);
            stageSummary.put("completions", completions);
            stageSummary.put("retries", stats.retries.getOrDefault(stage, 0));
            stageSummary.put("successRate", (double) completions / attempts);
            if (stats.mttrMillis.containsKey(stage)) {
                stageSummary.put("mttrMs", stats.mttrMillis.get(stage));
            }
            perStage.put(stage.name(), stageSummary);
        }
        summary.put("stages", perStage);
        return summary;
    }

    private static final class RunStats {
        final Map<Stage, Integer> attempts = new EnumMap<>(Stage.class);
        final Map<Stage, Integer> completions = new EnumMap<>(Stage.class);
        final Map<Stage, Integer> retries = new EnumMap<>(Stage.class);
        final Map<Stage, Instant> firstFailureAt = new EnumMap<>(Stage.class);
        final Map<Stage, Long> mttrMillis = new EnumMap<>(Stage.class);
        int rollbackCount = 0;
    }
}
