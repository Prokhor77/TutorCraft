package com.tutorcraft.core.enrollment.application;

import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.enrollment.EnrollmentEvents.EnrollmentChanged;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** События EnrollmentChanged и аудит изменений записей/групп/приглашений (в транзакции операции). */
@Component
class EnrollmentChanges {

    static final String OBJECT_ENROLLMENT = "enrollment";
    static final String OBJECT_INVITE_LINK = "invite_link";
    static final String OBJECT_GROUP = "group";
    /** Статус в событии при удалении записи (строки больше нет). */
    static final String STATUS_DELETED = "deleted";

    private static final Logger log = LoggerFactory.getLogger(EnrollmentChanges.class);

    private final ApplicationEventPublisher publisher;
    private final AuditLog audit;

    EnrollmentChanges(ApplicationEventPublisher publisher, AuditLog audit) {
        this.publisher = publisher;
        this.audit = audit;
    }

    void enrollmentChanged(UUID tenantId, UUID courseId, UUID userId, String roleKey, String status, String method,
                           boolean created) {
        log.info("Enrollment in course {} of user {}: {} ({})", courseId, userId, status, created ? "created" : "changed");
        publisher.publishEvent(new EnrollmentChanged(tenantId, courseId, userId, roleKey, status, method, created));
    }

    void audit(UUID tenantId, UUID actorId, String objectType, UUID objectId, String verb, UUID courseId,
               Map<String, Object> diff) {
        AuditRecord record = AuditRecord.of(tenantId, actorId, objectType + "." + verb, objectType, objectId.toString())
                .withContext("course:" + courseId);
        audit.record(diff == null || diff.isEmpty() ? record : record.withDiff(diff));
    }
}
