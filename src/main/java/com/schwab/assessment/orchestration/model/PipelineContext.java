package com.schwab.assessment.orchestration.model;

import com.schwab.assessment.orchestration.ContextStore;
import com.schwab.assessment.orchestration.DependencyGraph;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mutable, thread-safe state for a single pipeline run: stage states,
 * produced artifacts, checkpoints, and the handlers that do each stage's
 * actual work. Shared by the {@link com.schwab.assessment.orchestration.OrchestrationEngine},
 * its collaborators, and the {@link StageHandler} implementations supplied
 * by scenario components.
 */
public class PipelineContext {

    private final UUID runId;
    private final String scenarioType;
    private final String requirement;
    private final ContextStore contextStore;

    private final Map<Stage, StageHandler> handlers;
    private final Map<Stage, StageState> stageStates = new ConcurrentHashMap<>();
    private final Map<Stage, Object> artifacts = new ConcurrentHashMap<>();
    private final Map<Stage, StageResult> stageResults = new ConcurrentHashMap<>();
    private final Map<Stage, List<Runnable>> postStageHooks = new ConcurrentHashMap<>();
    private final Map<String, Object> attributes = new ConcurrentHashMap<>();
    private final Deque<Checkpoint> checkpoints = new ArrayDeque<>();
    private final List<String> timeline = new CopyOnWriteArrayList<>();

    private volatile boolean halted = false;
    private volatile String haltReason;
    private volatile Instant startedAt;
    private volatile Instant endedAt;

    public PipelineContext(UUID runId, String scenarioType, String requirement,
                            ContextStore contextStore, Map<Stage, StageHandler> handlers) {
        this.runId = runId;
        this.scenarioType = scenarioType;
        this.requirement = requirement;
        this.contextStore = contextStore;
        this.handlers = new EnumMap<>(handlers);
        for (Stage stage : Stage.values()) {
            stageStates.put(stage, StageState.PENDING);
        }
    }

    public UUID getRunId() {
        return runId;
    }

    public String getScenarioType() {
        return scenarioType;
    }

    public String getRequirement() {
        return requirement;
    }

    public ContextStore getContextStore() {
        return contextStore;
    }

    public StageHandler getHandler(Stage stage) {
        StageHandler handler = handlers.get(stage);
        if (handler == null) {
            throw new IllegalStateException("No StageHandler registered for stage " + stage);
        }
        return handler;
    }

    public StageState getState(Stage stage) {
        return stageStates.get(stage);
    }

    public void setState(Stage stage, StageState state) {
        stageStates.put(stage, state);
        timeline.add(Instant.now() + " " + stage + " -> " + state);
    }

    public Map<Stage, StageState> snapshotStates() {
        return new EnumMap<>(stageStates);
    }

    public Object getArtifact(Stage stage) {
        return artifacts.get(stage);
    }

    @SuppressWarnings("unchecked")
    public <T> T getArtifact(Stage stage, Class<T> type) {
        return (T) artifacts.get(stage);
    }

    public void setArtifact(Stage stage, Object artifact) {
        artifacts.put(stage, artifact);
    }

    public Map<Stage, Object> snapshotArtifacts() {
        return new EnumMap<>(artifacts);
    }

    /**
     * Records the attempt count and timing of a stage's most recent
     * {@link StageExecutor} run, success or failure, for the dashboard to
     * render per-stage duration and retry count.
     */
    public void setStageResult(Stage stage, StageResult result) {
        stageResults.put(stage, result);
    }

    public Map<Stage, StageResult> snapshotStageResults() {
        return new EnumMap<>(stageResults);
    }

    public Object getAttribute(String key) {
        return attributes.get(key);
    }

    public void putAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    /**
     * Registers work to run exactly once, immediately before the given
     * stage's dependencies are considered satisfied for execution. Used by
     * scenarios to simulate mid-pipeline events (e.g. an architecture
     * revision) at a deterministic point in the run.
     */
    public void registerPostStageHook(Stage afterStage, Runnable hook) {
        postStageHooks.computeIfAbsent(afterStage, s -> new CopyOnWriteArrayList<>()).add(hook);
    }

    /**
     * Runs and clears any hooks registered against {@code stage}. Safe to
     * call even if no hooks were registered.
     */
    public void runPostStageHooks(Stage stage) {
        List<Runnable> hooks = postStageHooks.remove(stage);
        if (hooks != null) {
            hooks.forEach(Runnable::run);
        }
    }

    public synchronized void pushCheckpoint(Checkpoint checkpoint) {
        checkpoints.push(checkpoint);
    }

    public synchronized Checkpoint getLastCheckpoint() {
        return checkpoints.peek();
    }

    /**
     * Restores stage states and artifacts to a previously captured
     * checkpoint, discarding any progress made since. Used to undo a
     * stage's partial effects after a failed attempt or exhausted retries.
     */
    public synchronized void restoreCheckpoint(Checkpoint checkpoint) {
        if (checkpoint == null) {
            return;
        }
        stageStates.clear();
        stageStates.putAll(checkpoint.stageStates());
        artifacts.clear();
        artifacts.putAll(checkpoint.artifacts());
        timeline.add(Instant.now() + " ROLLBACK -> restored checkpoint after " + checkpoint.afterStage());
    }

    /**
     * Resets {@code changedStage}'s transitive dependents back to PENDING if
     * they had already completed, so the engine will re-execute them with
     * the changed upstream artifact. Returns the stages that were invalidated.
     */
    public List<Stage> invalidateDownstream(Stage changedStage, DependencyGraph graph) {
        List<Stage> invalidated = new ArrayList<>();
        for (Stage dependent : graph.getTransitiveDependents(changedStage)) {
            if (stageStates.get(dependent) == StageState.COMPLETED) {
                setState(dependent, StageState.PENDING);
                artifacts.remove(dependent);
                stageResults.remove(dependent);
                invalidated.add(dependent);
            }
        }
        return invalidated;
    }

    public void halt(String reason) {
        this.halted = true;
        this.haltReason = reason;
        timeline.add(Instant.now() + " HALT -> " + reason);
    }

    public boolean isHalted() {
        return halted;
    }

    public String getHaltReason() {
        return haltReason;
    }

    public void markStarted() {
        this.startedAt = Instant.now();
    }

    public void markEnded() {
        this.endedAt = Instant.now();
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public List<String> getTimeline() {
        return List.copyOf(timeline);
    }
}
