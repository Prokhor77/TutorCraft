package com.tutorcraft.core.courses.infrastructure;

import com.tutorcraft.core.courses.application.CourseRepository;
import com.tutorcraft.core.courses.application.CoursesErrors;
import com.tutorcraft.core.courses.domain.Course;
import com.tutorcraft.core.shared.api.CursorCodec.Position;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.domain.ConflictException;
import com.tutorcraft.core.shared.persistence.Timestamps;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Repository;

@Repository
class JdbcCourseRepository implements CourseRepository {

    private static final Logger log = LoggerFactory.getLogger(JdbcCourseRepository.class);
    private static final String SELECT = """
            SELECT id, tenant_id, category_id, title, short_name, slug, description::text AS description, cover_file_id,
                   starts_at, ends_at, visibility, publish_at, self_enrol::text AS self_enrol, price_amount_minor,
                   price_currency, completion_rule::text AS completion_rule, group_mode, created_by, version,
                   created_at, updated_at, deleted_at
            FROM courses
            """;
    /** Курс видим студентам сейчас (FR-COURSE-04). */
    private static final String VISIBLE_NOW = "(visibility = 'published' OR (visibility = 'scheduled' AND publish_at <= :now))";
    /** Область видимости списка (см. CourseScope); пустые списки заменяются несуществующим id. */
    private static final String SCOPE = """
            (CAST(:all AS boolean) OR id IN (:staffIds) OR category_id IN (:categoryIds)
             OR (id IN (:learnerIds) AND %s))
            """.formatted(VISIBLE_NOW);
    private static final UUID NO_ID = new UUID(0L, 0L);
    private static final int MAX_PUBLIC_CATALOG = 500;
    private static final int PURGE_BATCH = 1000;
    private static final String SHORT_NAME_INDEX = "courses_tenant_short_name_uq";

    private final JdbcClient jdbc;
    private final CourseRowMapper rows;

    JdbcCourseRepository(JdbcClient jdbc, CourseRowMapper rows) {
        this.jdbc = jdbc;
        this.rows = rows;
    }

    @Override
    public void insert(Course course) {
        try {
            rows.bindAll(jdbc.sql("""
                    INSERT INTO courses (id, tenant_id, category_id, title, short_name, slug, description, cover_file_id,
                                         starts_at, ends_at, visibility, publish_at, self_enrol, price_amount_minor,
                                         price_currency, completion_rule, group_mode, created_by, version, created_at, updated_at)
                    VALUES (:id, :tenantId, :categoryId, :title, :shortName, :slug, :description, :coverFileId,
                            :startsAt, :endsAt, :visibility, :publishAt, :selfEnrol, :priceAmountMinor,
                            :priceCurrency, :completionRule, :groupMode, :createdBy, 0, :createdAt, :createdAt)
                    """), course)
                .param("createdBy", course.createdBy()).param("createdAt", Timestamps.of(course.createdAt()))
                .update();
        } catch (DuplicateKeyException e) {
            throw duplicate(e);
        }
    }

    @Override
    public boolean update(Course course, long expectedVersion, Instant now) {
        try {
            return rows.bindAll(jdbc.sql("""
                    UPDATE courses SET category_id = :categoryId, title = :title, short_name = :shortName, slug = :slug,
                        description = :description, cover_file_id = :coverFileId, starts_at = :startsAt, ends_at = :endsAt,
                        visibility = :visibility, publish_at = :publishAt, self_enrol = :selfEnrol,
                        price_amount_minor = :priceAmountMinor, price_currency = :priceCurrency,
                        completion_rule = :completionRule, group_mode = :groupMode,
                        version = version + 1, updated_at = :now
                    WHERE tenant_id = :tenantId AND id = :id AND version = :expected AND deleted_at IS NULL
                    """), course)
                .param("expected", expectedVersion).param("now", Timestamps.of(now))
                .update() == 1;
        } catch (DuplicateKeyException e) {
            throw duplicate(e);
        }
    }

    @Override
    public Optional<Course> find(UUID tenantId, UUID id) {
        return jdbc.sql(SELECT + " WHERE tenant_id = :tenantId AND id = :id AND deleted_at IS NULL")
            .param("tenantId", tenantId).param("id", id)
            .query(rows).optional();
    }

