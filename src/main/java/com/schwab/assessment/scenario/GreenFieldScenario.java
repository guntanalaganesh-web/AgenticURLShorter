package com.schwab.assessment.scenario;

import com.schwab.assessment.orchestration.ContextStore;
import com.schwab.assessment.orchestration.OrchestrationEngine;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs a fresh pipeline for "Build URL shortener from scratch" through all
 * seven SDLC stages, gated at ARCHITECTURE and RELEASE_READINESS, with every
 * stage producing a real, structured artifact. In demo mode
 * ({@code orchestration.gates.auto-approve=true}) both gates self-approve.
 */
@Component
public class GreenFieldScenario {

    private static final String REQUIREMENT = "Build URL shortener from scratch";

    private final OrchestrationEngine engine;
    private final ContextStore contextStore;

    public GreenFieldScenario(OrchestrationEngine engine, ContextStore contextStore) {
        this.engine = engine;
        this.contextStore = contextStore;
    }

    public PipelineStatus run() {
        UUID runId = UUID.randomUUID();
        PipelineContext context = new PipelineContext(runId, "greenfield", REQUIREMENT, contextStore, buildHandlers());
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
        RequirementsDocument doc = new RequirementsDocument(
                List.of(
                        "Users can submit a long URL and receive an 8-character short code",
                        "Requesting a short code redirects to the original URL",
                        "Users can view click analytics for a short code",
                        "Users can soft-delete a short code"
                ),
                List.of(
                        "Redirect path favors low latency via Redis cache-aside reads",
                        "Sliding-window rate limiting at 100 requests/min per client IP",
                        "Click analytics are processed asynchronously via Kafka, off the redirect path",
                        "No raw client IP or user agent is ever persisted -- SHA-256 hashed only"
                ),
                List.of()
        );
        contextStore.saveDecision(DecisionRecord.of(context.getRunId(), stage,
                "Adopt Redis cache-aside for redirect lookups",
                "The redirect is the hottest path in the system; avoiding a DB round trip on cache hits "
                        + "is the highest-leverage latency win available.",
                List.of("Write-through cache", "No cache (DB only)", "In-process local cache")));
        return StageExecutionResult.success(doc, "Captured 4 functional and 4 non-functional requirements");
    }

    private StageExecutionResult architecture(PipelineContext context, Stage stage, int attempt) {
        ArchitectureDocument doc = new ArchitectureDocument(
                List.of("UrlController", "UrlService", "AnalyticsService", "RateLimiterService",
                        "KafkaPublisher", "OrchestrationEngine"),
                List.of("ADR-001", "ADR-002", "ADR-003"),
                Map.of(
                        "PostgreSQL 15", "Durable primary store; JSONB fits the orchestration context's "
                                + "variably-shaped decision and artifact payloads",
                        "Redis 7", "Sub-millisecond cache-aside reads and a ZSET-based sliding window "
                                + "for the rate limiter",
                        "Kafka 3.6", "Decouples click recording from the redirect's request path"
                )
        );
        return StageExecutionResult.success(doc, "3 ADRs referenced, 6 components identified");
    }

    private StageExecutionResult taskPlanning(PipelineContext context, Stage stage, int attempt) {
        TaskPlan plan = new TaskPlan(List.of(
                new TaskPlan.PlannedTask("T1", "Define Flyway migrations for short_links and click_events",
                        "2h", List.of()),
                new TaskPlan.PlannedTask("T2", "Implement UrlService create/resolve/delete with Redis cache-aside",
                        "4h", List.of("T1")),
                new TaskPlan.PlannedTask("T3", "Implement RateLimiterService sliding window via Redis ZSET",
                        "3h", List.of("T1")),
                new TaskPlan.PlannedTask("T4", "Implement AnalyticsService Kafka consumer and aggregate queries",
                        "3h", List.of("T1")),
                new TaskPlan.PlannedTask("T5", "Wire UrlController with OpenAPI annotations", "2h",
                        List.of("T2", "T3"))
        ));
        return StageExecutionResult.success(plan, "5 tasks planned across schema, cache, rate limiting, "
                + "analytics, and API");
    }

    private StageExecutionResult implementation(PipelineContext context, Stage stage, int attempt) {
        ImplementationSummary summary = new ImplementationSummary(
                List.of("UrlService", "AnalyticsService", "RateLimiterService", "KafkaPublisher", "UrlController"),
                42, 4, true);
        return StageExecutionResult.success(summary, "5 classes created, 42 tests written, 4 migrations reviewed");
    }

    private StageExecutionResult testing(PipelineContext context, Stage stage, int attempt) {
        TestReport report = new TestReport(40, 0, 87.5);
        return StageExecutionResult.success(report, "40/40 tests passing, 87.5% line coverage");
    }

    private StageExecutionResult documentation(PipelineContext context, Stage stage, int attempt) {
        DocumentationArtifact doc = new DocumentationArtifact(true, "/v3/api-docs", 3);
        return StageExecutionResult.success(doc, "OpenAPI spec published at /v3/api-docs, 3 ADRs written");
    }

    private StageExecutionResult releaseReadiness(PipelineContext context, Stage stage, int attempt) {
        ReleaseChecklist checklist = new ReleaseChecklist(List.of(
                new ReleaseChecklist.ChecklistItem("Test coverage above 80%", true, "87.5%"),
                new ReleaseChecklist.ChecklistItem("OpenAPI spec published", true, "/v3/api-docs"),
                new ReleaseChecklist.ChecklistItem("Migrations reviewed", true, "4/4 reviewed"),
                new ReleaseChecklist.ChecklistItem("Docker Compose healthchecks configured", true,
                        "postgres, redis, kafka, app")
        ));
        return StageExecutionResult.success(checklist, "Release checklist: 4/4 items passed");
    }
}
