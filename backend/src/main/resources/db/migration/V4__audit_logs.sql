CREATE TABLE audit_logs (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT REFERENCES users (id),
    action         VARCHAR(100) NOT NULL,
    resource_type  VARCHAR(50),
    resource_id    BIGINT,
    details        JSONB,
    ip_address     VARCHAR(45),
    user_agent     TEXT,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_user ON audit_logs (user_id);
CREATE INDEX idx_audit_resource ON audit_logs (resource_type, resource_id);
CREATE INDEX idx_audit_date ON audit_logs (created_at);
