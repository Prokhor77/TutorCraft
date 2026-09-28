package com.tutorcraft.core.enrollment.infrastructure;

import com.tutorcraft.core.courses.spi.CourseDataOwner;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Курс удерживают записи (в любом статусе); группы и ссылки-приглашения удаляются вместе с курсом (FK на courses). */
@Component
class EnrollmentCourseData implements CourseDataOwner {

    private final JdbcClient jdbc;

    EnrollmentCourseData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean retainsCourse(UUID tenantId, UUID courseId) {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM enrollments WHERE tenant_id = :tenantId AND course_id = :courseId)
                """)
            .param("tenantId", tenantId).param("courseId", courseId)
            .query(Boolean.class).single();
    }

    @Override
    public void purgeCourse(UUID tenantId, UUID courseId) {
        jdbc.sql("DELETE FROM course_groups WHERE tenant_id = :tenantId AND course_id = :courseId")
            .param("tenantId", tenantId).param("courseId", courseId).update();
        jdbc.sql("DELETE FROM course_invite_links WHERE tenant_id = :tenantId AND course_id = :courseId")
            .param("tenantId", tenantId).param("courseId", courseId).update();
    }
}
