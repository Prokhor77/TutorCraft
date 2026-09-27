-- Общие технические таблицы: outbox (ARCH-04), идемпотентность (API-05), обработанные события, аудит (FR-REPORT-02).

CREATE TABLE outbox (
    id           UUID PRIMARY KEY,
    topic        TEXT        NOT NULL,
    message_key  TEXT        NOT NULL,
    payload      JSONB       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempts     INT         NOT NULL DEFAULT 0,
    last_error   TEXT
);
CREATE INDEX outbox_unpublished_idx ON outbox (created_at) WHERE published_at IS NULL;

CREATE TABLE processed_events (
    event_id     UUID        NOT NULL,
    consumer     TEXT        NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id, consumer)
);

CREATE TABLE idempotency_keys (
    tenant_id    UUID        NOT NULL,
    user_id      UUID        NOT NULL,
    operation    TEXT        NOT NULL,
    idem_key     TEXT        NOT NULL,
    request_hash TEXT        NOT NULL,
    response     JSONB,
    created_at   TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, user_id, operation, idem_key)
);
CREATE INDEX idempotency_keys_created_idx ON idempotency_keys (created_at);

-- Append-only (DATA-03): UPDATE/DELETE запрещены триггером; очистка по сроку — через функцию purge_audit_log.
CREATE TABLE audit_log (
    id          UUID PRIMARY KEY,
    tenant_id   UUID        NOT NULL,
    actor_id    UUID,
    action      TEXT        NOT NULL,
    object_type TEXT        NOT NULL,
    object_id   TEXT        NOT NULL,
    context     TEXT,
    ip          TEXT,
    user_agent  TEXT,
    diff        JSONB,
    at          TIMESTAMPTZ NOT NULL
);
CREATE INDEX audit_log_tenant_at_idx ON audit_log (tenant_id, at DESC, id DESC);
CREATE INDEX audit_log_actor_idx ON audit_log (tenant_id, actor_id, at DESC);
CREATE INDEX audit_log_object_idx ON audit_log (tenant_id, object_type, object_id);

CREATE FUNCTION forbid_mutation() RETURNS trigger AS $$
BEGIN
    IF current_setting('tutorcraft.retention_purge', true) = 'on' AND TG_OP = 'DELETE' THEN
        RETURN OLD;
    END IF;
    RAISE EXCEPTION 'table % is append-only', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_log_append_only BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();

CREATE FUNCTION purge_audit_log(older_than TIMESTAMPTZ) RETURNS BIGINT AS $$
DECLARE removed BIGINT;
BEGIN
    PERFORM set_config('tutorcraft.retention_purge', 'on', true);
    DELETE FROM audit_log WHERE at < older_than;
    GET DIAGNOSTICS removed = ROW_COUNT;
    RETURN removed;
END;
$$ LANGUAGE plpgsql;
