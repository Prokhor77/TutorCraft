package com.tutorcraft.core.gradebook.infrastructure;

import com.tutorcraft.core.courses.spi.CourseDataOwner;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Курс удерживают оценки (история оценок append-only, DATA-03); структура журнала удаляется вместе с курсом. */
@Component
class GradebookCourseData implements CourseDataOwner {

    /**
     * Порядок учитывает внешние ключи: столбцы → категории → настройки. Шкалы курса не удаляются: на них могут
     * ссылаться настройки других курсов (gradebook_settings.scale_id).
     */
    private static final List<String> STRUCTURE_TABLES = List.of("grade_items", "grade_categories", "gradebook_settings");

    private final JdbcClient jdbc;

    GradebookCourseData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean retainsCourse(UUID tenantId, UUID courseId) {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM grades g JOIN grade_items gi ON gi.id = g.grade_item_id
                        WHERE gi.tenant_id = :tenantId AND gi.course_id = :courseId)
                """)
            .param("tenantId", tenantId).param("courseId", courseId)
            .query(Boolean.class).single();
    }

    @Override
    public void purgeCourse(UUID tenantId, UUID courseId) {
        for (String table : STRUCTURE_TABLES) {
            jdbc.sql("DELETE FROM " + table + " WHERE tenant_id = :tenantId AND course_id = :courseId")
                .param("tenantId", tenantId).param("courseId", courseId).update();
        }
    }
}
