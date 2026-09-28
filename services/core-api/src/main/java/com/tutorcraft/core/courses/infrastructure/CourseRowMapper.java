package com.tutorcraft.core.courses.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.tutorcraft.core.courses.Visibility;
import com.tutorcraft.core.courses.domain.Course;
import com.tutorcraft.core.courses.domain.CourseCompletionRule;
import com.tutorcraft.core.courses.domain.GroupMode;
import com.tutorcraft.core.courses.domain.SelfEnrolSettings;
import com.tutorcraft.core.shared.domain.Money;
import com.tutorcraft.core.shared.persistence.JsonCodec;
import com.tutorcraft.core.shared.persistence.Timestamps;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Component;

/** Строка таблицы courses ↔ Course (jsonb-поля — через JsonCodec). */
@Component
class CourseRowMapper implements RowMapper<Course> {

    private static final TypeReference<Map<String, Object>> DOC_TYPE = new TypeReference<>() {
    };

    private final JsonCodec json;

    CourseRowMapper(JsonCodec json) {
        this.json = json;
    }

    @Override
    public Course mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new Course(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("category_id", UUID.class), rs.getString("title"), rs.getString("short_name"),
                rs.getString("slug"), json.read(rs.getString("description"), DOC_TYPE), rs.getObject("cover_file_id", UUID.class),
                Timestamps.read(rs, "starts_at"), Timestamps.read(rs, "ends_at"), Visibility.fromKey(rs.getString("visibility")),
                Timestamps.read(rs, "publish_at"), json.read(rs.getString("self_enrol"), SelfEnrolSettings.class), price(rs),
                json.read(rs.getString("completion_rule"), CourseCompletionRule.class), GroupMode.fromKey(rs.getString("group_mode")),
                rs.getObject("created_by", UUID.class), rs.getLong("version"), Timestamps.read(rs, "created_at"),
                Timestamps.read(rs, "updated_at"), Timestamps.read(rs, "deleted_at"));
    }

    /** Параметры изменяемых полей курса (общие для INSERT и UPDATE). */
    StatementSpec bindAll(StatementSpec spec, Course course) {
        Money price = course.price();
        return spec.param("id", course.id()).param("tenantId", course.tenantId()).param("categoryId", course.categoryId())
            .param("title", course.title()).param("shortName", course.shortName()).param("slug", course.slug())
            .param("description", json.toJsonb(course.description())).param("coverFileId", course.coverFileId())
            .param("startsAt", Timestamps.of(course.startsAt())).param("endsAt", Timestamps.of(course.endsAt()))
            .param("visibility", course.visibility().key()).param("publishAt", Timestamps.of(course.publishAt()))
            .param("selfEnrol", json.toJsonb(course.selfEnrol()))
            .param("priceAmountMinor", price == null ? null : price.amountMinor())
            .param("priceCurrency", price == null ? null : price.currency())
            .param("completionRule", json.toJsonb(course.completionRule())).param("groupMode", course.groupMode().key());
    }

    private static Money price(ResultSet rs) throws SQLException {
        long amount = rs.getLong("price_amount_minor");
        if (rs.wasNull()) {
            return null;
        }
        return new Money(amount, rs.getString("price_currency"));
    }
}
