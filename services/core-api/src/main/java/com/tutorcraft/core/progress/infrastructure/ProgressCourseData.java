package com.tutorcraft.core.progress.infrastructure;

import com.tutorcraft.core.courses.spi.CourseDataOwner;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Курс удерживают состояния выполнения и завершения курса учащимися; отметки просмотров удаляются вместе с курсом. */
@Component
class ProgressCourseData implements CourseDataOwner {

    private final JdbcClient jdbc;

    ProgressCourseData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean retainsCourse(UUID tenantId, UUID courseId) {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM completion_states WHERE tenant_id = :tenantId AND course_id = :courseId)
                    OR EXISTS (SELECT 1 FROM course_completions WHERE tenant_id = :tenantId AND course_id = :courseId)
                """)
            .param("tenantId", tenantId).param("courseId", courseId)
            .query(Boolean.class).single();
    }

    @Override
    public void purgeCourse(UUID tenantId, UUID courseId) {
        jdbc.sql("DELETE FROM item_views WHERE tenant_id = :tenantId AND course_id = :courseId")
            .param("tenantId", tenantId).param("courseId", courseId).update();
    }
}
