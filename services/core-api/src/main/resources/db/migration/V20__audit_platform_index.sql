-- audit: журнал всех школ сразу для главного администратора платформы (GET /audit-log?allTenants=true)
-- сортируется по времени без фильтра по tenant_id.
CREATE INDEX audit_log_at_idx ON audit_log (at DESC, id DESC);
