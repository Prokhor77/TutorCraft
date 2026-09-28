-- gradebook: журнал оценок (FR-GRADE-01..08, AC-7), история оценок append-only (DATA-03), шкалы (FR-GRADE-05).

CREATE TABLE scales (
    id          UUID PRIMARY KEY,
    tenant_id   UUID        NOT NULL REFERENCES tenants (id),
    course_id   UUID,                                   -- NULL — шкала уровня tenant
    name        TEXT        NOT NULL,
    levels      JSONB       NOT NULL,                   -- [{ "name": "...", "minPercent": 85 }, ...]
    created_at  TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX scales_tenant_name_uq ON scales (tenant_id, name) WHERE course_id IS NULL;
CREATE INDEX scales_course_idx ON scales (tenant_id, course_id);

CREATE TABLE gradebook_settings (
    course_id    UUID PRIMARY KEY,
    tenant_id    UUID        NOT NULL REFERENCES tenants (id),
    aggregation  TEXT        NOT NULL DEFAULT 'weighted_mean' CHECK (aggregation IN ('weighted_mean', 'sum')),
    scale_id     UUID REFERENCES scales (id),
    version      BIGINT      NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ NOT NULL
);

CREATE TABLE grade_categories (
    id          UUID PRIMARY KEY,
    tenant_id   UUID          NOT NULL REFERENCES tenants (id),
    course_id   UUID          NOT NULL,
    name        TEXT          NOT NULL,
    weight      NUMERIC(7, 3) NOT NULL DEFAULT 0 CHECK (weight >= 0 AND weight <= 100),   -- проценты
    position    INT           NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ   NOT NULL
);
CREATE INDEX grade_categories_course_idx ON grade_categories (tenant_id, course_id, position);

CREATE TABLE grade_items (
    id              UUID PRIMARY KEY,
    tenant_id       UUID           NOT NULL REFERENCES tenants (id),
    course_id       UUID           NOT NULL,
    source_item_id  UUID,                                          -- NULL — ручной столбец журнала
    name            TEXT           NOT NULL,
    max_score       NUMERIC(10, 2) NOT NULL CHECK (max_score >= 0),
    category_id     UUID REFERENCES grade_categories (id) ON DELETE SET NULL,
    position        INT            NOT NULL DEFAULT 0,
    deleted_at      TIMESTAMPTZ,
    created_at      TIMESTAMPTZ    NOT NULL,
    updated_at      TIMESTAMPTZ    NOT NULL
);
CREATE UNIQUE INDEX grade_items_source_uq ON grade_items (tenant_id, source_item_id) WHERE source_item_id IS NOT NULL;
CREATE INDEX grade_items_course_idx ON grade_items (tenant_id, course_id, position) WHERE deleted_at IS NULL;

CREATE TABLE grades (
    id             UUID PRIMARY KEY,
    tenant_id      UUID           NOT NULL REFERENCES tenants (id),
    grade_item_id  UUID           NOT NULL REFERENCES grade_items (id),
    user_id        UUID           NOT NULL REFERENCES users (id),
    raw_score      NUMERIC(10, 2),
    final_score    NUMERIC(10, 2),
    overridden     BOOLEAN        NOT NULL DEFAULT FALSE,
    locked         BOOLEAN        NOT NULL DEFAULT FALSE,
    published_at   TIMESTAMPTZ,
    graded_by      UUID REFERENCES users (id),
    graded_at      TIMESTAMPTZ,
    version        BIGINT         NOT NULL DEFAULT 0,
    created_at     TIMESTAMPTZ    NOT NULL,
    updated_at     TIMESTAMPTZ    NOT NULL,
    UNIQUE (grade_item_id, user_id)
);
CREATE INDEX grades_user_idx ON grades (tenant_id, user_id);
CREATE INDEX grades_user_published_idx ON grades (tenant_id, user_id, published_at DESC) WHERE published_at IS NOT NULL;

-- Append-only (DATA-03): UPDATE/DELETE запрещены триггером forbid_mutation() из V1.
CREATE TABLE grade_history (
    id         UUID PRIMARY KEY,
    tenant_id  UUID           NOT NULL REFERENCES tenants (id),
    grade_id   UUID           NOT NULL REFERENCES grades (id),
    old_score  NUMERIC(10, 2),
    new_score  NUMERIC(10, 2),
    actor_id   UUID,
    reason     TEXT           NOT NULL,
    at         TIMESTAMPTZ    NOT NULL
);
CREATE INDEX grade_history_grade_idx ON grade_history (tenant_id, grade_id, at DESC);

CREATE TRIGGER grade_history_append_only BEFORE UPDATE OR DELETE ON grade_history
    FOR EACH ROW EXECUTE FUNCTION forbid_mutation();
