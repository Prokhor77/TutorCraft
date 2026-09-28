-- Журнал активности (модуль activity): каждое действие пользователя в API и события браузера (переходы, ошибки).
-- В отличие от audit_log (бизнес-изменения, append-only, долгий срок) — операционный журнал с коротким сроком
-- хранения (tutorcraft.activity-log.retention), очищается заданием ActivityRetentionJob.
-- Не содержит тел запросов, query-строк, паролей и токенов (NFR-SEC-11).
CREATE TABLE activity_log (
    id            UUID PRIMARY KEY,
    at            TIMESTAMPTZ NOT NULL,
    kind          TEXT        NOT NULL CHECK (kind IN ('request', 'page_view', 'client_error')),
    tenant_id     UUID,                -- NULL: анонимный запрос (вход, регистрация, публичные страницы)
    user_id       UUID,
    ip            TEXT,
    user_agent    TEXT,
    request_id    TEXT,                -- X-Request-Id; у client_error — запрос, после которого случилась ошибка
    session_id    TEXT,                -- вкладка браузера (X-Client-Session)
    page          TEXT,                -- страница интерфейса, где произошло действие
    method        TEXT,
    route         TEXT,                -- шаблон маршрута: /api/v1/courses/{courseId}
    path          TEXT,                -- фактический путь, секреты замаскированы
    path_params   JSONB,
    handler       TEXT,                -- CourseController.update
    status        INTEGER,
    duration_ms   INTEGER,
    error_code    TEXT,
    error_type    TEXT,
    error_message TEXT,
    error_stack   TEXT
);
CREATE INDEX activity_log_tenant_at_idx ON activity_log (tenant_id, at DESC, id DESC);
CREATE INDEX activity_log_user_at_idx ON activity_log (user_id, at) WHERE user_id IS NOT NULL;
CREATE INDEX activity_log_session_at_idx ON activity_log (session_id, at) WHERE session_id IS NOT NULL;
CREATE INDEX activity_log_request_idx ON activity_log (request_id) WHERE request_id IS NOT NULL;
CREATE INDEX activity_log_errors_idx ON activity_log (tenant_id, at DESC, id DESC)
    WHERE status >= 500 OR kind = 'client_error';
CREATE INDEX activity_log_at_idx ON activity_log (at);
