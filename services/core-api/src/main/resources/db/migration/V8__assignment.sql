-- assessment.assignment: сдачи заданий (FR-ASSIGN-01..07, AC-3), отзывы, индивидуальные продления (FR-ASSIGN-03).
-- Ссылки на курсы/элементы — по UUID без FK (элементы живут в MongoDB, ADR-004).

CREATE TABLE submissions (
    id            UUID PRIMARY KEY,
    tenant_id     UUID        NOT NULL REFERENCES tenants (id),
    course_id     UUID        NOT NULL,
    item_id       UUID        NOT NULL,
    user_id       UUID        NOT NULL REFERENCES users (id),   -- автор попытки
    group_id      UUID,                                          -- групповая сдача
    owner_key     TEXT        NOT NULL,                          -- 'u:<userId>' | 'g:<groupId>'
    attempt_no    INT         NOT NULL CHECK (attempt_no >= 1),
    is_latest     BOOLEAN     NOT NULL DEFAULT TRUE,
    status        TEXT        NOT NULL CHECK (status IN ('draft', 'submitted', 'submitted_late', 'graded', 'returned')),
    text          JSONB,
    submitted_at  TIMESTAMPTZ,
    due_at        TIMESTAMPTZ,                                   -- действующий срок на момент сдачи (с учётом продления)
    late          BOOLEAN     NOT NULL DEFAULT FALSE,
    version       BIGINT      NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX submissions_owner_attempt_uq ON submissions (tenant_id, item_id, owner_key, attempt_no);
CREATE UNIQUE INDEX submissions_owner_latest_uq ON submissions (tenant_id, item_id, owner_key) WHERE is_latest;
CREATE INDEX submissions_item_latest_idx ON submissions (tenant_id, item_id, status) WHERE is_latest;
CREATE INDEX submissions_pending_idx ON submissions (tenant_id, course_id, submitted_at)
    WHERE is_latest AND status IN ('submitted', 'submitted_late');

-- Участники сдачи: автор (индивидуальная) или все члены группы (групповая).
CREATE TABLE submission_members (
    tenant_id      UUID NOT NULL REFERENCES tenants (id),
    submission_id  UUID NOT NULL REFERENCES submissions (id),
    user_id        UUID NOT NULL REFERENCES users (id),
    PRIMARY KEY (submission_id, user_id)
);
CREATE INDEX submission_members_user_idx ON submission_members (tenant_id, user_id);

CREATE TABLE submission_files (
    tenant_id      UUID NOT NULL REFERENCES tenants (id),
    submission_id  UUID NOT NULL REFERENCES submissions (id),
    file_id        UUID NOT NULL REFERENCES files (id),
    position       INT  NOT NULL,
    PRIMARY KEY (submission_id, file_id)
);

-- Отзывы append-only: действующий — последний по created_at.
CREATE TABLE submission_feedback (
    id                   UUID PRIMARY KEY,
    tenant_id            UUID          NOT NULL REFERENCES tenants (id),
    submission_id        UUID          NOT NULL REFERENCES submissions (id),
    grader_id            UUID          NOT NULL REFERENCES users (id),
    text                 JSONB,
    file_ids             JSONB         NOT NULL DEFAULT '[]',
    score                NUMERIC(10, 2),
    return_for_revision  BOOLEAN       NOT NULL DEFAULT FALSE,
    published_at         TIMESTAMPTZ,
    created_at           TIMESTAMPTZ   NOT NULL
);
CREATE INDEX submission_feedback_submission_idx ON submission_feedback (tenant_id, submission_id, created_at DESC);

-- Индивидуальные/групповые исключения по срокам элемента (FR-ASSIGN-03; схема общая с FR-QUIZ-06).
CREATE TABLE item_overrides (
    id              UUID PRIMARY KEY,
    tenant_id       UUID        NOT NULL REFERENCES tenants (id),
    course_id       UUID        NOT NULL,
    item_id         UUID        NOT NULL,
    user_id         UUID REFERENCES users (id),
    group_id        UUID,
    open_at         TIMESTAMPTZ,
    due_at          TIMESTAMPTZ,
    close_at        TIMESTAMPTZ,
    time_limit_sec  INT,
    max_attempts    INT,
    created_by      UUID        NOT NULL REFERENCES users (id),
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    CHECK ((user_id IS NULL) <> (group_id IS NULL))
);
CREATE UNIQUE INDEX item_overrides_user_uq ON item_overrides (tenant_id, item_id, user_id) WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX item_overrides_group_uq ON item_overrides (tenant_id, item_id, group_id) WHERE group_id IS NOT NULL;
