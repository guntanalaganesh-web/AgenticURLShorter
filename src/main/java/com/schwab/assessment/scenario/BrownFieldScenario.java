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
 * Runs a pipeline against the existing URL shortener codebase for "Add
 * Redis caching and Kafka analytics to the existing service." Performs an
 * impact-analysis pass before the pipeline starts, and injects one
 * deliberate failure into IMPLEMENTATION -- a Flyway migration version
 * collision -- to exercise {@link com.schwab.assessment.orchestration.StageExecutor}'s
 * retry-then-rollback-then-recover path for real, not as a description of it.
 */
@Component
public class BrownFieldScenario {

    private static final String REQUIREMENT = "Add Redis caching and Kafka analytics to the existing service";

    private final OrchestrationEngine engine;
    private final ContextStore contextStore;

    public BrownFieldScenario(OrchestrationEngine engine, ContextStore contextStore) {
        this.engine = engine;
        this.contextStore = contextStore;
    }

    public PipelineStatus run() {
        UUID runId = UUID.randomUUID();
        performImpactAnalysis(runId);

        PipelineContext context = new PipelineContext(runId, "brownfield", REQUIREMENT, contextStore, buildHandlers());
        return engine.runPipeline(context);
    }

    /**
     * Extra pre-stage, run before the pipeline itself: reasons about the
     * existing codebase to identify exactly what the requirement touches.
     * Recorded as a decision rather than a stage output, since it precedes
     * REQUIREMENTS and isn't itself a graph stage.
     */
    private void performImpactAnalysis(UUID runId) {
        List<String> impactedClasses = List.of(
                "UrlService (add Redis cache-aside read/write path)",
                "ClickEventRepository (new aggregate queries for analytics)",
                "application.yml (add spring.data.redis and spring.kafka blocks)"
        );
        contextStore.saveDecision(DecisionRecord.of(runId, Stage.REQUIREMENTS,
                "Impact analysis: 3 files/classes touched, 3 new Flyway migrations required",
                "UrlService owns URL create/resolve and is where cache-aside reads must be added; "
                        + "ClickEventRepository needs new query methods for the analytics endpoint; "
                        + "application.yml needs Redis and Kafka connection blocks added. "
                        + "Impacted: " + String.join("; ", impactedClasses),
                List.of()));
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
                        "Redirect lookups must check Redis before falling back to PostgreSQL",
                        "Click events must be recorded via Kafka rather than a synchronous DB write"
                ),
                List.of(
                        "Existing UrlService behavior for unaffected paths (create, delete) must not regress",
                        "New Flyway migrations must not collide with version numbers already applied elsewhere"
                ),
                List.of()
        );
        return StageExecutionResult.success(doc, "2 functional and 2 non-functional requirements derived from "
                + "impact analysis");
    }

    private StageExecutionResult architecture(PipelineContext context, Stage stage, int attempt) {
        ArchitectureDocument doc = new ArchitectureDocument(
                List.of("UrlService (modified)", "ClickEventRepository (modified)", "RedisCacheConfig (new)",
                        "KafkaTopicConfig (new)"),
                List.of("ADR-001", "ADR-003"),
                Map.of("Redis 7", "Cache-aside per ADR-001, additive to the existing UrlService",
                        "Kafka 3.6", "Async click recording per ADR-003, additive via a new consumer")
        );
        return StageExecutionResult.success(doc, "Additive architecture: 2 existing components modified, "
                + "2 new components introduced");
    }

    private StageExecutionResult taskPlanning(PipelineContext context, Stage stage, int attempt) {
        TaskPlan plan = new TaskPlan(List.of(
                new TaskPlan.PlannedTask("T1", "Add Flyway migrations for cache/analytics-related schema changes",
                        "1h", List.of()),
                new TaskPlan.PlannedTask("T2", "Add Redis cache-aside reads/writes to UrlService", "3h",
                        List.of("T1")),
                new TaskPlan.PlannedTask("T3", "Add Kafka consumer and analytics queries", "3h", List.of("T1"))
        ));
        return StageExecutionResult.success(plan, "3 tasks planned as additive changes to the existing service");
    }

    private StageExecutionResult implementation(PipelineContext context, Stage stage, int attempt) throws Exception {
        if (attempt == 1) {
            throw new MigrationVersionCollisionException(
                    "New migration V5__add_analytics_indexes.sql collides with a V5 migration already applied "
                            + "to the production database on a diverged branch");
        }

        contextStore.saveDecision(DecisionRecord.of(context.getRunId(), stage,
                "Renumber the colliding migration from V5 to V6",
                "Attempt 1 failed: V5 was already taken by a migration applied to production after this "
                        + "branch diverged. The engine rolled back to the last stable checkpoint and retried "
                        + "with the next available version.",
                List.of("Force-overwrite the existing V5 (rejected: unsafe against an already-applied migration)",
                        "Skip the analytics migration entirely (rejected: blocks the requirement)")));

        ImplementationSummary summary = new ImplementationSummary(
                List.of("RedisCacheConfig", "KafkaTopicConfig", "UrlService", "AnalyticsService"), 18, 3, true);
        return StageExecutionResult.success(summary,
                "Recovered after migration renumbering; 4 classes touched, 18 tests added, 3 migrations reviewed");
    }

    private StageExecutionResult testing(PipelineContext context, Stage stage, int attempt) {
        TestReport report = new TestReport(18, 0, 84.0);
        return StageExecutionResult.success(report, "18/18 new tests passing, 84.0% coverage on touched code");
    }

    private StageExecutionResult documentation(PipelineContext context, Stage stage, int attempt) {
        DocumentationArtifact doc = new DocumentationArtifact(true, "/v3/api-docs", 1);
        return StageExecutionResult.success(doc, "OpenAPI spec regenerated to reflect no contract changes; "
                + "1 ADR cross-referenced");
    }

    private StageExecutionResult releaseReadiness(PipelineContext context, Stage stage, int attempt) {
        ReleaseChecklist checklist = new ReleaseChecklist(List.of(
                new ReleaseChecklist.ChecklistItem("Test coverage above 80%", true, "84.0%"),
                new ReleaseChecklist.ChecklistItem("OpenAPI spec published", true, "/v3/api-docs"),
                new ReleaseChecklist.ChecklistItem("Migrations reviewed", true,
                        "3/3 reviewed, collision resolved (V5 -> V6)"),
                new ReleaseChecklist.ChecklistItem("Backward compatibility preserved", true,
                        "create/delete paths unchanged")
        ));
        return StageExecutionResult.success(checklist, "Release checklist: 4/4 items passed");
    }

    /** Simulates a migration version collision discovered on the first implementation attempt. */
    static final class MigrationVersionCollisionException extends RuntimeException {
        MigrationVersionCollisionException(String message) {
            super(message);
        }
    }
}
