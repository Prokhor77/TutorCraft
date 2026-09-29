-- communication.calendar: заметки с текстом и «весь день», занятия курсов для учеников (expand-only).

ALTER TABLE calendar_personal_events
    ADD COLUMN description TEXT,
    ADD COLUMN all_day     BOOLEAN NOT NULL DEFAULT FALSE;

-- Занятие репетитора в курсе: необязательная привязка к модулю/элементу; audience = course — всем ученикам курса,
-- students — только перечисленным в calendar_lesson_attendees. Физическое удаление курса убирает и занятия
-- (CourseDataOwner.purgeCourse). module_id/item_id ссылаются на документы MongoDB, поэтому без FK.
CREATE TABLE calendar_lessons (
    id           UUID PRIMARY KEY,
    tenant_id    UUID        NOT NULL REFERENCES tenants (id),
    course_id    UUID        NOT NULL REFERENCES courses (id),
    module_id    UUID,
    item_id      UUID,
    title        TEXT        NOT NULL,
    description  TEXT,
    starts_at    TIMESTAMPTZ NOT NULL,
    ends_at      TIMESTAMPTZ,
    audience     TEXT        NOT NULL CHECK (audience IN ('course', 'students')),
    created_by   UUID        NOT NULL,
    version      BIGINT      NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    CHECK (ends_at IS NULL OR ends_at >= starts_at)
);
CREATE INDEX calendar_lessons_course_idx ON calendar_lessons (tenant_id, course_id, starts_at);

CREATE TABLE calendar_lesson_attendees (
    lesson_id  UUID NOT NULL REFERENCES calendar_lessons (id) ON DELETE CASCADE,
    tenant_id  UUID NOT NULL REFERENCES tenants (id),
    user_id    UUID NOT NULL REFERENCES users (id),
    PRIMARY KEY (lesson_id, user_id)
);
CREATE INDEX calendar_lesson_attendees_user_idx ON calendar_lesson_attendees (tenant_id, user_id);
