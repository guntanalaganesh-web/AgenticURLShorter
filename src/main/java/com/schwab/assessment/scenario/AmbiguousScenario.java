package com.schwab.assessment.scenario;

import com.schwab.assessment.orchestration.ContextStore;
import com.schwab.assessment.orchestration.OrchestrationEngine;
import com.schwab.assessment.orchestration.model.AmbiguityRecord;
import com.schwab.assessment.orchestration.model.Confidence;
import com.schwab.assessment.orchestration.model.DecisionRecord;
import com.schwab.assessment.orchestration.model.PipelineContext;
import com.schwab.assessment.orchestration.model.PipelineStatus;
import com.schwab.assessment.orchestration.model.Stage;
import com.schwab.assessment.orchestration.model.StageExecutionResult;
import com.schwab.assessment.orchestration.model.StageHandler;
import com.schwab.assessment.scenario.artifact.ArchitectureDocument;
import com.schwab.assessment.scenario.artifact.DocumentationArtifact;
import com.schwab.assessment.scenario.artifact.ImplementationSummary;
import com.schwab.assessment.scenario.artifact.ReleaseChecklist;
import com.schwab.assessment.scenario.artifact.RequirementsDocument;
import com.schwab.assessment.scenario.artifact.TaskPlan;
import com.schwab.assessment.scenario.artifact.TestReport;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs a pipeline for the deliberately vague requirement "Add rate
 * limiting." REQUIREMENTS surfaces and resolves three ambiguities (scope,
 * algorithm, limit) with recorded confidence and impact-if-wrong. Partway
 * through the run, a simulated capacity-planning update revises the
 * ARCHITECTURE stage's rate limit from 100 to 60 req/min; the engine
 * detects the change, invalidates the already-completed TASK_PLANNING
 * stage, and re-executes it against the corrected value.
 */
@Component
public class AmbiguousScenario {

    private static final String REQUIREMENT = "Add rate limiting";
    private static final String RATE_LIMIT_KEY = "Rate limit";

    private final OrchestrationEngine engine;
    private final ContextStore contextStore;

    public AmbiguousScenario(OrchestrationEngine engine, ContextStore contextStore) {
        this.engine = engine;
        this.contextStore = contextStore;
    }

    public PipelineStatus run() {
        UUID runId = UUID.randomUUID();
        PipelineContext context = new PipelineContext(runId, "ambiguous", REQUIREMENT, contextStore, buildHandlers());

        context.registerPostStageHook(Stage.TASK_PLANNING, () -> simulateArchitectureRevision(context));

        return engine.runPipeline(context);
    }

    private Map<Stage, StageHandler> buildHandlers() {
        Map<Stage, StageHandler> handlers = new EnumMap<>(Stage.class);
        handlers.put(Stage.REQUIREMENTS, this::requirements);
        handlers.put(Stage.ARCHITECTURE, this::architecture);
        handlers.put(Stage.TASK_PLANNING, this::taskPlanning);
        handlers.put(Stage.IMPLEMENTATION, this::implementation);
        handlers.put(Stage.TESTING, this::testing);
        handlers.put(Stage.DOCUMENTATION, this::documentation);
        handlers.put(Stage.RELEASE_READINESS, this::releaseReadiness);
        return handlers;
    }

    private StageExecutionResult requirements(PipelineContext context, Stage stage, int attempt) {
        UUID runId = context.getRunId();

        contextStore.saveAmbiguity(AmbiguityRecord.of(runId, stage,
                "What is the rate-limiting scope: per-user, per-IP, or global?",
                "Per-IP",
                Confidence.MEDIUM,
                "If per-user was intended, clients sharing a NAT/proxy would be unfairly throttled together; "
                        + "would require reworking the Redis key from client IP to an authenticated user identifier."));

        contextStore.saveAmbiguity(AmbiguityRecord.of(runId, stage,
                "Which algorithm: fixed window, sliding window, or token bucket?",
                "Sliding window (Redis ZSET)",
                Confidence.HIGH,
                "Fixed window allows up to 2x burst at window boundaries; token bucket needs an untuned burst-"
                        + "capacity parameter this requirement doesn't specify. Sliding window is the safer default."));

        contextStore.saveAmbiguity(AmbiguityRecord.of(runId, stage,
                "What is the numeric limit?",
                "100 requests/minute",
                Confidence.LOW,
                "No traffic baseline was provided; if real traffic is materially different, legitimate clients "
                        + "could be throttled. This is the assumption most likely to need revision."));

        contextStore.saveDecision(DecisionRecord.of(runId, stage,
                "Proceed with sliding window, per-IP, 100 req/min",
                "Sliding window avoids boundary bursts and needs no burst-capacity tuning; per-IP is stateless "
                        + "and needs no auth wiring; 100 req/min is a common industry-standard default for public "
                        + "APIs pending real traffic data.",
                List.of("Fixed window (rejected: boundary burst)",
                        "Token bucket (rejected: needs an untuned burst parameter)",
                        "Global limit (rejected: one abusive client degrades service for everyone)")));

        RequirementsDocument doc = new RequirementsDocument(
                List.of("Requests are throttled per client IP once they exceed the configured rate"),
                List.of("Sliding-window algorithm via Redis ZSET", "Default limit of 100 requests/minute"),
                List.of("scope: per-IP", "algorithm: sliding window", "limit: 100 req/min")
        );
        return StageExecutionResult.success(doc, "3 ambiguities detected and resolved with documented confidence");
    }

