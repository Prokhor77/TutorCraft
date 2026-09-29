package com.tutorcraft.core.communication.calendar.infrastructure;

import com.tutorcraft.core.communication.calendar.application.LessonRepository;
import com.tutorcraft.core.communication.calendar.domain.Lesson;
import com.tutorcraft.core.communication.calendar.domain.LessonAudience;
import com.tutorcraft.core.shared.persistence.Timestamps;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcLessonRepository implements LessonRepository {

    private static final String FIELDS = """
            id, tenant_id, course_id, module_id, item_id, title, description, starts_at, ends_at, audience, created_by,
            version, created_at, updated_at""";

    private final JdbcClient jdbc;

    JdbcLessonRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insert(Lesson lesson) {
        jdbc.sql("""
                INSERT INTO calendar_lessons (id, tenant_id, course_id, module_id, item_id, title, description, starts_at,
                    ends_at, audience, created_by, version, created_at, updated_at)
                VALUES (:id, :tenantId, :courseId, :moduleId, :itemId, :title, :description, :startsAt, :endsAt, :audience,
                    :createdBy, :version, :createdAt, :updatedAt)
                """)
            .param("id", lesson.id()).param("tenantId", lesson.tenantId()).param("courseId", lesson.courseId())
            .param("createdBy", lesson.createdBy()).param("createdAt", Timestamps.of(lesson.createdAt()))
            .params(mutableParams(lesson))
            .update();
        replaceAttendees(lesson);
    }

    @Override
    public boolean update(Lesson lesson, long expectedVersion) {
        int updated = jdbc.sql("""
                UPDATE calendar_lessons SET module_id = :moduleId, item_id = :itemId, title = :title,
                    description = :description, starts_at = :startsAt, ends_at = :endsAt, audience = :audience,
                    version = :version, updated_at = :updatedAt
                WHERE tenant_id = :tenantId AND id = :id AND version = :expected
                """)
            .param("tenantId", lesson.tenantId()).param("id", lesson.id()).param("expected", expectedVersion)
            .params(mutableParams(lesson))
            .update();
        if (updated == 0) {
            return false;
        }
        replaceAttendees(lesson);
        return true;
    }

    @Override
    public void delete(UUID tenantId, UUID lessonId) {
        jdbc.sql("DELETE FROM calendar_lessons WHERE tenant_id = :tenantId AND id = :id")
            .param("tenantId", tenantId).param("id", lessonId).update();
    }

    @Override
    public Optional<Lesson> find(UUID tenantId, UUID courseId, UUID lessonId) {
        return jdbc.sql("SELECT " + FIELDS + """
                 FROM calendar_lessons WHERE tenant_id = :tenantId AND course_id = :courseId AND id = :id
                """)
            .param("tenantId", tenantId).param("courseId", courseId).param("id", lessonId)
            .query((rs, n) -> toRow(rs)).optional()
            .map(row -> row.toLesson(attendees(tenantId, List.of(row.id())).getOrDefault(row.id(), List.of())));
    }

    @Override
    public List<Lesson> inCourses(UUID tenantId, Collection<UUID> courseIds, Instant from, Instant to) {
        if (courseIds.isEmpty()) {
            return List.of();
        }
        List<Row> rows = jdbc.sql("SELECT " + FIELDS + """
                 FROM calendar_lessons
                WHERE tenant_id = :tenantId AND course_id IN (:courseIds)
                  AND starts_at < :to AND COALESCE(ends_at, starts_at) >= :from
                ORDER BY starts_at, id
                """)
            .param("tenantId", tenantId).param("courseIds", courseIds)
            .param("from", Timestamps.of(from)).param("to", Timestamps.of(to))
            .query((rs, n) -> toRow(rs)).list();
        Map<UUID, List<UUID>> attendees = attendees(tenantId, rows.stream().map(Row::id).toList());
        return rows.stream().map(row -> row.toLesson(attendees.getOrDefault(row.id(), List.of()))).toList();
    }

    private void replaceAttendees(Lesson lesson) {
        jdbc.sql("DELETE FROM calendar_lesson_attendees WHERE tenant_id = :tenantId AND lesson_id = :lessonId")
            .param("tenantId", lesson.tenantId()).param("lessonId", lesson.id()).update();
        for (UUID userId : lesson.attendeeIds()) {
            jdbc.sql("""
                    INSERT INTO calendar_lesson_attendees (lesson_id, tenant_id, user_id) VALUES (:lessonId, :tenantId, :userId)
                    """)
                .param("lessonId", lesson.id()).param("tenantId", lesson.tenantId()).param("userId", userId).update();
        }
    }

    private Map<UUID, List<UUID>> attendees(UUID tenantId, List<UUID> lessonIds) {
        if (lessonIds.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql("""
                SELECT lesson_id, user_id FROM calendar_lesson_attendees
                WHERE tenant_id = :tenantId AND lesson_id IN (:lessonIds)
                """)
            .param("tenantId", tenantId).param("lessonIds", lessonIds)
            .query((rs, n) -> Map.entry(rs.getObject("lesson_id", UUID.class), rs.getObject("user_id", UUID.class)))
            .list()
            .stream()
            .collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    private static Map<String, Object> mutableParams(Lesson lesson) {
        Map<String, Object> params = new HashMap<>();
        params.put("moduleId", lesson.moduleId());
        params.put("itemId", lesson.itemId());
        params.put("title", lesson.title());
        params.put("description", lesson.description());
        params.put("startsAt", Timestamps.of(lesson.startsAt()));
        params.put("endsAt", Timestamps.of(lesson.endsAt()));
        params.put("audience", lesson.audience().key());
        params.put("version", lesson.version());
        params.put("updatedAt", Timestamps.of(lesson.updatedAt()));
        return params;
    }

    private static Row toRow(ResultSet rs) throws SQLException {
        return new Row(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("course_id", UUID.class), rs.getObject("module_id", UUID.class),
                rs.getObject("item_id", UUID.class), rs.getString("title"), rs.getString("description"),
                Timestamps.read(rs, "starts_at"), Timestamps.read(rs, "ends_at"),
                LessonAudience.fromKey(rs.getString("audience")).orElse(LessonAudience.COURSE),
                rs.getObject("created_by", UUID.class), rs.getLong("version"), Timestamps.read(rs, "created_at"),
                Timestamps.read(rs, "updated_at"));
    }

    private record Row(UUID id, UUID tenantId, UUID courseId, UUID moduleId, UUID itemId, String title, String description,
                       Instant startsAt, Instant endsAt, LessonAudience audience, UUID createdBy, long version,
                       Instant createdAt, Instant updatedAt) {

        Lesson toLesson(List<UUID> attendeeIds) {
            return new Lesson(id, tenantId, courseId, moduleId, itemId, title, description, startsAt, endsAt, audience,
                    attendeeIds, createdBy, version, createdAt, updatedAt);
        }
    }
}
