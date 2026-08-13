package com.schwab.assessment.api;

import com.schwab.assessment.orchestration.ContextStore;
import com.schwab.assessment.orchestration.GateKeeper;
import com.schwab.assessment.orchestration.OrchestrationEngine;
import com.schwab.assessment.orchestration.OrchestrationRunLogRepository;
import com.schwab.assessment.orchestration.model.OrchestrationRunLogEntity;
import com.schwab.assessment.orchestration.model.PipelineStatus;
import com.schwab.assessment.orchestration.model.Stage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Exposes the orchestration engine's runtime state and the two operator
 * touch-points into it: resolving a pending human-approval gate, and
 * reading back the full decision/ambiguity lineage for a run.
 */
@RestController
@Tag(name = "Orchestration", description = "Pipeline status, gate approvals, and decision lineage")
public class OrchestrationController {

    private final OrchestrationEngine engine;
    private final GateKeeper gateKeeper;
    private final ContextStore contextStore;
    private final OrchestrationRunLogRepository runLogRepository;

    public OrchestrationController(OrchestrationEngine engine, GateKeeper gateKeeper, ContextStore contextStore,
                                    OrchestrationRunLogRepository runLogRepository) {
        this.engine = engine;
        this.gateKeeper = gateKeeper;
        this.contextStore = contextStore;
        this.runLogRepository = runLogRepository;
    }

    @Operation(summary = "Get pipeline status",
            description = "Returns the current (or, with ?runId=, a specific) pipeline run's stage-by-stage state.")
    @GetMapping("/orchestration/status")
    public ApiResponse<PipelineStatus> getStatus(@RequestParam(required = false) UUID runId) {
        PipelineStatus status = runId != null ? engine.getStatus(runId) : engine.getCurrentStatus();
        return ApiResponse.ok(status);
    }

    @Operation(summary = "Resolve a human approval gate",
            description = "Approves (default) or rejects the pending ARCHITECTURE or RELEASE_READINESS gate "
                    + "for whichever run is currently awaiting approval on that stage. No-op when "
                    + "orchestration.gates.auto-approve=true, since gates never become PENDING in that mode.")
    @PostMapping("/orchestration/gates/{stageId}/approve")
    public ApiResponse<GateResolutionResponse> resolveGate(@PathVariable String stageId,
                                                             @RequestBody(required = false) GateDecisionRequest body) {
        Stage stage = parseStage(stageId);
        GateDecisionRequest request = body != null ? body : new GateDecisionRequest(true, null, null);

        UUID runId = gateKeeper.findPendingRunFor(stage)
                .orElseThrow(() -> new NoSuchElementException("No pending gate for stage " + stage
                        + " (nothing is awaiting approval, or auto-approve is enabled)"));

        boolean resolved = request.isApproved()
                ? gateKeeper.approve(runId, stage, request.approverOrDefault(), request.reasonOrDefault())
                : gateKeeper.reject(runId, stage, request.approverOrDefault(), request.reasonOrDefault());

        return ApiResponse.ok(new GateResolutionResponse(runId, stage, request.isApproved(), resolved));
    }

    @Operation(summary = "Get the full decision log",
            description = "Every architectural/engineering decision recorded across all runs, plus ambiguity "
                    + "resolutions for the current run.")
    @GetMapping("/orchestration/decisions")
    public ApiResponse<DecisionLogResponse> getDecisions() {
        return ApiResponse.ok(new DecisionLogResponse(contextStore.getDecisionLog(), contextStore.getAmbiguityLog()));
    }

    @Operation(summary = "Get run history", description = "The last 10 pipeline runs, most recent first.")
    @GetMapping("/orchestration/runs")
    public ApiResponse<List<RunLogEntry>> getRuns() {
        return ApiResponse.ok(runLogRepository.findTop10ByOrderByStartedAtDesc().stream()
                .map(RunLogEntry::from)
                .toList());
    }

    private Stage parseStage(String stageId) {
        try {
            return Stage.valueOf(stageId.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown stage: " + stageId + ". Valid stages: "
                    + List.of(Stage.values()));
        }
    }

    public record GateResolutionResponse(UUID runId, Stage stage, boolean approved, boolean resolved) {
    }

    /** Lean run-history row -- full metrics are available via /orchestration/decisions if needed. */
    public record RunLogEntry(UUID runId, String scenarioType, Instant startedAt, Instant completedAt,
                               Boolean success) {
        static RunLogEntry from(OrchestrationRunLogEntity entity) {
            return new RunLogEntry(entity.getRunId(), entity.getScenarioType(), entity.getStartedAt(),
                    entity.getCompletedAt(), entity.getSuccess());
        }
    }
}
