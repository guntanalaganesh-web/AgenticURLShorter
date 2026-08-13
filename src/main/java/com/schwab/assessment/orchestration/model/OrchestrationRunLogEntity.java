package com.schwab.assessment.orchestration.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping for the {@code orchestration_run_log} table: a one-row-per-run
 * summary written by {@link com.schwab.assessment.orchestration.ObservabilityCollector}
 * when a pipeline run concludes, successfully or otherwise.
 */
@Entity
@Table(name = "orchestration_run_log")
public class OrchestrationRunLogEntity {

    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false, unique = true)
    private UUID runId;

    @Column(name = "scenario_type", nullable = false, length = 32)
    private String scenarioType;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "success")
    private Boolean success;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics_json", columnDefinition = "jsonb")
    private String metricsJson;

    protected OrchestrationRunLogEntity() {
        // required by JPA
    }

    public OrchestrationRunLogEntity(UUID runId, String scenarioType, Instant startedAt, Instant completedAt,
                                      Boolean success, String metricsJson) {
        this.id = UUID.randomUUID();
        this.runId = runId;
        this.scenarioType = scenarioType;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
        this.success = success;
        this.metricsJson = metricsJson;
    }

    public UUID getId() {
        return id;
    }

    public UUID getRunId() {
        return runId;
    }

    public String getScenarioType() {
        return scenarioType;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Boolean getSuccess() {
        return success;
    }

    public String getMetricsJson() {
        return metricsJson;
    }
}
