-- identity: сессии (ADR-003), одноразовые токены (FR-AUTH-02, FR-USER-03, привязка Telegram), предпросмотр импорта (FR-USER-02).
-- Все токены хранятся только SHA-256-хешем (NFR-SEC-07).

CREATE TABLE refresh_tokens (
    id           UUID PRIMARY KEY,
    tenant_id    UUID        NOT NULL REFERENCES tenants (id),
    user_id      UUID        NOT NULL REFERENCES users (id),
    family_id    UUID        NOT NULL,
    token_hash   TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ,
    replaced_by  UUID
);
CREATE UNIQUE INDEX refresh_tokens_hash_uq ON refresh_tokens (token_hash);
CREATE INDEX refresh_tokens_family_idx ON refresh_tokens (family_id);
CREATE INDEX refresh_tokens_user_active_idx ON refresh_tokens (tenant_id, user_id) WHERE revoked_at IS NULL;
CREATE INDEX refresh_tokens_expires_idx ON refresh_tokens (expires_at);

-- Три таблицы одноразовых токенов одной формы: used_at заполняется при использовании или аннулировании.
CREATE TABLE password_reset_tokens (
    id          UUID PRIMARY KEY,
    tenant_id   UUID        NOT NULL REFERENCES tenants (id),
    user_id     UUID        NOT NULL REFERENCES users (id),
    token_hash  TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ
);
CREATE UNIQUE INDEX password_reset_tokens_hash_uq ON password_reset_tokens (token_hash);
CREATE INDEX password_reset_tokens_user_idx ON password_reset_tokens (tenant_id, user_id) WHERE used_at IS NULL;

CREATE TABLE invitations (
    id          UUID PRIMARY KEY,
    tenant_id   UUID        NOT NULL REFERENCES tenants (id),
    user_id     UUID        NOT NULL REFERENCES users (id),
    token_hash  TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ
);
CREATE UNIQUE INDEX invitations_hash_uq ON invitations (token_hash);
CREATE INDEX invitations_user_idx ON invitations (tenant_id, user_id) WHERE used_at IS NULL;

CREATE TABLE telegram_link_codes (
    id          UUID PRIMARY KEY,
    tenant_id   UUID        NOT NULL REFERENCES tenants (id),
    user_id     UUID        NOT NULL REFERENCES users (id),
    token_hash  TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    used_at     TIMESTAMPTZ
);
CREATE UNIQUE INDEX telegram_link_codes_hash_uq ON telegram_link_codes (token_hash);
CREATE INDEX telegram_link_codes_user_idx ON telegram_link_codes (tenant_id, user_id) WHERE used_at IS NULL;

CREATE TABLE user_import_previews (
    id            UUID PRIMARY KEY,
    tenant_id     UUID        NOT NULL REFERENCES tenants (id),
    created_by    UUID        NOT NULL REFERENCES users (id),
    rows          JSONB       NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    expires_at    TIMESTAMPTZ NOT NULL,
    committed_at  TIMESTAMPTZ
);
CREATE INDEX user_import_previews_tenant_idx ON user_import_previews (tenant_id, created_by);
CREATE INDEX user_import_previews_expires_idx ON user_import_previews (expires_at);

CREATE INDEX users_google_sub_idx ON users (google_sub) WHERE google_sub IS NOT NULL;
CREATE INDEX users_telegram_user_idx ON users (telegram_user_id) WHERE telegram_user_id IS NOT NULL;
