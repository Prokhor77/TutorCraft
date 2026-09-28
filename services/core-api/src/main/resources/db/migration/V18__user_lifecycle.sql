-- identity: происхождение учётной записи (кто и как создал) и блокировка главным администратором платформы.
-- Блокировка платформой не зависит от users.status (блокировка школой): репетитор не может её снять.

ALTER TABLE users ADD COLUMN created_by UUID REFERENCES users (id);
ALTER TABLE users ADD COLUMN created_via TEXT NOT NULL DEFAULT 'unknown'
    CHECK (created_via IN ('unknown', 'self_signup', 'school_owner', 'tutor_invite', 'admin', 'import', 'system'));
ALTER TABLE users ADD COLUMN platform_blocked_at TIMESTAMPTZ;
ALTER TABLE users ADD COLUMN platform_block_reason TEXT;

-- Восстановление происхождения существующих записей из журнала аудита.
UPDATE users u SET created_via = 'school_owner'
FROM audit_log a
WHERE a.action = 'tenant.registered' AND a.actor_id = u.id AND a.tenant_id = u.tenant_id;

UPDATE users u SET created_via = 'self_signup'
FROM audit_log a
WHERE a.action = 'user.self_registered' AND a.object_id = u.id::text AND a.tenant_id = u.tenant_id
  AND u.created_via = 'unknown';

UPDATE users u SET created_via = 'admin', created_by = a.actor_id
FROM audit_log a
WHERE a.action = 'user.created' AND a.object_id = u.id::text AND a.tenant_id = u.tenant_id
  AND u.created_via = 'unknown' AND EXISTS (SELECT 1 FROM users c WHERE c.id = a.actor_id);

CREATE INDEX users_platform_created_idx ON users (created_at DESC, id DESC) WHERE deleted_at IS NULL;
CREATE INDEX users_created_by_idx ON users (created_by) WHERE created_by IS NOT NULL;
