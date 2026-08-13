CREATE TABLE orchestration_context (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id     UUID NOT NULL,
    stage      VARCHAR(64) NOT NULL,
    key        VARCHAR(128) NOT NULL,
    value      JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_orchestration_context_run_id ON orchestration_context (run_id);
CREATE INDEX idx_orchestration_context_run_id_stage ON orchestration_context (run_id, stage);
CREATE INDEX idx_orchestration_context_key ON orchestration_context (key);
