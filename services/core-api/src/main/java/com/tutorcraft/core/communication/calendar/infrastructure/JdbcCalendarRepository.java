package com.tutorcraft.core.communication.calendar.infrastructure;

import com.tutorcraft.core.communication.calendar.application.CalendarRepository;
import com.tutorcraft.core.shared.persistence.Timestamps;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCalendarRepository implements CalendarRepository {

    private static final String FIELDS =
            "id, tenant_id, user_id, title, description, all_day, starts_at, ends_at, created_at, updated_at";

    private final JdbcClient jdbc;

    JdbcCalendarRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertEvent(PersonalEvent event) {
        jdbc.sql("""
                INSERT INTO calendar_personal_events (id, tenant_id, user_id, title, description, all_day, starts_at, ends_at,
                    created_at, updated_at)
                VALUES (:id, :tenantId, :userId, :title, :description, :allDay, :startsAt, :endsAt, :createdAt, :updatedAt)
                """)
            .param("id", event.id()).param("tenantId", event.tenantId()).param("userId", event.userId())
            .param("title", event.title()).param("description", event.description()).param("allDay", event.allDay())
            .param("startsAt", Timestamps.of(event.startsAt()))
            .param("endsAt", Timestamps.of(event.endsAt())).param("createdAt", Timestamps.of(event.createdAt()))
            .param("updatedAt", Timestamps.of(event.updatedAt()))
            .update();
    }

    @Override
    public Optional<PersonalEvent> findEvent(UUID tenantId, UUID userId, UUID eventId) {
        return jdbc.sql("SELECT " + FIELDS + """
                 FROM calendar_personal_events WHERE tenant_id = :tenantId AND user_id = :userId AND id = :id
                """)
            .param("tenantId", tenantId).param("userId", userId).param("id", eventId)
            .query((rs, n) -> toEvent(rs)).optional();
    }

    @Override
    public void updateEvent(PersonalEvent event) {
        jdbc.sql("""
                UPDATE calendar_personal_events SET title = :title, description = :description, all_day = :allDay,
                    starts_at = :startsAt, ends_at = :endsAt, updated_at = :updatedAt
                WHERE tenant_id = :tenantId AND user_id = :userId AND id = :id
                """)
            .param("title", event.title()).param("description", event.description()).param("allDay", event.allDay())
            .param("startsAt", Timestamps.of(event.startsAt()))
            .param("endsAt", Timestamps.of(event.endsAt())).param("updatedAt", Timestamps.of(event.updatedAt()))
            .param("tenantId", event.tenantId()).param("userId", event.userId()).param("id", event.id())
            .update();
    }

    @Override
    public void deleteEvent(UUID tenantId, UUID userId, UUID eventId) {
        jdbc.sql("DELETE FROM calendar_personal_events WHERE tenant_id = :tenantId AND user_id = :userId AND id = :id")
            .param("tenantId", tenantId).param("userId", userId).param("id", eventId).update();
    }

    @Override
    public List<PersonalEvent> events(UUID tenantId, UUID userId, Instant from, Instant to) {
        return jdbc.sql("SELECT " + FIELDS + """
                 FROM calendar_personal_events
                WHERE tenant_id = :tenantId AND user_id = :userId AND starts_at < :to AND COALESCE(ends_at, starts_at) >= :from
                ORDER BY starts_at, id
                """)
            .param("tenantId", tenantId).param("userId", userId)
            .param("from", Timestamps.of(from)).param("to", Timestamps.of(to))
            .query((rs, n) -> toEvent(rs)).list();
    }

    @Override
    public void replaceIcalToken(UUID tenantId, UUID userId, String tokenHash, Instant now) {
        jdbc.sql("""
                INSERT INTO ical_tokens (user_id, tenant_id, token_hash, created_at) VALUES (:userId, :tenantId, :hash, :now)
                ON CONFLICT (user_id) DO UPDATE SET token_hash = EXCLUDED.token_hash, created_at = EXCLUDED.created_at
                """)
            .param("userId", userId).param("tenantId", tenantId).param("hash", tokenHash).param("now", Timestamps.of(now))
            .update();
    }

    /** Поиск по хешу секрета из публичной ссылки — единственный запрос без tenant: tenant определяется токеном. */
    @Override
    public Optional<IcalOwner> findIcalOwner(String tokenHash) {
        return jdbc.sql("SELECT tenant_id, user_id FROM ical_tokens WHERE token_hash = :hash")
                .param("hash", tokenHash)
                .query((rs, n) -> new IcalOwner(rs.getObject("tenant_id", UUID.class), rs.getObject("user_id", UUID.class)))
                .optional();
    }

    private static PersonalEvent toEvent(ResultSet rs) throws SQLException {
        return new PersonalEvent(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("user_id", UUID.class), rs.getString("title"), rs.getString("description"),
                rs.getBoolean("all_day"), Timestamps.read(rs, "starts_at"),
                Timestamps.read(rs, "ends_at"), Timestamps.read(rs, "created_at"), Timestamps.read(rs, "updated_at"));
    }
}
