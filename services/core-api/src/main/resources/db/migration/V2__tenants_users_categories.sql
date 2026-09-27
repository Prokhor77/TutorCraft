-- Базовые сущности идентичности и организации (FR-USER, FR-ADMIN-01, FR-COURSE-01).

CREATE TABLE tenants (
    id                UUID PRIMARY KEY,
    slug              TEXT        NOT NULL UNIQUE,
    name              TEXT        NOT NULL,
    status            TEXT        NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'suspended')),
    default_locale    TEXT        NOT NULL DEFAULT 'ru',
    default_timezone  TEXT        NOT NULL DEFAULT 'Europe/Moscow',
    logo_file_id      UUID,
    primary_color     TEXT,
    password_policy   JSONB       NOT NULL DEFAULT '{"minLength":10,"requireDigit":true,"requireLetter":true}',
    embed_whitelist   JSONB       NOT NULL DEFAULT '["www.youtube.com","youtube.com","player.vimeo.com","rutube.ru","vk.com","docs.google.com","www.geogebra.org"]',
    quota_users       INT,
    quota_storage_mb  BIGINT,
    version           BIGINT      NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL
);

CREATE TABLE users (
    id                UUID PRIMARY KEY,
    tenant_id         UUID        NOT NULL REFERENCES tenants (id),
    email             TEXT        NOT NULL,
    password_hash     TEXT,
    first_name        TEXT        NOT NULL,
    last_name         TEXT        NOT NULL,
    avatar_file_id    UUID,
    timezone          TEXT        NOT NULL DEFAULT 'Europe/Moscow',
    locale            TEXT        NOT NULL DEFAULT 'ru' CHECK (locale IN ('ru', 'en')),
    status            TEXT        NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'suspended', 'invited')),
    google_sub        TEXT,
    telegram_user_id  BIGINT,
    telegram_chat_id  BIGINT,
    last_login_at     TIMESTAMPTZ,
    version           BIGINT      NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ NOT NULL,
    updated_at        TIMESTAMPTZ NOT NULL,
    deleted_at        TIMESTAMPTZ
);
CREATE UNIQUE INDEX users_tenant_email_uq ON users (tenant_id, lower(email)) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX users_tenant_google_uq ON users (tenant_id, google_sub) WHERE google_sub IS NOT NULL;
CREATE UNIQUE INDEX users_tenant_telegram_uq ON users (tenant_id, telegram_user_id) WHERE telegram_user_id IS NOT NULL;
CREATE INDEX users_email_idx ON users (lower(email));
CREATE INDEX users_tenant_created_idx ON users (tenant_id, created_at DESC, id DESC);

CREATE TABLE categories (
    id          UUID PRIMARY KEY,
    tenant_id   UUID        NOT NULL REFERENCES tenants (id),
    parent_id   UUID REFERENCES categories (id),
    name        TEXT        NOT NULL,
    position    INT         NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX categories_tenant_parent_idx ON categories (tenant_id, parent_id, position);
