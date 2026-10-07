package com.tutorcraft.core.enrollment.infrastructure;

import com.tutorcraft.core.access.domain.CourseRole;
import com.tutorcraft.core.enrollment.application.EnrollmentRepository;
import com.tutorcraft.core.enrollment.domain.Enrollment;
import com.tutorcraft.core.enrollment.domain.EnrollmentStatus;
import com.tutorcraft.core.shared.api.CursorCodec.Position;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.persistence.Timestamps;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcEnrollmentRepository implements EnrollmentRepository {

    private static final String COLUMNS = """
            e.id, e.tenant_id, e.course_id, e.user_id, e.role_key, e.status, e.method, e.starts_at, e.ends_at,
            e.created_at, e.updated_at, e.last_access_at
            """;
    /** Запись даёт доступ сейчас (FR-ENROL-04). */
    private static final String ACTIVE_NOW = """
            e.status = 'active' AND (e.starts_at IS NULL OR e.starts_at <= :now) AND (e.ends_at IS NULL OR e.ends_at > :now)
            """;
    private static final String OVERRIDE_ON_CONFLICT = """
            ON CONFLICT (course_id, user_id) DO UPDATE SET role_key = EXCLUDED.role_key, status = 'active',
                starts_at = EXCLUDED.starts_at, ends_at = EXCLUDED.ends_at, updated_at = EXCLUDED.updated_at
            """;
    /** Активная запись не меняется; неактивная реактивируется с новой ролью, истёкший срок снимается. */
    private static final String REACTIVATE_ON_CONFLICT = """
            ON CONFLICT (course_id, user_id) DO UPDATE SET
                role_key = CASE WHEN enrollments.status = 'active' THEN enrollments.role_key ELSE EXCLUDED.role_key END,
                status = 'active',
                ends_at = CASE WHEN enrollments.ends_at <= EXCLUDED.updated_at THEN NULL ELSE enrollments.ends_at END,
                updated_at = EXCLUDED.updated_at
            """;
    private static final UUID NO_ID = new UUID(0L, 0L);

    private final JdbcClient jdbc;

    JdbcEnrollmentRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public UpsertResult upsert(Enrollment e, UpsertMode mode) {
        String onConflict = mode == UpsertMode.OVERRIDE ? OVERRIDE_ON_CONFLICT : REACTIVATE_ON_CONFLICT;
        return jdbc.sql("""
                INSERT INTO enrollments (id, tenant_id, course_id, user_id, role_key, status, method, starts_at, ends_at,
                                         created_at, updated_at)
                VALUES (:id, :tenantId, :courseId, :userId, :roleKey, 'active', :method, :startsAt, :endsAt, :now, :now)
                """ + onConflict + " RETURNING id, (xmax = 0) AS inserted, role_key")
            .param("id", e.id()).param("tenantId", e.tenantId()).param("courseId", e.courseId()).param("userId", e.userId())
            .param("roleKey", e.role().key()).param("method", e.method())
            .param("startsAt", Timestamps.of(e.startsAt())).param("endsAt", Timestamps.of(e.endsAt()))
            .param("now", Timestamps.of(e.createdAt()))
            .query((rs, n) -> new UpsertResult(rs.getObject("id", UUID.class), rs.getBoolean("inserted"), rs.getString("role_key")))
            .single();
    }

    @Override
    public Optional<Enrollment> find(UUID tenantId, UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM enrollments e WHERE e.tenant_id = :tenantId AND e.id = :id")
            .param("tenantId", tenantId).param("id", id)
            .query(JdbcEnrollmentRepository::map).optional();
    }

    @Override
    public Optional<Enrollment> findByCourseAndUser(UUID tenantId, UUID courseId, UUID userId) {
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM enrollments e WHERE e.tenant_id = :tenantId AND e.course_id = :courseId AND e.user_id = :userId
                """)
            .param("tenantId", tenantId).param("courseId", courseId).param("userId", userId)
            .query(JdbcEnrollmentRepository::map).optional();
    }

    /** q — подстрока имени/фамилии/email (users — справочник identity, только чтение). */
    @Override
    public List<Enrollment> list(UUID tenantId, UUID courseId, EnrollmentFilter filter, PageQuery page) {
        Optional<Position> after = page.after();
        String pattern = filter.q() == null ? null : "%" + escapeLike(filter.q()) + "%";
        return jdbc.sql("SELECT " + COLUMNS + """
                 FROM enrollments e
                 WHERE e.tenant_id = :tenantId AND e.course_id = :courseId
                   AND (CAST(:roleKey AS text) IS NULL OR e.role_key = :roleKey)
                   AND (CAST(:groupId AS uuid) IS NULL OR EXISTS (
                        SELECT 1 FROM course_group_members m JOIN course_groups g ON g.id = m.group_id
                        WHERE g.id = :groupId AND g.course_id = e.course_id AND m.user_id = e.user_id))
                   AND (CAST(:pattern AS text) IS NULL OR EXISTS (
                        SELECT 1 FROM users u WHERE u.id = e.user_id AND u.tenant_id = e.tenant_id
                          AND (u.first_name ILIKE :pattern OR u.last_name ILIKE :pattern OR u.email ILIKE :pattern
                               OR (u.first_name || ' ' || u.last_name) ILIKE :pattern)))
                   AND (CAST(:afterAt AS timestamptz) IS NULL
                        OR (e.created_at, e.id) < (CAST(:afterAt AS timestamptz), CAST(:afterId AS uuid)))
                 ORDER BY e.created_at DESC, e.id DESC
                 LIMIT :fetchSize
                """)
            .param("tenantId", tenantId).param("courseId", courseId).param("roleKey", filter.roleKey())
            .param("groupId", filter.groupId()).param("pattern", pattern)
            .param("afterAt", after.map(position -> Timestamps.of(position.sortKey())).orElse(null))
            .param("afterId", after.map(Position::id).orElse(null))
            .param("fetchSize", page.fetchSize())
            .query(JdbcEnrollmentRepository::map).list();
    }

    @Override
    public void update(Enrollment e, Instant now) {
        jdbc.sql("""
                UPDATE enrollments SET role_key = :roleKey, status = :status, starts_at = :startsAt, ends_at = :endsAt,
                    updated_at = :now
                WHERE tenant_id = :tenantId AND id = :id
                """)
            .param("roleKey", e.role().key()).param("status", e.status().key())
            .param("startsAt", Timestamps.of(e.startsAt())).param("endsAt", Timestamps.of(e.endsAt()))
            .param("now", Timestamps.of(now)).param("tenantId", e.tenantId()).param("id", e.id())
            .update();
    }

    @Override
    public void delete(UUID tenantId, UUID id) {
        jdbc.sql("DELETE FROM enrollments WHERE tenant_id = :tenantId AND id = :id")
            .param("tenantId", tenantId).param("id", id).update();
    }

    @Override
    public List<Enrollment> activeInCourse(UUID tenantId, UUID courseId, Set<CourseRole> roles, Instant now) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM enrollments e WHERE e.tenant_id = :tenantId AND e.course_id = :courseId"
                        + " AND (CAST(:anyRole AS boolean) OR e.role_key IN (:roleKeys)) AND " + ACTIVE_NOW
                        + " ORDER BY e.created_at, e.id")
            .param("tenantId", tenantId).param("courseId", courseId).param("now", Timestamps.of(now))
            .param("anyRole", roles == null || roles.isEmpty()).param("roleKeys", roleKeys(roles))
            .query(JdbcEnrollmentRepository::map).list();
    }

    @Override
    public List<UUID> activeCourseIds(UUID tenantId, UUID userId, Set<CourseRole> roles, Instant now) {
        return jdbc.sql("SELECT e.course_id FROM enrollments e WHERE e.tenant_id = :tenantId AND e.user_id = :userId"
                        + " AND (CAST(:anyRole AS boolean) OR e.role_key IN (:roleKeys)) AND " + ACTIVE_NOW)
            .param("tenantId", tenantId).param("userId", userId).param("now", Timestamps.of(now))
            .param("anyRole", roles == null || roles.isEmpty()).param("roleKeys", roleKeys(roles))
            .query(UUID.class).list();
    }

    @Override
    public Optional<String> activeRoleKey(UUID tenantId, UUID userId, UUID courseId, Instant now) {
        return jdbc.sql("SELECT e.role_key FROM enrollments e WHERE e.tenant_id = :tenantId AND e.user_id = :userId"
                        + " AND e.course_id = :courseId AND " + ACTIVE_NOW)
            .param("tenantId", tenantId).param("userId", userId).param("courseId", courseId).param("now", Timestamps.of(now))
            .query(String.class).optional();
    }

    @Override
    public Set<UUID> enrolledUserIds(UUID tenantId, UUID courseId, Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.sql("""
                SELECT user_id FROM enrollments WHERE tenant_id = :tenantId AND course_id = :courseId AND user_id IN (:userIds)
                """)
            .param("tenantId", tenantId).param("courseId", courseId).param("userIds", List.copyOf(Set.copyOf(userIds)))
            .query(UUID.class).list());
    }

    @Override
    public int countActiveByRole(UUID tenantId, UUID courseId, CourseRole role) {
        return jdbc.sql("""
                SELECT count(*) FROM enrollments
                WHERE tenant_id = :tenantId AND course_id = :courseId AND role_key = :roleKey AND status = 'active'
                """)
            .param("tenantId", tenantId).param("courseId", courseId).param("roleKey", role.key())
            .query(Integer.class).single();
    }

    @Override
    public void touchLastAccess(UUID tenantId, UUID courseId, UUID userId, Instant now) {
        jdbc.sql("""
                UPDATE enrollments SET last_access_at = :now
                WHERE tenant_id = :tenantId AND course_id = :courseId AND user_id = :userId
                """)
            .param("now", Timestamps.of(now)).param("tenantId", tenantId).param("courseId", courseId).param("userId", userId)
            .update();
    }

    /** Транзакционная advisory-блокировка по курсу (снимается при commit/rollback). */
    @Override
    public void lockCourse(UUID courseId) {
        long key = courseId.getMostSignificantBits() ^ courseId.getLeastSignificantBits();
        jdbc.sql("SELECT pg_advisory_xact_lock(:key)").param("key", key).query((rs, n) -> Boolean.TRUE).single();
    }

    @Override
    public List<RoleCount> countActiveByCourseAndRole(UUID tenantId, Collection<UUID> courseIds, Instant now) {
        if (courseIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT e.course_id, e.role_key, count(*) AS cnt FROM enrollments e"
                        + " WHERE e.tenant_id = :tenantId AND e.course_id IN (:courseIds) AND " + ACTIVE_NOW
                        + " GROUP BY e.course_id, e.role_key")
            .param("tenantId", tenantId).param("courseIds", courseIds).param("now", Timestamps.of(now))
            .query((rs, n) -> new RoleCount(rs.getObject("course_id", UUID.class),
                    CourseRole.fromKey(rs.getString("role_key")), rs.getInt("cnt")))
            .list();
    }

    private static List<String> roleKeys(Set<CourseRole> roles) {
        if (roles == null || roles.isEmpty()) {
            return List.of(NO_ID.toString());
        }
        return roles.stream().map(CourseRole::key).toList();
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Enrollment map(ResultSet rs, int rowNum) throws SQLException {
        return new Enrollment(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("course_id", UUID.class), rs.getObject("user_id", UUID.class),
                CourseRole.fromKey(rs.getString("role_key")), EnrollmentStatus.fromKey(rs.getString("status")),
                rs.getString("method"), Timestamps.read(rs, "starts_at"), Timestamps.read(rs, "ends_at"),
                Timestamps.read(rs, "created_at"), Timestamps.read(rs, "updated_at"), Timestamps.read(rs, "last_access_at"));
    }
}
