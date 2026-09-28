package com.tutorcraft.core.communication.forum.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.tutorcraft.core.communication.forum.application.ForumRepository;
import com.tutorcraft.core.communication.forum.domain.Discussion;
import com.tutorcraft.core.communication.forum.domain.ForumPost;
import com.tutorcraft.core.shared.api.CursorCodec;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.api.PageResponse;
import com.tutorcraft.core.shared.persistence.JsonCodec;
import com.tutorcraft.core.shared.persistence.Timestamps;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcForumRepository implements ForumRepository {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() { };
    private static final String DISCUSSION_COLUMNS = """
            id, tenant_id, course_id, item_id, author_id, title, pinned, locked, reply_count, last_post_at, created_at
            """;
    private static final String POST_COLUMNS = """
            id, tenant_id, course_id, item_id, discussion_id, parent_id, depth, author_id, body::text AS body, hidden,
            created_at, edited_at
            """;

    private final JdbcClient jdbc;
    private final JsonCodec json;

    JdbcForumRepository(JdbcClient jdbc, JsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void insertDiscussion(Discussion d) {
        jdbc.sql("""
                INSERT INTO forum_discussions (id, tenant_id, course_id, item_id, author_id, title, pinned, locked, reply_count,
                                               last_post_at, created_at)
                VALUES (:id, :tenantId, :courseId, :itemId, :authorId, :title, :pinned, :locked, :replyCount, :lastPostAt,
                        :createdAt)
                """)
            .param("id", d.id()).param("tenantId", d.tenantId()).param("courseId", d.courseId()).param("itemId", d.itemId())
            .param("authorId", d.authorId()).param("title", d.title()).param("pinned", d.pinned()).param("locked", d.locked())
            .param("replyCount", d.replyCount()).param("lastPostAt", Timestamps.of(d.lastPostAt()))
            .param("createdAt", Timestamps.of(d.createdAt()))
            .update();
    }

    @Override
    public Optional<Discussion> findDiscussion(UUID tenantId, UUID discussionId) {
        return jdbc.sql("SELECT " + DISCUSSION_COLUMNS + """
                 FROM forum_discussions WHERE tenant_id = :tenantId AND id = :id AND deleted_at IS NULL
                """)
            .param("tenantId", tenantId).param("id", discussionId)
            .query((rs, n) -> toDiscussion(rs)).optional();
    }

    @Override
    public List<Discussion> pinned(UUID tenantId, UUID itemId) {
        return jdbc.sql("SELECT " + DISCUSSION_COLUMNS + """
                 FROM forum_discussions
                WHERE tenant_id = :tenantId AND item_id = :itemId AND pinned AND deleted_at IS NULL
                ORDER BY last_post_at DESC, id DESC
                """)
            .param("tenantId", tenantId).param("itemId", itemId)
            .query((rs, n) -> toDiscussion(rs)).list();
    }

    @Override
    public PageResponse<Discussion> pageUnpinned(UUID tenantId, UUID itemId, PageQuery page) {
        CursorCodec.Position after = page.after().orElse(null);
        List<Discussion> rows = jdbc.sql("SELECT " + DISCUSSION_COLUMNS + """
                 FROM forum_discussions
                WHERE tenant_id = :tenantId AND item_id = :itemId AND NOT pinned AND deleted_at IS NULL
                  AND (CAST(:afterAt AS timestamptz) IS NULL OR (last_post_at, id) < (:afterAt, :afterId))
                ORDER BY last_post_at DESC, id DESC
                LIMIT :limit
                """)
            .param("tenantId", tenantId).param("itemId", itemId)
            .param("afterAt", after == null ? null : Timestamps.of(after.sortKey()))
            .param("afterId", after == null ? null : after.id())
            .param("limit", page.fetchSize())
            .query((rs, n) -> toDiscussion(rs)).list();
        return page.toPage(rows, Discussion::lastPostAt, Discussion::id);
    }

    @Override
    public void setPinned(UUID tenantId, UUID discussionId, boolean pinned) {
        jdbc.sql("UPDATE forum_discussions SET pinned = :pinned WHERE tenant_id = :tenantId AND id = :id")
            .param("pinned", pinned).param("tenantId", tenantId).param("id", discussionId).update();
    }

    @Override
    public void setLocked(UUID tenantId, UUID discussionId, boolean locked) {
        jdbc.sql("UPDATE forum_discussions SET locked = :locked WHERE tenant_id = :tenantId AND id = :id")
            .param("locked", locked).param("tenantId", tenantId).param("id", discussionId).update();
    }

    @Override
    public void touchDiscussion(UUID tenantId, UUID discussionId, Instant lastPostAt, int replyDelta) {
        jdbc.sql("""
                UPDATE forum_discussions
                SET last_post_at = COALESCE(CAST(:lastPostAt AS timestamptz), last_post_at),
                    reply_count = GREATEST(reply_count + :delta, 0)
                WHERE tenant_id = :tenantId AND id = :id
                """)
            .param("lastPostAt", Timestamps.of(lastPostAt)).param("delta", replyDelta)
            .param("tenantId", tenantId).param("id", discussionId)
            .update();
    }

    @Override
    public void deleteDiscussion(UUID tenantId, UUID discussionId, Instant at) {
        jdbc.sql("UPDATE forum_discussions SET deleted_at = :at WHERE tenant_id = :tenantId AND id = :id AND deleted_at IS NULL")
            .param("at", Timestamps.of(at)).param("tenantId", tenantId).param("id", discussionId).update();
    }

    @Override
    public void insertPost(ForumPost p) {
        jdbc.sql("""
                INSERT INTO forum_posts (id, tenant_id, course_id, item_id, discussion_id, parent_id, depth, author_id, body,
                                         hidden, created_at)
                VALUES (:id, :tenantId, :courseId, :itemId, :discussionId, :parentId, :depth, :authorId, :body, :hidden,
                        :createdAt)
                """)
            .param("id", p.id()).param("tenantId", p.tenantId()).param("courseId", p.courseId()).param("itemId", p.itemId())
            .param("discussionId", p.discussionId()).param("parentId", p.parentId()).param("depth", p.depth())
            .param("authorId", p.authorId()).param("body", json.toJsonb(p.body())).param("hidden", p.hidden())
            .param("createdAt", Timestamps.of(p.createdAt()))
            .update();
    }

    @Override
    public Optional<ForumPost> findPost(UUID tenantId, UUID postId) {
        return jdbc.sql("SELECT " + POST_COLUMNS + " FROM forum_posts WHERE tenant_id = :tenantId AND id = :id AND deleted_at IS NULL")
                .param("tenantId", tenantId).param("id", postId)
                .query((rs, n) -> toPost(rs)).optional();
    }

    @Override
    public List<ForumPost> posts(UUID tenantId, UUID discussionId) {
        return jdbc.sql("SELECT " + POST_COLUMNS + """
                 FROM forum_posts WHERE tenant_id = :tenantId AND discussion_id = :discussionId AND deleted_at IS NULL
                ORDER BY created_at, id
                """)
            .param("tenantId", tenantId).param("discussionId", discussionId)
            .query((rs, n) -> toPost(rs)).list();
    }

    @Override
    public boolean hasReplies(UUID tenantId, UUID postId) {
        return jdbc.sql("""
                SELECT EXISTS (SELECT 1 FROM forum_posts WHERE tenant_id = :tenantId AND parent_id = :id AND deleted_at IS NULL)
                """)
            .param("tenantId", tenantId).param("id", postId)
            .query(Boolean.class).single();
    }

    @Override
    public void updateBody(UUID tenantId, UUID postId, Map<String, Object> body, Instant editedAt) {
        jdbc.sql("UPDATE forum_posts SET body = :body, edited_at = :at WHERE tenant_id = :tenantId AND id = :id")
            .param("body", json.toJsonb(body)).param("at", Timestamps.of(editedAt))
            .param("tenantId", tenantId).param("id", postId)
            .update();
    }

    @Override
    public void setHidden(UUID tenantId, UUID postId, boolean hidden) {
        jdbc.sql("UPDATE forum_posts SET hidden = :hidden WHERE tenant_id = :tenantId AND id = :id")
            .param("hidden", hidden).param("tenantId", tenantId).param("id", postId).update();
    }

    @Override
    public int deleteSubtree(UUID tenantId, UUID postId, Instant at) {
        return jdbc.sql("""
                WITH RECURSIVE subtree AS (
                    SELECT id FROM forum_posts WHERE tenant_id = :tenantId AND id = :id AND deleted_at IS NULL
                    UNION ALL
                    SELECT p.id FROM forum_posts p JOIN subtree s ON p.parent_id = s.id
                    WHERE p.tenant_id = :tenantId AND p.deleted_at IS NULL
                )
                UPDATE forum_posts SET deleted_at = :at WHERE id IN (SELECT id FROM subtree)
                """)
            .param("tenantId", tenantId).param("id", postId).param("at", Timestamps.of(at))
            .update();
    }

    @Override
    public void subscribe(UUID tenantId, UUID discussionId, UUID userId, Instant at) {
        jdbc.sql("""
                INSERT INTO forum_subscriptions (tenant_id, discussion_id, user_id, created_at)
                VALUES (:tenantId, :discussionId, :userId, :at) ON CONFLICT DO NOTHING
                """)
            .param("tenantId", tenantId).param("discussionId", discussionId).param("userId", userId)
            .param("at", Timestamps.of(at))
            .update();
    }

    @Override
    public void unsubscribe(UUID tenantId, UUID discussionId, UUID userId) {
        jdbc.sql("DELETE FROM forum_subscriptions WHERE tenant_id = :tenantId AND discussion_id = :discussionId AND user_id = :userId")
            .param("tenantId", tenantId).param("discussionId", discussionId).param("userId", userId)
            .update();
    }

    @Override
    public Set<UUID> subscribers(UUID tenantId, UUID discussionId) {
        return new HashSet<>(jdbc.sql("""
                SELECT user_id FROM forum_subscriptions WHERE tenant_id = :tenantId AND discussion_id = :discussionId
                """)
            .param("tenantId", tenantId).param("discussionId", discussionId)
            .query((rs, n) -> rs.getObject("user_id", UUID.class)).list());
    }

    @Override
    public void markRead(UUID tenantId, UUID discussionId, UUID userId, Instant at) {
        jdbc.sql("""
                INSERT INTO forum_reads (tenant_id, discussion_id, user_id, last_read_at)
                VALUES (:tenantId, :discussionId, :userId, :at)
                ON CONFLICT (tenant_id, discussion_id, user_id) DO UPDATE
                    SET last_read_at = GREATEST(forum_reads.last_read_at, EXCLUDED.last_read_at)
                """)
            .param("tenantId", tenantId).param("discussionId", discussionId).param("userId", userId)
            .param("at", Timestamps.of(at))
            .update();
    }

    @Override
    public Map<UUID, ReaderState> readerStates(UUID tenantId, UUID userId, Collection<UUID> discussionIds) {
        Map<UUID, ReaderState> states = new HashMap<>();
        if (discussionIds.isEmpty()) {
            return states;
        }
        jdbc.sql("""
                SELECT d.id,
                       EXISTS (SELECT 1 FROM forum_subscriptions s
                               WHERE s.tenant_id = d.tenant_id AND s.discussion_id = d.id AND s.user_id = :userId) AS subscribed,
                       (SELECT CAST(count(*) AS int) FROM forum_posts p
                        LEFT JOIN forum_reads r ON r.tenant_id = p.tenant_id AND r.discussion_id = p.discussion_id
                                               AND r.user_id = :userId
                        WHERE p.tenant_id = d.tenant_id AND p.discussion_id = d.id AND p.deleted_at IS NULL AND NOT p.hidden
                          AND p.author_id <> :userId AND (r.last_read_at IS NULL OR p.created_at > r.last_read_at)) AS unread
                FROM forum_discussions d
                WHERE d.tenant_id = :tenantId AND d.id IN (:ids)
                """)
            .param("tenantId", tenantId).param("userId", userId).param("ids", List.copyOf(discussionIds))
            .query((ResultSet rs) -> {
                states.put(rs.getObject("id", UUID.class), new ReaderState(rs.getBoolean("subscribed"), rs.getInt("unread")));
            });
        return states;
    }

    @Override
    public List<RecentPostRow> recentPosts(UUID tenantId, Collection<UUID> courseIds, int limit) {
        return jdbc.sql("""
                SELECT p.discussion_id, p.course_id, d.title, p.author_id, p.created_at
                FROM forum_posts p JOIN forum_discussions d ON d.id = p.discussion_id AND d.tenant_id = p.tenant_id
                WHERE p.tenant_id = :tenantId AND p.course_id IN (:courseIds) AND p.deleted_at IS NULL AND NOT p.hidden
                  AND d.deleted_at IS NULL
                ORDER BY p.created_at DESC
                LIMIT :limit
                """)
            .param("tenantId", tenantId).param("courseIds", List.copyOf(courseIds)).param("limit", limit)
            .query((rs, n) -> new RecentPostRow(rs.getObject("discussion_id", UUID.class), rs.getObject("course_id", UUID.class),
                    rs.getString("title"), rs.getObject("author_id", UUID.class), Timestamps.read(rs, "created_at")))
            .list();
    }

    private static Discussion toDiscussion(ResultSet rs) throws SQLException {
        return new Discussion(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("course_id", UUID.class), rs.getObject("item_id", UUID.class), rs.getObject("author_id", UUID.class),
                rs.getString("title"), rs.getBoolean("pinned"), rs.getBoolean("locked"), rs.getInt("reply_count"),
                Timestamps.read(rs, "last_post_at"), Timestamps.read(rs, "created_at"));
    }

    private ForumPost toPost(ResultSet rs) throws SQLException {
        return new ForumPost(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("course_id", UUID.class), rs.getObject("item_id", UUID.class),
                rs.getObject("discussion_id", UUID.class), rs.getObject("parent_id", UUID.class), rs.getInt("depth"),
                rs.getObject("author_id", UUID.class), json.read(rs.getString("body"), MAP), rs.getBoolean("hidden"),
                Timestamps.read(rs, "created_at"), Timestamps.read(rs, "edited_at"));
    }
}