    private StageExecutionResult architecture(PipelineContext context, Stage stage, int attempt) {
        Map<String, String> rationale = new LinkedHashMap<>();
        rationale.put(RATE_LIMIT_KEY, "100 req/min");
        rationale.put("Algorithm", "Sliding window (Redis ZSET)");
        rationale.put("Scope", "Per-IP");

        ArchitectureDocument doc = new ArchitectureDocument(
                List.of("RateLimiterService", "RateLimitInterceptor"), List.of("ADR-002"), rationale);
        return StageExecutionResult.success(doc, "Rate limiter architecture defined at 100 req/min");
    }

    /**
     * Fired once, immediately after TASK_PLANNING first completes: simulates
     * a capacity-planning config update lowering the rate limit, then asks
     * the engine to revise ARCHITECTURE's output and invalidate whatever
     * already-completed stages depend on it.
     */
    private void simulateArchitectureRevision(PipelineContext context) {
        ArchitectureDocument current = context.getArtifact(Stage.ARCHITECTURE, ArchitectureDocument.class);
        Map<String, String> revisedRationale = new LinkedHashMap<>(current.techStackRationale());
        revisedRationale.put(RATE_LIMIT_KEY, "60 req/min (revised)");

        ArchitectureDocument revised = new ArchitectureDocument(current.components(), current.adrReferences(),
                revisedRationale);

        engine.reviseStageOutput(context, Stage.ARCHITECTURE, revised,
                "Simulated capacity-planning update: infra review found 100 req/min per-IP too permissive for "
                        + "current backend capacity; lowered to 60 req/min.");
    }

    private StageExecutionResult taskPlanning(PipelineContext context, Stage stage, int attempt) {
        ArchitectureDocument arch = context.getArtifact(Stage.ARCHITECTURE, ArchitectureDocument.class);
        String rateLimit = arch.techStackRationale().getOrDefault(RATE_LIMIT_KEY, "100 req/min");

        TaskPlan plan = new TaskPlan(List.of(
                new TaskPlan.PlannedTask("T1",
                        "Implement RateLimiterService sliding window via Redis ZSET, limit=" + rateLimit,
                        "3h", List.of()),
                new TaskPlan.PlannedTask("T2", "Implement RateLimitInterceptor returning 429 + Retry-After",
                        "1h", List.of("T1"))
        ));
        return StageExecutionResult.success(plan, "Task plan generated against rate limit: " + rateLimit);
    }

    private StageExecutionResult implementation(PipelineContext context, Stage stage, int attempt) {
        ArchitectureDocument arch = context.getArtifact(Stage.ARCHITECTURE, ArchitectureDocument.class);
        String rateLimit = arch.techStackRationale().getOrDefault(RATE_LIMIT_KEY, "100 req/min");

        ImplementationSummary summary = new ImplementationSummary(
                List.of("RateLimiterService", "RateLimitInterceptor", "RateLimiterProperties"), 10, 0, true);
        return StageExecutionResult.success(summary,
                "3 classes created, 10 tests written; rate limiter configured at " + rateLimit
                        + " per IP (sliding window)");
    }

    private StageExecutionResult testing(PipelineContext context, Stage stage, int attempt) {
        TestReport report = new TestReport(10, 0, 91.0);
        return StageExecutionResult.success(report, "10/10 tests passing, 91.0% coverage");
    }

    private StageExecutionResult documentation(PipelineContext context, Stage stage, int attempt) {
        DocumentationArtifact doc = new DocumentationArtifact(true, "/v3/api-docs", 2);
        return StageExecutionResult.success(doc, "OpenAPI spec updated with 429 response, 2 ADRs cross-referenced");
    }

    private StageExecutionResult releaseReadiness(PipelineContext context, Stage stage, int attempt) {
        ArchitectureDocument arch = context.getArtifact(Stage.ARCHITECTURE, ArchitectureDocument.class);
        String rateLimit = arch.techStackRationale().getOrDefault(RATE_LIMIT_KEY, "unknown");

        ReleaseChecklist checklist = new ReleaseChecklist(List.of(
                new ReleaseChecklist.ChecklistItem("Test coverage above 80%", true, "91.0%"),
                new ReleaseChecklist.ChecklistItem("OpenAPI spec published", true, "/v3/api-docs"),
                new ReleaseChecklist.ChecklistItem("Migrations reviewed", true, "0 migrations needed"),
                new ReleaseChecklist.ChecklistItem("Final rate limit matches latest architecture revision", true,
                        rateLimit)
        ));
        return StageExecutionResult.success(checklist, "Release checklist: 4/4 items passed, final limit "
                + rateLimit);
    }
}
