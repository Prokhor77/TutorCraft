package com.tutorcraft.core.assessment.assignment.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.tutorcraft.core.assessment.assignment.application.SubmissionRepository;
import com.tutorcraft.core.assessment.assignment.domain.Feedback;
import com.tutorcraft.core.assessment.assignment.domain.Submission;
import com.tutorcraft.core.assessment.assignment.domain.SubmissionStatus;
import com.tutorcraft.core.shared.api.PageQuery;
import com.tutorcraft.core.shared.persistence.JsonCodec;
import com.tutorcraft.core.shared.persistence.Timestamps;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSubmissionRepository implements SubmissionRepository {

    private static final TypeReference<Map<String, Object>> DOC = new TypeReference<>() { };
    private static final TypeReference<List<UUID>> UUIDS = new TypeReference<>() { };
    private static final String SELECT = """
            SELECT s.id, s.tenant_id, s.course_id, s.item_id, s.user_id, s.group_id, s.owner_key, s.attempt_no, s.is_latest,
                   s.status, s.text::text AS text, s.submitted_at, s.due_at, s.late, s.version, s.created_at, s.updated_at,
                   COALESCE((SELECT json_agg(f.file_id ORDER BY f.position) FROM submission_files f
                             WHERE f.submission_id = s.id), '[]'::json)::text AS file_ids
            FROM submissions s
            """;
    private static final String MEMBER_OF = """
             EXISTS (SELECT 1 FROM submission_members m WHERE m.submission_id = s.id AND m.user_id = :userId)
            """;
    private static final String FEEDBACK_COLUMNS = """
            id, tenant_id, submission_id, grader_id, text::text AS text, file_ids::text AS file_ids, score,
            return_for_revision, published_at, created_at
            """;

    private final JdbcClient jdbc;
    private final JsonCodec json;

    JdbcSubmissionRepository(JdbcClient jdbc, JsonCodec json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public Optional<Submission> find(UUID tenantId, UUID submissionId) {
        return jdbc.sql(SELECT + " WHERE s.tenant_id = :tenantId AND s.id = :id")
                .param("tenantId", tenantId).param("id", submissionId)
                .query((rs, n) -> toSubmission(rs)).optional();
    }

    @Override
    public Optional<Submission> lockLatest(UUID tenantId, UUID itemId, String ownerKey) {
        return jdbc.sql(SELECT + " WHERE s.tenant_id = :tenantId AND s.item_id = :itemId AND s.owner_key = :ownerKey"
                        + " AND s.is_latest FOR UPDATE OF s")
                .param("tenantId", tenantId).param("itemId", itemId).param("ownerKey", ownerKey)
                .query((rs, n) -> toSubmission(rs)).optional();
    }

    @Override
    public Optional<Submission> findLatest(UUID tenantId, UUID itemId, String ownerKey) {
        return jdbc.sql(SELECT + " WHERE s.tenant_id = :tenantId AND s.item_id = :itemId AND s.owner_key = :ownerKey AND s.is_latest")
                .param("tenantId", tenantId).param("itemId", itemId).param("ownerKey", ownerKey)
                .query((rs, n) -> toSubmission(rs)).optional();
    }

    @Override
    public Optional<Submission> findLatestForMember(UUID tenantId, UUID itemId, UUID userId) {
        return jdbc.sql(SELECT + " WHERE s.tenant_id = :tenantId AND s.item_id = :itemId AND s.is_latest AND" + MEMBER_OF
                        + " ORDER BY s.created_at DESC LIMIT 1")
                .param("tenantId", tenantId).param("itemId", itemId).param("userId", userId)
                .query((rs, n) -> toSubmission(rs)).optional();
    }

    @Override
    public boolean insert(Submission s) {
        return jdbc.sql("""
                INSERT INTO submissions (id, tenant_id, course_id, item_id, user_id, group_id, owner_key, attempt_no, is_latest,
                                         status, text, submitted_at, due_at, late, version, created_at, updated_at)
                VALUES (:id, :tenantId, :courseId, :itemId, :userId, :groupId, :ownerKey, :attemptNo, TRUE,
                        :status, :text, :submittedAt, :dueAt, :late, 0, :createdAt, :updatedAt)
                ON CONFLICT DO NOTHING
                """)
            .param("id", s.id()).param("tenantId", s.tenantId()).param("courseId", s.courseId()).param("itemId", s.itemId())
            .param("userId", s.authorId()).param("groupId", s.groupId()).param("ownerKey", s.ownerKey())
            .param("attemptNo", s.attemptNo()).param("status", s.status().key()).param("text", json.toJsonb(s.text()))
            .param("submittedAt", Timestamps.of(s.submittedAt())).param("dueAt", Timestamps.of(s.dueAt()))
            .param("late", s.late()).param("createdAt", Timestamps.of(s.createdAt()))
            .param("updatedAt", Timestamps.of(s.updatedAt()))
            .update() == 1;
    }

    @Override
    public void addMembers(UUID tenantId, UUID submissionId, Collection<UUID> userIds) {
        userIds.stream().distinct().forEach(userId -> jdbc.sql("""
                INSERT INTO submission_members (tenant_id, submission_id, user_id) VALUES (:tenantId, :submissionId, :userId)
                ON CONFLICT DO NOTHING
                """)
            .param("tenantId", tenantId).param("submissionId", submissionId).param("userId", userId)
            .update());
    }

    @Override
    public List<UUID> members(UUID tenantId, UUID submissionId) {
        return jdbc.sql("SELECT user_id FROM submission_members WHERE tenant_id = :tenantId AND submission_id = :id ORDER BY user_id")
                .param("tenantId", tenantId).param("id", submissionId)
                .query((rs, n) -> rs.getObject("user_id", UUID.class)).list();
    }

    @Override
    public boolean isMember(UUID tenantId, UUID submissionId, UUID userId) {
        return jdbc.sql("""
                SELECT COUNT(*) FROM submission_members
                WHERE tenant_id = :tenantId AND submission_id = :id AND user_id = :userId
                """)
            .param("tenantId", tenantId).param("id", submissionId).param("userId", userId)
            .query(Long.class).single() > 0;
    }

    @Override
    public void markSuperseded(UUID tenantId, UUID submissionId) {
        jdbc.sql("UPDATE submissions SET is_latest = FALSE WHERE tenant_id = :tenantId AND id = :id")
            .param("tenantId", tenantId).param("id", submissionId).update();
    }

    @Override
    public boolean update(Submission s, long expectedVersion) {
        return jdbc.sql("""
                UPDATE submissions SET status = :status, text = :text, submitted_at = :submittedAt, due_at = :dueAt,
                       late = :late, version = version + 1, updated_at = :updatedAt
                WHERE tenant_id = :tenantId AND id = :id AND version = :expected
                """)
            .param("status", s.status().key()).param("text", json.toJsonb(s.text()))
            .param("submittedAt", Timestamps.of(s.submittedAt())).param("dueAt", Timestamps.of(s.dueAt()))
            .param("late", s.late()).param("updatedAt", Timestamps.of(s.updatedAt()))
            .param("tenantId", s.tenantId()).param("id", s.id()).param("expected", expectedVersion)
            .update() == 1;
    }

    @Override
    public void replaceFiles(UUID tenantId, UUID submissionId, List<UUID> fileIds) {
        jdbc.sql("DELETE FROM submission_files WHERE tenant_id = :tenantId AND submission_id = :id")
            .param("tenantId", tenantId).param("id", submissionId).update();
        List<UUID> distinct = fileIds.stream().distinct().toList();
        for (int position = 0; position < distinct.size(); position++) {
            jdbc.sql("""
                    INSERT INTO submission_files (tenant_id, submission_id, file_id, position)
                    VALUES (:tenantId, :submissionId, :fileId, :position)
                    """)
                .param("tenantId", tenantId).param("submissionId", submissionId)
                .param("fileId", distinct.get(position)).param("position", position)
                .update();
        }
    }

    @Override
    public List<Submission> attempts(UUID tenantId, UUID itemId, String ownerKey) {
        return jdbc.sql(SELECT + " WHERE s.tenant_id = :tenantId AND s.item_id = :itemId AND s.owner_key = :ownerKey"
                        + " ORDER BY s.attempt_no")
                .param("tenantId", tenantId).param("itemId", itemId).param("ownerKey", ownerKey)
                .query((rs, n) -> toSubmission(rs)).list();
    }

    @Override
    public List<Submission> listLatest(UUID tenantId, UUID itemId, SubmissionFilter filter, PageQuery page) {
        if (filter.memberIds() != null && filter.memberIds().isEmpty()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder(SELECT)
                .append(" WHERE s.tenant_id = :tenantId AND s.item_id = :itemId AND s.is_latest");
        Map<String, Object> params = new HashMap<>(Map.of("tenantId", tenantId, "itemId", itemId, "limit", page.fetchSize()));
        appendFilter(sql, params, filter);
        page.after().ifPresent(after -> {
            sql.append(" AND (COALESCE(s.submitted_at, s.created_at), s.id) > (:afterAt, :afterId)");
            params.put("afterAt", Timestamps.of(after.sortKey()));
            params.put("afterId", after.id());
        });
        sql.append(" ORDER BY COALESCE(s.submitted_at, s.created_at), s.id LIMIT :limit");
        return jdbc.sql(sql.toString()).params(params).query((rs, n) -> toSubmission(rs)).list();
    }

    private static void appendFilter(StringBuilder sql, Map<String, Object> params, SubmissionFilter filter) {
        if (!filter.statuses().isEmpty()) {
            sql.append(" AND s.status IN (:statuses)");
            params.put("statuses", filter.statuses().stream().map(SubmissionStatus::key).toList());
        }
        if (filter.lateOnly()) {
            sql.append(" AND s.late");
        }
        if (filter.memberIds() != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM submission_members m WHERE m.submission_id = s.id AND m.user_id IN (:memberIds))");
            params.put("memberIds", List.copyOf(filter.memberIds()));
        }
    }

    @Override
    public Map<UUID, SubmissionStatus> latestStatusesForMember(UUID tenantId, UUID userId, Collection<UUID> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql("SELECT s.item_id, s.status FROM submissions s WHERE s.tenant_id = :tenantId AND s.item_id IN (:itemIds)"
                        + " AND s.is_latest AND" + MEMBER_OF)
                .param("tenantId", tenantId).param("itemIds", List.copyOf(itemIds)).param("userId", userId)
                .query((rs, n) -> Map.entry(rs.getObject("item_id", UUID.class), SubmissionStatus.fromKey(rs.getString("status"))))
                .list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (first, second) -> first));
    }

    @Override
    public List<Submission> awaitingGrading(UUID tenantId, Collection<UUID> courseIds) {
        if (courseIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql(SELECT + """
                 WHERE s.tenant_id = :tenantId AND s.course_id IN (:courseIds) AND s.is_latest
                   AND s.status IN ('submitted', 'submitted_late')
                ORDER BY s.submitted_at, s.id
                """)
            .param("tenantId", tenantId).param("courseIds", List.copyOf(courseIds))
            .query((rs, n) -> toSubmission(rs)).list();
    }

    @Override
    public void insertFeedback(Feedback f) {
        jdbc.sql("""
                INSERT INTO submission_feedback (id, tenant_id, submission_id, grader_id, text, file_ids, score,
                                                 return_for_revision, published_at, created_at)
                VALUES (:id, :tenantId, :submissionId, :graderId, :text, :fileIds, :score, :returned, :publishedAt, :createdAt)
                """)
            .param("id", f.id()).param("tenantId", f.tenantId()).param("submissionId", f.submissionId())
            .param("graderId", f.graderId()).param("text", json.toJsonb(f.text())).param("fileIds", json.toJsonb(f.fileIds()))
            .param("score", f.score()).param("returned", f.returnedForRevision())
            .param("publishedAt", Timestamps.of(f.publishedAt())).param("createdAt", Timestamps.of(f.createdAt()))
            .update();
    }

    @Override
    public Optional<Feedback> latestFeedback(UUID tenantId, UUID submissionId) {
        return jdbc.sql("SELECT " + FEEDBACK_COLUMNS + """
                 FROM submission_feedback WHERE tenant_id = :tenantId AND submission_id = :id
                ORDER BY created_at DESC, id DESC LIMIT 1
                """)
            .param("tenantId", tenantId).param("id", submissionId)
            .query((rs, n) -> toFeedback(rs)).optional();
    }

    @Override
    public Map<UUID, Feedback> latestFeedbacks(UUID tenantId, Collection<UUID> submissionIds) {
        if (submissionIds.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql("SELECT DISTINCT ON (submission_id) " + FEEDBACK_COLUMNS + """
                 FROM submission_feedback WHERE tenant_id = :tenantId AND submission_id IN (:ids)
                ORDER BY submission_id, created_at DESC, id DESC
                """)
            .param("tenantId", tenantId).param("ids", List.copyOf(submissionIds))
            .query((rs, n) -> toFeedback(rs)).list().stream()
            .collect(Collectors.toMap(Feedback::submissionId, Function.identity()));
    }

    @Override
    public Optional<FeedbackOwner> feedbackOwner(UUID tenantId, UUID feedbackId) {
        return jdbc.sql("""
                SELECT s.id, s.course_id, s.status, f.published_at FROM submission_feedback f
                JOIN submissions s ON s.id = f.submission_id
                WHERE f.tenant_id = :tenantId AND f.id = :id
                """)
            .param("tenantId", tenantId).param("id", feedbackId)
            .query((rs, n) -> new FeedbackOwner(rs.getObject("id", UUID.class), rs.getObject("course_id", UUID.class),
                    SubmissionStatus.fromKey(rs.getString("status")), Timestamps.read(rs, "published_at")))
            .optional();
    }

    @Override
    public int publishFeedback(UUID tenantId, UUID itemId, Instant at) {
        return jdbc.sql("""
                UPDATE submission_feedback f SET published_at = :at
                FROM submissions s
                WHERE f.submission_id = s.id AND f.tenant_id = :tenantId AND s.item_id = :itemId AND f.published_at IS NULL
                """)
            .param("at", Timestamps.of(at)).param("tenantId", tenantId).param("itemId", itemId)
            .update();
    }

    @Override
    public Map<UUID, Map<String, Object>> publishedFeedbackTexts(UUID tenantId, UUID userId, Collection<UUID> itemIds) {
        if (itemIds.isEmpty()) {
            return Map.of();
        }
        return jdbc.sql("""
                SELECT DISTINCT ON (s.item_id) s.item_id, f.text::text AS text
                FROM submissions s JOIN submission_feedback f ON f.submission_id = s.id
                WHERE s.tenant_id = :tenantId AND s.item_id IN (:itemIds) AND s.is_latest AND f.published_at IS NOT NULL
                  AND f.text IS NOT NULL AND
                """ + MEMBER_OF + " ORDER BY s.item_id, f.created_at DESC")
            .param("tenantId", tenantId).param("itemIds", List.copyOf(itemIds)).param("userId", userId)
            .query((rs, n) -> Map.entry(rs.getObject("item_id", UUID.class), json.read(rs.getString("text"), DOC)))
            .list().stream()
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Submission toSubmission(ResultSet rs) throws SQLException {
        return new Submission(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("course_id", UUID.class), rs.getObject("item_id", UUID.class),
                rs.getObject("user_id", UUID.class), rs.getObject("group_id", UUID.class), rs.getString("owner_key"),
                rs.getInt("attempt_no"), rs.getBoolean("is_latest"), SubmissionStatus.fromKey(rs.getString("status")),
                json.read(rs.getString("text"), DOC), json.read(rs.getString("file_ids"), UUIDS),
                Timestamps.read(rs, "submitted_at"), Timestamps.read(rs, "due_at"), rs.getBoolean("late"),
                rs.getLong("version"), Timestamps.read(rs, "created_at"), Timestamps.read(rs, "updated_at"));
    }

    private Feedback toFeedback(ResultSet rs) throws SQLException {
        return new Feedback(rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
                rs.getObject("submission_id", UUID.class), rs.getObject("grader_id", UUID.class),
                json.read(rs.getString("text"), DOC), json.read(rs.getString("file_ids"), UUIDS),
                rs.getBigDecimal("score"), rs.getBoolean("return_for_revision"), Timestamps.read(rs, "published_at"),
                Timestamps.read(rs, "created_at"));
    }
}
