package com.tutorcraft.core.courses.application;

import com.tutorcraft.core.audit.AuditLog;
import com.tutorcraft.core.audit.AuditRecord;
import com.tutorcraft.core.courses.CourseEvents.ChangeKind;
import com.tutorcraft.core.courses.CourseEvents.CourseChanged;
import com.tutorcraft.core.courses.CourseEvents.ItemChanged;
import com.tutorcraft.core.courses.CourseEvents.ItemViewed;
import com.tutorcraft.core.courses.domain.CourseItem;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Доменные события курсов (Spring) и записи аудита — в транзакции операции. */
@Component
class CourseChangeEvents {

    static final String OBJECT_COURSE = "course";
    static final String OBJECT_MODULE = "module";
    static final String OBJECT_ITEM = "item";

    private static final Logger log = LoggerFactory.getLogger(CourseChangeEvents.class);

    private final ApplicationEventPublisher publisher;
    private final AuditLog audit;

    CourseChangeEvents(ApplicationEventPublisher publisher, AuditLog audit) {
        this.publisher = publisher;
        this.audit = audit;
    }

    void courseChanged(UUID tenantId, UUID courseId, ChangeKind kind, UUID actorId) {
        log.info("Course {} {}", courseId, kind);
        publisher.publishEvent(new CourseChanged(tenantId, courseId, kind, actorId));
    }

    void itemChanged(CourseItem item, ChangeKind kind, UUID actorId) {
        log.info("Item {} {}", item.id(), kind);
        publisher.publishEvent(new ItemChanged(item.tenantId(), item.courseId(), item.id(), item.type(), kind, actorId));
    }

    void itemViewed(CourseItem item, UUID userId) {
        publisher.publishEvent(new ItemViewed(item.tenantId(), item.courseId(), item.id(), userId));
    }

    /** Аудит: action вида {@code <objectType>.<verb>}, context — курс. */
    void audit(UUID tenantId, UUID actorId, String objectType, UUID objectId, String verb, UUID courseId,
               Map<String, Object> diff) {
        AuditRecord record = AuditRecord.of(tenantId, actorId, objectType + "." + verb, objectType, objectId.toString())
                .withContext(OBJECT_COURSE + ":" + courseId);
        audit.record(diff == null || diff.isEmpty() ? record : record.withDiff(diff));
    }
}
