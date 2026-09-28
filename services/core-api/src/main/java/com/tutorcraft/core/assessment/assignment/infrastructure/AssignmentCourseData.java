package com.tutorcraft.core.assessment.assignment.infrastructure;

import com.tutorcraft.core.courses.spi.CourseDataOwner;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Курс удерживают сдачи заданий; продления сроков (item_overrides) удаляются вместе с курсом. */
@Component
class AssignmentCourseData implements CourseDataOwner {

    private final JdbcClient jdbc;

    AssignmentCourseData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean retainsCourse(UUID tenantId, UUID courseId) {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM submissions WHERE tenant_id = :tenantId AND course_id = :courseId)
                """)
            .param("tenantId", tenantId).param("courseId", courseId)
            .query(Boolean.class).single();
    }

    @Override
    public void purgeCourse(UUID tenantId, UUID courseId) {
        jdbc.sql("DELETE FROM item_overrides WHERE tenant_id = :tenantId AND course_id = :courseId")
            .param("tenantId", tenantId).param("courseId", courseId).update();
    }
}
