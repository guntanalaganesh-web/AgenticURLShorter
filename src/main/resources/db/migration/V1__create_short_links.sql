CREATE TABLE short_links (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    original_url  TEXT NOT NULL,
    short_code    VARCHAR(8) NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    expires_at    TIMESTAMP WITH TIME ZONE,
    created_by    VARCHAR(128) NOT NULL,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT uq_short_links_short_code UNIQUE (short_code)
);

CREATE INDEX idx_short_links_short_code_active ON short_links (short_code) WHERE is_active = TRUE;
CREATE INDEX idx_short_links_created_by ON short_links (created_by);
