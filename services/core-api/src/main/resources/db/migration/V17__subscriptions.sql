-- billing: подписка школы на платформу. Все сроки открывают одинаковый функционал; пробный период
-- начинается при первом обращении к подписке. Деньги — в минимальных единицах.

CREATE TABLE tenant_subscriptions (
    tenant_id      UUID PRIMARY KEY REFERENCES tenants (id),
    trial_ends_at  TIMESTAMPTZ NOT NULL,
    paid_until     TIMESTAMPTZ,
    version        BIGINT      NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ NOT NULL,
    updated_at     TIMESTAMPTZ NOT NULL
);

CREATE TABLE subscription_payments (
    id            UUID PRIMARY KEY,
    tenant_id     UUID        NOT NULL REFERENCES tenants (id),
    term          TEXT        NOT NULL CHECK (term IN ('month', 'quarter', 'year')),
    amount_minor  BIGINT      NOT NULL CHECK (amount_minor >= 0),
    currency      TEXT        NOT NULL,
    provider      TEXT        NOT NULL,
    period_start  TIMESTAMPTZ NOT NULL,
    period_end    TIMESTAMPTZ NOT NULL CHECK (period_end > period_start),
    paid_by       UUID        NOT NULL REFERENCES users (id),
    created_at    TIMESTAMPTZ NOT NULL
);
CREATE INDEX subscription_payments_tenant_created_idx ON subscription_payments (tenant_id, created_at DESC, id DESC);