    @Override
    public Optional<Course> findIncludingDeleted(UUID tenantId, UUID id) {
        return jdbc.sql(SELECT + " WHERE tenant_id = :tenantId AND id = :id")
            .param("tenantId", tenantId).param("id", id)
            .query(rows).optional();
    }

    @Override
    public Map<UUID, Course> findAll(UUID tenantId, Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql(SELECT + " WHERE tenant_id = :tenantId AND id IN (:ids) AND deleted_at IS NULL")
            .param("tenantId", tenantId).param("ids", List.copyOf(Set.copyOf(ids)))
            .query(rows).list().stream()
            .collect(Collectors.toMap(Course::id, Function.identity()));
    }

    @Override
    public Optional<Course> findByShortName(UUID tenantId, String shortName) {
        return jdbc.sql(SELECT + " WHERE tenant_id = :tenantId AND lower(short_name) = lower(:shortName) AND deleted_at IS NULL")
            .param("tenantId", tenantId).param("shortName", shortName)
            .query(rows).optional();
    }

    @Override
    public Optional<Course> findBySlug(UUID tenantId, String slug) {
        return jdbc.sql(SELECT + " WHERE tenant_id = :tenantId AND slug = :slug AND deleted_at IS NULL")
            .param("tenantId", tenantId).param("slug", slug)
            .query(rows).optional();
    }

    @Override
    public Set<String> slugsStartingWith(UUID tenantId, String base) {
        return new HashSet<>(jdbc.sql("SELECT slug FROM courses WHERE tenant_id = :tenantId AND (slug = :base OR slug LIKE :prefix)")
            .param("tenantId", tenantId).param("base", base).param("prefix", base + "-%")
            .query(String.class).list());
    }

