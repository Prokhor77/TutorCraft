-- Роли и права (FR-ACL-01/02). Системные роли: tenant_id IS NULL, is_system = true.

CREATE TABLE roles (
    id          UUID PRIMARY KEY,
    tenant_id   UUID REFERENCES tenants (id),
    key         TEXT        NOT NULL,
    name        TEXT        NOT NULL,
    scope       TEXT        NOT NULL CHECK (scope IN ('platform', 'tenant', 'category', 'course')),
    is_system   BOOLEAN     NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL
);
CREATE UNIQUE INDEX roles_system_key_uq ON roles (key) WHERE tenant_id IS NULL;
CREATE UNIQUE INDEX roles_tenant_key_uq ON roles (tenant_id, key) WHERE tenant_id IS NOT NULL;

CREATE TABLE role_permissions (
    role_id     UUID NOT NULL REFERENCES roles (id) ON DELETE CASCADE,
    permission  TEXT NOT NULL,
    PRIMARY KEY (role_id, permission)
);

-- Назначения ролей уровня platform/tenant/category. Роли в курсе — через enrollments.role_id.
CREATE TABLE role_assignments (
    id            UUID PRIMARY KEY,
    tenant_id     UUID        NOT NULL REFERENCES tenants (id),
    user_id       UUID        NOT NULL REFERENCES users (id),
    role_id       UUID        NOT NULL REFERENCES roles (id),
    context_type  TEXT        NOT NULL CHECK (context_type IN ('platform', 'tenant', 'category')),
    context_id    UUID,
    created_at    TIMESTAMPTZ NOT NULL,
    created_by    UUID
);
CREATE UNIQUE INDEX role_assignments_uq ON role_assignments (user_id, role_id, context_type, COALESCE(context_id, '00000000-0000-0000-0000-000000000000'));
CREATE INDEX role_assignments_user_idx ON role_assignments (tenant_id, user_id);
