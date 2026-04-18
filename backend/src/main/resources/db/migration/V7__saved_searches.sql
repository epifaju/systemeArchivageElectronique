-- Recherches sauvegardées par utilisateur (critères JSON)

CREATE TABLE saved_searches (
    id          BIGSERIAL PRIMARY KEY,
    uuid        UUID NOT NULL DEFAULT gen_random_uuid() UNIQUE,
    user_id     BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name        VARCHAR(200) NOT NULL,
    criteria    JSONB NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_saved_searches_user_updated ON saved_searches (user_id, updated_at DESC);
