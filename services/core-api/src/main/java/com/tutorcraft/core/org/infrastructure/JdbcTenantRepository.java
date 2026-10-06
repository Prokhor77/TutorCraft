package com.tutorcraft.core.org.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.tutorcraft.core.org.OrgApi;
import com.tutorcraft.core.org.OrgApi.PasswordPolicy;
import com.tutorcraft.core.org.OrgApi.TenantInfo;
import com.tutorcraft.core.org.application.TenantRepository;
import com.tutorcraft.core.shared.persistence.JsonCodec;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcTenantRepository implements TenantRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };
    private static final String COLUMNS = """
            id, slug, name, status, default_locale, default_timezone, logo_file_id, primary_color,
            password_policy::text AS password_policy, embed_whitelist::text AS embed_whitelist, version
            """;

    private final JdbcClient jdbc;
    private final JsonCodec json;
    private final Clock clock;

    JdbcTenantRepository(JdbcClient jdbc, JsonCodec json, Clock clock) {
        this.jdbc = jdbc;
        this.json = json;
        this.clock = clock;
    }

    @Override
    public boolean slugExists(String slug) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM tenants WHERE slug = :slug)").param("slug", slug)
                .query(Boolean.class).single();
    }

    @Override
    public void insert(UUID id, String slug, String name) {
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.sql("INSERT INTO tenants (id, slug, name, created_at, updated_at) VALUES (:id, :slug, :name, :now, :now)")
            .param("id", id).param("slug", slug).param("name", name).param("now", now).update();
    }

    @Override
    public Optional<TenantInfo> findById(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM tenants WHERE id = :id").param("id", id)
                .query((rs, n) -> toInfo(rs)).optional();
    }

    @Override
    public Optional<TenantInfo> findBySlug(String slug) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM tenants WHERE slug = :slug").param("slug", slug)
                .query((rs, n) -> toInfo(rs)).optional();
    }

    @Override
    public List<TenantSummary> list(String query, int limit) {
        String pattern = query == null || query.isBlank() ? null : "%" + query.trim().toLowerCase() + "%";
        return jdbc.sql("""
                SELECT t.id, t.slug, t.name, t.status, t.created_at, t.quota_storage_mb,
                       (SELECT count(*) FROM users u WHERE u.tenant_id = t.id) AS users_count
                FROM tenants t
                WHERE t.slug <> :platformSlug
                  AND (CAST(:pattern AS TEXT) IS NULL OR lower(t.name) LIKE :pattern OR t.slug LIKE :pattern)
                ORDER BY t.created_at DESC, t.id
                LIMIT :limit
                """)
            .param("platformSlug", OrgApi.PLATFORM_TENANT_SLUG).param("pattern", pattern).param("limit", limit)
            .query((rs, n) -> new TenantSummary(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("name"),
                    rs.getString("status"), rs.getTimestamp("created_at").toInstant(), rs.getLong("users_count"),
                    rs.getObject("quota_storage_mb", Long.class)))
            .list();
    }

    @Override
    public Optional<TenantSettingsView> settings(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM tenants WHERE id = :id").param("id", id)
                .query((rs, n) -> new TenantSettingsView(rs.getObject("id", UUID.class), rs.getString("slug"),
                        rs.getString("name"), rs.getObject("logo_file_id", UUID.class), rs.getString("primary_color"),
                        rs.getString("default_locale"), rs.getString("default_timezone"),
                        json.read(rs.getString("password_policy"), PasswordPolicy.class),
                        json.read(rs.getString("embed_whitelist"), STRING_LIST), rs.getLong("version")))
                .optional();
    }

    @Override
    public Set<String> embedWhitelist(UUID id) {
        return jdbc.sql("SELECT embed_whitelist::text AS embed_whitelist FROM tenants WHERE id = :id")
            .param("id", id)
            .query((rs, rowNum) -> json.read(rs.getString("embed_whitelist"), STRING_LIST))
            .optional()
            .<Set<String>>map(Set::copyOf)
            .orElse(Set.of());
    }

    @Override
    public Optional<Long> storageQuotaMb(UUID id) {
        return jdbc.sql("SELECT quota_storage_mb FROM tenants WHERE id = :id AND quota_storage_mb IS NOT NULL")
            .param("id", id)
            .query((rs, rowNum) -> rs.getLong("quota_storage_mb"))
            .optional();
    }

    @Override
    public boolean updateSettings(UUID id, long expectedVersion, TenantSettingsUpdate update) {
        return jdbc.sql("""
                UPDATE tenants SET name = :name, logo_file_id = :logo, primary_color = :color, default_locale = :locale,
                       default_timezone = :timezone, password_policy = :policy, embed_whitelist = :whitelist,
                       version = version + 1, updated_at = :now
                WHERE id = :id AND version = :version
                """)
            .param("name", update.name()).param("logo", update.logoFileId()).param("color", update.primaryColor())
            .param("locale", update.defaultLocale()).param("timezone", update.defaultTimezone())
            .param("policy", json.toJsonb(update.passwordPolicy()))
            .param("whitelist", json.toJsonb(update.embedWhitelist() == null ? List.of() : update.embedWhitelist()))
            .param("now", Timestamp.from(clock.instant())).param("id", id).param("version", expectedVersion)
            .update() == 1;
    }

    private TenantInfo toInfo(ResultSet rs) throws SQLException {
        return new TenantInfo(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("name"),
                rs.getString("status"), rs.getString("default_locale"), rs.getString("default_timezone"),
                rs.getObject("logo_file_id", UUID.class), rs.getString("primary_color"),
                json.read(rs.getString("password_policy"), PasswordPolicy.class));
    }
}
