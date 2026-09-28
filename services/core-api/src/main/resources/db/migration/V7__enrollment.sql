-- enrollment: записи на курс, ссылки-приглашения, группы (FR-ENROL-01..06).

CREATE TABLE enrollments (
    id              UUID PRIMARY KEY,
    tenant_id       UUID        NOT NULL REFERENCES tenants (id),
    course_id       UUID        NOT NULL REFERENCES courses (id),
    user_id         UUID        NOT NULL REFERENCES users (id),
    role_key        TEXT        NOT NULL,
    status          TEXT        NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'suspended', 'completed')),
    method          TEXT        NOT NULL CHECK (method IN ('manual', 'self', 'invite_link', 'payment', 'import')),
    starts_at       TIMESTAMPTZ,
    ends_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    last_access_at  TIMESTAMPTZ,
    CONSTRAINT enrollments_course_user_uq UNIQUE (course_id, user_id),
    CONSTRAINT enrollments_dates_order CHECK (starts_at IS NULL OR ends_at IS NULL OR ends_at > starts_at)
);
CREATE INDEX enrollments_tenant_user_idx ON enrollments (tenant_id, user_id, status);
CREATE INDEX enrollments_course_list_idx ON enrollments (tenant_id, course_id, created_at DESC, id DESC);
CREATE INDEX enrollments_course_role_idx ON enrollments (tenant_id, course_id, role_key, status);

-- Токен хранится только как SHA-256-хеш (NFR-SEC-07), показывается один раз при создании.
CREATE TABLE course_invite_links (
    id          UUID PRIMARY KEY,
    tenant_id   UUID        NOT NULL REFERENCES tenants (id),
    course_id   UUID        NOT NULL REFERENCES courses (id),
    token_hash  TEXT        NOT NULL UNIQUE,
    role_key    TEXT        NOT NULL,
    expires_at  TIMESTAMPTZ,
    max_uses    INT CHECK (max_uses IS NULL OR max_uses > 0),
    uses        INT         NOT NULL DEFAULT 0 CHECK (uses >= 0),
    revoked_at  TIMESTAMPTZ,
    created_by  UUID        NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ NOT NULL
);
CREATE INDEX course_invite_links_course_idx ON course_invite_links (tenant_id, course_id, created_at DESC);

CREATE TABLE course_groups (
    id          UUID PRIMARY KEY,
    tenant_id   UUID        NOT NULL REFERENCES tenants (id),
    course_id   UUID        NOT NULL REFERENCES courses (id),
    name        TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX course_groups_course_name_uq ON course_groups (course_id, lower(name));
CREATE INDEX course_groups_tenant_course_idx ON course_groups (tenant_id, course_id);

CREATE TABLE course_group_members (
    group_id    UUID        NOT NULL REFERENCES course_groups (id) ON DELETE CASCADE,
    user_id     UUID        NOT NULL REFERENCES users (id),
    tenant_id   UUID        NOT NULL REFERENCES tenants (id),
    created_at  TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (group_id, user_id)
);
CREATE INDEX course_group_members_user_idx ON course_group_members (tenant_id, user_id);
