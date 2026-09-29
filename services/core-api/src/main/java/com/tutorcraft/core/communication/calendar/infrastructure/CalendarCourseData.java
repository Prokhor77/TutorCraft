package com.tutorcraft.core.communication.calendar.infrastructure;

import com.tutorcraft.core.courses.spi.CourseDataOwner;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Занятия курса не удерживают его в корзине и удаляются вместе с ним (участники — каскадом). */
@Component
class CalendarCourseData implements CourseDataOwner {

    private final JdbcClient jdbc;

    CalendarCourseData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean retainsCourse(UUID tenantId, UUID courseId) {
        return false;
    }

    @Override
    public void purgeCourse(UUID tenantId, UUID courseId) {
        jdbc.sql("DELETE FROM calendar_lessons WHERE tenant_id = :tenantId AND course_id = :courseId")
            .param("tenantId", tenantId).param("courseId", courseId).update();
    }
}
