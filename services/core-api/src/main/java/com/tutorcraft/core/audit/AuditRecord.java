package com.tutorcraft.core.audit;

import java.util.Map;
import java.util.UUID;

/**
 * Запись журнала аудита (FR-REPORT-02, NFR-SEC-09). {@code diff} не должен содержать паролей, токенов,
 * ответов студентов (NFR-SEC-11) — только изменённые поля и идентификаторы.
 */
public record AuditRecord(UUID tenantId, UUID actorId, String action, String objectType, String objectId,
                          String context, Map<String, Object> diff) {

    public static AuditRecord of(UUID tenantId, UUID actorId, String action, String objectType, String objectId) {
        return new AuditRecord(tenantId, actorId, action, objectType, objectId, null, null);
    }

    public AuditRecord withDiff(Map<String, Object> newDiff) {
        return new AuditRecord(tenantId, actorId, action, objectType, objectId, context, newDiff);
    }

    public AuditRecord withContext(String newContext) {
        return new AuditRecord(tenantId, actorId, action, objectType, objectId, newContext, diff);
    }
}
