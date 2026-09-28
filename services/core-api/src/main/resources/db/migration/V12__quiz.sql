-- assessment.quiz: попытки тестов, ответы, исключения, отложенная публикация оценок (FR-QUIZ-01..07, DATA-02, AC-4, AC-10).
-- Банк вопросов и состав тестов — MongoDB (question_categories, questions, question_versions, quiz_layouts).

CREATE TABLE quiz_attempts (
    id           UUID PRIMARY KEY,
    tenant_id    UUID          NOT NULL REFERENCES tenants (id),
    course_id    UUID          NOT NULL,
    item_id      UUID          NOT NULL,
    user_id      UUID          NOT NULL REFERENCES users (id),
    number       INT           NOT NULL CHECK (number > 0),
    state        TEXT          NOT NULL CHECK (state IN ('in_progress', 'finished', 'abandoned')),
    started_at   TIMESTAMPTZ   NOT NULL,
    time_due     TIMESTAMPTZ,
    finished_at  TIMESTAMPTZ,
    score        NUMERIC(10, 2),
    max_score    NUMERIC(10, 2) NOT NULL,
    needs_manual BOOLEAN       NOT NULL DEFAULT false,
    -- снимок слотов: [{slot, page, points, questionId, questionVersionId, optionOrder[]}]
    layout       JSONB         NOT NULL,
    UNIQUE (tenant_id, item_id, user_id, number)
);
-- Не более одной незавершённой попытки пользователя в тесте (гонка параллельного старта).
CREATE UNIQUE INDEX quiz_attempts_one_open_idx ON quiz_attempts (tenant_id, item_id, user_id) WHERE state = 'in_progress';
CREATE INDEX quiz_attempts_user_idx ON quiz_attempts (tenant_id, item_id, user_id);
CREATE INDEX quiz_attempts_item_idx ON quiz_attempts (tenant_id, item_id, started_at DESC, id DESC);
CREATE INDEX quiz_attempts_course_idx ON quiz_attempts (tenant_id, course_id) WHERE state = 'finished' AND needs_manual;
CREATE INDEX quiz_attempts_due_idx ON quiz_attempts (time_due) WHERE state = 'in_progress' AND time_due IS NOT NULL;
CREATE INDEX quiz_attempts_layout_idx ON quiz_attempts USING gin (layout jsonb_path_ops);

-- Строки создаются при старте попытки для всех слотов: автосохранение — один UPDATE по PK (NFR-PERF-02).
CREATE TABLE attempt_answers (
    attempt_id          UUID          NOT NULL REFERENCES quiz_attempts (id),
    tenant_id           UUID          NOT NULL REFERENCES tenants (id),
    slot                INT           NOT NULL CHECK (slot > 0),
    question_version_id UUID          NOT NULL,
    response            JSONB,
    fraction            NUMERIC(7, 6),
    score               NUMERIC(10, 2),
    needs_manual        BOOLEAN       NOT NULL DEFAULT false,
    graded_by           UUID REFERENCES users (id),
    graded_at           TIMESTAMPTZ,
    comment             TEXT,
    flagged             BOOLEAN       NOT NULL DEFAULT false,
    saved_at            TIMESTAMPTZ,
    PRIMARY KEY (attempt_id, slot)
);
CREATE INDEX attempt_answers_pending_idx ON attempt_answers (attempt_id) WHERE needs_manual AND graded_by IS NULL;

CREATE TABLE quiz_overrides (
    id             UUID PRIMARY KEY,
    tenant_id      UUID        NOT NULL REFERENCES tenants (id),
    course_id      UUID        NOT NULL,
    item_id        UUID        NOT NULL,
    user_id        UUID REFERENCES users (id),
    group_id       UUID,
    open_at        TIMESTAMPTZ,
    close_at       TIMESTAMPTZ,
    time_limit_sec INT CHECK (time_limit_sec > 0),
    max_attempts   INT CHECK (max_attempts > 0),
    created_by     UUID        NOT NULL REFERENCES users (id),
    created_at     TIMESTAMPTZ NOT NULL,
    CHECK ((user_id IS NULL) <> (group_id IS NULL))
);
CREATE UNIQUE INDEX quiz_overrides_user_idx ON quiz_overrides (tenant_id, item_id, user_id) WHERE user_id IS NOT NULL;
CREATE UNIQUE INDEX quiz_overrides_group_idx ON quiz_overrides (tenant_id, item_id, group_id) WHERE group_id IS NOT NULL;

-- Публикация оценок в момент закрытия теста (review.whenScore = after_close).
CREATE TABLE quiz_grade_releases (
    tenant_id   UUID        NOT NULL REFERENCES tenants (id),
    item_id     UUID        NOT NULL,
    course_id   UUID        NOT NULL,
    release_at  TIMESTAMPTZ NOT NULL,
    released_at TIMESTAMPTZ,
    PRIMARY KEY (tenant_id, item_id)
);
CREATE INDEX quiz_grade_releases_due_idx ON quiz_grade_releases (release_at) WHERE released_at IS NULL;
