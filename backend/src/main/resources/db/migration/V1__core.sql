-- Départements, types documentaires, utilisateurs, refresh tokens

CREATE TABLE departments (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    code            VARCHAR(50) NOT NULL UNIQUE,
    name_fr         VARCHAR(200) NOT NULL,
    name_pt         VARCHAR(200) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE document_types (
    id               BIGSERIAL PRIMARY KEY,
    uuid             UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    code             VARCHAR(50) NOT NULL UNIQUE,
    label_fr         VARCHAR(200) NOT NULL,
    label_pt         VARCHAR(200) NOT NULL,
    required_fields  JSONB,
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    uuid            UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    username        VARCHAR(100) NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    email           VARCHAR(255) UNIQUE,
    full_name       VARCHAR(200),
    role            VARCHAR(50) NOT NULL,
    department_id   BIGINT REFERENCES departments (id),
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_users_role CHECK (role IN ('ADMIN', 'ARCHIVISTE', 'AGENT', 'LECTEUR', 'AUDITEUR'))
);

CREATE INDEX idx_users_department ON users (department_id);

CREATE TABLE refresh_tokens (
    id          BIGSERIAL PRIMARY KEY,
    token_id    VARCHAR(64) NOT NULL UNIQUE,
    user_id     BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);
