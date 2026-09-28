package com.tutorcraft.core.identity.infrastructure;

import com.tutorcraft.core.access.domain.TenantRole;
import com.tutorcraft.core.identity.application.UserRepository;
import com.tutorcraft.core.identity.domain.UserAccount;
import com.tutorcraft.core.identity.domain.UserStatus;
import com.tutorcraft.core.shared.api.CursorCodec;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.persistence.Timestamps;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcUserRepository implements UserRepository {

    private static final String COLUMNS = """
            id, tenant_id, email, password_hash, first_name, last_name, avatar_file_id, timezone, locale, status,
            google_sub, telegram_user_id, telegram_chat_id, last_login_at, created_at, version
            """;
    private static final String SELECT_ACTIVE = "SELECT " + COLUMNS + " FROM users WHERE deleted_at IS NULL ";
    private static final String SUMMARY_SELECT = """
            SELECT u.id, u.email, u.first_name, u.last_name, u.status, u.last_login_at, u.created_at,
                   ARRAY(SELECT DISTINCT r.key FROM role_assignments ra JOIN roles r ON r.id = ra.role_id
                         WHERE ra.tenant_id = u.tenant_id AND ra.user_id = u.id ORDER BY r.key) AS role_keys
            FROM users u
            """;
    private static final String LIKE_WILDCARD = "%";

    private final JdbcClient jdbc;
    private final Clock clock;

    JdbcUserRepository(JdbcClient jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Override
    public Optional<UserAccount> findById(UUID tenantId, UUID userId) {
        return jdbc.sql(SELECT_ACTIVE + "AND tenant_id = :tenantId AND id = :id")
                .param("tenantId", tenantId).param("id", userId)
                .query((rs, n) -> toAccount(rs)).optional();
    }

    @Override
    public List<UserAccount> findAll(UUID tenantId, Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SELECT_ACTIVE + "AND tenant_id = :tenantId AND id IN (:ids)")
                .param("tenantId", tenantId).param("ids", List.copyOf(userIds))
                .query((rs, n) -> toAccount(rs)).list();
    }

    @Override
    public Optional<UserAccount> findByEmail(UUID tenantId, String normalizedEmail) {
        return jdbc.sql(SELECT_ACTIVE + "AND tenant_id = :tenantId AND lower(email) = :email")
                .param("tenantId", tenantId).param("email", normalizedEmail)
                .query((rs, n) -> toAccount(rs)).optional();
    }

    @Override
    public List<UserAccount> findAllByEmail(String normalizedEmail) {
        return jdbc.sql(SELECT_ACTIVE + "AND lower(email) = :email ORDER BY created_at")
                .param("email", normalizedEmail)
                .query((rs, n) -> toAccount(rs)).list();
    }

    @Override
    public Optional<UserAccount> findByGoogleSub(UUID tenantId, String googleSub) {
        return jdbc.sql(SELECT_ACTIVE + "AND tenant_id = :tenantId AND google_sub = :sub")
                .param("tenantId", tenantId).param("sub", googleSub)
                .query((rs, n) -> toAccount(rs)).optional();
    }

    @Override
    public List<UserAccount> findAllByGoogleSub(String googleSub) {
        return jdbc.sql(SELECT_ACTIVE + "AND google_sub = :sub ORDER BY created_at")
                .param("sub", googleSub)
                .query((rs, n) -> toAccount(rs)).list();
    }

    @Override
    public Optional<UserAccount> findByTelegramUserId(UUID tenantId, long telegramUserId) {
        return jdbc.sql(SELECT_ACTIVE + "AND tenant_id = :tenantId AND telegram_user_id = :tg")
                .param("tenantId", tenantId).param("tg", telegramUserId)
                .query((rs, n) -> toAccount(rs)).optional();
    }

    @Override
    public List<UserAccount> findAllByTelegramUserId(long telegramUserId) {
        return jdbc.sql(SELECT_ACTIVE + "AND telegram_user_id = :tg ORDER BY created_at")
                .param("tg", telegramUserId)
                .query((rs, n) -> toAccount(rs)).list();
    }

    @Override
    public Set<String> existingEmails(UUID tenantId, Collection<String> normalizedEmails) {
        if (normalizedEmails.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.sql("""
                SELECT lower(email) FROM users
                WHERE tenant_id = :tenantId AND deleted_at IS NULL AND lower(email) IN (:emails)
                """)
            .param("tenantId", tenantId).param("emails", List.copyOf(normalizedEmails))
            .query(String.class).list());
    }

    @Override
    public void insert(NewUser user) {
        jdbc.sql("""
                INSERT INTO users (id, tenant_id, email, password_hash, first_name, last_name, timezone, locale, status,
                                   google_sub, telegram_user_id, created_at, updated_at)
                VALUES (:id, :tenantId, :email, :hash, :firstName, :lastName, :timezone, :locale, :status,
                        :googleSub, :telegramUserId, :now, :now)
                """)
            .param("id", user.id()).param("tenantId", user.tenantId()).param("email", user.email())
            .param("hash", user.passwordHash()).param("firstName", user.firstName()).param("lastName", user.lastName())
            .param("timezone", user.timezone()).param("locale", user.locale()).param("status", user.status().key())
            .param("googleSub", user.googleSub()).param("telegramUserId", user.telegramUserId())
            .param("now", Timestamps.of(clock.instant()))
            .update();
    }

    @Override
    public void updatePassword(UUID tenantId, UUID userId, String passwordHash) {
        update("password_hash = :value", tenantId, userId, passwordHash);
    }

    @Override
    public void updateStatus(UUID tenantId, UUID userId, UserStatus status) {
        update("status = :value", tenantId, userId, status.key());
    }

    @Override
    public void linkGoogle(UUID tenantId, UUID userId, String googleSub) {
        update("google_sub = :value", tenantId, userId, googleSub);
    }

    @Override
    public void recordLogin(UUID tenantId, UUID userId, Instant at) {
        jdbc.sql("UPDATE users SET last_login_at = :at WHERE tenant_id = :tenantId AND id = :id")
            .param("at", Timestamps.of(at)).param("tenantId", tenantId).param("id", userId).update();
    }

    @Override
    public void updateProfile(UUID tenantId, UUID userId, ProfileUpdate update) {
        jdbc.sql("""
                UPDATE users SET first_name = :firstName, last_name = :lastName, timezone = :timezone, locale = :locale,
                       avatar_file_id = :avatar, version = version + 1, updated_at = :now
                WHERE tenant_id = :tenantId AND id = :id AND deleted_at IS NULL
                """)
            .param("firstName", update.firstName()).param("lastName", update.lastName())
            .param("timezone", update.timezone()).param("locale", update.locale()).param("avatar", update.avatarFileId())
            .param("now", Timestamps.of(clock.instant())).param("tenantId", tenantId).param("id", userId)
            .update();
    }

    @Override
    public void updateNames(UUID tenantId, UUID userId, String firstName, String lastName) {
        jdbc.sql("""
                UPDATE users SET first_name = :firstName, last_name = :lastName, version = version + 1, updated_at = :now
                WHERE tenant_id = :tenantId AND id = :id AND deleted_at IS NULL
                """)
            .param("firstName", firstName).param("lastName", lastName)
            .param("now", Timestamps.of(clock.instant())).param("tenantId", tenantId).param("id", userId)
            .update();
    }

    @Override
    public void activate(UUID tenantId, UUID userId, String passwordHash, String firstName, String lastName) {
        jdbc.sql("""
                UPDATE users SET password_hash = :hash, first_name = :firstName, last_name = :lastName, status = 'active',
                       version = version + 1, updated_at = :now
                WHERE tenant_id = :tenantId AND id = :id AND deleted_at IS NULL
                """)
            .param("hash", passwordHash).param("firstName", firstName).param("lastName", lastName)
            .param("now", Timestamps.of(clock.instant())).param("tenantId", tenantId).param("id", userId)
            .update();
    }

    @Override
    public void linkTelegram(UUID tenantId, UUID userId, Long telegramUserId, Long telegramChatId) {
        jdbc.sql("""
                UPDATE users SET telegram_user_id = COALESCE(CAST(:tg AS bigint), telegram_user_id),
                       telegram_chat_id = COALESCE(CAST(:chat AS bigint), telegram_chat_id),
                       version = version + 1, updated_at = :now
                WHERE tenant_id = :tenantId AND id = :id AND deleted_at IS NULL
                """)
            .param("tg", telegramUserId).param("chat", telegramChatId)
            .param("now", Timestamps.of(clock.instant())).param("tenantId", tenantId).param("id", userId)
            .update();
    }

    @Override
    public List<UserSummaryView> search(UUID tenantId, UserFilter filter, PageQuery page) {
        CursorCodec.Position after = page.after().orElse(null);
        return jdbc.sql(SUMMARY_SELECT + """
                WHERE u.tenant_id = :tenantId AND u.deleted_at IS NULL
                  AND (CAST(:status AS text) IS NULL OR u.status = :status)
                  AND (CAST(:q AS text) IS NULL OR u.email ILIKE :q OR u.first_name ILIKE :q OR u.last_name ILIKE :q
                       OR (u.first_name || ' ' || u.last_name) ILIKE :q)
                  AND (CAST(:role AS text) IS NULL OR EXISTS (
                        SELECT 1 FROM role_assignments ra JOIN roles r ON r.id = ra.role_id
                        WHERE ra.tenant_id = u.tenant_id AND ra.user_id = u.id AND r.key = :role))
                  AND (CAST(:afterAt AS timestamptz) IS NULL OR (u.created_at, u.id) < (:afterAt, :afterId))
                ORDER BY u.created_at DESC, u.id DESC
                LIMIT :limit
                """)
            .param("tenantId", tenantId)
            .param("status", filter.status())
            .param("q", likePattern(filter.query()))
            .param("role", filter.roleKey())
            .param("afterAt", after == null ? null : Timestamps.of(after.sortKey()))
            .param("afterId", after == null ? null : after.id())
            .param("limit", page.fetchSize())
            .query((rs, n) -> toSummary(rs))
            .list();
    }

    @Override
    public Optional<UserSummaryView> summary(UUID tenantId, UUID userId) {
        return jdbc.sql(SUMMARY_SELECT + "WHERE u.tenant_id = :tenantId AND u.id = :id AND u.deleted_at IS NULL")
                .param("tenantId", tenantId).param("id", userId)
                .query((rs, n) -> toSummary(rs)).optional();
    }

    private void update(String assignment, UUID tenantId, UUID userId, Object value) {
        jdbc.sql("UPDATE users SET " + assignment + ", version = version + 1, updated_at = :now "
                + "WHERE tenant_id = :tenantId AND id = :id AND deleted_at IS NULL")
            .param("value", value).param("now", Timestamps.of(clock.instant()))
            .param("tenantId", tenantId).param("id", userId)
            .update();
    }

    private static String likePattern(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String escaped = query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return LIKE_WILDCARD + escaped + LIKE_WILDCARD;
    }

    private static UserAccount toAccount(ResultSet rs) throws SQLException {
        return new UserAccount(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class), rs.getString("email"),
                rs.getString("password_hash"), rs.getString("first_name"), rs.getString("last_name"),
                rs.getObject("avatar_file_id", UUID.class), rs.getString("timezone"), rs.getString("locale"),
                UserStatus.fromKey(rs.getString("status")), rs.getString("google_sub"),
                rs.getObject("telegram_user_id", Long.class), rs.getObject("telegram_chat_id", Long.class),
                Timestamps.read(rs, "last_login_at"), Timestamps.read(rs, "created_at"), rs.getLong("version"));
    }

    private static UserSummaryView toSummary(ResultSet rs) throws SQLException {
        return new UserSummaryView(rs.getObject("id", UUID.class), rs.getString("email"), rs.getString("first_name"),
                rs.getString("last_name"), rs.getString("status"), tenantRoles(rs.getArray("role_keys")),
                Timestamps.read(rs, "last_login_at"), Timestamps.read(rs, "created_at"));
    }

    private static List<String> tenantRoles(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        return Arrays.stream((String[]) array.getArray())
                .filter(key -> TenantRole.find(key).isPresent())
                .toList();
    }
}