    @Override
    public boolean shortNameTaken(UUID tenantId, String shortName, UUID exceptCourseId) {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM courses WHERE tenant_id = :tenantId AND lower(short_name) = lower(:shortName)
                               AND id <> :exceptId)
                """)
            .param("tenantId", tenantId).param("shortName", shortName).param("exceptId", exceptCourseId)
            .query(Boolean.class).single();
    }

    @Override
    public Map<UUID, Integer> countByCategory(UUID tenantId) {
        Map<UUID, Integer> result = new HashMap<>();
        jdbc.sql("""
                SELECT category_id, count(*) AS total FROM courses
                WHERE tenant_id = :tenantId AND deleted_at IS NULL AND category_id IS NOT NULL
                GROUP BY category_id
                """)
            .param("tenantId", tenantId)
            .query((rs, n) -> Map.entry(rs.getObject("category_id", UUID.class), rs.getInt("total")))
            .list()
            .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }

    @Override
    public List<UUID> usedCategoryIds(UUID tenantId) {
        return jdbc.sql("""
                SELECT DISTINCT category_id FROM courses
                WHERE tenant_id = :tenantId AND category_id IS NOT NULL
                """)
            .param("tenantId", tenantId).query(UUID.class).list();
    }

    @Override
    public List<Course> list(UUID tenantId, CourseFilter filter, PageQuery page, Instant now) {
        Optional<Position> after = page.after();
        String pattern = filter.q() == null ? null : "%" + escapeLike(filter.q()) + "%";
        return bindScope(jdbc.sql(SELECT + """
                 WHERE tenant_id = :tenantId AND deleted_at IS NULL
                   AND (CAST(:categoryId AS uuid) IS NULL OR category_id = :categoryId)
                   AND (CAST(:pattern AS text) IS NULL OR title ILIKE :pattern OR short_name ILIKE :pattern)
                """ + " AND " + SCOPE + """
                   AND (CAST(:afterAt AS timestamptz) IS NULL
                        OR (created_at, id) < (CAST(:afterAt AS timestamptz), CAST(:afterId AS uuid)))
                 ORDER BY created_at DESC, id DESC
                 LIMIT :fetchSize
                """), filter.scope())
            .param("tenantId", tenantId).param("categoryId", filter.categoryId()).param("pattern", pattern)
            .param("now", Timestamps.of(now))
            .param("afterAt", after.map(position -> Timestamps.of(position.sortKey())).orElse(null))
            .param("afterId", after.map(Position::id).orElse(null))
            .param("fetchSize", page.fetchSize())
            .query(rows).list();
    }

    @Override
    public List<Course> published(UUID tenantId, Instant now) {
        return jdbc.sql(SELECT + " WHERE tenant_id = :tenantId AND deleted_at IS NULL AND " + VISIBLE_NOW
                        + " ORDER BY created_at DESC, id DESC LIMIT :limit")
            .param("tenantId", tenantId).param("now", Timestamps.of(now)).param("limit", MAX_PUBLIC_CATALOG)
            .query(rows).list();
    }

    @Override
    public void softDelete(UUID tenantId, UUID id, Instant now) {
        jdbc.sql("""
                UPDATE courses SET deleted_at = :now, updated_at = :now, version = version + 1
                WHERE tenant_id = :tenantId AND id = :id AND deleted_at IS NULL
                """)
            .param("now", Timestamps.of(now)).param("tenantId", tenantId).param("id", id).update();
    }

    @Override
    public void restore(UUID tenantId, UUID id, Instant now) {
        jdbc.sql("""
                UPDATE courses SET deleted_at = NULL, updated_at = :now, version = version + 1
                WHERE tenant_id = :tenantId AND id = :id AND deleted_at IS NOT NULL
                """)
            .param("now", Timestamps.of(now)).param("tenantId", tenantId).param("id", id).update();
    }

    @Override
    public List<Course> deletedSince(UUID tenantId, CourseScope scope, Instant since) {
        return bindScope(jdbc.sql(SELECT + " WHERE tenant_id = :tenantId AND deleted_at >= :since AND " + SCOPE
                        + " ORDER BY deleted_at DESC"), scope)
            .param("tenantId", tenantId).param("since", Timestamps.of(since)).param("now", Timestamps.of(since))
            .query(rows).list();
    }

    @Override
    public Optional<Optional<UUID>> categoryOf(UUID tenantId, UUID id) {
        return jdbc.sql("SELECT category_id FROM courses WHERE tenant_id = :tenantId AND id = :id AND deleted_at IS NULL")
            .param("tenantId", tenantId).param("id", id)
            .query((rs, n) -> Optional.ofNullable(rs.getObject("category_id", UUID.class)))
            .optional();
    }

    @Override
    public boolean existsInOtherTenant(UUID tenantId, UUID id) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM courses WHERE id = :id AND tenant_id <> :tenantId)")
            .param("id", id).param("tenantId", tenantId).query(Boolean.class).single();
    }

    @Override
    public List<TenantCourseId> deletedBefore(Instant cutoff) {
        return jdbc.sql("SELECT tenant_id, id FROM courses WHERE deleted_at < :cutoff ORDER BY deleted_at LIMIT :limit")
            .param("cutoff", Timestamps.of(cutoff)).param("limit", PURGE_BATCH)
            .query((rs, n) -> new TenantCourseId(rs.getObject("tenant_id", UUID.class), rs.getObject("id", UUID.class)))
            .list();
    }

    /** Вне транзакции (autocommit): нарушение внешнего ключа означает, что на курс ссылаются данные других модулей. */
    @Override
    public boolean hardDelete(UUID tenantId, UUID id) {
        try {
            return jdbc.sql("DELETE FROM courses WHERE tenant_id = :tenantId AND id = :id AND deleted_at IS NOT NULL")
                .param("tenantId", tenantId).param("id", id).update() == 1;
        } catch (DataIntegrityViolationException e) {
            log.info("Course {} kept in trash: referenced by other data", id);
            return false;
        }
    }

    private static StatementSpec bindScope(StatementSpec spec, CourseScope scope) {
        return spec.param("all", scope.all())
            .param("staffIds", nonEmpty(scope.staffCourseIds()))
            .param("learnerIds", nonEmpty(scope.learnerCourseIds()))
            .param("categoryIds", nonEmpty(scope.categoryIds()));
    }

    private static List<UUID> nonEmpty(Collection<UUID> ids) {
        return ids == null || ids.isEmpty() ? List.of(NO_ID) : List.copyOf(Set.copyOf(ids));
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static ConflictException duplicate(DuplicateKeyException e) {
        String message = String.valueOf(e.getMostSpecificCause().getMessage());
        return message.contains(SHORT_NAME_INDEX)
                ? new ConflictException(CoursesErrors.SHORT_NAME_TAKEN, "Short name is already used")
                : new ConflictException(CoursesErrors.SLUG_TAKEN, "Slug is already used");
    }
}
