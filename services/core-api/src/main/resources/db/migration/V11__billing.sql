-- billing: заказы и идемпотентность вебхуков провайдеров (FR-ENROL-09, ADR-002). Деньги — в минимальных единицах.

CREATE TABLE orders (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID        NOT NULL REFERENCES tenants (id),
    course_id            UUID        NOT NULL,
    buyer_id             UUID        NOT NULL REFERENCES users (id),
    amount_minor         BIGINT      NOT NULL CHECK (amount_minor >= 0),
    currency             TEXT        NOT NULL,
    status               TEXT        NOT NULL CHECK (status IN ('pending', 'paid', 'failed', 'refunded', 'canceled')),
    provider             TEXT        NOT NULL,
    provider_payment_id  TEXT,
    confirmation_url     TEXT,
    created_at           TIMESTAMPTZ NOT NULL,
    paid_at              TIMESTAMPTZ,
    updated_at           TIMESTAMPTZ NOT NULL,
    version              BIGINT      NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX orders_provider_payment_uq ON orders (provider, provider_payment_id) WHERE provider_payment_id IS NOT NULL;
CREATE INDEX orders_tenant_created_idx ON orders (tenant_id, created_at DESC, id DESC);
CREATE INDEX orders_course_created_idx ON orders (tenant_id, course_id, created_at DESC, id DESC);
CREATE INDEX orders_buyer_idx ON orders (tenant_id, buyer_id);

-- Входящие события провайдеров: повторная доставка вебхука обрабатывается один раз.
CREATE TABLE payment_events (
    provider           TEXT        NOT NULL,
    provider_event_id  TEXT        NOT NULL,
    order_id           UUID REFERENCES orders (id),
    payload            JSONB       NOT NULL,
    received_at        TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (provider, provider_event_id)
);
