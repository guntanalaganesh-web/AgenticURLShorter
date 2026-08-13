package com.schwab.assessment.orchestration.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping for the {@code orchestration_context} table: the durable
 * store behind {@link com.schwab.assessment.orchestration.ContextStore}.
 * Each row is one piece of cross-stage context -- a decision, an
 * ambiguity resolution, or a stage output artifact -- keyed by run, stage,
 * and a logical key, with the payload held as JSONB.
 */
@Entity
@Table(name = "orchestration_context")
public class OrchestrationContextEntity {

    @Id
    private UUID id;

    @Column(name = "run_id", nullable = false)
    private UUID runId;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 64)
    private Stage stage;

    @Column(name = "key", nullable = false, length = 128)
    private String key;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "value", nullable = false, columnDefinition = "jsonb")
    private String value;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrchestrationContextEntity() {
        // required by JPA
    }

    public OrchestrationContextEntity(UUID runId, Stage stage, String key, String value) {
        this.id = UUID.randomUUID();
        this.runId = runId;
        this.stage = stage;
        this.key = key;
        this.value = value;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getRunId() {
        return runId;
    }

    public Stage getStage() {
        return stage;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
