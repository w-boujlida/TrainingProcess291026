CREATE TABLE audit_event (
    id BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    actor_id BIGINT,
    username VARCHAR(50),
    actor_role VARCHAR(30),
    action VARCHAR(80) NOT NULL,
    resource VARCHAR(180) NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    http_status INTEGER NOT NULL,
    ip_address VARCHAR(64) NOT NULL,
    peer_address VARCHAR(64) NOT NULL,
    ip_source VARCHAR(20) NOT NULL,
    user_agent VARCHAR(512),
    device_name VARCHAR(100),
    affected_count INTEGER,
    rejected_count INTEGER
);

CREATE INDEX ix_audit_event_occurred ON audit_event (occurred_at DESC, id DESC);
CREATE INDEX ix_audit_event_actor ON audit_event (username, occurred_at DESC);
