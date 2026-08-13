CREATE TABLE orchestration_run_log (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id        UUID NOT NULL,
    scenario_type VARCHAR(32) NOT NULL,
    started_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at  TIMESTAMP WITH TIME ZONE,
    success       BOOLEAN,
    metrics_json  JSONB,
    CONSTRAINT uq_orchestration_run_log_run_id UNIQUE (run_id)
);

CREATE INDEX idx_orchestration_run_log_scenario_type ON orchestration_run_log (scenario_type);
