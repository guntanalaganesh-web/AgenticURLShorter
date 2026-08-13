package com.schwab.assessment.orchestration;

import com.schwab.assessment.orchestration.model.Checkpoint;
import com.schwab.assessment.orchestration.model.DecisionRecord;
import com.schwab.assessment.orchestration.model.PipelineContext;
import com.schwab.assessment.orchestration.model.PipelineStatus;
import com.schwab.assessment.orchestration.model.PolicyViolationException;
import com.schwab.assessment.orchestration.model.Stage;
import com.schwab.assessment.orchestration.model.StageResult;
import com.schwab.assessment.orchestration.model.StageState;
import com.schwab.assessment.orchestration.model.events.SafeStopEvent;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Coordinates the full SDLC pipeline: repeatedly asks the
 * {@link DependencyGraph} which stages are currently executable, runs that
 * batch in parallel, and for each stage enforces policy
 * ({@link PolicyGuardrail}), human approval ({@link GateKeeper}), retries
 * and rollback ({@link StageExecutor}), decision lineage
 * ({@link ContextStore}), and reliability metrics
 * ({@link ObservabilityCollector}) before allowing the run to progress.
 *
 * <p>Also supports dynamic re-planning: a scenario can register a hook (via
 * {@link PipelineContext#registerPostStageHook}) that revises an earlier
 * stage's artifact once a later stage completes; {@link #reviseStageOutput}
 * then invalidates every already-completed stage downstream of the revision
 * so the engine re-executes them against the corrected artifact.
 */
@Component
public class OrchestrationEngine {

    private static final Logger log = LoggerFactory.getLogger(OrchestrationEngine.class);

    private final DependencyGraph graph = DependencyGraph.standardSdlcGraph();
    private final StageExecutor executor;
    private final GateKeeper gateKeeper;
    private final PolicyGuardrail guardrail;
    private final ContextStore contextStore;
    private final ObservabilityCollector observability;
    private final ApplicationEventPublisher eventPublisher;

    private final Map<UUID, PipelineContext> runsByRunId = new ConcurrentHashMap<>();
    private volatile UUID currentRunId;

    private final AtomicInteger stageThreadCounter = new AtomicInteger();
    private final ExecutorService stagePool = Executors.newFixedThreadPool(4, runnable -> {
        Thread thread = new Thread(runnable, "orchestration-stage-" + stageThreadCounter.incrementAndGet());
        thread.setDaemon(true);
        return thread;
    });

    public OrchestrationEngine(StageExecutor executor, GateKeeper gateKeeper, PolicyGuardrail guardrail,
                                ContextStore contextStore, ObservabilityCollector observability,
                                ApplicationEventPublisher eventPublisher) {
        this.executor = executor;
        this.gateKeeper = gateKeeper;
        this.guardrail = guardrail;
        this.contextStore = contextStore;
        this.observability = observability;
        this.eventPublisher = eventPublisher;
    }

    public DependencyGraph getGraph() {
        return graph;
    }

    /**
     * Runs {@code context}'s pipeline to completion or to a safe stop.
     * Blocks the calling thread until the run concludes; scenario
     * components typically call this from an async-annotated method or a
     * dedicated request thread.
     */
    public PipelineStatus runPipeline(PipelineContext context) {
        runsByRunId.put(context.getRunId(), context);
        currentRunId = context.getRunId();

        context.markStarted();
        observability.recordPipelineStart(context);
        log.info("Starting pipeline run {} ({}) for requirement: {}", context.getRunId(),
                context.getScenarioType(), context.getRequirement());

        while (!context.isHalted() && !graph.isTerminal(context.snapshotStates())) {
            List<Stage> executable = graph.getExecutableStages(context.snapshotStates());
            if (executable.isEmpty()) {
                log.warn("No executable stages and pipeline not terminal for run {}; stopping to avoid deadlock",
                        context.getRunId());
                context.halt("No stage became executable; possible unmet dependency or handler gap");
                break;
            }

            List<CompletableFuture<Void>> futures = executable.stream()
                    .map(stage -> CompletableFuture.runAsync(() -> runStage(context, stage), stagePool))
                    .toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        }

        context.markEnded();
        observability.recordPipelineEnd(context);
        log.info("Pipeline run {} concluded. halted={} states={}", context.getRunId(), context.isHalted(),
                context.snapshotStates());
        return PipelineStatus.from(context);
    }

    private void runStage(PipelineContext context, Stage stage) {
        if (context.isHalted()) {
            return;
        }

        context.setState(stage, StageState.RUNNING);

        try {
            guardrail.validate(stage, context);
        } catch (PolicyViolationException e) {
            failAndHalt(context, stage, "Policy violation: " + e.getMessage());
            return;
        }

        if (gateKeeper.requiresGate(stage)) {
            GateKeeper.GateDecision decision = gateKeeper.waitForApproval(context.getRunId(), stage);
            contextStore.saveDecision(DecisionRecord.of(context.getRunId(), stage,
                    "Gate " + decision.state() + " for " + stage, decision.reason(), List.of()));
            if (!decision.isApproved()) {
                failAndHalt(context, stage, "Gate rejected: " + decision.reason());
                return;
            }
        }

        StageResult result = executor.execute(context, stage);
        observability.record(result);

        if (result.success()) {
            context.setState(stage, StageState.COMPLETED);
            contextStore.saveStageOutput(context.getRunId(), stage, result.output());
            context.pushCheckpoint(Checkpoint.capture(stage, context.snapshotStates(), context.snapshotArtifacts()));
            context.runPostStageHooks(stage);
        } else {
            failAndHalt(context, stage, "Exhausted retries: " + result.failureReason());
        }
    }

    private void failAndHalt(PipelineContext context, Stage stage, String reason) {
        context.setState(stage, StageState.FAILED);
        context.halt(reason);
        eventPublisher.publishEvent(new SafeStopEvent(this, context.getRunId(), stage, reason));
        log.error("Pipeline run {} halted at stage {}: {}", context.getRunId(), stage, reason);
    }

    /**
     * Revises the artifact for {@code revisedStage} mid-run and invalidates
     * every already-completed stage that transitively depends on it, so the
     * engine's main loop re-executes them against the corrected artifact.
     * Records the revision itself as a decision for audit purposes.
     */
    public List<Stage> reviseStageOutput(PipelineContext context, Stage revisedStage, Object newArtifact,
                                          String reason) {
        context.setArtifact(revisedStage, newArtifact);
        contextStore.saveStageOutput(context.getRunId(), revisedStage, newArtifact);

        List<Stage> invalidated = context.invalidateDownstream(revisedStage, graph);

        contextStore.saveDecision(DecisionRecord.of(context.getRunId(), revisedStage,
                "Revised " + revisedStage + " output mid-pipeline; invalidated " + invalidated,
                reason, invalidated.stream().map(Enum::name).toList()));
        log.info("Re-plan for run {}: {} revised, invalidated downstream {}", context.getRunId(), revisedStage,
                invalidated);
        return invalidated;
    }

    public PipelineStatus getStatus(UUID runId) {
        PipelineContext context = runsByRunId.get(runId);
        if (context == null) {
            throw new NoSuchElementException("No pipeline run found with id " + runId);
        }
        return PipelineStatus.from(context);
    }

    public PipelineStatus getCurrentStatus() {
        if (currentRunId == null) {
            throw new IllegalStateException("No pipeline run has been started yet");
        }
        return getStatus(currentRunId);
    }

    public PipelineContext getContext(UUID runId) {
        PipelineContext context = runsByRunId.get(runId);
        if (context == null) {
            throw new NoSuchElementException("No pipeline run found with id " + runId);
        }
        return context;
    }

    public UUID getCurrentRunId() {
        return currentRunId;
    }

    @PreDestroy
    public void shutdown() {
        stagePool.shutdown();
    }
}
