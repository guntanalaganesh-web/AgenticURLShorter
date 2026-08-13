CREATE TABLE click_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    short_code      VARCHAR(8) NOT NULL,
    clicked_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    ip_hash         VARCHAR(64) NOT NULL,
    referrer        TEXT,
    user_agent_hash VARCHAR(64),
    country_code    VARCHAR(2)
);

CREATE INDEX idx_click_events_short_code ON click_events (short_code);
CREATE INDEX idx_click_events_short_code_clicked_at ON click_events (short_code, clicked_at);
