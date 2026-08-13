package com.schwab.assessment.orchestration;

import com.schwab.assessment.config.OrchestrationProperties;
import com.schwab.assessment.orchestration.model.GateState;
import com.schwab.assessment.orchestration.model.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Enforces human approval checkpoints before high-impact stages. ARCHITECTURE
 * requires design approval and RELEASE_READINESS requires deploy approval.
 * In demo mode ({@code orchestration.gates.auto-approve=true}) gates
 * self-approve immediately; otherwise the engine blocks until
 * {@code POST /orchestration/gates/{stageId}/approve} resolves the gate.
 *
 * <p>Because that endpoint is keyed only by stage (not by run), this class
 * resolves the pending gate for a stage against whichever run currently has
 * one outstanding -- the intended usage is one demo pipeline active at a
 * time, consistent with the rest of this assessment's scope.
 */
@Component
public class GateKeeper {

    private static final Logger log = LoggerFactory.getLogger(GateKeeper.class);
    private static final Set<Stage> GATED_STAGES = Set.of(Stage.ARCHITECTURE, Stage.RELEASE_READINESS);
    private static final Duration APPROVAL_TIMEOUT = Duration.ofMinutes(30);

    private final OrchestrationProperties properties;
    private final ConcurrentHashMap<String, CompletableFuture<GateDecision>> pendingGates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, GateState> gateStates = new ConcurrentHashMap<>();

    public GateKeeper(OrchestrationProperties properties) {
        this.properties = properties;
    }

    public boolean requiresGate(Stage stage) {
        return GATED_STAGES.contains(stage);
    }

    /**
     * Blocks the calling (stage-execution) thread until the gate for
     * {@code stage} on {@code runId} is resolved, either immediately via
     * auto-approve or by a later call to {@link #approve} / {@link #reject}.
     */
    public GateDecision waitForApproval(UUID runId, Stage stage) {
        String key = gateKey(runId, stage);

        if (properties.gates().autoApprove()) {
            GateDecision decision = new GateDecision(GateState.APPROVED, "auto-approved (demo mode)", Instant.now());
            gateStates.put(key, decision.state());
            log.info("Gate {} auto-approved for run {}", stage, runId);
            return decision;
        }

        gateStates.put(key, GateState.PENDING);
        CompletableFuture<GateDecision> future = pendingGates.computeIfAbsent(key, k -> new CompletableFuture<>());
        log.info("Gate {} for run {} is PENDING human approval", stage, runId);
        try {
            return future.get(APPROVAL_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            gateStates.put(key, GateState.REJECTED);
            return new GateDecision(GateState.REJECTED,
                    "approval timed out after " + APPROVAL_TIMEOUT.toMinutes() + " minutes", Instant.now());
        } catch (InterruptedException | ExecutionException e) {
            Thread.currentThread().interrupt();
            return new GateDecision(GateState.REJECTED, "approval wait interrupted", Instant.now());
        } finally {
            pendingGates.remove(key);
        }
    }

    public boolean approve(UUID runId, Stage stage, String approver, String reason) {
        return resolve(runId, stage, GateState.APPROVED, approver, reason);
    }

    public boolean reject(UUID runId, Stage stage, String approver, String reason) {
        return resolve(runId, stage, GateState.REJECTED, approver, reason);
    }

    /**
     * Finds the run currently awaiting human approval for {@code stage}, if any.
     */
    public Optional<UUID> findPendingRunFor(Stage stage) {
        String suffix = ":" + stage;
        return pendingGates.keySet().stream()
                .filter(key -> key.endsWith(suffix))
                .findFirst()
                .map(key -> UUID.fromString(key.substring(0, key.indexOf(':'))));
    }

    public GateState getGateState(UUID runId, Stage stage) {
        return gateStates.getOrDefault(gateKey(runId, stage), GateState.PENDING);
    }

    private boolean resolve(UUID runId, Stage stage, GateState state, String approver, String reason) {
        String key = gateKey(runId, stage);
        CompletableFuture<GateDecision> future = pendingGates.get(key);
        if (future == null || future.isDone()) {
            return false;
        }
        gateStates.put(key, state);
        String narrative = state + " by " + approver + (reason == null || reason.isBlank() ? "" : ": " + reason);
        future.complete(new GateDecision(state, narrative, Instant.now()));
        log.info("Gate {} for run {} resolved: {}", stage, runId, narrative);
        return true;
    }

    private String gateKey(UUID runId, Stage stage) {
        return runId + ":" + stage;
    }

    /** Outcome of a gate's approval process. */
    public record GateDecision(GateState state, String reason, Instant decidedAt) {
        public boolean isApproved() {
            return state == GateState.APPROVED;
        }
    }
}
