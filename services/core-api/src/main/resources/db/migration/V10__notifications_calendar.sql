-- communication.notifications + calendar: центр уведомлений (FR-NOTIF-01/02/04), доставки по каналам,
-- личные события календаря и iCal-подписка (FR-DASH-03).

-- In-app уведомления (канал web). Уникальность (user_id, dedupe_key) — идемпотентность FR-NOTIF-04.
CREATE TABLE notifications (
    id          UUID PRIMARY KEY,
    tenant_id   UUID        NOT NULL REFERENCES tenants (id),
    user_id     UUID        NOT NULL REFERENCES users (id),
    category    TEXT        NOT NULL,
    title       TEXT        NOT NULL,
    body        TEXT        NOT NULL,
    link        TEXT,
    dedupe_key  TEXT        NOT NULL,
    read_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL,
    UNIQUE (user_id, dedupe_key)
);
CREATE INDEX notifications_user_created_idx ON notifications (tenant_id, user_id, created_at DESC, id DESC);
CREATE INDEX notifications_user_unread_idx ON notifications (tenant_id, user_id) WHERE read_at IS NULL;

-- Доставки во внешние каналы: одно событие — одна доставка на канал (FR-NOTIF-04).
-- notification_id — id логического уведомления (для категории account строки в notifications нет).
CREATE TABLE notification_deliveries (
    id               UUID PRIMARY KEY,
    tenant_id        UUID        NOT NULL REFERENCES tenants (id),
    notification_id  UUID        NOT NULL,
    user_id          UUID        NOT NULL REFERENCES users (id),
    dedupe_key       TEXT        NOT NULL,
    channel          TEXT        NOT NULL CHECK (channel IN ('email', 'telegram')),
    status           TEXT        NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'sent', 'failed', 'skipped')),
    error            TEXT,
    created_at       TIMESTAMPTZ NOT NULL,
    updated_at       TIMESTAMPTZ NOT NULL,
    UNIQUE (user_id, dedupe_key, channel)
);
CREATE INDEX notification_deliveries_notification_idx ON notification_deliveries (notification_id, channel);

-- Только явные настройки пользователя; отсутствие строки — умолчание категории.
CREATE TABLE notification_preferences (
    tenant_id   UUID        NOT NULL REFERENCES tenants (id),
    user_id     UUID        NOT NULL REFERENCES users (id),
    category    TEXT        NOT NULL,
    channel     TEXT        NOT NULL CHECK (channel IN ('web', 'email', 'telegram')),
    enabled     BOOLEAN     NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (user_id, category, channel)
);

CREATE TABLE calendar_personal_events (
    id          UUID PRIMARY KEY,
    tenant_id   UUID        NOT NULL REFERENCES tenants (id),
    user_id     UUID        NOT NULL REFERENCES users (id),
    title       TEXT        NOT NULL,
    starts_at   TIMESTAMPTZ NOT NULL,
    ends_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    CHECK (ends_at IS NULL OR ends_at >= starts_at)
);
CREATE INDEX calendar_personal_events_user_idx ON calendar_personal_events (tenant_id, user_id, starts_at);

-- Секретная ссылка iCal: хранится только SHA-256 (NFR-SEC-07); перевыпуск заменяет строку.
CREATE TABLE ical_tokens (
    user_id     UUID PRIMARY KEY REFERENCES users (id),
    tenant_id   UUID        NOT NULL REFERENCES tenants (id),
    token_hash  TEXT        NOT NULL UNIQUE,
    created_at  TIMESTAMPTZ NOT NULL
);
