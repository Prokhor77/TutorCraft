package com.tutorcraft.core.activity.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.tutorcraft.core.activity.application.ActivityFilter;
import com.tutorcraft.core.activity.application.ActivityLogRepository;
import com.tutorcraft.core.activity.application.ActivityScope;
import com.tutorcraft.core.activity.application.ActivityViews.EntryView;
import com.tutorcraft.core.activity.application.ActivityViews.RouteErrors;
import com.tutorcraft.core.activity.application.ActivityViews.SummaryView;
import com.tutorcraft.core.activity.application.TrailQuery;
import com.tutorcraft.core.activity.domain.ActivityEntry;
import com.tutorcraft.core.shared.api.CursorCodec;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.persistence.JsonCodec;
import com.tutorcraft.core.shared.persistence.Timestamps;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcActivityLogRepository implements ActivityLogRepository {

    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() { };

    /** Видимость: выбранная школа или все школы и (для главного администратора по запросу) анонимные записи. */
    private static final String SCOPE = """
            (a.tenant_id = :tenantId OR (:allTenants AND a.tenant_id IS NOT NULL) OR (:anonymous AND a.tenant_id IS NULL))""";

    private static final String SELECT_LIST = """
            SELECT a.id, a.at, a.kind, a.tenant_id, t.name AS tenant_name, a.user_id,
                   u.first_name || ' ' || u.last_name AS actor_name,
                   u.email AS actor_email, a.ip, a.user_agent, a.request_id, a.session_id, a.page, a.method, a.route,
                   a.path, a.path_params::text AS path_params, a.handler, a.status, a.duration_ms, a.error_code,
                   a.error_type, a.error_message, %s AS error_stack
            FROM activity_log a LEFT JOIN users u ON u.id = a.user_id LEFT JOIN tenants t ON t.id = a.tenant_id
            """;

    private static final String INSERT = """
            INSERT INTO activity_log (id, at, kind, tenant_id, user_id, ip, user_agent, request_id, session_id, page,
                                      method, route, path, path_params, handler, status, duration_ms,
                                      error_code, error_type, error_message, error_stack)
            VALUES (:id, :at, :kind, :tenantId, :userId, :ip, :userAgent, :requestId, :sessionId, :page,
                    :method, :route, :path, :pathParams, :handler, :status, :durationMs,
                    :errorCode, :errorType, :errorMessage, :errorStack)
            """;

    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate batchJdbc;
    private final JsonCodec json;

    JdbcActivityLogRepository(JdbcClient jdbc, NamedParameterJdbcTemplate batchJdbc, JsonCodec json) {
        this.jdbc = jdbc;
        this.batchJdbc = batchJdbc;
        this.json = json;
    }

    @Override
    public void insertAll(List<ActivityEntry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        SqlParameterSource[] batch = entries.stream().map(this::insertParams).toArray(SqlParameterSource[]::new);
        batchJdbc.batchUpdate(INSERT, batch);
    }

    @Override
    public List<EntryView> search(ActivityScope scope, ActivityFilter filter, PageQuery page) {
        CursorCodec.Position after = page.after().orElse(null);
        String sql = SELECT_LIST.formatted("NULL") + """
                WHERE %s
                  AND (CAST(:userId AS uuid) IS NULL OR a.user_id = :userId)
                  AND (CAST(:actor AS text) IS NULL OR a.ip = :actor OR u.email ILIKE :actorLike
                       OR (u.first_name || ' ' || u.last_name) ILIKE :actorLike)
                  AND (CAST(:kind AS text) IS NULL OR a.kind = :kind)
                  AND (CAST(:outcome AS text) = 'all'
                       OR (CAST(:outcome AS text) = 'failed' AND (a.status >= 400 OR a.kind = 'client_error'))
                       OR (CAST(:outcome AS text) = 'errors' AND (a.status >= 500 OR a.kind = 'client_error')))
                  AND (CAST(:status AS integer) IS NULL OR a.status = :status)
                  AND (CAST(:route AS text) IS NULL OR a.route ILIKE :routeLike OR a.path ILIKE :routeLike
                       OR a.page ILIKE :routeLike)
                  AND (CAST(:requestId AS text) IS NULL OR a.request_id = :requestId)
                  AND (CAST(:sessionId AS text) IS NULL OR a.session_id = :sessionId)
                  AND (CAST(:from AS timestamptz) IS NULL OR a.at >= :from)
                  AND (CAST(:to AS timestamptz) IS NULL OR a.at < :to)
                  AND (CAST(:afterAt AS timestamptz) IS NULL OR (a.at, a.id) < (:afterAt, :afterId))
                ORDER BY a.at DESC, a.id DESC
                LIMIT :limit
                """.formatted(SCOPE);
        return jdbc.sql(sql)
            .params(scopeParams(scope))
            .param("userId", filter.userId())
            .param("actor", blankToNull(filter.actor()))
            .param("actorLike", likePattern(filter.actor()))
            .param("kind", filter.kind() == null ? null : filter.kind().key())
            .param("outcome", filter.outcomeOrAll().key())
            .param("status", filter.status())
            .param("route", blankToNull(filter.route()))
            .param("routeLike", likePattern(filter.route()))
            .param("requestId", blankToNull(filter.requestId()))
            .param("sessionId", blankToNull(filter.sessionId()))
            .param("from", Timestamps.of(filter.from()))
            .param("to", Timestamps.of(filter.to()))
            .param("afterAt", after == null ? null : Timestamps.of(after.sortKey()))
            .param("afterId", after == null ? null : after.id())
            .param("limit", page.fetchSize())
            .query(this::mapEntry)
            .list();
    }

    @Override
    public Optional<EntryView> find(ActivityScope scope, UUID id) {
        return jdbc.sql(SELECT_LIST.formatted("a.error_stack") + " WHERE " + SCOPE + " AND a.id = :id")
            .params(scopeParams(scope))
            .param("id", id)
            .query(this::mapEntry)
            .optional();
    }

    @Override
    public List<EntryView> trail(ActivityScope scope, TrailQuery query) {
        String sql = SELECT_LIST.formatted("NULL") + """
                WHERE %s
                  AND a.at >= :from AND a.at <= :to
                  AND (a.user_id = :userId OR a.session_id = :sessionId OR (a.user_id IS NULL AND a.ip = :ip))
                ORDER BY a.at ASC, a.id ASC
                LIMIT :limit
                """.formatted(SCOPE);
        return jdbc.sql(sql)
            .params(scopeParams(scope))
            .param("from", Timestamps.of(query.from()))
            .param("to", Timestamps.of(query.to()))
            .param("userId", query.userId())
            .param("sessionId", query.sessionId())
            .param("ip", query.ip())
            .param("limit", query.limit() + 1)
            .query(this::mapEntry)
            .list();
    }

    @Override
    public SummaryView summarize(ActivityScope scope, Instant from, Instant to, int topRoutes) {
        String totalsSql = """
                SELECT count(*) FILTER (WHERE a.kind = 'request') AS requests,
                       count(*) FILTER (WHERE a.kind = 'request' AND a.status >= 400) AS failed,
                       count(*) FILTER (WHERE a.status >= 500) AS server_errors,
                       count(*) FILTER (WHERE a.kind = 'client_error') AS client_errors,
                       count(DISTINCT a.user_id) AS active_users,
                       percentile_disc(0.95) WITHIN GROUP (ORDER BY a.duration_ms)
                           FILTER (WHERE a.kind = 'request')::bigint AS p95
                FROM activity_log a
                WHERE %s AND a.at >= :from AND a.at < :to
                """.formatted(SCOPE);
        return jdbc.sql(totalsSql)
            .params(scopeParams(scope))
            .param("from", Timestamps.of(from))
            .param("to", Timestamps.of(to))
            .query((rs, n) -> new SummaryView(from, to, rs.getLong("requests"), rs.getLong("failed"),
                    rs.getLong("server_errors"), rs.getLong("client_errors"), rs.getLong("active_users"),
                    rs.getObject("p95", Long.class), topErrorRoutes(scope, from, to, topRoutes)))
            .single();
    }

    @Override
    public int purgeBefore(Instant cutoff, int batchSize) {
        return jdbc.sql("""
                DELETE FROM activity_log
                WHERE id IN (SELECT id FROM activity_log WHERE at < :cutoff ORDER BY at LIMIT :batch)
                """)
            .param("cutoff", Timestamps.of(cutoff))
            .param("batch", batchSize)
            .update();
    }

    private List<RouteErrors> topErrorRoutes(ActivityScope scope, Instant from, Instant to, int limit) {
        String sql = """
                SELECT a.method, COALESCE(a.route, a.path, a.page) AS route, count(*) AS errors
                FROM activity_log a
                WHERE %s AND a.at >= :from AND a.at < :to AND (a.status >= 500 OR a.kind = 'client_error')
                GROUP BY a.method, COALESCE(a.route, a.path, a.page)
                ORDER BY errors DESC
                LIMIT :limit
                """.formatted(SCOPE);
        return jdbc.sql(sql)
            .params(scopeParams(scope))
            .param("from", Timestamps.of(from))
            .param("to", Timestamps.of(to))
            .param("limit", limit)
            .query((rs, n) -> new RouteErrors(rs.getString("method"), rs.getString("route"), rs.getLong("errors")))
            .list();
    }

    private static Map<String, Object> scopeParams(ActivityScope scope) {
        return Map.of("tenantId", scope.tenantId(), "anonymous", scope.includeAnonymous(), "allTenants",
                scope.allTenants());
    }

    private SqlParameterSource insertParams(ActivityEntry entry) {
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("id", entry.id())
            .addValue("at", Timestamps.of(entry.at()))
            .addValue("kind", entry.kind().key())
            .addValue("tenantId", entry.actor().tenantId())
            .addValue("userId", entry.actor().userId())
            .addValue("ip", entry.actor().ip())
            .addValue("userAgent", entry.actor().userAgent())
            .addValue("requestId", entry.correlation().requestId())
            .addValue("sessionId", entry.correlation().sessionId())
            .addValue("page", entry.correlation().page());
        addHttp(params, entry.http());
        addError(params, entry.error());
        return params;
    }

    private void addHttp(MapSqlParameterSource params, ActivityEntry.HttpDetails http) {
        boolean present = http != null;
        Map<String, String> pathParams = present ? http.pathParams() : null;
        params.addValue("method", present ? http.method() : null)
            .addValue("route", present ? http.route() : null)
            .addValue("path", present ? http.path() : null)
            .addValue("pathParams", pathParams == null || pathParams.isEmpty() ? null : json.toJsonb(pathParams))
            .addValue("handler", present ? http.handler() : null)
            .addValue("status", present ? http.status() : null)
            .addValue("durationMs", present ? http.durationMs() : null);
    }

    private static void addError(MapSqlParameterSource params, ActivityEntry.ErrorDetails error) {
        boolean present = error != null;
        params.addValue("errorCode", present ? error.code() : null)
            .addValue("errorType", present ? error.type() : null)
            .addValue("errorMessage", present ? error.message() : null)
            .addValue("errorStack", present ? error.stack() : null);
    }

    private EntryView mapEntry(ResultSet rs, int rowNum) throws SQLException {
        return new EntryView(rs.getObject("id", UUID.class), Timestamps.read(rs, "at"), rs.getString("kind"),
                rs.getObject("tenant_id", UUID.class), rs.getString("tenant_name"), rs.getObject("user_id", UUID.class),
                rs.getString("actor_name"), rs.getString("actor_email"), rs.getString("ip"),
                rs.getString("user_agent"), rs.getString("request_id"), rs.getString("session_id"),
                rs.getString("page"), rs.getString("method"), rs.getString("route"), rs.getString("path"),
                json.read(rs.getString("path_params"), STRING_MAP), rs.getString("handler"),
                rs.getObject("status", Integer.class), toLong(rs.getObject("duration_ms", Integer.class)),
                rs.getString("error_code"), rs.getString("error_type"), rs.getString("error_message"),
                rs.getString("error_stack"));
    }

    private static Long toLong(Integer value) {
        return value == null ? null : value.longValue();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** Подстрока для ILIKE: спецсимволы шаблона экранируются (ESCAPE по умолчанию — обратная косая черта). */
    private static String likePattern(String value) {
        String trimmed = blankToNull(value);
        if (trimmed == null) {
            return null;
        }
        String escaped = trimmed.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
