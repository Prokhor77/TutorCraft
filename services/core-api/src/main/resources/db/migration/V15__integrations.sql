-- integrations: personal access tokens (FR-INTEG-01), исходящие вебхуки и журнал доставки (FR-INTEG-02).

CREATE TABLE api_tokens (
    id            UUID PRIMARY KEY,
    tenant_id     UUID        NOT NULL REFERENCES tenants (id),
    user_id       UUID        NOT NULL REFERENCES users (id),
    name          TEXT        NOT NULL,
    token_hash    TEXT        NOT NULL,
    scopes        JSONB       NOT NULL,
    expires_at    TIMESTAMPTZ,
    last_used_at  TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL,
    revoked_at    TIMESTAMPTZ
);
CREATE UNIQUE INDEX api_tokens_hash_uq ON api_tokens (token_hash);
CREATE INDEX api_tokens_tenant_idx ON api_tokens (tenant_id, created_at DESC) WHERE revoked_at IS NULL;

CREATE TABLE webhooks (
    id                UUID PRIMARY KEY,
    tenant_id         UUID        NOT NULL REFERENCES tenants (id),
    url               TEXT        NOT NULL,
    events            JSONB       NOT NULL,
    secret_encrypted  TEXT        NOT NULL,
    created_by        UUID        NOT NULL REFERENCES users (id),
    created_at        TIMESTAMPTZ NOT NULL,
    deleted_at        TIMESTAMPTZ
);
CREATE INDEX webhooks_tenant_idx ON webhooks (tenant_id) WHERE deleted_at IS NULL;

CREATE TABLE webhook_deliveries (
    id               UUID PRIMARY KEY,
    tenant_id        UUID        NOT NULL REFERENCES tenants (id),
    webhook_id       UUID        NOT NULL REFERENCES webhooks (id),
    event_name       TEXT        NOT NULL,
    payload          JSONB       NOT NULL,
    status           TEXT        NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'succeeded', 'failed')),
    attempts         INT         NOT NULL DEFAULT 0,
    next_attempt_at  TIMESTAMPTZ NOT NULL,
    last_attempt_at  TIMESTAMPTZ,
    response_code    INT,
    error            TEXT,
    created_at       TIMESTAMPTZ NOT NULL
);
CREATE INDEX webhook_deliveries_due_idx ON webhook_deliveries (next_attempt_at) WHERE status = 'pending';
CREATE INDEX webhook_deliveries_webhook_idx ON webhook_deliveries (tenant_id, webhook_id, created_at DESC, id DESC);
