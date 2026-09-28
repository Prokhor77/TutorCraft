package com.tutorcraft.core.assessment.quiz.infrastructure;

import com.tutorcraft.core.courses.spi.CourseDataOwner;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Курс удерживают попытки тестов; исключения и расписание публикации оценок удаляются вместе с курсом. */
@Component
class QuizCourseData implements CourseDataOwner {

    private final JdbcClient jdbc;

    QuizCourseData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean retainsCourse(UUID tenantId, UUID courseId) {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM quiz_attempts WHERE tenant_id = :tenantId AND course_id = :courseId)
                """)
            .param("tenantId", tenantId).param("courseId", courseId)
            .query(Boolean.class).single();
    }

    @Override
    public void purgeCourse(UUID tenantId, UUID courseId) {
        jdbc.sql("DELETE FROM quiz_overrides WHERE tenant_id = :tenantId AND course_id = :courseId")
            .param("tenantId", tenantId).param("courseId", courseId).update();
        jdbc.sql("DELETE FROM quiz_grade_releases WHERE tenant_id = :tenantId AND course_id = :courseId")
            .param("tenantId", tenantId).param("courseId", courseId).update();
    }
}
