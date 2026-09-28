package com.tutorcraft.core.billing.infrastructure;

import com.tutorcraft.core.courses.spi.CourseDataOwner;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Курс удерживают заказы в любом статусе (финансовая отчётность). */
@Component
class BillingCourseData implements CourseDataOwner {

    private final JdbcClient jdbc;

    BillingCourseData(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean retainsCourse(UUID tenantId, UUID courseId) {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM orders WHERE tenant_id = :tenantId AND course_id = :courseId)
                """)
            .param("tenantId", tenantId).param("courseId", courseId)
            .query(Boolean.class).single();
    }
}
