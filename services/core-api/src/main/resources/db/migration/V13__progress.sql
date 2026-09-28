-- progress: выполнение элементов (FR-PROG-01), последний просмотр («Продолжить обучение», FR-DASH-01),
-- завершение курса (FR-PROG-05). Строки создаются только для элементов с отслеживанием выполнения.

CREATE TABLE completion_states (
    tenant_id    UUID        NOT NULL REFERENCES tenants (id),
    course_id    UUID        NOT NULL,
    item_id      UUID        NOT NULL,
    user_id      UUID        NOT NULL REFERENCES users (id),
    -- наступившие события автоматического выполнения
    viewed       BOOLEAN     NOT NULL DEFAULT false,
    submitted    BOOLEAN     NOT NULL DEFAULT false,
    graded       BOOLEAN     NOT NULL DEFAULT false,
    passed       BOOLEAN     NOT NULL DEFAULT false,
    posted       BOOLEAN     NOT NULL DEFAULT false,
    manual       BOOLEAN     NOT NULL DEFAULT false,
    state        TEXT        NOT NULL CHECK (state IN ('complete', 'incomplete')),
    completed_at TIMESTAMPTZ,
    source       TEXT        NOT NULL CHECK (source IN ('auto', 'manual')),
    updated_at   TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, item_id, user_id)
);
CREATE INDEX completion_states_user_idx ON completion_states (tenant_id, user_id, course_id) WHERE state = 'complete';
CREATE INDEX completion_states_course_idx ON completion_states (tenant_id, course_id) WHERE state = 'complete';

CREATE TABLE item_views (
    tenant_id UUID        NOT NULL REFERENCES tenants (id),
    course_id UUID        NOT NULL,
    user_id   UUID        NOT NULL REFERENCES users (id),
    item_id   UUID        NOT NULL,
    viewed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, course_id, user_id)
);

CREATE TABLE course_completions (
    tenant_id    UUID        NOT NULL REFERENCES tenants (id),
    course_id    UUID        NOT NULL,
    user_id      UUID        NOT NULL REFERENCES users (id),
    completed_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (tenant_id, course_id, user_id)
);
